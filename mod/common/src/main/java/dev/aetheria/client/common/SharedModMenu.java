package dev.aetheria.client.common;

import java.util.*;

public class SharedModMenu {
    public interface Actions {
        void openHudEditor();
        void closeMenu();
    }

    private static final class ModEntry {
        final String id, name, category, desc;
        ModEntry(String id, String name, String category, String desc) {
            this.id = id; this.name = name; this.category = category; this.desc = desc;
        }
    }

    private static final List<ModEntry> ALL_MODS = new ArrayList<ModEntry>();
    static {
        ALL_MODS.add(new ModEntry("crosshair", "Crosshair & Target", "pvp", "Custom shapes & Player-in-View switching"));
        ALL_MODS.add(new ModEntry("zoom", "Optical Zoom", "camera", "Telescopic FOV zoom with smooth easing"));
        ALL_MODS.add(new ModEntry("low_fire", "Low Fire", "pvp", "Lowers first-person fire screen overlay"));
        ALL_MODS.add(new ModEntry("fps", "FPS Counter", "hud", "Displays live client framerate"));
        ALL_MODS.add(new ModEntry("coords", "Coordinates", "camera", "Current X / Y / Z and facing direction"));
        ALL_MODS.add(new ModEntry("biome", "Biome", "camera", "Shows your current world biome"));
        ALL_MODS.add(new ModEntry("keystrokes", "Keystrokes", "pvp", "Live WASD, Space, LMB & RMB overlay"));
        ALL_MODS.add(new ModEntry("cps", "CPS Counter", "pvp", "Left and Right clicks per second"));
        ALL_MODS.add(new ModEntry("togglesprint", "ToggleSprint", "pvp", "Automatic sprint while holding forward"));
        ALL_MODS.add(new ModEntry("ping", "Live Ping", "pvp", "Shows current server latency in ms"));
        ALL_MODS.add(new ModEntry("armor", "Armor Status", "hud", "Equipped armor durability percentages"));
        ALL_MODS.add(new ModEntry("potions", "Potion Effects", "hud", "Active potion timers and names"));
        ALL_MODS.add(new ModEntry("compass", "Compass Strip", "hud", "Top-center cardinal direction strip"));
        ALL_MODS.add(new ModEntry("durability", "Tool Durability", "hud", "Remaining uses on held item"));
        ALL_MODS.add(new ModEntry("combo", "Combo Counter", "pvp", "Tracks consecutive melee hits"));
        ALL_MODS.add(new ModEntry("reach", "Reach Display", "pvp", "Displays last melee hit distance"));
        ALL_MODS.add(new ModEntry("clock", "System Clock", "hud", "Local 12h or 24h clock overlay"));
        ALL_MODS.add(new ModEntry("session", "Session Timer", "hud", "Elapsed time in current game session"));
        ALL_MODS.add(new ModEntry("server_ip", "Server IP", "hud", "Shows connected server address"));
        ALL_MODS.add(new ModEntry("day_count", "Day Counter", "camera", "Current Minecraft world day count"));
    }

    private static final String[] SWATCHES = {
        "#ffffff", "#a1a1aa", "#22d3ee", "#10b981", "#a855f7",
        "#f43f5e", "#f59e0b", "#84cc16", "#60a5fa", "#fde047"
    };
    private static final String[] XHAIR_STYLES = {"vanilla", "classic", "dot", "circle", "t_shape", "arrows", "x_cross"};
    private static final String[] TARGET_STYLES = {"same", "classic", "dot", "circle", "t_shape", "arrows", "x_cross"};
    private static final String[] ZOOM_KEYS = {"C", "V", "Z", "X", "G", "R", "ALT"};
    private static final String[] MENU_KEYS = {"RSHIFT", "M", "H", "INSERT", "K", "P"};
    private static final String[][] ACCENTS = {
        {"white", "Obsidian"}, {"cyan", "Cyan"}, {"emerald", "Emerald"},
        {"amethyst", "Amethyst"}, {"crimson", "Crimson"}, {"amber", "Amber"}, {"lime", "Lime"}
    };

    private String activeMenu = "all", editingModId = null, searchQuery = "";
    private boolean searchFocused = false;
    private float scrollPx = 0.0F, targetScrollPx = 0.0F;
    private int maxScrollPx = 0;

    private final long openedNs = System.nanoTime();
    private long lastFrameNs = System.nanoTime();
    private float frameDt = 0.016F;
    private final Map<String, Float> pillAnim = new HashMap<String, Float>();
    private final Map<String, Float> hoverAnim = new HashMap<String, Float>();

    private String activeSliderId = null;
    private int sliderX, sliderW;
    private double sliderMin, sliderMax, sliderStep;

    public SharedModMenu() {}
    public SharedModMenu(String initialModId) { this.editingModId = initialModId; }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        long now = System.nanoTime();
        frameDt = Math.min(0.05F, Math.max(0.001F, (now - lastFrameNs) / 1_000_000_000.0F));
        lastFrameNs = now;

        boolean anim = ClientConfig.get().getBool("ui_animations", true);
        if (anim) scrollPx += (targetScrollPx - scrollPx) * Math.min(1.0F, frameDt * 18.0F);
        else scrollPx = targetScrollPx;

        float openT = anim ? Math.min(1.0F, (now - openedNs) / 180_000_000.0F) : 1.0F;
        float ease = 1.0F - (float) Math.pow(1.0F - openT, 3);
        float scale = 0.94F + 0.06F * ease;

