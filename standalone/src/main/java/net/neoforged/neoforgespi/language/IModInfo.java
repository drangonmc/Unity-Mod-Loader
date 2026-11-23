package net.neoforged.neoforgespi.language;
public interface IModInfo {
    String getModId();
    String getDisplayName();
    String getDescription();
    default String getNamespace() { return getModId(); }
}
