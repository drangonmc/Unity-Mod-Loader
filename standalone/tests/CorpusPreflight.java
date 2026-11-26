import com.google.gson.*;
import java.io.*;
import java.lang.classfile.ClassFile;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.adapter.common.PlatformAbi;

/** Read-only survey: checks API symbols without loading candidate classes or executing author code. */
class CorpusPreflight {
    /** Declared or inherited descriptors in the pinned SDK and its dependencies. No sample class is defined. */
    private static final class BukkitSdk implements Closeable {
        final java.net.URLClassLoader resources;
        final Map<String,Optional<java.lang.classfile.ClassModel>> cache=new HashMap<>();
        BukkitSdk(Path sdk) throws IOException {
            List<java.net.URL> urls=new ArrayList<>();
            try(var files=Files.list(sdk)){
                for(var f:files.filter(p->p.toString().endsWith(".jar")).sorted().toList())urls.add(f.toUri().toURL());
            }
            resources=new java.net.URLClassLoader(urls.toArray(java.net.URL[]::new),CorpusPreflight.class.getClassLoader());
        }
        java.lang.classfile.ClassModel model(String owner) {
            return cache.computeIfAbsent(owner,n->{
                try(var in=resources.getResourceAsStream(n+".class")){
                    return in==null ? Optional.empty() : Optional.of(ClassFile.of().parse(in.readAllBytes()));
                } catch(IOException e){throw new UncheckedIOException(e);}
            }).orElse(null);
        }
        String check(String symbol) {
            int dot=symbol.indexOf('.'), colon=symbol.indexOf(':',dot);
            String owner=symbol.substring(0,dot), name=symbol.substring(dot+1,colon), desc=symbol.substring(colon+1);
            if(model(owner)==null)return "missing-type";
            var queue=new ArrayDeque<String>();queue.add(owner);
            var seen=new HashSet<String>();boolean incomplete=false;
            while(!queue.isEmpty()){
                String n=queue.remove();
                if(!seen.add(n))continue;
                var cls=model(n);
                if(cls==null){incomplete=true;continue;}
                if(desc.startsWith("(")){
                    if(cls.methods().stream().anyMatch(m->m.methodName().equalsString(name)&&m.methodType().equalsString(desc)))return "found";
                } else if(cls.fields().stream().anyMatch(f->f.fieldName().equalsString(name)&&f.fieldType().equalsString(desc)))return "found";
                if(!name.equals("<init>")){
                    cls.superclass().ifPresent(c->queue.add(c.asInternalName()));
                    cls.interfaces().forEach(c->queue.add(c.asInternalName()));
                }
            }
            return incomplete ? "incomplete-hierarchy" : "missing-member";
        }
        public void close() throws IOException {resources.close();}
    }
    public static void main(String[] args) throws Exception {
        var abi = new PlatformAbi();
        var report = new TreeMap<String,Object>();
        try (var sdk=args.length>2 ? new BukkitSdk(Path.of(args[2])) : null;
             var contents = Files.list(Path.of(args[0]))) {
            for (var file : contents.filter(p->p.toString().endsWith(".jar")).sorted().toList()) {
                var missing = new TreeSet<String>();
                var pluginReferences = new TreeSet<String>();
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
                            try {
                                var model=ClassFile.of().parse(in.readAllBytes());
                                for (var constant : model.constantPool()) {
                                    if (!(constant instanceof java.lang.classfile.constantpool.MemberRefEntry member)) continue;
                                    String owner=member.owner().asInternalName();
                                    if (owner.startsWith("org/bukkit/") || owner.startsWith("io/papermc/paper/") ||
                                        owner.startsWith("com/destroystokyo/paper/"))
                                        pluginReferences.add(owner+"."+member.nameAndType().name().stringValue()+":"+member.nameAndType().type().stringValue());
                                }
                                abi.verify(model,reason->{missing.add(reason);return true;});
                            }
                            catch (RuntimeException invalid) {missing.add(invalid.toString());}
                        }
                    }
                }
                var sdkGaps=new TreeMap<String,String>();
                if(sdk!=null)for(String symbol:pluginReferences){
                    String state=sdk.check(symbol);if(!state.equals("found"))sdkGaps.put(symbol,state);
                }
                report.put(file.getFileName().toString(),Map.of("classes",classes,"missing",missing,"descriptor",descriptor,
                    "pluginMemberReferences",pluginReferences,"sdkChecked",sdk!=null,"sdkGaps",sdkGaps));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
