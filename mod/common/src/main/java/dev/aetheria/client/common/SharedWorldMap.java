package dev.aetheria.client.common;

import java.util.*;

public class SharedWorldMap {
    public interface Actions {
        void openMinimapSettings();
        void closeMap();
    }

    private static final String[] QUICK_WP_PRESETS = {"Base", "Home", "Portal", "Loot", "Farm"};
    private boolean initialized = false, dragging = false, showWaypoints = true, wpPopupOpen = false;
    private String quickWpName = "";
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
        String wKey = d.worldKey();

        if (cfg.getBool("minimap_structures", true)) {
            List<ClientState.StructurePin> structs = ClientState.getVisibleStructures(d.dimensionId(), wKey);
            for (int i = 0; i < structs.size(); i++) {
                ClientState.StructurePin sp = structs.get(i);
                int sx = (int) Math.round(sw * 0.5 + (sp.x - centerX) / blocksPerPx);
                int sy = (int) Math.round(sh * 0.5 + (sp.z - centerZ) / blocksPerPx);
                if (sx < -24 || sx > sw + 24 || sy < -24 || sy > sh + 24) continue;
                String sKey = ClientState.structureKey(sp, wKey);
                boolean looted = cfg.isStructureLooted(sKey);
                boolean visited = cfg.isStructureVisited(sKey);
                int col = SharedHudRenderer.structureColor(sp.type);
                int brd = looted ? 0xFF52525B : (visited ? 0xFF22D3EE : col);
                g.drawGlassCard(sx - 8, sy - 8, 16, 16, 4, looted ? 0xA009090B : 0xDC09090B, brd);
                if (g.supportsStructureIcons()) {
                    g.drawStructureIcon(sp.type, sx - 6, sy - 6, 0.75F, looted);
                } else {
                    String tag = SharedHudRenderer.structureTag(sp.type);
                    g.text(tag, sx - g.textWidth(tag) / 2, sy - 4, looted ? 0xFF71717A : col, false);
                }
                if (looted) {
                    g.drawCircleFilled(sx + 7, sy - 7, 3, 0xFF10B981);
                } else if (visited) {
                    g.drawCircleFilled(sx + 7, sy - 7, 2, 0xFF22D3EE);
                }
                if (blocksPerPx <= 1.35) {
                    int nw = g.textWidth(sp.name);
                    g.text(sp.name, sx - nw / 2, sy + 10, looted ? 0xFF71717A : 0xFFE4E4E7, true);
                }
                if (Math.abs(mx - sx) <= 10 && Math.abs(my - sy) <= 10) {
                    long dist = Math.round(Math.hypot(sp.x - d.posX(), sp.z - d.posZ()));
                    String state = looted ? "LOOTED" : (visited ? "Visited" : (sp.fromSeed ? "Seed" : "Scanned"));
                    hoverTooltip = sp.name + " (" + sp.x + ", " + sp.z + ") \u2022 " + dist + "m [" + state + "] (LClick=Looted, RClick=WP)";
                }
            }
        }

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

        if (d.hasWorld()) {
            int px = (int) Math.round(sw * 0.5 + (d.posX() - centerX) / blocksPerPx);
            int py = (int) Math.round(sh * 0.5 + (d.posZ() - centerZ) / blocksPerPx);
            double rad = Math.toRadians(d.yaw() + 90.0);
            g.drawLine(px, py, px + (int) Math.round(Math.cos(rad) * 11.0), py + (int) Math.round(Math.sin(rad) * 11.0), 2, accent);
            g.drawCircleFilled(px, py, 5, 0xFF09090B);
            g.drawCircleFilled(px, py, 3, 0xFFFFFFFF);
        }

        drawOrHandleTopBar(g, d, sw, sh, mx, my, false, null, accent, accentFg);
        if (wpPopupOpen) drawOrHandleQuickWpBar(g, d, sw, mx, my, false, accent, accentFg);

        String hint = "Drag=Pan  |  Scroll=Zoom (" + String.format("%.2fx", Double.valueOf(1.0 / blocksPerPx)) + ")  |  LClick Struct=Mark Looted  |  RClick=Quick WP  |  ESC=Close";
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
        if (btn == 0 && drawOrHandleTopBar(g, d, sw, sh, (int) mx, (int) my, true, actions, ClientState.getAccentColor(), ClientState.getAccentFg())) {
            return true;
        }
        if (btn == 0 && wpPopupOpen && drawOrHandleQuickWpBar(g, d, sw, (int) mx, (int) my, true, ClientState.getAccentColor(), ClientState.getAccentFg())) {
            return true;
        }

