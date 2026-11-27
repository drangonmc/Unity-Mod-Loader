package net.neoforged.neoforge.client.event;

import java.lang.invoke.MethodHandles;
import java.util.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.*;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterMenuScreensEvent extends Event implements IModBusEvent {
    private final Map<MenuType<?>, MenuScreens.ScreenConstructor<?,?>> screens;
    private static Map<MenuType<?>, MenuScreens.ScreenConstructor<?,?>> installed = Map.of();

    public RegisterMenuScreensEvent(Map<MenuType<?>, MenuScreens.ScreenConstructor<?,?>> screens) {
        this.screens = Objects.requireNonNull(screens);
    }

    public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(
            MenuType<? extends M> type, MenuScreens.ScreenConstructor<M,U> constructor) {
        if (screens.putIfAbsent(Objects.requireNonNull(type), Objects.requireNonNull(constructor)) != null)
            throw new IllegalStateException("Duplicate menu screen: " + net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(type));
    }

    @SuppressWarnings("unchecked")
    private static Map<MenuType<?>, MenuScreens.ScreenConstructor<?,?>> registry() {
        try {
            return (Map<MenuType<?>, MenuScreens.ScreenConstructor<?,?>>) MethodHandles.privateLookupIn(
                MenuScreens.class, MethodHandles.lookup()).findStaticVarHandle(MenuScreens.class, "SCREENS", Map.class).get();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("26.2 menu screen registry", e); }
    }

    public static void fire() {
        var vanilla = registry();
        var pending = new HashMap<>(vanilla);
        var event = new RegisterMenuScreensEvent(pending);
        var mods = ModList.get();
        for (var info : mods.getMods())
            mods.getModContainerById(info.getModId()).orElseThrow().getEventBus().post(event);
        pending.keySet().removeAll(vanilla.keySet());
        installed = Map.copyOf(pending);
        vanilla.putAll(installed);
    }

    public static void clear() {
        if (installed.isEmpty()) return;
        var vanilla = registry();
        installed.forEach(vanilla::remove);
        installed = Map.of();
    }
}
