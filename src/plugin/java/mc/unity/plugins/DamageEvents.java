package mc.unity.plugins;

import mc.unity.core.Actions;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import net.minecraft.tags.DamageTypeTags;

final class DamageEvents {
    static float fire(Host host, net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource nativeSource, float amount, float previous) {
        String id = nativeSource.typeHolder().unwrapKey().orElseThrow().identifier().toString();
        var cause = cause(nativeSource,id);
        var target = host.views.entity(entity);
        var direct = nativeSource.getDirectEntity();
        var attacker = nativeSource.getEntity();
        var source = Views.of(org.bukkit.damage.DamageSource.class, id, (method,args) -> switch(method) {
            case "getDamageType" -> org.bukkit.Registry.DAMAGE_TYPE.getOrThrow(org.bukkit.NamespacedKey.fromString(id));
            case "getDirectEntity" -> direct == null ? null : host.views.entity(direct);
            case "getCausingEntity" -> attacker == null ? null : host.views.entity(attacker);
            case "isIndirect" -> !nativeSource.isDirect();
            case "getFoodExhaustion" -> nativeSource.getFoodExhaustion();
            case "scalesWithDifficulty" -> nativeSource.scalesWithDifficulty();
            case "getDamageLocation","getSourceLocation" -> {
                var pos = method.equals("getDamageLocation") ? nativeSource.sourcePositionRaw() : nativeSource.getSourcePosition();
                yield pos == null ? null : new org.bukkit.Location(target.getWorld(),pos.x,pos.y,pos.z);
            }
            default -> throw Views.unsupported("DamageSource",method);
        });
        var pearl = id.equals("minecraft:ender_pearl") ? mc.unity.runtime.ServerHooks.pearlDamager(entity,nativeSource) : null;
        var damager = pearl!=null ? pearl : direct == null ? attacker : direct;
        if(pearl!=null)cause=DamageCause.PROJECTILE;
        var values = new java.util.EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
        var functions = new java.util.EnumMap<EntityDamageEvent.DamageModifier,com.google.common.base.Function<Double,Double>>(EntityDamageEvent.DamageModifier.class);
        var base = EntityDamageEvent.DamageModifier.BASE;
        var cooldown = EntityDamageEvent.DamageModifier.INVULNERABILITY_REDUCTION;
        values.put(base,(double)amount); functions.put(base,v->-0.0);
        values.put(cooldown,-Math.min((double)previous,amount)); functions.put(cooldown,v->-Math.min(previous,v));
        var event = damager == null ? new EntityDamageEvent(target,cause,source,values,functions) :
            new EntityDamageByEntityEvent(host.views.entity(damager),target,cause,source,values,functions);
        host.fire(event);
        if(event.isCancelled()) {
            if(entity instanceof net.minecraft.server.level.ServerPlayer p) {
                var food=p.getFoodData();
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(p.getHealth(),food.getFoodLevel(),food.getSaturationLevel()));
            }
            return -1;
        }
        return (float)Math.max(0,event.getFinalDamage()) + previous;
    }

    private static DamageCause cause(net.minecraft.world.damagesource.DamageSource source,String id) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return source.getEntity() == null ? DamageCause.BLOCK_EXPLOSION : DamageCause.ENTITY_EXPLOSION;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return DamageCause.PROJECTILE;
        return switch(id) {
            case "minecraft:fall","minecraft:ender_pearl","minecraft:stalagmite" -> DamageCause.FALL;
            case "minecraft:player_attack","minecraft:mob_attack","minecraft:mob_attack_no_aggro","minecraft:sting","minecraft:mace_smash" -> DamageCause.ENTITY_ATTACK;
            case "minecraft:cactus","minecraft:sweet_berry_bush" -> DamageCause.CONTACT;
            case "minecraft:lava" -> DamageCause.LAVA;
            case "minecraft:in_fire","minecraft:campfire" -> DamageCause.FIRE;
            case "minecraft:on_fire" -> DamageCause.FIRE_TICK;
            case "minecraft:drown" -> DamageCause.DROWNING;
            case "minecraft:starve" -> DamageCause.STARVATION;
            case "minecraft:in_wall" -> DamageCause.SUFFOCATION;
            case "minecraft:out_of_world" -> DamageCause.VOID;
            case "minecraft:generic_kill" -> DamageCause.KILL;
            case "minecraft:magic","minecraft:indirect_magic" -> DamageCause.MAGIC;
            case "minecraft:wither" -> DamageCause.WITHER;
            case "minecraft:freeze" -> DamageCause.FREEZE;
            case "minecraft:lightning_bolt" -> DamageCause.LIGHTNING;
            case "minecraft:thorns" -> DamageCause.THORNS;
            case "minecraft:hot_floor" -> DamageCause.HOT_FLOOR;
            case "minecraft:cramming" -> DamageCause.CRAMMING;
            case "minecraft:outside_border" -> DamageCause.WORLD_BORDER;
            case "minecraft:fly_into_wall" -> DamageCause.FLY_INTO_WALL;
            case "minecraft:sonic_boom" -> DamageCause.SONIC_BOOM;
            case "minecraft:falling_block","minecraft:falling_anvil","minecraft:falling_stalactite" -> DamageCause.FALLING_BLOCK;
            case "minecraft:dragon_breath" -> DamageCause.DRAGON_BREATH;
            case "minecraft:dry_out" -> DamageCause.DRYOUT;
            default -> DamageCause.CUSTOM;
        };
    }
}
