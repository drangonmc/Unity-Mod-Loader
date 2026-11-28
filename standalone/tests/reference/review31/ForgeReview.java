package qa.unity31.forge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.*;

@Mod("unity31forgereview")
public final class ForgeReview {
    public ForgeReview(){
        LivingHurtEvent.BUS.addListener(e->{if(e.getEntity().entityTags().contains("rev31forgehurt")){System.out.println("FORGE31 hurt-cancel");return true;}return false;});
        LivingDamageEvent.BUS.addListener(e->{if(e.getEntity().entityTags().contains("rev31forgedamage")){System.out.println("FORGE31 damage-cancel");return true;}return false;});
    }
}
