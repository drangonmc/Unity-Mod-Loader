import com.google.gson.*;
import java.io.*;
import java.lang.classfile.ClassFile;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.adapter.common.PlatformAbi;

/** Read-only survey: checks API symbols without loading candidate classes or executing author code. */
class CorpusPreflight {
    public static void main(String[] args) throws Exception {
        var abi = new PlatformAbi();
        var report = new TreeMap<String,Object>();
        try (var contents = Files.list(Path.of(args[0]))) {
            for (var file : contents.filter(p->p.toString().endsWith(".jar")).sorted().toList()) {
                var missing = new TreeSet<String>();
                int classes = 0;
                String descriptor="";
                try (var jar = new JarFile(file.toFile(), true, JarFile.OPEN_READ,Runtime.version())) {
                    if(jar.getEntry("fabric.mod.json")!=null) {
                        try { mc.unity.adapter.fabric.FabricAdapter.read(jar,file,"SERVER"); mc.unity.adapter.fabric.FabricAdapter.read(jar,file,"CLIENT"); }
                        catch(Exception invalid) { descriptor=invalid.toString(); }
                    }
                    for (var it=jar.versionedStream().iterator();it.hasNext();) {
                        var e=it.next();
                        if (!e.getName().endsWith(".class") || e.getName().endsWith("module-info.class")) continue;
                        classes++;
                        try(var in=jar.getInputStream(e)) {
                            try { abi.verify(ClassFile.of().parse(in.readAllBytes()),reason->{missing.add(reason);return true;}); }
                            catch (RuntimeException invalid) {missing.add(invalid.toString());}
                        }
                    }
                }
                report.put(file.getFileName().toString(),Map.of("classes",classes,"missing",missing,"descriptor",descriptor));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
