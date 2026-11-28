package mc.unity.adapter.common;
import java.io.IOException;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

public final class PlatformAbi {
    private static boolean platform(String s) {
        return s.startsWith("net/minecraftforge/") || s.startsWith("net/neoforged/")
            || s.startsWith("net/fabricmc/") || s.equals(mc.unity.bootstrap.KffLanguage.CONTAINER) || mc.unity.bootstrap.KffLanguage.hostInternal(s+".class");
    }
    private static final Pattern TYPE =
        Pattern.compile("L(net/(?:minecraftforge|neoforged|fabricmc)/[a-zA-Z0-9_$/]+|thedarkcolour/kotlinforforge/neoforge/(?:KotlinModContainer|KotlinLanguageLoader|AutoKotlinEventBusSubscriber)(?:[$][a-zA-Z0-9_$]+)?)");
    private final Map<String,ClassModel> definitions = new HashMap<>();
    public static void check(ClassModel model) throws IOException { new PlatformAbi().verify(model); }
    private java.util.function.Predicate<String> deferred = reason -> false;
    public void verify(ClassModel model) throws IOException { verify(model, reason -> false); }
    public void verify(ClassModel model,java.util.function.Predicate<String> allowDormant) throws IOException {
        deferred=allowDormant;
        try { scan(model); } finally { deferred=reason -> false; }
    }
    private void missing(String reason) throws IOException {
        if(!deferred.test(reason)) throw new IOException(reason);
    }
    private void scan(ClassModel model) throws IOException {
        for (var p : model.constantPool()) {
            if (p instanceof ClassEntry c) {
                String n = c.asInternalName();
                if (platform(n))
                    type(n);
                else
                    descriptor(n);
            }
            if (p instanceof NameAndTypeEntry n)
                descriptor(n.type().stringValue());
            if (p instanceof MethodTypeEntry m)
                descriptor(m.descriptor().stringValue());
            if (p instanceof MemberRefEntry ref && platform(ref.owner().asInternalName())) {
                String owner = ref.owner().asInternalName(), name = ref.name().stringValue(),
                       signature = ref.type().stringValue();
                var definition=type(owner);
                if (definition != null && !member(definition, name, signature, new HashSet<>()))
                    missing("Unsupported platform member: " + owner + "." + name + signature);
            }
        }
        headers(model);
    }
    public void headers(ClassModel model,java.util.function.Predicate<String> allowDormant) throws IOException {
        deferred=allowDormant;
        try { headers(model); } finally { deferred=reason->false; }
    }
    public void headers(ClassModel model) throws IOException {
        if(model.superclass().isPresent()) descriptor("L"+model.superclass().get().asInternalName()+";");
        for(var face:model.interfaces()) descriptor("L"+face.asInternalName()+";");
        attributes(model);
        for (var method : model.methods()) {
            descriptor(method.methodType().stringValue());
            attributes(method);
            if (method.code().isPresent())
                attributes(method.code().get());
        }
        for (var f : model.fields()) {
            descriptor(f.fieldType().stringValue());
            attributes(f);
        }
    }
    private void attributes(AttributedElement owner) throws IOException {
        for (var a : owner.attributes()) {
            if (a instanceof RuntimeVisibleAnnotationsAttribute x)
                for (var n : x.annotations()) annotation(n);
            if (a instanceof RuntimeInvisibleAnnotationsAttribute x)
                for (var n : x.annotations()) annotation(n);
            if (a instanceof RuntimeVisibleTypeAnnotationsAttribute x)
                for (var n : x.annotations()) annotation(n.annotation());
            if (a instanceof RuntimeInvisibleTypeAnnotationsAttribute x)
                for (var n : x.annotations()) annotation(n.annotation());
            if (a instanceof RuntimeVisibleParameterAnnotationsAttribute x)
                for (var list : x.parameterAnnotations())
                    for (var n : list) annotation(n);
            if (a instanceof RuntimeInvisibleParameterAnnotationsAttribute x)
                for (var list : x.parameterAnnotations())
                    for (var n : list) annotation(n);
            if (a instanceof ExceptionsAttribute x)
                for(var exception:x.exceptions()) descriptor("L"+exception.asInternalName()+";");
            if (a instanceof AnnotationDefaultAttribute x)
                value(x.defaultValue());
            if (a instanceof SignatureAttribute x)
                descriptor(x.signature().stringValue());
            if (a instanceof RecordAttribute x)
                for (var field : x.components()) {
                    descriptor(field.descriptor().stringValue());
                    attributes(field);
                }
        }
    }
    private void annotation(Annotation annotation) throws IOException {
        descriptor(annotation.className().stringValue());
        for (var el : annotation.elements()) value(el.value());
    }
    private void value(AnnotationValue value) throws IOException {
        if (value instanceof AnnotationValue.OfClass x)
            descriptor(x.className().stringValue());
        if (value instanceof AnnotationValue.OfEnum x)
            descriptor(x.className().stringValue());
        if (value instanceof AnnotationValue.OfAnnotation x)
            annotation(x.annotation());
        if (value instanceof AnnotationValue.OfArray x)
            for (var el : x.values()) value(el);
    }
    private void descriptor(String d) throws IOException {
        var m = TYPE.matcher(d);
        while (m.find()) type(m.group(1));
    }
    private ClassModel type(String internal) throws IOException {
        var existing = definitions.get(internal);
        if (existing != null) return existing;
        try (var input = PlatformAbi.class.getClassLoader().getResourceAsStream(internal + ".class")) {
            if (input == null) {missing("Unsupported platform type: " + internal); return null;}
            var parsed = ClassFile.of().parse(input.readAllBytes());
            definitions.put(internal, parsed);
            return parsed;
        }
    }
    private boolean member(ClassModel model, String name, String sig, Set<String> seen) throws IOException {
        if (model == null || !seen.add(model.thisClass().asInternalName())) return false;
        if (model.fields().stream().anyMatch(f -> f.fieldName().equalsString(name) && f.fieldType().equalsString(sig))
            || model.methods().stream().anyMatch(m -> m.methodName().equalsString(name) && m.methodType().equalsString(sig)))
            return true;
        if (name.equals("<init>")) return false;
        for (var face : model.interfaces())
            if (member(type(face.asInternalName()), name, sig, seen)) return true;
        var parent = model.superclass();
        return parent.isPresent() && member(type(parent.get().asInternalName()), name, sig, seen);
    }
}
