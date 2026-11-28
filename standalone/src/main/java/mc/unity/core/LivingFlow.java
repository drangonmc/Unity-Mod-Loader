package mc.unity.core;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

/** Per-hit and per-death state. A recursive hurt or death owns a separate frame. */
public final class LivingFlow {
    public interface Damage {
        float incoming(float amount);
        float shield(float vanilla,boolean originallyBlocked);
        int shieldWear(int vanilla);
        float reduce(float before,float after,int step);
        float pre(float amount);
        void finalAmount(float amount);
        void post();
        int ticks(int vanilla);
        boolean sideEffects(boolean vanilla);
        void excess(int ticks);
    }
    public interface Factory { Damage open(LivingEntity target,DamageSource source,float original); }
    private static volatile Factory factory;
    public static synchronized Runnable bind(Factory f) {
        if(factory!=null)throw new IllegalStateException("A damage adapter is already registered");
        factory=Objects.requireNonNull(f);
        return ()->{synchronized(LivingFlow.class){if(factory==f)factory=null;}};
    }
    private record Hit(Damage damage,Hit parent){}
    private static final ThreadLocal<Hit> current=new ThreadLocal<>();
    public static Object begin(LivingEntity target,DamageSource source,float amount){
        var installed=factory;
        if(installed==null)return null;
        Hit frame=new Hit(installed.open(target,source,amount),current.get());current.set(frame);return frame;
    }
    public static void end(Object token){
        if(token==null)return;var parent=((Hit)token).parent();if(parent==null)current.remove();else current.set(parent);
    }
    private static Damage damage(){var frame=current.get();return frame==null?null:frame.damage();}
    public static float incoming(float amount){var d=damage();return d==null?amount:d.incoming(amount);}
    public static boolean shieldBypassed(boolean bypassed){return damage()==null && bypassed;}
    public static float shield(float vanilla,net.minecraft.world.item.component.BlocksAttacks blocks,DamageSource source){
        var d=damage();
        boolean blocked=vanilla>0 && !blocks.bypassedBy().map(t->t.contains(source.typeHolder())).orElse(false);
        return d==null?vanilla:d.shield(blocked?vanilla:0,blocked);
    }
    public static float withoutShield(){
        var d=damage();return d==null?0:Math.max(0,d.shield(0,false));
    }
    public static int shieldWear(int vanilla){var d=damage();return d==null?vanilla:d.shieldWear(vanilla);}
    public static float reduction(float after,float before,int step){var d=damage();return d==null?after:d.reduce(before,after,step);}
    public static float pre(float amount){var d=damage();return d==null?amount:d.pre(amount);}
    public static void finalAmount(float amount){var d=damage();if(d!=null)d.finalAmount(amount);}
    public static int ticks(int vanilla){var d=damage();return d==null?vanilla:d.ticks(vanilla);}
    public static boolean effects(boolean vanilla){var d=damage();return d==null?vanilla:d.sideEffects(vanilla);}
    public static void excess(int ticks){var d=damage();if(d!=null)d.excess(ticks);}
    public static void post(){var d=damage();if(d!=null)d.post();}
    public static final class Death {
        public final LivingEntity entity;public final DamageSource source;public boolean cancelled;
        Death(LivingEntity entity,DamageSource source){this.entity=entity;this.source=source;}
    }
    public static final Events.Signal<Death> DEATH=new Events.Signal<>();
    public static boolean dying(LivingEntity entity,DamageSource source) {
        if(entity.level().isClientSide())return false;
        if(!DEATH.empty()){var e=new Death(entity,source);DEATH.post(e);if(e.cancelled)return true;}
        return entity instanceof net.minecraft.server.level.ServerPlayer p && mc.unity.runtime.LifeHooks.dying(p,source);
    }
    public static final class Drops {
        public final LivingEntity entity;
        public final DamageSource source;
        public final ServerLevel level;
        public final List<ItemEntity> items = new ArrayList<>();
        public boolean recentlyHit, cancelled;
        private boolean complete;
        private final Drops parent;
        private Drops(LivingEntity entity, ServerLevel level, DamageSource source, Drops parent) {
            this.entity=entity; this.level=level; this.source=source; this.parent=parent;
        }
    }
    public static final Events.Signal<Drops> DROPS = new Events.Signal<>();
    private static final ThreadLocal<Drops> capture = new ThreadLocal<>();

    public static Drops openDeath(LivingEntity entity, DamageSource source) {
        if (DROPS.empty() || !(entity.level() instanceof ServerLevel level)) return null;
        var parent=capture.get();
        if (parent!=null && parent.entity==entity && parent.source==source) return null;
        var frame=new Drops(entity,level,source,parent);
        capture.set(frame);
        return frame;
    }
    // Enclose the virtual call, including later Mixin RETURN/TAIL callbacks on the overriding method.
    public static void die(LivingEntity entity, DamageSource source) {
        var frame=openDeath(entity,source);
        try {
            entity.die(source);
        } catch (Throwable failure) {
            finishDeath(frame,false);
            throw failure;
        }
        finishDeath(frame,true);
    }
    public static Drops beginDrops(LivingEntity entity, ServerLevel level, DamageSource source, int recent) {
        var owned=openDeath(entity,source);
        var frame=capture.get();
        if (frame!=null && frame.entity==entity && frame.level==level) frame.recentlyHit=recent>0;
        return owned;
    }
    public static boolean spawn(Level level, Entity item, Entity owner) {
        var frame=capture.get();
        if (frame!=null && frame.entity==owner && item instanceof ItemEntity drop && level==frame.level) {
            frame.items.add(drop);
            return true;
        }
        return level.addFreshEntity(item);
    }
    public static void endDrops(Drops owned, boolean success) {
        var frame=capture.get();
        if (frame!=null) frame.complete=success;
        finishDeath(owned,success);
    }
    public static void finishDeath(Drops frame, boolean success) {
        if (frame==null || capture.get()!=frame) return;
        if (frame.parent==null) capture.remove(); else capture.set(frame.parent);
        if (!success || !frame.complete) return;
        DROPS.post(frame);
        if (!frame.cancelled) for (var drop:frame.items) {
            if (drop.level()==frame.level && !drop.isRemoved()) frame.level.addFreshEntity(drop);
        }
    }
    public static void clear(){factory=null;DEATH.clear();DROPS.clear();current.remove();capture.remove();}
}
