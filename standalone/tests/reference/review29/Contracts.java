import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.lang.classfile.ClassFile;
import mc.unity.adapter.common.OptionalLinkage;
import mc.unity.adapter.common.PlatformAbi;
import mc.unity.bootstrap.JarContract;
import org.objectweb.asm.*;

class Contracts implements Opcodes {
    static byte[] choice(boolean both,boolean frame) {
        String name="qa/Conditional",a="net/neoforged/optional/MissingA",b="net/neoforged/optional/MissingB";
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(V17,ACC_PUBLIC,name,null,"java/lang/Object",null);
        var m=writer.visitMethod(ACC_PUBLIC|ACC_STATIC,"test","(Z)V",null,null);
        m.visitCode();var second=new Label();var done=new Label();m.visitVarInsn(ILOAD,0);m.visitJumpInsn(IFEQ,second);
        m.visitMethodInsn(INVOKESTATIC,a,"call","()V",false);m.visitJumpInsn(GOTO,done);
        m.visitLabel(second);m.visitFrame(F_SAME,0,null,0,null);
        if(both)m.visitMethodInsn(INVOKESTATIC,b,"call","()V",false);
        if(frame){m.visitInsn(ACONST_NULL);m.visitTypeInsn(CHECKCAST,b);m.visitVarInsn(ASTORE,1);
            var edge=new Label();m.visitJumpInsn(GOTO,edge);m.visitLabel(edge);m.visitFrame(F_APPEND,1,new Object[]{b},0,null);m.visitInsn(NOP);}
        m.visitLabel(done);m.visitFrame(frame?F_CHOP:F_SAME,frame?1:0,null,0,null);m.visitInsn(RETURN);m.visitMaxs(0,0);m.visitEnd();writer.visitEnd();return writer.toByteArray();
    }
    static byte[] type(String n,String parent,boolean face,boolean declare,boolean concrete,String... interfaces) {
        var out=new ClassWriter(0);
        out.visit(V17,ACC_PUBLIC|(face?ACC_INTERFACE|ACC_ABSTRACT:concrete?ACC_SUPER:ACC_ABSTRACT),n,null,parent,interfaces);
        if(declare){
            var m=out.visitMethod(ACC_PUBLIC|(face?ACC_ABSTRACT:0),"entry","()V",null,null);
            if(!face){m.visitCode();m.visitInsn(RETURN);m.visitMaxs(0,1);}
            m.visitEnd();
        }
        out.visitEnd();return out.toByteArray();
    }
    public static void main(String[] args)throws Exception{
        var ctor=mc.unity.core.Actions.Action.class.getDeclaredConstructor(mc.unity.core.Actions.Kind.class);
        ctor.setAccessible(true);
        var action=ctor.newInstance(mc.unity.core.Actions.Kind.BREAK);action.experience=29;
        var forge=new mc.unity.adapter.forge.ActionAdapter();
        forge.accept(action);
        if(!Objects.equals(action.experience,29))throw new AssertionError("Lost previous XP override");
        var bus=net.minecraftforge.event.level.BlockEvent.BreakEvent.BUS;
        bus.addListener((java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent>)e->e.setExpToDrop(7));
        forge.accept(action);
        if(!Objects.equals(action.experience,7))throw new AssertionError("Failed to apply explicit XP override");
        for(int i=0;i<3;i++){
            byte[] bytes=choice(i==1,i==2);
            try { OptionalLinkage.check("sample.jar",ClassFile.of().parse(bytes),bytes,new PlatformAbi());
                  if(i!=0)throw new AssertionError("Invalid path admitted "+i); }
            catch(IOException e){if(i==0 || !(i==1?e.getMessage().contains("no normal return"):e.getMessage().contains("verification frame")))throw e;}
        }
        Path tmp=Files.createTempFile("unity29-contract",".jar");
        try {
            try(var z=new JarOutputStream(Files.newOutputStream(tmp))){
                var graph=Map.of("test/Root",type("test/Root","java/lang/Object",true,true,false),
                    "test/Leaf",type("test/Leaf","java/lang/Object",true,false,false,"test/Root"));
                for(var e:graph.entrySet()){z.putNextEntry(new JarEntry(e.getKey()+".class"));z.write(e.getValue());z.closeEntry();}
            }
            try(var z=new JarFile(tmp.toFile())){
                var child=type("qa/Child","qa/Base",false,false,true,"test/Leaf");
                JarContract.check(z,List.of(child,type("qa/Base","java/lang/Object",false,true,false)),"test/");
                try {JarContract.check(z,List.of(child,type("qa/Base","java/lang/Object",false,false,false)),"test/");throw new AssertionError();}
                catch(IOException expected){if(!expected.getMessage().contains("entry"))throw expected;}
            }
        } finally {Files.deleteIfExists(tmp);}
        System.out.println("PASS XP preservation and override, joint optional branches, frame diagnostics, transitive interfaces and inherited implementations");
    }
}
