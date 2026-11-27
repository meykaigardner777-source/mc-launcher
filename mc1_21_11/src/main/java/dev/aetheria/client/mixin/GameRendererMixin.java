package dev.aetheria.client.mixin;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void aetheria$applyZoomFov(Camera camera, float pt, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (!useFovSetting) return;
        float div = ClientState.getSmoothedZoomDivisor();
        if (div > 1.01f) cir.setReturnValue(cir.getReturnValue() / div);
    }
}