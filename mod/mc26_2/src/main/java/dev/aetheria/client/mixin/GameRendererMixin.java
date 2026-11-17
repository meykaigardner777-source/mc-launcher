package dev.aetheria.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    // 26.2 compatible No Hurt Cam injection
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$noHurtCam(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.getBool("pvp_no_hurtcam", false)) {
            ci.cancel();
            return;
        }
        double factor = cfg.getDouble("pvp_hurtcam_factor", 1.0);
        if (factor <= 0.01) {
            ci.cancel();
        } else if (factor < 0.99) {
            poseStack.scale((float) factor, (float) factor, (float) factor);
        }
    }
}
