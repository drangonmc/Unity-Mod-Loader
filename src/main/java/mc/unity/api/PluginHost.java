package mc.unity.api;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public interface PluginHost extends UnityMod, AutoCloseable {
    void tick(MinecraftServer server);
    void worldEvent(ServerLevel level, boolean loaded);
    void aboutToStart(MinecraftServer server);
    net.minecraft.network.chat.Component playerMessage(net.minecraft.network.chat.Component text,
                                                       net.minecraft.server.level.ServerPlayer player,
                                                       boolean join);
    boolean changeSlot(net.minecraft.server.level.ServerPlayer player, int slot);
    boolean allowMode(net.minecraft.server.level.ServerPlayer player,
                      net.minecraft.world.level.GameType type);
    boolean allowFlight(net.minecraft.server.level.ServerPlayer player, boolean flying);
    boolean containerInput(net.minecraft.server.level.ServerPlayer player,
                           net.minecraft.network.protocol.game.ServerboundContainerClickPacket packet);
    float acceptedDamage(net.minecraft.world.entity.LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount, float previous);
    net.minecraft.world.item.ItemStack dispense(net.minecraft.core.dispenser.BlockSource source, net.minecraft.core.BlockPos target, net.minecraft.world.item.ItemStack offered);
    net.minecraft.world.level.block.state.BlockState cauldron(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos,
        net.minecraft.world.level.block.state.BlockState proposed, String reason);
    boolean observesCauldron();
    boolean observesBucket(boolean fill);
    net.minecraft.world.item.ItemStack bucket(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos target,
        net.minecraft.world.phys.BlockHitResult hit, net.minecraft.world.InteractionHand hand,
        boolean fill, net.minecraft.world.item.ItemStack result);
    boolean shoot(net.minecraft.world.entity.LivingEntity shooter, net.minecraft.world.item.ItemStack bow,
        net.minecraft.world.item.ItemStack ammo, net.minecraft.world.entity.projectile.Projectile projectile,
        net.minecraft.world.InteractionHand hand, float force);
    boolean launch(net.minecraft.world.entity.projectile.Projectile projectile);
    boolean hit(net.minecraft.world.entity.projectile.Projectile projectile, net.minecraft.world.phys.HitResult hit);
    void levelTick(net.minecraft.server.level.ServerPlayer player);
    void chunkLoad(net.minecraft.world.level.chunk.LevelChunk chunk, boolean fresh);
    void worldSave(ServerLevel level);
    boolean saveUnloadingChunk(net.minecraft.world.level.chunk.LevelChunk chunk);
    List<String> names();
    void close();
}
