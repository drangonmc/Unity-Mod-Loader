package qa.fabric;
public final class FullFabricClient implements net.fabricmc.api.ClientModInitializer {
    public void onInitializeClient() {
        FabricPlayClient.bind();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
            mc -> FullFabricProbe.verifyTransforms());
    }
}
