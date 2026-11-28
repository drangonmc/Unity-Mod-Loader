package net.neoforged.neoforge.registries;
import net.minecraft.core.Registry;

public final class UnityRegistration {
    public static RegisterEvent event(Registry<?> registry){return new RegisterEvent(registry.key(),registry);}
}
