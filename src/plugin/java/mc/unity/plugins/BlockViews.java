package mc.unity.plugins;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

final class BlockViews {
    private final BukkitViews views;
    BlockViews(BukkitViews views) {
        this.views = views;
    }
    private record Key(ServerLevel world, BlockPos pos) {}
    static Material material(net.minecraft.resources.Identifier id) {
        if (id.getNamespace().equals("minecraft")) {
            var value = Material.getMaterial(id.getPath().toUpperCase(Locale.ROOT));
            if (value != null)
                return value;
        }
        var modded = Material.matchMaterial(id.toString());
        if (modded != null && modded.getKey().toString().equals(id.toString())) return modded;
        throw Views.unsupported("Material","mapping for " + id);
    }
    static final class Stack extends org.bukkit.inventory.ItemStack {
        private final net.minecraft.world.item.ItemStack stack;
        private final Runnable changed;

        private Stack(net.minecraft.world.item.ItemStack stack, Runnable changed) {
            super();
            this.stack = stack;
            this.changed = changed;
        }
        static Stack mirror(net.minecraft.world.item.ItemStack stack, Runnable changed) {
            return new Stack(stack, changed);
        }
        Stack(net.minecraft.world.item.ItemStack original) {
            this(original.copy(), () -> {});
        }
        static net.minecraft.world.item.ItemStack nativeCopy(org.bukkit.inventory.ItemStack value) {
            if (value == null || value.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
            if (value instanceof Stack own) return own.stack.copy();
            var key = net.minecraft.resources.Identifier.parse(value.getType().getKey().toString());
            var result = new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getOptional(key).orElseThrow(), value.getAmount());
            if (value.hasItemMeta()) {
                var meta = StackMeta.from(value.getItemMeta());
                if (meta == null) throw Views.unsupported("ItemStack", "foreign metadata conversion");
                result.applyComponents(meta.item.getComponentsPatch());
            }
            return result;
        }
        public boolean hasItemMeta() { return !stack.getComponentsPatch().isEmpty(); }
        public org.bukkit.inventory.meta.ItemMeta getItemMeta() {
            return stack.isEmpty() ? null : StackMeta.of(stack);
        }
        public boolean setItemMeta(org.bukkit.inventory.meta.ItemMeta value) {
            var result = new net.minecraft.world.item.ItemStack(stack.getItem(), stack.getCount());
            if (value != null) {
                var meta = StackMeta.from(value);
                if (meta == null) return false;
                result.applyComponents(meta.item.getComponentsPatch());
            }
            if (stack.isEmpty()) return false;
            for (var key : stack.getComponentsPatch().entrySet().stream().map(Map.Entry::getKey).toList())
                stack.copyFrom(key, stack.getPrototype());
            stack.applyComponents(result.getComponentsPatch());
            changed.run();
            return true;
        }
        public boolean isSimilar(org.bukkit.inventory.ItemStack other) {
            return other != null && net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, nativeCopy(other));
        }
        @Override public byte[] serializeAsBytes() {
            try {
                var ops=Host.active().game.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
                var tag=(net.minecraft.nbt.CompoundTag)net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
                tag.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());
                var out=new java.io.ByteArrayOutputStream();net.minecraft.nbt.NbtIo.writeCompressed(tag,out);
                return out.toByteArray();
            } catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        }
        public Material getType() {
            return material(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        public int getAmount() {
            return stack.getCount();
        }
        public void setAmount(int count) {
            if (stack == net.minecraft.world.item.ItemStack.EMPTY) return;
            stack.setCount(count);
            changed.run();
        }
        public boolean isEmpty() {
            return stack.isEmpty();
        }
        public int getMaxStackSize() {
            return stack.getMaxStackSize();
        }
        public Stack clone() {
            return new Stack(stack);
        }
        public boolean equals(Object other){return other==this || other instanceof org.bukkit.inventory.ItemStack o && getAmount()==o.getAmount() && isSimilar(o);}
        public int hashCode(){return 31*net.minecraft.world.item.ItemStack.hashItemAndComponents(stack)+getAmount();}
        public String toString() {
            return stack.toString();
        }
    }
    Block block(ServerLevel level, BlockPos location) {
        var pos = location.immutable();
        return Views.of(Block.class, pos.toShortString(), new Key(level, pos), (m, a) -> {
            if(m.equals("setType") || m.equals("setBlockData")){
                BlockState next=m.equals("setType")?BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.parse(((Material)a[0]).getKey().toString())).orElseThrow().defaultBlockState():
                    net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),((BlockData)a[0]).getAsString(),false).blockState();
                level.setBlock(pos,next,a.length>1 && Boolean.FALSE.equals(a[1])?net.minecraft.world.level.block.Block.UPDATE_CLIENTS|net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE|net.minecraft.world.level.block.Block.UPDATE_SKIP_ON_PLACE:3);return null;
            }
            if (m.equals("getState"))
                return snapshot(level, pos, level.getBlockState(pos));
            if (m.equals("getRelative")) {
                if (a[0] instanceof BlockFace face) {
                    int n = a.length > 1 ? (int)a[1] : 1;
                    return block(level,
                                 pos.offset(face.getModX() * n, face.getModY() * n, face.getModZ() * n));
                }
                return block(level, pos.offset((int)a[0], (int)a[1], (int)a[2]));
            }
            return query(level, pos, level.getBlockState(pos), m, a);
        });
    }
    org.bukkit.block.BlockState snapshot(ServerLevel level, BlockPos pos, BlockState state) {
        return snapshot(level,pos,new java.util.concurrent.atomic.AtomicReference<>(state));
    }
    org.bukkit.block.BlockState snapshot(ServerLevel level, BlockPos pos, java.util.concurrent.atomic.AtomicReference<BlockState> state) {
        return Views.of(org.bukkit.block.BlockState.class, "snapshot " + pos.toShortString(), (m,a)->switch(m) {
            case "getBlock" -> block(level,pos);
            case "isPlaced" -> true;
            case "setType" -> { state.set(BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.parse(((Material)a[0]).getKey().toString())).orElseThrow().defaultBlockState()); yield null; }
            case "setBlockData" -> {
                state.set(net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                    level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),((BlockData)a[0]).getAsString(),false).blockState());
                yield null;
            }
            default -> query(level,pos,state.get(),m,a);
        });
    }
    private Object query(ServerLevel level, BlockPos pos, BlockState state, String m, Object[] a) {
        return switch (m) {
            case "getWorld" -> views.world(level);
            case "getX" -> pos.getX();
            case "getY" -> pos.getY();
            case "getZ" -> pos.getZ();
            case "getLocation" -> new Location(views.world(level), pos.getX(), pos.getY(), pos.getZ());
            case "getLocation/into" -> {
                var at = (Location)a[0];
                if (at != null) {
                    at.setWorld(views.world(level));
                    at.setX(pos.getX());
                    at.setY(pos.getY());
                    at.setZ(pos.getZ());
                    at.setYaw(0);
                    at.setPitch(0);
                }
                yield at;
            }
            case "getChunk" -> views.chunk(level, pos.getX() >> 4, pos.getZ() >> 4);
            case "getType" -> material(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            case "getBlockData" -> data(state);
            case "isPassable" -> state.getCollisionShape(level,pos).isEmpty();
            case "isEmpty" -> state.isAir();
            case "isLiquid" ->
                !state.getFluidState().isEmpty() && state.getBlock() instanceof
                                                        net.minecraft.world.level.block.LiquidBlock;
            default -> throw Views.unsupported("Block", m);
        };
    }
    static BlockData data(BlockState original) {
        var state=new java.util.concurrent.atomic.AtomicReference<>(original);
        var water=net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;
        var cauldron=net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL;
        var fluid=net.minecraft.world.level.block.LiquidBlock.LEVEL;
        var levels=original.hasProperty(cauldron)?cauldron:original.hasProperty(fluid)?fluid:null;
        Class<? extends BlockData> type=original.is(net.minecraft.world.level.block.Blocks.DISPENSER) ? org.bukkit.block.data.type.Dispenser.class :
            original.getBlock() instanceof net.minecraft.world.level.block.SlabBlock ? org.bukkit.block.data.type.Slab.class :
            original.hasProperty(water)?org.bukkit.block.data.Waterlogged.class:
            levels!=null?org.bukkit.block.data.Levelled.class:BlockData.class;
        return Views.of(type,original.toString(),(m,a)->switch(m) {
            case "getMaterial" -> material(BuiltInRegistries.BLOCK.getKey(state.get().getBlock()));
            case "getAsString" -> net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state.get());
            case "getFacing" -> org.bukkit.block.BlockFace.valueOf(state.get().getValue(net.minecraft.world.level.block.DispenserBlock.FACING).name());
            case "getFaces" -> EnumSet.of(BlockFace.DOWN,BlockFace.UP,BlockFace.NORTH,BlockFace.SOUTH,BlockFace.WEST,BlockFace.EAST);
            case "setFacing" -> {state.set(state.get().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,net.minecraft.core.Direction.valueOf(((BlockFace)a[0]).name())));yield null;}
            case "isTriggered" -> state.get().getValue(net.minecraft.world.level.block.DispenserBlock.TRIGGERED);
            case "setTriggered" -> {state.set(state.get().setValue(net.minecraft.world.level.block.DispenserBlock.TRIGGERED,(boolean)a[0]));yield null;}
            case "getType" -> org.bukkit.block.data.type.Slab.Type.valueOf(state.get().getValue(net.minecraft.world.level.block.SlabBlock.TYPE).name());
            case "setType" -> {state.set(state.get().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.valueOf(((org.bukkit.block.data.type.Slab.Type)a[0]).name())));yield null;}
            case "getLevel" -> state.get().getValue(levels);
            case "getMaximumLevel" -> levels.getPossibleValues().getLast();
            case "setLevel" -> {state.set(state.get().setValue(levels,(int)a[0]));yield null;}
            case "isWaterlogged" -> state.get().getValue(water);
            case "setWaterlogged" -> {state.set(state.get().setValue(water,(boolean)a[0]));yield null;}
            case "clone" -> data(state.get());
            default -> throw Views.unsupported("BlockData",m);
        });
    }
}
