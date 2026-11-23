package qa.bridge.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value = qa.plain.Plain.class, remap = false)
public abstract class ModMixin {
    @Inject(method = "value", at = @At("HEAD"), cancellable = true, remap = false)
    private static void transform(CallbackInfoReturnable<String> ci) {
        ci.setReturnValue("transformed");
    }
}
