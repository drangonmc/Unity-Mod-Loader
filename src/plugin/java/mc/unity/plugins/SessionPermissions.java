package mc.unity.plugins;

import io.papermc.paper.plugin.PermissionManager;
import org.bukkit.permissions.*;
import java.util.*;

final class SessionPermissions implements PermissionManager {
    private final org.bukkit.plugin.SimplePluginManager registry;
    SessionPermissions(org.bukkit.plugin.SimplePluginManager registry) {this.registry=registry;}
    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
    private static Set<Permissible> weak() { return Collections.newSetFromMap(new WeakHashMap<>()); }
    public synchronized Permission getPermission(String name){return registry.permissions.get(key(name));}
    public void addPermission(Permission p) {
        synchronized(this) {
            if(registry.permissions.putIfAbsent(key(p.getName()),p)!=null)throw new IllegalArgumentException("Duplicate permission "+p.getName());
        }
        recalculatePermissionDefaults(p);
    }
    public void removePermission(Permission p){removePermission(p.getName());}
    public void removePermission(String name) {
        Permission p;
        synchronized(this){p=registry.permissions.remove(key(name));}
        if(p!=null)recalculatePermissionDefaults(p);
    }
    public synchronized Set<Permission> getDefaultPermissions(boolean op) {
        return Set.copyOf(registry.defaultPerms.getOrDefault(op,Set.of()));
    }
    public void recalculatePermissionDefaults(Permission p) {
        Set<Permissible> listeners = new LinkedHashSet<>();
        synchronized(this) {
            for(boolean op:new boolean[]{true,false}) {
                var set=registry.defaultPerms.get(op);
                if(registry.permissions.containsKey(key(p.getName())) && p.getDefault().getValue(op))set.add(p);else set.remove(p);
            }
            registry.defSubs.values().forEach(s->listeners.addAll(s.keySet()));
            listeners.addAll(getPermissionSubscriptions(p.getName()));
        }
        listeners.forEach(Permissible::recalculatePermissions);
    }
    public synchronized void subscribeToPermission(String name,Permissible p){registry.permSubs.computeIfAbsent(key(name),k->new WeakHashMap<>()).put(p,true);}
    public synchronized void unsubscribeFromPermission(String name,Permissible p){
        var s=registry.permSubs.get(key(name));
        if(s!=null){s.remove(p);if(s.isEmpty())registry.permSubs.remove(key(name));}
    }
    public synchronized Set<Permissible> getPermissionSubscriptions(String name){return Set.copyOf(registry.permSubs.getOrDefault(key(name),Map.of()).keySet());}
    public synchronized void subscribeToDefaultPerms(boolean op,Permissible p){registry.defSubs.computeIfAbsent(op,k->new WeakHashMap<>()).put(p,true);}
    public synchronized void unsubscribeFromDefaultPerms(boolean op,Permissible p){var s=registry.defSubs.get(op);if(s!=null)s.remove(p);}
    public synchronized Set<Permissible> getDefaultPermSubscriptions(boolean op){return Set.copyOf(registry.defSubs.getOrDefault(op,Map.of()).keySet());}
    public synchronized Set<Permission> getPermissions(){return Set.copyOf(registry.permissions.values());}
    public void addPermissions(List<Permission> permissions){permissions.forEach(this::addPermission);}
    public void clearPermissions(){
        Set<Permissible> notify=new HashSet<>();
        synchronized(this){registry.permissions.clear();registry.defaultPerms.values().forEach(Set::clear);registry.defSubs.values().forEach(s->notify.addAll(s.keySet()));registry.permSubs.values().forEach(s->notify.addAll(s.keySet()));}
        notify.forEach(Permissible::recalculatePermissions);
    }
}
