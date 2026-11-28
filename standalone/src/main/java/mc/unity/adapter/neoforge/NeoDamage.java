package mc.unity.adapter.neoforge;

import mc.unity.core.LivingFlow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;

final class NeoDamage implements LivingFlow.Damage {
    private final NeoBus bus;
    private final LivingEntity entity;
    private final DamageContainer damage;
    NeoDamage(NeoBus bus,LivingEntity entity,DamageSource source,float original){
        this.bus=bus;this.entity=entity;damage=new DamageContainer(source,original);
    }
    public float incoming(float amount){
        damage.setNewDamage(amount);
        var e=bus.post(new LivingIncomingDamageEvent(entity,damage));
        return e.isCanceled()?-1:Math.max(0,damage.getNewDamage());
    }
    private int itemDamage=-1;
    public float shield(float vanilla,boolean originallyBlocked){
        var event=bus.post(new LivingShieldBlockEvent(entity,damage,vanilla,originallyBlocked));
        if(!event.getBlocked())return -1;
        damage.setBlockedDamage(event);
        itemDamage=event.shieldDamage();
        return damage.getBlockedDamage();
    }
    public int shieldWear(int vanilla){return itemDamage<0?vanilla:itemDamage;}
    public float reduce(float before,float after,int step){
        var reduction=switch(step){
            case 0->DamageContainer.Reduction.INVULNERABILITY;
            case 1->DamageContainer.Reduction.ARMOR;
            case 2->DamageContainer.Reduction.MOB_EFFECTS;
            case 3->DamageContainer.Reduction.ENCHANTMENTS;
            case 4->DamageContainer.Reduction.ABSORPTION;
            default->throw new AssertionError(step);
        };
        damage.setNewDamage(before);damage.setReduction(reduction,before-after);return damage.getNewDamage();
    }
    public float pre(float amount){damage.setNewDamage(amount);bus.post(new LivingDamageEvent.Pre(entity,damage));damage.captureInflictedDamage();return damage.getNewDamage();}
    public void finalAmount(float amount){
        damage.setNewDamage(Math.max(0,amount));
        if(amount==0 && damage.getInflictedDamage()>0 && damage.getReduction(DamageContainer.Reduction.ABSORPTION)>0)post();
    }
    public void post(){bus.post(new LivingDamageEvent.Post(entity,damage));}
    public int ticks(int vanilla){return damage.getPostAttackInvulnerabilityTicks();}
    public void excess(int ticks){damage.setShouldCauseSideEffects(false);damage.setPostAttackInvulnerabilityTicks(ticks);}
    public boolean sideEffects(boolean vanilla){return damage.shouldCauseSideEffects();}
}
