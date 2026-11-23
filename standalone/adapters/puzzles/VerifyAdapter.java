import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.reflect.*;
import java.util.*;
import java.util.jar.*;
import mc.unity.adapter.neoforge.PuzzlesModules;
import mc.unity.core.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import fuzs.puzzleslib.common.api.core.v1.ModConstructor;
import fuzs.puzzleslib.common.api.core.v1.context.GameRegistriesContext;

class VerifyAdapter {
    record TestPacket(int value) implements CustomPacketPayload {
        static final Type<TestPacket> ID=new Type<>(Identifier.parse("qa:optional"));
        public Type<TestPacket> type(){return ID;}
    }
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    static Method lookup(String name,Class<?>...args)throws Exception{
        Method m=PlayNetwork.class.getDeclaredMethod(name,args);m.setAccessible(true);return m;
    }
    public static void main(String[] args)throws Exception {
        PlayNetwork.clear();
        PlayNetwork.register(TestPacket.ID.id(),"1",null,TestPacket.ID,null,(p,c)->{},true,true);
        check(PlayNetwork.manifest().isEmpty(),"Optional wire must not enter mandatory digest");
        var yes=new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var no=new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var offers=(Collection<?>)lookup("offers").invoke(null);
        check(offers.size()==1 && !PlayNetwork.hasChannel(yes,TestPacket.ID),"Premature optional channel");
        var agree=lookup("agree",Connection.class,Collection.class);
        agree.invoke(null,yes,offers);
        agree.invoke(null,no,List.of("different-version"));
        check(PlayNetwork.hasChannel(yes,TestPacket.ID) && !PlayNetwork.hasChannel(no,TestPacket.ID),"Per-connection optional intersection");
        PlayNetwork.clear();
        var unsupported=new ModConstructor(){public void onRegisterGameRegistries(GameRegistriesContext ctx){throw new AssertionError();}};
        Class<?> lifecycle=Class.forName("mc.unity.adapter.puzzles.PuzzlesLifecycle");
        Method require=lifecycle.getDeclaredMethod("requireSupported",Object.class,Class.class,Set.class);
        require.setAccessible(true);
        try {require.invoke(null,unsupported,ModConstructor.class,Set.of("onConstructMod","onCommonSetup"));throw new AssertionError("Missing callback silently accepted");}
        catch(InvocationTargetException e){check(e.getCause().toString().contains("onRegisterGameRegistries"),e.toString());}
        String cls="fuzs/puzzleslib/neoforge/impl/core/NeoForgeCommonProxy";
        try(var jar=new JarFile(args[0]); var stream=jar.getInputStream(jar.getJarEntry(cls+".class"))){
            var transformed=PuzzlesModules.transformer().apply(stream.readAllBytes());
            var method=ClassFile.of().parse(transformed).methods().stream().filter(m->m.methodName().equalsString("getMinecraftServer")).findFirst().orElseThrow();
            check(method.code().orElseThrow().elementStream().anyMatch(x->x instanceof InvokeInstruction i && i.name().equalsString("unavailable")),
                  "Deferred unavailable call needs its runtime guard");
        }
        System.out.println("PASS per-connection optional negotiation, unsupported constructor override rejection and deferred bytecode linkage guard");
    }
}
