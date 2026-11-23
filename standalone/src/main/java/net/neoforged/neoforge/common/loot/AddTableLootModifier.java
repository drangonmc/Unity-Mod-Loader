package net.neoforged.neoforge.common.loot;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
public class AddTableLootModifier extends LootModifier {
    public static final MapCodec<AddTableLootModifier> CODEC =
        RecordCodecBuilder.mapCodec(i
                                    -> LootModifier.<AddTableLootModifier>codecStart(i)
                                           .and(ResourceKey.codec(Registries.LOOT_TABLE)
                                                    .fieldOf("table")
                                                    .forGetter(AddTableLootModifier::table))
                                           .apply(i, AddTableLootModifier::new));
    private final ResourceKey<LootTable> table;
    public AddTableLootModifier(LootItemCondition[] conditions, int priority, ResourceKey<LootTable> table) {
        super(conditions, priority);
        this.table = table;
    }
    public ResourceKey<LootTable> table() { return table; }
    public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext c) {
        c.getResolver()
            .lookupOrThrow(Registries.LOOT_TABLE)
            .get(table)
            .ifPresent(h -> h.value().getRandomItemsRaw(c, loot::add));
        return loot;
    }
}
