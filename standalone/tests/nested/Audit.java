package qa.stage;
import net.minecraftforge.event.level.BlockEvent.*;
import net.minecraftforge.fml.common.Mod;
import java.util.function.Consumer;
@Mod("qa_stage_audit")
public final class Audit {
    public Audit() {
        EntityPlaceEvent.BUS.addListener((Consumer<EntityPlaceEvent>) e -> {
            if (e.getPos().getX() == 59 && e.getPos().getY() == 200)
                System.out.println("QA_PLACEMENT_SINGLE " + e.getPos().toShortString());
        });
        EntityMultiPlaceEvent.BUS.addListener((Consumer<EntityMultiPlaceEvent>) e -> {
            if (e.getPos().getX() == 59 && e.getPos().getY() == 200)
                throw new AssertionError("Repeated writes raised a multi-place event");
        });
    }
}
