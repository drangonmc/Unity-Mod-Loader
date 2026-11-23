package net.neoforged.neoforge.resource;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.*;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.crafting.RecipeManager;
public final class VanillaServerListeners {
    public static final Identifier RECIPES = Identifier.withDefaultNamespace("recipe_manager"),
                                   FUNCTIONS = Identifier.withDefaultNamespace("server_function_library"),
                                   ADVANCEMENTS =
                                       Identifier.withDefaultNamespace("server_advancement_manager"),
                                   FIRST = RECIPES, LAST = ADVANCEMENTS;
    private static final Map<Class<?>, Identifier> NAMES =
        Map.of(RecipeManager.class, RECIPES, ServerFunctionLibrary.class, FUNCTIONS,
               ServerAdvancementManager.class, ADVANCEMENTS);
    public static Identifier getNameForClass(Class<? extends PreparableReloadListener> type) {
        return NAMES.get(type);
    }
}
