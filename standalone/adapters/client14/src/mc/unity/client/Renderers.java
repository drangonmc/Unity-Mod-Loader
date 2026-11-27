package mc.unity.client;
import java.lang.invoke.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.EntityType;

public final class Renderers {
    private static MethodHandle factory(Class<?> owner, Class<?> type, Class<?> provider) {
        try {
            return MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
                .findStatic(owner, "register", MethodType.methodType(void.class, type, provider));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    private static final MethodHandle BLOCK =
        factory(BlockEntityRenderers.class, BlockEntityType.class, BlockEntityRendererProvider.class);
    private static final MethodHandle ENTITY =
        factory(EntityRenderers.class, EntityType.class, EntityRendererProvider.class);
    public static void block(BlockEntityType<?> type, BlockEntityRendererProvider<?, ?> provider) {
        try {
            BLOCK.invokeExact(type, provider);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
    public static void entity(EntityType<?> type, EntityRendererProvider<?> provider) {
        try {
            ENTITY.invokeExact(type, provider);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
    public static void setup() {
        net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.fire();
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.fire();
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.fire();
        var list = net.neoforged.fml.ModList.get();
        for (var info : list.getMods()) {
            list.getModContainerById(info.getModId()).orElseThrow().getEventBus().post(new net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions());
            list.getModContainerById(info.getModId())
                .orElseThrow()
                .getEventBus()
                .post(new net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers());
        }
    }
    public static void close() {
        net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.clear();
        net.minecraftforge.client.event.RegisterKeyMappingsEvent.clear();
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.clear();
        net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.clear();
        ModelLayers.clear();
        Models.clear();
    }
}
