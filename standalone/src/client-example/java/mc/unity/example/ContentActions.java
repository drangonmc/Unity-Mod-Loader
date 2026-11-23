package mc.unity.example;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.file.*;
import mc.unity.qa.QaPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
/** Test-only driver inside the interactive client. All world interactions use its multiplayer game mode. */
final class ContentActions {
    private final GameActionProbe gameActions = new GameActionProbe();
    private long frames;
    private int ticks, start, serial, initialCount;
    private JsonObject request;
    private BlockPos mining;
    private Direction miningFace;
    private final Path io = Path.of(System.getProperty("unity.qa.io"));
    void tick() {
        var mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && !mc.getCurrentServer().ip.startsWith("127.0.0.1:"))
            return;
        if (mc.getFps() > 0)
            frames++;
        ticks++;
        if (Files.exists(io.resolve("quit"))) {
            if (mc.level != null)
                mc.disconnectWithProgressScreen();
            mc.stop();
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null || frames < 5)
            return;
        try {
            if (request == null) {
                Path input = io.resolve("request.json");
                if (!Files.isRegularFile(input))
                    return;
                request = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
                Files.delete(input);
                serial = request.get("seq").getAsInt();
                start = ticks;
            }
            var player = mc.player;
            if (ticks - start > 300)
                throw new IllegalStateException("Client action timed out: " + request);
            String action = request.get("action").getAsString();
            var result = new JsonObject();
            int slot = request.has("slot") ? request.get("slot").getAsInt() : 0;
            if (action.startsWith("qa")) {
                if (!gameActions.run(mc, action, request, result, ticks == start, ticks - start))
                    return;
            } else if (action.equals("packetRoundTrip")) {
                int nonce = request.get("nonce").getAsInt();
                if (ticks == start)
                    QaPackets.ask(nonce);
                var reply = QaPackets.last;
                if (reply == null || reply.nonce() != nonce)
                    return;
                result.addProperty("nonce", reply.nonce());
                result.addProperty("id", reply.stack());
                result.addProperty("count", reply.count());
            } else if (action.startsWith("menu")) {
                if (!MenuProbe.apply(mc, action, request, result, ticks == start))
                    return;
            } else if (action.equals("status")) {
                if (mc.gui.screen() != null || frames < 20)
                    return;
                result.addProperty("player", player.getGameProfile().name());
                result.addProperty("visible", org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_VISIBLE) != 0);
                result.addProperty("gpu", RenderSystem.getDevice().getDeviceInfo().name());
                result.addProperty("driver", RenderSystem.getDevice().getDeviceInfo().driverInfo());
                result.addProperty("graphicsAPI", RenderSystem.getDevice().getDeviceInfo().backendName());
                result.addProperty("backend", RenderSystem.getBackendDescription());
                result.addProperty("remote", mc.getCurrentServer() != null && !mc.hasSingleplayerServer());
                result.addProperty("world", mc.level.dimension().identifier().toString());
                result.addProperty("x", player.getX());
                result.addProperty("y", player.getY());
                result.addProperty("z", player.getZ());
                result.addProperty("uuid", player.getUUID().toString());
                result.addProperty("width", mc.getWindow().getWidth());
                result.addProperty("height", mc.getWindow().getHeight());
            } else if (action.equals("slot") || action.equals("registry")) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (action.equals("registry")) {
                    var id = Identifier.parse(request.get("id").getAsString());
                    var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
                    stack = new ItemStack(item);
                    result.addProperty("itemNumber", BuiltInRegistries.ITEM.getId(item));
                    var block = BuiltInRegistries.BLOCK.getOptional(id);
                    if (block.isPresent())
                        result.addProperty("blockNumber", BuiltInRegistries.BLOCK.getId(block.get()));
                    var resource =
                        Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json");
                    result.addProperty("assetPack",
                        mc.getResourceManager().getResource(resource).orElseThrow().sourcePackId());
                    var missing = mc.getModelManager().getItemModel(
                        Identifier.fromNamespaceAndPath("unity_graphical_qa", "missing_sentinel"));
                    if (mc.getModelManager().getItemModel(id) == missing)
                        throw new IllegalStateException("Missing baked item model: " + id);
                    var renderState = new ItemStackRenderState();
                    mc.getItemModelResolver().updateForLiving(
                        renderState, stack, ItemDisplayContext.GUI, player);
                    result.addProperty("renderLayers", !renderState.isEmpty());
                }
                result.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                result.addProperty("count", stack.getCount());
                result.addProperty("foodLevel", player.getFoodData().getFoodLevel());
            } else if (action.equals("block")) {
                var block = mc.level.getBlockState(pos()).getBlock();
                result.addProperty("id", BuiltInRegistries.BLOCK.getKey(block).toString());
                result.addProperty("number", BuiltInRegistries.BLOCK.getId(block));
            } else if (action.equals("use")) {
                if (ticks == start) {
                    player.getInventory().setSelectedSlot(slot);
                    initialCount = player.getMainHandItem().getCount();
                    if (!matchesItem(player.getMainHandItem()))
                        throw new IllegalStateException("Wrong item for use");
                    mc.options.keyUse.setDown(true);
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                }
                if (player.getInventory().getItem(slot).getCount() >= initialCount)
                    return;
                mc.options.keyUse.setDown(false);
                mc.gameMode.releaseUsingItem(player);
                result.addProperty("before", initialCount);
                result.addProperty("after", player.getInventory().getItem(slot).getCount());
            } else if (action.equals("place") || action.equals("break")) {
                BlockPos target = pos();
                boolean placing = action.equals("place");
                if (ticks == start) {
                    player.getInventory().setSelectedSlot(slot);
                    if (!matchesItem(player.getMainHandItem()))
                        throw new IllegalStateException("Wrong selected item");
                    BlockPos hitPos = placing ? target.below() : target;
                    Vec3 aim = Vec3.atCenterOf(hitPos).add(0, placing ? 0.49 : 0.15, 0);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
                    BlockHitResult hit = mc.level.clip(new ClipContext(player.getEyePosition(), aim,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(hitPos)
                        || !player.isWithinBlockInteractionRange(hitPos, 0)
                        || (placing && hit.getDirection() != Direction.UP))
                        throw new IllegalStateException(
                            "Unreachable target " + hit + " from " + player.getEyePosition());
                    if (placing) {
                        var interaction = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                        if (!interaction.consumesAction())
                            throw new IllegalStateException("Placement refused: " + interaction);
                    } else {
                        mining = target;
                        miningFace = hit.getDirection();
                        mc.options.keyAttack.setDown(true);
                        mc.gameMode.startDestroyBlock(mining, miningFace);
                    }
                    player.swing(InteractionHand.MAIN_HAND);
                }
                if (placing) {
                    if (!BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target).getBlock())
                            .toString()
                            .equals(request.get("block").getAsString()))
                        return;
                } else if (!mc.level.getBlockState(target).isAir()) {
                    return;
                }
                if (!placing)
                    mc.options.keyAttack.setDown(false);
                result.addProperty("ticks", ticks - start);
                result.addProperty("target", target.toShortString());
            } else if (action.equals("look")) {
                player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos()));
            } else if (action.equals("screenshot")) {
                Screenshot.grab(mc, false);
            } else if (action.equals("quit")) {
                respond(result);
                mc.disconnectWithProgressScreen();
                mc.stop();
                return;
            } else
                throw new IllegalArgumentException(action);
            respond(result);
        } catch (Exception | LinkageError error) {
            error.printStackTrace();
            mc.options.keyUse.setDown(false);
            mc.options.keyAttack.setDown(false);
            var reply = new JsonObject();
            reply.addProperty("error", error.toString());
            respond(reply);
        }
    }
    private boolean matchesItem(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem())
            .toString()
            .equals(request.get("item").getAsString());
    }
    private BlockPos pos() {
        return new BlockPos(
            request.get("x").getAsInt(), request.get("y").getAsInt(), request.get("z").getAsInt());
    }
    private void respond(JsonObject result) {
        result.addProperty("frames", frames);
        result.addProperty("clientTick", ticks);
        result.addProperty("seq", serial);
        Path temp = io.resolve("response.tmp");
        try {
            Files.writeString(temp, result.toString());
            Files.move(temp, io.resolve("response.json"), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        request = null;
    }
}
