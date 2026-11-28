package mc.unity.plugins;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.*;

/** Bukkit's primitive adapter contract backed by a holder's saved NBT. */
final class PersistentTags implements PersistentDataContainer {
    private final Supplier<CompoundTag> read;
    private final Consumer<CompoundTag> write;

    private PersistentTags(Supplier<CompoundTag> read, Consumer<CompoundTag> write) {
        this.read=read;this.write=write;
    }
    static PersistentTags detached(CompoundTag tag) {
        var value=new java.util.concurrent.atomic.AtomicReference<>(tag.copy());
        return new PersistentTags(value::get,value::set);
    }
    static PersistentTags item(ItemStack stack) {
        return new PersistentTags(()->stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag()
            .getCompound("PublicBukkitValues").orElseGet(CompoundTag::new),tag->CustomData.update(DataComponents.CUSTOM_DATA,stack,root->{
                if(tag.isEmpty())root.remove("PublicBukkitValues");else root.put("PublicBukkitValues",tag);
            }));
    }
    static PersistentTags entity(net.minecraft.world.entity.Entity entity) {
        var owner=(mc.unity.core.Attachments.Owner)entity;
        var key=mc.unity.core.PluginData.KEY;
        return new PersistentTags(()->Objects.requireNonNullElseGet(mc.unity.core.Attachments.find(owner,key),CompoundTag::new),
            value->mc.unity.core.Attachments.set(owner,key,value.isEmpty()?null:value));
    }
    private void edit(Consumer<CompoundTag> change) {
        CompoundTag value=read.get().copy();
        change.accept(value);
        write.accept(value);
    }
    public <P,C> void set(NamespacedKey key,PersistentDataType<P,C> type,C value) {
        Tag data=encode(type,type.toPrimitive(Objects.requireNonNull(value),getAdapterContext()));
        edit(tag->tag.put(key.toString(),data));
    }
    public boolean has(NamespacedKey key){return read.get().contains(key.toString());}
    public <P,C> boolean has(NamespacedKey key,PersistentDataType<P,C> type) {
        Tag tag=read.get().get(key.toString());return tag!=null && tag.getId()==tagType(type.getPrimitiveType());
    }
    public <P,C> C get(NamespacedKey key,PersistentDataType<P,C> type) {
        Tag raw=read.get().get(key.toString());
        if(raw==null)return null;
        return type.fromPrimitive(type.getPrimitiveType().cast(decode(type,raw)),getAdapterContext());
    }
    public <P,C> C getOrDefault(NamespacedKey key,PersistentDataType<P,C> type,C fallback) {
        var v=get(key,type);return v==null?fallback:v;
    }
    public void remove(NamespacedKey key){edit(tag->tag.remove(key.toString()));}
    public boolean isEmpty(){return read.get().isEmpty();}
    public int getSize(){return read.get().size();}
    public Set<NamespacedKey> getKeys(){
        Set<NamespacedKey> keys=new LinkedHashSet<>();
        for(String k:read.get().keySet()){var key=NamespacedKey.fromString(k);if(key!=null)keys.add(key);}
        return Set.copyOf(keys);
    }
    public PersistentDataAdapterContext getAdapterContext(){return ()->detached(new CompoundTag());}
    public byte[] serializeToBytes() throws IOException {
        var out=new ByteArrayOutputStream();NbtIo.write(read.get(),new DataOutputStream(out));return out.toByteArray();
    }
    public void readFromBytes(byte[] bytes,boolean clear) throws IOException {
        CompoundTag input=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.defaultQuota());
        if(clear)write.accept(input);else edit(tag->{for(String key:input.keySet())tag.put(key,input.get(key));});
    }
    public void copyTo(PersistentDataContainer other,boolean replace) {
        if(!(other instanceof PersistentTags dst))throw new IllegalArgumentException("Foreign persistent data container");
        var source=read.get().copy();
        dst.edit(target->{for(String key:source.keySet())if(replace||!target.contains(key))target.put(key,source.get(key));});
    }
    private static int tagType(Class<?> c) {
        if(c==Byte.class)return Tag.TAG_BYTE;if(c==Short.class)return Tag.TAG_SHORT;if(c==Integer.class)return Tag.TAG_INT;
        if(c==Long.class)return Tag.TAG_LONG;if(c==Float.class)return Tag.TAG_FLOAT;if(c==Double.class)return Tag.TAG_DOUBLE;
        if(c==String.class)return Tag.TAG_STRING;if(c==byte[].class)return Tag.TAG_BYTE_ARRAY;if(c==int[].class)return Tag.TAG_INT_ARRAY;
        if(c==long[].class)return Tag.TAG_LONG_ARRAY;if(c==List.class||c==PersistentDataContainer[].class)return Tag.TAG_LIST;
        if(c==PersistentDataContainer.class)return Tag.TAG_COMPOUND;
        throw new IllegalArgumentException("Unsupported persistent primitive "+c);
    }
    private static Tag encode(PersistentDataType<?,?> type,Object value) {
        return switch(tagType(type.getPrimitiveType())) {
            case Tag.TAG_BYTE->ByteTag.valueOf((byte)value);case Tag.TAG_SHORT->ShortTag.valueOf((short)value);
            case Tag.TAG_INT->IntTag.valueOf((int)value);case Tag.TAG_LONG->LongTag.valueOf((long)value);
            case Tag.TAG_FLOAT->FloatTag.valueOf((float)value);case Tag.TAG_DOUBLE->DoubleTag.valueOf((double)value);
            case Tag.TAG_STRING->StringTag.valueOf((String)value);case Tag.TAG_BYTE_ARRAY->new ByteArrayTag(((byte[])value).clone());
            case Tag.TAG_INT_ARRAY->new IntArrayTag(((int[])value).clone());case Tag.TAG_LONG_ARRAY->new LongArrayTag(((long[])value).clone());
            case Tag.TAG_COMPOUND->((PersistentTags)value).read.get().copy();
            case Tag.TAG_LIST->{
                var out=new ListTag();
                if(type instanceof ListPersistentDataType<?,?> list)for(Object v:(List<?>)value)out.add(encode(list.elementType(),v));
                else for(var c:(PersistentDataContainer[])value)out.add(((PersistentTags)c).read.get().copy());
                yield out;
            }
            default->throw new IllegalArgumentException("Unknown persistent primitive");
        };
    }
    private static Object decode(PersistentDataType<?,?> type,Tag t) {
        if(t.getId()!=tagType(type.getPrimitiveType()))throw new IllegalArgumentException("Persistent data primitive mismatch");
        return switch(t) {
            case ByteTag n->n.byteValue();case ShortTag n->n.shortValue();case IntTag n->n.intValue();
            case LongTag n->n.longValue();case FloatTag n->n.floatValue();case DoubleTag n->n.doubleValue();
            case StringTag n->n.value();case ByteArrayTag n->n.getAsByteArray().clone();
            case IntArrayTag n->n.getAsIntArray().clone();case LongArrayTag n->n.getAsLongArray().clone();
            case CompoundTag n->detached(n);
            case ListTag n-> type instanceof ListPersistentDataType<?,?> list
                ? n.stream().map(v->decode(list.elementType(),v)).toList()
                : n.stream().map(v->(PersistentDataContainer)detached((CompoundTag)v)).toArray(PersistentDataContainer[]::new);
            default->throw new IllegalArgumentException("Unknown persistent tag");
        };
    }
}
