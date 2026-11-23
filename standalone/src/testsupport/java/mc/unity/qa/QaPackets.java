package mc.unity.qa;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.network.*;

/** Test-only independent S2C oracle: report slot zero of the requesting player's actual open menu. */
public final class QaPackets {
    record Ask(int nonce) implements CustomPacketPayload {
        static final Type<Ask> ID =
            new Type<>(Identifier.fromNamespaceAndPath("unity_graphical_qa", "inspect_open_menu"));
        public Type<Ask> type() {
            return ID;
        }
    }
    public record Answer(int nonce, String stack, int count) implements CustomPacketPayload {
        static final Type<Answer> ID =
            new Type<>(Identifier.fromNamespaceAndPath("unity_graphical_qa", "open_menu_slot"));
        public Type<Answer> type() {
            return ID;
        }
    }
    public static volatile Answer last;
    private static Channel<CustomPacketPayload> channel;
    public static void install() {
        var connection =
            ChannelBuilder.named("unity_graphical_qa:probe").networkProtocolVersion(1).payloadChannel();
        var play = connection.play();
        StreamCodec<RegistryFriendlyByteBuf, Ask> ask =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, Ask::nonce, Ask::new);
        StreamCodec<RegistryFriendlyByteBuf, Answer> reply =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, Answer::nonce, ByteBufCodecs.stringUtf8(256),
                Answer::stack, ByteBufCodecs.VAR_INT, Answer::count, Answer::new);
        play.serverbound().addMain(Ask.ID, ask, (msg, ctx) -> {
            var player = ctx.getSender();
            var menu = player.containerMenu;
            if (menu == player.inventoryMenu || menu.slots.isEmpty())
                return;
            var item = menu.getSlot(0).getItem();
            channel.send(new Answer(msg.nonce(), BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                             item.getCount()),
                ctx.getConnection());
        });
        channel = play.clientbound().addMain(Answer.ID, reply, (msg, ctx) -> last = msg).build();
    }
    public static void ask(int nonce) {
        last = null;
        channel.send(new Ask(nonce), PacketDistributor.SERVER.noArg());
    }
}
