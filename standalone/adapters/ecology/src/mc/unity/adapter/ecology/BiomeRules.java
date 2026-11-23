package mc.unity.adapter.ecology;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.invoke.*;
import java.util.*;
import mc.unity.core.WorldRegistries;
import net.fabricmc.fabric.api.biome.v1.*;
import net.fabricmc.fabric.impl.biome.modification.BiomeSelectionContextImpl;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** World-owned modifier registries feed the same biome construction that Fabric mods use. */
public final class BiomeRules {
    private interface Rule {
        String type();
        HolderSet<Biome> biomes();
        void apply(BiomeModificationContext context);
    }
    private record Features(HolderSet<Biome> biomes, HolderSet<PlacedFeature> features,
                            GenerationStep.Decoration step) implements Rule {
        private static final MapCodec<Features> CODEC = RecordCodecBuilder.mapCodec(
            i
            -> i.group(Biome.LIST_CODEC.fieldOf("biomes").forGetter(Features::biomes),
                       PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(Features::features),
                       GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(Features::step))
                   .apply(i, Features::new));
        public String type() { return "add_features"; }
        public void apply(BiomeModificationContext c) {
            for (var f : features)
                c.getGenerationSettings().addFeature(step, f.unwrapKey().orElseThrow());
        }
    }
    private record Spawns(HolderSet<Biome> biomes, List<Weighted<MobSpawnSettings.SpawnerData>> spawners)
        implements Rule {
        private static final Codec<Weighted<MobSpawnSettings.SpawnerData>> ENTRY =
            Weighted.codec(MobSpawnSettings.SpawnerData.CODEC);
        private static final MapCodec<Spawns> CODEC = RecordCodecBuilder.mapCodec(
            i
            -> i.group(Biome.LIST_CODEC.fieldOf("biomes").forGetter(Spawns::biomes),
                       Codec.withAlternative(ENTRY.listOf(), ENTRY, List::of)
                           .fieldOf("spawners")
                           .forGetter(Spawns::spawners))
                   .apply(i, Spawns::new));
        public String type() { return "add_spawns"; }
        public void apply(BiomeModificationContext c) {
            for (var e : spawners)
                c.getMobSpawnSettings().addSpawn(e.value().type().getCategory(), e.value(), e.weight());
        }
    }
    private static Codec<Rule> codec(String platform) {
        Codec<Rule> delegate = Identifier.CODEC.partialDispatch(
            "type", r -> DataResult.success(Identifier.fromNamespaceAndPath(platform, r.type())), id -> {
                if (id.getNamespace().equals(platform)) {
                    if (id.getPath().equals("add_features"))
                        return DataResult.success(Features.CODEC);
                    if (id.getPath().equals("add_spawns"))
                        return DataResult.success(Spawns.CODEC);
                }
                return DataResult.error(() -> "Unity 尚未支持此生物群系修改器 " + id);
            });
        return new Codec<>() {
            public <O> DataResult<Pair<Rule, O>> decode(DynamicOps<O> ops, O input) {
                for (String field : List.of("conditions", "neoforge:conditions", "forge:conditions"))
                    if (ops.get(input, field).result().isPresent())
                        return DataResult.error(() -> "Unsupported conditional biome modifier " + field);
                return delegate.decode(ops, input);
            }
            public <O> DataResult<O> encode(Rule input, DynamicOps<O> ops, O prefix) {
                return delegate.encode(input, ops, prefix);
            }
        };
    }
    public static void install() throws ReflectiveOperationException {
        // Fabric 18.0.6 does not expose registry access on its public selection context.
        // Use the pinned implementation's per-world lookup, including on an integrated server.
        VarHandle context;
        try {
            context = MethodHandles.privateLookupIn(BiomeSelectionContextImpl.class, MethodHandles.lookup())
                          .findVarHandle(BiomeSelectionContextImpl.class, "dynamicRegistries",
                                         RegistryAccess.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Unity 生物群系适配需要 fabric-biome-api-v1 18.0.6 的 " +
                                            "BiomeSelectionContextImpl.dynamicRegistries（RegistryAccess）",
                                            e);
        }
        for (String platform : List.of("forge", "neoforge")) {
            var key = ResourceKey.<Rule>createRegistryKey(
                Identifier.fromNamespaceAndPath(platform, "biome_modifier"));
            WorldRegistries.add(key, codec(platform), null, false);
            BiomeModifications
                .create(Identifier.fromNamespaceAndPath("unity", platform + "_biome_modifiers"))
                .add(ModificationPhase.ADDITIONS,
                     s
                     -> {
                         var registry = ((RegistryAccess)context.get(s)).lookupOrThrow(key);
                         return registry.stream().anyMatch(
                             rule -> rule.biomes().contains(s.getBiomeHolder()));
                     },
                     (s, c) -> {
                         var registry = ((RegistryAccess)context.get(s)).lookupOrThrow(key);
                         for (var rule : registry)
                             if (rule.biomes().contains(s.getBiomeHolder()))
                                 rule.apply(c);
                     });
        }
    }
}
