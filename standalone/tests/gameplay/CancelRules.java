package qa.cancellation;
import java.util.*;
import java.util.function.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.*;
import net.minecraftforge.event.level.BlockEvent.*;
import net.minecraftforge.eventbus.api.bus.*;
import net.minecraftforge.eventbus.api.listener.ObjBooleanBiConsumer;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod("unity_cancel_fixture")
public final class CancelRules {
    static boolean armed;
    static String damage = "none";
    static boolean at(LevelAccessor l, BlockPos p, int x) {
        return armed && l instanceof Level level && !level.isClientSide() && p.equals(new BlockPos(x, 201, 18));
    }
    static boolean combat(LivingEntity entity) {
        return armed && !entity.level().isClientSide() && entity instanceof Player p
            && p.getGameProfile().name().equals("UnityGuardB");
    }
    static <T extends net.minecraftforge.eventbus.internal.Event
            & net.minecraftforge.eventbus.api.event.characteristic.Cancellable> void
    group(CancellableEventBus<T> bus, String name, Predicate<T> eligible, Predicate<T> veto) {
        bus.addListener((byte) 110, (Consumer<T>) e -> {
            if (eligible.test(e))
                System.out.println("CHECK " + name + " HIGH");
        });
        bus.addListener((byte) 60, (Predicate<T>) e -> {
            if (!eligible.test(e))
                return false;
            System.out.println("CHECK " + name + " RULE");
            return veto.test(e);
        });
        bus.addListener((byte) -100, (Consumer<T>) e -> {
            if (eligible.test(e))
                System.out.println("CHECK " + name + " LOW");
        });
        bus.addListener((ObjBooleanBiConsumer<T>) (e, cancel) -> {
            if (eligible.test(e))
                System.out.println("CHECK " + name + " MONITOR " + cancel);
        });
    }
    public CancelRules() {
        group(RightClickItem.BUS, "ITEM",
            e
            -> armed && !e.getLevel().isClientSide()
                && e.getItemStack().is(net.minecraft.world.item.Items.SNOWBALL),
            e -> {
                e.setCancellationResult(InteractionResult.FAIL);
                return true;
            });
        group(EntityInteractSpecific.BUS, "ENTITY_USE",
            e
            -> armed && !e.getLevel().isClientSide()
                && e.getTarget().entityTags().contains("unity_cancel_test"),
            e -> {
                e.setCancellationResult(InteractionResult.FAIL);
                return true;
            });
        group(net.minecraftforge.event.entity.player.AttackEntityEvent.BUS, "ENTITY_ATTACK",
            e
            -> armed && !e.getEntity().level().isClientSide()
                && e.getTarget().entityTags().contains("unity_cancel_test"),
            e -> true);
        group(EntityPlaceEvent.BUS, "PLACE", e -> at(e.getLevel(), e.getPos(), 22), e -> true);
        group(EntityMultiPlaceEvent.BUS, "MULTI",
            e -> at(e.getLevel(), e.getPos(), 24), e -> e.getReplacedBlockSnapshots().size() > 1);
        BreakEvent.BUS.addListener((Consumer<BreakEvent>) e -> {
            if (at(e.getLevel(), e.getPos(), 39)) {
                int original = e.getExpToDrop();
                e.setExpToDrop(7);
                System.out.println("CHECK XP " + original + " -> " + e.getExpToDrop());
            }
        });
        group(BreakEvent.BUS, "BREAK", e -> at(e.getLevel(), e.getPos(), 26), e -> true);
        group(RightClickBlock.BUS, "USE", e -> at(e.getLevel(), e.getPos(), 28), e -> {
            e.setCancellationResult(InteractionResult.FAIL);
            return true;
        });
        group(LivingAttackEvent.BUS, "ATTACK", e -> combat(e.getEntity()), e -> false);
        group(LivingHurtEvent.BUS, "HURT", e -> combat(e.getEntity()), e -> damage.equals("hurt"));
        group(LivingDamageEvent.BUS, "DAMAGE", e -> combat(e.getEntity()), e -> damage.equals("damage"));
        RegisterCommandsEvent.BUS.addListener(e -> {
            e.getDispatcher().register(Commands.literal("unitycanceltest")
                    .requires(s
                        -> s.permissions().hasPermission(
                            net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("mode", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(c -> {
                                String mode =
                                    com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mode");
                                armed = !mode.equals("off");
                                damage = mode;
                                System.out.println("CHECK ARMED " + mode);
                                return 1;
                            })));
            e.getDispatcher().register(Commands.literal("unityabsstat")
                    .requires(s
                        -> s.permissions().hasPermission(
                            net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .executes(c -> {
                        var p = c.getSource().getPlayerOrException();
                        var stat = p.getStats().getValue(
                            net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DAMAGE_ABSORBED));
                        System.out.println("CHECK ABSORBED " + stat + " " + p.getAbsorptionAmount());
                        return 1;
                    }));
            e.getDispatcher().register(Commands.literal("unityauthoritybag")
                    .requires(s
                        -> s.permissions().hasPermission(
                            net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                    .executes(c -> {
                        var p = c.getSource().getPlayerOrException();
                        var bag = new com.google.gson.JsonObject();
                        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                            var stack = p.getInventory().getItem(i);
                            if (!stack.isEmpty())
                                bag.addProperty(Integer.toString(i),
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                                        + "*" + stack.getCount() + "@" + stack.getDamageValue());
                        }
                        System.out.println("CHECK AUTH_BAG " + p.getGameProfile().name() + " " + bag);
                        return 1;
                    }));
            e.getDispatcher().register(Commands.literal("unityactionbarrier").executes(c -> {
                System.out.println("CHECK BARRIER " + c.getSource().getTextName());
                return 1;
            }));
        });
    }
    @Mod.EventBusSubscriber(modid = "unity_cancel_fixture", bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class CreativeListener {
        @SubscribeEvent
        public static void tab(BuildCreativeModeTabContentsEvent e) {
            if (e.getTabKey().identifier().toString().equals("minecraft:ingredients"))
                System.out.println("CHECK MOD CREATIVE " + e.getTabKey().identifier());
        }
    }
}