        g.fill(0, 0, sw, sh, ClientState.scaleAlpha(ClientState.UI_BACKDROP, ease));
        if (scale < 0.999F) {
            float ox = (sw / 2.0F) * (1.0F - scale), oy = (sh / 2.0F) * (1.0F - scale);
            g.pushTransform(ox, oy, scale);
            handleFrame(g, sw, sh, mx, my, false, null);
            g.popTransform();
        } else {
            handleFrame(g, sw, sh, mx, my, false, null);
        }
    }

    public boolean mouseClicked(UiBridge.UiDraw g, int sw, int sh, double mx, double my, Actions actions) {
        return handleFrame(g, sw, sh, (int) mx, (int) my, true, actions);
    }

    public boolean mouseDragged(double mx) {
        if (activeSliderId == null) return false;
        applySliderValue(activeSliderId, (int) mx);
        return true;
    }

    public boolean mouseReleased() {
        if (activeSliderId != null) { activeSliderId = null; ClientConfig.get().save(); return true; }
        return false;
    }

    public boolean mouseScrolled(double scrollY) {
        if (scrollY == 0) return false;
        targetScrollPx = Math.max(0.0F, Math.min(maxScrollPx, targetScrollPx - (float) (scrollY * 32.0)));
        return true;
    }

    public boolean keyPressed(boolean isBackspace, boolean isEscape) {
        if (searchFocused) {
            if (isBackspace && searchQuery.length() > 0) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); resetScroll(); return true; }
            if (isEscape) { searchFocused = false; searchQuery = ""; resetScroll(); return true; }
        }
        return false;
    }

    public boolean charTyped(char ch) {
        if (!searchFocused) return false;
        if (ch >= 32 && ch != 127 && searchQuery.length() < 32) { searchQuery += ch; resetScroll(); return true; }
        return false;
    }

    private void resetScroll() { scrollPx = 0.0F; targetScrollPx = 0.0F; }

    private float animHover(String key, boolean hovered) {
        Float prev = hoverAnim.get(key);
        float cur = prev != null ? prev.floatValue() : 0.0F;
        if (!ClientConfig.get().getBool("ui_animations", true)) cur = hovered ? 1.0F : 0.0F;
        else cur += ((hovered ? 1.0F : 0.0F) - cur) * Math.min(1.0F, frameDt * 16.0F);
        hoverAnim.put(key, Float.valueOf(cur));
        return cur;
    }

    private float animPill(String key, boolean on) {
        Float prev = pillAnim.get(key);
        float target = on ? 1.0F : 0.0F;
        float cur = prev != null ? prev.floatValue() : target;
        if (!ClientConfig.get().getBool("ui_animations", true)) cur = target;
        else cur += (target - cur) * Math.min(1.0F, frameDt * 18.0F);
        pillAnim.put(key, Float.valueOf(cur));
        return cur;
    }

    private boolean handleFrame(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions) {
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();
        int panelW = Math.min(730, sw - 24), panelH = Math.min(430, sh - 24);
        int px = (sw - panelW) / 2, py = (sh - panelH) / 2;
        int topH = 42;
        int cx = px + 16, cy = py + topH + 12, cw = panelW - 32, ch = panelH - topH - 24;

        if (!click) {
            g.drawSoftShadow(px, py, panelW, panelH, 14);
            g.drawGlassCard(px, py, panelW, panelH, 14, ClientState.UI_PANEL, ClientState.UI_BORDER_HOV, true);
            g.drawRoundedRect(px + 1, py + 1, panelW - 2, topH - 1, 13, ClientState.UI_TITLEBAR);
            g.fill(px + 1, py + topH, px + panelW - 1, py + topH + 1, ClientState.UI_BORDER);
            g.text("AETHERIA", px + 14, py + 17, ClientState.UI_TEXT, true);
            g.drawGlassCard(px + 68, py + 14, 42, 14, 5, ClientState.withAlpha(accent, 26), ClientState.UI_BORDER, false);
            g.text("CLIENT", px + 73, py + 17, ClientState.UI_TEXT_MUTED, false);
        }

        // Right-aligned Close & Edit HUD pill buttons (matching index.html titlebar)
        int closeX = px + panelW - 32, closeY = py + 10;
        boolean closeHov = mx >= closeX && mx <= closeX + 22 && my >= closeY && my <= closeY + 22;
        if (!click) {
            float ht = animHover("win_close", closeHov);
            int cBg = ClientState.lerpColor(0x00000000, ClientState.UI_DANGER, ht);
            if ((cBg >>> 24) > 4) g.drawRoundedRect(closeX, closeY, 22, 22, 6, cBg);
            g.text("x", closeX + 8, closeY + 7, closeHov ? 0xFFFFFFFF : ClientState.UI_TEXT_MUTED, false);
        } else if (closeHov) {
            ClientConfig.get().save();
            if (actions != null) actions.closeMenu();
            return true;
        }

        int ehW = 68, ehX = closeX - ehW - 8, ehY = py + 10;
        if (drawBtn(g, "btn_edithud", ehX, ehY, ehW, 22, 8, "Edit HUD", accent, accentFg, true, mx, my, click)) {
            if (actions != null) actions.openHudEditor();
            return true;
        }

        // Top Horizontal Pill Nav (matching index.html .top-nav-btn)
        String[][] nav = {{"all", "All"}, {"hud", "HUD"}, {"pvp", "PvP"}, {"camera", "Camera"}, {"settings", "Settings"}};
        int navRight = ehX - 10;
        int navX = px + 120;
        for (int i = 0; i < nav.length; i++) {
            String id = nav[i][0], lbl = nav[i][1];
            int bw = g.textWidth(lbl) + 16;
            if (navX + bw > navRight) break;
            boolean sel = (editingModId == null) && activeMenu.equals(id);
            boolean hov = mx >= navX && mx <= navX + bw && my >= py + 10 && my <= py + 32;
            if (!click) {
                float ht = animHover("nav_" + id, hov);
                int bg = sel ? ClientState.withAlpha(accent, 34) : ClientState.lerpColor(0x00000000, ClientState.withAlpha(accent, 20), ht);
                int brd = sel ? accent : ClientState.lerpColor(0x00000000, ClientState.UI_BORDER, ht);
                if ((bg >>> 24) > 4 || (brd >>> 24) > 4) g.drawGlassCard(navX, py + 10, bw, 22, 8, bg, brd, false);
                g.text(lbl, navX + 8, py + 17, sel ? ClientState.UI_TEXT : (hov ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED), false);
            } else if (hov) {
                activeMenu = id; editingModId = null; searchFocused = false; resetScroll(); return true;
            }
            navX += bw + 4;
        }

        if (editingModId != null) {
            return renderModSettingsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg);
        }
        if ("settings".equals(activeMenu)) {
            if (click) searchFocused = false;
            return renderClientSettingsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg);
        }

        // Subheader: Section title + rounded pill search input
        if (!click) g.text(getMenuTitle(activeMenu), cx + 2, cy + 5, ClientState.UI_TEXT, true);
        int sbW = Math.min(185, cw / 2), sbX = cx + cw - sbW, sbY = cy;
        boolean sHov = mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + 22;
        if (!click) {
            g.drawGlassCard(sbX, sbY, sbW, 22, 9, ClientState.UI_INPUT, searchFocused ? accent : (sHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER), false);
            String disp = searchQuery.isEmpty() ? (searchFocused ? "_" : "Search mods...") : (searchQuery + (searchFocused ? "_" : ""));
            g.text(disp, sbX + 9, sbY + 7, searchQuery.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
            if (!searchQuery.isEmpty()) g.text("x", sbX + sbW - 14, sbY + 7, ClientState.UI_TEXT_MUTED, false);
        } else if (sHov) {
            if (!searchQuery.isEmpty() && mx >= sbX + sbW - 18) { searchQuery = ""; resetScroll(); }
            else searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        return renderModCardGrid(g, cx, cy + 30, cw, ch - 30, mx, my, click, accent, accentFg);
    }

    private boolean renderModCardGrid(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg) {
        List<ModEntry> list = new ArrayList<ModEntry>();
        String q = searchQuery.trim().toLowerCase();
        for (ModEntry m : ALL_MODS) {
            if (!"all".equals(activeMenu) && !m.category.equals(activeMenu)) continue;
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.desc.toLowerCase().contains(q)) continue;
            list.add(m);
        }

        int cols = cw >= 430 ? 2 : 1, gap = 10, cardH = 66, trackW = 8;
        int cardW = (cw - trackW - gap * (cols - 1)) / cols;
        int rows = (int) Math.ceil(list.size() / (double) cols);
        int totalH = Math.max(0, rows * (cardH + gap) - gap);
        maxScrollPx = Math.max(0, totalH - ch);
        if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx);

        if (!click && list.isEmpty()) g.text("No matching mods found.", cx + 8, cy + 16, ClientState.UI_TEXT_MUTED, false);

        for (int i = 0; i < list.size(); i++) {
            ModEntry m = list.get(i);
            int r = i / cols, c = i % cols;
            int bx = cx + c * (cardW + gap), by = cy + r * (cardH + gap) - curScroll;
            if (by + cardH < cy || by > cy + ch) continue;

            boolean fullVis = by >= cy && by + cardH <= cy + ch;
            int topVis = Math.max(cy, by), botVis = Math.min(cy + ch, by + cardH);
            if (botVis - topVis < 18) continue;

            boolean enabled = isModEnabled(m.id);
            boolean hov = mx >= bx && mx <= bx + cardW && my >= topVis && my <= botVis;
            int cardRad = fullVis ? 10 : 4;

            if (!click) {
                float ht = animHover("card_" + m.id, hov);
                int bg = ClientState.lerpColor(ClientState.UI_CARD, ClientState.UI_CARD_HOVER, ht);
                int brd = enabled
                    ? ClientState.lerpColor(ClientState.withAlpha(accent, 145), accent, ht)
                    : ClientState.lerpColor(ClientState.UI_BORDER, ClientState.UI_BORDER_HOV, ht);
                g.drawGlassCard(bx, topVis, cardW, botVis - topVis, cardRad, bg, brd, fullVis);
                if (by + 10 >= cy && by + 20 <= cy + ch) {
                    g.drawCircleFilled(bx + 14, by + 14, 3, enabled ? accent : ClientState.UI_TEXT_DIM);
                    g.text(m.name, bx + 23, by + 10, ClientState.UI_TEXT, true);
                }
                if (by + 23 >= cy && by + 33 <= cy + ch) {
                    g.text(trimToWidth(g, m.desc, cardW - 24), bx + 11, by + 23, ClientState.UI_TEXT_MUTED, false);
                }
            }

            if (by + 39 >= cy && by + 58 <= cy + ch) {
                if (drawBtn(g, "cfg_" + m.id, bx + 11, by + 39, 62, 18, 7, "Settings", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    editingModId = m.id; resetScroll(); return true;
                }
                if (drawPill(g, "tog_" + m.id, bx + cardW - 50, by + 40, 39, 16, enabled, accent, accentFg, mx, my, click)) {
                    toggleMod(m.id); return true;
                }
            }
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, curScroll, accent);
        return false;
    }

    private boolean renderModSettingsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg) {
        ClientConfig cfg = ClientConfig.get();
        boolean isHud = cfg.hudModules.containsKey(editingModId), enabled = isModEnabled(editingModId);

        if (drawBtn(g, "set_back", cx, cy, 56, 22, 8, "< Back", ClientState.UI_CARD, ClientState.UI_TEXT, false, mx, my, click)) {
            editingModId = null; resetScroll(); return true;
        }
        if (!click) g.text(getModTitle(editingModId), cx + 66, cy + 7, ClientState.UI_TEXT, true);
        if (isHud && drawBtn(g, "set_rst", cx + cw - 126, cy, 74, 22, 8, "Reset Style", ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
            cfg.resetModule(editingModId); return true;
        }
        if (drawPill(g, "set_tog_" + editingModId, cx + cw - 44, cy + 3, 44, 16, enabled, accent, accentFg, mx, my, click)) {
            toggleMod(editingModId); return true;
        }

        int listY = cy + 30, listH = ch - 30, rw = cw - 12;
        List<SettingRow> rows = new ArrayList<SettingRow>();
        if ("crosshair".equals(editingModId)) buildCrosshairRows(rows);
        else if ("zoom".equals(editingModId)) buildZoomRows(rows);
        else if ("low_fire".equals(editingModId)) buildLowFireRows(rows);
        else if (isHud) buildHudModuleRows(rows, cfg.hudModules.get(editingModId));

        int totalH = 0;
        for (int i = 0; i < rows.size(); i++) totalH += rows.get(i).height() + 8;
        maxScrollPx = Math.max(0, totalH - listH);
        if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx);

        int ry = listY - curScroll;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int rh = row.height();
            if (ry >= listY && ry + rh <= listY + listH) {
                if (row.renderOrHandle(g, cx, ry, rw, mx, my, click, accent, accentFg)) return true;
            }
            ry += rh + 8;
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, curScroll, accent);
        return false;
    }

    private boolean renderClientSettingsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg) {
        if (!click) g.text("Client Settings", cx + 2, cy + 4, ClientState.UI_TEXT, true);
        int listY = cy + 24, listH = ch - 24, rw = cw - 12;

        List<SettingRow> rows = new ArrayList<SettingRow>();
        rows.add(cycleRow("Menu Keybind", "In-game key to open Aetheria Client", "ctl_menu_key", MENU_KEYS));
        rows.add(accentRow("Launcher Accent Color"));
        rows.add(boolRow("Smooth iOS Animations", "Easing on menu open, toggles & scroll", "ui_animations", true));
        rows.add(boolRow("HUD Text Drop Shadow", "Global shadow behind all HUD modules", "hud_shadow", true));
        rows.add(boolRow("HUD Editor Snap Grid", "Snaps modules to a 40-step grid when dragging", "hud_snap_grid", false));
        rows.add(actionRow("Reset All HUD Positions", "Restores default positions, radius & colors", "reset_layout"));
        rows.add(actionRow("Reset All Client Settings", "Restores all options and layout to defaults", "reset_all"));

        int totalH = 0;
        for (int i = 0; i < rows.size(); i++) totalH += rows.get(i).height() + 8;
        maxScrollPx = Math.max(0, totalH - listH);
        if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx);

        int ry = listY - curScroll;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int rh = row.height();
            if (ry >= listY && ry + rh <= listY + listH) {
                if (row.renderOrHandle(g, cx, ry, rw, mx, my, click, accent, accentFg)) return true;
            }
            ry += rh + 8;
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, curScroll, accent);
        return false;
    }

    private void buildCrosshairRows(List<SettingRow> rows) {
        rows.add(xhairPreviewRow());
        rows.add(cycleRow("Default Shape", "Crosshair texture when looking at blocks/air", "pvp_xhair_style", XHAIR_STYLES));
        rows.add(sliderRow("Size", "Crosshair arm length or circle radius", "xhair_size", 2, 16, 1, true));
        rows.add(sliderRow("Center Gap", "Spacing around center pixel", "xhair_gap", 0, 8, 1, true));
        rows.add(sliderRow("Thickness", "Pixel line width (1-3px)", "xhair_thick", 1, 3, 1, true));
        rows.add(colorRow("Default Color", "opt:pvp_xhair_color"));
        rows.add(boolRow("Player-in-View Switch", "Change texture & color when aiming at a player", "pvp_xhair_target", true));
        rows.add(boolRow("Players Only", "ON = Players only | OFF = Players & Mobs", "pvp_xhair_target_players_only", true));
        rows.add(sliderRow("Detection Range", "Max distance in blocks (3-32)", "xhair_range", 3, 32, 1, true));
        rows.add(cycleRow("In-View Shape", "Crosshair texture when player is in view", "pvp_xhair_target_style", TARGET_STYLES));
        rows.add(colorRow("In-View Color", "opt:pvp_xhair_target_color"));
    }

    private void buildZoomRows(List<SettingRow> rows) {
        rows.add(sliderRow("Zoom Magnification", "Optical zoom multiplier (2x-12x)", "zoom_fac", 2, 12, 1, true));
        rows.add(boolRow("Smooth Zoom Easing", "Smoothly animates FOV in and out", "cam_zoom_ease", true));
        rows.add(boolRow("Slow Mouse While Zoomed", "Lowers look sensitivity for precision aim", "cam_zoom_slow", true));
        rows.add(cycleRow("Zoom Keybind", "Hold this key in-game to zoom", "cam_zoom_key", ZOOM_KEYS));
    }

    private void buildLowFireRows(List<SettingRow> rows) {
        rows.add(boolRow("Low Fire Overlay", "Lowers first-person fire flames", "pvp_low_fire", true));
        rows.add(sliderRow("Fire Vertical Offset", "Negative values lower fire further", "fire_offset", -0.50, 0.0, 0.02, false));
    }

    private void buildHudModuleRows(List<SettingRow> rows, ClientConfig.HudModule m) {
        if ("fps".equals(m.id)) rows.add(boolRow("Show 'FPS' Label", "Display '185 FPS' or just '185'", "fps_show_label", true));
        if ("coords".equals(m.id)) {
            rows.add(boolRow("Show Facing Direction", "Includes North/South/East/West", "coords_facing", true));
            rows.add(boolRow("Compact Single-Line", "Displays X, Y, Z on one compact line", "coords_compact", false));
        }
        if ("clock".equals(m.id)) rows.add(boolRow("24-Hour Clock", "Use 24-hour time instead of AM/PM", "clock_24h", false));
        if ("compass".equals(m.id)) rows.add(boolRow("Show Degrees", "Displays yaw angle beside direction", "compass_degrees", true));
        if ("armor".equals(m.id)) rows.add(boolRow("Show Percentages", "Show durability % on each armor piece", "armor_percent", true));
        if ("keystrokes".equals(m.id)) {
            rows.add(boolRow("Show LMB / RMB CPS", "Includes click counters on Keystrokes", "keystrokes_cps", true));
            rows.add(boolRow("Show Spacebar", "Includes Spacebar key below WASD", "keystrokes_space", true));
        }
        if ("togglesprint".equals(m.id)) rows.add(boolRow("Hide Status Text", "Keep ToggleSprint running with no HUD label", "togglesprint_hide_hud", false));

        rows.add(sliderRow("Scale", "Resize module (or drag corner in Edit HUD)", "mod_scale:" + m.id, 0.50, 2.50, 0.05, false));
        rows.add(sliderRow("Corner Radius", "Roundness of HUD box corners (0-14px)", "mod_rad:" + m.id, 0, 14, 1, true));
        rows.add(modBoolRow("Background Box", "Draw dark Obsidian glass backdrop behind text", m.id, "bg"));
        rows.add(sliderRow("Background Opacity", "Transparency of module backdrop", "mod_opacity:" + m.id, 0.0, 1.0, 0.05, false));
        rows.add(modBoolRow("Border", "Draw thin rounded border around module", m.id, "border"));
        rows.add(modBoolRow("Text Shadow", "Drop shadow under this module's text", m.id, "shadow"));
        rows.add(modBoolRow("Chroma RGB", "Smooth rainbow color cycling", m.id, "chroma"));
        rows.add(colorRow("Text Color", "mod:" + m.id));
        rows.add(sliderRow("X Position", "Horizontal screen placement (0-95%)", "mod_x:" + m.id, 0.0, 0.95, 0.005, false));
        rows.add(sliderRow("Y Position", "Vertical screen placement (0-95%)", "mod_y:" + m.id, 0.0, 0.95, 0.005, false));
    }

    private interface SettingRow {
        int height();
        boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg);
    }

    private SettingRow boolRow(final String label, final String desc, final String optId, final boolean def) {
        return new SettingRow() {
            @Override public int height() { return 38; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                boolean val = ClientConfig.get().getBool(optId, def);
                if (!click) {
                    g.drawGlassCard(x, y, w, 38, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 12, y + 21, ClientState.UI_TEXT_DIM, false);
                }
                if (drawPill(g, "opt_" + optId, x + w - 54, y + 11, 42, 16, val, accent, accentFg, mx, my, click)) {
                    ClientConfig.get().setOption(optId, Boolean.valueOf(!val));
                    return true;
                }
                return false;
            }
        };
    }

    private SettingRow modBoolRow(final String label, final String desc, final String modId, final String field) {
        return new SettingRow() {
            @Override public int height() { return 38; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                ClientConfig.HudModule m = ClientConfig.get().hudModules.get(modId);
                if (m == null) return false;
                boolean val = "bg".equals(field) ? m.bg : "border".equals(field) ? m.border : "chroma".equals(field) ? m.chroma : m.shadow;
                if (!click) {
                    g.drawGlassCard(x, y, w, 38, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 12, y + 21, ClientState.UI_TEXT_DIM, false);
                }
                if (drawPill(g, "mb_" + modId + "_" + field, x + w - 54, y + 11, 42, 16, val, accent, accentFg, mx, my, click)) {
                    if ("bg".equals(field)) m.bg = !m.bg;
                    else if ("border".equals(field)) m.border = !m.border;
                    else if ("chroma".equals(field)) m.chroma = !m.chroma;
                    else m.shadow = !m.shadow;
                    ClientConfig.get().save();
                    return true;
                }
                return false;
            }
        };
    }

    private SettingRow cycleRow(final String label, final String desc, final String optId, final String[] values) {
        return new SettingRow() {
            @Override public int height() { return 38; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                String cur = ClientConfig.get().getString(optId, values[0]);
                if (!click) {
                    g.drawGlassCard(x, y, w, 38, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 12, y + 21, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, "cyc_" + optId, x + w - 102, y + 9, 90, 20, 7, cur.toUpperCase(), ClientState.UI_INPUT, ClientState.UI_TEXT, false, mx, my, click)) {
                    int next = 0;
                    for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(cur)) next = (i + 1) % values.length;
                    ClientConfig.get().setOption(optId, values[next]);
                    return true;
                }
                return false;
            }
        };
    }

    private SettingRow sliderRow(final String label, final String desc, final String sliderKey, final double min, final double max, final double step, final boolean isInt) {
        return new SettingRow() {
            @Override public int height() { return 46; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                double val = readSliderValue(sliderKey);
                double pct = Math.max(0.0, Math.min(1.0, (val - min) / Math.max(0.0001, max - min)));
                int trackX = x + 12, trackY = y + 33, trackW = w - 24;
                if (!click) {
                    g.drawGlassCard(x, y, w, 46, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false);
                    String valStr = isInt ? (Math.round(val) + ("mod_rad:".equals(sliderKey.length() > 8 ? sliderKey.substring(0, 8) : "") ? "px" : ""))
                        : (sliderKey.startsWith("mod_x:") || sliderKey.startsWith("mod_y:") || sliderKey.startsWith("mod_opacity:")
                            ? (Math.round(val * 100) + "%") : String.format("%.2f", Double.valueOf(val)));
                    g.text(valStr, x + w - g.textWidth(valStr) - 12, y + 8, accent, false);
                    g.text(desc, x + 12, y + 19, ClientState.UI_TEXT_DIM, false);

                    g.drawRoundedRect(trackX, trackY, trackW, 4, 2, ClientState.UI_BORDER);
                    int fillW = (int) Math.round(trackW * pct);
                    if (fillW > 2) g.drawRoundedRect(trackX, trackY, fillW, 4, 2, accent);
                    g.drawCircleFilled(trackX + fillW, trackY + 2, 5, ClientState.UI_TEXT);
                } else if (mx >= trackX - 5 && mx <= trackX + trackW + 5 && my >= trackY - 7 && my <= trackY + 11) {
                    activeSliderId = sliderKey; sliderX = trackX; sliderW = trackW; sliderMin = min; sliderMax = max; sliderStep = step;
                    applySliderValue(sliderKey, mx);
                    return true;
                }
                return false;
            }
        };
    }

    private SettingRow colorRow(final String label, final String target) {
        return new SettingRow() {
            @Override public int height() { return 64; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                int curArgb = readColorTarget(target);
                String hex = ClientState.toHexColor(curArgb);
                if (!click) {
                    g.drawGlassCard(x, y, w, 64, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label + "  " + hex.toUpperCase(), x + 12, y + 8, ClientState.UI_TEXT, false);
                    g.drawCircleFilled(x + w - 18, y + 12, 6, curArgb);
                }
                int sx = x + 18, sy = y + 29;
                for (int i = 0; i < SWATCHES.length; i++) {
                    int swCol = ClientState.parseHexColor(SWATCHES[i], 0xFFFFFFFF);
                    boolean sel = SWATCHES[i].equalsIgnoreCase(hex);
                    if (!click) {
                        if (sel) g.drawCircleFilled(sx, sy, 8, accent);
                        g.drawCircleFilled(sx, sy, sel ? 6 : 6, swCol);
                    } else if (mx >= sx - 7 && mx <= sx + 7 && my >= sy - 7 && my <= sy + 7) {
                        writeColorTarget(target, SWATCHES[i]);
                        return true;
                    }
                    sx += 20;
                }
                int rgbW = (w - 40) / 3;
                String[] chIds = {"rgb_r:" + target, "rgb_g:" + target, "rgb_b:" + target};
                int[] chCols = {0xFFEF4444, 0xFF10B981, 0xFF3B82F6};
                int[] chVals = {(curArgb >>> 16) & 0xFF, (curArgb >>> 8) & 0xFF, curArgb & 0xFF};
                for (int c = 0; c < 3; c++) {
                    int rx = x + 12 + c * (rgbW + 8), ry = y + 48;
                    if (!click) {
                        g.drawRoundedRect(rx, ry, rgbW, 4, 2, ClientState.UI_BORDER);
                        int fw = (int) Math.round(rgbW * (chVals[c] / 255.0));
                        if (fw > 2) g.drawRoundedRect(rx, ry, fw, 4, 2, chCols[c]);
                        g.drawCircleFilled(rx + fw, ry + 2, 4, ClientState.UI_TEXT);
                    } else if (mx >= rx - 4 && mx <= rx + rgbW + 4 && my >= ry - 6 && my <= ry + 10) {
                        activeSliderId = chIds[c]; sliderX = rx; sliderW = rgbW; sliderMin = 0; sliderMax = 255; sliderStep = 1;
                        applySliderValue(chIds[c], mx);
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private SettingRow xhairPreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 68; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get();
                int halfW = (w - 10) / 2;
                String baseStyle = cfg.getString("pvp_xhair_style", "classic");
                int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6)), gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2));
                int thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1))));
                int baseCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF);
                String tgtStyle = cfg.getString("pvp_xhair_target_style", "same");
                if ("same".equalsIgnoreCase(tgtStyle)) tgtStyle = baseStyle;
                int tgtCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);

                for (int i = 0; i < 2; i++) {
                    int bx = x + i * (halfW + 10);
                    g.drawGlassCard(bx, y, halfW, 68, 10, 0xCC161A22, ClientState.UI_BORDER, true);
                    g.text(i == 0 ? "DEFAULT" : "PLAYER IN VIEW", bx + 10, y + 8, ClientState.UI_TEXT_MUTED, false);
                    String st = i == 0 ? baseStyle : tgtStyle;
                    if (!"vanilla".equalsIgnoreCase(st)) SharedHudRenderer.drawCrosshairShape(g, bx + halfW / 2, y + 40, st, sz, gap, thick, i == 0 ? baseCol : tgtCol);
                    else g.text("Vanilla", bx + halfW / 2 - 16, y + 36, ClientState.UI_TEXT_DIM, false);
                }
                return false;
            }
        };
    }

    private SettingRow accentRow(final String label) {
        return new SettingRow() {
            @Override public int height() { return 52; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                String cur = ClientConfig.get().getString("ui_accent", "white");
                if (!click) {
                    g.drawGlassCard(x, y, w, 52, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false);
                }
                int bx = x + 20, by = y + 32;
                int[] cols = {0xFFFFFFFF, 0xFF22D3EE, 0xFF10B981, 0xFFA855F7, 0xFFF43F5E, 0xFFF59E0B, 0xFF84CC16};
                for (int i = 0; i < ACCENTS.length; i++) {
                    boolean sel = ACCENTS[i][0].equals(cur);
                    if (!click) {
                        if (sel) g.drawCircleFilled(bx, by, 10, 0x55FFFFFF);
                        g.drawCircleFilled(bx, by, 7, cols[i]);
                        if (sel) g.drawCircleFilled(bx, by, 2, 0xFF09090B);
                    } else if (mx >= bx - 9 && mx <= bx + 9 && my >= by - 9 && my <= by + 9) {
                        ClientConfig.get().setOption("ui_accent", ACCENTS[i][0]);
                        return true;
                    }
                    bx += 26;
                }
                return false;
            }
        };
    }

    private SettingRow actionRow(final String label, final String desc, final String actId) {
        return new SettingRow() {
            @Override public int height() { return 40; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                if (!click) {
                    g.drawGlassCard(x, y, w, 40, 10, ClientState.UI_CARD, ClientState.UI_BORDER, true);
                    g.text(label, x + 12, y + 9, ClientState.UI_TEXT, false);
                    g.text(desc, x + 12, y + 22, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, "act_" + actId, x + w - 78, y + 10, 66, 20, 7, "Reset", 0xCC451A1E, ClientState.UI_TEXT, false, mx, my, click)) {
                    if ("reset_layout".equals(actId)) ClientConfig.get().resetLayout();
                    else if ("reset_all".equals(actId)) ClientConfig.get().resetAll();
                    return true;
                }
                return false;
            }
        };
    }

    private double readSliderValue(String key) {
        ClientConfig cfg = ClientConfig.get();
        if ("xhair_size".equals(key)) return cfg.getDouble("pvp_xhair_size", 6);
        if ("xhair_gap".equals(key)) return cfg.getDouble("pvp_xhair_gap", 2);
        if ("xhair_thick".equals(key)) return cfg.getDouble("pvp_xhair_thick", 1);
        if ("xhair_range".equals(key)) return cfg.getDouble("pvp_xhair_target_range", 12);
        if ("zoom_fac".equals(key)) return cfg.getDouble("cam_zoom_fac", 4);
        if ("fire_offset".equals(key)) return cfg.getDouble("pvp_low_fire_offset", -0.32);
        if (key.startsWith("mod_")) {
            ClientConfig.HudModule m = cfg.hudModules.get(key.substring(key.indexOf(':') + 1));
            if (m == null) return 0;
            if (key.startsWith("mod_scale:")) return m.scale;
            if (key.startsWith("mod_rad:")) return m.borderRadius;
            if (key.startsWith("mod_opacity:")) return m.opacity;
            if (key.startsWith("mod_x:")) return m.x;
            if (key.startsWith("mod_y:")) return m.y;
        }
        return 0;
    }

    private void applySliderValue(String key, int mx) {
        double pct = Math.max(0.0, Math.min(1.0, (mx - sliderX) / (double) Math.max(1, sliderW)));
        double raw = sliderMin + pct * (sliderMax - sliderMin);
        double snapped = Math.max(sliderMin, Math.min(sliderMax, Math.round(raw / sliderStep) * sliderStep));
        ClientConfig cfg = ClientConfig.get();
        if ("xhair_size".equals(key)) cfg.setOption("pvp_xhair_size", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_gap".equals(key)) cfg.setOption("pvp_xhair_gap", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_thick".equals(key)) cfg.setOption("pvp_xhair_thick", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_range".equals(key)) cfg.setOption("pvp_xhair_target_range", Integer.valueOf((int) Math.round(snapped)));
        else if ("zoom_fac".equals(key)) cfg.setOption("cam_zoom_fac", Integer.valueOf((int) Math.round(snapped)));
        else if ("fire_offset".equals(key)) cfg.setOption("pvp_low_fire_offset", Double.valueOf(Math.round(snapped * 100.0) / 100.0));
        else if (key.startsWith("mod_")) {
            ClientConfig.HudModule m = cfg.hudModules.get(key.substring(key.indexOf(':') + 1));
            if (m != null) {
                if (key.startsWith("mod_scale:")) m.scale = snapped;
                else if (key.startsWith("mod_rad:")) m.borderRadius = (int) Math.round(snapped);
                else if (key.startsWith("mod_opacity:")) m.opacity = snapped;
                else if (key.startsWith("mod_x:")) m.x = snapped;
                else if (key.startsWith("mod_y:")) m.y = snapped;
            }
        } else if (key.startsWith("rgb_")) {
            String target = key.substring(6);
            int cur = readColorTarget(target), r = (cur >>> 16) & 0xFF, g = (cur >>> 8) & 0xFF, b = cur & 0xFF, val = (int) Math.round(snapped);
            if (key.startsWith("rgb_r:")) r = val; else if (key.startsWith("rgb_g:")) g = val; else b = val;
            writeColorTarget(target, String.format("#%02x%02x%02x", Integer.valueOf(r), Integer.valueOf(g), Integer.valueOf(b)));
        }
    }

    private int readColorTarget(String target) {
        if (target.startsWith("opt:")) return ClientState.parseHexColor(ClientConfig.get().getString(target.substring(4), "#ffffff"), 0xFFFFFFFF);
        ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(4));
        return m != null ? ClientState.parseHexColor(m.color, 0xFFFFFFFF) : 0xFFFFFFFF;
    }

    private void writeColorTarget(String target, String hex) {
        if (target.startsWith("opt:")) ClientConfig.get().setOption(target.substring(4), hex);
        else { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(4)); if (m != null) { m.color = hex; m.chroma = false; ClientConfig.get().save(); } }
    }

    private boolean isModEnabled(String id) {
        ClientConfig cfg = ClientConfig.get();
        if ("crosshair".equals(id)) return !"vanilla".equalsIgnoreCase(cfg.getString("pvp_xhair_style", "classic"));
        if ("zoom".equals(id)) return cfg.getBool("cam_zoom", true);
        if ("low_fire".equals(id)) return cfg.getBool("pvp_low_fire", true);
        ClientConfig.HudModule m = cfg.hudModules.get(id);
        return m != null && m.enabled;
    }

    private void toggleMod(String id) {
        ClientConfig cfg = ClientConfig.get();
        if ("crosshair".equals(id)) cfg.setOption("pvp_xhair_style", "vanilla".equalsIgnoreCase(cfg.getString("pvp_xhair_style", "classic")) ? "classic" : "vanilla");
        else if ("zoom".equals(id)) cfg.setOption("cam_zoom", Boolean.valueOf(!cfg.getBool("cam_zoom", true)));
        else if ("low_fire".equals(id)) cfg.setOption("pvp_low_fire", Boolean.valueOf(!cfg.getBool("pvp_low_fire", true)));
        else { ClientConfig.HudModule m = cfg.hudModules.get(id); if (m != null) cfg.toggleModule(m); }
    }

    private String getModTitle(String id) { for (ModEntry m : ALL_MODS) if (m.id.equals(id)) return m.name; return "Mod Settings"; }
    private String getMenuTitle(String id) { return "hud".equals(id) ? "HUD Modules" : "pvp".equals(id) ? "PvP & Crosshair" : "camera".equals(id) ? "Camera & World" : "All Modules"; }
    private String trimToWidth(UiBridge.UiDraw g, String s, int maxW) { if (g.textWidth(s) <= maxW) return s; while (s.length() > 3 && g.textWidth(s + "..") > maxW) s = s.substring(0, s.length() - 1); return s + ".."; }

    private void drawScrollbar(UiBridge.UiDraw g, int x, int y, int viewH, int totalH, int curScroll, int accent) {
        g.drawRoundedRect(x, y, 4, viewH, 2, ClientState.UI_BORDER);
        int thumbH = Math.max(20, (viewH * viewH) / Math.max(1, totalH));
        int thumbY = y + (int) Math.round(((viewH - thumbH) * (double) curScroll) / Math.max(1, maxScrollPx));
        g.drawRoundedRect(x, thumbY, 4, thumbH, 2, ClientState.withAlpha(accent, 195));
    }

    private boolean drawBtn(UiBridge.UiDraw g, String id, int x, int y, int w, int h, int rad, String txt, int bg, int fg, boolean solidAccent, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            float ht = animHover(id, hov);
            int fillCol = ClientState.lerpColor(bg, ClientState.brightenArgb(bg, solidAccent ? -20 : 18), ht);
            int brdCol = solidAccent ? bg : ClientState.lerpColor(ClientState.UI_BORDER, ClientState.UI_BORDER_HOV, ht);
            g.drawGlassCard(x, y, w, h, rad, fillCol, brdCol, !solidAccent);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        return hov && click;
    }

    private boolean drawPill(UiBridge.UiDraw g, String id, int x, int y, int w, int h, boolean on, int accent, int accentFg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            float t = animPill(id, on);
            int trackCol = ClientState.lerpColor(ClientState.UI_INPUT, accent, t);
            int brdCol = ClientState.lerpColor(ClientState.UI_BORDER_HOV, accent, t);
            g.drawRoundedRect(x, y, w, h, h / 2, trackCol);
            g.drawRoundedOutline(x, y, w, h, h / 2, brdCol);
            int r = (h - 4) / 2;
            int minKx = x + 3 + r, maxKx = x + w - 3 - r;
            int kx = Math.round(minKx + (maxKx - minKx) * t);
            int knobCol = ClientState.lerpColor(ClientState.UI_TEXT_MUTED, accentFg, t);
            g.drawCircleFilled(kx, y + h / 2, r, knobCol);
        }
        return hov && click;
    }
}