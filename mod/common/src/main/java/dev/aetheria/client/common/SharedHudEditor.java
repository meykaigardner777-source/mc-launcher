package dev.aetheria.client.common;

import java.util.*;

public class SharedHudEditor {
    public interface Actions {
        void backToMenu();
        void openModSettings(String modId);
    }

    private String selectedId = "fps", draggingId = null, resizingId = null;
    private double dragOffsetX = 0, dragOffsetY = 0;
    private double resizeStartX = 0, resizeStartY = 0, resizeStartScale = 1.0, resizeOrigW = 60.0, resizeOrigH = 20.0;

    public void render(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, int mx, int my) {
        int accent = ClientState.getAccentColor();
        g.fill(0, 0, sw, sh, 0x33000000);

        // ONLY render active/enabled HUD modules
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            if (!m.enabled) continue;
            SharedHudRenderer.renderModule(g, d, m, sw, sh, true, true);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            int rad = Math.max(0, Math.min(12, m.borderRadius));
            boolean sel = m.id.equals(selectedId), hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

            if (sel) {
                g.drawRoundedOutline(bx - 2, by - 2, bw + 4, bh + 4, rad + 2, accent);
                g.drawCircleFilled(bx + bw + 1, by + bh + 1, 5, 0xFF09090B);
                g.drawCircleFilled(bx + bw + 1, by + bh + 1, 4, accent);
            } else if (hov) {
                g.drawRoundedOutline(bx - 1, by - 1, bw + 2, bh + 2, rad + 1, ClientState.UI_BORDER_HOV);
            }
        }

