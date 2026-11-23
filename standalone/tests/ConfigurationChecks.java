import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.electronwill.nightconfig.core.*;
import net.neoforged.fml.config.*;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import mc.unity.core.PlayNetwork;
import mc.unity.core.Attachments;
import com.mojang.serialization.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import io.netty.buffer.Unpooled;
import mc.unity.adapter.common.AccessTransforms;
import mc.unity.core.ConfigurationNetwork;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.*;
import net.minecraft.resources.Identifier;

class ConfigurationChecks {
    static final Identifier ID = Identifier.parse("probe:rules");
    record Num(int n) implements CustomPacketPayload {
        static final Type<Num> TYPE = new Type<>(ID);
        public Type<Num> type() { return TYPE; }
    }
    record Word(String s) implements CustomPacketPayload {
        static final Type<Word> TYPE = new Type<>(ID);
        public Type<Word> type() { return TYPE; }
    }
    static void check(boolean b, String reason) {
        if (!b)
            throw new AssertionError(reason);
    }
    static void reject(Runnable r, String reason) {
        try {
            r.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError(reason);
    }
    @SuppressWarnings("unchecked")
    static void packets() {
        ConfigurationNetwork.clear();
        var num = StreamCodec.composite(ByteBufCodecs.VAR_INT, Num::n, Num::new);
        var word = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Word::s, Word::new);
        ConfigurationNetwork.type(PacketFlow.CLIENTBOUND, Num.TYPE, num, "1", false);
        ConfigurationNetwork.type(PacketFlow.SERVERBOUND, Word.TYPE, word, "1", false);
        reject(()
                   -> ConfigurationNetwork.type(PacketFlow.CLIENTBOUND, Num.TYPE, num, "1", false),
               "duplicate phase codec");
        StreamCodec<FriendlyByteBuf, ClientboundCustomPayloadPacket> out =
            ConfigurationNetwork.clientbound(ClientboundCustomPayloadPacket.CONFIG_STREAM_CODEC);
        StreamCodec<FriendlyByteBuf, ServerboundCustomPayloadPacket> in =
            ConfigurationNetwork.serverbound(ServerboundCustomPayloadPacket.STREAM_CODEC);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            out.encode(buf, new ClientboundCustomPayloadPacket(new Num(7123)));
            check(out.decode(buf).payload().equals(new Num(7123)), "clientbound");
            buf.clear();
            in.encode(buf, new ServerboundCustomPayloadPacket(new Word("ack")));
            check(in.decode(buf).payload().equals(new Word("ack")), "serverbound");
            buf.clear();
            var brand = new BrandPayload("stock");
            out.encode(buf, new ClientboundCustomPayloadPacket(brand));
            check(out.decode(buf).payload().equals(brand), "vanilla packet");
            buf.clear();
            out.encode(buf, new ClientboundCustomPayloadPacket(new Num(1)));
            buf.writeByte(0);
            reject(() -> out.decode(buf), "trailing data");
            buf.clear();
            buf.writeIdentifier(ID);
            buf.writeZero(32768);
            reject(() -> in.decode(buf), "configuration serverbound limit");
            var other = new CustomPacketPayload.Type<Num>(Identifier.parse("probe:oneway"));
            ConfigurationNetwork.type(PacketFlow.CLIENTBOUND, other, num, "1", false);
            buf.clear();
            buf.writeIdentifier(other.id());
            buf.writeVarInt(1);
            reject(() -> in.decode(buf), "wrong phase direction");
            check(ConfigurationNetwork.manifest().size() == 3, "mandatory channels");
            reject(()
                       -> ConfigurationNetwork.type(PacketFlow.SERVERBOUND, other, num, "1", false),
                   "late required registration");
        } finally {
            buf.release();
            ConfigurationNetwork.clear();
        }
    }
    static void signatures(Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("signature-classes")),
             pkg = Files.createDirectories(dir.resolve("checks"));
        var sources = Map.of("Carrier", "public abstract class Carrier<T> extends java.util.ArrayList<T> implements java.util.function.Supplier<T> {}", "Outer",
                             "public class Outer {public interface Inner<X> {} }", "Dollar$Name",
                             "public interface Dollar$Name {}");
        var args = new ArrayList<String>(List.of("--release", "25", "-d", dir.toString()));
        for (var e : sources.entrySet()) {
            var f = pkg.resolve(e.getKey() + ".java");
            Files.writeString(f, "package checks; " + e.getValue());
            args.add(f.toString());
        }
        check(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[] ::new)) == 0,
              "compile signature fixture");
        var jar = dir.resolve("interfaces.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (var e : Map.of("fabric.mod.json",
                                "{\"schemaVersion\":1,\"id\":\"interfaces\",\"version\":\"1\"," +
                                "\"accessWidener\":\"interfaces.aw\"}",
                                "interfaces.aw",
                                "classTweaker v1 named\ninject-interface checks/Carrier " +
                                "checks/Outer$Inner\ninject-interface checks/Carrier checks/Dollar$Name\n")
                             .entrySet()) {
                out.putNextEntry(new JarEntry(e.getKey()));
                out.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        try (var cl = new URLClassLoader(new URL[] {dir.toUri().toURL()},
                                         ConfigurationChecks.class.getClassLoader());
             var z = new JarFile(jar.toFile())) {
            var bytes = AccessTransforms.prepare(List.of(z), cl, false).get("checks/Carrier");
            check(bytes != null, "injection missing");
            Files.write(pkg.resolve("Carrier.class"), bytes);
            var type = cl.loadClass("checks.Carrier");
            check(((ParameterizedType)type.getGenericSuperclass())
                      .getActualTypeArguments()[0]
                      .equals(type.getTypeParameters()[0]),
                  "generic superclass changed");
            var interfaces=List.of(type.getGenericInterfaces());
            var original=(ParameterizedType)interfaces.getFirst();
            check(original.getRawType()==java.util.function.Supplier.class && original.getActualTypeArguments()[0].equals(type.getTypeParameters()[0]),"generic interface changed");
            check(Set.copyOf(interfaces.subList(1,interfaces.size())).equals(Set.of(cl.loadClass("checks.Outer$Inner"),cl.loadClass("checks.Dollar$Name"))),"raw interface reflection");
        }
    }
    static void commonRegistration(){
        ConfigurationNetwork.clear();PlayNetwork.clear();
        var reg=new PayloadRegistrar("3");
        var codec=StreamCodec.composite(ByteBufCodecs.VAR_INT,Num::n,Num::new);
        reg.commonBidirectional(Num.TYPE,codec,(p,ctx)->{});
        new RegisterClientPayloadHandlersEvent().register(Num.TYPE,(p,ctx)->{});
        check(ConfigurationNetwork.received(null,PacketFlow.SERVERBOUND).contains(ID)
            && ConfigurationNetwork.received(null,PacketFlow.CLIENTBOUND).contains(ID),"common receiver directions");
        check(ConfigurationNetwork.manifest().size()==2 && PlayNetwork.manifest().size()==1,"separate common phase manifests");
        PayloadRegistrar.clearClients();PlayNetwork.clear();ConfigurationNetwork.clear();
    }
    static void configRace(Path root)throws Exception{
        var entered=new CountDownLatch(1);var proceed=new CountDownLatch(1);var cleaning=new CountDownLatch(1);
        var error=new AtomicReference<Throwable>();var events=new CopyOnWriteArrayList<String>();
        var spec=new IConfigSpec(){
            public boolean isEmpty(){return false;}
            public void validateSpec(ModConfig cfg){}
            public boolean isCorrect(UnmodifiableCommentedConfig doc){return doc.get("value") instanceof Integer;}
            public void correct(CommentedConfig doc){doc.set("value",1);}
            public void acceptConfig(ILoadedConfig value){
                if(value==null)return;
                entered.countDown();
                try{if(!proceed.await(10,TimeUnit.SECONDS))throw new AssertionError("Missing release");}
                catch(InterruptedException e){throw new AssertionError(e);}
            }
        };
        class TestConnection extends Connection{
            TestConnection(){super(PacketFlow.CLIENTBOUND);}
            public boolean isConnected(){return true;}
            public boolean isMemoryConnection(){return false;}
        }
        var first=new TestConnection();var second=new TestConnection();
        ModConfigs.bind(root,true);
        try{
            ModConfigs.register("race",ModConfig.Type.SERVER,spec,"rules.toml",event->events.add(event.getClass().getSimpleName()));
            ModConfigs.loadCommon();
            var cfg=ModConfigs.getConfigSet(ModConfig.Type.SERVER).iterator().next();
            FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());
            CustomPacketPayload snapshot;
            try{
                buf.writeIdentifier(Identifier.parse("unity:server_configs"));buf.writeVarInt(1);buf.writeUtf("race/rules.toml");buf.writeUtf("value=19");
                snapshot=ConfigSync.configurationCodec(ClientboundCustomPayloadPacket.CONFIG_STREAM_CODEC).decode(buf).payload();
            }finally{buf.release();}
            var receive=Thread.ofPlatform().name("configuration-receive").start(()->{
                try{ConfigSync.receive(snapshot,first);}catch(Throwable t){error.set(t);}
            });
            check(entered.await(5,TimeUnit.SECONDS),"Config update did not start");
            var disconnect=Thread.ofPlatform().name("configuration-disconnect").start(()->{
                cleaning.countDown();try{ConfigSync.disconnected(first);}catch(Throwable t){error.set(t);}
            });
            try{
                check(cleaning.await(5,TimeUnit.SECONDS),"Disconnect did not start");
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
                while(disconnect.isAlive() && disconnect.getState()!=Thread.State.BLOCKED && System.nanoTime()<until)Thread.sleep(1);
                check(disconnect.getState()==Thread.State.BLOCKED,"Disconnect raced an unfinished config update");
            }finally{proceed.countDown();}
            receive.join(5000);disconnect.join(5000);
            check(!receive.isAlive() && !disconnect.isAlive() && error.get()==null && cfg.getLoadedConfig()==null &&
                  events.equals(List.of("Loading","Unloading")),"Callbacks or handle leaked across disconnect");
            ConfigSync.receive(snapshot,second);ConfigSync.disconnected(first);
            check(cfg.getLoadedConfig()!=null && cfg.getLoadedConfig().config().getInt("value")==19,"Stale disconnect unloaded new session");
            ConfigSync.disconnected(second);
        }finally{proceed.countDown();ModConfigs.close();PlayNetwork.clear();}
        System.out.println("PASS config receive/disconnect ordering and stale-connection cleanup");
    }
    private record TestKey(Identifier id,Codec<Integer> codec) implements Attachments.Key<Integer> {}
    @SuppressWarnings("unchecked")
    static void damagedValues() throws Exception {
        var good=new TestKey(Identifier.parse("checks:int"),Codec.INT);
        var old=new TestKey(Identifier.parse("checks:outdated"),Codec.INT);
        var broken=new TestKey(Identifier.parse("checks:throwing"),Codec.INT.xmap(n->{
            if(n<0)throw new IllegalArgumentException("old negative field");return n;
        },n->n));
        var field=Attachments.class.getDeclaredField("CODEC");field.setAccessible(true);
        var codec=(Codec<Attachments>)field.get(null);
        Attachments.register(good);Attachments.register(old);Attachments.register(broken);
        try{
            Map<Object,Object> input=new LinkedHashMap<>();
            input.put(13,"invalid key");input.put(good.id().toString(),47);
            input.put(old.id().toString(),"keep");input.put(broken.id().toString(),-17);
            var state=codec.parse(JavaOps.INSTANCE,input).getOrThrow();
            Attachments.Owner owner=create->state;
            check(Attachments.find(owner,good)==47 && Attachments.find(owner,old)==null && Attachments.find(owner,broken)==null,"damage isolated per key");
            Attachments.initialize(owner,old,1);Attachments.initialize(owner,broken,2);
            var preserved=(Map<?,?>)codec.encodeStart(JavaOps.INSTANCE,state).getOrThrow();
            check(preserved.equals(Map.of("checks:int",47,"checks:outdated","keep","checks:throwing",-17)),"Quarantined raw values changed");
            Attachments.set(owner,old,5);Attachments.set(owner,broken,null);
            var repaired=codec.encodeStart(JavaOps.INSTANCE,state).getOrThrow();
            check(repaired.equals(Map.of("checks:int",47,"checks:outdated",5)),"Explicit writes must supersede raw values");
            check(codec.parse(JavaOps.INSTANCE,List.of()).error().isPresent(),"Non-map needs a DataResult error");
        }finally{var clear=Attachments.class.getDeclaredMethod("clear");clear.setAccessible(true);clear.invoke(null);}
        System.out.println("PASS corrupt attachment keys, failed and throwing mod codecs, retained NBT and explicit replacement");
    }
    public static void main(String[] args) throws Exception {
        packets();
        signatures(Path.of(args[0]));
        commonRegistration();configRace(Path.of(args[0]));damagedValues();
        System.out.println("PASS configuration codecs, frame boundaries, vanilla fallback and " +
                           "nested/dollar generic interface reflection");
    }
}
