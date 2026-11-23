package qa.worlds;
import com.mojang.serialization.Codec;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.*;
@Mod("worldprobe_neo")
public class NeoMod {
    static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities("worldprobe_neo");
    static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "worldprobe_neo");
    public static final DeferredHolder<EntityType<?>, EntityType<EndCrystal>> CRYSTAL =
        ENTITIES.registerEntityType("orb", EndCrystal::new, MobCategory.MISC, b -> b.sized(1, 1));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> NAME =
        COMPONENTS.registerComponentType("name", b -> b.persistent(Codec.STRING));
    public NeoMod(IEventBus bus, Dist side) {
        ENTITIES.register(bus);
        COMPONENTS.register(bus);
        bus.addListener(DataPackRegistryEvent.NewRegistry.class,
                        e
                        -> e.dataPackRegistry(
                            ResourceKey.<String>createRegistryKey(Identifier.parse("worldprobe_neo:labels")),
                            Codec.STRING, Codec.STRING));
        if (side.isClient())
            Client.bind(bus);
    }
    private static class Client {
        static void bind(IEventBus bus) {
            bus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class,
                            e
                            -> e.registerEntityRenderer(
                                CRYSTAL.get(), net.minecraft.client.renderer.entity.EndCrystalRenderer::new));
        }
    }
}
