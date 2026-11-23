package net.fabricmc.fabric.api.resource.v1;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
public interface FabricResource {
    default PackSource getFabricPackSource() { return ((Resource)(Object)this).source().location().source(); }
}
