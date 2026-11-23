package qa.config;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.network.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.*;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class NeoPhase {
    private static final Identifier ID = Identifier.parse("qa_server_config:phase");
    static final CustomPacketPayload.Type<Question> QUESTION = new CustomPacketPayload.Type<>(ID);
    static final CustomPacketPayload.Type<Answer> ANSWER = new CustomPacketPayload.Type<>(ID);
    static final CustomPacketPayload.Type<OptionalMessage> OPTIONAL =
        new CustomPacketPayload.Type<>(Identifier.parse("qa_server_config:optional_phase"));
    static final StreamCodec<FriendlyByteBuf, Question> QUESTIONS =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, Question::nonce, Question::new);
    static final StreamCodec<FriendlyByteBuf, Answer> ANSWERS =
        StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Answer::text, Answer::new);
    static final StreamCodec<FriendlyByteBuf, OptionalMessage> OPT = StreamCodec.unit(new OptionalMessage());
    private record Question(int nonce) implements CustomPacketPayload {
        public Type<Question> type() {
            return QUESTION;
        }
    }
    private record Answer(String text) implements CustomPacketPayload {
        public Type<Answer> type() {
            return ANSWER;
        }
    }
    private record OptionalMessage() implements CustomPacketPayload {
        public Type<OptionalMessage> type() {
            return OPTIONAL;
        }
    }
    private record PlayText(String value) implements CustomPacketPayload {
        static final Type<PlayText> IDENTITY = new Type<>(ID);
        public Type<PlayText> type() {
            return IDENTITY;
        }
    }
    public static void play(net.minecraft.server.level.ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            player, new PlayText("play-" + player.getGameProfile().name()));
    }
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type("qa_server_config:rules");
    private static final AtomicInteger serial = new AtomicInteger(7300);
    private static final Map<Connection, Integer> expected = Collections.synchronizedMap(new WeakHashMap<>());
    public static void bind(IEventBus bus, Dist side) {
        LoginCapacity.bind();
        bus.addListener(RegisterPayloadHandlersEvent.class, e -> {
            var reg = e.registrar(side.isClient() && java.nio.file.Files.isRegularFile(
                                                         java.nio.file.Path.of("config/qa21-negative"))
                                      ? "mismatch"
                                      : "1");
            e.registrar("play-1").playToClient(
                PlayText.IDENTITY,
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PlayText::value, PlayText::new),
                (p, ctx) -> {
                    if (ctx.protocol() != ConnectionProtocol.PLAY)
                        throw new IllegalStateException("Wrong play codec");
                    System.out.println("QA21 PLAY " + p.value());
                });
            reg.configurationToClient(QUESTION, QUESTIONS);
            reg.executesOn(HandlerThread.NETWORK).configurationToServer(ANSWER, ANSWERS, (answer, ctx) -> {
                if (ctx.protocol() != ConnectionProtocol.CONFIGURATION ||
                    ctx.flow() != PacketFlow.SERVERBOUND)
                    throw new IllegalStateException("Wrong packet phase");
                var listener = ctx.listener();
                Integer nonce = expected.remove(ctx.connection());
                if (nonce == null || !answer.text().equals("neo-" + nonce))
                    throw new IllegalStateException("Wrong connection's rule data");
                String ingress = Thread.currentThread().getName();
                ctx.enqueueWork(() -> {
                    System.out.println(
                        "QA21 NEO ACK " + nonce + " input=" + ingress +
                        " complete=" + Thread.currentThread().getName() +
                        " local=" + ctx.connection().isMemoryConnection() + " player=" +
                        ((net.minecraft.server.network.ServerConfigurationPacketListenerImpl)listener)
                            .getOwner()
                            .name());
                    ctx.finishCurrentTask(TASK);
                });
            });
            reg.optional()
                .versioned(side.isClient() ? "client-version" : "server-version")
                .configurationToClient(OPTIONAL, OPT, (p, ctx) -> {});
        });
        bus.addListener(RegisterConfigurationTasksEvent.class, e -> {
            var peer = (net.neoforged.neoforge.common.extensions.ICommonPacketListener)e.getListener();
            if (!peer.hasChannel(QUESTION) ||
                (!peer.getConnection().isMemoryConnection() && peer.hasChannel(OPTIONAL)))
                throw new IllegalStateException("Negotiation mismatch");
            e.register(new ICustomConfigurationTask() {
                public ConfigurationTask.Type type() {
                    return TASK;
                }
                public void run(Consumer<CustomPacketPayload> send) {
                    int n = serial.incrementAndGet();
                    expected.put(peer.getConnection(), n);
                    System.out.println("QA21 NEO OFFER " + n);
                    send.accept(new Question(n));
                }
            });
        });
        if (side.isClient())
            Client.bind(bus);
    }
    private static final class Client {
        static void bind(IEventBus bus) {
            bus.addListener(
                net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent.class,
                e -> e.register(QUESTION, (question, ctx) -> {
                    if (ctx.protocol() != ConnectionProtocol.CONFIGURATION ||
                        ctx.flow() != PacketFlow.CLIENTBOUND)
                        throw new IllegalStateException("Client phase");
                    System.out.println("QA21 NEO CLIENT " + question.nonce() + " " +
                                       Thread.currentThread().getName());
                    if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of("config/qa22-abort"))) {
                        System.out.println("QA22 ABORT before task completion");
                        ctx.disconnect(net.minecraft.network.chat.Component.literal(
                            "QA22 deliberate configuration disconnect"));
                        return;
                    }
                    ctx.reply(new Answer("neo-" + question.nonce()));
                }));
        }
    }
}
