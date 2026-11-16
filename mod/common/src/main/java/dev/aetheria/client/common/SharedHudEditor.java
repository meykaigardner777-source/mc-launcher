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
        g.fill(0, 0, sw, sh, 0x40000000);

        ClientConfig cfg = ClientConfig.get();
        if (cfg.getBool("hud_snap_grid", true)) {
            int stepX = Math.max(16, sw / 40), stepY = Math.max(16, sh / 40);
            for (int gx = stepX; gx < sw; gx += stepX) g.fill(gx, 0, gx + 1, sh, 0x0AFFFFFF);
            for (int gy = stepY; gy < sh; gy += stepY) g.fill(0, gy, sw, gy + 1, 0x0AFFFFFF);
        }

        if (guideX != null) g.fill(guideX.intValue(), 0, guideX.intValue() + 1, sh, ClientState.withAlpha(accent, 170));
        if (guideY != null) g.fill(0, guideY.intValue(), sw, guideY.intValue() + 1, ClientState.withAlpha(accent, 170));

        int globalRad = (int) Math.round(cfg.getDouble("global_hud_rounding", -1.0));

        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (!m.enabled) continue;
            SharedHudRenderer.renderModule(g, d, m, sw, sh, true, true);
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            int rad = globalRad >= 0 ? globalRad : Math.max(0, Math.min(30, m.borderRadius));
            boolean sel = m.id.equals(primaryId) || selectedIds.contains(m.id);
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

            if (sel) {
                g.drawRoundedOutline(bx - 2, by - 2, bw + 4, bh + 4, rad + 2, accent);
                if (m.id.equals(primaryId)) {
                    // Resize corner circle handle
                    g.drawCircleFilled(bx + bw + 1, by + bh + 1, 5, 0xFF09090B);
                    g.drawCircleFilled(bx + bw + 1, by + bh + 1, 4, accent);

                    // Clean circular Delete "X" button on top-right
                    int closeX = bx + bw - 3, closeY = by - 5;
                    boolean closeHov = Math.hypot(mx - closeX, my - closeY) <= 7.0;
                    g.drawCircleFilled(closeX, closeY, 6, closeHov ? 0xFFEF4444 : 0xDD181820);
                    g.drawCircle(closeX, closeY, 6, 1, closeHov ? 0xFFFFFFFF : 0xFF52525B);
                    g.text("x", closeX - 2, closeY - 4, 0xFFFFFFFF, false);
                }
            } else if (hov) {
                g.drawRoundedOutline(bx - 1, by - 1, bw + 2, bh + 2, rad + 1, ClientState.UI_BORDER_HOV);
            }
        }

        ClientConfig.HudModule active = cfg.hudModules.get(resizingId != null ? resizingId : (draggingId != null ? draggingId : primaryId));
        if (active != null && active.enabled) {
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, active, sw, sh);
            String tag = String.format("%s  %.2fx  (%d%%,%d%%)", active.label, Double.valueOf(active.scale), Integer.valueOf((int) Math.round(active.x * 100)), Integer.valueOf((int) Math.round(active.y * 100)));
            int tw = g.textWidth(tag) + 16;
            int ty = b[1] > 24 ? b[1] - 22 : b[1] + b[3] + 6;
            int tx = Math.max(8, Math.min(sw - tw - 8, b[0]));
            g.drawGlassCard(tx, ty, tw, 18, 9, 0xF0101015, ClientState.UI_BORDER);
            g.text(tag, tx + 8, ty + 5, ClientState.UI_TEXT, false);
        }

        drawOrHandleDock(g, sw, sh, mx, my, false, null, accent);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int button, Actions actions) {
        int dockW = Math.min(540, sw - 16), dockX = (sw - dockW) / 2, dockH = 34, dockY = sh - dockH - 8;
        if (button == 0 && mx >= dockX && mx <= dockX + dockW && my >= dockY && my <= dockY + dockH) {
            return drawOrHandleDock(g, sw, sh, (int) mx, (int) my, true, actions, ClientState.getAccentColor());
        }

        ClientConfig cfg = ClientConfig.get();
        if (button == 0 && primaryId != null) {
            ClientConfig.HudModule sel = cfg.hudModules.get(primaryId);
            if (sel != null && sel.enabled) {
                int[] b = SharedHudRenderer.getModulePixelBounds(g, d, sel, sw, sh);
                // Click Delete 'x' Button
                int closeX = b[0] + b[2] - 3, closeY = b[1] - 5;
                if (Math.hypot(mx - closeX, my - closeY) <= 8.0) {
                    pushUndo();
                    sel.enabled = false;
                    if (sel.optId != null) cfg.setOption(sel.optId, Boolean.FALSE);
                    cfg.save();
                    selectedIds.remove(sel.id);
                    primaryId = null;
                    return true;
                }
                // Click Resize Handle
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
            snap.put(m.id, new double[]{m.x, m.y, m.scale, m.enabled ? 1.0 : 0.0});
        }
        undoStack.push(snap);
        while (undoStack.size() > 25) undoStack.removeLast();
        redoStack.clear();
    }

    private void applySnapshot(Map<String, double[]> snap) {
        ClientConfig cfg = ClientConfig.get();
        for (Map.Entry<String, double[]> e : snap.entrySet()) {
            ClientConfig.HudModule m = cfg.hudModules.get(e.getKey());
            if (m != null) {
                m.x = e.getValue()[0];
                m.y = e.getValue()[1];
                m.scale = e.getValue()[2];
                if (e.getValue().length > 3) m.enabled = (e.getValue()[3] > 0.5);
            }
        }
        cfg.save();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        Map<String, double[]> cur = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            cur.put(m.id, new double[]{m.x, m.y, m.scale, m.enabled ? 1.0 : 0.0});
        }
        redoStack.push(cur);
        applySnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        Map<String, double[]> cur = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            cur.put(m.id, new double[]{m.x, m.y, m.scale, m.enabled ? 1.0 : 0.0});
        }
        undoStack.push(cur);
        applySnapshot(redoStack.pop());
    }

    private boolean drawOrHandleDock(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions, int accent) {
        ClientConfig cfg = ClientConfig.get();
        ClientConfig.HudModule sel = cfg.hudModules.get(primaryId);
        int dockW = Math.min(540, sw - 16), dockX = (sw - dockW) / 2, dockH = 34, dockY = sh - dockH - 8;

        if (!click) {
            g.drawGlassCard(dockX, dockY, dockW, dockH, 12, 0xF0101015, ClientState.UI_BORDER);
            String title = selectedIds.size() > 1 ? (selectedIds.size() + " Selected") : (sel != null && sel.enabled ? sel.label : "Click to select | [x] to hide");
            g.text(title, dockX + 14, dockY + 13, ClientState.UI_TEXT, false);
        }

        int by = dockY + 7;
        int rx = dockX + dockW - 8;
        if (btn(g, rx - 54, by, 54, 20, "Done", accent, ClientState.getAccentFg(), mx, my, click)) {
            cfg.save(); if (actions != null) actions.backToMenu(); return true;
        }
        rx -= 58;
        if (btn(g, rx - 68, by, 68, 20, "Prof:" + cfg.activeProfile, 0xFF191924, ClientState.UI_TEXT, mx, my, click)) {
            cfg.cycleProfilePreset(); return true;
        }
        rx -= 72;
        if (sel != null && sel.enabled) {
            if (btn(g, rx - 58, by, 58, 20, "Config", 0xFF191924, ClientState.UI_TEXT, mx, my, click)) {
                if (actions != null) actions.openModSettings(sel.id); return true;
            }
            rx -= 62;
            if (btn(g, rx - 48, by, 48, 20, "Reset", 0xFF191924, ClientState.UI_TEXT_MUTED, mx, my, click)) {
                pushUndo(); cfg.resetModule(sel.id); return true;
            }
            rx -= 52;
        }
        if (btn(g, rx - 40, by, 40, 20, "Redo", 0xFF191924, redoStack.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, mx, my, click)) {
            redo(); return true;
        }
        rx -= 44;
        if (btn(g, rx - 40, by, 40, 20, "Undo", 0xFF191924, undoStack.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, mx, my, click)) {
            undo(); return true;
        }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            g.drawGlassCard(x, y, w, h, 10, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        return hov && click;
    }
}
