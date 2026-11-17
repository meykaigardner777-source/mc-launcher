package dev.aetheria.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void aetheria$lowFire(PoseStack ps, SubmitNodeCollector snc, TextureAtlasSprite sp, CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.getBool("pvp_low_fire", true)) {
            double offset = cfg.getDouble("pvp_low_fire_offset", -0.32);
            ps.translate(0.0, offset, 0.0);
        }
    }
}
