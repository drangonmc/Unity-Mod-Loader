package fixture;
import java.lang.invoke.MethodHandles;
import java.util.*;
import mc.unity.adapter.forge.CancelBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.bus.*;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

public final class BusPriorityChecks {
    public static final class Hit extends MutableEvent implements Cancellable {
        public static final CancellableEventBus<Hit> BUS = new CancelBus<>(Hit.class);
    }
    private static final List<String> calls = new ArrayList<>();
    public static final class Observers {
        @SubscribeEvent(priority = 80)
        public static void first(Hit e) {
            calls.add("first");
        }
        @SubscribeEvent(priority = 50)
        public static boolean veto(Hit e) {
            calls.add("veto");
            return true;
        }
        @SubscribeEvent(priority = -60)
        public static void skipped(Hit e) {
            calls.add("skipped");
        }
        @SubscribeEvent(priority = -128)
        public static void observe(Hit e, boolean cancelled) {
            calls.add("monitor " + cancelled);
        }
    }
    public static final class Always {
        @SubscribeEvent(alwaysCancelling = true)
        public static void stop(Hit e) {
            calls.add("always");
        }
    }
    public static final class Wrong {
        @SubscribeEvent
        public static void tab(BuildCreativeModeTabContentsEvent e) {
            throw new AssertionError();
        }
    }
    public static void verify() {
        var lookup = MethodHandles.lookup();
        var handles = BusGroup.DEFAULT.register(lookup, Observers.class);
        if (!Hit.BUS.post(new Hit()) || !calls.equals(List.of("first", "veto", "monitor true")))
            throw new AssertionError(calls);
        BusGroup.DEFAULT.unregister(handles);
        calls.clear();
        var second = BusGroup.DEFAULT.register(lookup, Always.class);
        if (!Hit.BUS.post(new Hit()) || !calls.equals(List.of("always")))
            throw new AssertionError(calls);
        BusGroup.DEFAULT.unregister(second);
        if (Hit.BUS.post(new Hit()))
            throw new AssertionError("Cancelled after unregister");
        try {
            BusGroup.DEFAULT.register(lookup, Wrong.class);
            throw new AssertionError("Wrong bus was silently ignored");
        } catch (IllegalArgumentException expected) {
            if (!expected.toString().contains("Invalid event subscriber"))
                throw expected;
        }
        System.out.println("PASS annotated boolean cancellation, always-cancel, final monitor, unsubscribe and "
                           + "wrong-bus failure");
    }
}
