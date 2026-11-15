package dev.aetheria.client.common;

import java.util.ArrayList;
import java.util.List;

public class SharedHudEditor {
    public interface Actions {
        void backToMenu();
        void openModSettings(String modId);
    }

    private String selectedId = "fps";
    private String draggingId = null;
    private String resizingId = null;
    private double dragOffsetX = 0, dragOffsetY = 0;
    private double resizeStartX = 0, resizeStartY = 0, resizeStartScale = 1.0, resizeOrigW = 60.0, resizeOrigH = 20.0;

    public void render(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, int mx, int my) {
        int accent = ClientState.getAccentColor();
        g.fill(0, 0, sw, sh, 0x44000000);

        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            SharedHudRenderer.renderModule(g, d, m, sw, sh, true, true);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            boolean sel = m.id.equals(selectedId);
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

            if (sel) {
                g.drawBorder(bx - 2, by - 2, bw + 4, bh + 4, accent);
                int hx = bx + bw - 3, hy = by + bh - 3;
                g.fill(hx, hy, hx + 7, hy + 7, accent);
                g.drawBorder(hx, hy, 7, 7, 0xFF09090B);
            } else if (hov) {
                g.drawBorder(bx - 1, by - 1, bw + 2, bh + 2, ClientState.UI_BORDER_HOV);
            } else if (!m.enabled) {
                g.drawBorder(bx, by, bw, bh, 0x66EF4444);
            }
        }

