package mc.unity.plugins;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.potion.*;

/** Registry values used by Bukkit potion metadata and rule flags. */
final class EffectView extends PotionEffectType {
    private final NamespacedKey key;
    private final MobEffect effect;
    EffectView(NamespacedKey key,MobEffect effect){this.key=key;this.effect=effect;}
    public NamespacedKey getKey(){return key;}
    public String getName(){return key.getKey().toUpperCase(Locale.ROOT);}
    public String getTranslationKey(){return effect.getDescriptionId();}
    public String translationKey(){return getTranslationKey();}
    public int getId(){return BuiltInRegistries.MOB_EFFECT.getId(effect)+1;}
    public boolean isInstant(){return effect.isInstantaneous();}
    public double getDurationModifier(){return 1.0;}
    public Color getColor(){return Color.fromRGB(effect.getColor());}
    public PotionEffectType.Category getEffectCategory(){return PotionEffectType.Category.valueOf(effect.getCategory().name());}
    public PotionEffectTypeCategory getCategory(){return PotionEffectTypeCategory.valueOf(effect.getCategory().name());}
    public PotionEffect createEffect(int duration,int amplifier){return new PotionEffect(this,isInstant()?1:duration,amplifier);}
    public Map<Attribute,AttributeModifier> getEffectAttributes(){throw Views.unsupported("PotionEffectType","attribute modifiers");}
    public double getAttributeModifierAmount(Attribute a,int amplifier){throw Views.unsupported("PotionEffectType","attribute modifier amount");}
}
