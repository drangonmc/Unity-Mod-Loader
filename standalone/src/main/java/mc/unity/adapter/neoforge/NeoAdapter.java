package mc.unity.adapter.neoforge;
import com.google.gson.*;
import java.io.*;
import java.lang.classfile.*;
import java.nio.file.*;
import java.util.*;

import mc.unity.adapter.Entry;
import mc.unity.adapter.common.FmlDescriptor;
public final class NeoAdapter {
    public static List<String> subscribers(List<ClassModel> classes, Set<String> inactive, String id,
                                           boolean soleMod) throws IOException {
        var result = new ArrayList<String>();
        for (var type : classes) {
            String name = type.thisClass().asInternalName();
            if (inactive.contains(name)) continue;
            var annotations = type.findAttribute(Attributes.runtimeVisibleAnnotations()).orElseThrow().annotations();
            for (var annotation : annotations) {
                if (!annotation.className().equalsString("Lnet/neoforged/fml/common/EventBusSubscriber;")) continue;
                String owner = stringMember(annotation, "modid");
                if (owner.isEmpty()) {
                    owner = annotations.stream().filter(a -> a.className().equalsString("Lnet/neoforged/fml/common/Mod;"))
                        .map(a -> stringMember(a, "value")).findFirst().orElse("");
                    if (owner.isEmpty() && !soleMod) throw new IOException("Ambiguous subscriber owner: " + name);
                }
                if (owner.isEmpty() || owner.equals(id)) result.add(name.replace('/', '.'));
            }
        }
        return List.copyOf(result);
    }

    private static String stringMember(Annotation annotation, String key) {
        return annotation.elements().stream().filter(e -> e.name().equalsString(key))
            .map(e -> ((AnnotationValue.OfString)e.value()).stringValue()).findFirst().orElse("");
    }

    public static java.lang.reflect.Constructor<?> constructor(Class<?> type) throws IOException {
        var supported = Set.<Class<?>>of(net.neoforged.bus.api.IEventBus.class, net.neoforged.fml.ModContainer.class,
                                        net.neoforged.api.distmarker.Dist.class);
        var choices = Arrays.stream(type.getConstructors()).filter(c -> {
            var params=Arrays.asList(c.getParameterTypes());
            return supported.containsAll(params) && new HashSet<>(params).size()==params.size();
        }).sorted(Comparator.comparingInt((java.lang.reflect.Constructor<?> c)->c.getParameterCount()).reversed()).toList();
        if (choices.isEmpty() || choices.size()>1 && choices.get(0).getParameterCount()==choices.get(1).getParameterCount())
            throw new IOException("No unique injectable NeoForge constructor: "+type.getName()+" "+choices);
        return choices.getFirst();
    }

    public static Entry read(Path file, String side, List<ClassModel> annotated, FmlDescriptor.Meta meta)
        throws IOException {
        var names = new ArrayList<String>();
        boolean declared = false;
        for (var m : annotated) {
            var ann = m.findAttribute(java.lang.classfile.Attributes.runtimeVisibleAnnotations());
            if (ann.isPresent())
                for (var a : ann.get().annotations())
                    if (a.className().equalsString("Lnet/neoforged/fml/common/Mod;")) {
                        if (!a.elements().stream().anyMatch(e
                                                            -> e.name().equalsString("value") &&
                                                                   e.value() instanceof
                                                                       AnnotationValue.OfString v &&
                                                                   v.stringValue().equals(meta.id())))
                            continue;
                        declared = true;
                        boolean relevant =
                            a.elements()
                                .stream()
                                .filter(e -> e.name().equalsString("dist"))
                                .allMatch(e
                                          -> e.value() instanceof AnnotationValue.OfArray arr &&
                                                 arr.values().stream().anyMatch(
                                                     v
                                                     -> v instanceof AnnotationValue.OfEnum en &&
                                                            en.constantName().equalsString(
                                                                side.equals("CLIENT") ? "CLIENT"
                                                                                      : "DEDICATED_SERVER")));
                        if (relevant && !mc.unity.adapter.common.SidedClasses.outside(m, Entry.Kind.NEOFORGE,
                                                                                      side.equals("CLIENT")))
                            names.add(m.thisClass().asInternalName().replace('/', '.'));
                    }
        }
        if (!declared)
            throw new IOException("Expected NeoForge entry point " + meta.id());
        return new Entry(meta.id(), meta.version(), names, Entry.Kind.NEOFORGE, file, meta.dependencies());
    }
}
