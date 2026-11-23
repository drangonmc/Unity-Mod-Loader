package net.neoforged.fml;
import net.neoforged.bus.api.IEventBus;
public class ModContainer {
    private final String id;
    private final IEventBus bus;
    private final net.neoforged.neoforgespi.language.IModInfo info;
    private final java.util.Map<Class<?>,java.util.function.Supplier<?>> extensions = new java.util.concurrent.ConcurrentHashMap<>();
    public ModContainer(String id, IEventBus bus) {this(id,bus,id,"");}
    public ModContainer(String id, IEventBus bus,String title,String summary) {
        info=new net.neoforged.neoforgespi.language.IModInfo(){
            public String getModId(){return id;}
            public String getDisplayName(){return title;}
            public String getDescription(){return summary;}
        };
        this.id = id;
        this.bus = bus;
    }
    public String getModId() {
        return id;
    }
    public net.neoforged.neoforgespi.language.IModInfo getModInfo() {return info;}
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> key,T value) { registerExtensionPoint(key,(java.util.function.Supplier<T>)()->value); }
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> key,java.util.function.Supplier<T> value) {
        if(extensions.putIfAbsent(key,value)!=null) throw new IllegalArgumentException("Duplicate extension "+key);
    }
    public <T extends IExtensionPoint> java.util.Optional<T> getCustomExtension(Class<T> key) {
        var supplier=extensions.get(key); return supplier==null ? java.util.Optional.empty() : java.util.Optional.of(key.cast(supplier.get()));
    }
    public String getNamespace() { return id; }
    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type, net.neoforged.fml.config.IConfigSpec spec, String filename) {
        net.neoforged.fml.config.ModConfigs.register(this,type,spec,filename);
    }
    public void registerConfig(net.neoforged.fml.config.ModConfig.Type type, net.neoforged.fml.config.IConfigSpec spec) {
        registerConfig(type,spec,id+"-"+type.extension()+".toml");
    }
    public IEventBus getEventBus() {
        return bus;
    }
}
