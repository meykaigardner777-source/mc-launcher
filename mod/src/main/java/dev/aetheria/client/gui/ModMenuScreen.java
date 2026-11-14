package dev.aetheria.client.gui;

import dev.aetheria.client.AetheriaClientMod;
import dev.aetheria.client.config.ClientConfig;
import dev.aetheria.client.hud.HudManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ModMenuScreen extends Screen {
    private boolean prevMouseDown = false;
    private int scrollRow = 0;

    public ModMenuScreen() {
        super(Component.literal("Aetheria Client"));
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

        boolean mouseDown = AetheriaClientMod.windowHandle != 0L
                && GLFW.glfwGetMouseButton(AetheriaClientMod.windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = mouseDown && !prevMouseDown;
        prevMouseDown = mouseDown;

        int sw = this.width;
        int sh = this.height;
        int panelW = Math.min(540, sw - 30);
        int panelH = Math.min(340, sh - 30);
        int px = (sw - panelW) / 2;
        int py = (sh - panelH) / 2;

        g.fill(0, 0, sw, sh, 0x77000000);
        g.fill(px, py, px + panelW, py + panelH, 0xEA10141D);
        HudManager.drawBorder(g, px, py, panelW, panelH, 0xFF4F8EF7);

        // Header
        g.text(mc.font, "✦ AETHERIA CLIENT", px + 14, py + 12, 0xFFFFFFFF, true);

        // Top action buttons
        if (drawBtn(g, mc, px + panelW - 224, py + 8, 108, 20, "Edit HUD Layout", 0xFF2D6CDF, mouseX, mouseY, clicked)) {
            mc.gui.setScreen(new HudEditorScreen());
            return;
        }
        if (drawBtn(g, mc, px + panelW - 110, py + 8, 96, 20, "Save & Close", 0xFF263042, mouseX, mouseY, clicked)) {
            cfg.save();
            mc.gui.setScreen(null);
            return;
        }

        // Scroll controls
        List<ClientConfig.HudModule> mods = new ArrayList<>(cfg.hudModules.values());
        int cols = 2;
        int rowsVisible = Math.max(2, (panelH - 116) / 34);
        int maxScroll = Math.max(0, (int) Math.ceil(mods.size() / (double) cols) - rowsVisible);
        if (scrollRow > maxScroll) scrollRow = maxScroll;

        if (drawBtn(g, mc, px + 14, py + 34, 62, 18, "▲ Up", 0xFF1F2735, mouseX, mouseY, clicked)) {
            scrollRow = Math.max(0, scrollRow - 1);
        }
        if (drawBtn(g, mc, px + 80, py + 34, 62, 18, "▼ Down", 0xFF1F2735, mouseX, mouseY, clicked)) {
            scrollRow = Math.min(maxScroll, scrollRow + 1);
        }

        // Quick gameplay toggles & adjustments row
        int zoomFac = (int) Math.round(cfg.getDouble("cam_zoom_fac", 4));
        if (drawBtn(g, mc, px + 150, py + 34, 105, 18, "Zoom: " + zoomFac + "x (+)", 0xFF1F2735, mouseX, mouseY, clicked)) {
            int next = zoomFac >= 12 ? 2 : zoomFac + 1;
            cfg.setOption("cam_zoom_fac", next);
        }
        String xh = cfg.getString("pvp_xhair_style", "classic");
        if (drawBtn(g, mc, px + 260, py + 34, 128, 18, "Crosshair: " + xh, 0xFF1F2735, mouseX, mouseY, clicked)) {
            String[] styles = {"vanilla", "classic", "dot", "t_shape"};
            int idx = 0;
            for (int i = 0; i < styles.length; i++) if (styles[i].equalsIgnoreCase(xh)) idx = (i + 1) % styles.length;
            cfg.setOption("pvp_xhair_style", styles[idx]);
        }
        boolean lowFire = cfg.getBool("pvp_low_fire", true);
        if (drawBtn(g, mc, px + 393, py + 34, 118, 18, "Low Fire: " + (lowFire ? "ON" : "OFF"), lowFire ? 0xFF215E43 : 0xFF2A2F3A, mouseX, mouseY, clicked)) {
            cfg.setOption("pvp_low_fire", !lowFire);
        }

        // Grid of HUD modules (Toggle + quick scale + quick color/bg)
        int cardW = (panelW - 38) / cols;
        int startIdx = scrollRow * cols;
        for (int r = 0; r < rowsVisible; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = startIdx + r * cols + c;
                if (idx >= mods.size()) break;
                ClientConfig.HudModule m = mods.get(idx);
                int cx = px + 14 + c * (cardW + 10);
                int cy = py + 60 + r * 34;

                g.fill(cx, cy, cx + cardW, cy + 30, 0xCC181E2B);
                HudManager.drawBorder(g, cx, cy, cardW, 30, m.enabled ? 0xFF4F8EF7 : 0x44FFFFFF);
                g.text(mc.font, m.label, cx + 8, cy + 6, 0xFFFFFFFF, false);
                g.text(mc.font, "Scale " + String.format("%.1fx", m.scale) + " • " + Math.round(m.x * 100) + "%," + Math.round(m.y * 100) + "%", cx + 8, cy + 17, 0xFF9AA5B8, false);

                if (drawBtn(g, mc, cx + cardW - 86, cy + 5, 34, 20, "BG", m.bg ? 0xFF334766 : 0xFF222834, mouseX, mouseY, clicked)) {
                    m.bg = !m.bg;
                    cfg.save();
                }
                if (drawBtn(g, mc, cx + cardW - 48, cy + 5, 42, 20, m.enabled ? "ON" : "OFF", m.enabled ? 0xFF216E4E : 0xFF3A2428, mouseX, mouseY, clicked)) {
                    cfg.toggleModule(m);
                }
            }
        }

        // Footer
        g.text(mc.font, "ESC to close • Click 'Edit HUD Layout' to drag every module's position on-screen", px + 14, py + panelH - 16, 0xFF8C97AB, false);
    }

    private boolean drawBtn(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, String label, int bg, int mx, int my, boolean clicked) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hover ? bg + 0x00151515 : bg);
        HudManager.drawBorder(g, x, y, w, h, hover ? 0xAAFFFFFF : 0x44FFFFFF);
        int tw = mc.font.width(label);
        g.text(mc.font, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        return hover && clicked;
    }
}
