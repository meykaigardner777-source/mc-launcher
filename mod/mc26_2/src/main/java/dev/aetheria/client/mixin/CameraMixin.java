package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$modifyFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        ClientConfig cfg = ClientConfig.get();
        Minecraft mc = Minecraft.getInstance();
        float fov = cir.getReturnValue();
        float baseFov = ((Number) mc.options.fov().get()).floatValue();

        if (!cfg.getBool("cam_fov_dynamic", true)) {
            fov = baseFov;
        } else {
            boolean usingItem = mc.player != null && mc.player.isUsingItem();
            boolean sprinting = mc.player != null && mc.player.isSprinting();
            if (!cfg.getBool("cam_fov_bow", true) && usingItem && fov < baseFov) {
                fov = baseFov;
            } else if (!cfg.getBool("cam_fov_sprint", true) && sprinting && !usingItem && fov > baseFov) {
                fov = baseFov;
            } else {
                double factor = cfg.getDouble("cam_fov_factor", 1.0);
                if (Math.abs(factor - 1.0) > 0.01) {
                    fov = baseFov + (float) ((fov - baseFov) * factor);
                }
            }
        }

        float div = ClientState.getSmoothedZoomDivisor();
        if (div > 1.01f) {
            fov /= div;
        }
        cir.setReturnValue(fov);
    }
}