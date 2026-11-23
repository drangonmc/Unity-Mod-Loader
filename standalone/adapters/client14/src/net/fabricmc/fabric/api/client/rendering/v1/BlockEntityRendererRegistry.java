package net.fabricmc.fabric.api.client.rendering.v1;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.*;
public final class BlockEntityRendererRegistry {
    public static <E extends BlockEntity, S extends BlockEntityRenderState> void register(
        BlockEntityType<E> type, BlockEntityRendererProvider<? super E, ? super S> provider) {
        mc.unity.client.Renderers.block(type, provider);
    }
}
