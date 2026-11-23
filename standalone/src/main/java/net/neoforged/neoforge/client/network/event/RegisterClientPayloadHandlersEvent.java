package net.neoforged.neoforge.client.network.event;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.*;
public class RegisterClientPayloadHandlersEvent
    extends net.neoforged.bus.api.Event implements net.neoforged.fml.event.IModBusEvent {
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> t,
                                                         IPayloadHandler<T> h) {
        register(t, HandlerThread.MAIN, h);
    }
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> t, HandlerThread thread,
                                                         IPayloadHandler<T> h) {
        PayloadRegistrar.configurationClientReceiver(t, thread, h);
    }
}
