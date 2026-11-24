package qa.fabric;

import java.nio.file.*;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;

final class FabricPlayClient {
    private static final int nonce =
        java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    private static boolean sent;
    private static String pending;
    static void bind() {
        ClientPlayNetworking.registerGlobalReceiver(FabricPlayProbe.Answer.TYPE, (reply, ctx) -> {
            if (!ctx.client().isSameThread() || !reply.value().equals(ctx.player().getUUID() + "/" + nonce))
                throw new AssertionError("Wrong Play reply: " + reply);
            System.out.println("QA23 PLAY CLIENT " + reply.value());
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.getConnection() == null || mc.gui.screen() != null ||
                !Files.exists(Path.of("config/qa23-play")))
                return;
            if (!sent) {
                if (!ClientPlayNetworking.canSend(FabricPlayProbe.Request.TYPE))
                    throw new AssertionError("No server receiver");
                if (!Component.translatable("qa23.pack.value").getString().equals("Unity pack overlay 88"))
                    throw new AssertionError("Wrong client pack translation " +
                                             Component.translatable("qa23.pack.value").getString());
                ClientPlayNetworking.send(new FabricPlayProbe.Request(nonce));
                System.out.println("QA23 ASSET translated overlay 88");
                sent = true;
            }
            if (pending != null && mc.player.hasEffect(MobEffects.NIGHT_VISION) == pending.equals("enable")) {
                System.out.println("QA23 VISION CLIENT " + pending + " " + mc.player.getUUID());
                pending = null;
            }
            for (String mode : new String[] {"enable", "clear"}) {
                try {
                    if (pending != null || !Files.deleteIfExists(Path.of("config/qa23-" + mode)))
                        continue;
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                String name = mode.equals("enable") ? "key.night_vision_keybine.nightvisionenable"
                                                    : "key.night_vision_keybine.night_vision_clear";
                var key = Arrays.stream(mc.options.keyMappings)
                              .filter(k -> k.getName().equals(name))
                              .findFirst()
                              .orElseThrow();
                KeyMapping.click(key.getDefaultKey());
                pending = mode;
            }
        });
    }
}
