package qa.fabric;
import net.fabricmc.fabric.api.client.networking.v1.*;
public final class FabricPhaseClient {
    public static void bind() {
        ClientConfigurationConnectionEvents.INIT.register((listener, mc) -> {
            if (!ClientConfigurationNetworking.registerReceiver(FabricPhase.RULES, (rules, ctx) -> {
                    if (!ClientConfigurationNetworking.canSend(FabricPhase.ACCEPTED))
                        throw new IllegalStateException("Server did not announce the connection receiver");
                    System.out.println("QA21 FABRIC CLIENT " + rules.value() + " " +
                                       Thread.currentThread().getName());
                    ctx.responseSender().sendPacket(new FabricPhase.Accepted("fabric-" + rules.value()));
                }))
                throw new IllegalStateException("Previous connection retained its handler");
        });
    }
}
