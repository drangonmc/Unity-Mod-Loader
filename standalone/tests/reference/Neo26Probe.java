package qa.neo26;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.*;
import net.neoforged.fml.common.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("unity_neo26_probe")
public final class Neo26Probe {
    public interface Extension extends IExtensionPoint { String marker(); }

    public Neo26Probe(ModContainer container) {
        if (!ModLoadingContext.get().getActiveNamespace().equals(container.getModId())) throw new AssertionError();
        ModLoadingContext.get().registerExtensionPoint(Extension.class, () -> () -> "registered");
        if (!container.getCustomExtension(Extension.class).orElseThrow().marker().equals("registered"))
            throw new AssertionError("extension lookup");
    }

    @EventBusSubscriber(modid="unity_neo26_probe",value=Dist.DEDICATED_SERVER)
    public static final class Ticks {
        private static int phase;
        private static final Temporary transientListener = new Temporary();
        @SubscribeEvent(priority=EventPriority.HIGHEST)
        public static void first(ServerTickEvent.Pre event) {
            if (phase != 0) return;
            if (!event.getServer().isSameThread()) throw new AssertionError("tick thread");
            event.hasTime();
            NeoForge.EVENT_BUS.register(transientListener);
            phase = 1;
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void last(ServerTickEvent.Pre event) {
            if (phase == 1) phase = 2;
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public static void after(ServerTickEvent.Post event) {
            if (phase == 2 && transientListener.calls == 1) {
                NeoForge.EVENT_BUS.unregister(transientListener);
                phase = 3;
            } else if (phase == 3) {
                if (transientListener.calls != 1) throw new AssertionError("unregister");
                phase = 4;
                System.out.println("UNITY_NEO26_TICKS_OK extension priority thread unregister");
            }
        }
    }
    public static final class Temporary {
        int calls;
        @SubscribeEvent(priority=EventPriority.HIGHEST)
        public void tick(ServerTickEvent.Post event) { calls++; }
    }
}
