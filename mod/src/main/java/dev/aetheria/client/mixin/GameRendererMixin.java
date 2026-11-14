package dev.aetheria.client.mixin;

import dev.aetheria.client.AetheriaClientMod;
import dev.aetheria.client.config.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void aetheria$applyZoomFov(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (AetheriaClientMod.isZooming()) {
            float factor = (float) ClientConfig.get().getZoomFactor();
            cir.setReturnValue(cir.getReturnValue() / factor);
        }
    }
}
