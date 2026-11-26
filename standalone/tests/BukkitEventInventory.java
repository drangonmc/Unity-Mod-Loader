import java.lang.classfile.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
/**
 * Read the pinned API without class initialization. Output concrete org.bukkit.event types and their
 * HandlerLists.
 */
class BukkitEventInventory {
    static final Map<String, ClassModel> classes = new HashMap<>();
    static boolean derives(String type, String base) {
        if (type.equals(base))
            return true;
        var model = classes.get(type);
        return model != null &&
            (model.interfaces().stream().anyMatch(i -> derives(i.asInternalName(), base)) ||
             model.superclass().map(s -> derives(s.asInternalName(), base)).orElse(false));
    }
    public static void main(String[] args) throws Exception {
        try (var jar = new JarFile(args[0])) {
            var cf = ClassFile.of();
            for (var e : Collections.list(jar.entries()))
                if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                    try (var in = jar.getInputStream(e)) {
                        var m = cf.parse(in.readAllBytes());
                        classes.put(m.thisClass().asInternalName(), m);
                    }
                }
        }
        var rows = new ArrayList<String>();
        for (var e : new TreeMap<>(classes).entrySet()) {
            var name = e.getKey();
            var m = e.getValue();
            if (!name.startsWith("org/bukkit/event/") ||
                (m.flags().flagsMask() & ClassFile.ACC_ABSTRACT) != 0 ||
                !derives(name, "org/bukkit/event/Event"))
                continue;
            var owner = m;
            while (owner != null &&
                   owner.methods().stream().noneMatch(
                       method
                       -> method.methodName().equalsString("getHandlerList") &&
                              method.methodType().equalsString("()Lorg/bukkit/event/HandlerList;") &&
                              (method.flags().flagsMask() & ClassFile.ACC_STATIC) != 0))
                owner = owner.superclass().map(s -> classes.get(s.asInternalName())).orElse(null);
            rows.add(name.replace('/', '.') + "\t" +
                     (owner == null ? "-" : owner.thisClass().asInternalName().replace('/', '.')) + "\t" +
                     derives(name, "org/bukkit/event/Cancellable"));
        }
        Files.write(Path.of(args[1]), rows);
        System.out.println("Concrete Bukkit event classes: " + rows.size());
    }
}
