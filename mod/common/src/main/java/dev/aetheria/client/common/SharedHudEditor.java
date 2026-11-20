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
        g.fill(0, 0, sw, sh, 0x38000000);

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
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh, true);
            int bx = b[0], by = b[1], bw = b[2], bh = b[3];
            if (bw <= 0 || bh <= 0) continue;
            int rad = globalRad >= 0 ? globalRad : Math.max(0, Math.min(30, m.borderRadius));
            boolean sel = m.id.equals(primaryId) || selectedIds.contains(m.id);
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;

            if (sel) {
                g.drawRoundedOutline(bx - 2, by - 2, bw + 4, bh + 4, rad + 2, 2, "solid", accent, accent);
                if (m.id.equals(primaryId)) {
                    g.drawCircleFilled(bx + bw + 1, by + bh + 1, 5, 0xFF09090B);
                    g.drawCircleFilled(bx + bw + 1, by + bh + 1, 4, accent);

                    int closeX = bx + bw - 3, closeY = by - 5;
                    boolean closeHov = Math.hypot(mx - closeX, my - closeY) <= 7.0;
                    g.drawCircleFilled(closeX, closeY, 6, closeHov ? 0xFFEF4444 : 0xDD181820);
                    g.drawCircle(closeX, closeY, 6, 1, closeHov ? 0xFFFFFFFF : 0xFF52525B);
                    g.text("x", closeX - 2, closeY - 4, 0xFFFFFFFF, false);
                }
            } else if (hov) {
                g.drawRoundedOutline(bx - 1, by - 1, bw + 2, bh + 2, rad + 1, 2, "solid", ClientState.UI_BORDER_HOV, ClientState.UI_BORDER_HOV);
            }
        }

        drawOrHandleTopPill(g, sw, mx, my, false, null, accent);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int button, Actions actions) {
        if (button == 0 && drawOrHandleTopPill(g, sw, (int) mx, (int) my, true, actions, ClientState.getAccentColor())) {
            return true;
        }

        ClientConfig cfg = ClientConfig.get();
        if (button == 0 && primaryId != null) {
            ClientConfig.HudModule sel = cfg.hudModules.get(primaryId);
            if (sel != null && sel.enabled) {
                int[] b = SharedHudRenderer.getModulePixelBounds(g, d, sel, sw, sh, true);
                int closeX = b[0] + b[2] - 3, closeY = b[1] - 5;
                if (Math.hypot(mx - closeX, my - closeY) <= 8.0) {
                    g.playClickSound(); pushUndo();
                    sel.enabled = false;
                    if (sel.optId != null) cfg.setOption(sel.optId, Boolean.FALSE);
                    cfg.save(); selectedIds.remove(sel.id); primaryId = null;
                    return true;
                }
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
            int[] b = SharedHudRenderer.getModulePixelBounds(g, d, m, sw, sh, true);
            if (b[2] <= 0 || b[3] <= 0) continue;
            if (mx >= b[0] - 2 && mx <= b[0] + b[2] + 4 && my >= b[1] - 2 && my <= b[1] + b[3] + 4) {
                if (button == 1 && actions != null) { g.playClickSound(); primaryId = m.id; actions.openModSettings(m.id); return true; }
                if (button == 0) {
                    if (shiftHeld) {
                        g.playClickSound();
                        if (selectedIds.contains(m.id)) selectedIds.remove(m.id); else selectedIds.add(m.id);
                        primaryId = m.id; return true;
                    }
                    if (!selectedIds.contains(m.id)) { selectedIds.clear(); selectedIds.add(m.id); }
                    primaryId = m.id; pushUndo(); draggingId = m.id; dragStartMx = mx; dragStartMy = my;
                    dragOrigXY.clear();
                    for (String sid : selectedIds) {
                        ClientConfig.HudModule sm = cfg.hudModules.get(sid);
                        if (sm != null) dragOrigXY.put(sid, new double[]{sm.x, sm.y});
                    }
                    return true;
                }
            }
        }
        if (button == 0 && !shiftHeld) { selectedIds.clear(); primaryId = null; }
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
                double nx = ent.getValue()[0] + dxNorm, ny = ent.getValue()[1] + dyNorm;
                if (cfg.getBool("hud_snap_grid", true)) { nx = Math.round(nx * 40.0) / 40.0; ny = Math.round(ny * 40.0) / 40.0; }
                dm.x = Math.max(0.0, Math.min(1.0, nx)); dm.y = Math.max(0.0, Math.min(1.0, ny));
                if (ent.getKey().equals(draggingId) && cfg.getBool("hud_align_guides", true)) {
                    int[] b = SharedHudRenderer.getModulePixelBounds(g, d, dm, sw, sh, true);
                    int cx = b[0] + b[2] / 2, cy = b[1] + b[3] / 2;
                    if (Math.abs(cx - sw / 2) <= 5) { guideX = Integer.valueOf(sw / 2); dm.x = Math.max(0.0, Math.min(1.0, dm.x + (sw / 2.0 - cx) / sw)); }
                    if (Math.abs(cy - sh / 2) <= 5) { guideY = Integer.valueOf(sh / 2); dm.y = Math.max(0.0, Math.min(1.0, dm.y + (sh / 2.0 - cy) / sh)); }
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

    public boolean keyPressed(int keyCode, int sw, int sh) {
        if (primaryId == null && selectedIds.isEmpty()) return false;
        int step = shiftHeld ? 5 : 1;
        double dx = 0.0, dy = 0.0;
        if (keyCode == 262) dx = step / Math.max(1.0, sw);       // RIGHT
        else if (keyCode == 263) dx = -step / Math.max(1.0, sw); // LEFT
        else if (keyCode == 264) dy = step / Math.max(1.0, sh);  // DOWN
        else if (keyCode == 265) dy = -step / Math.max(1.0, sh); // UP
        else return false;

        pushUndo();
        ClientConfig cfg = ClientConfig.get();
        Set<String> targets = selectedIds.isEmpty() ? Collections.singleton(primaryId) : selectedIds;
        for (String id : targets) {
            ClientConfig.HudModule m = cfg.hudModules.get(id);
            if (m != null) {
                m.x = Math.max(0.0, Math.min(1.0, m.x + dx));
                m.y = Math.max(0.0, Math.min(1.0, m.y + dy));
            }
        }
        cfg.save();
        return true;
    }

    private void pushUndo() {
        Map<String, double[]> snap = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) snap.put(m.id, new double[]{m.x, m.y, m.scale, m.enabled ? 1.0 : 0.0});
        undoStack.push(snap); while (undoStack.size() > 25) undoStack.removeLast(); redoStack.clear();
    }

    private void applySnapshot(Map<String, double[]> snap) {
        ClientConfig cfg = ClientConfig.get();
        for (Map.Entry<String, double[]> e : snap.entrySet()) {
            ClientConfig.HudModule m = cfg.hudModules.get(e.getKey());
            if (m != null) { m.x = e.getValue()[0]; m.y = e.getValue()[1]; m.scale = e.getValue()[2]; if (e.getValue().length > 3) m.enabled = (e.getValue()[3] > 0.5); }
        }
        cfg.save();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        Map<String, double[]> cur = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) cur.put(m.id, new double[]{m.x, m.y, m.scale, m.enabled ? 1.0 : 0.0});
        redoStack.push(cur); applySnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        Map<String, double[]> cur = new LinkedHashMap<String, double[]>();
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) cur.put(m.id, new double[]{m.x, m.y, m.scale, m.enabled ? 1.0 : 0.0});
        undoStack.push(cur); applySnapshot(redoStack.pop());
    }

    private boolean drawOrHandleTopPill(UiBridge.UiDraw g, int sw, int mx, int my, boolean click, Actions actions, int accent) {
        int pillW = Math.min(310, sw - 20), pillX = (sw - pillW) / 2, pillY = 6, pillH = 22;
        if (click && (mx < pillX || mx > pillX + pillW || my < pillY || my > pillY + pillH)) return false;

        ClientConfig cfg = ClientConfig.get();
        ClientConfig.HudModule sel = primaryId == null ? null : cfg.hudModules.get(primaryId);
        if (!click) {
            g.drawGlassCard(pillX, pillY, pillW, pillH, 11, 0xD809090B, ClientState.UI_BORDER);
            String hint = selectedIds.size() > 1 ? (selectedIds.size() + " sel") : (sel != null && sel.enabled ? (sel.label + " " + String.format("%.1fx", Double.valueOf(sel.scale))) : "Drag | RClick=Cfg");
            g.text(hint, pillX + 9, pillY + 7, ClientState.UI_TEXT_MUTED, false);
        }
        int by = pillY + 3, rx = pillX + pillW - 4;
        if (btn(g, rx - 40, by, 40, 16, "Done", accent, ClientState.getAccentFg(), mx, my, click)) { cfg.save(); if (actions != null) actions.backToMenu(); return true; }
        rx -= 43;
        if (sel != null && sel.enabled) {
            if (btn(g, rx - 38, by, 38, 16, "Rst", 0xFF191924, ClientState.UI_TEXT_MUTED, mx, my, click)) { pushUndo(); cfg.resetModule(sel.id); return true; }
            rx -= 41;
        }
        if (btn(g, rx - 34, by, 34, 16, "Redo", 0xFF191924, redoStack.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, mx, my, click)) { redo(); return true; }
        rx -= 37;
        if (btn(g, rx - 34, by, 34, 16, "Undo", 0xFF191924, undoStack.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, mx, my, click)) { undo(); return true; }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            g.drawGlassCard(x, y, w, h, 8, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        if (hov && click) { g.playClickSound(); return true; }
        return false;
    }
}