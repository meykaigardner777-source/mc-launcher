package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aetheria$customHitOverlay(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
        if (state.hasRedOverlay && ClientConfig.get().getBool("pvp_hit_color", false)) {
            cir.setReturnValue(Integer.valueOf(OverlayTexture.pack(OverlayTexture.u(whiteOverlayProgress), OverlayTexture.v(false))));
        }
    }

    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$customHitTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        ClientConfig cfg = ClientConfig.get();
        if (state.hasRedOverlay && cfg.getBool("pvp_hit_color", false)) {
            int custom = cfg.getBool("pvp_hit_color_chroma", false)
                ? ClientState.getChromaColor(2.0, 0.85, 45.0, 0.5, 0.5)
                : ClientState.parseHexColor(cfg.getString("pvp_hit_color_hex", "#22d3ee"), 0xFF22D3EE);
            float alpha = (float) Math.max(0.05, Math.min(1.0, cfg.getDouble("pvp_hit_color_alpha", 0.55)));
            Integer ret = cir.getReturnValue();
            int base = ret != null ? ret.intValue() : 0xFFFFFFFF;
            cir.setReturnValue(Integer.valueOf(ClientState.lerpColor(base, (base & 0xFF000000) | (custom & 0x00FFFFFF), alpha)));
        }
    }
}