package dev.aetheria.client.mixin;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$applyZoomFov(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return;
        float div = ClientState.getSmoothedZoomDivisor();
        if (div > 1.01f) cir.setReturnValue(cir.getReturnValue() / div);
    }
}