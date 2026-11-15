package dev.aetheria.client.common;

import java.util.ArrayList;
import java.util.List;

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
        {"white", "Obsidian White"}, {"cyan", "Ice Cyan"}, {"emerald", "Emerald"},
        {"amethyst", "Amethyst"}, {"crimson", "Crimson"}, {"amber", "Amber Gold"}, {"lime", "Moss Lime"}
    };

    private String activeMenu = "all";
    private String editingModId = null;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private int scrollPx = 0;
    private int maxScrollPx = 0;

    private String activeSliderId = null;
    private int sliderX, sliderW;
    private double sliderMin, sliderMax, sliderStep;

    public SharedModMenu() {}
    public SharedModMenu(String initialModId) {
        this.editingModId = initialModId;
    }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        handleFrame(g, sw, sh, mx, my, false, null);
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
        if (activeSliderId != null) {
            activeSliderId = null;
            ClientConfig.get().save();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double scrollY) {
        if (scrollY == 0) return false;
        scrollPx = Math.max(0, Math.min(maxScrollPx, scrollPx - (int) Math.round(scrollY * 26.0)));
        return true;
    }

    public boolean keyPressed(boolean isBackspace, boolean isEscape) {
        if (searchFocused) {
            if (isBackspace && searchQuery.length() > 0) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollPx = 0;
                return true;
            }
            if (isEscape) {
                searchFocused = false;
                searchQuery = "";
                scrollPx = 0;
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char ch) {
        if (!searchFocused) return false;
        if (ch >= 32 && ch != 127 && searchQuery.length() < 32) {
            searchQuery += ch;
            scrollPx = 0;
            return true;
        }
        return false;
    }

    private boolean handleFrame(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions) {
        int accent = ClientState.getAccentColor();
        int accentFg = ClientState.getAccentFg();
        int panelW = Math.min(680, sw - 24);
        int panelH = Math.min(410, sh - 24);
        int px = (sw - panelW) / 2;
        int py = (sh - panelH) / 2;
        int sideW = panelW < 500 ? 108 : 138;
        int topH = 40;
        int contentX = px + sideW + 10;
        int contentY = py + topH + 8;
        int contentW = panelW - sideW - 18;
        int contentH = panelH - topH - 16;

        if (!click) {
            g.fill(0, 0, sw, sh, ClientState.UI_BACKDROP);
            g.fill(px, py, px + panelW, py + panelH, ClientState.UI_PANEL);
            g.fill(px, py, px + sideW, py + panelH, ClientState.UI_SIDEBAR);
            g.fill(px + sideW, py, px + sideW + 1, py + panelH, ClientState.UI_BORDER);
            g.fill(px + sideW + 1, py + topH, px + panelW, py + topH + 1, ClientState.UI_BORDER);
            g.drawBorder(px, py, panelW, panelH, ClientState.UI_BORDER_HOV);

            g.text("AETHERIA", px + 12, py + 11, ClientState.UI_TEXT, true);
            g.fill(px + 12, py + 23, px + 56, py + 33, ClientState.withAlpha(accent, 32));
            g.drawBorder(px + 12, py + 23, 44, 10, ClientState.UI_BORDER);
            g.text("CLIENT", px + 17, py + 24, ClientState.UI_TEXT_MUTED, false);
        }

        String[][] nav = {
            {"all", "All Mods"}, {"hud", "HUD"}, {"pvp", "PvP & Xhair"},
            {"camera", "Camera"}, {"settings", "Settings"}
        };
        int ny = py + 48;
        for (int i = 0; i < nav.length; i++) {
            String id = nav[i][0], lbl = nav[i][1];
            boolean sel = (editingModId == null) && activeMenu.equals(id);
            boolean hov = mx >= px + 8 && mx <= px + sideW - 8 && my >= ny && my <= ny + 24;
            if (!click) {
                if (sel) {
                    g.fill(px + 8, ny, px + sideW - 8, ny + 24, ClientState.withAlpha(accent, 28));
                    g.drawBorder(px + 8, ny, sideW - 16, 24, accent);
                    g.fill(px + 8, ny + 4, px + 10, ny + 20, accent);
                } else if (hov) {
                    g.fill(px + 8, ny, px + sideW - 8, ny + 24, ClientState.UI_CARD_HOVER);
                    g.drawBorder(px + 8, ny, sideW - 16, 24, ClientState.UI_BORDER);
                }
                g.text(lbl, px + 16, ny + 8, sel ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false);
            } else if (hov) {
                activeMenu = id;
                editingModId = null;
                searchFocused = false;
                scrollPx = 0;
                return true;
            }
            ny += 28;
        }

        int ehY = py + panelH - 36;
        if (drawBtn(g, px + 8, ehY, sideW - 16, 26, "Edit HUD", accent, accentFg, true, mx, my, click)) {
            if (actions != null) actions.openHudEditor();
            return true;
        }

        String headerTitle = editingModId != null ? getModTitle(editingModId) : getMenuTitle(activeMenu);
        if (!click) {
            g.text(headerTitle, contentX, py + 15, ClientState.UI_TEXT, true);
        }

        int closeW = 84;
        int closeX = px + panelW - closeW - 10;
        if (drawBtn(g, closeX, py + 9, closeW, 22, "Save & Close", ClientState.UI_CARD, ClientState.UI_TEXT, false, mx, my, click)) {
            ClientConfig.get().save();
            if (actions != null) actions.closeMenu();
            return true;
        }

        if (editingModId == null && !"settings".equals(activeMenu)) {
            int sbW = Math.min(165, Math.max(90, closeX - (contentX + g.textWidth(headerTitle) + 18)));
            int sbX = closeX - sbW - 8;
            int sbY = py + 9;
            boolean sHov = mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + 22;
            if (!click) {
                g.fill(sbX, sbY, sbX + sbW, sbY + 22, ClientState.UI_INPUT);
                g.drawBorder(sbX, sbY, sbW, 22, searchFocused ? accent : (sHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
                String disp = searchQuery.isEmpty() ? (searchFocused ? "_" : "Search...") : (searchQuery + (searchFocused ? "_" : ""));
                g.text(disp, sbX + 7, sbY + 7, searchQuery.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
                if (!searchQuery.isEmpty()) g.text("x", sbX + sbW - 13, sbY + 7, ClientState.UI_TEXT_MUTED, false);
            } else if (sHov) {
                if (!searchQuery.isEmpty() && mx >= sbX + sbW - 18) {
                    searchQuery = "";
                    scrollPx = 0;
                } else {
                    searchFocused = true;
                }
                return true;
            } else {
                searchFocused = false;
            }
        } else if (click) {
            searchFocused = false;
        }

        if (editingModId != null) {
            return renderModSettingsPage(g, contentX, contentY, contentW, contentH, mx, my, click, accent, accentFg);
        }
        if ("settings".equals(activeMenu)) {
            return renderClientSettingsPage(g, contentX, contentY, contentW, contentH, mx, my, click, accent, accentFg);
        }
        return renderModCardGrid(g, contentX, contentY, contentW, contentH, mx, my, click, accent, accentFg);
    }

    private boolean renderModCardGrid(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg) {
        List<ModEntry> list = new ArrayList<ModEntry>();
        String q = searchQuery.trim().toLowerCase();
        for (ModEntry m : ALL_MODS) {
            if (!"all".equals(activeMenu) && !m.category.equals(activeMenu)) continue;
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.desc.toLowerCase().contains(q)) continue;
            list.add(m);
        }

        int cols = cw >= 390 ? 2 : 1;
        int gap = 8;
        int trackW = 6;
        int cardW = (cw - trackW - gap * (cols - 1) - 4) / cols;
        int cardH = 58;
        int rows = (int) Math.ceil(list.size() / (double) cols);
        int totalH = Math.max(0, rows * (cardH + gap) - gap);
        maxScrollPx = Math.max(0, totalH - ch);
        if (scrollPx > maxScrollPx) scrollPx = maxScrollPx;

        if (!click && list.isEmpty()) {
            g.text("No matching mods found.", cx + 10, cy + 16, ClientState.UI_TEXT_MUTED, false);
        }

        for (int i = 0; i < list.size(); i++) {
            ModEntry m = list.get(i);
            int r = i / cols, c = i % cols;
            int bx = cx + c * (cardW + gap);
            int by = cy + r * (cardH + gap) - scrollPx;
            if (by + cardH < cy || by > cy + ch) continue;

            int topVis = Math.max(cy, by);
            int botVis = Math.min(cy + ch, by + cardH);
            if (botVis - topVis < 14) continue;

            boolean enabled = isModEnabled(m.id);
            boolean hov = mx >= bx && mx <= bx + cardW && my >= topVis && my <= botVis;

            if (!click) {
                g.fill(bx, topVis, bx + cardW, botVis, hov ? ClientState.UI_CARD_HOVER : ClientState.UI_CARD);
                g.drawBorder(bx, topVis, cardW, botVis - topVis, enabled ? ClientState.withAlpha(accent, 170) : (hov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
                if (by >= cy) {
                    g.fill(bx + 1, by + 1, bx + cardW - 1, by + 3, enabled ? accent : ClientState.UI_BORDER);
                }
                if (by + 9 >= cy && by + 18 <= cy + ch) {
                    g.text(m.name, bx + 10, by + 9, ClientState.UI_TEXT, true);
                }
                if (by + 21 >= cy && by + 30 <= cy + ch) {
                    g.text(trimToWidth(g, m.desc, cardW - 20), bx + 10, by + 21, ClientState.UI_TEXT_MUTED, false);
                }
            }

            if (by + 34 >= cy && by + 52 <= cy + ch) {
                int cfgW = 58, togW = 46;
                if (drawBtn(g, bx + 10, by + 34, cfgW, 17, "Settings", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    editingModId = m.id;
                    scrollPx = 0;
                    return true;
                }
                if (drawPill(g, bx + cardW - togW - 10, by + 35, togW, 15, enabled, accent, accentFg, mx, my, click)) {
                    toggleMod(m.id);
                    return true;
                }
            }
        }

        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, accent);
        return false;
    }

    private boolean renderModSettingsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg) {
        ClientConfig cfg = ClientConfig.get();
        boolean isHud = cfg.hudModules.containsKey(editingModId);
        boolean enabled = isModEnabled(editingModId);

        if (drawBtn(g, cx, cy, 56, 20, "< Back", ClientState.UI_CARD, ClientState.UI_TEXT, false, mx, my, click)) {
            editingModId = null;
            scrollPx = 0;
            return true;
        }
        if (isHud && drawBtn(g, cx + 62, cy, 82, 20, "Reset Style", ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
            cfg.resetModule(editingModId);
            return true;
        }
        if (drawPill(g, cx + cw - 52, cy + 2, 52, 16, enabled, accent, accentFg, mx, my, click)) {
            toggleMod(editingModId);
            return true;
        }

        int listY = cy + 26;
        int listH = ch - 26;
        int rw = cw - 10;

        List<SettingRow> rows = new ArrayList<SettingRow>();
        if ("crosshair".equals(editingModId)) buildCrosshairRows(rows);
        else if ("zoom".equals(editingModId)) buildZoomRows(rows);
        else if ("low_fire".equals(editingModId)) buildLowFireRows(rows);
        else if (isHud) buildHudModuleRows(rows, cfg.hudModules.get(editingModId));

        int totalH = 0;
        for (int i = 0; i < rows.size(); i++) totalH += rows.get(i).height() + 6;
        maxScrollPx = Math.max(0, totalH - listH);
        if (scrollPx > maxScrollPx) scrollPx = maxScrollPx;

        int ry = listY - scrollPx;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int rh = row.height();
            if (ry >= listY - 2 && ry + rh <= listY + listH + 2) {
                if (row.renderOrHandle(g, cx, ry, rw, mx, my, click, accent, accentFg)) return true;
            }
            ry += rh + 6;
        }

        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, accent);
        return false;
    }

    private boolean renderClientSettingsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg) {
        List<SettingRow> rows = new ArrayList<SettingRow>();
        rows.add(cycleRow("Menu Keybind", "In-game key to open Aetheria Client", "ctl_menu_key", MENU_KEYS));
        rows.add(accentRow("Launcher Accent Theme"));
        rows.add(boolRow("HUD Text Drop Shadow", "Global shadow behind all HUD modules", "hud_shadow", true));
        rows.add(boolRow("HUD Editor Snap Grid", "Snaps modules to a 40-step grid when dragging", "hud_snap_grid", false));
        rows.add(actionRow("Reset All HUD Positions", "Restores default positions, scales & colors", "reset_layout"));
        rows.add(actionRow("Reset All Client Settings", "Restores all options and layout to defaults", "reset_all"));

        int totalH = 0;
        for (int i = 0; i < rows.size(); i++) totalH += rows.get(i).height() + 6;
        maxScrollPx = Math.max(0, totalH - ch);
        if (scrollPx > maxScrollPx) scrollPx = maxScrollPx;

        int ry = cy - scrollPx;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int rh = row.height();
            if (ry >= cy - 2 && ry + rh <= cy + ch + 2) {
                if (row.renderOrHandle(g, cx, ry, cw - 10, mx, my, click, accent, accentFg)) return true;
            }
            ry += rh + 6;
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, accent);
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
        rows.add(modBoolRow("Background Box", "Draw dark Obsidian backdrop behind text", m.id, "bg"));
        rows.add(sliderRow("Background Opacity", "Transparency of module backdrop", "mod_opacity:" + m.id, 0.0, 1.0, 0.05, false));
        rows.add(modBoolRow("Border", "Draw thin 1px border around module", m.id, "border"));
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
            @Override public int height() { return 34; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                boolean val = ClientConfig.get().getBool(optId, def);
                if (!click) {
                    g.fill(x, y, x + w, y + 34, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 34, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 7, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 19, ClientState.UI_TEXT_DIM, false);
                }
                if (drawPill(g, x + w - 54, y + 9, 44, 15, val, accent, accentFg, mx, my, click)) {
                    ClientConfig.get().setOption(optId, Boolean.valueOf(!val));
                    return true;
                }
                return false;
            }
        };
    }

    private SettingRow modBoolRow(final String label, final String desc, final String modId, final String field) {
        return new SettingRow() {
            @Override public int height() { return 34; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                ClientConfig.HudModule m = ClientConfig.get().hudModules.get(modId);
                if (m == null) return false;
                boolean val = "bg".equals(field) ? m.bg : "border".equals(field) ? m.border : "chroma".equals(field) ? m.chroma : m.shadow;
                if (!click) {
                    g.fill(x, y, x + w, y + 34, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 34, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 7, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 19, ClientState.UI_TEXT_DIM, false);
                }
                if (drawPill(g, x + w - 54, y + 9, 44, 15, val, accent, accentFg, mx, my, click)) {
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
            @Override public int height() { return 34; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                String cur = ClientConfig.get().getString(optId, values[0]);
                if (!click) {
                    g.fill(x, y, x + w, y + 34, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 34, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 7, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 19, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, x + w - 102, y + 7, 92, 20, cur.toUpperCase(), ClientState.UI_INPUT, ClientState.UI_TEXT, false, mx, my, click)) {
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
            @Override public int height() { return 42; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                double val = readSliderValue(sliderKey);
                double pct = Math.max(0.0, Math.min(1.0, (val - min) / Math.max(0.0001, max - min)));
                int trackX = x + 10, trackY = y + 29, trackW = w - 20;
                if (!click) {
                    g.fill(x, y, x + w, y + 42, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 42, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 7, ClientState.UI_TEXT, false);
                    String valStr = isInt ? String.valueOf(Math.round(val))
                        : (sliderKey.startsWith("mod_x:") || sliderKey.startsWith("mod_y:") || sliderKey.startsWith("mod_opacity:")
                            ? (Math.round(val * 100) + "%") : String.format("%.2f", Double.valueOf(val)));
                    int vw = g.textWidth(valStr);
                    g.text(valStr, x + w - vw - 10, y + 7, accent, false);
                    g.text(desc, x + 10, y + 17, ClientState.UI_TEXT_DIM, false);

                    g.fill(trackX, trackY, trackX + trackW, trackY + 4, ClientState.UI_BORDER);
                    int fillW = (int) Math.round(trackW * pct);
                    g.fill(trackX, trackY, trackX + fillW, trackY + 4, accent);
                    int kx = trackX + fillW - 4;
                    g.fill(kx, trackY - 3, kx + 8, trackY + 7, ClientState.UI_TEXT);
                } else if (mx >= trackX - 4 && mx <= trackX + trackW + 4 && my >= trackY - 6 && my <= trackY + 10) {
                    activeSliderId = sliderKey;
                    sliderX = trackX; sliderW = trackW;
                    sliderMin = min; sliderMax = max; sliderStep = step;
                    applySliderValue(sliderKey, mx);
                    return true;
                }
                return false;
            }
        };
    }

    private SettingRow colorRow(final String label, final String target) {
        return new SettingRow() {
            @Override public int height() { return 58; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                int curArgb = readColorTarget(target);
                String hex = ClientState.toHexColor(curArgb);
                if (!click) {
                    g.fill(x, y, x + w, y + 58, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 58, ClientState.UI_BORDER);
                    g.text(label + " (" + hex.toUpperCase() + ")", x + 10, y + 7, ClientState.UI_TEXT, false);
                    g.fill(x + w - 26, y + 6, x + w - 10, y + 18, curArgb);
                    g.drawBorder(x + w - 26, y + 6, 16, 12, ClientState.UI_BORDER_HOV);
                }
                int sx = x + 10, sy = y + 21;
                for (int i = 0; i < SWATCHES.length; i++) {
                    int swCol = ClientState.parseHexColor(SWATCHES[i], 0xFFFFFFFF);
                    boolean sel = SWATCHES[i].equalsIgnoreCase(hex);
                    if (!click) {
                        g.fill(sx, sy, sx + 16, sy + 13, swCol);
                        g.drawBorder(sx, sy, 16, 13, sel ? accent : ClientState.UI_BORDER_HOV);
                    } else if (mx >= sx && mx <= sx + 16 && my >= sy && my <= sy + 13) {
                        writeColorTarget(target, SWATCHES[i]);
                        return true;
                    }
                    sx += 20;
                }
                int rgbW = (w - 36) / 3;
                String[] chIds = {"rgb_r:" + target, "rgb_g:" + target, "rgb_b:" + target};
                int[] chCols = {0xFFEF4444, 0xFF10B981, 0xFF3B82F6};
                int[] chVals = {(curArgb >>> 16) & 0xFF, (curArgb >>> 8) & 0xFF, curArgb & 0xFF};
                for (int c = 0; c < 3; c++) {
                    int rx = x + 10 + c * (rgbW + 8), ry = y + 43;
                    if (!click) {
                        g.fill(rx, ry, rx + rgbW, ry + 4, ClientState.UI_BORDER);
                        int fw = (int) Math.round(rgbW * (chVals[c] / 255.0));
                        g.fill(rx, ry, rx + fw, ry + 4, chCols[c]);
                        g.fill(rx + fw - 2, ry - 2, rx + fw + 3, ry + 6, ClientState.UI_TEXT);
                    } else if (mx >= rx - 2 && mx <= rx + rgbW + 2 && my >= ry - 5 && my <= ry + 9) {
                        activeSliderId = chIds[c];
                        sliderX = rx; sliderW = rgbW; sliderMin = 0; sliderMax = 255; sliderStep = 1;
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
            @Override public int height() { return 64; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get();
                int halfW = (w - 8) / 2;
                String baseStyle = cfg.getString("pvp_xhair_style", "classic");
                int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6));
                int gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2));
                int thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1))));
                int baseCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF);
                String tgtStyle = cfg.getString("pvp_xhair_target_style", "same");
                if ("same".equalsIgnoreCase(tgtStyle)) tgtStyle = baseStyle;
                int tgtCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);

                for (int i = 0; i < 2; i++) {
                    int bx = x + i * (halfW + 8);
                    g.fill(bx, y, bx + halfW, y + 64, 0xFF181C24);
                    g.drawBorder(bx, y, halfW, 64, ClientState.UI_BORDER);
                    g.text(i == 0 ? "DEFAULT" : "PLAYER IN VIEW", bx + 8, y + 6, ClientState.UI_TEXT_MUTED, false);
                    String st = i == 0 ? baseStyle : tgtStyle;
                    if (!"vanilla".equalsIgnoreCase(st)) {
                        SharedHudRenderer.drawCrosshairShape(g, bx + halfW / 2, y + 38, st, sz, gap, thick, i == 0 ? baseCol : tgtCol);
                    } else {
                        g.text("Vanilla", bx + halfW / 2 - 16, y + 34, ClientState.UI_TEXT_DIM, false);
                    }
                }
                return false;
            }
        };
    }

    private SettingRow accentRow(final String label) {
        return new SettingRow() {
            @Override public int height() { return 48; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                String cur = ClientConfig.get().getString("ui_accent", "white");
                if (!click) {
                    g.fill(x, y, x + w, y + 48, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 48, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 7, ClientState.UI_TEXT, false);
                }
                int bx = x + 10, by = y + 22;
                int[] cols = {0xFFFFFFFF, 0xFF22D3EE, 0xFF10B981, 0xFFA855F7, 0xFFF43F5E, 0xFFF59E0B, 0xFF84CC16};
                for (int i = 0; i < ACCENTS.length; i++) {
                    boolean sel = ACCENTS[i][0].equals(cur);
                    if (!click) {
                        g.fill(bx, by, bx + 20, by + 16, cols[i]);
                        g.drawBorder(bx, by, 20, 16, sel ? 0xFFFFFFFF : ClientState.UI_BORDER_HOV);
                        if (sel) g.fill(bx + 7, by + 5, bx + 13, by + 11, 0xFF09090B);
                    } else if (mx >= bx && mx <= bx + 20 && my >= by && my <= by + 16) {
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
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg) {
                if (!click) {
                    g.fill(x, y, x + w, y + 36, ClientState.UI_CARD);
                    g.drawBorder(x, y, w, 36, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 20, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, x + w - 76, y + 8, 66, 20, "Reset", 0xFF3A1A1E, ClientState.UI_TEXT, false, mx, my, click)) {
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
            if (key.startsWith("mod_opacity:")) return m.opacity;
            if (key.startsWith("mod_x:")) return m.x;
            if (key.startsWith("mod_y:")) return m.y;
        }
        return 0;
    }

    private void applySliderValue(String key, int mx) {
        double pct = Math.max(0.0, Math.min(1.0, (mx - sliderX) / (double) Math.max(1, sliderW)));
        double raw = sliderMin + pct * (sliderMax - sliderMin);
        double snapped = Math.round(raw / sliderStep) * sliderStep;
        snapped = Math.max(sliderMin, Math.min(sliderMax, snapped));
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
                else if (key.startsWith("mod_opacity:")) m.opacity = snapped;
                else if (key.startsWith("mod_x:")) m.x = snapped;
                else if (key.startsWith("mod_y:")) m.y = snapped;
            }
        } else if (key.startsWith("rgb_")) {
            String target = key.substring(6);
            int cur = readColorTarget(target);
            int r = (cur >>> 16) & 0xFF, g = (cur >>> 8) & 0xFF, b = cur & 0xFF;
            int val = (int) Math.round(snapped);
            if (key.startsWith("rgb_r:")) r = val;
            else if (key.startsWith("rgb_g:")) g = val;
            else b = val;
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
        else {
            ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(4));
            if (m != null) { m.color = hex; m.chroma = false; ClientConfig.get().save(); }
        }
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
        if ("crosshair".equals(id)) {
            String cur = cfg.getString("pvp_xhair_style", "classic");
            cfg.setOption("pvp_xhair_style", "vanilla".equalsIgnoreCase(cur) ? "classic" : "vanilla");
        } else if ("zoom".equals(id)) {
            cfg.setOption("cam_zoom", Boolean.valueOf(!cfg.getBool("cam_zoom", true)));
        } else if ("low_fire".equals(id)) {
            cfg.setOption("pvp_low_fire", Boolean.valueOf(!cfg.getBool("pvp_low_fire", true)));
        } else {
            ClientConfig.HudModule m = cfg.hudModules.get(id);
            if (m != null) cfg.toggleModule(m);
        }
    }

    private String getModTitle(String id) {
        for (ModEntry m : ALL_MODS) if (m.id.equals(id)) return m.name + " Settings";
        return "Mod Settings";
    }

    private String getMenuTitle(String id) {
        if ("hud".equals(id)) return "HUD Modules";
        if ("pvp".equals(id)) return "PvP & Crosshair";
        if ("camera".equals(id)) return "Camera & World";
        if ("settings".equals(id)) return "Client Settings";
        return "All Mods";
    }

    private String trimToWidth(UiBridge.UiDraw g, String s, int maxW) {
        if (g.textWidth(s) <= maxW) return s;
        while (s.length() > 3 && g.textWidth(s + "..") > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void drawScrollbar(UiBridge.UiDraw g, int x, int y, int viewH, int totalH, int accent) {
        g.fill(x, y, x + 3, y + viewH, ClientState.UI_BORDER);
        int thumbH = Math.max(18, (viewH * viewH) / Math.max(1, totalH));
        int thumbY = y + (int) Math.round(((viewH - thumbH) * (double) scrollPx) / Math.max(1, maxScrollPx));
        g.fill(x, thumbY, x + 3, thumbY + thumbH, ClientState.withAlpha(accent, 190));
    }

    private boolean drawBtn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, boolean solidAccent, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            int fillCol = hov ? ClientState.brightenArgb(bg, solidAccent ? -18 : 16) : bg;
            g.fill(x, y, x + w, y + h, fillCol);
            g.drawBorder(x, y, w, h, solidAccent ? bg : (hov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        return hov && click;
    }

    private boolean drawPill(UiBridge.UiDraw g, int x, int y, int w, int h, boolean on, int accent, int accentFg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            int trackCol = on ? accent : ClientState.UI_INPUT;
            g.fill(x, y, x + w, y + h, hov ? ClientState.brightenArgb(trackCol, on ? -15 : 15) : trackCol);
            g.drawBorder(x, y, w, h, on ? accent : ClientState.UI_BORDER_HOV);
            int knobW = h - 4;
            int kx = on ? (x + w - knobW - 2) : (x + 2);
            g.fill(kx, y + 2, kx + knobW, y + h - 2, on ? accentFg : ClientState.UI_TEXT_MUTED);
            String lbl = on ? "ON" : "OFF";
            int tx = on ? (x + 5) : (x + w - g.textWidth(lbl) - 5);
            g.text(lbl, tx, y + (h - 8) / 2, on ? accentFg : ClientState.UI_TEXT_MUTED, false);
        }
        return hov && click;
    }
}
