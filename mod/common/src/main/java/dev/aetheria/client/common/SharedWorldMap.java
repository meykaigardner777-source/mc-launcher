package dev.aetheria.client.common;

import java.util.*;

public class SharedWorldMap {
    public interface Actions {
        void openMinimapSettings();
        void closeMap();
    }

    private boolean initialized = false, dragging = false, showWaypoints = true;
    private double centerX = 0.0, centerZ = 0.0, blocksPerPx = 1.0;
    private double dragStartMx = 0.0, dragStartMy = 0.0, dragStartCx = 0.0, dragStartCz = 0.0;

    public void render(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, int mx, int my) {
        if (!initialized && d.hasWorld()) {
            centerX = d.posX();
            centerZ = d.posZ();
            initialized = true;
        }
        ClientConfig cfg = ClientConfig.get();
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();

        g.fill(0, 0, sw, sh, 0xFF07080D);
        g.drawWorldMapTiles(0, 0, sw, sh, centerX, centerZ, blocksPerPx);

        // Subtle chunk grid when zoomed in
        if (blocksPerPx <= 1.25) {
            double leftW = centerX - (sw * 0.5) * blocksPerPx, topW = centerZ - (sh * 0.5) * blocksPerPx;
            int firstCx = (int) Math.floor(leftW / 16.0) * 16;
            int firstCz = (int) Math.floor(topW / 16.0) * 16;
            for (int wx = firstCx; wx <= leftW + sw * blocksPerPx; wx += 16) {
                int sx = (int) Math.round(sw * 0.5 + (wx - centerX) / blocksPerPx);
                g.fill(sx, 0, sx + 1, sh, 0x10FFFFFF);
            }
            for (int wz = firstCz; wz <= topW + sh * blocksPerPx; wz += 16) {
                int sy = (int) Math.round(sh * 0.5 + (wz - centerZ) / blocksPerPx);
                g.fill(0, sy, sw, sy + 1, 0x10FFFFFF);
            }
        }

        String hoverTooltip = null;

        // Structures
        if (cfg.getBool("minimap_structures", true)) {
            List<ClientState.StructurePin> structs = ClientState.getVisibleStructures(d.dimensionId(), d.worldKey());
            for (int i = 0; i < structs.size(); i++) {
                ClientState.StructurePin sp = structs.get(i);
                int sx = (int) Math.round(sw * 0.5 + (sp.x - centerX) / blocksPerPx);
                int sy = (int) Math.round(sh * 0.5 + (sp.z - centerZ) / blocksPerPx);
                if (sx < -24 || sx > sw + 24 || sy < -24 || sy > sh + 24) continue;
                int col = SharedHudRenderer.structureColor(sp.type);
                String tag = SharedHudRenderer.structureTag(sp.type);
                g.drawGlassCard(sx - 7, sy - 7, 14, 14, 4, 0xD809090B, col);
                g.text(tag, sx - g.textWidth(tag) / 2, sy - 4, col, false);
                if (blocksPerPx <= 1.35) {
                    int nw = g.textWidth(sp.name);
                    g.text(sp.name, sx - nw / 2, sy + 9, 0xFFE4E4E7, true);
                }
                if (Math.abs(mx - sx) <= 9 && Math.abs(my - sy) <= 9) {
                    long dist = Math.round(Math.hypot(sp.x - d.posX(), sp.z - d.posZ()));
                    hoverTooltip = sp.name + " (" + sp.x + ", " + sp.z + ") \u2022 " + dist + "m [" + (sp.fromSeed ? "Seed" : "Scanned") + "]";
                }
            }
        }

        // Waypoints
        if (showWaypoints) {
            boolean sync = cfg.getBool("wp_nether_sync", true);
            for (int i = 0; i < cfg.waypoints.size(); i++) {
                ClientConfig.Waypoint wp = cfg.waypoints.get(i);
                double[] pos = ClientState.resolveWaypointCoords(wp, d.dimensionId(), sync);
                if (pos == null) continue;
                int sx = (int) Math.round(sw * 0.5 + (pos[0] - centerX) / blocksPerPx);
                int sy = (int) Math.round(sh * 0.5 + (pos[2] - centerZ) / blocksPerPx);
                if (sx < -24 || sx > sw + 24 || sy < -24 || sy > sh + 24) continue;
                int wc = ClientState.parseHexColor(wp.color, 0xFF22D3EE);
                g.drawCircleFilled(sx, sy, 5, 0xDD09090B);
                g.drawCircleFilled(sx, sy, 4, wc);
                int nw = g.textWidth(wp.name) + 8;
                g.drawGlassCard(sx - nw / 2, sy + 6, nw, 12, 4, 0xB809090B, ClientState.withAlpha(wc, 160));
                g.text(wp.name, sx - nw / 2 + 4, sy + 8, 0xFFF4F4F5, false);
                if (Math.abs(mx - sx) <= 8 && Math.abs(my - sy) <= 8) {
                    long dist = Math.round(Math.hypot(pos[0] - d.posX(), pos[2] - d.posZ()));
                    hoverTooltip = "WP: " + wp.name + " (" + Math.round(pos[0]) + ", " + wp.y + ", " + Math.round(pos[2]) + ") \u2022 " + dist + "m";
                }
            }
        }

        // Player arrow
        if (d.hasWorld()) {
            int px = (int) Math.round(sw * 0.5 + (d.posX() - centerX) / blocksPerPx);
            int py = (int) Math.round(sh * 0.5 + (d.posZ() - centerZ) / blocksPerPx);
            double rad = Math.toRadians(d.yaw() + 90.0);
            g.drawLine(px, py, px + (int) Math.round(Math.cos(rad) * 11.0), py + (int) Math.round(Math.sin(rad) * 11.0), 2, accent);
            g.drawCircleFilled(px, py, 5, 0xFF09090B);
            g.drawCircleFilled(px, py, 3, 0xFFFFFFFF);
        }

        // Top bar
        drawOrHandleTopBar(g, d, sw, mx, my, false, null, accent, accentFg);

        // Bottom controls hint
        String hint = "Drag=Pan  |  Scroll=Zoom (" + String.format("%.2fx", Double.valueOf(1.0 / blocksPerPx)) + ")  |  Right-Click=Add Waypoint  |  ESC=Close";
        int hw = g.textWidth(hint) + 16;
        g.drawGlassCard(8, sh - 24, hw, 18, 6, 0xD009090B, ClientState.UI_BORDER);
        g.text(hint, 16, sh - 19, ClientState.UI_TEXT_MUTED, false);

        if (hoverTooltip != null) {
            int tw = g.textWidth(hoverTooltip) + 14;
            int tx = Math.max(8, Math.min(sw - tw - 8, mx + 12));
            int ty = Math.max(36, Math.min(sh - 24, my - 10));
            g.drawGlassCard(tx, ty, tw, 16, 6, 0xF0121219, accent);
            g.text(hoverTooltip, tx + 7, ty + 4, ClientState.UI_TEXT, false);
        }
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int btn, Actions actions) {
        if (btn == 0 && drawOrHandleTopBar(g, d, sw, (int) mx, (int) my, true, actions, ClientState.getAccentColor(), ClientState.getAccentFg())) {
            return true;
        }
        if (btn == 0) {
            dragging = true;
            dragStartMx = mx; dragStartMy = my;
            dragStartCx = centerX; dragStartCz = centerZ;
            return true;
        }
        if (btn == 1 && d.hasWorld()) {
            g.playClickSound();
            int wx = (int) Math.round(centerX + (mx - sw * 0.5) * blocksPerPx);
            int wz = (int) Math.round(centerZ + (my - sh * 0.5) * blocksPerPx);
            ClientConfig.get().addWaypointAt("Map WP #" + (ClientConfig.get().waypoints.size() + 1), wx, d.y(), wz, d.dimensionId(), d.worldKey());
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (!dragging) return false;
        centerX = dragStartCx - (mx - dragStartMx) * blocksPerPx;
        centerZ = dragStartCz - (my - dragStartMy) * blocksPerPx;
        return true;
    }

    public boolean mouseReleased() {
        if (dragging) { dragging = false; return true; }
        return false;
    }

    public boolean mouseScrolled(double scrollY) {
        if (scrollY == 0) return false;
        double factor = scrollY > 0 ? 0.82 : 1.22;
        blocksPerPx = Math.max(0.20, Math.min(10.0, blocksPerPx * factor));
        return true;
    }

    private boolean drawOrHandleTopBar(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int mx, int my, boolean click, Actions actions, int accent, int accentFg) {
        int barW = Math.min(600, sw - 16), bx = (sw - barW) / 2, by = 8, bh = 24;
        if (click && (mx < bx || mx > bx + barW || my < by || my > by + bh)) return false;

        ClientConfig cfg = ClientConfig.get();
        int curWx = (int) Math.round(centerX + (mx - sw * 0.5) * blocksPerPx);
        int curWz = (int) Math.round(centerZ + (my - 240) * blocksPerPx);
        if (!click) {
            g.drawGlassCard(bx, by, barW, bh, 8, 0xDC09090B, ClientState.UI_BORDER);
            String info = "WORLDMAP  \u2022  X:" + curWx + " Z:" + curWz + "  \u2022  " + ClientState.getActiveSeedMode();
            g.text(info, bx + 10, by + 8, ClientState.UI_TEXT_MUTED, false);
        }

        int rx = bx + barW - 6, y0 = by + 4;
        if (btn(g, rx - 44, y0, 44, 16, "Close", 0xFF3B1A1E, 0xFFFFFFFF, mx, my, click)) { if (actions != null) actions.closeMap(); return true; }
        rx -= 48;
        if (btn(g, rx - 34, y0, 34, 16, "Cfg", 0xFF1E1E28, ClientState.UI_TEXT, mx, my, click)) { if (actions != null) actions.openMinimapSettings(); return true; }
        rx -= 38;
        if (btn(g, rx - 58, y0, 58, 16, "Center", accent, accentFg, mx, my, click)) { if (d.hasWorld()) { centerX = d.posX(); centerZ = d.posZ(); } return true; }
        rx -= 62;
        boolean stOn = cfg.getBool("minimap_structures", true);
        if (btn(g, rx - 62, y0, 62, 16, "Struct:" + (stOn ? "ON" : "OFF"), stOn ? 0xFF162C25 : 0xFF1A1A24, stOn ? 0xFF6EE7B7 : ClientState.UI_TEXT_DIM, mx, my, click)) { cfg.setOption("minimap_structures", Boolean.valueOf(!stOn)); return true; }
        rx -= 66;
        if (btn(g, rx - 46, y0, 46, 16, "WP:" + (showWaypoints ? "ON" : "OFF"), showWaypoints ? ClientState.withAlpha(accent, 45) : 0xFF1A1A24, ClientState.UI_TEXT, mx, my, click)) { showWaypoints = !showWaypoints; return true; }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) { g.drawGlassCard(x, y, w, h, 5, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER); g.text(txt, x + (w - g.textWidth(txt)) / 2, y + 4, fg, false); }
        if (hov && click) { g.playClickSound(); return true; }
        return false;
    }
}