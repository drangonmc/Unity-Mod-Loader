package qa.plain;
import net.minecraftforge.fml.common.Mod;
@Mod("zz_plainlib")
public final class Plain {
    public static String order = "";
    public Plain() {
        order += "L";
    }
    public static String value() {
        return "untransformed";
    }
    public interface VendorWitness {}
    public interface RendererWitness {}
}
