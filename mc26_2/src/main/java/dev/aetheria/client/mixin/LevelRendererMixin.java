package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$hideVanillaBlockOutline(CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.getBool("bo_enabled", false) && cfg.getBool("bo_hide_vanilla", true)) ci.cancel();
    }
}