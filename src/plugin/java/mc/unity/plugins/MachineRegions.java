package mc.unity.plugins;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;

/** A machine needs approval from each installed region manager. Query the live policy at the actual inventory or block. */
final class MachineRegions {
    private interface Rule {boolean allows(Location at,Player player,OfflinePlayer identity,boolean mining)throws ReflectiveOperationException;}
    private final Host host;
    private final Map<String,Rule> bindings=new HashMap<>();
    private final Set<String> warned=new HashSet<>();
    MachineRegions(Host host){this.host=host;}
    boolean allows(ServerLevel level,BlockPos pos,NameAndId owner,String operation){
        boolean mine=operation.equals("MINE");
        var wg=host.providers.get("WorldGuard");var residence=host.providers.get("Residence");
        if(wg==null && residence==null)return true;
        if(owner==null || !level.getServer().isSameThread())return false;
        var online=level.getServer().getPlayerList().getPlayer(owner.id());
        var identity=online==null?host.server.getOfflinePlayer(owner.id()):host.views.player(online);
        Player subject=online==null || online.level()!=level?null:host.views.player(online);
        if(subject==null && residence!=null){
            String name=owner.name()!=null?owner.name():identity.getName();
            if(name==null || name.isBlank())return false;
            var fake=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(owner.id(),name));
            subject=host.views.player(fake);
        }
        var at=new Location(host.views.world(level),pos.getX(),pos.getY(),pos.getZ());
        boolean worldGuard=evaluate(wg,at,subject,identity,mine);
        boolean res=evaluate(residence,at,subject,identity,mine);
        if(Boolean.getBoolean("mc.unity.machineQA"))Host.LOG.info("U33 POLICY "+operation+" "+pos.toShortString()+" "+owner.id()+" WorldGuard="+worldGuard+" Residence="+res);
        return worldGuard && res;
    }
    private boolean evaluate(PluginFile.Loader loader,Location at,Player player,OfflinePlayer owner,boolean mine){
        if(loader==null)return true;
        Plugin plugin=loader.plugin;
        if(plugin==null || !plugin.isEnabled())return false;
        String name=plugin.getName();
        try{
            var r=bindings.get(name);
            if(r==null){r=name.equalsIgnoreCase("WorldGuard")?new Guard(plugin):new Residence(plugin);bindings.put(name,r);}
            return r.allows(at,player,owner,mine);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            Throwable cause=failure instanceof InvocationTargetException ex?ex.getCause():failure;
            if(cause instanceof VirtualMachineError fatal)throw fatal;
            if(cause instanceof ThreadDeath death)throw death;
            if(warned.add(name))Host.LOG.log(Level.SEVERE,"Machine access denied: "+name+" region query failed",cause);
            return false;
        }
    }
    void close(){bindings.clear();warned.clear();}
    private Guard editGuard;
    boolean allowsEdit(UUID actor,Location at) {
        var loader=host.providers.get("WorldGuard");
        if(loader==null || loader.plugin==null || !loader.plugin.isEnabled())return false;
        try {
            if(editGuard==null)editGuard=new Guard(loader.plugin);
            var player=actor==null?null:host.server.getPlayer(actor);
            var subject=player==null?null:editGuard.wrap.invoke(loader.plugin,player);
            if(subject!=null && (boolean)editGuard.bypass.invoke(editGuard.sessions,subject,editGuard.adaptWorld.invoke(null,at.getWorld()))) return true;
            return (boolean)editGuard.test.invoke(editGuard.query,editGuard.adaptLoc.invoke(null,at),subject,Array.newInstance(editGuard.flag,0));
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("WorldGuard edit authorization failed at "+at,failure);}
    }
    void disabled(Plugin plugin){bindings.remove(plugin.getName());warned.remove(plugin.getName());if(plugin.getName().equalsIgnoreCase("WorldGuard"))editGuard=null;}
    private static final class Guard implements Rule {
        private final Plugin plugin;
        private final Object query,sessions,breakFlag,chestFlag;
        private final Method adaptLoc,adaptWorld,wrap,wrapOffline,test,bypass;
        private final Class<?> flag;
        Guard(Plugin p)throws ReflectiveOperationException{
            plugin=p;var cl=p.getClass().getClassLoader();var wg=cl.loadClass("com.sk89q.worldguard.WorldGuard");
            var platformClass=cl.loadClass("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
            var platform=wg.getMethod("getPlatform").invoke(wg.getMethod("getInstance").invoke(null));
            var local=cl.loadClass("com.sk89q.worldguard.LocalPlayer");
            var world=cl.loadClass("com.sk89q.worldedit.world.World");
            var loc=cl.loadClass("com.sk89q.worldedit.util.Location");
            var adapter=cl.loadClass("com.sk89q.worldedit.bukkit.BukkitAdapter");
            adaptLoc=adapter.getMethod("adapt",Location.class);adaptWorld=adapter.getMethod("adapt",org.bukkit.World.class);
            wrap=p.getClass().getMethod("wrapPlayer",Player.class);wrapOffline=p.getClass().getMethod("wrapOfflinePlayer",OfflinePlayer.class);
            sessions=platformClass.getMethod("getSessionManager").invoke(platform);
            bypass=cl.loadClass("com.sk89q.worldguard.session.SessionManager").getMethod("hasBypass",local,world);
            var regions=platformClass.getMethod("getRegionContainer").invoke(platform);
            query=cl.loadClass("com.sk89q.worldguard.protection.regions.RegionContainer").getMethod("createQuery").invoke(regions);
            flag=cl.loadClass("com.sk89q.worldguard.protection.flags.StateFlag");
            var flags=cl.loadClass("com.sk89q.worldguard.protection.flags.Flags");
            breakFlag=flags.getField("BLOCK_BREAK").get(null);chestFlag=flags.getField("CHEST_ACCESS").get(null);
            test=cl.loadClass("com.sk89q.worldguard.protection.regions.RegionQuery").getMethod("testBuild",loc,local,flag.arrayType());
        }
        public boolean allows(Location at,Player player,OfflinePlayer owner,boolean mine)throws ReflectiveOperationException{
            var subject=owner instanceof Player live?wrap.invoke(plugin,live):wrapOffline.invoke(plugin,owner);
            if(owner instanceof Player && (boolean)bypass.invoke(sessions,subject,adaptWorld.invoke(null,at.getWorld())))return true;
            Object flags=Array.newInstance(flag,1);Array.set(flags,0,mine?breakFlag:chestFlag);
            return (boolean)test.invoke(query,adaptLoc.invoke(null,at),subject,flags);
        }
    }
    private static final class Residence implements Rule {
        private final Plugin plugin;private final Method canBreak,permissions,has,admin,disabled,enabled;private final Object container,use;
        Residence(Plugin p)throws ReflectiveOperationException{
            plugin=p;var cl=p.getClass().getClassLoader();var flags=cl.loadClass("com.bekvon.bukkit.residence.containers.Flags");
            container=flags.getField("container").get(null);use=flags.getField("use").get(null);
            enabled=flags.getMethod("isGlobalyEnabled");
            canBreak=cl.loadClass("com.bekvon.bukkit.residence.listeners.ResidenceBlockListener").getMethod("canBreakBlock",Player.class,org.bukkit.block.Block.class,boolean.class);
            permissions=p.getClass().getMethod("getPermsByLocForPlayer",Location.class,Player.class);
            has=cl.loadClass("com.bekvon.bukkit.residence.protection.FlagPermissions").getMethod("playerHas",Player.class,flags,boolean.class);
            admin=p.getClass().getMethod("isResAdminOn",Player.class);
            disabled=p.getClass().getMethod("isDisabledWorldListener",org.bukkit.block.Block.class);
        }
        public boolean allows(Location at,Player player,OfflinePlayer owner,boolean mine)throws ReflectiveOperationException{
            if(player==null)return false;
            if(mine)return (boolean)canBreak.invoke(null,player,at.getBlock(),false);
            if((boolean)disabled.invoke(plugin,at.getBlock()) || (boolean)admin.invoke(plugin,player) || !(boolean)enabled.invoke(container))return true;
            var perms=permissions.invoke(plugin,at,player);
            return (boolean)has.invoke(perms,player,container,(boolean)has.invoke(perms,player,use,true));
        }
    }
}
