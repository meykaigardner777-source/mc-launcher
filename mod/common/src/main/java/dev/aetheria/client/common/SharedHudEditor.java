package dev.aetheria.client.common;

import java.util.*;

public class SharedHudEditor {
    public interface Actions {
        void backToMenu();
        void openModSettings(String modId);
    }

    private final Set<String> selectedIds = new LinkedHashSet<String>();
    private final Deque<Map<String, double[]>> undoStack = new ArrayDeque<Map<String, double[]>>();
    private final Deque<Map<String, double[]>> redoStack = new ArrayDeque<Map<String, double[]>>();

    private String primaryId = "fps", draggingId = null, resizingId = null;
    private boolean shiftHeld = false;
    private double dragStartMx = 0, dragStartMy = 0;
    private final Map<String, double[]> dragOrigXY = new LinkedHashMap<String, double[]>();
    private double resizeStartX = 0, resizeStartY = 0, resizeStartScale = 1.0, resizeOrigW = 60.0, resizeOrigH = 20.0;
    private Integer guideX = null, guideY = null;

    public void setShiftHeld(boolean down) { this.shiftHeld = down; }

    public void render(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, int mx, int my) {
        int accent = ClientState.getAccentColor();
        g.fill(0, 0, sw, sh, 0x33000000);

        if (ClientConfig.get().getBool("hud_snap_grid", true)) {
            int stepX = Math.max(16, sw / 40), stepY = Math.max(16, sh / 40);
            for (int gx = stepX; gx < sw; gx += stepX) g.fill(gx, 0, gx + 1, sh, 0x0CFFFFFF);
            for (int gy = stepY; gy < sh; gy += stepY) g.fill(0, gy, sw, gy + 1, 0x0CFFFFFF);
        }

        if (guideX != null) g.fill(guideX.intValue(), 0, guideX.intValue() + 1, sh, ClientState.withAlpha(accent, 170));
        if (guideY != null) g.fill(0, guideY.intValue(), sw, guideY.intValue() + 1, ClientState.withAlpha(accent, 170));

        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            if (!m.enabled) continue;
            SharedHudRenderer.renderModule(g, d, m, sw, sh, true, true);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            int rad = Math.max(0, Math.min(30, m.borderRadius));
            boolean sel = m.id.equals(primaryId) || selectedIds.contains(m.id);
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

            if (sel) {
                g.drawRoundedOutline(bx - 2, by - 2, bw + 4, bh + 4, rad + 2, accent);
                if (m.id.equals(primaryId)) {
                    g.drawCircleFilled(bx + bw + 1, by + bh + 1, 5, 0xFF09090B);
                    g.drawCircleFilled(bx + bw + 1, by + bh + 1, 4, accent);
                }
            } else if (hov) {
                g.drawRoundedOutline(bx - 1, by - 1, bw + 2, bh + 2, rad + 1, ClientState.UI_BORDER_HOV);
            }
        }

