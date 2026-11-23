package net.minecraftforge.event;
import java.util.function.BooleanSupplier;
import mc.unity.adapter.forge.CommandBus;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.eventbus.api.event.RecordEvent;
public interface TickEvent {
    interface ServerTickEvent extends TickEvent {
        BooleanSupplier haveTimeSupplier();
        MinecraftServer server();
        default boolean haveTime() {
            return haveTimeSupplier().getAsBoolean();
        }
        record Pre(BooleanSupplier haveTimeSupplier, MinecraftServer server)
            implements RecordEvent, ServerTickEvent {
            public static final EventBus<Pre> BUS = new CommandBus<>();
        }
        record Post(BooleanSupplier haveTimeSupplier, MinecraftServer server)
            implements RecordEvent, ServerTickEvent {
            public static final EventBus<Post> BUS = new CommandBus<>();
        }
    }
    interface ClientTickEvent extends TickEvent {
        record Pre() implements RecordEvent, ClientTickEvent {
            public static final EventBus<Pre> BUS = new CommandBus<>();
            public static final Pre INSTANCE = new Pre();
        }
        record Post() implements RecordEvent, ClientTickEvent {
            public static final EventBus<Post> BUS = new CommandBus<>();
            public static final Post INSTANCE = new Post();
        }
    }
}
