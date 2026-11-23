package net.fabricmc.fabric.api.networking.v1;
import mc.unity.core.ConfigurationNetwork;
import net.minecraft.server.network.ConfigurationTask;
public interface FabricServerConfigurationPacketListenerImpl {
    default void addTask(ConfigurationTask task) {
        ConfigurationNetwork.addTask((ConfigurationNetwork.ServerPeer)this, task);
    }
    default void completeTask(ConfigurationTask.Type type) {
        ConfigurationNetwork.finish((ConfigurationNetwork.ServerPeer)this, type);
    }
}