        ClientConfig.HudModule active = ClientConfig.get().hudModules.get(resizingId != null ? resizingId : (draggingId != null ? draggingId : primaryId));
        if (active != null && active.enabled) {
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, active, sw, sh);
            String tag = String.format("%s  %.2fx  (%d%%,%d%%) [%s]", active.label, Double.valueOf(active.scale), Integer.valueOf((int) Math.round(active.x * 100)), Integer.valueOf((int) Math.round(active.y * 100)), active.anchor);
            int tw = g.textWidth(tag) + 14;
            int ty = b[1] > 24 ? b[1] - 20 : b[1] + b[3] + 6;
            int tx = Math.max(8, Math.min(sw - tw - 8, b[0]));
            g.drawGlassCard(tx, ty, tw, 16, 6, ClientState.UI_PANEL, ClientState.UI_BORDER_HOV);
            g.text(tag, tx + 7, ty + 4, ClientState.UI_TEXT, false);
        }

        drawOrHandleDock(g, sw, sh, mx, my, false, null, accent);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int button, Actions actions) {
        int dockW = Math.min(540, sw - 16), dockX = (sw - dockW) / 2, dockH = 32, dockY = sh - dockH - 8;
        if (button == 0 && mx >= dockX && mx <= dockX + dockW && my >= dockY && my <= dockY + dockH) {
            return drawOrHandleDock(g, sw, sh, (int) mx, (int) my, true, actions, ClientState.getAccentColor());
        }

        ClientConfig cfg = ClientConfig.get();
        if (button == 0 && primaryId != null) {
            ClientConfig.HudModule sel = cfg.hudModules.get(primaryId);
            if (sel != null && sel.enabled) {
                int[] b = SharedHudRenderer.getModulePixelBounds(g, d, sel, sw, sh);
                int hx = b[0] + b[2] + 1, hy = b[1] + b[3] + 1;
                if (Math.hypot(mx - hx, my - hy) <= 9.0) {
                    pushUndo();
                    resizingId = sel.id; resizeStartX = mx; resizeStartY = my; resizeStartScale = sel.scale;
                    resizeOrigW = Math.max(20.0, b[2] / Math.max(0.25, sel.scale));
                    resizeOrigH = Math.max(12.0, b[3] / Math.max(0.25, sel.scale));
                    return true;
                }
            }
        }

        List<ClientConfig.HudModule> list = new ArrayList<ClientConfig.HudModule>(cfg.hudModules.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientConfig.HudModule m = list.get(i);
            if (!m.enabled) continue;
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            if (mx >= b[0] - 2 && mx <= b[0] + b[2] + 4 && my >= b[1] - 2 && my <= b[1] + b[3] + 4) {
                if (button == 1 && actions != null) { primaryId = m.id; actions.openModSettings(m.id); return true; }
                if (button == 0) {
                    if (shiftHeld) {
                        if (selectedIds.contains(m.id)) selectedIds.remove(m.id);
                        else selectedIds.add(m.id);
                        primaryId = m.id;
                        return true;
                    }
                    if (!selectedIds.contains(m.id)) {
                        selectedIds.clear();
                        selectedIds.add(m.id);
                    }
                    primaryId = m.id;
                    pushUndo();
                    draggingId = m.id;
                    dragStartMx = mx; dragStartMy = my;
                    dragOrigXY.clear();
                    for (String sid : selectedIds) {
                        ClientConfig.HudModule sm = cfg.hudModules.get(sid);
                        if (sm != null) dragOrigXY.put(sid, new double[]{sm.x, sm.y});
                    }
                    return true;
                }
            }
        }
        if (button == 0 && !shiftHeld) { selectedIds.clear(); }
        return false;
    }

    public boolean mouseDragged(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my) {
        ClientConfig cfg = ClientConfig.get();
        if (resizingId != null) {
            ClientConfig.HudModule rm = cfg.hudModules.get(resizingId);
            if (rm == null) return false;
            double dx = mx - resizeStartX, dy = my - resizeStartY;
            double nextScale = Math.abs(dx) >= Math.abs(dy) ? (resizeStartScale + dx / resizeOrigW) : (resizeStartScale + dy / resizeOrigH);
            rm.scale = Math.max(0.25, Math.min(4.0, Math.round(nextScale * 20.0) / 20.0));
            return true;
        }
        if (draggingId != null) {
            double dxNorm = (mx - dragStartMx) / Math.max(1.0, sw);
            double dyNorm = (my - dragStartMy) / Math.max(1.0, sh);
            guideX = null; guideY = null;

            for (Map.Entry<String, double[]> ent : dragOrigXY.entrySet()) {
                ClientConfig.HudModule dm = cfg.hudModules.get(ent.getKey());
                if (dm == null) continue;
                double nx = ent.getValue()[0] + dxNorm;
                double ny = ent.getValue()[1] + dyNorm;
                if (cfg.getBool("hud_snap_grid", true)) {
                    nx = Math.round(nx * 40.0) / 40.0;
                    ny = Math.round(ny * 40.0) / 40.0;
                }
                dm.x = Math.max(0.0, Math.min(1.0, nx));
                dm.y = Math.max(0.0, Math.min(1.0, ny));

                if (ent.getKey().equals(draggingId) && cfg.getBool("hud_align_guides", true)) {
                    int[] b = SharedHudRenderer.getModulePixelBounds(g, d, dm, sw, sh);
                    int cx = b[0] + b[2] / 2, cy = b[1] + b[3] / 2;
                    if (Math.abs(cx - sw / 2) <= 5) {
                        guideX = Integer.valueOf(sw / 2);
                        dm.x = Math.max(0.0, Math.min(1.0, dm.x + (sw / 2.0 - cx) / sw));
                    }
                    if (Math.abs(cy - sh / 2) <= 5) {
                        guideY = Integer.valueOf(sh / 2);
                        dm.y = Math.max(0.0, Math.min(1.0, dm.y + (sh / 2.0 - cy) / sh));
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        guideX = null; guideY = null;
        if (draggingId != null || resizingId != null) { draggingId = null; resizingId = null; ClientConfig.get().save(); return true; }
        return false;
    }

    private void pushUndo() {
        Map<String, double[]> snap = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            snap.put(m.id, new double[]{m.x, m.y, m.scale});
        }
        undoStack.push(snap);
        while (undoStack.size() > 25) undoStack.removeLast();
        redoStack.clear();
    }

    private void applySnapshot(Map<String, double[]> snap) {
        ClientConfig cfg = ClientConfig.get();
        for (Map.Entry<String, double[]> e : snap.entrySet()) {
            ClientConfig.HudModule m = cfg.hudModules.get(e.getKey());
            if (m != null) { m.x = e.getValue()[0]; m.y = e.getValue()[1]; m.scale = e.getValue()[2]; }
        }
        cfg.save();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        Map<String, double[]> cur = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) cur.put(m.id, new double[]{m.x, m.y, m.scale});
        redoStack.push(cur);
        applySnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        Map<String, double[]> cur = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) cur.put(m.id, new double[]{m.x, m.y, m.scale});
        undoStack.push(cur);
        applySnapshot(redoStack.pop());
    }

    private boolean drawOrHandleDock(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions, int accent) {
        ClientConfig cfg = ClientConfig.get();
        ClientConfig.HudModule sel = cfg.hudModules.get(primaryId);
        int dockW = Math.min(540, sw - 16), dockX = (sw - dockW) / 2, dockH = 32, dockY = sh - dockH - 8;

        if (!click) {
            g.drawSoftShadow(dockX, dockY, dockW, dockH, 10);
            g.drawGlassCard(dockX, dockY, dockW, dockH, 10, ClientState.UI_PANEL, ClientState.UI_BORDER_HOV);
            String title = selectedIds.size() > 1 ? (selectedIds.size() + " Selected") : (sel != null && sel.enabled ? sel.label : "Shift+Click multi-select");
            g.text(title, dockX + 12, dockY + 12, ClientState.UI_TEXT, true);
        }

        int by = dockY + 6;
        int rx = dockX + dockW - 8;
        if (btn(g, rx - 52, by, 52, 20, "Done", accent, ClientState.getAccentFg(), mx, my, click)) {
            cfg.save(); if (actions != null) actions.backToMenu(); return true;
        }
        rx -= 56;
        if (btn(g, rx - 66, by, 66, 20, "Prof:" + cfg.activeProfile, ClientState.UI_CARD, ClientState.UI_TEXT, mx, my, click)) {
            cfg.cycleProfilePreset(); return true;
        }
        rx -= 70;
        if (sel != null && sel.enabled) {
            if (btn(g, rx - 58, by, 58, 20, "Config", ClientState.UI_CARD, ClientState.UI_TEXT, mx, my, click)) {
                if (actions != null) actions.openModSettings(sel.id); return true;
            }
            rx -= 62;
            if (btn(g, rx - 44, by, 44, 20, "Reset", ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, mx, my, click)) {
                pushUndo(); cfg.resetModule(sel.id); return true;
            }
            rx -= 48;
        }
        if (btn(g, rx - 38, by, 38, 20, "Redo", ClientState.UI_CARD, redoStack.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, mx, my, click)) {
            redo(); return true;
        }
        rx -= 42;
        if (btn(g, rx - 38, by, 38, 20, "Undo", ClientState.UI_CARD, undoStack.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, mx, my, click)) {
            undo(); return true;
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