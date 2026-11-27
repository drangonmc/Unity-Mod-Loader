package mc.unity.adapter.common;

import java.io.*;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassFile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Defer only executable links skipped along a normal return path. Headers, handlers and unconditional uses stay strict. */
public final class OptionalLinkage {
    private record Key(String jar,String name) {}
    private static final Map<Key,Set<String>> plans=new ConcurrentHashMap<>();
    private static final Set<Key> transformed=ConcurrentHashMap.newKeySet();
    private static final Set<Key> earlyCandidates=ConcurrentHashMap.newKeySet();
    private static volatile boolean scanned;
    private static final String TYPE="Unsupported platform type: ", MEMBER="Unsupported platform member: ";
    private static final boolean STRICT = switch (System.getProperty("mc.unity.abi","conditional")) {
        case "strict" -> true; case "conditional" -> false;
        default -> throw new IllegalArgumentException("mc.unity.abi must be strict or conditional");
    };
    public static void check(String jar,ClassModel model,byte[] bytes,PlatformAbi abi) throws IOException {
        check(jar,model,bytes,abi,reason->false);
    }
    public static void check(String jar,ClassModel model,byte[] bytes,PlatformAbi abi,
                             java.util.function.Predicate<String> dormant) throws IOException {
        var missing=new LinkedHashSet<String>();
        abi.verify(model, reason->{if(!dormant.test(reason))missing.add(reason);return true;});
        if(missing.isEmpty())return;
        if(STRICT)throw new IOException(missing.iterator().next());
        abi.headers(model,dormant);
        var node=new ClassNode();
        new ClassReader(bytes).accept(node,0);
        var found=new HashSet<String>();
        for(var method:node.methods) {
            for(var handler:method.tryCatchBlocks) if(missing.contains(TYPE+handler.type))throw new IOException(TYPE+handler.type);
            var code=method.instructions.toArray();
            var sites=new LinkedHashMap<String,BitSet>();
            for(int i=0;i<code.length;i++)
                for(String reason:references(code[i],missing))
                    sites.computeIfAbsent(reason,k->new BitSet()).set(i);
            if(sites.isEmpty())continue;
            var positions=new IdentityHashMap<AbstractInsnNode,Integer>();
            for(int i=0;i<code.length;i++)positions.put(code[i],i);
            for(var site:sites.entrySet()) {
                if(!canReturnWithout(code,positions,site.getValue()))throw new IOException(site.getKey());
                found.add(site.getKey());
            }
        }
        if(!found.containsAll(missing)) {missing.removeAll(found);throw new IOException(missing.iterator().next());}
        plans.put(new Key(jar,node.name), Set.copyOf(found));
    }
    /** A normal path must return without using this symbol anywhere in this method. */
    private static boolean canReturnWithout(AbstractInsnNode[] code,Map<AbstractInsnNode,Integer> positions,BitSet sites) {
        var pending=new ArrayDeque<Integer>();pending.add(0);
        var visited=new BitSet(code.length);
        while(!pending.isEmpty()) {
            int pos=pending.removeFirst();
            if(pos>=code.length || visited.get(pos) || sites.get(pos))continue;
            visited.set(pos);
            var ins=code[pos];int op=ins.getOpcode();
            if(op>=Opcodes.IRETURN && op<=Opcodes.RETURN)return true;
            if(op==Opcodes.ATHROW || op==Opcodes.RET || op==Opcodes.JSR)continue;
            for(var target:targets(ins))pending.add(positions.get(target));
            if(op!=Opcodes.GOTO && op!=Opcodes.TABLESWITCH && op!=Opcodes.LOOKUPSWITCH) pending.add(pos+1);
        }
        return false;
    }
    private static List<LabelNode> targets(AbstractInsnNode ins) {
        if(ins instanceof JumpInsnNode j)return List.of(j.label);
        if(ins instanceof TableSwitchInsnNode s){var t=new ArrayList<>(s.labels);t.add(s.dflt);return t;}
        if(ins instanceof LookupSwitchInsnNode s){var t=new ArrayList<>(s.labels);t.add(s.dflt);return t;}
        return List.of();
    }
    private static Set<String> references(AbstractInsnNode ins,Set<String> missing) {
        var refs=new LinkedHashSet<String>();
        if(ins instanceof FieldInsnNode f) member(refs,f.owner,f.name,f.desc);
        if(ins instanceof MethodInsnNode m) member(refs,m.owner,m.name,m.desc);
        if(ins instanceof TypeInsnNode t){refs.add(TYPE+t.desc);descriptor(refs,t.desc);}
        if(ins instanceof MultiANewArrayInsnNode t)descriptor(refs,t.desc);
        if(ins instanceof LdcInsnNode l)constant(refs,l.cst);
        if(ins instanceof InvokeDynamicInsnNode d){descriptor(refs,d.desc);constant(refs,d.bsm);for(var v:d.bsmArgs)constant(refs,v);}
        if(ins instanceof FrameNode f) {
            for(var list:Arrays.asList(f.local,f.stack)) if(list!=null)for(var v:list)if(v instanceof String s && missing.contains(TYPE+s))
                throw new IllegalStateException("Unresolved verification frame: "+s);
        }
        refs.retainAll(missing);
        return refs;
    }
    private static void member(Set<String> refs,String owner,String name,String desc) {
        refs.add(TYPE+owner);refs.add(MEMBER+owner+"."+name+desc);descriptor(refs,desc);
    }
    private static void descriptor(Set<String> refs,String d) {
        for(int i=0;(i=d.indexOf('L',i))>=0;){int end=d.indexOf(';',++i);if(end<0)return;refs.add(TYPE+d.substring(i,end));i=end+1;}
    }
    private static void constant(Set<String> refs,Object v) {
        if(v instanceof org.objectweb.asm.Type t)descriptor(refs,t.getDescriptor());
        if(v instanceof Handle h)member(refs,h.getOwner(),h.getName(),h.getDesc());
        if(v instanceof ConstantDynamic d) {
            descriptor(refs,d.getDescriptor());constant(refs,d.getBootstrapMethod());
            for(int i=0;i<d.getBootstrapMethodArgumentCount();i++)constant(refs,d.getBootstrapMethodArgument(i));
        }
    }
    public static BiFunction<String,byte[],byte[]> transformer(){return OptionalLinkage::guard;}
    private static byte[] guard(String jar,byte[] bytes) {
        if(STRICT)return bytes;
        var reader=new ClassReader(bytes);
        var key=new Key(jar,reader.getClassName());
        if(!scanned && !plans.containsKey(key) && earlyCandidates.add(key)) {
            try {check(jar,ClassFile.of().parse(bytes),bytes,new PlatformAbi());}
            catch(IOException ignored){ /* Eager links are diagnosed by complete descriptor preflight. */ }
        }
        var missing=plans.get(key);
        if(missing==null)return bytes;
        var node=new ClassNode();reader.accept(node,0);
        for(var method:node.methods) for(var ins:method.instructions.toArray()) {
            var uses=references(ins,missing);if(uses.isEmpty())continue;
            var guard=new InsnList();
            guard.add(new LdcInsnNode(jar+" "+node.name+"."+method.name+": "+String.join(", ",uses)));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mc/unity/adapter/common/OptionalLinkage","unavailable","(Ljava/lang/String;)V",false));
            method.instructions.insertBefore(ins,guard);
        }
        var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);node.accept(writer);var result=writer.toByteArray();transformed.add(key);return result;
    }
    public static boolean transformed(String jar,String name){return transformed.contains(new Key(jar,name));}
    public static void finishPreflight(){scanned=true;earlyCandidates.clear();}
    public static void clear(){plans.clear();transformed.clear();earlyCandidates.clear();scanned=false;}
    public static void unavailable(String where){throw new UnsupportedOperationException("Unity 可选平台 API 未实现: "+where);}
}
