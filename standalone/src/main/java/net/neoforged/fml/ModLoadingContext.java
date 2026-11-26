package net.neoforged.fml;

import java.util.function.Supplier;

public class ModLoadingContext {
    private static final ThreadLocal<ModLoadingContext> CONTEXT = ThreadLocal.withInitial(ModLoadingContext::new);
    private ModContainer active;

    public static ModLoadingContext get() { return CONTEXT.get(); }
    public void setActiveContainer(ModContainer container) { active = container; }
    public ModContainer getActiveContainer() {
        return active == null ? ModList.get().getModContainerById("minecraft").orElseThrow() : active;
    }
    public String getActiveNamespace() { return active == null ? "minecraft" : active.getNamespace(); }
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> key, Supplier<T> extension) {
        getActiveContainer().registerExtensionPoint(key, extension);
    }
    public static ModContainer enter(ModContainer current) {
        var context = get();
        var previous = context.active;
        context.setActiveContainer(current);
        return previous;
    }
}
