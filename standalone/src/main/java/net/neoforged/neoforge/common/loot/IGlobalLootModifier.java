package net.neoforged.neoforge.common.loot;
import com.mojang.serialization.*;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
public interface IGlobalLootModifier {
    Codec<LootItemCondition[]> LOOT_CONDITIONS_CODEC = LootItemCondition.DIRECT_CODEC.listOf().xmap(
        list -> list.toArray(LootItemCondition[] ::new), java.util.Arrays::asList);
    Codec<IGlobalLootModifier> DIRECT_CODEC =
        Codec.lazyInitialized(()
                                  -> mc.unity.adapter.common.LootModifiers.NEO.byNameCodec().dispatch(
                                      IGlobalLootModifier::codec, c -> c));
    ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot, LootContext context);
    MapCodec<? extends IGlobalLootModifier> codec();
    int DEFAULT_PRIORITY = 1000;
    int priority();
}
