package net.minecraftforge.common.loot;
import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
public abstract class LootModifier implements IGlobalLootModifier {
    protected final LootItemCondition[] conditions;

    protected LootModifier(LootItemCondition[] conditions) { this.conditions = conditions.clone(); }
    protected static <T extends LootModifier> Products.P1<RecordCodecBuilder.Mu<T>, LootItemCondition[]>
    codecStart(RecordCodecBuilder.Instance<T> instance) {
        return instance.group(LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(m -> m.conditions));
    }
    public final ObjectArrayList<ItemStack> apply(LootTable table, ObjectArrayList<ItemStack> loot,
                                                  LootContext context) {
        for (var condition : conditions)
            if (!condition.test(context))
                return loot;
        return doApply(table, loot, context);
    }
    protected abstract ObjectArrayList<ItemStack> doApply(LootTable table, ObjectArrayList<ItemStack> loot,
                                                          LootContext context);
}
