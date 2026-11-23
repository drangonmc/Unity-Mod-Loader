package net.neoforged.neoforge.common.loot;
import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
public abstract class LootModifier implements IGlobalLootModifier {
    protected final LootItemCondition[] conditions;
    protected final int priority;
    public int priority() { return priority; }
    protected LootModifier(LootItemCondition[] conditions, int priority) {
        this.conditions = conditions.clone();
        this.priority = priority;
    }
    protected static <T extends LootModifier>
        Products.P2<RecordCodecBuilder.Mu<T>, LootItemCondition[], Integer>
        codecStart(RecordCodecBuilder.Instance<T> instance) {
        return instance.group(
            LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(m -> m.conditions),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(m -> m.priority));
    }
    public final ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context) {
        for (var condition : conditions)
            if (!condition.test(context))
                return loot;
        return doApply(loot, context);
    }
    protected abstract ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot,
                                                          LootContext context);
}
