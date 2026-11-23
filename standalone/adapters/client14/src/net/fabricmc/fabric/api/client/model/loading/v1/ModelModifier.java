package net.fabricmc.fabric.api.client.model.loading.v1;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.world.level.block.state.BlockState;
public final class ModelModifier {
    public interface AfterBakeBlock {
        interface Context {
            BlockState state();
            BlockStateModel.UnbakedRoot sourceModel();
            ModelBaker baker();
        }
        BlockStateModel modifyModelAfterBake(BlockStateModel model, Context context);
    }
}
