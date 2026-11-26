package mc.unity.bootstrap;
import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import com.google.gson.GsonBuilder;

/** Only the test JVM redirects the canonical plan's build call into the inspector. */
public final class AnchorAudit {
    private static final List<Map<String,String>> results=new ArrayList<>();
    private static Object call(Object target,String method) throws ReflectiveOperationException {
        var accessor=target.getClass().getDeclaredMethod(method);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }
    @SuppressWarnings("unchecked")
    public static Map<String,byte[]> inspect(TransformPlan plan,JarFile game,ClassFile cf) throws Exception {
        var field=TransformPlan.class.getDeclaredField("classes");field.setAccessible(true);
        var classes=(Map<String,List<?>>)field.get(plan);
        for (var entry:classes.entrySet()) {
            var file=game.getJarEntry(entry.getKey()+".class");
            byte[] bytes=null;
            if (file!=null) try(var in=game.getInputStream(file)){bytes=in.readAllBytes();}
            record Step(int priority,String adapter,String member,TransformPlan.ByteTransform code) {}
            var steps=new ArrayList<Step>();
            for(var raw:entry.getValue())steps.add(new Step((int)call(raw,"priority"),(String)call(raw,"adapter"),
                                                    (String)call(raw,"member"),(TransformPlan.ByteTransform)call(raw,"transform")));
            steps.sort(Comparator.comparingInt(Step::priority).thenComparing(Step::adapter).thenComparing(Step::member));
            String blocked=null;
            for (var step:steps) {
                String status="ok",reason="";
                if (blocked!=null){status="blocked";reason=blocked;}
                else try {
                    if(bytes==null)throw new IOException("Missing input class");
                    bytes=step.code.apply(cf,bytes);
                } catch(Exception | LinkageError failure) {
                    status="failed";reason=failure.toString();blocked=step.adapter+" "+step.member;
                }
                results.add(Map.of("class",entry.getKey(),"stage",step.adapter,"member",step.member,"status",status,"detail",reason));
            }
        }
        return Map.of();
    }
    public static void main(String[] a)throws Exception {
        if (a[0].equals("prepare")) {
            var cf=ClassFile.of();
            try(var z=new JarFile(a[1])) {
                String name="mc/unity/bootstrap/GamePatches.class";
                try(var in=z.getInputStream(z.getJarEntry(name))) {
                    int[] count={0};
                    var bytes=cf.transformClass(cf.parse(in.readAllBytes()),ClassTransform.transformingMethodBodies(
                        m->m.methodName().equalsString("installSide"),(b,e)->{
                            if(e instanceof InvokeInstruction i && i.owner().asInternalName().equals("mc/unity/bootstrap/TransformPlan") &&
                               i.name().equalsString("build") && i.type().equalsString("(Ljava/util/jar/JarFile;Ljava/lang/classfile/ClassFile;)Ljava/util/Map;")) {
                                count[0]++;
                                b.invokestatic(ClassDesc.of("mc.unity.bootstrap.AnchorAudit"),"inspect",MethodTypeDesc.ofDescriptor(
                                    "(Lmc/unity/bootstrap/TransformPlan;Ljava/util/jar/JarFile;Ljava/lang/classfile/ClassFile;)Ljava/util/Map;"));
                            } else b.with(e);
                        }));
                    if(count[0]!=1)throw new IOException("Changed plan entry point");
                    Path target=Path.of(a[2],name);Files.createDirectories(target.getParent());Files.write(target,bytes);
                }
            }
            return;
        }
        Path game=Path.of(a[2]);
        var paths=new ArrayList<java.net.URL>();
        for(String p: System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            paths.add(Path.of(p).toUri().toURL());
        try(var loader=new GameLoader(paths.toArray(java.net.URL[]::new),game)) {
            if(a[1].equals("client"))GamePatches.installClient(loader,game);else GamePatches.install(loader,game);
        }
        if(results.isEmpty())throw new IOException("Canonical plan did not reach inspector");
        Files.writeString(Path.of(a[3]), new GsonBuilder().setPrettyPrinting().create().toJson(results));
    }
}
