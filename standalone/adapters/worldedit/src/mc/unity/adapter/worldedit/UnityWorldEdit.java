package mc.unity.adapter.worldedit;

import java.util.*;
import com.sk89q.worldedit.bukkit.adapter.BukkitImplAdapter;
import com.sk89q.worldedit.registry.state.*;
import com.sk89q.worldedit.world.registry.BlockMaterial;
import com.sk89q.worldedit.world.block.*;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.util.formatting.text.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.piston.*;
import org.bukkit.Location;
import org.bukkit.World;

/** WorldEdit 7.4.6 platform adapter for Minecraft 26.2. */
public final class UnityWorldEdit implements BukkitImplAdapter {
    private static UnsupportedOperationException unsupported(String action) {
        return new UnsupportedOperationException("Unity WorldEdit adapter: "+action);
    }
    private static net.minecraft.world.level.block.Block block(BlockType type){
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(type.id())).orElseThrow();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    public Map<String,? extends Property<?>> getProperties(BlockType type) {
        Map<String,Property<?>> out=new LinkedHashMap<>();
        for(var prop:block(type).getStateDefinition().getProperties()){
            String name=prop.getName();
            Property<?> value;
            if(prop instanceof net.minecraft.world.level.block.state.properties.BooleanProperty p)
                value=new BooleanProperty(name,p.getPossibleValues());
            else if(prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty p)
                value=new IntegerProperty(name,p.getPossibleValues());
            else if(prop.getValueClass()==net.minecraft.core.Direction.class)
                value=new DirectionalProperty(name,prop.getPossibleValues().stream()
                    .map(v->com.sk89q.worldedit.util.Direction.valueOf(((Enum<?>)v).name())).toList());
            else {
                var raw=(net.minecraft.world.level.block.state.properties.Property)prop;
                List<String> names=new ArrayList<>();
                for(var v:prop.getPossibleValues()) names.add(raw.getName(v));
                value=new EnumProperty(name,names);
            }
            out.put(name,value);
        }
        return Collections.unmodifiableMap(out);
    }
    public BlockMaterial getBlockMaterial(BlockType type){
        var b=block(type);var s=b.defaultBlockState();var level=EmptyBlockGetter.INSTANCE;var pos=BlockPos.ZERO;
        return new BlockMaterial(){
            public boolean isAir(){return s.isAir();}
            public boolean isFullCube(){return s.isCollisionShapeFullBlock(level,pos);}
            public boolean isOpaque(){return s.isSolidRender();}
            public boolean isPowerSource(){return s.isSignalSource();}
            public boolean isLiquid(){return b instanceof net.minecraft.world.level.block.LiquidBlock;}
            public boolean isSolid(){return s.isSolid();}
            public float getHardness(){return s.getDestroySpeed(level,pos);}
            public float getResistance(){return b.getExplosionResistance();}
            public float getSlipperiness(){return b.getFriction();}
            public int getLightValue(){return s.getLightEmission();}
            public boolean isFragileWhenPushed(){return s.getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.DESTROY;}
            public boolean isUnpushable(){return s.getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.BLOCK;}
            public boolean isTicksRandomly(){return s.isRandomlyTicking();}
            public boolean isMovementBlocker(){return s.blocksMotion();}
            public boolean isBurnable(){return s.ignitedByLava();}
            public boolean isToolRequired(){return s.requiresCorrectToolForDrops();}
            public boolean isReplacedDuringPlacement(){return s.canBeReplaced();}
            public boolean isTranslucent(){return !s.canOcclude();}
            public boolean hasContainer(){return s.hasBlockEntity();}
        };
    }
    public BlockState getBlock(Location at){return com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(at.getBlock().getBlockData());}
    public BaseBlock getFullBlock(Location at){
        var access = access(at.getWorld());
        return access.onServer(() -> {
            var state = getBlock(at);
            var tile = access.level.getBlockEntity(new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
            if (tile == null) return state.toBaseBlock();
            var tag = WorldBlocks.snapshot(tile.saveWithFullMetadata(access.level.registryAccess()));
            return state.toBaseBlock(com.sk89q.worldedit.util.concurrency.LazyReference.computed(tag));
        });
    }
    public Component getRichBlockName(BlockType type){return TextComponent.of(block(type).getName().getString());}
    public Component getRichItemName(com.sk89q.worldedit.world.item.ItemType type){return TextComponent.of(BuiltInRegistries.ITEM.getOptional(Identifier.parse(type.id())).orElseThrow().getDefaultInstance().getHoverName().getString());}
    public Component getRichItemName(BaseItemStack item){return getRichItemName(item.getType());}
    public Set<com.sk89q.worldedit.util.SideEffect> getSupportedSideEffects(){return WorldBlocks.EFFECTS;}
    public com.sk89q.worldedit.world.DataFixer getDataFixer(){return null;}
    public com.sk89q.worldedit.internal.wna.WorldNativeAccess<?,?,?> createWorldNativeAccess(World world){return access(world);}
    private static WorldBlocks access(World world) {
        return new WorldBlocks(((mc.unity.plugins.NativeWorldView)world).unityLevel());
    }
    public boolean clearContainerBlockContents(World world,com.sk89q.worldedit.math.BlockVector3 pos) {
        var access = access(world);
        return access.onServer(() -> {
            var entity = access.level.getBlockEntity(access.getPosition(pos.x(), pos.y(), pos.z()));
            if (!(entity instanceof net.minecraft.world.Container inventory)) return false;
            inventory.clearContent();
            entity.setChanged();
            return true;
        });
    }
    public com.sk89q.worldedit.entity.BaseEntity getEntity(org.bukkit.entity.Entity e){throw unsupported("entity NBT");}
    public org.bukkit.entity.Entity createEntity(Location at,com.sk89q.worldedit.entity.BaseEntity e){throw unsupported("entity creation");}
    public void sendFakeNBT(org.bukkit.entity.Player p,com.sk89q.worldedit.math.BlockVector3 pos,org.enginehub.linbus.tree.LinCompoundTag nbt){throw unsupported("client NBT edits");}
    public void sendFakeOP(org.bukkit.entity.Player p){throw unsupported("client operator settings");}
    public boolean canPlaceAt(World world,com.sk89q.worldedit.math.BlockVector3 pos,BlockState state){
        var access = access(world);
        return access.onServer(() -> access.toNative(state).canSurvive(access.level,
            new BlockPos(pos.x(), pos.y(), pos.z())));
    }
    public org.bukkit.inventory.ItemStack adapt(BaseItemStack item) {
        var root=org.enginehub.linbus.tree.LinCompoundTag.builder().putString("id",item.getType().id()).putInt("count",item.getAmount());
        if(item.getNbt()!=null)root.put("components",item.getNbt());
        try {
            var out=new java.io.ByteArrayOutputStream();
            try(var gz=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(out))){
                org.enginehub.linbus.stream.LinBinaryIO.write(gz,new org.enginehub.linbus.tree.LinRootEntry("",root.build()));
            }
            return org.bukkit.inventory.ItemStack.deserializeBytes(out.toByteArray());
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    }
    public BaseItemStack adapt(org.bukkit.inventory.ItemStack item) {
        var type=com.sk89q.worldedit.world.item.ItemTypes.get(item.getType().getKey().toString());
        if(!item.hasItemMeta())return new BaseItemStack(type,item.getAmount());
        try(var in=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(item.serializeAsBytes())))){
            var tag=org.enginehub.linbus.stream.LinBinaryIO.readUsing(in,org.enginehub.linbus.tree.LinRootEntry::readFrom).value();
            var patch=(org.enginehub.linbus.tree.LinCompoundTag)tag.value().get("components");
            return new BaseItemStack(type,com.sk89q.worldedit.util.concurrency.LazyReference.computed(patch),item.getAmount());
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    }
}
