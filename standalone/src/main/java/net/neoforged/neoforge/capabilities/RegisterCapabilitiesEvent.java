package net.neoforged.neoforge.capabilities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
public class RegisterCapabilitiesEvent
    extends net.neoforged.bus.api.Event implements net.neoforged.fml.event.IModBusEvent {
    public <T, C> void registerBlock(BlockCapability<T, C> cap, IBlockCapabilityProvider<T, C> provider,
                                     Block... blocks) {
        for (var block : blocks)
            cap.add(block, provider);
    }
    public <T, C, E extends BlockEntity> void
    registerBlockEntity(BlockCapability<T, C> cap, BlockEntityType<E> type,
                        ICapabilityProvider<? super E, C, T> provider) {
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
            if (type.isValid(block.defaultBlockState()))
                cap.add(block,
                        (level, pos, state, be, side)
                            -> be != null && be.getType() == type ? provider.getCapability((E)be, side)
                                                                  : null);
    }
    public boolean isBlockRegistered(BlockCapability<?, ?> cap, Block block) { return cap.has(block); }
    public <T,C,E extends net.minecraft.world.entity.Entity> void registerEntity(EntityCapability<T,C> cap,net.minecraft.world.entity.EntityType<E> type,ICapabilityProvider<? super E,C,T> provider){
        cap.add(type,(entity,context)->provider.getCapability((E)entity,context));
    }
    public boolean isEntityRegistered(EntityCapability<?,?> cap,net.minecraft.world.entity.EntityType<?> type){return cap.has(type);}
    public <T,C> void registerItem(ItemCapability<T,C> cap,ICapabilityProvider<net.minecraft.world.item.ItemStack,C,T> provider,net.minecraft.world.level.ItemLike... items){
        for(var item:items)cap.add(item.asItem(),provider);
    }
    public boolean isItemRegistered(ItemCapability<?,?> cap,net.minecraft.world.item.Item item){return cap.has(item);}
}
