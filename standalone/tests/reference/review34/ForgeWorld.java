package qa.unity34;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.LevelEvent;
@Mod("forge34world")
public class ForgeWorld {
    public ForgeWorld(){
        LevelEvent.Load.BUS.addListener(e->mark("load",e));
        LevelEvent.Save.BUS.addListener(e->mark("save",e));
        LevelEvent.Unload.BUS.addListener(e->mark("unload",e));
    }
    private static void mark(String phase,LevelEvent e){
        if(e.getLevel() instanceof net.minecraft.world.level.Level local && local.isClientSide())System.out.println("U34 CLIENT FORGEWORLD "+phase);
        if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            System.out.println("U34 FORGE "+phase+" "+level.dimension().identifier());
    }
}
