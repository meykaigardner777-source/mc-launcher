package dev.aetheria.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$noHurtCam(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
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

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$modifyFov(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        ClientConfig cfg = ClientConfig.get();
        float fov = cir.getReturnValue();

        if (!cfg.getBool("cam_fov_dynamic", true)) {
            fov = (float) cfg.getDouble("cam_fov_base", 70.0);
        } else {
            double factor = cfg.getDouble("cam_fov_factor", 1.0);
            if (factor != 1.0) {
                float base = (float) cfg.getDouble("cam_fov_base", 70.0);
                fov = base + (float) ((fov - base) * factor);
            }
        }

        float zoomDiv = ClientState.getSmoothedZoomDivisor();
        if (zoomDiv > 1.01f) {
            fov /= zoomDiv;
        }

        cir.setReturnValue(fov);
    }
}
