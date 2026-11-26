package qa.neoguard;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerLifecycleEvent;

@Mod("qa_neo_guard")
public class NeoSideGuard {
    public NeoSideGuard() {
        if (FMLEnvironment.getDist() == Dist.CLIENT)
            ClientOnly.start();
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvent.class, e -> {
            if (e.getServer() == null)
                throw new AssertionError("Missing server");
            System.out.println("QA24 NEO LIFECYCLE " + e.getClass().getSimpleName());
        });
        System.out.println("QA24 NEO GUARD " + FMLEnvironment.getDist().name());
    }
    @OnlyIn(Dist.CLIENT)
    public static class ClientOnly {
        static void start() {
            System.out.println("QA24 NEO CLIENT BRANCH " +
                               net.minecraft.client.Minecraft.class.getSimpleName());
        }
    }
}
