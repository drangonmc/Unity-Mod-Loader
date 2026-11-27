import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import mc.unity.adapter.common.FmlDescriptor;
import mc.unity.bootstrap.JarContract;
import org.objectweb.asm.*;
public class Contracts {
    public static void main(String[] args) throws Exception {
        var fabric=mc.unity.adapter.fabric.LoaderView.INSTANCE;
        fabric.bind(Path.of("."),false,List.of());
        var java=fabric.getModContainer("java").orElseThrow().getMetadata();
        require(java.getType().equals("builtin") && java.getVersion().getFriendlyString().equals(System.getProperty("java.version")));
        fabric.close();
        String union="[1.0,2.0),[3.0,)";
        for (String v:List.of("1","1.7","3","10"))require(FmlDescriptor.inRange(v,union));
        for (String v:List.of("0","2","2.99"))require(!FmlDescriptor.inRange(v,union));
        var ban=new FmlDescriptor.Dependency("other",union,"incompatible","NONE");
        require(ban.accepts("2") && !ban.accepts("1.1"));
        new FmlDescriptor.Dependency("other",union,"discouraged","NONE").validate("fixture","1.2");
        try{FmlDescriptor.inRange("1","[1],[2],");throw new AssertionError("trailing union");}catch(java.io.IOException expected){}
        requiredLinks();
        accessRules();
        var bridge=new ArrayList<byte[]>();
        try(var jar=new JarFile(args[0])) {
            for(var entry:Collections.list(jar.entries())) if(entry.getName().endsWith(".class"))
                try(var in=jar.getInputStream(entry)){bridge.add(in.readAllBytes());}
        }
        for(int i=1;i<=2;i++)try(var jar=new JarFile(args[i])) {
            JarContract.check(jar,bridge,"com/sk89q/worldedit/","org/enginehub/linbus/");
        }
        var generated=new ClassWriter(0);
        generated.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"qa/BadLink",null,"java/lang/Object",null);
        var m=generated.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"example","()V",null,null);
        m.visitCode();m.visitMethodInsn(Opcodes.INVOKESTATIC,"com/sk89q/worldedit/WorldEdit","absentUnityMethod","()V",false);
        m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();generated.visitEnd();
        try(var jar=new JarFile(args[1])) {
            try {JarContract.check(jar,List.of(generated.toByteArray()),"com/sk89q/worldedit/");throw new AssertionError();}
            catch(java.io.IOException expected){require(expected.getMessage().contains("absentUnityMethod"));}
        }
        System.out.println("PASS Maven union, dependency policies, two original WorldEdit ABIs and missing method rejection");
    }
    static void requiredLinks() throws Exception {
        String missing="net/neoforged/testoptional/RequiredWidget";
        for(boolean header:List.of(false,true)) {
            var generated=new ClassWriter(0);
            generated.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"qa/Required",null,header?missing:"java/lang/Object",null);
            var m=generated.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"run","()V",null,null);
            m.visitCode();
            if(!header)m.visitMethodInsn(Opcodes.INVOKESTATIC,missing,"run","()V",false);
            m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();generated.visitEnd();
            byte[] bytes=generated.toByteArray();
            try {
                mc.unity.adapter.common.OptionalLinkage.check("required.jar",java.lang.classfile.ClassFile.of().parse(bytes),
                        bytes,new mc.unity.adapter.common.PlatformAbi());
                throw new AssertionError("Eager platform link was admitted");
            } catch(java.io.IOException expected){require(expected.getMessage().contains(missing));}
        }
        System.out.println("PASS conditional mode rejects hard inheritance and unconditional calls");
    }
    static void archive(Path file,Map<String,String> entries) throws Exception {
        try(var out=new JarOutputStream(Files.newOutputStream(file))) {
            for(var entry:entries.entrySet()){out.putNextEntry(new JarEntry(entry.getKey()));out.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.closeEntry();}
        }
    }
    static void accessRules() throws Exception {
        Path dir=Files.createTempDirectory("unity28-rules-");
        Path aw=dir.resolve("access.jar"),at=dir.resolve("transform.jar");
        String owner="net/minecraft/world/entity/LivingEntity", method="updateDirtyEffects";
        try {
            archive(aw,Map.of("fabric.mod.json","{\"schemaVersion\":1,\"id\":\"testaw\",\"version\":\"1\",\"accessWidener\":\"test.aw\"}",
                              "test.aw","accessWidener v2 named\naccessible method "+owner+" "+method+" ()V\n"));
            archive(at,Map.of("META-INF/accesstransformer.cfg","public "+owner.replace('/','.')+" "+method+"()V\n"));
            try(var a=new JarFile(aw.toFile());var t=new JarFile(at.toFile())) {
                for(var order:List.of(List.of(a),List.of(a,t),List.of(t,a))) {
                    var patched=mc.unity.adapter.common.AccessTransforms.prepare(order,Contracts.class.getClassLoader(),false).get(owner);
                    var flags=java.lang.classfile.ClassFile.of().parse(patched).methods().stream()
                        .filter(m->m.methodName().equalsString(method)).findFirst().orElseThrow().flags();
                    require(flags.has(java.lang.reflect.AccessFlag.PUBLIC));
                    require(flags.has(java.lang.reflect.AccessFlag.FINAL)==(order.size()==1));
                }
            }
            for(String name:List.of("jdk.internal.misc.Unsafe","javax.crypto.Cipher","sun.misc.Unsafe")) {
                archive(at,Map.of("META-INF/accesstransformer.cfg","public "+name+"\n"));
                try(var t=new JarFile(at.toFile())) {
                    try { mc.unity.adapter.common.AccessTransforms.prepare(List.of(t),Contracts.class.getClassLoader(),false);throw new AssertionError(name); }
                    catch(java.io.IOException ex){require(ex.getMessage().contains("Unsupported access target"));}
                }
            }
            System.out.println("PASS AW alone and both AW/AT orders; protected runtime namespaces");
        } finally {Files.deleteIfExists(aw);Files.deleteIfExists(at);Files.deleteIfExists(dir);}
    }
    static void require(boolean b){if(!b)throw new AssertionError();}
}
