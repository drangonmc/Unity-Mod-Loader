package mc.unity.plugins;

import java.lang.reflect.*;
import java.util.Objects;

/** Explicit subsets of large server interfaces. Never report success for an unknown operation. */
public final class Views {
    @FunctionalInterface
    public interface Calls {
        Object call(String method, Object[] args) throws Throwable;
    }
    public static <T> T of(Class<T> type, String label, Calls calls) {
        return of(type, label, null, calls);
    }
    public static <T> T of(Class<T> type, String label, Object key, Calls calls) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                                                new Handler(type, label, key, calls)));
    }
    private record Handler(Class<?> type, String label, Object key, Calls calls)
        implements InvocationHandler {
        @Override
        public Object invoke(Object self, Method m, Object[] args) throws Throwable {
            if(m.getDeclaringClass()==Object.class && self instanceof org.bukkit.block.data.BlockData data){
                return switch(m.getName()){
                    case "equals" -> self==args[0] || args[0] instanceof org.bukkit.block.data.BlockData other && data.getAsString().equals(other.getAsString());
                    case "hashCode" -> data.getAsString().hashCode();
                    case "toString" -> data.getAsString();
                    default -> throw unsupported(label,m.getName());
                };
            }
            if (m.getDeclaringClass() == Object.class)
                return switch (m.getName()) {
                    case "toString" -> label;
                    case "equals" ->
                        self == args[0] ||
                            (key != null && args[0] != null && Proxy.isProxyClass(args[0].getClass()) &&
                             Proxy.getInvocationHandler(args[0]) instanceof Handler h && type == h.type &&
                             key.equals(h.key));
                    case "hashCode" -> key == null ? System.identityHashCode(self) : Objects.hash(type, key);
                    default -> throw unsupported(label, m.getName());
                };
            try {
                return calls.call(dispatchName(m), args == null ? new Object[0] : args);
            } catch (UnsupportedCall e) {
                if (m.isDefault())
                    return InvocationHandler.invokeDefault(self, m, args);
                throw e;
            }
        }
    }
    private static String dispatchName(Method m) {
        var p = m.getParameterTypes();
        return switch (m.getName()) {
            case "getChunkAt", "loadChunk", "isChunkLoaded" -> {
                if (p.length >= 2 && p[0] == int.class &&p[1] == int.class)
                    yield m.getName();
                if (p.length == 1 && p[0] == org.bukkit.Chunk.class)
                    yield m.getName() + "/chunk";
                if (p.length == 1 && p[0] == org.bukkit.block.Block.class)
                    yield m.getName() + "/block";
                yield m.toGenericString(); // let Paper execute the default Location / packed-long overloads
            }
            case "getLocation" -> p.length == 0 ? "getLocation" : "getLocation/into";
            case "sendMessage", "sendPlainMessage", "sendRichMessage" -> {
                boolean legacy = p.length == 1 || (p.length == 2 && p[0] == java.util.UUID.class);
                var last = p.length == 0 ? void.class : p[p.length - 1];
                if (legacy && (last == String.class || last == String[].class ||
                               last == net.kyori.adventure.text.Component.class))
                    yield m.getName();
                yield m.toGenericString();
            }
            default -> m.getName();
        };
    }
    static boolean capabilityFailure(Throwable failure) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        for (var t = failure; t != null && seen.add(t); t = t.getCause())
            if (t instanceof UnsupportedCall) return true;
        return false;
    }
    private static final class UnsupportedCall extends UnsupportedOperationException {
        UnsupportedCall(String message) {
            super(message);
        }
    }
    public static UnsupportedOperationException unsupported(String type, String method) {
        return new UnsupportedCall("Unity plugin API does not implement " + type + "." + method);
    }
}
