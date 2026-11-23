package qa.worlds;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.dispatch.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.*;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.event.EntityRenderersEvent;
public final class Visuals implements ClientModInitializer {
    public static final AtomicInteger sessions = new AtomicInteger(), providers = new AtomicInteger(),
                                      draws = new AtomicInteger(), lastDrawnProvider = new AtomicInteger(),
                                      sounds = new AtomicInteger();
    public static volatile String originalGrate = "";
    public void onInitializeClient() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(e -> {
            e.registerEntityRenderer(ContentMod.CRYSTAL.get(), EndCrystalRenderer::new);
            net.minecraft.client.Minecraft.getInstance().getSoundManager().addListener(
                (instance, events, range) -> {
                    if (instance.getIdentifier().equals(ContentMod.id("chime")))
                        sounds.incrementAndGet();
                });
        });
        BlockEntityRendererRegistry.register(ContentMod.TILE.get(), factory -> {
            int generation = providers.incrementAndGet();
            var bell = new BellRenderer(factory);
            return new BlockEntityRenderer<ContentMod.Tile, BellRenderState>() {
                public BellRenderState createRenderState() { return bell.createRenderState(); }
                public void submit(BellRenderState state, PoseStack stack, SubmitNodeCollector sink,
                                   CameraRenderState camera) {
                    bell.submit(state, stack, sink, camera);
                    draws.incrementAndGet();
                    lastDrawnProvider.set(generation);
                }
            };
        });
        ModelLoadingPlugin.register(ctx -> {
            int session = sessions.incrementAndGet();
            ctx.modifyBlockModelAfterBake().register((model, context) -> {
                if (context.state().is(ContentMod.GRATE.get())) {
                    var parts = new ArrayList<BlockStateModelPart>();
                    model.collectParts(RandomSource.create(7), parts);
                    originalGrate = parts.getFirst()
                                        .getQuads(net.minecraft.core.Direction.NORTH)
                                        .getFirst()
                                        .materialInfo()
                                        .layer()
                                        .name();
                }
                return context.state().is(ContentMod.GLASS.get()) ? new Tagged(model, session) : model;
            });
        });
        mc.unity.api.client.BlockRenderLayers.register(ContentMod.GRATE.get(), ChunkSectionLayer.CUTOUT);
    }
    public record Tagged(BlockStateModel delegate, int session) implements BlockStateModel {
        public void collectParts(RandomSource seed, List<BlockStateModelPart> out) {
            delegate.collectParts(seed, out);
        }
        public Material.Baked particleMaterial() { return delegate.particleMaterial(); }
        public int materialFlags() { return delegate.materialFlags(); }
    }
}
