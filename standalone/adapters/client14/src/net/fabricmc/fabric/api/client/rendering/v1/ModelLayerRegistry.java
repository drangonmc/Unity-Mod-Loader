package net.fabricmc.fabric.api.client.rendering.v1;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;
public final class ModelLayerRegistry {
    public interface TexturedLayerDefinitionProvider {
        LayerDefinition createLayerDefinition();
    }
    public interface TexturedArmorModelSetProvider {
        ArmorModelSet<LayerDefinition> createArmorModelSet();
    }
    public static void registerModelLayer(ModelLayerLocation layer, TexturedLayerDefinitionProvider factory) {
        mc.unity.client.ModelLayers.register(layer, factory::createLayerDefinition);
    }
    public static void registerArmorModelLayers(ArmorModelSet<ModelLayerLocation> layers,
                                                TexturedArmorModelSetProvider factory) {
        mc.unity.client.ModelLayers.armor(layers, factory::createArmorModelSet);
    }
}