        ClientConfig.HudModule active = ClientConfig.get().hudModules.get(resizingId != null ? resizingId : (draggingId != null ? draggingId : selectedId));
        if (active != null) {
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, active, sw, sh);
            String tag = String.format("%s  %.2fx  (%d%%, %d%%)", active.label, Double.valueOf(active.scale), Integer.valueOf((int) Math.round(active.x * 100)), Integer.valueOf((int) Math.round(active.y * 100)));
            int tw = g.textWidth(tag) + 10;
            int ty = b[1] > 20 ? b[1] - 17 : b[1] + b[3] + 5;
            int tx = Math.max(8, Math.min(sw - tw - 8, b[0]));
            g.fill(tx, ty, tx + tw, ty + 14, ClientState.UI_PANEL);
            g.drawBorder(tx, ty, tw, 14, ClientState.UI_BORDER_HOV);
            g.text(tag, tx + 5, ty + 3, ClientState.UI_TEXT, false);
        }

        drawOrHandleBar(g, sw, sh, mx, my, false, null, accent);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int button, Actions actions) {
        int barH = sw < 520 ? 54 : 32;
        if (button == 0 && my >= sh - barH - 8) {
            return drawOrHandleBar(g, sw, sh, (int) mx, (int) my, true, actions, ClientState.getAccentColor());
        }

        ClientConfig cfg = ClientConfig.get();
        if (button == 0 && selectedId != null) {
            ClientConfig.HudModule sel = cfg.hudModules.get(selectedId);
            if (sel != null) {
                int[] b = SharedHudRenderer.getModulePixelBounds(g, d, sel, sw, sh);
                int hx = b[0] + b[2] - 5, hy = b[1] + b[3] - 5;
                if (mx >= hx && mx <= hx + 12 && my >= hy && my <= hy + 12) {
                    resizingId = sel.id;
                    resizeStartX = mx;
                    resizeStartY = my;
                    resizeStartScale = sel.scale;
                    resizeOrigW = Math.max(20.0, b[2] / Math.max(0.5, sel.scale));
                    resizeOrigH = Math.max(12.0, b[3] / Math.max(0.5, sel.scale));
                    return true;
                }
            }
        }

        List<ClientConfig.HudModule> list = new ArrayList<ClientConfig.HudModule>(cfg.hudModules.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientConfig.HudModule m = list.get(i);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            if (mx >= b[0] - 2 && mx <= b[0] + b[2] + 4 && my >= b[1] - 2 && my <= b[1] + b[3] + 4) {
                selectedId = m.id;
                if (button == 1 && actions != null) {
                    actions.openModSettings(m.id);
                    return true;
                }
                if (button == 0) {
                    draggingId = m.id;
                    dragOffsetX = mx - b[0];
                    dragOffsetY = my - b[1];
                    return true;
                }
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
            double scaleFromW = resizeStartScale + dx / resizeOrigW;
            double scaleFromH = resizeStartScale + dy / resizeOrigH;
            double nextScale = Math.abs(dx) >= Math.abs(dy) ? scaleFromW : scaleFromH;
            rm.scale = Math.max(0.5, Math.min(2.5, Math.round(nextScale * 20.0) / 20.0));
            return true;
        }
        if (draggingId != null) {
            ClientConfig.HudModule dm = cfg.hudModules.get(draggingId);
            if (dm == null) return false;
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, dm, sw, sh);
            double maxX = Math.max(0.0, (double) (sw - b[2]) / Math.max(1.0, sw));
            double maxY = Math.max(0.0, (double) (sh - (sw < 520 ? 60 : 40) - b[3]) / Math.max(1.0, sh));
            double nx = (mx - dragOffsetX) / Math.max(1.0, sw);
            double ny = (my - dragOffsetY) / Math.max(1.0, sh);
            if (cfg.getBool("hud_snap_grid", false)) {
                nx = Math.round(nx * 40.0) / 40.0;
                ny = Math.round(ny * 40.0) / 40.0;
            }
            dm.x = Math.max(0.0, Math.min(maxX, nx));
            dm.y = Math.max(0.0, Math.min(maxY, ny));
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (draggingId != null || resizingId != null) {
            draggingId = null;
            resizingId = null;
            ClientConfig.get().save();
            return true;
        }
        return false;
    }

    private boolean drawOrHandleBar(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions, int accent) {
        ClientConfig cfg = ClientConfig.get();
        ClientConfig.HudModule sel = cfg.hudModules.get(selectedId);
        boolean twoRow = sw < 520;
        int barH = twoRow ? 54 : 32;
        int barY = sh - barH - 6;

        if (!click) {
            g.fill(8, barY, sw - 8, sh - 6, ClientState.UI_PANEL);
            g.drawBorder(8, barY, sw - 16, barH, ClientState.UI_BORDER_HOV);
        }
        if (sel == null) return false;
        if (!click) {
            g.text(sel.label + "  |  Drag corner to resize  |  Right-click for Settings", 16, barY + (twoRow ? 6 : 12), ClientState.UI_TEXT_MUTED, false);
        }

        int cx = twoRow ? 14 : Math.max(260, sw - 266);
        int by = twoRow ? barY + 24 : barY + 6;
        if (btn(g, cx, by, 44, 20, sel.enabled ? "ON" : "OFF", sel.enabled ? 0xFF10B981 : 0xFF3A1A1E, sel.enabled ? 0xFF09090B : ClientState.UI_TEXT, mx, my, click)) {
            cfg.toggleModule(sel); return true;
        }
        if (btn(g, cx + 48, by, 68, 20, "Settings", ClientState.UI_CARD, ClientState.UI_TEXT, mx, my, click)) {
            if (actions != null) actions.openModSettings(sel.id); return true;
        }
        if (btn(g, cx + 120, by, 54, 20, "Reset", ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, mx, my, click)) {
            cfg.resetLayout(); return true;
        }
        if (btn(g, cx + 178, by, 72, 20, "Done", accent, ClientState.getAccentFg(), mx, my, click)) {
            cfg.save(); if (actions != null) actions.backToMenu(); return true;
        }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            g.fill(x, y, x + w, y + h, hov ? ClientState.brightenArgb(bg, 18) : bg);
            g.drawBorder(x, y, w, h, ClientState.UI_BORDER_HOV);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        return hov && click;
    }
}
