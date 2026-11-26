package mc.unity.plugins;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;

/** Native world behind the Bukkit view, available to Unity's versioned platform adapters. */
public interface NativeWorldView extends World {
    ServerLevel unityLevel();
}
