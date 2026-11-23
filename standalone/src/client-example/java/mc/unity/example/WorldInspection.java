package mc.unity.example;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

final class WorldInspection {
    private static Identifier id(String value) { return Identifier.fromNamespaceAndPath("worldprobe", value); }
    private static Integer value(Level level, String registry, String key) {
        var type = ResourceKey.<Integer>createRegistryKey(id(registry));
        return level.registryAccess().lookup(type).map(r -> r.getValue(id(key))).orElse(null);
    }
    @SuppressWarnings("unchecked")
    private static DataComponentType<Integer> component() {
        return (DataComponentType<Integer>)BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id("score"));
    }
    static JsonObject shared(Level level, Player player) {
        var r = new JsonObject();
        var ids = new JsonArray();
        for (var pair : List.of(Map.entry(BuiltInRegistries.SOUND_EVENT, "chime"),
                                Map.entry(BuiltInRegistries.ENTITY_TYPE, "crystal"),
                                Map.entry(BuiltInRegistries.DATA_COMPONENT_TYPE, "score"),
                                Map.entry(BuiltInRegistries.RECIPE_SERIALIZER, "convert"))) {
            Registry reg = pair.getKey();
            Object value = reg.getOptional(id(pair.getValue())).orElseThrow();
            ids.add(reg.getId(value));
        }
        r.addProperty("neoLabel", level.registryAccess()
                                      .lookupOrThrow(ResourceKey.<String>createRegistryKey(
                                          Identifier.parse("worldprobe_neo:labels")))
                                      .getValue(Identifier.parse("worldprobe_neo:sample")));
        r.add("numericIds", ids);
        r.addProperty("worldValue", value(level, "tuning", "world_only"));
        r.addProperty("baselineValue", value(level, "tuning", "value"));
        r.addProperty("privateRule", value(level, "server_rules", "value"));
        var stack = player.getInventory().getItem(0);
        r.addProperty("count", stack.getCount());
        r.addProperty("component", stack.get(component()));
        var entities = level.getEntitiesOfClass(
            Entity.class, new AABB(36, 199, 31, 55, 208, 45),
            e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(id("crystal")));
        var uuids = new JsonArray();
        entities.forEach(e -> uuids.add(e.getUUID().toString()));
        r.add("entities", uuids);
        var be = level.getBlockEntity(new BlockPos(44, 201, 36));
        if (be != null)
            r.addProperty("tile", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString());
        return r;
    }
    static JsonObject server(net.minecraft.server.MinecraftServer server, Player player) {
        var level = server.overworld();
        var r = shared(level, player);
        var recipe = (CraftingRecipe)server.getRecipeManager()
                         .byKey(ResourceKey.create(Registries.RECIPE, id("convert")))
                         .orElseThrow()
                         .value();
        var input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.STICK)));
        if (!recipe.matches(input, level))
            throw new IllegalStateException("Custom recipe does not match");
        r.addProperty("recipeResult", recipe.assemble(input).get(component()));
        return r;
    }
    static JsonObject client(Minecraft mc) throws Exception {
        var r = shared(mc.level, mc.player);
        for (String name : List.of("glass_panel", "grate")) {
            var state = BuiltInRegistries.BLOCK.getOptional(id(name)).orElseThrow().defaultBlockState();
            var model = mc.getModelManager().getBlockStateModelSet().get(state);
            var parts = new ArrayList<BlockStateModelPart>();
            model.collectParts(RandomSource.create(41), parts);
            var layers = new TreeSet<String>();
            for (var part : parts) {
                for (var face : Direction.values())
                    part.getQuads(face).forEach(q -> layers.add(q.materialInfo().layer().name()));
                part.getQuads(null).forEach(q -> layers.add(q.materialInfo().layer().name()));
            }
            r.addProperty(name, String.join(",", layers));
            r.addProperty(name + "Model", model.getClass().getName());
            if (name.equals("glass_panel"))
                r.addProperty("modelSession", (int)model.getClass().getMethod("session").invoke(model));
        }
        r.addProperty("soundWeight", mc.getSoundManager().getSoundEvent(id("chime")).getWeight());
        var mod = Class.forName("qa.worlds.Visuals", false, mc.getClass().getClassLoader());
        r.addProperty("originalGrate", (String)mod.getField("originalGrate").get(null));
        for (String field : List.of("sessions", "providers", "draws", "sounds", "lastDrawnProvider")) {
            var counter = (java.util.concurrent.atomic.AtomicInteger)mod.getField(field).get(null);
            r.addProperty(field, counter.get());
        }
        return r;
    }
}
