package mc.unity.adapter.common;

import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.util.*;
import com.google.gson.JsonObject;
import mc.unity.adapter.Entry;

/** Drop only code exclusive to an unselected platform or physical side. Shared dependencies stay checked. */
public final class SidedClasses {
    private static final java.util.regex.Pattern REFERENCE = java.util.regex.Pattern.compile("L([^;\\[]+);");
    private static void descriptor(Set<String> names, String text) {
        var matches = REFERENCE.matcher(text);
        while (matches.find())
            names.add(matches.group(1));
    }
    public static Set<String> inactive(List<ClassModel> classes, PlatformChoice.Choice choice, boolean client,
                                       JsonObject fabric) throws IOException {
        if (choice.kind() == null || choice.kind() == Entry.Kind.FABRIC && !choice.multiple())
            return Set.of();
        var excluded = new HashSet<String>();
        var otherSide = new HashSet<String>();
        var active = new HashSet<String>();
        if (fabric != null && fabric.has("entrypoints"))
            for (var row : fabric.getAsJsonObject("entrypoints").entrySet())
                if (row.getKey().equals("main") || row.getKey().equals(client ? "client" : "server"))
                    for (var val : row.getValue().getAsJsonArray()) {
                        String name = val.isJsonPrimitive()
                                          ? val.getAsString()
                                          : val.getAsJsonObject().get("value").getAsString();
                        active.add(name.split("::", 2)[0].replace('.', '/'));
                    }
        for (var model : classes) {
            var annotations = new ArrayList<Annotation>();
            model.findAttribute(Attributes.runtimeVisibleAnnotations())
                .ifPresent(a -> annotations.addAll(a.annotations()));
            model.findAttribute(Attributes.runtimeInvisibleAnnotations())
                .ifPresent(a -> annotations.addAll(a.annotations()));
            String name = model.thisClass().asInternalName();
            boolean selected = false, foreign = false, wrongSide = false;
            for (var ann : annotations) {
                String type = ann.className().stringValue();
                boolean neo = type.startsWith("Lnet/neoforged/"), forge =
                                                                      type.startsWith("Lnet/minecraftforge/");
                if (!neo && !forge)
                    continue;
                boolean entry = type.endsWith("/fml/common/Mod;") ||
                                type.endsWith("/fml/common/EventBusSubscriber;") ||
                                type.endsWith("/fml/common/Mod$EventBusSubscriber;");
                boolean only = type.endsWith("/api/distmarker/OnlyIn;");
                if (!entry && !only)
                    continue;
                if (only && ann.elements().stream().anyMatch(e -> e.name().equalsString("_interface")))
                    continue;
                if (choice.kind() != (neo ? Entry.Kind.NEOFORGE : Entry.Kind.FORGE)) {
                    if (entry)
                        foreign = true;
                    continue;
                }
                if (entry)
                    selected = true;
                String qualifier = type.endsWith("/common/Mod;") ? "dist" : "value";
                for (var e : ann.elements())
                    if (e.name().equalsString(qualifier) && (e.value() instanceof AnnotationValue.OfArray ||
                                                             e.value() instanceof AnnotationValue.OfEnum)) {
                        var vals =
                            e.value() instanceof AnnotationValue.OfArray a ? a.values() : List.of(e.value());
                        if (vals.stream().noneMatch(
                                v
                                -> v instanceof AnnotationValue.OfEnum d &&
                                       d.constantName().equalsString(client ? "CLIENT" : "DEDICATED_SERVER")))
                            wrongSide = true;
                    }
            }
            if (wrongSide)
                otherSide.add(name);
            if (wrongSide || foreign && !selected && !active.contains(name))
                excluded.add(name);
            else if (selected)
                active.add(name);
        }
        if (excluded.isEmpty())
            return Set.of();
        var graph = new HashMap<String, Set<String>>();
        var headers = new HashMap<String, Set<String>>();
        for (var m : classes) {
            String name = m.thisClass().asInternalName();
            var hard = new HashSet<String>();
            m.superclass().ifPresent(c -> hard.add(c.asInternalName()));
            m.interfaces().forEach(c -> hard.add(c.asInternalName()));
            m.fields().forEach(field -> descriptor(hard, field.fieldType().stringValue()));
            m.methods().forEach(method -> descriptor(hard, method.methodType().stringValue()));
            headers.put(name, hard);
            var refs = new HashSet<>(hard);
            for (var method : m.methods()) {
                method.code().ifPresent(code -> code.forEach(e -> {
                    if (e instanceof java.lang.classfile.instruction.NewObjectInstruction i)
                        refs.add(i.className().asInternalName());
                    if (e instanceof java.lang.classfile.instruction.TypeCheckInstruction i)
                        refs.add(i.type().asInternalName());
                    if (e instanceof java.lang.classfile.instruction.NewReferenceArrayInstruction i)
                        refs.add(i.componentType().asInternalName());
                    if (e instanceof java.lang.classfile.instruction.ConstantInstruction i &&
                        i.constantValue() instanceof java.lang.constant.ClassDesc t)
                        descriptor(refs, t.descriptorString());
                }));
            }
            for (var v : m.constantPool())
                if (v instanceof MemberRefEntry ref) {
                    refs.add(ref.owner().asInternalName());
                    descriptor(refs, ref.nameAndType().type().stringValue());
                }
            graph.put(m.thisClass().asInternalName(), refs);
        }
        var dormant = closure(excluded, graph, Set.of());
        graph.keySet().stream().filter(n -> !dormant.contains(n)).forEach(active::add);
        var live = closure(active, graph, otherSide);
        for (String owner : live)
            for (String dependency : headers.getOrDefault(owner, Set.of())) {
                if (otherSide.contains(dependency))
                    throw new IOException("Active class " + owner +
                                          " declares an inactive platform/side type " + dependency);
            }
        dormant.removeAll(live);
        return dormant;
    }
    public static boolean outside(ClassModel m, Entry.Kind platform, boolean client) {
        var annotations = new ArrayList<Annotation>();
        m.findAttribute(Attributes.runtimeVisibleAnnotations())
            .ifPresent(v -> annotations.addAll(v.annotations()));
        m.findAttribute(Attributes.runtimeInvisibleAnnotations())
            .ifPresent(v -> annotations.addAll(v.annotations()));
        var prefix = platform == Entry.Kind.NEOFORGE ? "Lnet/neoforged/" : "Lnet/minecraftforge/";
        for (var annotation : annotations) {
            if (!annotation.className().equalsString(prefix + "api/distmarker/OnlyIn;"))
                continue;
            if (annotation.elements().stream().anyMatch(e -> e.name().equalsString("_interface")))
                continue;
            for (var e : annotation.elements())
                if (e.name().equalsString("value") && e.value() instanceof AnnotationValue.OfEnum value &&
                    !value.constantName().equalsString(client ? "CLIENT" : "DEDICATED_SERVER"))
                    return true;
        }
        return false;
    }
    private static Set<String> closure(Set<String> roots, Map<String, Set<String>> graph,
                                       Set<String> excluded) {
        var seen = new HashSet<>(roots);
        var todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty())
            for (String n : graph.getOrDefault(todo.remove(), Set.of()))
                if (!excluded.contains(n) && graph.containsKey(n) && seen.add(n))
                    todo.add(n);
        return seen;
    }
}
