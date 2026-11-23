package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ForgeRegistries {
    private ForgeRegistries() {}
    public static final IForgeRegistry<Block> BLOCKS = new View<>(BuiltInRegistries.BLOCK);
    public static final IForgeRegistry<Item> ITEMS = new View<>(BuiltInRegistries.ITEM);
    public static final IForgeRegistry<net.minecraft.world.level.block.entity.BlockEntityType<?>>
        BLOCK_ENTITY_TYPES = new View<>(BuiltInRegistries.BLOCK_ENTITY_TYPE);
    public static final IForgeRegistry<net.minecraft.world.inventory.MenuType<?>> MENU_TYPES =
        new View<>(BuiltInRegistries.MENU);
    public static final IForgeRegistry<net.minecraft.sounds.SoundEvent> SOUND_EVENTS = new View<>(BuiltInRegistries.SOUND_EVENT);
    public static final IForgeRegistry<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES = new View<>(BuiltInRegistries.ENTITY_TYPE);
    public static final IForgeRegistry<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = new View<>(BuiltInRegistries.RECIPE_SERIALIZER);
    public static final class Keys {
        public static final ResourceKey<Registry<com.mojang.serialization.MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>>> GLOBAL_LOOT_MODIFIER_SERIALIZERS=
            ResourceKey.createRegistryKey(Identifier.parse("forge:global_loot_modifier_serializers"));
    }
    public static final DeferredRegister.RegistryHolder<com.mojang.serialization.MapCodec<? extends net.minecraftforge.common.loot.IGlobalLootModifier>>
        GLOBAL_LOOT_MODIFIER_SERIALIZERS=new DeferredRegister.RegistryHolder<>(new View<>(mc.unity.adapter.common.LootModifiers.FORGE));
    private record View<T>(Registry<T> registry) implements IForgeRegistry<T> {
        public ResourceKey<? extends Registry<T>> getRegistryKey() {
            return registry.key();
        }
        public T getValue(Identifier id) {
            return registry.getValue(id);
        }
        public Identifier getKey(T value) {
            return registry.getKey(value);
        }
    }
}
