package mc.unity.adapter.common;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.lang.module.ModuleDescriptor;
import java.security.*;
/** Identity shared by an original Maven archive and Fabric Loom's metadata-wrapped copy of it. */
record LibraryIdentity(String module,String vendor,String title,String version){
    record Family(String module,String vendor,String title){}
    Family family(){return new Family(module,vendor,title);}
    static LibraryIdentity read(Path path,boolean generated)throws IOException{
        try(var jar=new JarFile(path.toFile(),true)){
            var fabric=NestedArchives.json(jar,"fabric.mod.json");
            if(generated && (fabric==null || !Set.of("schemaVersion","id","name","version","custom").containsAll(fabric.keySet()) ||
                    !fabric.has("custom") || !fabric.getAsJsonObject("custom").has("fabric-loom:generated") ||
                    !fabric.getAsJsonObject("custom").get("fabric-loom:generated").getAsBoolean()))return null;
            var mf=jar.getManifest();if(mf==null)return null;
            var a=mf.getMainAttributes();String vendor=a.getValue("Implementation-Vendor"),title=a.getValue("Implementation-Title"),version=a.getValue("Implementation-Version");
            if(vendor==null || title==null || version==null || vendor.isBlank() || title.isBlank())return null;
            var descriptor=jar.getJarEntry("module-info.class");if(descriptor==null)descriptor=jar.getJarEntry("META-INF/versions/9/module-info.class");
            if(descriptor==null)return null;
            try(var in=jar.getInputStream(descriptor)){
                return new LibraryIdentity(ModuleDescriptor.read(in).name(),vendor,title,version);
            }
        }
    }
    static Map<String,String> payload(Path file)throws Exception{
        var hashes=new TreeMap<String,String>();
        try(var jar=new JarFile(file.toFile(),true)){
            for(var e:Collections.list(jar.entries())){
                String n=e.getName();if(e.isDirectory() || n.equals("fabric.mod.json") || n.equals(JarFile.MANIFEST_NAME))continue;
                try(var in=jar.getInputStream(e)){var digest=MessageDigest.getInstance("SHA-256");in.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(),digest));hashes.put(n,HexFormat.of().formatHex(digest.digest()));}
            }
        }return hashes;
    }
}
