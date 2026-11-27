package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class GameRendererMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void aetheria$applyZoomFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float div = ClientState.getSmoothedZoomDivisor();
        if (div > 1.01f) cir.setReturnValue(cir.getReturnValue() / div);
    }
}
