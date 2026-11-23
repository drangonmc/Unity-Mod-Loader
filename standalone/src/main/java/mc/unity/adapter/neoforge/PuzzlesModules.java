package mc.unity.adapter.neoforge;
import java.io.*;
import java.util.*;
import java.util.function.UnaryOperator;
import org.objectweb.asm.*;
import mc.unity.adapter.common.PlatformAbi;
import java.lang.classfile.ClassFile;
/** Only applicable to the verified SDK when Unity's SPI provider is selected. */
public final class PuzzlesModules {
    private static final Set<String> DEFERRED = read();
    private static Set<String> read() {
        try (var in = PuzzlesModules.class.getResourceAsStream("/META-INF/unity/puzzles/dormant-abi.tsv")) {
            if (in == null) throw new FileNotFoundException("Missing SDK linkage profile");
            return Set.copyOf(new BufferedReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).lines().toList());
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private static final Set<String> OPTIONAL_CLASSES = ownerClasses();
    private static Set<String> ownerClasses() {
        var result = new HashSet<String>();
        DEFERRED.forEach(line -> result.add(line.substring(0,line.indexOf('\t'))));
        return Set.copyOf(result);
    }
    public static UnaryOperator<byte[]> transformer() {return PuzzlesModules::guard;}
    private static byte[] guard(byte[] original) {
        var reader=new ClassReader(original);
        String owner=reader.getClassName();
        if (!OPTIONAL_CLASSES.contains(owner)) return original;
        var missing=new HashSet<String>();
        try {
            new PlatformAbi().verify(ClassFile.of().parse(original),reason->{
                if(!deferred(owner,reason)) return false;
                missing.add(reason);return true;
            });
        } catch(IOException ex){throw new IllegalStateException(owner+": "+ex.getMessage(),ex);}
        if(missing.isEmpty()) return original;
        var types=new HashSet<String>();
        var members=new HashSet<String>();
        for(var reason:missing) {
            if (reason.startsWith("Unsupported platform type: ")) types.add(reason.substring("Unsupported platform type: ".length()));
            else if(reason.startsWith("Unsupported platform member: ")) members.add(reason.substring("Unsupported platform member: ".length()));
        }
        var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
            public MethodVisitor visitMethod(int a,String m,String d,String signature,String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,m,d,signature,exceptions)) {
                    private void reject(String symbol) {
                        mv.visitLdcInsn(owner+"."+m+": "+symbol);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,"mc/unity/adapter/neoforge/PuzzlesModules","unavailable","(Ljava/lang/String;)V",false);
                    }
                    private void descriptor(String desc) {
                        for(String t:types) if(desc.contains("L"+t+";")){reject(t);return;}
                    }
                    private void reference(String cls,String name,String desc) {
                        if(types.contains(cls))reject(cls);
                        else if(members.contains(cls+"."+name+desc))reject(cls+"."+name+desc);
                        else descriptor(desc);
                    }
                    public void visitFieldInsn(int op,String cls,String name,String desc){
                        reference(cls,name,desc);super.visitFieldInsn(op,cls,name,desc);
                    }
                    public void visitMethodInsn(int op,String cls,String name,String desc,boolean itf){
                        reference(cls,name,desc);super.visitMethodInsn(op,cls,name,desc,itf);
                    }
                    public void visitTypeInsn(int op,String cls) {
                        if(types.contains(cls))reject(cls);else descriptor(cls);
                        super.visitTypeInsn(op,cls);
                    }
                    public void visitMultiANewArrayInsn(String desc,int dims) {
                        descriptor(desc);super.visitMultiANewArrayInsn(desc,dims);
                    }
                    private void constant(Object v) {
                        if(v instanceof Type t)descriptor(t.getDescriptor());
                        else if(v instanceof Handle h)reference(h.getOwner(),h.getName(),h.getDesc());
                    }
                    public void visitLdcInsn(Object v){constant(v);super.visitLdcInsn(v);}
                    public void visitInvokeDynamicInsn(String n,String desc,Handle bootstrap,Object... args) {
                        descriptor(desc);constant(bootstrap);
                        for(var value:args)constant(value);
                        super.visitInvokeDynamicInsn(n,desc,bootstrap,args);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }
    public static void unavailable(String symbol) {throw new UnsupportedOperationException("Unsupported Puzzles Lib platform call: "+symbol);}
    public static boolean deferred(String owner, String missing) {
        return DEFERRED.contains(owner + "\t" + missing);
    }
}
