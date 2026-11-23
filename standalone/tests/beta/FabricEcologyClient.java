package qa.fabric;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.PigRenderer;
public class FabricEcologyClient implements ClientModInitializer {
    private boolean checked;
    @SuppressWarnings("unchecked")
    private void clientFallback(net.minecraft.client.Minecraft mc) {
        if (checked || mc.player == null || mc.level == null)
            return;
        try {
            Class<?> actions = Class.forName("mc.unity.core.Actions"),
                     kind = Class.forName("mc.unity.core.Actions$Kind"),
                     action = Class.forName("mc.unity.core.Actions$Action");
            var breaking = Enum.valueOf((Class)kind, "BREAK");
            if (!(boolean)actions.getMethod("active", kind).invoke(null, breaking))
                throw new AssertionError("Expected break listener");
            var player = mc.player;
            var pos = player.blockPosition();
            var state = FabricEcologyProbe.resistant.defaultBlockState();
            var tool = player.getMainHandItem();
            var result =
                actions
                    .getMethod("breaking", net.minecraft.world.item.ItemStack.class,
                               net.minecraft.world.level.block.state.BlockState.class,
                               net.minecraft.world.level.Level.class, net.minecraft.core.BlockPos.class,
                               net.minecraft.world.entity.player.Player.class)
                    .invoke(null, tool, state, mc.level, pos, player);
            if ((boolean)actions.getMethod("canBreak", action).invoke(null, result) !=
                tool.canDestroyBlock(state, mc.level, pos, player))
                throw new AssertionError("Changed vanilla break decision");
            var constructor = action.getDeclaredConstructor(kind);
            constructor.setAccessible(true);
            var decision = constructor.newInstance(breaking);
            action.getField("experience").set(decision, 9);
            int before = FabricEcologyProbe.Resistant.clientHarvests;
            actions
                .getMethod("harvested", net.minecraft.world.level.block.Block.class,
                           net.minecraft.world.level.Level.class,
                           net.minecraft.world.entity.player.Player.class, net.minecraft.core.BlockPos.class,
                           net.minecraft.world.level.block.state.BlockState.class,
                           net.minecraft.world.level.block.entity.BlockEntity.class,
                           net.minecraft.world.item.ItemStack.class, action)
                .invoke(null, FabricEcologyProbe.resistant, mc.level, player, pos, state, null, tool,
                        decision);
            if (FabricEcologyProbe.Resistant.clientHarvests != before + 1)
                throw new AssertionError("Vanilla harvest did not run once");
            checked = true;
            System.out.println("QA22 CLIENT ACTIONS vanillaBreak=true vanillaHarvest=1");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            this::clientFallback);
        FabricPhaseClient.bind();
        EntityRendererRegistry.register(FabricEcologyProbe.pig, PigRenderer::new);
    }
}
