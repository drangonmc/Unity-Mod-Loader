package mc.unity.example;
import com.google.gson.*;
import mc.unity.core.TabPages;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
/** Loopback-only, normal LocalPlayer/MultiPlayerGameMode calls. State queries observe the live client level. */
final class GameActionProbe {
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> completions;
    private double walkStart;
    private BlockPos mining;
    private Direction face;
    boolean run(Minecraft mc, String action, JsonObject req, JsonObject out, boolean first, int elapsed) {
        var me = mc.player;
        int slot = req.has("slot") ? req.get("slot").getAsInt() : 0;
        switch (action) {
            case "qaRegistryData" -> {
                try {
                    var probe = Class.forName("qa.registry.NestedDataProbe", false, me.getClass().getClassLoader());
                    out.addProperty("value", (int)probe.getMethod("read").invoke(null));
                } catch (ReflectiveOperationException ex) {throw new IllegalStateException(ex);}
            }
            case "qaChannels" -> {
                var peer=(mc.unity.core.PlayNetwork.Peer)(Object)mc.getConnection();
                for(String id:new String[]{"blockrunner:handshake","puzzleslib:handshake","qa_missing:handshake"})
                    out.addProperty(id,peer.hasChannel(new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.parse(id))));
            }
            case "qaWorldExtensions" -> {
                try {out.add("client",WorldInspection.client(mc));} catch(Exception e){throw new IllegalStateException(e);}
            }
            case "qaWalk" -> {
                if (first) {
                    walkStart = me.getZ();
                    me.setYRot(0);
                    me.setXRot(0);
                    me.setSprinting(false);
                }
                mc.options.keyUp.setDown(elapsed < 38);
                if (elapsed < 50) return false;
                out.addProperty("distance", me.getZ()-walkStart);
                out.addProperty("startZ", walkStart);
                out.addProperty("endZ", me.getZ());
            }
            case "qaKeyPress" -> {
                String name=req.get("name").getAsString();
                var matches=java.util.Arrays.stream(mc.options.keyMappings).filter(k->k.getName().equals(name)).toList();
                if(matches.size()!=1)throw new IllegalStateException("Expected one key binding "+name+": "+matches.size());
                var key=matches.getFirst().getDefaultKey();
                net.minecraft.client.KeyMapping.set(key,true);
                net.minecraft.client.KeyMapping.set(key,false);
            }
            case "qaFlight" -> {
                if(!me.getAbilities().mayfly)throw new IllegalStateException("Flight not available");
                me.getAbilities().flying=true;me.onUpdateAbilities();
            }
            case "qaSelect" -> {
                me.getInventory().setSelectedSlot(slot);
            }
            case "qaNearby" -> {
                var center=new Vec3(req.get("x").getAsDouble(),req.get("y").getAsDouble(),req.get("z").getAsDouble());
                if (!mc.level.hasChunkAt(BlockPos.containing(center))) return false;
                var items=new JsonObject();var living=new JsonArray();
                var box=new AABB(center,center).inflate(2.6);
                for (var entity:mc.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,box)) {
                    if (entity.distanceToSqr(center)>2.6*2.6) continue;
                    if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
                        var stack=item.getItem();
                        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        items.addProperty(id,(items.has(id)?items.get(id).getAsInt():0)+stack.getCount());
                    } else if (entity instanceof net.minecraft.world.entity.LivingEntity mob && !mob.isDeadOrDying()) {
                        var row=new JsonObject();
                        row.addProperty("kind",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
                        row.addProperty("health",mob.getHealth());living.add(row);
                    }
                }
                out.add("items",items);out.add("living",living);
            }
            case "qaTransferState" -> {
                var pos=new BlockPos(req.get("x").getAsInt(),req.get("y").getAsInt(),req.get("z").getAsInt());
                var state=mc.level.getBlockState(pos);
                out.addProperty("state",net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state));
                var be=mc.level.getBlockEntity(pos);
                if(be!=null)out.addProperty("nbt",be.saveWithFullMetadata(mc.level.registryAccess()).toString());
            }
            case "qaMachineAttachments","qaAttachmentView" -> {
                try {out.addProperty("values",(String)Class.forName(action.equals("qaMachineAttachments")?"qa.unity33.Routes":"qa.unity32.SyncProbe",true,mc.unity.core.Attachments.class.getClassLoader()).getMethod("inspect",net.minecraft.world.entity.player.Player.class).invoke(null,me));}
                catch(ReflectiveOperationException ex){throw new IllegalStateException("Attachment witness",ex);}
            }
            case "qaCompletions" -> {
                var connection=mc.getConnection();var commands=connection.getCommands();
                if(first)completions=commands.getCompletionSuggestions(commands.parse(req.get("command").getAsString(),connection.getSuggestionsProvider()));
                if(!completions.isDone())return false;
                var choices=new JsonArray();completions.join().getList().forEach(v->choices.add(v.getText()));out.add("choices",choices);
            }
            case "qaMenuClosed" -> out.addProperty("closed",me.containerMenu==me.inventoryMenu && mc.gui.screen()==null);
            case "qaChat" -> mc.getConnection().sendChat(req.get("text").getAsString());
            case "qaCommand" -> {
                mc.getConnection().sendCommand(req.get("cmd").getAsString());
            }
            case "qaRespawn" -> {
                if (!me.isDeadOrDying())
                    throw new IllegalStateException("Player has not died");
                me.respawn();
            }
            case "qaSneak" -> {
                mc.options.keyShift.setDown(req.get("down").getAsBoolean());
            }
            case "qaNewContent" -> {
                var id = net.minecraft.resources.Identifier.parse(req.get("id").getAsString());
                var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow();
                CreativeModeTabs.tryRebuildTabContents(
                    mc.level.enabledFeatures(), true, mc.level.registryAccess());
                var tabs = new JsonArray();
                for (var tab : CreativeModeTabs.allTabs())
                    if (tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item)))
                        tabs.add(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString());
                out.add("tabs", tabs);
                var resource = net.minecraft.resources.Identifier.parse(req.get("texture").getAsString());
                var texture = mc.getResourceManager().getResource(resource).orElseThrow();
                out.addProperty("pack", texture.sourcePackId());
                try (var input = texture.open()) {
                    out.addProperty("textureSHA256",
                        java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            case "qaTabScreen" -> {
                var screen = new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(
                    me, mc.level.enabledFeatures(), true);
                mc.gui.setScreen(screen);
                try {
                    var tab =
                        BuiltInRegistries.CREATIVE_MODE_TAB
                            .getOptional(net.minecraft.resources.Identifier.parse(req.get("tab").getAsString()))
                            .orElseThrow();
                    var field = screen.getClass().getDeclaredField("selectedTab");
                    field.setAccessible(true);
                    for (int i = 0; i < 8 && !TabPages.visible().contains(tab); i++)
                        screen.keyPressed(new net.minecraft.client.input.KeyEvent(267, 0, 0));
                    if (!TabPages.visible().contains(tab))
                        throw new IllegalStateException("Tab not available on any page");
                    var x = screen.getClass().getDeclaredMethod("getTabX", CreativeModeTab.class);
                    var y = screen.getClass().getDeclaredMethod("getTabY", CreativeModeTab.class);
                    x.setAccessible(true);
                    y.setAccessible(true);
                    var base = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class;
                    var left = base.getDeclaredField("leftPos");
                    var top = base.getDeclaredField("topPos");
                    left.setAccessible(true);
                    top.setAccessible(true);
                    var mouse = new net.minecraft.client.input.MouseButtonEvent(
                        left.getInt(screen) + (int) x.invoke(screen, tab) + 12,
                        top.getInt(screen) + (int) y.invoke(screen, tab) + 14,
                        new net.minecraft.client.input.MouseButtonInfo(0, 0));
                    screen.mouseClicked(mouse, false);
                    screen.mouseReleased(mouse);
                    if (field.get(null) != tab)
                        throw new IllegalStateException("Mouse did not select requested tab");
                    out.addProperty("selected", BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab).toString());
                    out.addProperty("items", tab.getDisplayItems().size());
                    if (req.has("checkPinned")) {
                        var pinned = new JsonArray();
                        for (var type : java.util.List.of(CreativeModeTab.Type.SEARCH, CreativeModeTab.Type.INVENTORY)) {
                            var target = TabPages.visible().stream().filter(t -> t.getType() == type).findFirst().orElseThrow();
                            var click = new net.minecraft.client.input.MouseButtonEvent(
                                left.getInt(screen) + (int)x.invoke(screen,target) + 12,
                                top.getInt(screen) + (int)y.invoke(screen,target) + 14,
                                new net.minecraft.client.input.MouseButtonInfo(0,0));
                            screen.mouseClicked(click,false); screen.mouseReleased(click);
                            if (field.get(null) != target || !TabPages.visible().contains(tab))
                                throw new IllegalStateException("Pinned tab failed on mod page: "+type);
                            pinned.add(type.name());
                        }
                        out.add("pinned",pinned);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "qaCloseTab" -> {
                if (mc.gui.screen() != null)
                    mc.gui.screen().onClose();
            }
            case "qaNeoPayload" -> {
                try {
                    var sample = Class.forName("qa.neocontent.NeoParent", true, me.getClass().getClassLoader());
                    int nonce = req.get("nonce").getAsInt();
                    if (first)
                        sample.getMethod("ask", int.class).invoke(null, nonce);
                    int reply = (int) sample.getMethod("last").invoke(null);
                    if (reply != nonce)
                        return false;
                    out.addProperty("reply", reply);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
            case "qaCreative" -> {
                CreativeModeTabs.tryRebuildTabContents(
                    mc.level.enabledFeatures(), true, mc.level.registryAccess());
                out.addProperty("tabs", CreativeModeTabs.allTabs().size());
            }
            case "qaPlayer" -> {
                Player p = req.has("name")
                    ? mc.level.players()
                          .stream()
                          .filter(x -> x.getGameProfile().name().equals(req.get("name").getAsString()))
                          .findFirst()
                          .orElse(null)
                    : me;
                if (p == null)
                    return false;
                out.addProperty("health", p.getHealth());
                out.addProperty(
                    "hand", BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());
                out.addProperty("held", p.getMainHandItem().getCount());
                out.addProperty("damage", p.getMainHandItem().getDamageValue());
                out.addProperty("chest",BuiltInRegistries.ITEM.getKey(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()).toString());
                out.addProperty("dead", p.isDeadOrDying());
                out.addProperty("x", p.getX());
                out.addProperty("y", p.getY());
                out.addProperty("z", p.getZ());
                if (p == me) {
                    out.addProperty("mayfly",me.getAbilities().mayfly);
                    out.addProperty("flying",me.getAbilities().flying);
                    out.addProperty("xpLevel",me.experienceLevel);
                    out.addProperty("xpProgress",me.experienceProgress);
                    out.addProperty("xpPoints",Math.round(me.experienceProgress*me.getXpNeededForNextLevel()));
                    out.addProperty("selected",me.getInventory().getSelectedSlot());
                    var bag = new JsonObject();
                    for (int i = 0; i < me.getInventory().getContainerSize(); i++) {
                        var stack = me.getInventory().getItem(i);
                        if (!stack.isEmpty())
                            bag.addProperty(Integer.toString(i),
                                BuiltInRegistries.ITEM.getKey(stack.getItem()) + "*" + stack.getCount() + "@"
                                    + stack.getDamageValue());
                    }
                    out.add("bag", bag);
                }
            }
            case "qaProtectedUse", "qaProtectedBow" -> {
                if(first) {
                    me.getInventory().setSelectedSlot(slot);
                    Vec3 target;
                    if(req.has("name")) {
                        var other=mc.level.players().stream().filter(p->p.getGameProfile().name().equals(req.get("name").getAsString())).findFirst().orElseThrow();
                        target=other.position().add(0,1.25,0);
                    } else {
                        target=new Vec3(req.get("x").getAsDouble(),req.get("y").getAsDouble(),req.get("z").getAsDouble());
                    }
                    me.lookAt(EntityAnchorArgument.Anchor.EYES,target);
                    if(action.equals("qaProtectedBow")) mc.options.keyUse.setDown(true);
                    var used=mc.gameMode.useItem(me,InteractionHand.MAIN_HAND);
                    out.addProperty("used",used.consumesAction());
                }
                if(action.equals("qaProtectedBow")) {
                    if(elapsed<25)return false;
                    mc.options.keyUse.setDown(false);
                    mc.gameMode.releaseUsingItem(me);
                }
            }
            case "qaEat" -> {
                if(first){me.getInventory().setSelectedSlot(slot);mc.options.keyUse.setDown(true);mc.gameMode.useItem(me,InteractionHand.MAIN_HAND);}
                if(elapsed<45)return false;mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(me);
            }
            case "qaUseOnce" -> {
                me.getInventory().setSelectedSlot(slot);
                var result = mc.gameMode.useItem(me, InteractionHand.MAIN_HAND);
                out.addProperty("predicted", result.consumesAction());
            }
            case "qaStandUse", "qaStandAttack" -> {
                var stands = mc.level.getEntitiesOfClass(
                    net.minecraft.world.entity.decoration.ArmorStand.class, me.getBoundingBox().inflate(4));
                if (stands.size() != 1 || !me.isWithinEntityInteractionRange(stands.getFirst(), 0)
                    || !me.hasLineOfSight(stands.getFirst()))
                    throw new IllegalStateException("Armor stand is not reachable");
                var stand = stands.getFirst();
                var center = stand.getBoundingBox().getCenter();
                me.getInventory().setSelectedSlot(slot);
                me.lookAt(EntityAnchorArgument.Anchor.EYES, center);
                if (action.equals("qaStandUse"))
                    mc.gameMode.interact(
                        me, stand, new EntityHitResult(stand, center), InteractionHand.MAIN_HAND);
                else
                    mc.gameMode.attack(me, stand);
                me.swing(InteractionHand.MAIN_HAND);
                out.addProperty("entity", stand.getUUID().toString());
            }
            case "qaHoldShield" -> {
                mc.options.keyUse.setDown(true);
                me.getInventory().setSelectedSlot(slot);
                var interaction = mc.gameMode.useItem(me, InteractionHand.MAIN_HAND);
                var blocking = me.getItemBlockingWith();
                out.addProperty("immediate",
                    blocking == null ? "none" : BuiltInRegistries.ITEM.getKey(blocking.getItem()).toString());
                out.addProperty("ticks", me.getTicksUsingItem());
                out.addProperty("used", interaction.consumesAction());
            }
            case "qaRelease" -> {
                mc.options.keyUse.setDown(false);
                mc.gameMode.releaseUsingItem(me);
            }
            case "qaAttack" -> {
                var target = mc.level.players()
                                 .stream()
                                 .filter(x -> x.getGameProfile().name().equals(req.get("name").getAsString()))
                                 .findFirst()
                                 .orElseThrow();
                if (!me.isWithinEntityInteractionRange(target, 0) || !me.hasLineOfSight(target))
                    throw new IllegalStateException("Target out of reach");
                me.getInventory().setSelectedSlot(slot);
                me.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                mc.gameMode.attack(me, target);
                me.swing(InteractionHand.MAIN_HAND);
            }
            case "qaBlock", "qaRight", "qaMine" -> {
                var target =
                    new BlockPos(req.get("x").getAsInt(), req.get("y").getAsInt(), req.get("z").getAsInt());
                if (!mc.level.hasChunkAt(target))
                    return false;
                if (action.equals("qaRight") || action.equals("qaMine")) {
                    if (first) {
                        me.getInventory().setSelectedSlot(slot);
                        boolean top = req.has("top") && req.get("top").getAsBoolean();
                        var shape = mc.level.getBlockState(target).getShape(mc.level, target);
                        var aim = top
                            ? Vec3.atCenterOf(target).add(0, .49, 0)
                            : shape.bounds().getCenter().add(target.getX(), target.getY(), target.getZ());
                        me.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
                        var hit = mc.level.clip(new ClipContext(
                            me.getEyePosition(), aim, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, me));
                        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(target)
                            || !me.isWithinBlockInteractionRange(target, 0)
                            || top && hit.getDirection() != Direction.UP)
                            throw new IllegalStateException(
                                "Unreachable " + target + " from " + me.getEyePosition() + " hit " + hit);
                        if (action.equals("qaRight")) {
                            var result = mc.gameMode.useItemOn(me, InteractionHand.MAIN_HAND, hit);
                            out.addProperty("predicted", result.consumesAction());
                        } else {
                            mining = target;
                            face = hit.getDirection();
                            mc.gameMode.startDestroyBlock(target, face);
                        }
                        me.swing(InteractionHand.MAIN_HAND);
                    }
                    if (action.equals("qaMine")) {
                        if (!first && !mc.level.getBlockState(target).isAir())
                            mc.gameMode.continueDestroyBlock(mining, face);
                        if (!mc.level.getBlockState(target).isAir() && elapsed < 45)
                            return false;
                        mc.gameMode.stopDestroyBlock();
                        mining = null;
                    }
                }
                var state = mc.level.getBlockState(target);
                out.addProperty("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                out.addProperty("state", state.toString());
                out.addProperty("held", me.getMainHandItem().getCount());
                out.addProperty("damage", me.getMainHandItem().getDamageValue());
            }
            default -> throw new IllegalArgumentException(action);
        }
        return true;
    }
}
