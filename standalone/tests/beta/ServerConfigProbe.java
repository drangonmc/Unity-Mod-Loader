package qa.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@Mod("qa_server_config")
public final class ServerConfigProbe {
    static final net.neoforged.neoforge.registries
        .DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACH =
        net.neoforged.neoforge.registries.DeferredRegister.create(
            net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES, "qa_server_config");
    public static final java.util.function
        .Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> SCORE =
        ATTACH.register("score",
                        ()
                            -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 3)
                                   .serialize(com.mojang.serialization.Codec.INT.fieldOf("score"))
                                   .build());
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> LEGACY =
        ATTACH.register("legacy",()->{
            com.mojang.serialization.Codec<Integer> codec=java.nio.file.Files.exists(java.nio.file.Path.of("config/qa21-new-schema"))?
                com.mojang.serialization.Codec.STRING.xmap(Integer::valueOf,Object::toString):com.mojang.serialization.Codec.INT;
            return net.neoforged.neoforge.attachment.AttachmentType.builder(()->7).serialize(codec.fieldOf("value")).build();
        });
    static final net.neoforged.neoforge.registries.DeferredRegister.Entities ENTITIES =
        net.neoforged.neoforge.registries.DeferredRegister.createEntities("qa_server_config");
    public static final net.neoforged.neoforge.registries.DeferredHolder<
        net.minecraft.world.entity.EntityType<?>,
        net.minecraft.world.entity.EntityType<net.minecraft.world.entity.animal.pig.Pig>> PIG =
        ENTITIES.registerEntityType("pig", net.minecraft.world.entity.animal.pig.Pig::new,
                                    net.minecraft.world.entity.MobCategory.CREATURE, b -> b.sized(.9f, .9f));
    public ServerConfigProbe(IEventBus bus, ModContainer owner, net.neoforged.api.distmarker.Dist side) {
        NeoPhase.bind(bus,side);
        bus.addListener(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent.class, e -> {
            var storage =
                new java.util.WeakHashMap<net.minecraft.world.level.block.entity.BellBlockEntity,
                                          net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler>();
            e.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                net.minecraft.world.level.block.entity.BlockEntityTypes.BELL,
                (bell, face)
                    -> face == net.minecraft.core.Direction.UP
                           ? storage.computeIfAbsent(
                                 bell,
                                 b -> new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(100))
                           : null);
        });
        ATTACH.register(bus);
        ENTITIES.register(bus);
        bus.addListener(
            net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent.class,
            e
            -> e.put(PIG.get(), net.minecraft.world.entity.animal.pig.Pig.createAttributes()
                                    .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 47)
                                    .build()));
        bus.addListener(
            net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.class,
            e
            -> e.register(
                PIG.get(), net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, level, reason, pos, random)
                    -> pos.getY() == 112,
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE));
        if (side.isClient())
            Visuals.bind(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            net.neoforged.neoforge.event.AddServerReloadListenersEvent.class, e -> {
                var a = net.minecraft.resources.Identifier.parse("qa_server_config:first");
                var b = net.minecraft.resources.Identifier.parse("qa_server_config:second");
                net.minecraft.server.packs.resources.ResourceManagerReloadListener second =
                    manager -> System.out.println("QA_RELOAD second");
                net.minecraft.server.packs.resources.ResourceManagerReloadListener first =
                    manager -> System.out.println("QA_RELOAD first");
                e.addListener(b, second);
                e.addListener(a, first);
                e.addDependency(a, b);
            });
        var spec = new ModConfigSpec.Builder();
        var limit = spec.defineInRange("limit", 11, 1, 99);
        owner.registerConfig(ModConfig.Type.SERVER, spec.build());
        bus.addListener((ModConfigEvent.Loading e) -> log("load", limit.get()));
        bus.addListener((ModConfigEvent.Reloading e) -> log("reload", limit.get()));
        bus.addListener((ModConfigEvent.Unloading e) -> log("unload", limit.get()));
    }
    private static class Visuals {
        static final net.minecraft.client.model.geom.ModelLayerLocation LAYER =
            new net.minecraft.client.model.geom.ModelLayerLocation(
                net.minecraft.resources.Identifier.parse("qa_server_config:pig"), "test");
        static void bind(IEventBus bus) {
            bus.addListener(
                net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.class,
                e
                -> e.registerLayerDefinition(
                    LAYER,
                    ()
                        -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                            new net.minecraft.client.model.geom.builders.MeshDefinition(), 16, 16)));
            bus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class,
                            e -> e.registerEntityRenderer(PIG.get(), ctx -> {
                                ctx.bakeLayer(LAYER);
                                System.out.println("QA_MODEL custom layer baked");
                                return new net.minecraft.client.renderer.entity.PigRenderer(ctx);
                            }));
        }
    }
    private static void log(String action, int value) {
        System.out.println("QA_SERVER_CONFIG " + action + " " + value + " " +
                           Thread.currentThread().getName());
    }
}
