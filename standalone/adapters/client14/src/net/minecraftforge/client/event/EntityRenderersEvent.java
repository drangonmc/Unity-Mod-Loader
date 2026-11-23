package net.minecraftforge.client.event;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.entity.*;
import mc.unity.adapter.forge.CommandBus;
import net.minecraftforge.eventbus.api.bus.EventBus;
public interface EntityRenderersEvent {
    record RegisterRenderers() implements EntityRenderersEvent,
                                          net.minecraftforge.eventbus.api.event.RecordEvent,
                                          net.minecraftforge.eventbus.api.event.characteristic.SelfDestructing {
        public static final EventBus<RegisterRenderers> BUS = new CommandBus<>();
        public static void fire() {
            try {
                ((CommandBus<RegisterRenderers>)BUS).dispatch(new RegisterRenderers());
            } finally {
                clear();
            }
        }
        public static void clear() { ((CommandBus<?>)BUS).clear(); }
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
    record RegisterLayerDefinitions() implements EntityRenderersEvent, net.minecraftforge.eventbus.api.event.RecordEvent, net.minecraftforge.eventbus.api.event.characteristic.SelfDestructing {
        public static final EventBus<RegisterLayerDefinitions> BUS=new CommandBus<>();
        public static void fire() { try { ((CommandBus<RegisterLayerDefinitions>)BUS).dispatch(new RegisterLayerDefinitions()); } finally {clear();} }
        public static void clear() { ((CommandBus<?>)BUS).clear(); }
        public void registerLayerDefinition(net.minecraft.client.model.geom.ModelLayerLocation id,
                java.util.function.Supplier<net.minecraft.client.model.geom.builders.LayerDefinition> factory) {
            mc.unity.client.ModelLayers.register(id,factory);
        }
    }
}
