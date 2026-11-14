package dev.aetheria.client.common;

import java.util.*;

public class SharedHudEditor {
    private String selectedId = "fps", draggingId = null;
    private double dragOffsetX = 0, dragOffsetY = 0;

    public interface Actions { void backToMenu(); }

    public void render(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, int mx, int my) {
        g.fill(0, 0, sw, sh, 0x33000000);
        g.fill(sw / 2, 0, sw / 2 + 1, sh, 0x28FFFFFF);
        g.fill(0, sh / 2, sw, sh / 2 + 1, 0x28FFFFFF);
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            SharedHudRenderer.renderModule(g, d, m, sw, sh, true, true);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            if (m.id.equals(selectedId)) g.drawBorder(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 0xFF4F8EF7);
            else if (!m.enabled) g.drawBorder(b[0], b[1], b[2], b[3], 0x55FF8844);
        }
        drawOrHandleBar(g, sw, sh, mx, my, false, null);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, Actions actions) {
        int barH = sw < 560 ? 58 : 34;
        if (my >= sh - barH - 8) return drawOrHandleBar(g, sw, sh, (int) mx, (int) my, true, actions);
        List<ClientConfig.HudModule> list = new ArrayList<ClientConfig.HudModule>(ClientConfig.get().hudModules.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientConfig.HudModule m = list.get(i);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                selectedId = m.id; draggingId = m.id; dragOffsetX = mx - b[0]; dragOffsetY = my - b[1]; return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my) {
        if (draggingId == null) return false;
        ClientConfig cfg = ClientConfig.get(); ClientConfig.HudModule dm = cfg.hudModules.get(draggingId);
        if (dm == null) return false;
        int[] b = SharedHudRenderer.getModulePixelBounds(g, d, dm, sw, sh);
        double maxX = Math.max(0.0, (double) (sw - b[2]) / Math.max(1.0, sw));
        double maxY = Math.max(0.0, (double) (sh - (sw < 560 ? 66 : 44) - b[3]) / Math.max(1.0, sh));
        double nx = (mx - dragOffsetX) / Math.max(1.0, sw), ny = (my - dragOffsetY) / Math.max(1.0, sh);
        if (cfg.getBool("hud_snap_grid", true)) { nx = Math.round(nx * 40.0) / 40.0; ny = Math.round(ny * 40.0) / 40.0; }
        dm.x = Math.max(0.0, Math.min(maxX, nx)); dm.y = Math.max(0.0, Math.min(maxY, ny));
        return true;
    }

    public boolean mouseReleased() { if (draggingId != null) { draggingId = null; ClientConfig.get().save(); return true; } return false; }

    private boolean drawOrHandleBar(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions) {
        ClientConfig cfg = ClientConfig.get(); ClientConfig.HudModule sel = cfg.hudModules.get(selectedId);
        boolean twoRow = sw < 560; int barH = twoRow ? 58 : 34, barY = sh - barH - 6;
        if (!click) { g.fill(8, barY, sw - 8, sh - 6, 0xE610141D); g.drawBorder(8, barY, sw - 16, barH, 0xFF4F8EF7); }
        if (sel == null) return false;
        if (!click) g.text(sel.label + " (" + String.format("%.2fx", sel.scale) + ")", 16, barY + (twoRow ? 6 : 13), 0xFFFFFFFF, false);

        int cx = twoRow ? 14 : Math.max(140, sw - 420), by = twoRow ? barY + 26 : barY + 7, gap = 4;
        if (btn(g, cx, by, 44, 20, sel.enabled ? "ON" : "OFF", sel.enabled ? 0xFF216E4E : 0xFF4A252A, mx, my, click)) { cfg.toggleModule(sel); return true; } cx += 48;
        if (btn(g, cx, by, 30, 20, "S-", 0xFF232B3A, mx, my, click)) { sel.scale = Math.max(0.5, sel.scale - 0.1); cfg.save(); return true; } cx += 34;
        if (btn(g, cx, by, 30, 20, "S+", 0xFF232B3A, mx, my, click)) { sel.scale = Math.min(2.2, sel.scale + 0.1); cfg.save(); return true; } cx += 34;
        if (btn(g, cx, by, 34, 20, "BG", sel.bg ? 0xFF334766 : 0xFF232B3A, mx, my, click)) { sel.bg = !sel.bg; cfg.save(); return true; } cx += 38;
        if (btn(g, cx, by, 48, 20, "Border", sel.border ? 0xFF334766 : 0xFF232B3A, mx, my, click)) { sel.border = !sel.border; cfg.save(); return true; } cx += 52;
        if (btn(g, cx, by, 50, 20, "Chroma", sel.chroma ? 0xFF5D3AA6 : 0xFF232B3A, mx, my, click)) { sel.chroma = !sel.chroma; cfg.save(); return true; } cx += 54;
        if (btn(g, cx, by, 46, 20, "Reset", 0xFF3C2B2B, mx, my, click)) { cfg.resetLayout(); return true; } cx += 50;
        if (btn(g, cx, by, 72, 20, "Back", 0xFF2D6CDF, mx, my, click)) { cfg.save(); if (actions != null) actions.backToMenu(); return true; }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int mx, int my, boolean click) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            g.fill(x, y, x + w, y + h, hover ? ClientState.brightenArgb(bg, 22) : bg);
            g.drawBorder(x, y, w, h, 0x55FFFFFF);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        }
        return hover && click;
    }
}