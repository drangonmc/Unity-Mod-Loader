package mc.unity.plugins;

import org.bukkit.Server;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/** Root of the generated Bukkit Server delegate used by classic server implementation lookups. */
public abstract class ServerFacade implements Server {
    protected final Server view;
    private final MinecraftServer handle;
    protected ServerFacade(Server view, MinecraftServer handle) {this.view=view;this.handle=handle;}
    public DedicatedServer getServer(){return (DedicatedServer)handle;}
    static Server create(Server delegate,MinecraftServer handle) {
        try {return (Server)Class.forName("org.bukkit.craftbukkit.CraftServer",true,ServerFacade.class.getClassLoader())
            .getConstructor(Server.class,MinecraftServer.class).newInstance(delegate,handle);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Server delegate unavailable",e);}
    }
}
