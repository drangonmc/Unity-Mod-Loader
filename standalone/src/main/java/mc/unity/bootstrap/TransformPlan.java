package mc.unity.bootstrap;
import java.io.*;
import java.lang.classfile.*;
import java.util.*;
import java.util.jar.JarFile;
/**
 * One ordered chain per vanilla class. Stages that claim the same member at the same priority are ambiguous.
 */
public final class TransformPlan {
    public interface Transform {
        byte[] apply(ClassFile cf, ClassModel model) throws Exception;
    }
    public interface ByteTransform {
        byte[] apply(ClassFile cf, byte[] bytes) throws Exception;
    }
    private record Step(int priority, String adapter, String member, ByteTransform transform) {}
    private final Map<String, List<Step>> classes = new TreeMap<>();
    public void add(String target, int priority, String adapter, String member, Transform transform) {
        addBytes(target, priority, adapter, member, (cf, bytes) -> transform.apply(cf, cf.parse(bytes)));
    }
    public void addBytes(String target, int priority, String adapter, String member, ByteTransform transform) {
        var steps = classes.computeIfAbsent(target, k -> new ArrayList<>());
        if (steps.stream().anyMatch(s
                -> s.priority == priority
                    && (s.member.equals(member) || s.member.equals("*") || member.equals("*"))))
            throw new IllegalArgumentException(
                "Conflicting transform at " + target + "." + member + " priority " + priority);
        steps.add(new Step(priority, adapter, member, transform));
    }
    Map<String, byte[]> build(JarFile original, ClassFile cf) throws Exception {
        var inputs = new LinkedHashMap<String, byte[]>();
        for (var name : classes.keySet()) {
            var entry = original.getJarEntry(name + ".class");
            if (entry == null)
                throw new IOException("Missing original class " + name);
            try (var in = original.getInputStream(entry)) {
                inputs.put(name, in.readAllBytes());
            }
        }
        return buildFrom(inputs, cf);
    }
    public Map<String, byte[]> buildFrom(Map<String, byte[]> inputs, ClassFile cf) throws Exception {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (var group : classes.entrySet()) {
            byte[] bytes = Objects.requireNonNull(inputs.get(group.getKey()), group.getKey());
            group.getValue().sort(Comparator.comparingInt(Step::priority)
                    .thenComparing(Step::adapter)
                    .thenComparing(Step::member));
            for (var step : group.getValue()) {
                try {
                    bytes = step.transform.apply(cf, bytes);
                } catch (Exception e) {
                    throw new IOException(
                        step.adapter + " transformation failed for " + group.getKey() + "." + step.member, e);
                }
            }
            result.put(group.getKey(), bytes);
        }
        return result;
    }
}
