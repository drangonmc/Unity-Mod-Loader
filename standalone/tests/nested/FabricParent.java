package qa.fabriccontent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import qa.inner.Secret;
public final class FabricParent implements ModInitializer {
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("qa_inner")
            || !FabricLoader.getInstance().isModLoaded("fabric-lifecycle-events-v1")
            || FabricLoader.getInstance()
                .getModContainer("qa_inner")
                .orElseThrow()
                .findPath("qa/inner/Secret.class")
                .isEmpty())
            throw new IllegalStateException("Missing nested Fabric metadata");
        if (Secret.answer() != 41)
            throw new IllegalStateException("Nested Fabric access");
        Secret.value = 42;
        CommandRegistrationCallback.EVENT.register(
            (d, c, e) -> d.register(Commands.literal("unityawcheck").executes(ctx -> {
                System.out.println("QA_ACCESS_FABRIC " + Secret.answer());
                return Secret.answer();
            })));
    }
}
