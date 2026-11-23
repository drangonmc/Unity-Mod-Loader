package qa.before;
import net.neoforged.fml.common.Mod;
@Mod("zz_neo_before")
public final class Before {
    public Before() {
        qa.plain.Plain.order += "N";
    }
}
