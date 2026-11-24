package net.fabricmc.loader.api;
import java.util.Collection;
public interface MappingResolver {
    Collection<String> getNamespaces();
    String getCurrentRuntimeNamespace();
    String mapClassName(String namespace, String name);
    String unmapClassName(String namespace, String name);
    String mapMethodName(String namespace, String owner, String name, String descriptor);
    String mapFieldName(String namespace, String owner, String name, String descriptor);
}
