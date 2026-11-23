package qa.events;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.lang.invoke.MethodHandles;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent.*;
import net.minecraftforge.event.entity.player.PlayerEvent.*;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.*;
@Mod("aa_events")
public final class ForgeEvents {
    private record Msg(int nonce) implements CustomPacketPayload {
        private static final Type<Msg> TYPE = new Type<>(Identifier.parse("aa_events:message"));
        public Type<Msg> type() {
            return TYPE;
        }
    }
    private record Ack(int nonce) implements CustomPacketPayload {
        private static final Type<Ack> TYPE = new Type<>(Identifier.parse("aa_events:ack"));
        public Type<Ack> type() {
            return TYPE;
        }
    }
    private static final Map<Integer, Set<String>> receipts = new HashMap<>();
    private static Channel<CustomPacketPayload> channel;
    private static boolean high, ready, clientSetup, clientChecked;
    private static long pre, post;
    private static final Set<String> joined = new TreeSet<>();
    public ForgeEvents(FMLJavaModLoadingContext context) {
        if (!qa.bridge.Bridge.ready() || !ModList.isLoaded("aa_bridge") || !ModList.isLoaded("zz_neo_before"))
            throw new IllegalStateException("Cross-platform dependency or discovery");
        BusGroup.DEFAULT.register(MethodHandles.lookup(), ForgeEvents.class);
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(e -> {
            clientSetup = true;
            System.out.println("UNITY_EARLY_SETUP");
        });
        var p = ChannelBuilder.named("aa_events:channel").networkProtocolVersion(1).payloadChannel().play();
        p.clientbound().addMain(Msg.TYPE, StreamCodec.composite(ByteBufCodecs.VAR_INT, Msg::nonce, Msg::new),
            (m, ctx) -> channel.send(new Ack(m.nonce()), PacketDistributor.SERVER.noArg()));
        channel = p.serverbound()
                      .addMain(Ack.TYPE, StreamCodec.composite(ByteBufCodecs.VAR_INT, Ack::nonce, Ack::new),
                          (m, ctx) -> {
                              String who = ctx.getSender().getGameProfile().name();
                              receipts.computeIfAbsent(m.nonce(), k -> new TreeSet<>()).add(who);
                              System.out.println("UNITY_ROUTE_ACK " + m.nonce() + " " + who);
                          })
                      .build();
    }
    @SubscribeEvent
    public static void about(ServerAboutToStartEvent e) {
        System.out.println("UNITY_ABOUT_TO_START");
    }
    @SubscribeEvent
    public static void starting(ServerStartingEvent e) {
        System.out.println("UNITY_STARTING");
    }
    @SubscribeEvent(priority = 64)
    public static void high(ServerStartedEvent e) {
        high = true;
    }
    @SubscribeEvent(priority = -64)
    public static void low(ServerStartedEvent e) {
        if (!high)
            throw new AssertionError("Priority");
        ready = true;
        System.out.println("UNITY_STARTED_ORDER");
    }
    @SubscribeEvent
    public static void pre(ServerTickEvent.Pre e) {
        pre++;
    }
    @SubscribeEvent
    public static void post(ServerTickEvent.Post e) {
        post++;
    }
    @SubscribeEvent
    public static void client(ClientTickEvent.Pre e) {
        if (!clientChecked) {
            try {
                if (!clientSetup
                    || !qa.plain.Plain.RendererWitness.class.isAssignableFrom(
                        Class.forName("com.mojang.blaze3d.vertex.PoseStack")))
                    throw new AssertionError("Renderer or setup");
                System.out.println("UNITY_RENDERER_TARGET");
                clientChecked = true;
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
    @SubscribeEvent
    public static void login(PlayerLoggedInEvent e) {
        joined.add(e.getEntity().getGameProfile().name());
        System.out.println("UNITY_LOGIN " + e.getEntity().getGameProfile().name());
    }
    @SubscribeEvent
    public static void logout(PlayerLoggedOutEvent e) {
        joined.remove(e.getEntity().getGameProfile().name());
        System.out.println("UNITY_LOGOUT " + e.getEntity().getGameProfile().name());
    }
    @Mod.EventBusSubscriber(modid = "aa_events", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Auto {
        @SubscribeEvent
        public static void onStart(ServerStartedEvent e) {
            System.out.println("UNITY_AUTO_SUBSCRIBER");
        }
    }
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        System.out.println("UNITY_STOPPING");
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        System.out.println("UNITY_STOPPED");
    }
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("unitywatch")
                .requires(s
                    -> s.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var l = p.level();
                    var who = l.getChunkSource()
                                  .chunkMap.getPlayers(l.getChunkAt(p.blockPosition()).getPos(), false)
                                  .stream()
                                  .map(x -> x.getGameProfile().name())
                                  .sorted()
                                  .toList();
                    c.getSource().sendSuccess(() -> Component.literal("WATCH " + who), false);
                    return 1;
                }));
        event.getDispatcher().register(Commands.literal("unityack")
                .requires(s
                    -> s.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("nonce", IntegerArgumentType.integer()).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "nonce");
                    c.getSource().sendSuccess(
                        () -> Component.literal("ACK " + n + " " + receipts.getOrDefault(n, Set.of())), false);
                    return 1;
                })));
        event.getDispatcher().register(Commands.literal("unityroute")
                .requires(s
                    -> s.permissions().hasPermission(
                        net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("nonce", IntegerArgumentType.integer()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    int n = IntegerArgumentType.getInteger(c, "nonce");
                    if (!ready || pre < 1 || post < 1 || !joined.contains(p.getGameProfile().name()))
                        throw new IllegalStateException("Missing lifecycle events");
                    switch (Math.floorMod(n, 3)) {
                        case 0 -> channel.send(new Msg(n), PacketDistributor.PLAYER.with(p));
                        case 1 -> channel.send(new Msg(n), PacketDistributor.ALL.noArg());
                        case 2 ->
                            channel.send(new Msg(n),
                                PacketDistributor.TRACKING_CHUNK.with(p.level().getChunkAt(p.blockPosition())));
                    }
                    return 1;
                })));
    }
}
