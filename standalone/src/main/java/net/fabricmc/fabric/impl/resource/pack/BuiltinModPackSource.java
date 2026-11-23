package net.fabricmc.fabric.impl.resource.pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;
public record BuiltinModPackSource(String modId) implements PackSource {
    public boolean shouldAddAutomatically() { return true; }
    public Component decorate(Component title) {
        return Component.translatable("pack.nameAndSource", title,
                                      Component.translatable("pack.source.builtinMod", modId));
    }
}
