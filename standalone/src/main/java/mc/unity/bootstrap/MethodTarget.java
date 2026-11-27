package mc.unity.bootstrap;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.DirectMethodHandleDesc;
import java.util.*;
import java.util.function.Predicate;

/** Resolve a callback through LambdaMetafactory and the game operation it performs. */
record MethodTarget(String origin,String description, Predicate<MethodModel> body) {
    static MethodTarget named(String name){ return new MethodTarget(name,name,null); }
    static MethodTarget callback(String factory,String call) {
        return new MethodTarget(factory,call,m->m.code().stream().flatMap(c->c.elementStream()).anyMatch(
            e->e instanceof InvokeInstruction i && i.name().equalsString(call)));
    }
    static MethodTarget fields(String factory,String... names) {
        return new MethodTarget(factory,String.join(",",names),m->{
            var missing=new HashSet<>(List.of(names));
            m.code().stream().flatMap(c->c.elementStream()).forEach(e->{
                if(e instanceof FieldInstruction i)missing.remove(i.owner().asInternalName()+"."+i.name().stringValue());
            });
            return missing.isEmpty();
        });
    }
    String resolve(ClassModel model) throws IOException {
        if(body==null)return origin;
        var handles=new HashSet<String>();
        for(var method:model.methods())if(method.methodName().equalsString(origin))
            method.code().stream().flatMap(c->c.elementStream()).forEach(e->{
                if(e instanceof InvokeDynamicInstruction indy && indy.bootstrapMethod().owner().displayName().equals("LambdaMetafactory")
                        && indy.bootstrapArgs().size()>1 && indy.bootstrapArgs().get(1) instanceof DirectMethodHandleDesc impl
                        && impl.owner().equals(model.thisClass().asSymbol()))
                    handles.add(impl.methodName()+impl.lookupDescriptor());
            });
        var choices=model.methods().stream().filter(m->handles.contains(m.methodName().stringValue()+m.methodType().stringValue()))
            .filter(body).toList();
        if(choices.size()!=1)throw new IOException("Expected one callback of "+model.thisClass().asInternalName()+"."+origin+", got "+choices.size());
        return choices.getFirst().methodName().stringValue();
    }
}
