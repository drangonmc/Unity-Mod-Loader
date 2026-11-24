package qa.fabric;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.entries.LootItem;

public class FabricEcologyProbe implements ModInitializer {
    static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<String> NOTE =
        net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.createPersistent(
            Identifier.parse("qa_fabric_ecology:note"), com.mojang.serialization.Codec.STRING);
    static EntityType<Pig> pig;
    private static int rawLegacy(net.minecraft.nbt.CompoundTag tag) {
        return tag.getCompoundOrEmpty("unity:attachments")
            .getCompoundOrEmpty("qa_server_config:legacy")
            .getIntOr("value", -1);
    }
    static int attachments(net.minecraft.server.level.ServerLevel level, boolean write) {
        var pos = new net.minecraft.core.BlockPos(7, 201, 8);
        Object[] holders = {level.getBlockEntity(pos), level.getChunkAt(pos)};
        int offset = 0;
        for (var holder : holders) {
            var neo = (net.neoforged.neoforge.attachment.IAttachmentHolder)holder;
            var fabric = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget)holder;
            if (write)
                neo.setData(qa.config.ServerConfigProbe.LEGACY, 84 + offset);
            else if (neo.getData(qa.config.ServerConfigProbe.LEGACY) != 7)
                throw new AssertionError("Old attachment did not fall back");
            if (write) {
                neo.setData(qa.config.ServerConfigProbe.SCORE, 42 + offset);
                fabric.setAttached(NOTE, "slot-" + offset);
            }
            if (neo.getData(qa.config.ServerConfigProbe.SCORE) != 42 + offset ||
                !("slot-" + offset).equals(fabric.getAttached(NOTE)))
                throw new IllegalStateException("Attachment round trip " + offset);
            if (!write) {
                net.minecraft.nbt.CompoundTag snapshot;
                if (holder instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                    snapshot = be.saveWithFullMetadata(level.registryAccess());
                else
                    snapshot = net.minecraft.world.level.chunk.storage.SerializableChunkData
                                   .copyOf(level, (net.minecraft.world.level.chunk.ChunkAccess)holder)
                                   .write();
                if (rawLegacy(snapshot) != 84 + offset)
                    throw new AssertionError("Lost original NBT on reload " + offset);
            }
            offset++;
        }
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            var fabric = (net.fabricmc.fabric.api.attachment.v1.AttachmentTarget)(Object)player;
            var neo = (net.neoforged.neoforge.attachment.IAttachmentHolder)(Object)player;
            String name = player.getGameProfile().name();
            int value = name.equals("ConfigA") ? 61 : 79;
            if (write) {
                fabric.setAttached(NOTE, name);
                neo.setData(qa.config.ServerConfigProbe.SCORE, value);
                neo.setData(qa.config.ServerConfigProbe.LEGACY, 100 + value);
            } else if (neo.getData(qa.config.ServerConfigProbe.LEGACY) != 7)
                throw new AssertionError("Corrupt player attachment affected login");
            if (!write) {
                var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
                player.saveWithoutId(out);
                if (rawLegacy(out.buildResult()) != 100 + value)
                    throw new AssertionError("Lost original player NBT " + name);
            }
            if (!name.equals(fabric.getAttached(NOTE)) ||
                neo.getData(qa.config.ServerConfigProbe.SCORE) != value)
                throw new IllegalStateException("Player attachment " + name);
        }
        if (level.getServer().getPlayerList().getPlayerCount() != 2)
            throw new IllegalStateException("Both players required");
        if (!write)
            System.out.println("QA21 QUARANTINE healthy=4 defaults=4 originalNBT=4");
        System.out.println("QA_ATTACHMENT " + (write ? "stored" : "restored") + " tile=42 chunk=43");
        return 1;
    }
    static net.minecraft.world.item.Item currency;
    static net.minecraft.world.level.block.Block resistant;
    static class Currency extends net.minecraft.world.item.Item {
        Currency(Properties p) {
            super(p);
        }
        public boolean isPiglinCurrency(net.minecraft.world.item.ItemStack stack) {
            return stack.getCount() == 2;
        }
    }
    static class Resistant extends net.minecraft.world.level.block.Block {
        static int clientHarvests;
        Resistant(Properties p) {
            super(p);
        }
        @Override
        public void
        playerDestroy(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
                      net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                      net.minecraft.world.level.block.entity.BlockEntity be,
                      net.minecraft.world.item.ItemStack tool) {
            if (level.isClientSide()) {
                clientHarvests++;
                return;
            }
            super.playerDestroy(level, player, pos, state, be, tool);
        }
        public float getExplosionResistance(net.minecraft.world.level.block.state.BlockState state,
                                            net.minecraft.world.level.BlockGetter world,
                                            net.minecraft.core.BlockPos pos,
                                            net.minecraft.world.level.Explosion explosion) {
            return pos.getX() == 8 ? 700 : 300;
        }
    }
    static int craftArchive(net.minecraft.server.level.ServerLevel level) {
        var blank = net.minecraft.world.item.ItemStack.EMPTY;
        var shard = new net.minecraft.world.item.ItemStack(Items.PRISMARINE_SHARD);
        var diamond = new net.minecraft.world.item.ItemStack(Items.DIAMOND);
        var heart = new net.minecraft.world.item.ItemStack(Items.HEART_OF_THE_SEA);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(
            3, 3, java.util.List.of(blank, shard, heart, blank, diamond, shard, diamond, blank, blank));
        var recipe = level.getServer()
                         .getRecipeManager()
                         .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level)
                         .orElseThrow();
        var stack = recipe.value().assemble(input);
        if (!recipe.id().identifier().toString().equals("craftable_trident:trident") ||
            !stack.is(Items.TRIDENT) || stack.getCount() != 1)
            throw new IllegalStateException("Wrong author recipe " + recipe.id());
        System.out.println(
            "QA22 UNIVERSAL recipe=craftable_trident:trident result=minecraft:trident count=1");
        return 1;
    }
    private static int inspect(net.minecraft.server.level.ServerLevel level) throws Exception {
        var plains = level.registryAccess()
                         .lookupOrThrow(Registries.BIOME)
                         .getValue(Identifier.parse("minecraft:plains"));
        boolean spawn = plains.getMobSettings()
                            .getMobs(MobCategory.CREATURE)
                            .unwrap()
                            .stream()
                            .anyMatch(e -> e.value().type() == pig && e.weight() == 17);
        var feature =
            ResourceKey.create(Registries.PLACED_FEATURE, Identifier.parse("qa_fabric_ecology:sky_marker"));
        boolean placed = plains.getGenerationSettings()
                             .features()
                             .stream()
                             .flatMap(net.minecraft.core.HolderSet::stream)
                             .anyMatch(h -> h.is(feature));
        var calc = new net.minecraft.world.level.ExplosionDamageCalculator();
        var pos = new net.minecraft.core.BlockPos(8, 201, 8);
        float value = calc.getBlockExplosionResistance(null, level, pos, resistant.defaultBlockState(),
                                                       level.getFluidState(pos))
                          .orElseThrow();
        var method = net.minecraft.world.entity.monster.piglin.PiglinAi.class.getDeclaredMethod(
            "isBarterCurrency", net.minecraft.world.item.ItemStack.class);
        method.setAccessible(true);
        boolean barter = (boolean)method.invoke(null, new net.minecraft.world.item.ItemStack(currency, 2));
        boolean plain = (boolean)method.invoke(null, new net.minecraft.world.item.ItemStack(currency));
        if (!spawn || !placed || value != 700 || !barter || plain)
            throw new IllegalStateException("Biome or extension contract failed " + spawn + " " + placed +
                                            " " + value + " " + barter);
        var custom = qa.config.ServerConfigProbe.PIG.get();
        boolean accept =
            SpawnPlacements.checkSpawnRules(custom, level, EntitySpawnReason.NATURAL,
                                            new net.minecraft.core.BlockPos(0, 112, 0), level.getRandom());
        boolean deny =
            SpawnPlacements.checkSpawnRules(custom, level, EntitySpawnReason.NATURAL,
                                            new net.minecraft.core.BlockPos(0, 111, 0), level.getRandom());
        if (!accept || deny || SpawnPlacements.getPlacementType(custom) != SpawnPlacementTypes.ON_GROUND)
            throw new IllegalStateException("Custom spawn predicate");
        int count = 0;
        for (int cx = 181; cx < 184; cx++)
            for (int cz = 181; cz < 184; cz++) {
                var chunk = level.getChunk(cx, cz);
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        var spot = new net.minecraft.core.BlockPos(cx * 16 + x, 250, cz * 16 + z);
                        if (chunk.getBlockState(spot).is(
                                net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK))
                            count++;
                    }
            }
        if (count == 0)
            throw new IllegalStateException("No generated sky markers");
        System.out.println("QA_ECOLOGY spawnWeight=17 feature=true resistance=700 barter=true markers=" +
                           count);
        return count;
    }
    public static final class FilteredTanks
        extends net.fabricmc.fabric.api.transfer.v1.storage.base
                    .CombinedStorage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant,
                                     net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage> {
        int enumerations;
        long revision;
        public long getVersion() {
            return revision;
        }
        FilteredTanks() {
            super(new java.util.ArrayList<>());
            for (int i = 0; i < 32; i++) {
                var fluid = i == 0 ? net.minecraft.world.level.material.Fluids.LAVA
                                   : net.minecraft.world.level.material.Fluids.WATER;
                parts.add(new net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage() {
                    protected long getCapacity(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant v) {
                        return 81000;
                    }
                    protected boolean canInsert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant v) {
                        return v.getFluid() == fluid;
                    }
                });
            }
        }
        public java.util.Iterator<net.fabricmc.fabric.api.transfer.v1.storage
                                      .StorageView<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant>>
        iterator() {
            enumerations++;
            return super.iterator();
        }
    }
    private static Object fluidLookup(net.minecraft.world.level.block.entity.BlockEntity entity) {
        try {
            return Class.forName("mc.unity.core.SidedFluids")
                .getMethod("find", net.minecraft.world.level.block.entity.BlockEntity.class,
                           net.minecraft.core.Direction.class)
                .invoke(null, entity, net.minecraft.core.Direction.DOWN);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
    public void onInitialize() {
        FabricPlayProbe.bind();
        FabricPhase.bind();
        var itemKey = ResourceKey.create(Registries.ITEM, Identifier.parse("qa_fabric_ecology:currency"));
        currency =
            Registry.register(BuiltInRegistries.ITEM, itemKey,
                              new Currency(new net.minecraft.world.item.Item.Properties().setId(itemKey)));
        var blockKey = ResourceKey.create(Registries.BLOCK, Identifier.parse("qa_fabric_ecology:resistant"));
        resistant = Registry.register(
            BuiltInRegistries.BLOCK, blockKey,
            new Resistant(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().setId(blockKey)));
        var key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse("qa_fabric_ecology:pig"));
        pig = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, key,
            EntityType.Builder.<Pig>of(Pig::new, MobCategory.CREATURE).sized(.9f, .9f).build(key));
        FabricDefaultAttributeRegistry.register(pig, Pig.createAttributes().add(Attributes.MAX_HEALTH, 53));
        var tanks =
            new java.util.WeakHashMap<net.minecraft.world.level.block.entity.BellBlockEntity,
                                      net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage>();
        var filtered =
            new java.util
                .WeakHashMap<net.minecraft.world.level.block.entity.BellBlockEntity, FilteredTanks>();
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity((bell, face) -> {
            if (face == net.minecraft.core.Direction.DOWN)
                return filtered.computeIfAbsent(bell, b -> new FilteredTanks());
            if (face != net.minecraft.core.Direction.UP)
                return null;
            return tanks.computeIfAbsent(
                bell,
                b
                -> net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage.withFixedCapacity(
                    81019, () -> {}));
        }, net.minecraft.world.level.block.entity.BlockEntityTypes.BELL);
        net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.registerForBlockEntity((bell, face) -> {
            if (face != net.minecraft.core.Direction.UP)
                return null;
            var other = bell.getLevel().getBlockEntity(bell.getBlockPos().west());
            return other instanceof net.minecraft.world.Container inventory
                ? net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage.of(inventory, null)
                : null;
        }, net.minecraft.world.level.block.entity.BlockEntityTypes.BELL);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, access, environment)
                -> dispatcher.register(
                    net.minecraft.commands.Commands.literal("qa_transfer").executes(ctx -> {
                        var level = ctx.getSource().getLevel();
                        var origin = new net.minecraft.core.BlockPos(8, 201, 8);
                        var entity = level.getBlockEntity(origin);
                        var inventory = (net.minecraft.world.Container)level.getBlockEntity(origin.west());
                        var handler = qa.forgeconfig.ForgeInventoryProbe.handler(entity);
                        qa.forgeconfig.ForgeInventoryProbe.checkInsert(handler);
                        qa.forgeconfig.ForgeInventoryProbe.take(handler, 4, true);
                        if (inventory.getItem(0).getCount() != 7)
                            throw new AssertionError("simulation leaked");
                        try (var root =
                                 net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                            qa.forgeconfig.ForgeInventoryProbe.take(handler, 3, false);
                        }
                        if (inventory.getItem(0).getCount() != 7)
                            throw new AssertionError("abort leaked");
                        try (var root =
                                 net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                            qa.forgeconfig.ForgeInventoryProbe.take(handler, 2, false);
                            root.commit();
                        }
                        if (inventory.getItem(0).getCount() != 5)
                            throw new AssertionError("commit amount");
                        qa.forgeconfig.ForgeInventoryProbe.energyAndFluid(entity);
                        var tested = filtered.get(entity);
                        if (tested.enumerations != 1 || tested.parts.stream().anyMatch(t -> t.amount != 0))
                            throw new AssertionError("Tank scan or rollback " + tested.enumerations);
                        if (!qa.forgeconfig.ForgeInventoryProbe.resize(entity, () -> {
                                tested.parts.subList(1, tested.parts.size()).clear();
                                tested.revision++;
                            }))
                            throw new AssertionError("tank topology stale");
                        if (tested.enumerations != 2)
                            throw new AssertionError("topology refresh count " + tested.enumerations);
                        if (java.util.concurrent.CompletableFuture.supplyAsync(() -> fluidLookup(entity))
                                .join() != null)
                            throw new AssertionError("off-thread fluid access");
                        System.out.println("QA21 TANKS count=32 enumerations=1 rejectedWater=true "
                                           + "acceptedLava=true rollback=true resized=1 refreshed=1");
                        var drops = tanks.get(entity);
                        if (drops.amount != 60750)
                            throw new IllegalStateException("Fabric fluid remainder " + drops.amount);
                        System.out.println("QA_RESOURCES energy=23 fluidMb=750 droplets=60750");
                        level.removeBlock(origin, false);
                        if (fluidLookup(entity) != null)
                            throw new AssertionError("Removed fluid capability");
                        if (handler.getSlots() != 0)
                            throw new AssertionError("removed block handle still active");
                        System.out.println("QA_TRANSFER simulated=7 aborted=7 committed=5 invalidated=true");
                        return 1;
                    })));
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (d, access,
             env) -> d.register(net.minecraft.commands.Commands.literal("qa_ecology").executes(c -> {
                try {
                    return inspect(c.getSource().getLevel());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            })));
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, access, env) -> {
            d.register(net.minecraft.commands.Commands.literal("qa_attachment_save")
                           .executes(c -> attachments(c.getSource().getLevel(), true)));
            d.register(net.minecraft.commands.Commands.literal("qa_attachment_read")
                           .executes(c -> attachments(c.getSource().getLevel(), false)));
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (d, access,
             env) -> d.register(net.minecraft.commands.Commands.literal("qa_phase_play").executes(c -> {
                qa.config.NeoPhase.play(c.getSource().getPlayerOrException());
                return 1;
            })));
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (d, access, env)
                -> d.register(net.minecraft.commands.Commands.literal("qa_craft_archive")
                                  .executes(c -> craftArchive(c.getSource().getLevel()))));
        LootTableEvents.MODIFY.register((id, builder, source, registries) -> {
            if (id.identifier().getNamespace().equals("qa_fabric_ecology")) {
                builder.withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.AMETHYST_SHARD)));
                System.out.println("QA_LOOT " + id.identifier() + " " + source);
            }
        });
    }
}
