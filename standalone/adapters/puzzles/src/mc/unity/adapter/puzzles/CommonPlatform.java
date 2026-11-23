package mc.unity.adapter.puzzles;
import fuzs.puzzleslib.common.api.core.v1.ModConstructor;
import fuzs.puzzleslib.common.impl.core.context.ModConstructorImpl;
public final class CommonPlatform extends fuzs.puzzleslib.neoforge.impl.core.NeoForgeCommonProxy {
    public ModConstructorImpl<ModConstructor> getModConstructorImpl() {return PuzzlesLifecycle::common;}
    public void registerAllLoadingHandlers() {PuzzlesLifecycle.loadingEvents();}
    public void registerAllEventHandlers() {PuzzlesLifecycle.gameEvents();}
}
