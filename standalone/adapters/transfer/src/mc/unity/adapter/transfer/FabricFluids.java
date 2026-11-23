package mc.unity.adapter.transfer;
import java.util.*;
import mc.unity.core.SidedFluids;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.core.component.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
public final class FabricFluids implements SidedFluids.Tanks {
    private static final long UNIT = FluidConstants.BUCKET / 1000;
    private final Storage<FluidVariant> storage;
    private final SlottedStorage<FluidVariant> slots;
    private final BlockEntity owner;
    private List<StorageView<FluidVariant>> cached;
    private int lastTick;
    private long version;
    private Object transaction;
    private static final ClassValue<Boolean> VERSIONED = new ClassValue<>() {
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getVersion").getDeclaringClass() != Storage.class;
            } catch (NoSuchMethodException missing) {
                throw new IllegalStateException(missing);
            }
        }
    };
    private FabricFluids(BlockEntity owner, Storage<FluidVariant> storage) {
        this.owner = owner;
        this.storage = storage;
        this.slots = storage instanceof SlottedStorage<FluidVariant> s ? s : null;
    }
    public static SidedFluids.Tanks find(BlockEntity be, Direction face, SidedFluids.Tanks previous) {
        var storage = FluidStorage.SIDED.find(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, face);
        if (storage == null)
            return null;
        return previous instanceof FabricFluids adapter && adapter.storage == storage
            ? adapter
            : new FabricFluids(be, storage);
    }
    private List<StorageView<FluidVariant>> views() {
        int tick = ((net.minecraft.server.level.ServerLevel)owner.getLevel()).getServer().getTickCount();
        var tx = Transaction.getCurrentUnsafe();
        long ver = tx == null && VERSIONED.get(storage.getClass()) ? storage.getVersion() : 0;
        if (cached == null || tick != lastTick || transaction != tx || ver != version) {
            var views = new ArrayList<StorageView<FluidVariant>>();
            storage.forEach(views::add);
            cached = views;
            lastTick = tick;
            version = ver;
            transaction = tx;
        }
        return cached;
    }
    public int size() { return slots == null ? views().size() : slots.getSlotCount(); }
    private StorageView<FluidVariant> view(int i) {
        if (i < 0)
            return null;
        if (slots != null)
            return i < slots.getSlotCount() ? slots.getSlot(i) : null;
        var list = views();
        return i < list.size() ? list.get(i) : null;
    }
    public int capacity(int i) {
        var v = view(i);
        return v == null ? 0 : toMb(v.getCapacity());
    }
    public int amount(int i) {
        var v = view(i);
        return v == null ? 0 : toMb(v.getAmount());
    }
    private static int toMb(long amount) { return (int)Math.min(Integer.MAX_VALUE, amount / UNIT); }
    public SidedFluids.Variant variant(int i) {
        var view = view(i);
        if (view == null)
            return null;
        var v = view.getResource();
        if (v.isBlank())
            return null;
        var patch = v.getComponentsPatch();
        if (patch.isEmpty())
            return new SidedFluids.Variant(v.getFluid(), null);
        var tag = v.get(DataComponents.CUSTOM_DATA);
        return patch.size() == 1 && tag != null &&
                patch.entrySet().iterator().next().getKey() == DataComponents.CUSTOM_DATA
            ? new SidedFluids.Variant(v.getFluid(), tag.copyTag())
            : null;
    }
    private static FluidVariant fabric(SidedFluids.Variant v) {
        return v.tag() == null
            ? FluidVariant.of(v.fluid())
            : FluidVariant.of(v.fluid(), DataComponentPatch.builder()
                                             .set(DataComponents.CUSTOM_DATA, CustomData.of(v.tag()))
                                             .build());
    }
    public boolean accepts(int i, SidedFluids.Variant v) {
        var tank = view(i);
        if (tank == null || !storage.supportsInsertion())
            return false;
        var variant = fabric(v);
        if (!tank.isResourceBlank() && !tank.getResource().equals(variant))
            return false;
        try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            if (tank instanceof Storage<?> indexed) {
                @SuppressWarnings("unchecked") var target = (Storage<FluidVariant>)indexed;
                return target.insert(variant, UNIT, tx) > 0;
            }
            long before = tank.getAmount();
            return storage.insert(variant, UNIT, tx) > 0 && tank.getAmount() > before &&
                tank.getResource().equals(variant);
        }
    }
    public int move(SidedFluids.Variant v, int max, boolean insert, boolean simulate) {
        if (max <= 0 || v == null || v.fluid() == Fluids.EMPTY)
            return 0;
        cached = null;
        var variant = fabric(v);
        long requested = UNIT * max;
        try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            long moved = move(variant, requested, insert, tx);
            if (moved < 0 || moved > requested)
                throw new IllegalStateException("Invalid Fabric fluid transfer amount " + moved);
            int whole = toMb(moved);
            if (simulate || whole == 0)
                return whole;
            if (moved % UNIT == 0) {
                tx.commit();
                return whole;
            }
        }
        // Roll back the fractional result, then commit only a whole number of millibuckets.
        try (var tx = Transaction.openNested(Transaction.getCurrentUnsafe())) {
            long available;
            try (var probe = tx.openNested()) {
                available = move(variant, requested, insert, probe);
            }
            long whole = available - available % UNIT;
            if (whole <= 0 || whole > requested || move(variant, whole, insert, tx) != whole)
                return 0;
            tx.commit();
            return toMb(whole);
        }
    }
    private long move(FluidVariant variant, long n, boolean insert, Transaction tx) {
        return insert ? storage.insert(variant, n, tx) : storage.extract(variant, n, tx);
    }
}
