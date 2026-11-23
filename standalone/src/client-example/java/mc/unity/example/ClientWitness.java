package mc.unity.example;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Properties;
import mc.unity.api.ClientTickMod;
import mc.unity.qa.QaPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Client-side example. The screenshot and receipt are enabled only in the explicit loopback QA run. */
public final class ClientWitness implements ClientTickMod {
    private Path directory;
    private Object lastWorld;
    private int frames;
    private boolean captured;
    private final boolean qa = Boolean.getBoolean("mc.unity.standaloneQA");
    private final ContentActions actions =
        qa && System.getProperty("unity.qa.io") != null ? new ContentActions() : null;
    private final WorldUi worldUi = qa && Boolean.getBoolean("mc.unity.integratedQA") ? new WorldUi() : null;
    @Override
    public void initialize(Path dir) {
        directory = dir;
        if (Boolean.getBoolean("mc.unity.dualQA"))
            QaPackets.install();
    }
    @Override
    public void clientTick() {
        if (worldUi != null) worldUi.tick();
        if (actions != null) {
            actions.tick();
            return;
        }
        var mc = Minecraft.getInstance();
        if (qa && Files.exists(directory.resolve("quit"))) {
            if (mc.level != null)
                mc.disconnectWithProgressScreen();
            mc.stop();
            return;
        }
        if (mc.level == null || mc.player == null) {
            lastWorld = null;
            return;
        }
        if (lastWorld != mc.level) {
            lastWorld = mc.level;
            mc.player.sendSystemMessage(
                Component.literal("Unity native client mod active: " + mc.level.dimension().identifier()));
        }
        if (!qa || captured || mc.getCurrentServer() == null
            || !mc.getCurrentServer().ip.startsWith("127.0.0.1:") || mc.gui.screen() != null || mc.getFps() < 1)
            return;
        var position = new BlockPos(3, 200, 3);
        if (mc.player.position().distanceToSqr(Vec3.atCenterOf(position)) > 55
            || !mc.level.hasChunkAt(position))
            return;
        String block = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(position).getBlock()).toString();
        if (!block.equals("minecraft:amethyst_block"))
            return;
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(position));
        if (++frames < 12)
            return;
        var info = new Properties();
        info.setProperty("world", mc.level.dimension().identifier().toString());
        info.setProperty("block", block);
        info.setProperty("player", mc.player.getGameProfile().name());
        info.setProperty("gpu", RenderSystem.getDevice().getDeviceInfo().name());
        info.setProperty("graphics", RenderSystem.getDevice().getDeviceInfo().backendName());
        info.setProperty("width", Integer.toString(mc.getWindow().getWidth()));
        info.setProperty("height", Integer.toString(mc.getWindow().getHeight()));
        info.setProperty("gameLoader", mc.getClass().getClassLoader().getName());
        info.setProperty("modLoader", getClass().getClassLoader().getName());
        try {
            var temp = directory.resolve("seen.part");
            try (var out = Files.newOutputStream(temp)) {
                info.store(out, "Unity independent client");
            }
            Files.move(temp, directory.resolve("seen.properties"), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Screenshot.grab(mc, false);
        captured = true;
    }
}
