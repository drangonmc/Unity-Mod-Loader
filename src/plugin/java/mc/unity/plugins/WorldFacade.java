package mc.unity.plugins;

import net.minecraft.server.level.ServerLevel;
public abstract class WorldFacade implements NativeWorldView {
    protected final NativeWorldView view;
    private final ServerLevel level;
    protected WorldFacade(NativeWorldView view,ServerLevel level){this.view=view;this.level=level;}
    public ServerLevel getHandle(){return level;}
    public ServerLevel unityLevel(){return level;}
    static NativeWorldView create(NativeWorldView delegate,ServerLevel level){
        try{return (NativeWorldView)Class.forName("org.bukkit.craftbukkit.CraftWorld",true,WorldFacade.class.getClassLoader())
            .getConstructor(NativeWorldView.class,ServerLevel.class).newInstance(delegate,level);}
        catch(ReflectiveOperationException ex){throw new IllegalStateException("Creating Bukkit world view",ex);}
    }
}
