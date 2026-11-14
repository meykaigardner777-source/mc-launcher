package dev.aetheria.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.config.ClientConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void aetheria$lowFire(PoseStack poseStack, MultiBufferSource bufferSource, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (ClientConfig.get().getBool("pvp_low_fire", true)) {
            poseStack.translate(0.0, -0.32, 0.0);
        }
    }
}
