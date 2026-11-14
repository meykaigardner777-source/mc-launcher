package dev.aetheria.client.gui;

import dev.aetheria.client.AetheriaClientMod;
import dev.aetheria.client.config.ClientConfig;
import dev.aetheria.client.hud.HudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends Screen {
    private String selectedId = "fps";
    private String draggingId = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean prevDown = false;

    public HudEditorScreen() {
        super(Component.literal("Aetheria HUD Editor"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        AetheriaClientMod.captureWindow();
        ClientConfig cfg = ClientConfig.get();
        int sw = this.width;
        int sh = this.height;

        boolean down = AetheriaClientMod.windowHandle != 0L
                && GLFW.glfwGetMouseButton(AetheriaClientMod.windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean justPressed = down && !prevDown;
        boolean justReleased = !down && prevDown;
        prevDown = down;

        g.fill(0, 0, sw, sh, 0x33000000);

        // Center snap guide lines
        g.fill(sw / 2, 0, sw / 2 + 1, sh, 0x28FFFFFF);
        g.fill(0, sh / 2, sw, sh / 2 + 1, 0x28FFFFFF);

        // Render all HUD modules + handle click/drag
        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            HudManager.renderModule(g, mc, m, sw, sh, true, true);
            int[] b = HudManager.getModulePixelBounds(mc, m, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];

            if (m.id.equals(selectedId)) {
                HudManager.drawBorder(g, bx - 2, by - 2, bw + 4, bh + 4, 0xFF4F8EF7);
            } else if (!m.enabled) {
                HudManager.drawBorder(g, bx, by, bw, bh, 0x55FF8844);
            }

            if (justPressed && mouseY < sh - 46 && mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
                selectedId = m.id;
                draggingId = m.id;
                dragOffsetX = mouseX - bx;
                dragOffsetY = mouseY - by;
            }
        }

        if (down && draggingId != null) {
            ClientConfig.HudModule dm = cfg.hudModules.get(draggingId);
            if (dm != null) {
                double nx = (mouseX - dragOffsetX) / Math.max(1.0, sw);
                double ny = (mouseY - dragOffsetY) / Math.max(1.0, sh);
                if (cfg.getBool("hud_snap_grid", true)) {
                    nx = Math.round(nx * 40.0) / 40.0;
                    ny = Math.round(ny * 40.0) / 40.0;
                }
                dm.x = Math.max(0.0, Math.min(0.94, nx));
                dm.y = Math.max(0.0, Math.min(0.92, ny));
            }
        }
        if (justReleased && draggingId != null) {
            draggingId = null;
            cfg.save();
        }

        // Bottom inspector bar for selected module
        int barY = sh - 40;
        g.fill(8, barY, sw - 8, sh - 6, 0xE610141D);
        HudManager.drawBorder(g, 8, barY, sw - 16, 34, 0xFF4F8EF7);

        ClientConfig.HudModule sel = cfg.hudModules.get(selectedId);
        if (sel != null) {
            g.text(mc.font, sel.label + " (" + String.format("%.2fx", sel.scale) + ")", 16, barY + 13, 0xFFFFFFFF, false);
            int x = sw - 455;
            if (btn(g, mc, x, barY + 7, 52, 20, sel.enabled ? "ON" : "OFF", sel.enabled ? 0xFF216E4E : 0xFF4A252A, mouseX, mouseY, justPressed)) {
                cfg.toggleModule(sel);
            }
            if (btn(g, mc, x + 56, barY + 7, 36, 20, "S-", 0xFF232B3A, mouseX, mouseY, justPressed)) {
                sel.scale = Math.max(0.5, sel.scale - 0.1); cfg.save();
            }
            if (btn(g, mc, x + 95, barY + 7, 36, 20, "S+", 0xFF232B3A, mouseX, mouseY, justPressed)) {
                sel.scale = Math.min(2.2, sel.scale + 0.1); cfg.save();
            }
            if (btn(g, mc, x + 135, barY + 7, 46, 20, "BG", sel.bg ? 0xFF334766 : 0xFF232B3A, mouseX, mouseY, justPressed)) {
                sel.bg = !sel.bg; cfg.save();
            }
            if (btn(g, mc, x + 185, barY + 7, 52, 20, "Border", sel.border ? 0xFF334766 : 0xFF232B3A, mouseX, mouseY, justPressed)) {
                sel.border = !sel.border; cfg.save();
            }
            if (btn(g, mc, x + 241, barY + 7, 56, 20, "Chroma", sel.chroma ? 0xFF5D3AA6 : 0xFF232B3A, mouseX, mouseY, justPressed)) {
                sel.chroma = !sel.chroma; cfg.save();
            }
            if (btn(g, mc, x + 301, barY + 7, 56, 20, "Reset", 0xFF3C2B2B, mouseX, mouseY, justPressed)) {
                cfg.resetLayout();
            }
            if (btn(g, mc, x + 361, barY + 7, 80, 20, "Back to Menu", 0xFF2D6CDF, mouseX, mouseY, justPressed)) {
                cfg.save();
                mc.gui.setScreen(new ModMenuScreen());
            }
        }
    }

    private boolean btn(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, String txt, int bg, int mx, int my, boolean clicked) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hover ? bg + 0x00151515 : bg);
        HudManager.drawBorder(g, x, y, w, h, 0x55FFFFFF);
        int tw = mc.font.width(txt);
        g.text(mc.font, txt, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        return hover && clicked;
    }
}
