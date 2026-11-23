package net.minecraftforge.common.util;
public enum Result {
    ALLOW,
    DEFAULT,
    DENY;
    public boolean isAllowed() {
        return this == ALLOW;
    }
    public boolean isDenied() {
        return this == DENY;
    }
    public boolean isDefault() {
        return this == DEFAULT;
    }
}
