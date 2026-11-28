package mc.unity.client;
import java.util.*;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.neoforged.neoforge.client.gui.PictureInPictureRendererRegistration;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.fml.ModList;

public final class MachinePictures {
    public static List<PictureInPictureRenderer<?>> append(List<PictureInPictureRenderer<?>> vanilla){
        var entries=new ArrayList<PictureInPictureRendererRegistration<?>>();
        var event=new RegisterPictureInPictureRenderersEvent(entries);
        var mods=ModList.get();
        for(var info:mods.getMods())mods.getModContainerById(info.getModId()).orElseThrow().getEventBus().post(event);
        if(entries.isEmpty())return vanilla;
        var result=new ArrayList<>(vanilla);
        for(var entry:entries){
            var renderer=entry.factory().get();
            if(renderer.getRenderStateClass()!=entry.stateClass())throw new IllegalArgumentException("Mismatched renderer for "+entry.stateClass());
            result.add(renderer);
        }
        return result;
    }
}
