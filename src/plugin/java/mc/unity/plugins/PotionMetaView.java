package mc.unity.plugins;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.bukkit.Color;
import org.bukkit.potion.*;
import java.util.*;

final class PotionMetaView {
    private static PotionType type(PotionContents data){
        return data.potion().map(h->h.unwrapKey().orElseThrow().identifier().toString()).flatMap(id->
            Arrays.stream(PotionType.values()).filter(p->p.getKey().toString().equals(id)).findFirst()).orElse(null);
    }
    private static PotionEffect effect(MobEffectInstance e){
        var key=e.getEffect().unwrapKey().orElseThrow().identifier();
        return new PotionEffect(org.bukkit.Registry.MOB_EFFECT.getOrThrow(org.bukkit.NamespacedKey.fromString(key.toString())),e.getDuration(),e.getAmplifier(),e.isAmbient(),e.isVisible(),e.showIcon());
    }
    static Object call(ItemStack item,String name,Object[] args){
        var v=item.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
        return switch(name){
            case "hasBasePotionType"->v.potion().isPresent();
            case "getBasePotionType"->type(v);
            case "getBasePotionData"->{
                var t=type(v);if(t==null)t=PotionType.WATER;
                String key=t.name();boolean longer=key.startsWith("LONG_"),strong=key.startsWith("STRONG_");
                yield new PotionData(longer||strong?PotionType.valueOf(key.substring(key.indexOf('_')+1)):t,longer,strong);
            }
            case "setBasePotionType","setBasePotionData"->{
                PotionType t;
                if(args[0] instanceof PotionData legacy)t=PotionType.valueOf((legacy.isExtended()?"LONG_":legacy.isUpgraded()?"STRONG_":"")+legacy.getType().name());
                else t=(PotionType)args[0];
                var potion=t==null?Optional.<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>>empty():Optional.<net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion>>of(BuiltInRegistries.POTION.get(Identifier.parse(t.getKey().toString())).orElseThrow());
                item.set(DataComponents.POTION_CONTENTS,new PotionContents(potion,v.customColor(),v.customEffects(),v.customName()));yield null;
            }
            case "getCustomEffects"->v.customEffects().stream().map(PotionMetaView::effect).toList();
            case "getAllEffects"->{var out=new ArrayList<PotionEffect>();v.getAllEffects().forEach(e->out.add(effect(e)));yield List.copyOf(out);}
            case "hasCustomEffects"->!v.customEffects().isEmpty();
            case "hasCustomEffect","clearCustomEffects","removeCustomEffect","addCustomEffect","setMainEffect"->{
                var effects=new ArrayList<>(v.customEffects());boolean changed=false;
                if(name.equals("clearCustomEffects")){changed=!effects.isEmpty();effects.clear();}
                else {
                    var input=args[0] instanceof PotionEffect e?e:null;
                    var kind=input==null?(PotionEffectType)args[0]:input.getType();
                    var h=BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(kind.getKey().toString())).orElseThrow();
                    int index=-1;for(int i=0;i<effects.size();i++)if(effects.get(i).getEffect().equals(h)){index=i;break;}
                    if(name.equals("hasCustomEffect"))yield index>=0;
                    if(name.equals("setMainEffect")){if(index>0){effects.addFirst(effects.remove(index));changed=true;}}
                    else if(input==null){if(index>=0){effects.remove(index);changed=true;}}
                    else if(index<0 || Boolean.TRUE.equals(args[1])){
                        var next=new MobEffectInstance(h,input.getDuration(),input.getAmplifier(),input.isAmbient(),input.hasParticles(),input.hasIcon());
                        if(index<0)effects.add(next);else if(next.equals(effects.set(index,next)))yield false;changed=true;
                    }
                }
                if(changed)item.set(DataComponents.POTION_CONTENTS,new PotionContents(v.potion(),v.customColor(),effects,v.customName()));yield changed;
            }
            case "hasColor"->v.customColor().isPresent();
            case "getColor"->v.customColor().map(i->Color.fromRGB(i&0xffffff)).orElse(null);
            case "computeEffectiveColor"->Color.fromRGB(v.getColor()&0xffffff);
            case "setColor"->{var c=(Color)args[0];item.set(DataComponents.POTION_CONTENTS,new PotionContents(v.potion(),Optional.ofNullable(c).map(Color::asRGB),v.customEffects(),v.customName()));yield null;}
            case "hasCustomPotionName"->v.customName().isPresent();
            case "getCustomPotionName"->v.customName().orElse(null);
            case "setCustomPotionName"->{item.set(DataComponents.POTION_CONTENTS,new PotionContents(v.potion(),v.customColor(),v.customEffects(),Optional.ofNullable((String)args[0])));yield null;}
            default->throw Views.unsupported("PotionMeta",name);
        };
    }
}
