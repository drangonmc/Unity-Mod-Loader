package qa.forgeconfig;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

@Mod("qa_forge_config")
public final class ForgeServerConfigProbe {
    public ForgeServerConfigProbe(FMLJavaModLoadingContext context) {
        var builder = new ForgeConfigSpec.Builder();
        var value = builder.defineInRange("limit", 9, 1, 99);
        context.registerConfig(ModConfig.Type.SERVER, builder.build());
        var bus = context.getModBusGroup();
        ForgeLootProbe.install(bus);
        net.minecraftforge.event.level.BlockEvent.BreakEvent.BUS.addListener(
            (java.util.function.Consumer<net.minecraftforge.event.level.BlockEvent.BreakEvent>)e -> {
                if (e.getLevel().isClientSide())
                    throw new AssertionError("Server-only break event fired on client");
            });
        ModConfigEvent.Loading.getBus(bus).addListener(e -> log("load", value.get()));
        ModConfigEvent.Reloading.getBus(bus).addListener(e -> log("reload", value.get()));
        ModConfigEvent.Unloading.getBus(bus).addListener(e -> log("unload", value.get()));
        AddReloadListenerEvent.BUS.addListener(e -> {
            ResourceManagerReloadListener listener = manager -> System.out.println("QA_RELOAD forge");
            e.addListener(listener);
        });
    }
    private static void log(String phase, int value) {
        System.out.println("QA_FORGE_CONFIG " + phase + " " + value + " " + Thread.currentThread().getName());
    }
}
