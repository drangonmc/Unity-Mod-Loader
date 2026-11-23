package qa.bridge.client;
@org.spongepowered.asm.mixin.Mixin(targets = "com.mojang.blaze3d.vertex.PoseStack", remap = false)
public interface RendererMixin extends qa.plain.Plain.RendererWitness {}
