package qa.unity34;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
@Mod("neo34world")
public class NeoWorld {
    public NeoWorld(){
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class,e->mark("load",e));
        NeoForge.EVENT_BUS.addListener(LevelEvent.Save.class,e->mark("save",e));
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class,e->mark("unload",e));
    }
    private static void mark(String phase,LevelEvent e){
        if(e.getLevel() instanceof net.minecraft.world.level.Level local && local.isClientSide())System.out.println("U34 CLIENT NEOWORLD "+phase);
        if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            System.out.println("U34 NEO "+phase+" "+level.dimension().identifier());
    }
}
