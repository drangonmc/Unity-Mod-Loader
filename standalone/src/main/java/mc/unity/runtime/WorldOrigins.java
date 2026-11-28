package mc.unity.runtime;

import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;

/** Capture the existing-save decision after the storage lock is acquired, before first autosave. */
public final class WorldOrigins {
    private static final ConcurrentHashMap<Path,Boolean> existing=new ConcurrentHashMap<>();
    private static Path root(LevelStorageAccess access){return access.getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize();}
    public static void opened(LevelStorageAccess access){
        Path path=root(access);
        existing.put(path,Files.isRegularFile(path.resolve("level.dat")) || Files.isRegularFile(path.resolve("level.dat_old")));
    }
    public static void closed(LevelStorageAccess access){existing.remove(root(access));}
    public static boolean existing(Path path){
        Boolean value=existing.get(path.toAbsolutePath().normalize());
        if(value==null)throw new IllegalStateException("World identity requested outside a locked storage session");
        return value;
    }
}
