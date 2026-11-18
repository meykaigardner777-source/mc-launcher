package dev.aetheria.client.mixin;

import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import dev.aetheria.client.common.SharedHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
    @Unique private boolean aetheria$hotbarPushed = false;

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
        if (pot != null && pot.enabled && cfg.getBool("potions_hide_vanilla", true)) ci.cancel();
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), require = 0)
    private void aetheria$hotbarPre(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig.HudModule hb = ClientConfig.get().hudModules.get("hotbar");
        if (hb == null || !hb.enabled) return;
        if (Math.abs(hb.x - 0.5) <= 0.002 && Math.abs(hb.y - 1.0) <= 0.002 && Math.abs(hb.scale - 1.0) <= 0.01) return;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        int[] b = SharedHudRenderer.getModulePixelBounds(McBridge.draw(g, mc), McBridge.data(mc), hb, sw, sh);
        float sc = (float) Math.max(0.25, Math.min(4.0, hb.scale));
        g.pose().pushMatrix();
        g.pose().translate(b[0], b[1]);
        g.pose().scale(sc, sc);
        g.pose().translate(-(sw / 2.0F - 91.0F), -(sh - 22.0F));
        aetheria$hotbarPushed = true;
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"), require = 0)
    private void aetheria$hotbarPost(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        if (aetheria$hotbarPushed) { g.pose().popMatrix(); aetheria$hotbarPushed = false; }
    }
}