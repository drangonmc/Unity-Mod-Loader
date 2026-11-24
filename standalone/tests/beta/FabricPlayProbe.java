package qa.fabric;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

final class FabricPlayProbe {
    static final Identifier ID = Identifier.parse("qa_fabric_ecology:play_echo");
    record Request(int value) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(ID);
        public Type<Request> type() {
            return TYPE;
        }
    }
    record Answer(String value) implements CustomPacketPayload {
        static final Type<Answer> TYPE = new Type<>(ID);
        public Type<Answer> type() {
            return TYPE;
        }
    }
    static void bind() {
        PayloadTypeRegistry.serverboundPlay().register(
            Request.TYPE, StreamCodec.composite(ByteBufCodecs.VAR_INT, Request::value, Request::new));
        PayloadTypeRegistry.clientboundPlay().register(
            Answer.TYPE, StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Answer::value, Answer::new));
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (r, ctx) -> {
            if (!ctx.server().isSameThread() || !ServerPlayNetworking.canSend(ctx.player(), Answer.TYPE))
                throw new AssertionError("Play callback ownership or remote receiver");
            String response = ctx.player().getUUID() + "/" + r.value();
            ctx.responseSender().sendPacket(new Answer(response));
            System.out.println("QA23 PLAY SERVER " + response);
        });
    }
}
