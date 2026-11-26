package mc.unity.plugins;

/** Service provider for the pinned Paper API. Unsupported services fail at the call site. */
public final class InternalBridge implements io.papermc.paper.InternalAPIBridge {
    @Override
    public org.bukkit.block.Biome constructLegacyCustomBiome() {
        throw new UnsupportedOperationException("Paper API: constructLegacyCustomBiome");
    }
    @Override
    public io.papermc.paper.world.damagesource.CombatEntry
    createCombatEntry(org.bukkit.entity.LivingEntity arg0, org.bukkit.damage.DamageSource arg1, float arg2) {
        throw new UnsupportedOperationException("Paper API: createCombatEntry");
    }
    @Override
    public io.papermc.paper.world.damagesource.CombatEntry
    createCombatEntry(org.bukkit.damage.DamageSource arg0, float arg1,
                      io.papermc.paper.world.damagesource.FallLocationType arg2, float arg3) {
        throw new UnsupportedOperationException("Paper API: createCombatEntry");
    }
    @Override
    public java.util.function.Predicate<io.papermc.paper.command.brigadier.CommandSourceStack>
    restricted(java.util.function.Predicate<io.papermc.paper.command.brigadier.CommandSourceStack> arg0) {
        throw new UnsupportedOperationException("Paper API: restricted");
    }
    @Override
    public io.papermc.paper.datacomponent.item.ResolvableProfile defaultMannequinProfile() {
        throw new UnsupportedOperationException("Paper API: defaultMannequinProfile");
    }
    @Override
    public com.destroystokyo.paper.SkinParts.Mutable allSkinParts() {
        throw new UnsupportedOperationException("Paper API: allSkinParts");
    }
    @Override
    public net.kyori.adventure.text.Component defaultMannequinDescription() {
        throw new UnsupportedOperationException("Paper API: defaultMannequinDescription");
    }
    @Override
    public <MODERN, LEGACY> org.bukkit.GameRule<LEGACY>
    legacyGameRuleBridge(org.bukkit.GameRule<MODERN> arg0, java.util.function.Function<LEGACY, MODERN> arg1,
                         java.util.function.Function<MODERN, LEGACY> arg2, java.lang.Class<LEGACY> arg3) {
        throw new UnsupportedOperationException("Paper API: legacyGameRuleBridge");
    }
    @Override
    public java.util.Set<org.bukkit.entity.Pose> validMannequinPoses() {
        throw new UnsupportedOperationException("Paper API: validMannequinPoses");
    }
    @Override
    public io.papermc.paper.entity.poi.PoiType.Occupancy createOccupancy(java.lang.String arg0) {
        throw new UnsupportedOperationException("Paper API: createOccupancy");
    }
    @Override
    public org.bukkit.damage.DamageSource.Builder
    createDamageSourceBuilder(org.bukkit.damage.DamageType arg0) {
        throw new UnsupportedOperationException("Paper API: createDamageSourceBuilder");
    }
    @Override
    public org.bukkit.damage.DamageEffect getDamageEffect(java.lang.String arg0) {
        throw new UnsupportedOperationException("Paper API: getDamageEffect");
    }
    @Override
    public java.lang.String getTranslationKey(org.bukkit.entity.EntityType arg0) {
        throw new UnsupportedOperationException("Paper API: getTranslationKey");
    }
    @Override
    public org.bukkit.entity.SpawnCategory getSpawnCategory(org.bukkit.entity.EntityType arg0) {
        throw new UnsupportedOperationException("Paper API: getSpawnCategory");
    }
    @Override
    public org.bukkit.inventory.ItemStack deserializeItem(byte[] arg0) {
        try {
            var tag=net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(arg0),net.minecraft.nbt.NbtAccounter.create(16L*1024*1024));
            int current=net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version();
            int version=tag.getIntOr("DataVersion",current);
            if(version!=current)throw new IllegalArgumentException("Item data version "+version+" requires migration to "+current);
            var ops=Host.active().game.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            return new BlockViews.Stack(net.minecraft.world.item.ItemStack.CODEC.parse(ops,tag).getOrThrow());
        } catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    @Override
    public boolean hasDefaultEntityAttributes(org.bukkit.NamespacedKey arg0) {
        throw new UnsupportedOperationException("Paper API: hasDefaultEntityAttributes");
    }
    @Override
    public org.bukkit.attribute.Attributable getDefaultEntityAttributes(org.bukkit.NamespacedKey arg0) {
        throw new UnsupportedOperationException("Paper API: getDefaultEntityAttributes");
    }
    @Override
    public java.lang.String getStatisticCriteriaKey(org.bukkit.Statistic arg0) {
        throw new UnsupportedOperationException("Paper API: getStatisticCriteriaKey");
    }
    @Override
    public io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<org.bukkit.plugin.Plugin>
    createPluginLifecycleEventManager(org.bukkit.plugin.java.JavaPlugin arg0,
                                      java.util.function.BooleanSupplier arg1) {
        return Host.active().lifecycle(arg0);
    }
    @Override
    public org.bukkit.inventory.ItemStack createEmptyStack() {
        return new BlockViews.Stack(net.minecraft.world.item.ItemStack.EMPTY);
    }
    @Override
    public net.kyori.adventure.text.Component resolveWithContext(net.kyori.adventure.text.Component arg0,
                                                                 org.bukkit.command.CommandSender arg1,
                                                                 org.bukkit.entity.Entity arg2, boolean arg3)
        throws java.io.IOException {
        throw new UnsupportedOperationException("Paper API: resolveWithContext");
    }
    @Override
    public net.kyori.adventure.text.flattener.ComponentFlattener componentFlattener() {
        throw new UnsupportedOperationException("Paper API: componentFlattener");
    }
}
