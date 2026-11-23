package net.fabricmc.fabric.api.client.rendering.v1;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.*;
public final class EntityRendererRegistry {
    public static <E extends Entity> void register(EntityType<? extends E> type,
                                                   EntityRendererProvider<E> provider) {
        mc.unity.client.Renderers.entity(type, provider);
    }
}
