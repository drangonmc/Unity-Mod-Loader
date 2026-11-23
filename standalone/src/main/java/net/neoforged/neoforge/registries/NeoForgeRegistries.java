package net.neoforged.neoforge.registries;
import mc.unity.core.ExtraRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.neoforged.neoforge.attachment.AttachmentType;
public final class NeoForgeRegistries {
    public static final class Keys {
        public static final ResourceKey<? extends Registry<?>> BIOME_MODIFIERS =
            ResourceKey.createRegistryKey(Identifier.parse("neoforge:biome_modifier"));
        public static final ResourceKey<? extends Registry<?>> BIOME_MODIFIER_SERIALIZERS =
            ResourceKey.createRegistryKey(Identifier.parse("neoforge:biome_modifier_serializers"));
        public static final ResourceKey<? extends Registry<?>> ENTITY_DATA_SERIALIZERS =
            ResourceKey.createRegistryKey(Identifier.parse("neoforge:entity_data_serializers"));
        public static final ResourceKey<Registry<com.mojang.serialization.MapCodec<
            ? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>>>
            GLOBAL_LOOT_MODIFIER_SERIALIZERS =
                ResourceKey.createRegistryKey(Identifier.parse("neoforge:global_loot_modifier_serializers"));
        public static final ResourceKey<Registry<AttachmentType<?>>> ATTACHMENT_TYPES =
            ResourceKey.createRegistryKey(Identifier.parse("neoforge:attachment_types"));
    }
    public static final Registry<AttachmentType<?>> ATTACHMENT_TYPES =
        ExtraRegistries.create(Keys.ATTACHMENT_TYPES);
    public static final Registry<
        com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.loot.IGlobalLootModifier>>
        GLOBAL_LOOT_MODIFIER_SERIALIZERS = mc.unity.adapter.common.LootModifiers.NEO;
    public static void initialize() {}
}
