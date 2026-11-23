package net.neoforged.neoforge.network.event;
import java.util.*;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.server.network.ConfigurationTask;
public class RegisterConfigurationTasksEvent
    extends net.neoforged.bus.api.Event implements net.neoforged.fml.event.IModBusEvent {
    private final ServerConfigurationPacketListener listener;
    private final Queue<ConfigurationTask> tasks = new ArrayDeque<>();
    public RegisterConfigurationTasksEvent(ServerConfigurationPacketListener listener) {
        this.listener = listener;
    }
    public ServerConfigurationPacketListener getListener() { return listener; }
    public void register(ConfigurationTask task) { tasks.add(task); }
    public Queue<ConfigurationTask> getConfigurationTasks() { return new ArrayDeque<>(tasks); }
}
