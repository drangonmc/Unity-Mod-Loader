package net.minecraft.world.item;

import java.util.function.Consumer;
import mc.unity.core.Actions;
import mc.unity.runtime.ServerHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.*;

/** The original item still performs placement, pickup, statistics and hand exchange. */
public final class UnityProtection {
    public static boolean empty(BucketItem bucket,LivingEntity entity,Level level,BlockPos pos,BlockHitResult hit,InteractionHand hand) {
        var host=ServerHooks.pluginHost();
        if(host!=null && entity instanceof ServerPlayer p && bucket.content instanceof FlowingFluid && hit!=null && host.observesBucket(false)) {
            var target=pos;
            if(!accepts(bucket,p,level,target,true)) {
                target=hit.getBlockPos().relative(hit.getDirection());
                if(!accepts(bucket,p,level,target,false))return bucket.emptyContents(entity,level,pos,hit);
            }
            if(host.bucket(p,target,hit,hand,false,new ItemStack(Items.BUCKET))==null) {
                Actions.resync(p,target,hit.getBlockPos(),hit.getBlockPos().relative(hit.getDirection()));return false;
            }
        }
        return bucket.emptyContents(entity,level,pos,hit);
    }
    private static boolean accepts(BucketItem bucket,LivingEntity p,Level level,BlockPos pos,boolean first) {
        var state=level.getBlockState(pos);
        return state.isAir() || ((!first || !p.isShiftKeyDown()) && (state.canBeReplaced(bucket.content) ||
            state.getBlock() instanceof LiquidBlockContainer c && c.canPlaceLiquid(p,level,pos,state,bucket.content)));
    }
    public static ItemStack fill(BucketPickup pickup,LivingEntity entity,LevelAccessor level,BlockPos pos,BlockState state,InteractionHand hand,BlockHitResult hit) {
        var host=ServerHooks.pluginHost();
        if(host==null || !(entity instanceof ServerPlayer p) || !host.observesBucket(true))return pickup.pickupBlock(entity,level,pos,state);
        ItemStack offered;
        if(state.is(Blocks.POWDER_SNOW)) offered=new ItemStack(Items.POWDER_SNOW_BUCKET);
        else if(state.getFluidState().isSource()) offered=new ItemStack(state.getFluidState().getType().getBucket());
        else if(pickup instanceof LiquidBlock || pickup instanceof SimpleWaterloggedBlock)return pickup.pickupBlock(entity,level,pos,state);
        else offered=ItemStack.EMPTY;
        var approved=host.bucket(p,pos,hit,hand,true,offered);
        if(approved==null){ Actions.resync(p,pos,pos.relative(hit.getDirection()));return ItemStack.EMPTY; }
        var result=pickup.pickupBlock(entity,level,pos,state);
        return result.isEmpty() || approved==offered ? result : approved;
    }
    public static Projectile shoot(Projectile projectile,ServerLevel level,ItemStack ammo,Consumer<Projectile> aim,
                                   LivingEntity shooter,InteractionHand hand,ItemStack weapon,float speed) {
        var host=ServerHooks.pluginHost();
        if(host==null)return Projectile.spawnProjectile(projectile,level,ammo,aim);
        aim.accept(projectile);
        if(!host.shoot(shooter,weapon,ammo,projectile,hand,weapon.getItem() instanceof BowItem ? speed/3.0f : 1.0f)){
            projectile.discard();
            if(shooter instanceof ServerPlayer p) Actions.resync(p);
            return projectile;
        }
        return Projectile.spawnProjectile(projectile,level,ammo);
    }
    public static boolean allowLaunch(Entity entity) {
        if(!(entity instanceof Projectile projectile))return true;
        var host=ServerHooks.pluginHost();
        if(host==null || host.launch(projectile))return true;
        projectile.discard();
        if(projectile.getOwner() instanceof ServerPlayer p)Actions.resync(p);
        return false;
    }
    private static final class Piercing {
        private static final java.lang.invoke.VarHandle ignored;
        static {
            try {
                var type=net.minecraft.world.entity.projectile.arrow.AbstractArrow.class;
                ignored=java.lang.invoke.MethodHandles.privateLookupIn(type,java.lang.invoke.MethodHandles.lookup())
                    .findVarHandle(type,"piercingIgnoreEntityIds",it.unimi.dsi.fastutil.ints.IntOpenHashSet.class);
            } catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
        }
        static void ignore(net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow,int id) {
            var ids=(it.unimi.dsi.fastutil.ints.IntOpenHashSet)ignored.get(arrow);
            if(ids==null){ids=new it.unimi.dsi.fastutil.ints.IntOpenHashSet(5);ignored.set(arrow,ids);}
            ids.add(id);
        }
    }
    public static boolean allowHit(Projectile projectile,HitResult hit) {
        if(!(projectile.level() instanceof ServerLevel) || hit.getType()==HitResult.Type.MISS)return true;
        var host=ServerHooks.pluginHost();
        if(host==null || host.hit(projectile,hit))return true;
        if(hit instanceof EntityHitResult target && projectile instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.getPierceLevel()>0)
            Piercing.ignore(arrow,target.getEntity().getId());
        return false;
    }
}
