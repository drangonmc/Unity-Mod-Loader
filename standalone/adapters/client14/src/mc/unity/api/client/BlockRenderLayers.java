package mc.unity.api.client;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockRenderLayers {
    private static final Map<Block, ChunkSectionLayer> layers = new ConcurrentHashMap<>();
    public static void register(Block block, ChunkSectionLayer layer) {
        layers.put(Objects.requireNonNull(block), Objects.requireNonNull(layer));
    }
    public static void clear() { layers.clear(); }
    public static BlockStateModel apply(BlockState state, BlockStateModel model) {
        var layer = layers.get(state.getBlock());
        return layer == null ? model : new LayerModel(model, layer);
    }
    private static int flags(int f, ChunkSectionLayer layer) {
        return (f & ~BakedQuad.FLAG_TRANSLUCENT) |
            (layer == ChunkSectionLayer.TRANSLUCENT ? BakedQuad.FLAG_TRANSLUCENT : 0);
    }
    private static final class LayerModel implements BlockStateModel {
        final BlockStateModel source;
        final ChunkSectionLayer layer;
        final com.google.common.cache.LoadingCache<BlockStateModelPart, BlockStateModelPart> parts;
        LayerModel(BlockStateModel source, ChunkSectionLayer layer) {
            this.source = source;
            this.layer = layer;
            parts = CacheBuilder.newBuilder().weakKeys().build(
                com.google.common.cache.CacheLoader.from(p -> new Part(p, layer)));
        }
        public void collectParts(RandomSource random, List<BlockStateModelPart> out) {
            int start = out.size();
            source.collectParts(random, out);
            for (int i = start; i < out.size(); i++)
                out.set(i, parts.getUnchecked(out.get(i)));
        }
        public Material.Baked particleMaterial() { return source.particleMaterial(); }
        public int materialFlags() { return flags(source.materialFlags(), layer); }
    }
    private static final class Part implements BlockStateModelPart {
        final Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
        final List<BakedQuad> unculled;
        final int materialFlags;
        final boolean ao;
        final Material.Baked particle;
        Part(BlockStateModelPart source, ChunkSectionLayer layer) {
            for (var face : Direction.values())
                faces.put(face, convert(source.getQuads(face), layer));
            unculled = convert(source.getQuads(null), layer);
            materialFlags = flags(source.materialFlags(), layer);
            ao = source.useAmbientOcclusion();
            particle = source.particleMaterial();
        }
        private static List<BakedQuad> convert(List<BakedQuad> quads, ChunkSectionLayer layer) {
            var transparency = switch (layer) {
                case SOLID -> Transparency.NONE;
                case CUTOUT -> Transparency.TRANSPARENT;
                case TRANSLUCENT -> Transparency.TRANSLUCENT;
            };
            return quads.stream()
                .map(q -> {
                    var m = q.materialInfo();
                    if (m.layer() == layer)
                        return q;
                    var replaced =
                        BakedQuad.MaterialInfo.of(new Material.Baked(m.sprite(), false), transparency,
                                                  m.tintIndex(), m.shade(), m.lightEmission());
                    return new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(),
                                         q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(),
                                         q.direction(), replaced);
                })
                .toList();
        }
        public List<BakedQuad> getQuads(Direction face) { return face == null ? unculled : faces.get(face); }
        public boolean useAmbientOcclusion() { return ao; }
        public int materialFlags() { return materialFlags; }
        public Material.Baked particleMaterial() { return particle; }
    }
}
