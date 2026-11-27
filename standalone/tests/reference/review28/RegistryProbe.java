package qa.review28;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("unity28registry")
public final class RegistryProbe {
    public RegistryProbe(String unsupported) { throw new AssertionError("Unsupported constructor selected"); }
    public RegistryProbe(IEventBus bus) {
        var effects=DeferredRegister.create(BuiltInRegistries.MOB_EFFECT,"unity28registry");
        var effect=effects.register("marked",()->new MobEffect(MobEffectCategory.BENEFICIAL,0x665544){});
        var potions=DeferredRegister.create(BuiltInRegistries.POTION,"unity28registry");
        var potion=potions.register("marked",()->new Potion("marked",new MobEffectInstance(effect,1200)));
        var attributes=DeferredRegister.create(BuiltInRegistries.ATTRIBUTE,"unity28registry");
        var attribute=attributes.register("rating",()->new RangedAttribute("unity28.rating",2,0,10));
        var recipes=DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE,"unity28registry");
        var recipe=recipes.register("press",()->new RecipeType<Recipe<?>>(){});
        potions.register(bus);recipes.register(bus);effects.register(bus);attributes.register(bus);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class,e->{
            if (BuiltInRegistries.POTION.getId(potion.get())<0 || BuiltInRegistries.ATTRIBUTE.getId(attribute.get())<0
                    || BuiltInRegistries.RECIPE_TYPE.getId(recipe.get())<0 || BuiltInRegistries.MOB_EFFECT.getId(effect.get())<0)
                throw new AssertionError("Missing registered content");
            if(Boolean.getBoolean("unity.test.onlyNeo")) {
                var mods=net.neoforged.fml.ModList.get();
                if(!mods.isLoaded("neoforge") || mods.isLoaded("forge") || mods.isLoaded("fabricloader"))throw new AssertionError("Platform identity");
                try {
                    var get=Class.forName("mc.unity.core.Content").getDeclaredMethod("registries");
                    get.setAccessible(true);
                    var active=(java.util.Collection<?>)get.invoke(null);
                    for(var registry:BuiltInRegistries.REGISTRY)if(!active.contains(registry))throw new AssertionError(registry.key());
                    System.out.println("UNITY28_ROOT_ALL "+BuiltInRegistries.REGISTRY.size());
                } catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
                System.out.println("UNITY28_PLATFORM_IDS_OK");
            }
            optional(false);
            try {optional(true);throw new AssertionError("Missing guard");}
            catch(UnsupportedOperationException expected) {
                if(!expected.getMessage().contains("unity28registry.jar") || !expected.getMessage().contains("OptionalWidget"))throw expected;
            }
            System.out.println("UNITY28_REGISTRIES_OK effect potion attribute recipe; optional branch guarded");
        });
    }
    static void optional(boolean installed) {
        if(installed) net.neoforged.testoptional.OptionalWidget.render();
    }
}
