package qa.worlds;
import com.mojang.serialization.Codec;
import java.util.Set;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.*;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;
@Mod(ContentMod.ID)
public final class ContentMod {
    public static final String ID = "worldprobe";
    public static Identifier id(String value) { return Identifier.fromNamespaceAndPath(ID, value); }
    static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    static final DeferredRegister<BlockEntityType<?>> TILES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ID);
    static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    static final DeferredRegister<RecipeSerializer<?>> RECIPES =
        DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ID);
    public static final RegistryObject<Block> GLASS = block("glass_panel", false),
                                              GRATE = block("grate", false), STAND = block("bell_stand", true);
    public static final RegistryObject<BlockEntityType<Tile>> TILE =
        TILES.register("bell_stand", () -> new BlockEntityType<>(Tile::new, Set.of(STAND.get())));
    public static final RegistryObject<EntityType<EndCrystal>> CRYSTAL =
        ENTITIES.register("crystal",
                          ()
                              -> EntityType.Builder.<EndCrystal>of(EndCrystal::new, MobCategory.MISC)
                                     .sized(1, 1)
                                     .build(ResourceKey.create(Registries.ENTITY_TYPE, id("crystal"))));
    public static final RegistryObject<SoundEvent> CHIME =
        SOUNDS.register("chime", () -> SoundEvent.createVariableRangeEvent(id("chime")));
    public static final RegistryObject<DataComponentType<Integer>> SCORE =
        COMPONENTS.register("score",
                            ()
                                -> DataComponentType.<Integer>builder()
                                       .persistent(Codec.INT)
                                       .networkSynchronized(ByteBufCodecs.VAR_INT)
                                       .build());
    public static final RegistryObject<RecipeSerializer<Convert>> CONVERT = RECIPES.register(
        "convert",
        ()
            -> new RecipeSerializer<Convert>(Codec.INT.fieldOf("score").xmap(Convert::new, r -> r.value),
                                             net.minecraft.network.codec.StreamCodec.composite(
                                                 ByteBufCodecs.VAR_INT, (Convert r) -> r.value, Convert::new)));
    public static final class Convert extends CustomRecipe {
        private final int value;
        Convert(int value) { this.value = value; }
        public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
            return input.size() == 1 && input.getItem(0).is(Items.STICK);
        }
        public ItemStack assemble(CraftingInput input) {
            var result = new ItemStack(Items.EMERALD);
            result.set(SCORE.get(), value);
            return result;
        }
        public RecipeSerializer<Convert> getSerializer() { return CONVERT.get(); }
    }
    static RegistryObject<Block> block(String name, boolean tile) {
        var key = ResourceKey.create(Registries.BLOCK, id(name));
        var itemKey = ResourceKey.create(Registries.ITEM, id(name));
        var block = BLOCKS.register(name, () -> {
            var props = BlockBehaviour.Properties.of().strength(1).noOcclusion().setId(key);
            return tile ? new Stand(props) : new Block(props);
        });
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().setId(itemKey)));
        return block;
    }
    public static final class Stand extends Block implements EntityBlock {
        Stand(BlockBehaviour.Properties props) { super(props); }
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new Tile(pos, state); }
    }
    public static final class Tile extends BlockEntity {
        Tile(BlockPos pos, BlockState state) { super(TILE.get(), pos, state); }
    }
    public ContentMod(FMLJavaModLoadingContext ctx) {
        var bus = ctx.getModBusGroup();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TILES.register(bus);
        SOUNDS.register(bus);
        ENTITIES.register(bus);
        COMPONENTS.register(bus);
        RECIPES.register(bus);
        net.minecraftforge.event.RegisterCommandsEvent.BUS.addListener(
            e
            -> e.getDispatcher().register(
                net.minecraft.commands.Commands.literal("worldprobe_inspect").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    var level = player.level();
                    var key = ResourceKey.<Integer>createRegistryKey(id("tuning"));
                    var rule = ResourceKey.<Integer>createRegistryKey(id("server_rules"));
                    var recipe = (CraftingRecipe)c.getSource()
                                     .getServer()
                                     .getRecipeManager()
                                     .byKey(ResourceKey.create(Registries.RECIPE, id("convert")))
                                     .orElseThrow()
                                     .value();
                    var input = CraftingInput.of(1, 1, java.util.List.of(new ItemStack(Items.STICK)));
                    if (!recipe.matches(input, level) || recipe.getSerializer() != CONVERT.get())
                        throw new IllegalStateException("Incorrect custom recipe");
                    var result = new com.google.gson.JsonObject();
                    var numbers = new com.google.gson.JsonArray();
                    numbers.add(BuiltInRegistries.SOUND_EVENT.getId(CHIME.get()));
                    numbers.add(BuiltInRegistries.ENTITY_TYPE.getId(CRYSTAL.get()));
                    numbers.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(SCORE.get()));
                    numbers.add(BuiltInRegistries.RECIPE_SERIALIZER.getId(CONVERT.get()));
                    result.add("numericIds", numbers);
                    result.addProperty("worldValue",
                                       level.registryAccess().lookupOrThrow(key).getValue(id("world_only")));
                    result.addProperty("privateRule",
                                       level.registryAccess().lookupOrThrow(rule).getValue(id("value")));
                    result.addProperty("component", player.getInventory().getItem(0).get(SCORE.get()));
                    result.addProperty("recipeResult", recipe.assemble(input).get(SCORE.get()));
                    var ids = new com.google.gson.JsonArray();
                    level
                        .getEntitiesOfClass(EndCrystal.class,
                                            new net.minecraft.world.phys.AABB(36, 199, 31, 55, 208, 45),
                                            entity -> entity.getType() == CRYSTAL.get())
                        .forEach(entity -> ids.add(entity.getUUID().toString()));
                    result.add("entities", ids);
                    System.out.println("QA14 NETWORK " + result);
                    return 1;
                })));
        DataPackRegistryEvent.NewRegistry.BUS.addListener(
            e -> e.dataPackRegistry(ResourceKey.createRegistryKey(id("server_rules")), Codec.INT));
    }
}
