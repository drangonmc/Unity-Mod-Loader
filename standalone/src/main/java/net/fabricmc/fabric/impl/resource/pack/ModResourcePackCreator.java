package net.fabricmc.fabric.impl.resource.pack;
import net.minecraft.server.packs.repository.PackSource;
/** Source identity shared by Unity-mounted mod packs and upstream Fabric loot callbacks. */
public final class ModResourcePackCreator {
    public static final PackSource RESOURCE_PACK_SOURCE = mc.unity.core.Content.MOD_SOURCE;
    private ModResourcePackCreator() {}
}
