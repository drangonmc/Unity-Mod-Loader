package net.minecraftforge.event.network;
import java.util.concurrent.CompletableFuture;
import mc.unity.core.PlayNetwork;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
public final class CustomPayloadEvent {
    public static final class Context {
        private final PlayNetwork.Context ctx;
        private boolean handled;
        public Context(PlayNetwork.Context ctx) {
            this.ctx = ctx;
        }
        public ServerPlayer getSender() {
            return ctx.sender();
        }
        public Connection getConnection() {
            return ctx.connection();
        }
        public CompletableFuture<Void> enqueueWork(Runnable r) {
            return ctx.enqueue(r);
        }
        public void setPacketHandled(boolean value) {
            handled = value;
        }
        public boolean getPacketHandled() {
            return handled;
        }
    }
}
