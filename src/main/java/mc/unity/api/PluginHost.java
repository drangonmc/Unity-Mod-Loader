package mc.unity.api;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public interface PluginHost extends UnityMod, AutoCloseable {
    default boolean chat(net.minecraft.server.level.ServerPlayer p,net.minecraft.network.chat.PlayerChatMessage message,net.minecraft.network.chat.ChatType.Bound bound){return false;}
    default String command(net.minecraft.server.level.ServerPlayer p,String input){return input;}
    default boolean move(net.minecraft.server.level.ServerPlayer p,double x,double y,double z,float yaw,float pitch,double fromX,double fromY,double fromZ,float oldYaw,float oldPitch){return true;}
    default net.minecraft.world.level.portal.TeleportTransition teleport(net.minecraft.server.level.ServerPlayer p,net.minecraft.world.level.portal.TeleportTransition transition,String cause){return transition;}
    default boolean creature(net.minecraft.world.entity.Entity entity,String reason){return true;}
    default java.util.List<net.minecraft.core.BlockPos> explode(net.minecraft.world.level.ServerExplosion e,java.util.List<net.minecraft.core.BlockPos> blocks){return blocks;}
    default boolean playerDying(net.minecraft.server.level.ServerPlayer p,net.minecraft.world.damagesource.DamageSource source){return false;}
    default boolean deathEquipment(net.minecraft.server.level.ServerPlayer p){return false;}
    default int deathXp(net.minecraft.server.level.ServerPlayer p,int vanilla){return vanilla;}
    default net.minecraft.network.chat.Component deathMessage(net.minecraft.server.level.ServerPlayer p,net.minecraft.network.chat.Component vanilla){return vanilla;}
    default boolean showDeath(net.minecraft.server.level.ServerPlayer p,boolean original){return original;}
    default void restored(net.minecraft.server.level.ServerPlayer player,net.minecraft.server.level.ServerPlayer old,boolean alive){}
    default net.minecraft.world.level.portal.TeleportTransition respawning(net.minecraft.server.level.ServerPlayer p,net.minecraft.world.level.portal.TeleportTransition t,boolean alive){return t;}
    default boolean menuOpen(net.minecraft.server.level.ServerPlayer p,net.minecraft.world.inventory.AbstractContainerMenu m,net.minecraft.world.MenuProvider provider){return true;}
    default void menuClose(net.minecraft.server.level.ServerPlayer p,String reason){}
    default boolean machineAccess(net.minecraft.server.level.ServerLevel world,net.minecraft.core.BlockPos pos,net.minecraft.server.players.NameAndId owner,String operation){return false;}
    default mc.unity.runtime.PortalHooks.Plan portal(net.minecraft.server.level.ServerPlayer player,mc.unity.runtime.PortalHooks.Plan plan,String reason){return plan;}
    void tick(MinecraftServer server);
    default void entityUntracked(net.minecraft.world.entity.Entity entity) {}
    default net.minecraft.network.chat.Component preLogin(com.mojang.authlib.GameProfile profile,java.net.InetAddress address){return null;}
    default net.minecraft.network.chat.Component login(net.minecraft.server.level.ServerPlayer p,java.net.InetAddress address){return null;}
    default void forgetLogin(net.minecraft.server.level.ServerPlayer p) {}
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
