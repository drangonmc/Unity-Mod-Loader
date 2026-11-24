package net.fabricmc.loader.api.metadata;
import net.fabricmc.loader.api.Version;
public interface ModMetadata {
    String getId();
    String getName();
    Version getVersion();
    String getType();
    boolean containsCustomValue(String key);
    CustomValue getCustomValue(String key);
    java.util.Optional<String> getIconPath(int size);
}