        ClientConfig cfg = ClientConfig.get();
        if (cfg.getBool("minimap_structures", true)) {
            String wKey = d.worldKey();
            List<ClientState.StructurePin> structs = ClientState.getVisibleStructures(d.dimensionId(), wKey);
            for (int i = structs.size() - 1; i >= 0; i--) {
                ClientState.StructurePin sp = structs.get(i);
                int sx = (int) Math.round(sw * 0.5 + (sp.x - centerX) / blocksPerPx);
                int sy = (int) Math.round(sh * 0.5 + (sp.z - centerZ) / blocksPerPx);
                if (Math.abs(mx - sx) <= 10 && Math.abs(my - sy) <= 10) {
                    g.playClickSound();
                    if (btn == 0) {
                        cfg.toggleStructureLooted(ClientState.structureKey(sp, wKey));
                    } else if (btn == 1 && d.hasWorld()) {
                        cfg.addWaypointAt(sp.name, sp.x, d.y(), sp.z, d.dimensionId(), wKey);
                        if (!cfg.waypoints.isEmpty()) {
                            cfg.waypoints.get(cfg.waypoints.size() - 1).color = ClientState.toHexColor(SharedHudRenderer.structureColor(sp.type));
                            cfg.save();
                        }
                    }
                    return true;
                }
            }
        }

