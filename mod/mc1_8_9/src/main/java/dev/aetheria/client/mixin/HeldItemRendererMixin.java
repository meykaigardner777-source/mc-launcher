package dev.aetheria.client.mixin;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), require = 0)
    private void aetheria$lowFire(float tickDelta, CallbackInfo ci) {
        if (ClientConfig.get().getBool("pvp_low_fire", true)) GlStateManager.translatef(0.0F, -0.32F, 0.0F);
    }
}