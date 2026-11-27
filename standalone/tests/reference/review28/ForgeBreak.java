package qa.review28;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
@Mod("unity28forge")
public final class ForgeBreak {
    public ForgeBreak() {
        BreakEvent.BUS.addListener(e -> {
            var p=e.getPos();
            boolean cancel=p.getY()==80 && p.getZ()==8 && (p.getX()==35 || p.getX()==37);
            if(cancel)System.out.println("UNITY28_FORGE_CANCEL "+p.getX());
            return cancel;
        });
    }
}
