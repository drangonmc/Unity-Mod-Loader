package mc.unity.client;

import com.google.common.collect.ImmutableMap;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ArmorModelSet;

/** Suppliers are registered once and called for every resource reload. */
public final class ModelLayers {
    private static final Set<ModelLayerLocation> locations = new LinkedHashSet<>();
    private static final List<Consumer<ImmutableMap.Builder<ModelLayerLocation, LayerDefinition>>> factories =
        new ArrayList<>();
    public static synchronized void register(ModelLayerLocation layer, Supplier<LayerDefinition> factory) {
        Objects.requireNonNull(factory);
        if (!locations.add(Objects.requireNonNull(layer)))
            throw new IllegalArgumentException("Duplicate model layer " + layer);
        factories.add(map -> map.put(layer, factory.get()));
    }
    public static synchronized void armor(ArmorModelSet<ModelLayerLocation> layers,
                                          Supplier<ArmorModelSet<LayerDefinition>> factory) {
        Objects.requireNonNull(factory);
        var ids = Set.of(layers.head(), layers.chest(), layers.legs(), layers.feet());
        if (!Collections.disjoint(ids, locations))
            throw new IllegalArgumentException("Duplicate armor layers " + layers);
        locations.addAll(ids);
        factories.add(map -> layers.putFrom(factory.get(), map));
    }
    public static synchronized Stream<ModelLayerLocation> known(Stream<ModelLayerLocation> vanilla) {
        return locations.isEmpty() ? vanilla : Stream.concat(vanilla, List.copyOf(locations).stream());
    }
    public static synchronized void bake(ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> map) {
        for (var add : factories)
            add.accept(map);
    }
    public static synchronized void clear() {
        locations.clear();
        factories.clear();
    }
}
