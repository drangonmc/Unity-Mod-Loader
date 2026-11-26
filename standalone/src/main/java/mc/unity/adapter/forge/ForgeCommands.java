package mc.unity.adapter.forge;

import java.io.*;
import java.lang.classfile.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import mc.unity.adapter.common.FmlDescriptor;
import mc.unity.core.*;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Discovers original Forge JARs, resolves dependencies and invokes their entry points at registry bootstrap.
 */
public final class ForgeCommands {
    private static final Map<String, List<String>> subscribers = new TreeMap<>();
    public static void subscribe(String id, ClassLoader domain, BusGroup group)
        throws ReflectiveOperationException {
        for (String n : subscribers.getOrDefault(id, List.of())) {
            var type = Class.forName(n, false, domain);
            var spec = type.getAnnotation(net.minecraftforge.fml.common.Mod.EventBusSubscriber.class);
            var lookup =
                java.lang.invoke.MethodHandles.privateLookupIn(type, java.lang.invoke.MethodHandles.lookup());
            if (spec.bus() != net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
                BusGroup.DEFAULT.subscribe(lookup, type, null,
                                           spec.bus() ==
                                               net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.BOTH);
            if (spec.bus() != net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
                group.subscribe(lookup, type, null,
                                spec.bus() == net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.BOTH);
        }
    }
    public static void wire(String side) {
        net.minecraftforge.fml.loading.FMLLoader.setSide(side);
        mc.unity.core.Actions.attach(new ActionAdapter());
        mc.unity.core.Reloads.COLLECT.listen(r -> {
            var bus = net.minecraftforge.event.AddReloadListenerEvent.BUS;
            if (!bus.hasListeners())
                return;
            var e = new net.minecraftforge.event.AddReloadListenerEvent(
                r.resources(), r.resources().fullRegistries().lookup());
            bus.post(e);
            r.listeners().addAll(e.getListeners());
        });
        Events.SERVER_ABOUT_TO_START.listen(GameEvents::about);
        Events.SERVER_STARTING.listen(GameEvents::starting);
        Events.SERVER_STARTED.listen(GameEvents::started);
        Events.SERVER_STOPPING.listen(GameEvents::stopping);
        Events.SERVER_STOPPED.listen(GameEvents::stopped);
        Events.SERVER_TICK_PRE.listen(GameEvents::tickPre);
        Events.SERVER_TICK_POST.listen(GameEvents::tickPost);
        Events.CLIENT_TICK_PRE.listen(v -> GameEvents.clientPre());
        Events.CLIENT_TICK_POST.listen(v -> GameEvents.clientPost());
        Events.PLAYER_LOGIN.listen(GameEvents::joined);
        Events.PLAYER_LOGOUT.listen(GameEvents::left);
        Events.CREATIVE.listen(
            e -> net.minecraftforge.event.BuildCreativeModeTabContentsEvent.post(e.tab(), e.output()));
        Events.COMMANDS.listen(
            e
            -> CommandBus.INSTANCE.dispatch(new net.minecraftforge.event.RegisterCommandsEvent(
                e.dispatcher(), e.selection(), e.context())));
        Events.CLIENT_SETUP.listen(
            ignored -> net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.fire());
    }
    public static mc.unity.adapter.Entry read(Path file, String side, List<ClassModel> annotated,
                                              FmlDescriptor.Meta metaInfo, boolean singleMod)
        throws IOException {
        String id = metaInfo.id();
        String entry = null;
        boolean declaredInactive = false;
        for (var model : annotated) {
            boolean otherSide = mc.unity.adapter.common.SidedClasses.outside(
                model, mc.unity.adapter.Entry.Kind.FORGE, side.equals("CLIENT"));
            var annotations = model.findAttribute(Attributes.runtimeVisibleAnnotations());
            if (annotations.isEmpty())
                continue;
            for (var annotation : annotations.get().annotations())
                if (annotation.className().equalsString(
                        "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")) {
                    boolean enabled = true;
                    if (!singleMod && annotation.elements().stream().noneMatch(
                                          el
                                          -> el.name().equalsString("modid") &&
                                                 el.value() instanceof AnnotationValue.OfString v &&
                                                 !v.stringValue().isEmpty()))
                        throw new IOException("Ambiguous unqualified event subscriber in multi-mod JAR: " +
                                              model.thisClass().asInternalName());
                    for (var el : annotation.elements()) {
                        if (el.name().equalsString("modid") &&
                            el.value() instanceof AnnotationValue.OfString value &&
                            !value.stringValue().isEmpty() && !value.stringValue().equals(id))
                            enabled = false;
                        if (el.name().equalsString("value") && el.value() instanceof
                                                                   AnnotationValue.OfArray arr)
                            enabled &= arr.values().stream().anyMatch(
                                v
                                -> v instanceof AnnotationValue.OfEnum en &&
                                       en.constantName().equalsString(
                                           side.equals("CLIENT") ? "CLIENT" : "DEDICATED_SERVER"));
                    }
                    if (enabled && !otherSide)
                        subscribers.computeIfAbsent(id, k -> new ArrayList<>())
                            .add(model.thisClass().asInternalName().replace('/', '.'));
                }
            for (var a : annotations.get().annotations())
                if (a.className().equalsString("Lnet/minecraftforge/fml/common/Mod;")) {
                    boolean matches = a.elements().stream().anyMatch(
                        e
                        -> e.name().equalsString("value") &&
                               e.value() instanceof AnnotationValue.OfString v && v.stringValue().equals(id));
                    if (!matches)
                        continue;
                    if (otherSide) {
                        declaredInactive = true;
                        continue;
                    }
                    if (entry != null)
                        throw new IOException(
                            "Forge annotation and descriptor disagree or entry is ambiguous: " + id);
                    entry = model.thisClass().asInternalName().replace('/', '.');
                }
        }
        if (entry == null && !declaredInactive)
            throw new IOException("Missing @Mod entry: " + id);
        return new mc.unity.adapter.Entry(id, metaInfo.version(), entry == null ? List.of() : List.of(entry),
                                          mc.unity.adapter.Entry.Kind.FORGE, file, metaInfo.dependencies());
    }
    public static void clear() {
        subscribers.clear();
        net.minecraftforge.registries.DataPackRegistryEvent.NewRegistry.clear();
        GameEvents.clear();
        ActionAdapter.clear();
        CommandBus.INSTANCE.clear();
        net.minecraftforge.event.BuildCreativeModeTabContentsEvent.clear();
        net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.clear();
    }
}
