package dev.aetheria.client.common;

import java.util.*;

public class SharedModMenu {
    private int scrollRow = 0;

    public interface Actions { void openHudEditor(); void closeMenu(); }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) { handleInteraction(g, sw, sh, mx, my, false, null); }
    public boolean mouseClicked(UiBridge.UiDraw g, int sw, int sh, double mx, double my, Actions actions) { return handleInteraction(g, sw, sh, (int) mx, (int) my, true, actions); }

    public boolean mouseScrolled(int sw, int sh, double scrollY) {
        int panelH = Math.min(340, sh - 30), rowsVisible = Math.max(2, (panelH - 116) / 34);
        int maxScroll = Math.max(0, (int) Math.ceil(ClientConfig.get().hudModules.size() / 2.0) - rowsVisible);
        if (scrollY < 0) scrollRow = Math.min(maxScroll, scrollRow + 1);
        else if (scrollY > 0) scrollRow = Math.max(0, scrollRow - 1);
        return true;
    }

    private boolean handleInteraction(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions) {
        ClientConfig cfg = ClientConfig.get();
        int panelW = Math.min(540, sw - 30), panelH = Math.min(340, sh - 30), px = (sw - panelW) / 2, py = (sh - panelH) / 2;

        if (!click) {
            g.fill(0, 0, sw, sh, 0x77000000); g.fill(px, py, px + panelW, py + panelH, 0xEA10141D);
            g.drawBorder(px, py, panelW, panelH, 0xFF4F8EF7); g.text("* AETHERIA CLIENT", px + 14, py + 12, 0xFFFFFFFF, true);
        }
        if (btn(g, px + panelW - 224, py + 8, 108, 20, "Edit HUD Layout", 0xFF2D6CDF, mx, my, click)) { if (actions != null) actions.openHudEditor(); return true; }
        if (btn(g, px + panelW - 110, py + 8, 96, 20, "Save & Close", 0xFF263042, mx, my, click)) { cfg.save(); if (actions != null) actions.closeMenu(); return true; }

        List<ClientConfig.HudModule> mods = new ArrayList<ClientConfig.HudModule>(cfg.hudModules.values());
        int cols = 2, rowsVisible = Math.max(2, (panelH - 116) / 34);
        int maxScroll = Math.max(0, (int) Math.ceil(mods.size() / (double) cols) - rowsVisible);
        if (scrollRow > maxScroll) scrollRow = maxScroll;

        if (btn(g, px + 14, py + 34, 54, 18, "Up", 0xFF1F2735, mx, my, click)) { scrollRow = Math.max(0, scrollRow - 1); return true; }
        if (btn(g, px + 72, py + 34, 54, 18, "Down", 0xFF1F2735, mx, my, click)) { scrollRow = Math.min(maxScroll, scrollRow + 1); return true; }

        int zoomFac = (int) Math.round(cfg.getDouble("cam_zoom_fac", 4));
        if (btn(g, px + 134, py + 34, 96, 18, "Zoom: " + zoomFac + "x", 0xFF1F2735, mx, my, click)) { cfg.setOption("cam_zoom_fac", zoomFac >= 12 ? 2 : zoomFac + 1); return true; }

        String xh = cfg.getString("pvp_xhair_style", "classic");
        if (btn(g, px + 234, py + 34, 134, 18, "Crosshair: " + xh, 0xFF1F2735, mx, my, click)) {
            String[] styles = {"vanilla", "classic", "dot", "circle", "t_shape"};
            int next = 0; for (int i = 0; i < styles.length; i++) if (styles[i].equalsIgnoreCase(xh)) next = (i + 1) % styles.length;
            cfg.setOption("pvp_xhair_style", styles[next]); return true;
        }
        boolean lowFire = cfg.getBool("pvp_low_fire", true);
        if (btn(g, px + 372, py + 34, Math.max(80, panelW - 386), 18, "Low Fire: " + (lowFire ? "ON" : "OFF"), lowFire ? 0xFF215E43 : 0xFF2A2F3A, mx, my, click)) {
            cfg.setOption("pvp_low_fire", !lowFire); return true;
        }

        int cardW = (panelW - 38) / cols, startIdx = scrollRow * cols;
        for (int r = 0; r < rowsVisible; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = startIdx + r * cols + c; if (idx >= mods.size()) break;
                ClientConfig.HudModule m = mods.get(idx);
                int cx = px + 14 + c * (cardW + 10), cy = py + 60 + r * 34;
                if (!click) {
                    g.fill(cx, cy, cx + cardW, cy + 30, 0xCC181E2B);
                    g.drawBorder(cx, cy, cardW, 30, m.enabled ? 0xFF4F8EF7 : 0x44FFFFFF);
                    g.text(m.label, cx + 8, cy + 6, 0xFFFFFFFF, false);
                    g.text("Scale " + String.format("%.1fx", m.scale) + " | " + Math.round(m.x * 100) + "%," + Math.round(m.y * 100) + "%", cx + 8, cy + 17, 0xFF9AA5B8, false);
                }
                if (btn(g, cx + cardW - 86, cy + 5, 34, 20, "BG", m.bg ? 0xFF334766 : 0xFF222834, mx, my, click)) { m.bg = !m.bg; cfg.save(); return true; }
                if (btn(g, cx + cardW - 48, cy + 5, 42, 20, m.enabled ? "ON" : "OFF", m.enabled ? 0xFF216E4E : 0xFF3A2428, mx, my, click)) { cfg.toggleModule(m); return true; }
            }
        }
        if (!click) g.text("ESC to close | Scroll or click 'Edit HUD Layout' to drag widgets", px + 14, py + panelH - 16, 0xFF8C97AB, false);
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String label, int bg, int mx, int my, boolean click) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            g.fill(x, y, x + w, y + h, hover ? ClientState.brightenArgb(bg, 22) : bg);
            g.drawBorder(x, y, w, h, hover ? 0xAAFFFFFF : 0x44FFFFFF);
            g.text(label, x + (w - g.textWidth(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        }
        return hover && click;
    }
}