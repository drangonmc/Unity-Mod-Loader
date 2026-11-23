package net.minecraftforge.fml.javafmlmod;
import net.minecraftforge.eventbus.api.bus.BusGroup;
public class FMLJavaModLoadingContext extends net.minecraftforge.fml.ModLoadingContext {
    private final BusGroup group;
    public FMLJavaModLoadingContext(BusGroup group){super(group);this.group=group;}
    public BusGroup getModBusGroup(){return group;}
    public static FMLJavaModLoadingContext get(){return (FMLJavaModLoadingContext)net.minecraftforge.fml.ModLoadingContext.get();}
}
