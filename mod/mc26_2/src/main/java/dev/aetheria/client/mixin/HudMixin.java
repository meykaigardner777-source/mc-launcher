package dev.aetheria.client.mixin;

import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aetheria$renderHud(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) { McBridge.renderInGame(g); }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void aetheria$cancelCrosshair(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        if (ClientState.shouldCancelVanillaCrosshair()) ci.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$cancelEffects(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        ClientConfig.HudModule pot = cfg.hudModules.get("potions");
        if (pot != null && pot.enabled && cfg.getBool("potions_hide_vanilla", true)) {
            ci.cancel();
        }
    }
}