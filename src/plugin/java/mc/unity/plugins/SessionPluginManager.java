package mc.unity.plugins;

import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.PluginManager;

/** Exposes the session command map to classic Bukkit command registration. */
final class SessionPluginManager implements PluginManager {
    private final SimpleCommandMap commandMap;
    private final PluginManager delegate;
    private final SessionPermissions local;
    private volatile io.papermc.paper.plugin.PermissionManager permissionManager;
    private org.bukkit.plugin.Plugin permissionOwner;
    SessionPluginManager(SimpleCommandMap commandMap, PluginManager delegate, org.bukkit.plugin.SimplePluginManager facade) {
        local=new SessionPermissions(facade);
        permissionManager=local;
        this.commandMap=commandMap;
        this.delegate=delegate;
    }
    @Override public void addPermissions(java.util.List<org.bukkit.permissions.Permission> permissions) { permissionManager.addPermissions(permissions); }
    @Override public void clearPermissions() { permissionManager.clearPermissions(); }
    @Override
    public void registerInterface(java.lang.Class<? extends org.bukkit.plugin.PluginLoader> type) throws java.lang.IllegalArgumentException {
        delegate.registerInterface(type);
    }
    @Override
    public org.bukkit.plugin.Plugin getPlugin(java.lang.String name) {
        return delegate.getPlugin(name);
    }
    @Override
    public org.bukkit.plugin.Plugin[] getPlugins() {
        return delegate.getPlugins();
    }
    @Override
    public boolean isPluginEnabled(java.lang.String name) {
        return delegate.isPluginEnabled(name);
    }
    @Override
    public boolean isPluginEnabled(org.bukkit.plugin.Plugin plugin) {
        return delegate.isPluginEnabled(plugin);
    }
    @Override
    public org.bukkit.plugin.Plugin loadPlugin(java.io.File file) throws org.bukkit.plugin.InvalidPluginException, org.bukkit.plugin.InvalidDescriptionException, org.bukkit.plugin.UnknownDependencyException {
        return delegate.loadPlugin(file);
    }
    @Override
    public org.bukkit.plugin.Plugin[] loadPlugins(java.io.File file) {
        return delegate.loadPlugins(file);
    }
    @Override
    public org.bukkit.plugin.Plugin[] loadPlugins(java.io.File[] files) {
        return delegate.loadPlugins(files);
    }
    @Override
    public void disablePlugins() {
        delegate.disablePlugins();
    }
    @Override
    public void clearPlugins() {
        delegate.clearPlugins();
    }
    @Override
    public void callEvent(org.bukkit.event.Event event) throws java.lang.IllegalStateException {
        delegate.callEvent(event);
    }
    @Override
    public void registerEvents(org.bukkit.event.Listener listener, org.bukkit.plugin.Plugin plugin) {
        delegate.registerEvents(listener, plugin);
    }
    @Override
    public void registerEvent(java.lang.Class<? extends org.bukkit.event.Event> type, org.bukkit.event.Listener listener, org.bukkit.event.EventPriority priority, org.bukkit.plugin.EventExecutor executor, org.bukkit.plugin.Plugin plugin) {
        delegate.registerEvent(type, listener, priority, executor, plugin);
    }
    @Override
    public void registerEvent(java.lang.Class<? extends org.bukkit.event.Event> type, org.bukkit.event.Listener listener, org.bukkit.event.EventPriority priority, org.bukkit.plugin.EventExecutor executor, org.bukkit.plugin.Plugin plugin, boolean flag) {
        delegate.registerEvent(type, listener, priority, executor, plugin, flag);
    }
    @Override
    public void enablePlugin(org.bukkit.plugin.Plugin plugin) {
        delegate.enablePlugin(plugin);
    }
    @Override
    public void disablePlugin(org.bukkit.plugin.Plugin plugin) {
        delegate.disablePlugin(plugin);
    }
    @Override
    public org.bukkit.permissions.Permission getPermission(java.lang.String name) {
        return permissionManager.getPermission(name);
    }
    @Override
    public void addPermission(org.bukkit.permissions.Permission permission) {
        permissionManager.addPermission(permission);
    }
    @Override
    public void removePermission(org.bukkit.permissions.Permission permission) {
        permissionManager.removePermission(permission);
    }
    @Override
    public void removePermission(java.lang.String name) {
        permissionManager.removePermission(name);
    }
    @Override
    public java.util.Set<org.bukkit.permissions.Permission> getDefaultPermissions(boolean flag) {
        return permissionManager.getDefaultPermissions(flag);
    }
    @Override
    public void recalculatePermissionDefaults(org.bukkit.permissions.Permission permission) {
        permissionManager.recalculatePermissionDefaults(permission);
    }
    @Override
    public void subscribeToPermission(java.lang.String name, org.bukkit.permissions.Permissible permissible) {
        permissionManager.subscribeToPermission(name, permissible);
    }
    @Override
    public void unsubscribeFromPermission(java.lang.String name, org.bukkit.permissions.Permissible permissible) {
        permissionManager.unsubscribeFromPermission(name, permissible);
    }
    @Override
    public java.util.Set<org.bukkit.permissions.Permissible> getPermissionSubscriptions(java.lang.String name) {
        return permissionManager.getPermissionSubscriptions(name);
    }
    @Override
    public void subscribeToDefaultPerms(boolean flag, org.bukkit.permissions.Permissible permissible) {
        permissionManager.subscribeToDefaultPerms(flag, permissible);
    }
    @Override
    public void unsubscribeFromDefaultPerms(boolean flag, org.bukkit.permissions.Permissible permissible) {
        permissionManager.unsubscribeFromDefaultPerms(flag, permissible);
    }
    @Override
    public java.util.Set<org.bukkit.permissions.Permissible> getDefaultPermSubscriptions(boolean flag) {
        return permissionManager.getDefaultPermSubscriptions(flag);
    }
    @Override
    public java.util.Set<org.bukkit.permissions.Permission> getPermissions() {
        return permissionManager.getPermissions();
    }
    @Override
    public boolean useTimings() {
        return delegate.useTimings();
    }
    @Override
    public boolean isTransitiveDependency(io.papermc.paper.plugin.configuration.PluginMeta value1, io.papermc.paper.plugin.configuration.PluginMeta value2) {
        return delegate.isTransitiveDependency(value1, value2);
    }
    @Override
    public synchronized void overridePermissionManager(org.bukkit.plugin.Plugin plugin, io.papermc.paper.plugin.PermissionManager value2) {
        java.util.Objects.requireNonNull(plugin);
        if (permissionOwner!=null && permissionOwner!=plugin) throw new IllegalStateException("Permission manager owned by "+permissionOwner.getName());
        permissionOwner=value2==null?null:plugin;
        permissionManager=value2==null?local:value2;
    }
}
