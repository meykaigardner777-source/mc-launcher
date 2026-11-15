package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), require = 0)
    private void aetheria$lowFire(float tickDelta, CallbackInfo ci) {
        if (ClientConfig.get().getBool("pvp_low_fire", true)) {
            GL11.glTranslatef(0.0F, (float) ClientConfig.get().getDouble("pvp_low_fire_offset", -0.32), 0.0F);
        }
    }
}
