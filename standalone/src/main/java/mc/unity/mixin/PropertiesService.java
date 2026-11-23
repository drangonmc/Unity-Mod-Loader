package mc.unity.mixin;
import java.util.*;
import org.spongepowered.asm.service.*;
public final class PropertiesService implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey {}
    private final Map<IPropertyKey, Object> values = new java.util.concurrent.ConcurrentHashMap<>();
    public void clear() {
        values.clear();
    }
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }
    public <T> T getProperty(IPropertyKey key) {
        return (T) values.get(key);
    }
    public <T> T getProperty(IPropertyKey key, T fallback) {
        return (T) values.getOrDefault(key, fallback);
    }
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null)
            values.remove(key);
        else
            values.put(key, value);
    }
    public String getPropertyString(IPropertyKey key, String fallback) {
        return String.valueOf(values.getOrDefault(key, fallback));
    }
}
