package dev.aetheria.client.mixin;

import dev.aetheria.client.AetheriaCleanFont;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {
    @Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$numericPing(GuiGraphicsExtractor g, int slotWidth, int xo, int yo, PlayerInfo info, CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        if (!cfg.getBool("tab_numeric_ping", true) || g == null || info == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        int ms = info.getLatency();
        String txt = ms < 0 ? "?" : (cfg.getBool("tab_ping_suffix", true) ? (ms + "ms") : String.valueOf(ms));
        int col;
        if (ms < 0) col = 0xFF71717A;
        else if (ms == 0) col = 0xFF22D3EE;
        else if (ms < 80) col = 0xFF10B981;
        else if (ms < 160) col = 0xFFFACC15;
        else if (ms < 250) col = 0xFFF97316;
        else col = 0xFFEF4444;

        boolean clean = cfg.getBool("ui_clean_font", true);
        int tw = clean ? AetheriaCleanFont.width(mc, txt) : mc.font.width(txt);
        float sc = tw > 22 ? 0.65F : 0.75F;
        float drawX = xo + slotWidth - 1.0F - tw * sc;
        float drawY = yo + 1.5F;

        g.pose().pushMatrix();
        g.pose().translate(drawX, drawY);
        g.pose().scale(sc, sc);
        if (clean) AetheriaCleanFont.draw(g, mc, txt, 0, 0, col, true);
        else g.text(mc.font, txt, 0, 0, col, true);
        g.pose().popMatrix();
        ci.cancel();
    }
}