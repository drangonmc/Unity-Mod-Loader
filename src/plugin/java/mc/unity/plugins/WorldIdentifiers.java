package mc.unity.plugins;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

final class WorldIdentifiers {
    static UUID load(ServerLevel level,String oldName){
        var root=level.getServer().getWorldPath(LevelResource.ROOT);
        var directory=level.dimension().equals(Level.OVERWORLD)?root:DimensionType.getStorageFolder(level.dimension(),root);
        var path=directory.resolve("uid.dat");
        try {
            if(Files.exists(path))return read(path);
            Files.createDirectories(directory);
            String previous=oldName+"/"+level.getSeed()+"/"+level.dimension().identifier();
            var id=mc.unity.runtime.WorldOrigins.existing(root)?UUID.nameUUIDFromBytes(previous.getBytes(java.nio.charset.StandardCharsets.UTF_8)):UUID.randomUUID();
            var temp=Files.createTempFile(directory,"unity-uid-",".tmp");
            try {
                var data=ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
                Files.write(temp,data.array(),StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.SYNC);
                try{Files.move(temp,path);}catch(FileAlreadyExistsException raced){return read(path);}
                return id;
            } finally {Files.deleteIfExists(temp);}
        } catch(IOException failure){throw new UncheckedIOException("Loading world identity "+path,failure);}
    }
    private static UUID read(Path path)throws IOException{
        try(var in=new DataInputStream(Files.newInputStream(path))){
            var result=new UUID(in.readLong(),in.readLong());
            if(in.read()!=-1)throw new IOException("Invalid uid.dat length: "+path);
            return result;
        }
    }
}