        if (btn == 0) {
            wpPopupOpen = false;
            dragging = true;
            dragStartMx = mx; dragStartMy = my;
            dragStartCx = centerX; dragStartCz = centerZ;
            return true;
        }
        if (btn == 1 && d.hasWorld()) {
            g.playClickSound();
            int wx = (int) Math.round(centerX + (mx - sw * 0.5) * blocksPerPx);
            int wz = (int) Math.round(centerZ + (my - sh * 0.5) * blocksPerPx);
            cfg.addWaypointAt("Map WP #" + (cfg.waypoints.size() + 1), wx, d.y(), wz, d.dimensionId(), d.worldKey());
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

    public boolean keyPressed(boolean isBackspace, boolean isEscape, boolean isEnter, UiBridge.HudData d) {
        if (!wpPopupOpen) return false;
        if (isEscape) { wpPopupOpen = false; return true; }
        if (isBackspace && quickWpName.length() > 0) { quickWpName = quickWpName.substring(0, quickWpName.length() - 1); return true; }
        if (isEnter && d.hasWorld()) {
            ClientConfig.get().addWaypointAt(quickWpName.trim(), d.x(), d.y(), d.z(), d.dimensionId(), d.worldKey());
            quickWpName = ""; wpPopupOpen = false; return true;
        }
        return true;
    }

    public boolean charTyped(char ch) {
        if (!wpPopupOpen) return false;
        if (ch >= 32 && ch != 127 && quickWpName.length() < 24) quickWpName += ch;
        return true;
    }

    private boolean drawOrHandleQuickWpBar(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int mx, int my, boolean click, int accent, int accentFg) {
        int pw = Math.min(430, sw - 20), px = (sw - pw) / 2, py = 36, ph = 46;
        if (click && (mx < px || mx > px + pw || my < py || my > py + ph)) return false;
        if (!click) g.drawGlassCard(px, py, pw, ph, 8, 0xEC101018, accent);

        int inW = pw - 166;
        if (!click) {
            g.drawGlassCard(px + 8, py + 6, inW, 18, 5, ClientState.UI_INPUT, accent);
            g.text(quickWpName.isEmpty() ? "Type quick waypoint name..." : (quickWpName + "_"), px + 14, py + 11, quickWpName.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
        }
        if (btn(g, px + 12 + inW, py + 6, 70, 18, "At Player", accent, accentFg, mx, my, click)) {
            if (d.hasWorld()) {
                ClientConfig.get().addWaypointAt(quickWpName.trim(), d.x(), d.y(), d.z(), d.dimensionId(), d.worldKey());
                quickWpName = ""; wpPopupOpen = false;
            }
            return true;
        }
        if (btn(g, px + 86 + inW, py + 6, 72, 18, "At Center", 0xFF1E1E28, ClientState.UI_TEXT, mx, my, click)) {
            if (d.hasWorld()) {
                ClientConfig.get().addWaypointAt(quickWpName.trim(), (int) Math.round(centerX), d.y(), (int) Math.round(centerZ), d.dimensionId(), d.worldKey());
                quickWpName = ""; wpPopupOpen = false;
            }
            return true;
        }
        int cx0 = px + 8;
        for (int i = 0; i < QUICK_WP_PRESETS.length; i++) {
            String pre = QUICK_WP_PRESETS[i]; int bw = g.textWidth(pre) + 12;
            if (btn(g, cx0, py + 27, bw, 14, pre, 0xFF181822, ClientState.UI_TEXT_MUTED, mx, my, click)) { quickWpName = pre; return true; }
            cx0 += bw + 4;
        }
        return true;
    }

    private boolean drawOrHandleTopBar(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, int mx, int my, boolean click, Actions actions, int accent, int accentFg) {
        int barW = Math.min(680, sw - 16), bx = (sw - barW) / 2, by = 8, bh = 24;
        if (click && (mx < bx || mx > bx + barW || my < by || my > by + bh)) return false;

        ClientConfig cfg = ClientConfig.get();
        int curWx = (int) Math.round(centerX + (mx - sw * 0.5) * blocksPerPx);
        int curWz = (int) Math.round(centerZ + (my - sh * 0.5) * blocksPerPx);
        if (!click) {
            g.drawGlassCard(bx, by, barW, bh, 8, 0xDC09090B, ClientState.UI_BORDER);
            String info = "WORLDMAP \u2022 X:" + curWx + " Z:" + curWz + " \u2022 " + ClientState.getActiveSeedMode();
            int maxInfoW = barW - 332;
            while (info.length() > 8 && g.textWidth(info) > maxInfoW) info = info.substring(0, info.length() - 1);
            g.text(info, bx + 9, by + 8, ClientState.UI_TEXT_MUTED, false);
        }

        int rx = bx + barW - 6, y0 = by + 4;
        if (btn(g, rx - 38, y0, 38, 16, "Close", 0xFF3B1A1E, 0xFFFFFFFF, mx, my, click)) { if (actions != null) actions.closeMap(); return true; }
        rx -= 41;
        if (btn(g, rx - 30, y0, 30, 16, "Cfg", 0xFF1E1E28, ClientState.UI_TEXT, mx, my, click)) { if (actions != null) actions.openMinimapSettings(); return true; }
        rx -= 33;
        if (btn(g, rx - 46, y0, 46, 16, "Center", accent, accentFg, mx, my, click)) { if (d.hasWorld()) { centerX = d.posX(); centerZ = d.posZ(); } return true; }
        rx -= 49;
        if (btn(g, rx - 42, y0, 42, 16, "+ WP", wpPopupOpen ? accent : 0xFF1E293B, wpPopupOpen ? accentFg : ClientState.UI_TEXT, mx, my, click)) { wpPopupOpen = !wpPopupOpen; return true; }
        rx -= 45;
        boolean hideLoot = cfg.getBool("minimap_hide_looted", false);
        if (btn(g, rx - 52, y0, 52, 16, hideLoot ? "Loot:Hid" : "Loot:Shw", hideLoot ? 0xFF2B181E : 0xFF1A1A24, ClientState.UI_TEXT_MUTED, mx, my, click)) { cfg.setOption("minimap_hide_looted", Boolean.valueOf(!hideLoot)); return true; }
        rx -= 55;
        boolean stOn = cfg.getBool("minimap_structures", true);
        if (btn(g, rx - 48, y0, 48, 16, "Str:" + (stOn ? "ON" : "OFF"), stOn ? 0xFF162C25 : 0xFF1A1A24, stOn ? 0xFF6EE7B7 : ClientState.UI_TEXT_DIM, mx, my, click)) { cfg.setOption("minimap_structures", Boolean.valueOf(!stOn)); return true; }
        rx -= 51;
        if (btn(g, rx - 42, y0, 42, 16, "WP:" + (showWaypoints ? "ON" : "OFF"), showWaypoints ? ClientState.withAlpha(accent, 45) : 0xFF1A1A24, ClientState.UI_TEXT, mx, my, click)) { showWaypoints = !showWaypoints; return true; }
        return false;
    }

    private boolean btn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) { g.drawGlassCard(x, y, w, h, 5, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER); g.text(txt, x + (w - g.textWidth(txt)) / 2, y + 4, fg, false); }
        if (hov && click) { g.playClickSound(); return true; }
        return false;
    }
}