        ClientConfig.HudModule active = ClientConfig.get().hudModules.get(resizingId != null ? resizingId : (draggingId != null ? draggingId : selectedId));
        if (active != null && active.enabled) {
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, active, sw, sh);
            String tag = String.format("%s  %.2fx  (%d%%, %d%%)", active.label, Double.valueOf(active.scale), Integer.valueOf((int) Math.round(active.x * 100)), Integer.valueOf((int) Math.round(active.y * 100)));
            int tw = g.textWidth(tag) + 14;
            int ty = b[1] > 24 ? b[1] - 20 : b[1] + b[3] + 6;
            int tx = Math.max(8, Math.min(sw - tw - 8, b[0]));
            g.drawGlassCard(tx, ty, tw, 16, 6, ClientState.UI_PANEL, ClientState.UI_BORDER_HOV);
            g.text(tag, tx + 7, ty + 4, ClientState.UI_TEXT, false);
        }

        drawOrHandleDock(g, sw, sh, mx, my, false, null, accent);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int button, Actions actions) {
        int dockW = Math.min(440, sw - 20), dockX = (sw - dockW) / 2, dockH = 32, dockY = sh - dockH - 8;
        if (button == 0 && mx >= dockX && mx <= dockX + dockW && my >= dockY && my <= dockY + dockH) {
            return drawOrHandleDock(g, sw, sh, (int) mx, (int) my, true, actions, ClientState.getAccentColor());
        }

        ClientConfig cfg = ClientConfig.get();
        if (button == 0 && selectedId != null) {
            ClientConfig.HudModule sel = cfg.hudModules.get(selectedId);
            if (sel != null && sel.enabled) {
                int[] b = SharedHudRenderer.getModulePixelBounds(g, d, sel, sw, sh);
                int hx = b[0] + b[2] + 1, hy = b[1] + b[3] + 1;
                if (Math.hypot(mx - hx, my - hy) <= 9.0) {
                    resizingId = sel.id; resizeStartX = mx; resizeStartY = my; resizeStartScale = sel.scale;
                    resizeOrigW = Math.max(20.0, b[2] / Math.max(0.5, sel.scale)); resizeOrigH = Math.max(12.0, b[3] / Math.max(0.5, sel.scale));
                    return true;
                }
            }
        }

        List<ClientConfig.HudModule> list = new ArrayList<ClientConfig.HudModule>(cfg.hudModules.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientConfig.HudModule m = list.get(i);
            if (!m.enabled) continue; // Only pick enabled modules
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            if (mx >= b[0] - 2 && mx <= b[0] + b[2] + 4 && my >= b[1] - 2 && my <= b[1] + b[3] + 4) {
                selectedId = m.id;
                if (button == 1 && actions != null) { actions.openModSettings(m.id); return true; }
                if (button == 0) { draggingId = m.id; dragOffsetX = mx - b[0]; dragOffsetY = my - b[1]; return true; }
            }
        }
        return false;
    }

    public boolean mouseDragged(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my) {
        ClientConfig cfg = ClientConfig.get();
        if (resizingId != null) {
            ClientConfig.HudModule rm = cfg.hudModules.get(resizingId);
            if (rm == null) return false;
            double dx = mx - resizeStartX, dy = my - resizeStartY;
            double nextScale = Math.abs(dx) >= Math.abs(dy) ? (resizeStartScale + dx / resizeOrigW) : (resizeStartScale + dy / resizeOrigH);
            rm.scale = Math.max(0.5, Math.min(2.5, Math.round(nextScale * 20.0) / 20.0));
            return true;
        }
        if (draggingId != null) {
            ClientConfig.HudModule dm = cfg.hudModules.get(draggingId);
            if (dm == null) return false;
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, dm, sw, sh);
            // Full screen coverage down to the very bottom edge!
            double maxX = Math.max(0.0, (double) (sw - b[2]) / Math.max(1.0, sw));
            double maxY = Math.max(0.0, (double) (sh - b[3]) / Math.max(1.0, sh));
            double nx = (mx - dragOffsetX) / Math.max(1.0, sw), ny = (my - dragOffsetY) / Math.max(1.0, sh);
            if (cfg.getBool("hud_snap_grid", false)) { nx = Math.round(nx * 40.0) / 40.0; ny = Math.round(ny * 40.0) / 40.0; }
            dm.x = Math.max(0.0, Math.min(maxX, nx)); dm.y = Math.max(0.0, Math.min(maxY, ny));
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (draggingId != null || resizingId != null) { draggingId = null; resizingId = null; ClientConfig.get().save(); return true; }
        return false;
    }

    private boolean drawOrHandleDock(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions, int accent) {
        ClientConfig cfg = ClientConfig.get(); ClientConfig.HudModule sel = cfg.hudModules.get(selectedId);
        int dockW = Math.min(440, sw - 20), dockX = (sw - dockW) / 2, dockH = 32, dockY = sh - dockH - 8;

        if (!click) {
            g.drawSoftShadow(dockX, dockY, dockW, dockH, 10);
            g.drawGlassCard(dockX, dockY, dockW, dockH, 10, ClientState.UI_PANEL, ClientState.UI_BORDER_HOV);
        }
        if (sel == null || !sel.enabled) {
            if (!click) g.text("Click any active HUD to move or drag corner to resize", dockX + 14, dockY + 12, ClientState.UI_TEXT_MUTED, false);
            int doneX = dockX + dockW - 64, doneY = dockY + 6;
            if (btn(g, doneX, doneY, 56, 20, "Done", accent, ClientState.getAccentFg(), mx, my, click)) {
                cfg.save(); if (actions != null) actions.backToMenu(); return true;
            }
            return false;
        }
        if (!click) g.text(sel.label, dockX + 14, dockY + 12, ClientState.UI_TEXT, true);

        int bx = dockX + dockW - 200, by = dockY + 6;
        if (btn(g, bx, by, 66, 20, "Settings", ClientState.UI_CARD, ClientState.UI_TEXT, mx, my, click)) {
            if (actions != null) actions.openModSettings(sel.id); return true;
        }
        if (btn(g, bx + 70, by, 50, 20, "Reset", ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, mx, my, click)) {
            cfg.resetModule(sel.id); return true;
        }
        if (btn(g, bx + 124, by, 68, 20, "Done", accent, ClientState.getAccentFg(), mx, my, click)) {
            cfg.save(); if (actions != null) actions.backToMenu(); return true;
        }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            g.drawGlassCard(x, y, w, h, 6, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER_HOV);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        return hov && click;
    }
}