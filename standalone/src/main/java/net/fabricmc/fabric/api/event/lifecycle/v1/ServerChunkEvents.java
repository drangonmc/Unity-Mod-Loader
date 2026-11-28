package net.fabricmc.fabric.api.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
import mc.unity.adapter.fabric.SessionEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ServerChunkEvents {
    private ServerChunkEvents(){}
    public interface Load { void onChunkLoad(ServerLevel world,LevelChunk chunk,boolean fresh); }
    public interface Generate { void onChunkGenerate(ServerLevel world,LevelChunk chunk); }
    public interface Unload { void onChunkUnload(ServerLevel world,LevelChunk chunk); }
    public static final Event<Load> CHUNK_LOAD=new SessionEvent<>(Load.class,list->(world,chunk,fresh)->{
        for(var cb:list)cb.onChunkLoad(world,chunk,fresh);
    });
    public static final Event<Generate> CHUNK_GENERATE=new SessionEvent<>(Generate.class,list->(world,chunk)->{
        for(var cb:list)cb.onChunkGenerate(world,chunk);
    });
    public static final Event<Unload> CHUNK_UNLOAD=new SessionEvent<>(Unload.class,list->(world,chunk)->{
        for(var cb:list)cb.onChunkUnload(world,chunk);
    });
}
