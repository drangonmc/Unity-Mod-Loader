package net.neoforged.neoforge.capabilities;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
public final class Capabilities {
    public static final class Energy {
        public static final BlockCapability<EnergyHandler, Direction> BLOCK =
            BlockCapability.createSided(Identifier.parse("neoforge:energy_handler"), EnergyHandler.class);
    }
}
