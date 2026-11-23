package qa.worlds;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
public final class FabricMod implements ModInitializer {
    public static final ResourceKey<Registry<Integer>> TUNING =
        ResourceKey.createRegistryKey(ContentMod.id("tuning"));
    public void onInitialize() {
        DynamicRegistries.registerSynced(TUNING, Codec.INT);
        ServerLifecycleEvents.SERVER_STARTING.register(
            s -> System.out.println("QA14 starting " + s.getWorldData().getLevelName()));
        ServerLifecycleEvents.SERVER_STARTED.register(
            s
            -> System.out.println("QA14 started " + s.getWorldData().getLevelName() + " " +
                                  s.registryAccess().lookupOrThrow(TUNING).getValue(ContentMod.id("value"))));
        ServerLifecycleEvents.SERVER_STOPPING.register(
            s -> System.out.println("QA14 stopping " + s.getWorldData().getLevelName()));
        ServerLifecycleEvents.SERVER_STOPPED.register(
            s -> System.out.println("QA14 stopped " + s.getWorldData().getLevelName()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.LOAD.register(
            (s, l)
                -> System.out.println("QA14 level-load " + s.getWorldData().getLevelName() + " " +
                                      l.dimension().identifier()));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents.UNLOAD.register(
            (s, l)
                -> System.out.println("QA14 level-unload " + s.getWorldData().getLevelName() + " " +
                                      l.dimension().identifier()));
        UseBlockCallback.EVENT.register((p, w, h, hit) -> {
            if (w.getBlockState(hit.getBlockPos()).is(ContentMod.GRATE.get()) &&
                p.getItemInHand(h).is(net.minecraft.world.item.Items.STICK)) {
                System.out.println("QA14 use " + (w.isClientSide() ? "client" : "server"));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
