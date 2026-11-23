package net.minecraft.world.level.block;
import mc.unity.core.Actions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.state.BlockState;
/** Vanilla XP providers for the built-in Forge BreakEvent contract on the pinned 26.2 runtime. */
public final class UnityBlockExperience {
    private static final java.lang.reflect.Field ORE = range(DropExperienceBlock.class),
                                                 CATALYST = range(SculkCatalystBlock.class);
    private static java.lang.reflect.Field range(Class<?> type) {
        try {
            var f = type.getDeclaredField("xpRange");
            if (f.getType() != IntProvider.class || java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                throw new NoSuchFieldException(type.getName() + ".xpRange: IntProvider");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    public static void verifyRuntime() { /* Initializing this class verifies both 26.2 fields. */ }
    public static int sample(Player player, BlockState state) {
        if (!player.hasCorrectToolForDrops(state))
            return 0;
        var block = state.getBlock();
        var random = player.level().getRandom();
        if (block instanceof SpawnerBlock)
            return 15 + random.nextInt(15) + random.nextInt(15);
        var silk =
            player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silk, player.getMainHandItem()) > 0)
            return 0;
        if (block instanceof RedStoneOreBlock)
            return 1 + random.nextInt(5);
        if (block instanceof SculkSensorBlock || block instanceof SculkShriekerBlock)
            return 5;
        if (block instanceof DropExperienceBlock || block instanceof SculkCatalystBlock)
            try {
                return ((IntProvider)(block instanceof DropExperienceBlock ? ORE : CATALYST).get(block))
                    .sample(random);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        return 0;
    }
    public static void award(Block block, ServerLevel level, BlockPos pos, int amount) {
        if (amount > 0)
            block.popExperience(level, pos, amount);
    }
}
