package mc.unity.plugins;

import org.bukkit.Location;
import org.bukkit.damage.*;
import org.bukkit.entity.Entity;
import net.kyori.adventure.pointer.Pointers;
import java.util.Objects;

record PluginDamageSource(DamageType getDamageType, Entity getCausingEntity, Entity getDirectEntity,
                          Location position, Pointers getDamageContext) implements DamageSource {
    public Location getDamageLocation(){return position==null?null:position.clone();}
    public Location getSourceLocation(){return position!=null?position.clone():getCausingEntity!=null?getCausingEntity.getLocation():null;}
    public boolean isIndirect(){return getCausingEntity!=getDirectEntity;}
    public float getFoodExhaustion(){return getDamageType.getExhaustion();}
    public boolean scalesWithDifficulty(){return switch(getDamageType.getDamageScaling()){
        case ALWAYS -> true;
        case NEVER -> false;
        case WHEN_CAUSED_BY_LIVING_NON_PLAYER -> getCausingEntity instanceof org.bukkit.entity.LivingEntity && !(getCausingEntity instanceof org.bukkit.entity.Player);
    };}
    static final class Builder implements DamageSource.Builder{
        final DamageType type;Entity causing,direct;Location position;
        final Pointers.Builder pointers=Pointers.builder();
        Builder(DamageType type){this.type=Objects.requireNonNull(type);}
        public Builder withDirectEntity(Entity e){direct=Objects.requireNonNull(e);return this;}
        public Builder withCausingEntity(Entity e){causing=Objects.requireNonNull(e);return this;}
        public Builder withDamageLocation(Location at){position=Objects.requireNonNull(at).clone();return this;}
        public Builder withDamageContext(java.util.function.Consumer<Pointers.Builder> change){change.accept(pointers);return this;}
        public DamageSource build(){return new PluginDamageSource(type,causing,direct,position==null?null:position.clone(),pointers.build());}
    }
}
