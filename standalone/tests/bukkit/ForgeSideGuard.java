package qa.forgeguard;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.*;

@Mod("qa_forge_guard")
public class ForgeSideGuard {
    public ForgeSideGuard() {
        if (FMLEnvironment.dist.isClient())
            ClientOnly.start();
        System.out.println("QA24 FORGE GUARD " + FMLEnvironment.dist.name());
    }
    @OnlyIn(Dist.CLIENT)
    public static class ClientOnly {
        static void start() {
            System.out.println("QA24 FORGE CLIENT BRANCH " +
                               net.minecraft.client.Minecraft.class.getSimpleName());
        }
    }
}
