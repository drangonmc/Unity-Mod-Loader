package mc.unity.adapter.neoforge;
import com.google.gson.*;
import java.io.*;
import java.lang.classfile.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import mc.unity.adapter.Entry;
import mc.unity.adapter.common.FmlDescriptor;
public final class NeoAdapter {
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
                        if (relevant)
                            names.add(m.thisClass().asInternalName().replace('/', '.'));
                    }
        }
        if (!declared)
            throw new IOException("Expected NeoForge entry point " + meta.id());
        return new Entry(meta.id(), meta.version(), names, Entry.Kind.NEOFORGE, file, meta.dependencies());
    }
}
