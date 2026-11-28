package qa.unity34;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import java.util.HashSet;
import java.util.Set;

public class FabricChunks implements ModInitializer {
    private final Set<String> seen=new HashSet<>();
    private void mark(String s){if(seen.add(s))System.out.println("U34 FABRIC "+s);}
    public void onInitialize(){
        ServerChunkEvents.CHUNK_LOAD.register((l,c,f)->mark("load "+f));
        ServerChunkEvents.CHUNK_GENERATE.register((l,c)->mark("generate"));
        ServerChunkEvents.CHUNK_UNLOAD.register((l,c)->mark("unload"));
    }
}
