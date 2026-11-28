package net.neoforged.neoforge.fluids;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.*;

public class FluidType {
    public static final int BUCKET_VOLUME=1000;
    private static final Map<Fluid,FluidType> types=new IdentityHashMap<>();
    private final Fluid fluid;
    private FluidType(Fluid fluid){this.fluid=fluid;}
    public static synchronized FluidType of(Fluid liquid) {
        Fluid source=liquid instanceof net.minecraft.world.level.material.FlowingFluid f?f.getSource():liquid;
        return types.computeIfAbsent(source,FluidType::new);
    }
    public boolean isLighterThanAir(){return false;}
    public String getDescriptionId(){return BuiltInRegistries.FLUID.getKey(fluid).toLanguageKey("block");}
    public String getDescriptionId(FluidStack stack){return getDescriptionId();}
    public Component getDescription(FluidStack stack){return Component.translatable(getDescriptionId(stack));}
}
