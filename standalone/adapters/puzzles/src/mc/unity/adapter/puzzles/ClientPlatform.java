package mc.unity.adapter.puzzles;
import fuzs.puzzleslib.common.api.core.v1.ModConstructor;
import fuzs.puzzleslib.common.impl.core.context.ModConstructorImpl;
public final class ClientPlatform extends fuzs.puzzleslib.neoforge.impl.client.core.NeoForgeClientProxy {
    public ModConstructorImpl<ModConstructor> getModConstructorImpl() {return PuzzlesLifecycle::common;}
    public void registerAllLoadingHandlers() {PuzzlesLifecycle.loadingEvents();}
    public void registerAllEventHandlers() {PuzzlesClient.gameEvents();}
    public ModConstructorImpl<fuzs.puzzleslib.common.api.client.core.v1.ClientModConstructor> getClientModConstructorImpl() {return PuzzlesClient::construct;}
}
