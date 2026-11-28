package org.bukkit.craftbukkit.entity;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.*;
import org.bukkit.plugin.Plugin;
import java.util.Set;

/** An actual per-connection permissible holder; permission providers can replace the live base. */
public abstract class CraftHumanEntity implements org.bukkit.entity.HumanEntity {
    protected final Player view;
    private final ServerPlayer initial;
    protected PermissibleBase perm;
    protected CraftHumanEntity(Player view,ServerPlayer initial) {
        this.view=view;this.initial=initial;this.perm=new PermissibleBase(this);
    }
    public ServerPlayer getHandle(){return initial.connection==null?initial:initial.connection.getPlayer();}
    public boolean isOp(){return view.isOp();}
    public boolean hasPermission(String n){return perm.hasPermission(n);}
    public boolean hasPermission(Permission n){return perm.hasPermission(n);}
    public boolean isPermissionSet(String n){return perm.isPermissionSet(n);}
    public boolean isPermissionSet(Permission n){return perm.isPermissionSet(n);}
    public void recalculatePermissions(){perm.recalculatePermissions();}
    public PermissionAttachment addAttachment(Plugin p){return perm.addAttachment(p);}
    public PermissionAttachment addAttachment(Plugin p,int ticks){return perm.addAttachment(p,ticks);}
    public PermissionAttachment addAttachment(Plugin p,String n,boolean v){return perm.addAttachment(p,n,v);}
    public PermissionAttachment addAttachment(Plugin p,String n,boolean v,int ticks){return perm.addAttachment(p,n,v,ticks);}
    public void removeAttachment(PermissionAttachment a){perm.removeAttachment(a);}
    public Set<PermissionAttachmentInfo> getEffectivePermissions(){return perm.getEffectivePermissions();}
    public void clearSessionPermissions(){perm.clearPermissions();}
    @Override public int hashCode(){return initial.getUUID().hashCode();}
    @Override public boolean equals(Object other){return this==other || other instanceof Player p && initial.getUUID().equals(p.getUniqueId());}
    @Override public String toString(){return view.toString();}
    public static Player create(Player delegate,ServerPlayer player) {
        try{return (Player)Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer",true,CraftHumanEntity.class.getClassLoader())
            .getConstructor(Player.class,ServerPlayer.class).newInstance(delegate,player);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Player delegate unavailable",e);}
    }
}
