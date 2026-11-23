package net.neoforged.neoforge.client.event;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.entity.*;
public abstract class EntityRenderersEvent
    extends net.neoforged.bus.api.Event implements net.neoforged.fml.event.IModBusEvent {
    public static class RegisterRenderers extends EntityRenderersEvent {
        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                                                              EntityRendererProvider<T> factory) {
            mc.unity.client.Renderers.entity(type, factory);
        }
        public <T extends BlockEntity, S extends BlockEntityRenderState> void
        registerBlockEntityRenderer(BlockEntityType<? extends T> type,
                                    BlockEntityRendererProvider<T, S> factory) {
            mc.unity.client.Renderers.block(type, factory);
        }
    }
    public static class RegisterLayerDefinitions extends EntityRenderersEvent {
        public void registerLayerDefinition(net.minecraft.client.model.geom.ModelLayerLocation id,
                java.util.function.Supplier<net.minecraft.client.model.geom.builders.LayerDefinition> factory) {
            mc.unity.client.ModelLayers.register(id,factory);
        }
    }
}
