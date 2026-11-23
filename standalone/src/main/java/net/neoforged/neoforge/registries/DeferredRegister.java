package net.neoforged.neoforge.registries;
import java.util.*;
import java.util.function.*;
import mc.unity.adapter.neoforge.NeoBus;
import mc.unity.core.Content;
import net.minecraft.core.Registry;
import net.minecraft.resources.*;
import net.neoforged.bus.api.IEventBus;
public class DeferredRegister<R> {
    protected final ResourceKey<? extends Registry<R>> type;
    protected final String ns;
    protected final List<DeferredHolder<R, ?>> entries = new ArrayList<>();
    protected DeferredRegister(ResourceKey<? extends Registry<R>> type, String ns) {
        this.type = type;
        this.ns = ns;
    }
    public static <R> DeferredRegister<R> create(Registry<R> registry,String namespace){return create(registry.key(),namespace);}
    public static <R> DeferredRegister<R> create(ResourceKey<? extends Registry<R>> type, String ns) {
        return new DeferredRegister<>(type, ns);
    }
    public static DataComponents createDataComponents(ResourceKey<Registry<net.minecraft.core.component.DataComponentType<?>>> key,String ns){return new DataComponents(key,ns);}
    public static Entities createEntities(String ns){return new Entities(ns);}
    public static class DataComponents extends DeferredRegister<net.minecraft.core.component.DataComponentType<?>> {
        protected DataComponents(ResourceKey<Registry<net.minecraft.core.component.DataComponentType<?>>> key,String ns){super(key,ns);}
        public <T> DeferredHolder<net.minecraft.core.component.DataComponentType<?>,net.minecraft.core.component.DataComponentType<T>> registerComponentType(
            String name,java.util.function.UnaryOperator<net.minecraft.core.component.DataComponentType.Builder<T>> builder) {
            return register(name,()->builder.apply(net.minecraft.core.component.DataComponentType.<T>builder()).build());
        }
    }
    public static class Entities extends DeferredRegister<net.minecraft.world.entity.EntityType<?>> {
        protected Entities(String ns){super(net.minecraft.core.registries.Registries.ENTITY_TYPE,ns);}
        public <E extends net.minecraft.world.entity.Entity> DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<E>> registerEntityType(
            String name,net.minecraft.world.entity.EntityType.EntityFactory<E> factory,net.minecraft.world.entity.MobCategory category){
            return registerEntityType(name,factory,category,java.util.function.UnaryOperator.identity());
        }
        public <E extends net.minecraft.world.entity.Entity> DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<E>> registerEntityType(
            String name,net.minecraft.world.entity.EntityType.EntityFactory<E> factory,net.minecraft.world.entity.MobCategory category,java.util.function.UnaryOperator<net.minecraft.world.entity.EntityType.Builder<E>> builder) {
            return register(name,id->builder.apply(net.minecraft.world.entity.EntityType.Builder.of(factory,category)).build(ResourceKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,id)));
        }
    }
    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }
    public static Items createItems(String namespace) {
        return new Items(namespace);
    }
    public <T extends R> DeferredHolder<R, T> register(String name, Supplier<? extends T> factory) {
        return register(name, id -> factory.get());
    }
    public <T extends R> DeferredHolder<R, T> register(String name, Function<Identifier, ? extends T> factory) {
        Content.checkOpen();
        var id = Identifier.fromNamespaceAndPath(ns, name);
        var h = new DeferredHolder<R, T>(ResourceKey.create(type, id), () -> factory.apply(id));
        entries.add(h);
        return h;
    }
    public void register(IEventBus bus) {
        if (!(bus instanceof NeoBus n) || n.owner() == null)
            throw new IllegalArgumentException("Expected mod event bus");
        Content.attach(n.owner(), type, r -> entries.forEach(e -> e.bind((Registry<R>) r)));
    }

    public static class Blocks extends DeferredRegister<net.minecraft.world.level.block.Block> {
        protected Blocks(String ns) {
            super(net.minecraft.core.registries.Registries.BLOCK, ns);
        }
        public <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> register(
            String name, Supplier<? extends B> f) {
            return register(name, id -> f.get());
        }
        public <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> register(
            String name, Function<Identifier, ? extends B> f) {
            Content.checkOpen();
            var id = Identifier.fromNamespaceAndPath(ns, name);
            var h = new DeferredBlock<B>(ResourceKey.create(type, id), () -> f.apply(id));
            entries.add(h);
            return h;
        }
        public <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> registerBlock(String n,
            Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties, ? extends B> make,
            Supplier<net.minecraft.world.level.block.state.BlockBehaviour.Properties> props) {
            return register(n, id -> make.apply(props.get().setId(ResourceKey.create(type, id))));
        }
        public <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> registerBlock(String n,
            Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties, ? extends B> make,
            UnaryOperator<net.minecraft.world.level.block.state.BlockBehaviour.Properties> props) {
            return registerBlock(n, make,
                () -> props.apply(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()));
        }
        public <B extends net.minecraft.world.level.block.Block> DeferredBlock<B> registerBlock(String n,
            Function<net.minecraft.world.level.block.state.BlockBehaviour.Properties, ? extends B> make) {
            return registerBlock(n, make, net.minecraft.world.level.block.state.BlockBehaviour.Properties::of);
        }
        public DeferredBlock<net.minecraft.world.level.block.Block> registerSimpleBlock(String n) {
            return registerBlock(n, net.minecraft.world.level.block.Block::new);
        }
        public DeferredBlock<net.minecraft.world.level.block.Block> registerSimpleBlock(
            String n, Supplier<net.minecraft.world.level.block.state.BlockBehaviour.Properties> props) {
            return registerBlock(n, net.minecraft.world.level.block.Block::new, props);
        }
        public DeferredBlock<net.minecraft.world.level.block.Block> registerSimpleBlock(
            String n, UnaryOperator<net.minecraft.world.level.block.state.BlockBehaviour.Properties> props) {
            return registerBlock(n, net.minecraft.world.level.block.Block::new, props);
        }
    }
    public static class Items extends DeferredRegister<net.minecraft.world.item.Item> {
        protected Items(String ns) {
            super(net.minecraft.core.registries.Registries.ITEM, ns);
        }
        public <I extends net.minecraft.world.item.Item> DeferredItem<I> register(
            String name, Supplier<? extends I> f) {
            return register(name, id -> f.get());
        }
        public <I extends net.minecraft.world.item.Item> DeferredItem<I> register(
            String name, Function<Identifier, ? extends I> f) {
            Content.checkOpen();
            var id = Identifier.fromNamespaceAndPath(ns, name);
            var h = new DeferredItem<I>(ResourceKey.create(type, id), () -> f.apply(id));
            entries.add(h);
            return h;
        }
        public <I extends net.minecraft.world.item.Item> DeferredItem<I> registerItem(String n,
            Function<net.minecraft.world.item.Item.Properties, ? extends I> make,
            Supplier<net.minecraft.world.item.Item.Properties> props) {
            return register(n, id -> make.apply(props.get().setId(ResourceKey.create(type, id))));
        }
        public <I extends net.minecraft.world.item.Item> DeferredItem<I> registerItem(String n,
            Function<net.minecraft.world.item.Item.Properties, ? extends I> make,
            UnaryOperator<net.minecraft.world.item.Item.Properties> props) {
            return registerItem(n, make, () -> props.apply(new net.minecraft.world.item.Item.Properties()));
        }
        public <I extends net.minecraft.world.item.Item> DeferredItem<I> registerItem(
            String n, Function<net.minecraft.world.item.Item.Properties, ? extends I> make) {
            return registerItem(n, make, net.minecraft.world.item.Item.Properties::new);
        }
        public DeferredItem<net.minecraft.world.item.Item> registerSimpleItem(String n) {
            return registerItem(n, net.minecraft.world.item.Item::new);
        }
        public DeferredItem<net.minecraft.world.item.Item> registerSimpleItem(
            String n, Supplier<net.minecraft.world.item.Item.Properties> props) {
            return registerItem(n, net.minecraft.world.item.Item::new, props);
        }
        public DeferredItem<net.minecraft.world.item.Item> registerSimpleItem(
            String n, UnaryOperator<net.minecraft.world.item.Item.Properties> props) {
            return registerItem(n, net.minecraft.world.item.Item::new, props);
        }
        public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(
            String n, Supplier<? extends net.minecraft.world.level.block.Block> b) {
            return registerItem(
                n, p -> new net.minecraft.world.item.BlockItem(b.get(), p.useBlockDescriptionPrefix()));
        }
        public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(String n,
            Supplier<? extends net.minecraft.world.level.block.Block> b,
            Supplier<net.minecraft.world.item.Item.Properties> props) {
            return registerItem(
                n, p -> new net.minecraft.world.item.BlockItem(b.get(), p.useBlockDescriptionPrefix()), props);
        }
        public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(String n,
            Supplier<? extends net.minecraft.world.level.block.Block> b,
            UnaryOperator<net.minecraft.world.item.Item.Properties> props) {
            return registerSimpleBlockItem(
                n, b, () -> props.apply(new net.minecraft.world.item.Item.Properties()));
        }
        public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(
            net.minecraft.core.Holder<net.minecraft.world.level.block.Block> b) {
            return registerSimpleBlockItem(b.unwrapKey().orElseThrow().identifier().getPath(), b::value);
        }
        public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(
            net.minecraft.core.Holder<net.minecraft.world.level.block.Block> b,
            Supplier<net.minecraft.world.item.Item.Properties> props) {
            return registerSimpleBlockItem(b.unwrapKey().orElseThrow().identifier().getPath(), b::value, props);
        }
        public DeferredItem<net.minecraft.world.item.BlockItem> registerSimpleBlockItem(
            net.minecraft.core.Holder<net.minecraft.world.level.block.Block> b,
            UnaryOperator<net.minecraft.world.item.Item.Properties> props) {
            return registerSimpleBlockItem(
                b, () -> props.apply(new net.minecraft.world.item.Item.Properties()));
        }
    }
}
