package net.fabricmc.fabric.api.client.model.loading.v1;
import java.util.List;
import net.fabricmc.fabric.api.event.Event;
public interface ModelLoadingPlugin {
    interface Context {
        Event<ModelModifier.AfterBakeBlock> modifyBlockModelAfterBake();
    }
    static void register(ModelLoadingPlugin plugin) { mc.unity.client.Models.register(plugin); }
    static List<ModelLoadingPlugin> getAll() { return mc.unity.client.Models.plugins(); }
    void initialize(Context context);
}
