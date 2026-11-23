package qa.fabric.audit;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class FabricWireAudit {
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At("HEAD"))
    private void
    observe(Packet<?> packet, ChannelFutureListener callback, boolean flush, CallbackInfo ci) {
        var c = (Connection)(Object)this;
        if (!c.isMemoryConnection())
            return;
        CustomPacketPayload p = packet instanceof ClientboundCustomPayloadPacket out  ? out.payload()
                                : packet instanceof ServerboundCustomPayloadPacket in ? in.payload()
                                                                                      : null;
        if (p == null)
            return;
        String id = p.type().id().toString();
        if (id.equals("unity:server_configs"))
            throw new AssertionError("Redundant SERVER config on memory connection");
        if (id.equals("qa_server_config:phase"))
            System.out.println("QA21 MEMORY WIRE " + packet.type());
    }
}
