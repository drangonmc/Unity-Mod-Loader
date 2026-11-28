package mc.unity.client;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.IRenderableSection;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.ModList;

public final class MachineModels {
    private static boolean ready;
    private static final Map<Boolean,ExtraCodecs.LateBoundIdMapper> pending=new LinkedHashMap<>();
    public static void ready(){ready=true;pending.forEach((special,mapper)->dispatch(mapper,special));pending.clear();}
    public static void clear(){ready=false;pending.clear();}
    @SuppressWarnings("unchecked")
    public static void models(ExtraCodecs.LateBoundIdMapper mapper,boolean special) {
        if(!ready){pending.put(special,mapper);return;}
        dispatch(mapper,special);
    }
    @SuppressWarnings("unchecked")
    private static void dispatch(ExtraCodecs.LateBoundIdMapper mapper,boolean special) {
        Event event=special?new RegisterSpecialModelRendererEvent(mapper):new RegisterItemModelsEvent(mapper);
        var mods=ModList.get();
        for(var info:mods.getMods())mods.getModContainerById(info.getModId()).orElseThrow().getEventBus().post(event);
    }
    public static boolean outline(LevelExtractor extractor,ClientLevel level,BlockPos pos,BlockState state,BlockHitResult hit,
                                  CollisionContext context,boolean translucent,boolean contrast,Camera camera,LevelRenderState renderState){
        if(!mc.unity.adapter.neoforge.NeoBus.clientEvent(ExtractBlockOutlineRenderStateEvent.class))return false;
        var event=new ExtractBlockOutlineRenderStateEvent(extractor,level,pos,state,hit,context,translucent,contrast,camera,renderState);
        NeoForge.EVENT_BUS.post(event);
        if(!event.getCustomRenderers().isEmpty())throw new UnsupportedOperationException("Additional per-block outline renderers");
        return event.isCanceled();
    }
    public static void submit(LevelRenderState state,SubmitNodeCollector collector,PoseStack pose,Iterable<RenderSection> visible) {
        if(!mc.unity.adapter.neoforge.NeoBus.clientEvent(SubmitCustomGeometryEvent.class))return;
        Iterable<IRenderableSection> sections=()->new Iterator<>(){
            final Iterator<RenderSection> it=visible.iterator();
            public boolean hasNext(){return it.hasNext();}
            public IRenderableSection next(){var sec=it.next();return new IRenderableSection(){
                public BlockPos getRenderOrigin(){return sec.getRenderOrigin();}
                public AABB getBoundingBox(){return sec.getBoundingBox();}
                public boolean isEmpty(){return !sec.getSectionMesh().hasRenderableLayers();}
            };}
        };
        NeoForge.EVENT_BUS.post(new SubmitCustomGeometryEvent(state,collector,pose,sections));
    }
}
