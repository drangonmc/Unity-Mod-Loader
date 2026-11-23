package mc.unity.client;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.event.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.world.level.block.state.BlockState;
import static net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier.AfterBakeBlock;

public final class Models {
    private static final List<ModelLoadingPlugin> plugins = new CopyOnWriteArrayList<>();
    public static void register(ModelLoadingPlugin p) { plugins.add(Objects.requireNonNull(p)); }
    public static List<ModelLoadingPlugin> plugins() { return List.copyOf(plugins); }
    public interface Owner {
        AfterBakeBlock unity$modifier();
    }
    public static AfterBakeBlock begin() {
        if (plugins.isEmpty())
            return null;
        Event<AfterBakeBlock> e = EventFactory.createArrayBacked(AfterBakeBlock.class, list -> (model, ctx) -> {
            for (var callback : list)
                model = Objects.requireNonNull(callback.modifyModelAfterBake(model, ctx), "null baked model");
            return model;
        });
        for (var plugin : plugins)
            plugin.initialize(() -> e);
        return e.invoker();
    }
    private record Context(BlockState state, BlockStateModel.UnbakedRoot sourceModel, ModelBaker baker)
        implements AfterBakeBlock.Context {}
    public static BlockStateModel bake(BlockStateModel.UnbakedRoot source, BlockState state, ModelBaker baker) {
        var model = source.bake(state, baker);
        var callback = ((Owner)baker).unity$modifier();
        if (callback != null)
            model = callback.modifyModelAfterBake(model, new Context(state, source, baker));
        return mc.unity.api.client.BlockRenderLayers.apply(state, model);
    }
    public static void clear() {
        plugins.clear();
        mc.unity.api.client.BlockRenderLayers.clear();
    }
}
