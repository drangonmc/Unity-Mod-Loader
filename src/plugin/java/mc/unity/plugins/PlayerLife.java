package mc.unity.plugins;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;

final class PlayerLife {
    private final Host host;
    private final Map<ServerPlayer,PlayerDeathEvent> plans=new WeakHashMap<>();
    private static final NamespacedKey RESPAWN=new NamespacedKey("unity","death_state");
    PlayerLife(Host host){this.host=host;}
    boolean dying(ServerPlayer p,DamageSource source){
        if(PlayerDeathEvent.getHandlerList().getRegisteredListeners().length==0)return false;
        var world=p.level();boolean keep=world.getGameRules().get(GameRules.KEEP_INVENTORY)||p.isSpectator();
        var drops=new ArrayList<org.bukkit.inventory.ItemStack>();var inv=p.getInventory();
        if(!keep)for(int i=0;i<inv.getContainerSize();i++){
            var stack=inv.getItem(i);
            if(!stack.isEmpty() && !EnchantmentHelper.has(stack,EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))drops.add(new BlockViews.Stack(stack));
        }
        var msg=host.adventure(p.getCombatTracker().getDeathMessage());
        var event=new PlayerDeathEvent(host.views.player(p),DamageEvents.source(host,p,source),drops,
            keep?0:p.getExperienceReward(world,source.getEntity()),0,0,0,msg,world.getGameRules().get(GameRules.SHOW_DEATH_MESSAGES),!keep);
        event.setKeepInventory(keep);event.setKeepLevel(keep);
        host.fire(event);
        if(event.isCancelled()){float health=(float)event.getReviveHealth();p.setHealth(Float.isFinite(health) && health>0?Math.min(health,p.getMaxHealth()):p.getMaxHealth());return true;}
        plans.put(p,event);
        int[] xp=event.getKeepLevel()?new int[]{1,p.experienceLevel,p.totalExperience,Float.floatToIntBits(p.experienceProgress)}:
            new int[]{0,event.getNewLevel(),event.getNewTotalExp(),event.getNewExp()};
        PersistentTags.entity(p).set(RESPAWN,PersistentDataType.INTEGER_ARRAY,xp);
        return false;
    }
    boolean equipment(ServerPlayer p){
        var e=plans.get(p);if(e==null)return false;
        if(!e.getKeepInventory()){
            var inv=p.getInventory();
            var kept=new ArrayList<ItemStack>();
            for(var value:e.getItemsToKeep()){var stack=BlockViews.Stack.nativeCopy(value);if(!stack.isEmpty())kept.add(stack);}
            // Keep matching stacks in their old slots. A plugin may also supply a new retained stack.
            for(int i=0;i<inv.getContainerSize();i++){
                var old=inv.getItem(i);
                if(old.isEmpty())continue;
                int match=-1;
                for(int j=0;j<kept.size();j++)if(ItemStack.matches(old,kept.get(j))){match=j;break;}
                if(match<0)inv.setItem(i,ItemStack.EMPTY);else kept.remove(match);
            }
            for(var item:kept)inv.add(item);
        }
        for(var value:e.getDrops()){var stack=BlockViews.Stack.nativeCopy(value);if(!stack.isEmpty())p.drop(stack,true,false);}
        p.getInventory().setChanged();return true;
    }
    int xp(ServerPlayer p,int vanilla){
        var e=plans.get(p);return e==null?vanilla:e.shouldDropExperience()?Math.max(0,e.getDroppedExp()):0;
    }
    boolean show(ServerPlayer p,boolean original){var e=plans.get(p);return e==null?original:e.getShowDeathMessages() && e.deathMessage()!=null;}
    net.minecraft.network.chat.Component message(ServerPlayer p,net.minecraft.network.chat.Component original){
        var e=plans.get(p);return e==null?original:host.nativeReason(e.deathMessage());
    }
    void forget(ServerPlayer p){plans.remove(p);}
    void restored(ServerPlayer p,ServerPlayer old,boolean alive){
        plans.remove(old);host.clearMove(p);
        if(alive)return;
        var data=PersistentTags.entity(old);var xp=data.get(RESPAWN,PersistentDataType.INTEGER_ARRAY);
        if(xp==null || xp.length!=4)return;
        if(p!=old)p.getInventory().replaceWith(old.getInventory());
        p.setExperienceLevels(xp[1]);p.totalExperience=xp[2];
        if(xp[0]==1)p.experienceProgress=Float.intBitsToFloat(xp[3]);else p.setExperiencePoints(xp[3]);
        data.remove(RESPAWN);PersistentTags.entity(p).remove(RESPAWN);
    }
    TeleportTransition respawn(ServerPlayer p,TeleportTransition t,boolean alive){
        host.clearMove(p);
        if(PlayerRespawnEvent.getHandlerList().getRegisteredListeners().length==0)return t;
        var cfg=p.getRespawnConfig();boolean anchor=false,bed=false;
        if(cfg!=null && !t.missingRespawnBlock()){
            var key=cfg.respawnData();var level=p.level().getServer().getLevel(key.dimension());
            if(level!=null){var state=level.getBlockState(key.pos());anchor=state.is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR);
                bed=state.is(net.minecraft.tags.BlockTags.BEDS);}
        }
        var at=t.position();var loc=new Location(host.views.world(t.newLevel()),at.x,at.y,at.z,t.yRot(),t.xRot());
        var e=new PlayerRespawnEvent(host.views.player(p),loc.clone(),bed,anchor,t.missingRespawnBlock(),
            alive?PlayerRespawnEvent.RespawnReason.END_PORTAL:PlayerRespawnEvent.RespawnReason.DEATH);
        host.fire(e);var dest=e.getRespawnLocation();if(loc.equals(dest))return t;
        dest.checkFinite();
        return new TeleportTransition(((NativeWorldView)Objects.requireNonNull(dest.getWorld())).unityLevel(),
            new net.minecraft.world.phys.Vec3(dest.getX(),dest.getY(),dest.getZ()),t.deltaMovement(),dest.getYaw(),dest.getPitch(),
            t.missingRespawnBlock(),t.asPassenger(),Set.of(),t.postTeleportTransition());
    }
}
