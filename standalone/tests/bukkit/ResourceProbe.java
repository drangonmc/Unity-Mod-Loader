package qa.entry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;

public final class ResourceProbe implements ClientModInitializer {
    private boolean checked, reportedDisconnect;
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!reportedDisconnect && client.gui.screen() instanceof
                                           net.minecraft.client.gui.screens.DisconnectedScreen screen) {
                reportedDisconnect = true;
                System.out.println("QA24 DISCONNECT SCREEN " + screen.getNarrationMessage().getString());
            }
            if (checked || client.player == null || client.level == null)
                return;
            var value = Component.translatable("qa24.assets.only").getString();
            if (!value.equals("pure client overlay"))
                throw new AssertionError(value);
            checked = true;
            System.out.println("QA24 PURE ASSET OVERLAY");
        });
    }
}
