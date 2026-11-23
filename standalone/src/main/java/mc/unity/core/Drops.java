package mc.unity.core;
import java.util.*;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;

/** Bind immutable drop rules to a particular successful vanilla resource reload. */
public final class Drops {
    @FunctionalInterface
    public interface Modifier {
        ObjectArrayList<ItemStack> apply(LootTable table, ObjectArrayList<ItemStack> items,
                                         LootContext context);
    }
    public record Rules(List<Modifier> modifiers, Map<LootTable, Identifier> names) {}
    private record Active(ReloadableServerRegistries.Holder registries, Rules rules) {}
    private static final Map<ReloadableServerRegistries.Holder, Rules> loaded = new WeakHashMap<>();
    private static volatile Active active;
    private static final ThreadLocal<Identifier> queried = new ThreadLocal<>();
    public static Identifier queriedTable() { return queried.get(); }
    public static synchronized void publish(ReloadableServerRegistries.Holder resources, Rules rules) {
        loaded.put(resources, rules);
        active = null;
    }
    private static synchronized Active select(ReloadableServerRegistries.Holder resources) {
        return active = new Active(resources, loaded.get(resources));
    }
    public static void roll(LootTable table, LootParams params, Consumer<ItemStack> output,
                            Optional<Identifier> sequence) {
        roll(table, new LootContext.Builder(params).create(sequence), output);
    }
    public static void roll(LootTable table, LootContext ctx, Consumer<ItemStack> output) {
        var resources = ctx.getLevel().getServer().reloadableRegistries();
        var snapshot = active;
        if (snapshot == null || snapshot.registries != resources)
            snapshot = select(resources);
        if (snapshot.rules == null || snapshot.rules.modifiers.isEmpty()) {
            table.getRandomItemsRaw(ctx, output);
            return;
        }
        var generated = new ObjectArrayList<ItemStack>();
        table.getRandomItemsRaw(ctx, generated::add);
        var previous = queried.get();
        queried.set(snapshot.rules.names.get(table));
        try {
            for (var rule : snapshot.rules.modifiers)
                generated =
                    Objects.requireNonNull(rule.apply(table, generated, ctx), "Null loot modifier result");
        } finally {
            if (previous == null)
                queried.remove();
            else
                queried.set(previous);
        }
        generated.forEach(output);
    }
    public static synchronized void clear() {
        active = null;
        loaded.clear();
        queried.remove();
    }
}
