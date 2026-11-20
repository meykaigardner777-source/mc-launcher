package dev.aetheria.client.common;

import java.util.*;

public class SharedModMenu {
    public interface Actions {
        void openHudEditor();
        default void openTitleEditor() {}
        void closeMenu();
    }

    private static final class ModEntry {
        final String id, name, category, desc;
        ModEntry(String id, String name, String category, String desc) { this.id = id; this.name = name; this.category = category; this.desc = desc; }
    }

    private static final Set<String> REAL_HUDS = new HashSet<String>(Arrays.asList(
        "fps", "frametime_graph", "ram_usage", "cpu_load", "ping", "ping_graph", "server_tps", "packet_counter",
        "session", "total_playtime", "clock", "stopwatch", "countdown", "gc_monitor", "chunk_updates", "entity_counter",
        "cps", "cps_graph", "keystrokes", "mouse_visualizer", "combo", "reach", "hit_accuracy", "sprint_reset",
        "target_hud", "armor", "durability", "potions", "potion_count", "heal_count", "arrow_count", "totem_count",
        "kill_counter", "pearl_cd", "shield_cd", "attack_charge", "togglesprint", "speedometer",
        "coords", "chunk_coords", "compass", "biome", "light_level", "slime_chunk", "day_count", "moon_phase",
        "weather", "minimap", "waypoints", "spawn_dist", "death_loc", "y_alert", "saturation", "oxygen",
        "elytra_guide", "villager_timer", "waila_block", "waila_entity", "inv_space", "mini_inventory",
        "server_ip", "player_count", "bw_resources", "uhc_border", "scoreboard",
        "paperdoll", "zoom_indicator", "gamma_level", "fov_indicator", "watermark", "low_hp_vignette", "hotbar"
    ));

    private static final List<ModEntry> ALL_MODS = new ArrayList<ModEntry>();
    private static boolean initialized = false;

    private static void ensureMods() {
        if (initialized) return;
        initialized = true;
        ALL_MODS.add(new ModEntry("crosshair", "Crosshair", "utility", "Dynamic shapes, pixel editor & attack indicator"));
        ALL_MODS.add(new ModEntry("zoom", "Optical Zoom", "utility", "Scroll-wheel zoom, easing & Any-Key bind"));
        ALL_MODS.add(new ModEntry("freelook", "FreeLook 360", "utility", "Detached 360-degree camera & Any-Key bind"));
        ALL_MODS.add(new ModEntry("fullbright", "Fullbright", "utility", "Clean max brightness + quick toggle keybind"));
        ALL_MODS.add(new ModEntry("fov_mod", "FOV Modifier", "utility", "Disable dynamic FOV, sprint/bow zoom & intensity"));
        ALL_MODS.add(new ModEntry("hurtcam", "No Hurt Cam", "utility", "Disable or reduce damage camera shake"));
        ALL_MODS.add(new ModEntry("low_fire", "Low Fire", "utility", "Lower first-person flame height with POV preview"));
        ALL_MODS.add(new ModEntry("low_shield", "Low Shield", "pvp", "Shrink or lower first-person shield with preview"));
        ALL_MODS.add(new ModEntry("viewmodel", "Hand / Viewmodel", "visuals", "Adjust hand & item positions + Small Totem"));
        ALL_MODS.add(new ModEntry("hit_color", "Hit Colorizer", "pvp", "Custom damage tint & chroma flash on hit targets"));
        ALL_MODS.add(new ModEntry("hitboxes", "Custom Hitboxes", "pvp", "Customizable 3D hitboxes, eye line & live preview"));
        ALL_MODS.add(new ModEntry("block_break", "Block Break FX", "visuals", "5 custom 3D block breaking animations + preview"));
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            if (REAL_HUDS.contains(m.id)) ALL_MODS.add(new ModEntry(m.id, m.label, m.category, m.desc));
        }
    }

    private static final String[] SWATCHES = {"#ffffff","#ff3333","#09090b","#27272a","#a1a1aa","#22d3ee","#10b981","#a855f7","#f43f5e","#f59e0b"};
    private static final String[] WP_STYLES = {"lunar", "block", "dot", "beacon"};
    private static final String[] WP_NAME_PRESETS = {"Base", "Home", "Portal", "Farm", "Raid", "End", "Stronghold", "Village"};
    private static final String[] BB_STYLES = {"shrink", "expand", "rise", "slide_down", "pulse"};
    private static final String[] ACC_FILTER_TAGS = {"All", "Main", "PvP", "SMP", "Alt", "Test"};
    private static final String[] ANCHORS = {"top-left","top-center","top-right","mid-left","center","mid-right","bottom-left","bottom-center","bottom-right"};
    private static final String[] BORDER_STYLES = {"solid", "dashed", "gradient"};
    private static final String[] BRACKETS = {"none", "square", "round", "angle", "curly"};
    private static final String[] XHAIR_STYLES = {"vanilla", "classic", "dot", "circle", "t_shape", "arrows", "x_cross", "custom"};
    private static final String[] TARGET_STYLES = {"same", "classic", "dot", "circle", "t_shape", "arrows", "x_cross", "custom"};
    private static final String[] ATK_IND_MODES = {"bar", "dot", "none"};
    private static final String[] SAT_STYLES = {"appleskin", "bar", "text"};
    private static final String[] POT_STYLES = {"vanilla", "compact", "cards"};
    private static final String[] MENU_LAYOUTS = {"oneclient", "topbar", "sidebar", "grid", "palette"};
    private static final String[] SWITCH_STYLES = {"pill", "ios", "checkbox"};
    private static final String[] ANIM_CURVES = {"smooth", "snappy"};
    private static final String[] SPEED_UNITS = {"bps", "kmh", "mph"};
    private static final String[][] ACCENTS = {{"white","Obsidian"},{"crimson","Crimson"},{"cyan","Cyan"},{"emerald","Emerald"},{"amethyst","Amethyst"},{"amber","Amber"},{"lime","Lime"},{"custom","Custom"}};

    private String activeMenu = "all", editingModId = null, inspectorTab = "geom", searchQuery = "", listeningKeyOptId = null;
    private String accountInput = "", accTagFilter = "All", accStatusMsg = "", waypointNameInput = "", hoveredTooltip = null;
    private boolean searchFocused = false, accountInputFocused = false, waypointInputFocused = false, onlyEnabledFilter = false, wpShowAllWorlds = false;
    private boolean foldMods = false, foldPersonalize = false, editingTargetPixels = false, pixelMirrorX = true, pixelMirrorY = false;
    private final Set<String> foldedAccordions = new HashSet<String>();
    private float scrollPx = 0.0F, targetScrollPx = 0.0F;
    private int maxScrollPx = 0, sliderX, sliderW;
    private final long openedNs = System.nanoTime();
    private long lastFrameNs = System.nanoTime();
    private float frameDt = 0.016F;
    private final Map<String, Float> pillAnim = new HashMap<String, Float>(), hoverAnim = new HashMap<String, Float>();
    private String activeSliderId = null;
    private double sliderMin, sliderMax, sliderStep;
    private Actions activeActions = null;

    public SharedModMenu() { this(null, "all"); }
    public SharedModMenu(String initialModId) { this(initialModId, "all"); }
    public SharedModMenu(String initialModId, String initialTab) {
        ensureMods();
        this.editingModId = initialModId;
        if (initialTab != null && !initialTab.isEmpty()) this.activeMenu = initialTab;
    }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        ensureMods(); hoveredTooltip = null;
        long now = System.nanoTime(); frameDt = Math.min(0.05F, Math.max(0.001F, (now - lastFrameNs) / 1_000_000_000.0F)); lastFrameNs = now;
        ClientConfig cfg = ClientConfig.get(); boolean anim = cfg.getBool("ui_animations", true);
        float mult = (float) (Math.max(0.5, cfg.getDouble("ui_anim_speed", 1.0)) * ("snappy".equalsIgnoreCase(cfg.getString("ui_anim_curve", "smooth")) ? 1.5 : 1.0));
        if (anim) scrollPx += (targetScrollPx - scrollPx) * Math.min(1.0F, frameDt * 18.0F * mult); else scrollPx = targetScrollPx;
        float openT = anim ? Math.min(1.0F, ((now - openedNs) / 150_000_000.0F) * mult) : 1.0F;
        float ease = 1.0F - (float) Math.pow(1.0F - openT, 3), scale = 0.95F + 0.05F * ease;
        g.fill(0, 0, sw, sh, ClientState.scaleAlpha(0x99000000, ease));
        if (scale < 0.999F) { g.pushTransform((sw / 2.0F) * (1.0F - scale), (sh / 2.0F) * (1.0F - scale), scale); handleFrame(g, sw, sh, mx, my, false, null); g.popTransform(); }
        else handleFrame(g, sw, sh, mx, my, false, null);
        if (hoveredTooltip != null && !hoveredTooltip.isEmpty() && cfg.getBool("ui_tooltips", true)) {
            int tw = g.textWidth(hoveredTooltip) + 14, tx = Math.max(6, Math.min(sw - tw - 6, mx + 10)), ty = Math.max(6, Math.min(sh - 22, my + 14));
            g.drawGlassCard(tx, ty, tw, 16, 6, 0xF2121218, ClientState.UI_BORDER_HOV); g.text(hoveredTooltip, tx + 7, ty + 4, ClientState.UI_TEXT, false);
        }
    }

    public boolean mouseClicked(UiBridge.UiDraw g, int sw, int sh, double mx, double my, Actions actions) { this.activeActions = actions; return handleFrame(g, sw, sh, (int) mx, (int) my, true, actions); }
    public boolean mouseDragged(double mx) { if (activeSliderId == null) return false; applySliderValue(activeSliderId, (int) mx); return true; }
    public boolean mouseReleased() { if (activeSliderId != null) { activeSliderId = null; ClientConfig.get().save(); return true; } return false; }
    public boolean mouseScrolled(double scrollY) { if (scrollY == 0) return false; targetScrollPx = Math.max(0.0F, Math.min(maxScrollPx, targetScrollPx - (float) (scrollY * 36.0))); return true; }

    public boolean keyPressedCode(int keyCode) {
        if (listeningKeyOptId != null) {
            if (keyCode == 256) { listeningKeyOptId = null; return true; }
            if (keyCode == 259 || keyCode == 261) { ClientConfig.get().setOption(listeningKeyOptId, "NONE"); listeningKeyOptId = null; return true; }
            ClientConfig.get().setOption(listeningKeyOptId, ClientState.glfwKeyToName(keyCode)); listeningKeyOptId = null; return true;
        }
        if (waypointInputFocused && (keyCode == 257 || keyCode == 335)) {
            ClientConfig.get().addWaypointAt(waypointNameInput.trim(), ClientState.getPlayerX(), ClientState.getPlayerY(), ClientState.getPlayerZ(), ClientState.getPlayerDim(), ClientState.getCurrentWorldKey());
            waypointNameInput = ""; waypointInputFocused = false; return true;
        }
        if (accountInputFocused && (keyCode == 257 || keyCode == 335)) {
            if (!accountInput.trim().isEmpty()) { ClientConfig.get().addAccount(accountInput.trim(), "offline", "Alt"); accountInput = ""; accStatusMsg = "Saved Alt! Click Use to swap."; }
            return true;
        }
        return false;
    }

    public boolean keyPressed(boolean isBackspace, boolean isEscape) {
        if (listeningKeyOptId != null) { if (isEscape) { listeningKeyOptId = null; return true; } if (isBackspace) { ClientConfig.get().setOption(listeningKeyOptId, "NONE"); listeningKeyOptId = null; return true; } }
        if (waypointInputFocused) { if (isBackspace && waypointNameInput.length() > 0) { waypointNameInput = waypointNameInput.substring(0, waypointNameInput.length() - 1); return true; } if (isEscape) { waypointInputFocused = false; return true; } }
        if (accountInputFocused) { if (isBackspace && accountInput.length() > 0) { accountInput = accountInput.substring(0, accountInput.length() - 1); return true; } if (isEscape) { accountInputFocused = false; return true; } }
        if (searchFocused) { if (isBackspace && searchQuery.length() > 0) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); resetScroll(); return true; } if (isEscape) { searchFocused = false; searchQuery = ""; resetScroll(); return true; } }
        return false;
    }

    public boolean charTyped(char ch) {
        if (listeningKeyOptId != null) return false;
        if (waypointInputFocused) { if (ch >= 32 && ch != 127 && waypointNameInput.length() < 24) waypointNameInput += ch; return true; }
        if (accountInputFocused) { if (ch >= 33 && ch <= 126 && accountInput.length() < 16) accountInput += ch; return false; }
        if (searchFocused && ch >= 32 && ch != 127 && searchQuery.length() < 32) { searchQuery += ch; resetScroll(); return true; }
        return false;
    }

    private void resetScroll() { scrollPx = 0.0F; targetScrollPx = 0.0F; }
    private float animHover(String key, boolean hov) { Float p = hoverAnim.get(key); float cur = p != null ? p.floatValue() : 0.0F; cur = ClientConfig.get().getBool("ui_animations", true) ? (cur + ((hov ? 1.0F : 0.0F) - cur) * Math.min(1.0F, frameDt * 16.0F)) : (hov ? 1.0F : 0.0F); hoverAnim.put(key, Float.valueOf(cur)); return cur; }
    private float animPill(String key, boolean on) { Float p = pillAnim.get(key); float t = on ? 1.0F : 0.0F, cur = p != null ? p.floatValue() : t; cur = ClientConfig.get().getBool("ui_animations", true) ? (cur + (t - cur) * Math.min(1.0F, frameDt * 18.0F)) : t; pillAnim.put(key, Float.valueOf(cur)); return cur; }

    private boolean handleFrame(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, Actions actions) {
        ClientConfig cfg = ClientConfig.get();
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();
        double gRad = cfg.getDouble("global_hud_rounding", -1.0), gOpac = cfg.getDouble("global_hud_opacity", -1.0);
        int menuRad = gRad >= 0 ? (int) Math.round(gRad) : Math.max(0, Math.min(24, (int) Math.round(cfg.getDouble("ui_menu_rounding", 10.0))));
        int panelBg = ClientState.withAlpha(0xFF0D0D11, (int) Math.round(Math.max(0.45, Math.min(1.0, gOpac >= 0 ? Math.max(0.45, gOpac) : cfg.getDouble("ui_menu_opacity", 0.94))) * 255.0));
        String layout = cfg.getString("ui_menu_layout", "oneclient");
        boolean isOneClient = "oneclient".equalsIgnoreCase(layout);
        int panelW = Math.min(isOneClient ? 680 : 640, sw - 16), panelH = Math.min(isOneClient ? 410 : 390, sh - 16);
        int px = (sw - panelW) / 2, py = (sh - panelH) / 2;

        if (!click) g.drawGlassCard(px, py, panelW, panelH, menuRad, panelBg, ClientState.UI_BORDER);
        if (isOneClient) return renderOneClientFrame(g, px, py, panelW, panelH, mx, my, click, actions, accent, accentFg, menuRad);

        int topH = 38;
        if (!click) {
            g.drawRoundedRect(px + 1, py + 1, panelW - 2, topH - 1, Math.max(0, menuRad - 1), 0xFF14141A);
            g.fill(px + 1, py + topH, px + panelW - 1, py + topH + 1, ClientState.UI_BORDER);
            g.text("AETHERIA", px + 12, py + 15, ClientState.UI_TEXT, false);
            g.drawRoundedRect(px + 64, py + 11, 46, 16, Math.max(2, menuRad / 2), ClientState.withAlpha(accent, 45));
            g.text(cfg.activeProfile.toUpperCase(), px + 69, py + 15, accent, false);
        }
        int closeX = px + panelW - 28, closeY = py + 9;
        boolean closeHov = mx >= closeX && mx <= closeX + 20 && my >= closeY && my <= closeY + 20;
        if (!click) {
            if (closeHov) hoveredTooltip = "Close Menu (ESC)";
            int cBg = ClientState.lerpColor(0x00000000, ClientState.UI_DANGER, animHover("win_close", closeHov));
            if ((cBg >>> 24) > 4) g.drawRoundedRect(closeX, closeY, 20, 20, Math.max(2, menuRad / 2), cBg);
            g.text("x", closeX + 7, closeY + 6, closeHov ? 0xFFFFFFFF : ClientState.UI_TEXT_MUTED, false);
        } else if (closeHov) { g.playClickSound(); listeningKeyOptId = null; cfg.save(); if (actions != null) actions.closeMenu(); return true; }

        int ehW = 62, ehX = closeX - ehW - 6;
        if (drawBtn(g, "btn_edithud", ehX, py + 9, ehW, 20, Math.max(2, menuRad / 2), "Edit HUD", accent, accentFg, true, mx, my, click)) { listeningKeyOptId = null; if (actions != null) actions.openHudEditor(); return true; }
        int layW = 52, layX = ehX - layW - 5;
        if (drawBtn(g, "btn_lay", layX, py + 9, layW, 20, Math.max(2, menuRad / 2), layout.toUpperCase(), 0xFF1A1A22, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
            int next = 0; for (int i = 0; i < MENU_LAYOUTS.length; i++) if (MENU_LAYOUTS[i].equalsIgnoreCase(layout)) next = (i + 1) % MENU_LAYOUTS.length;
            cfg.setOption("ui_menu_layout", MENU_LAYOUTS[next]); return true;
        }
        String[][] cats = {{"all","All"},{"favorites","Fav"},{"pvp","PvP"},{"visuals","Vis"},{"world","World"},{"system","Sys"},{"utility","Util"},{"waypoints","WP"},{"accounts","Acc"},{"profiles","Prof"},{"ui_engine","UI"}};
        int cx = px + 12, cy = py + topH + 8, cw = panelW - 24, ch = panelH - topH - 16;
        if ("sidebar".equals(layout)) {
            int sideW = 86; if (!click) g.fill(px + sideW + 8, py + topH + 4, px + sideW + 9, py + panelH - 6, ClientState.UI_BORDER);
            for (int i = 0; i < cats.length; i++) {
                int by = py + topH + 8 + i * 24; if (by + 20 > py + panelH - 6) break;
                boolean sel = editingModId == null && activeMenu.equals(cats[i][0]);
                if (drawBtn(g, "sb_" + cats[i][0], px + 10, by, sideW - 6, 20, Math.max(2, menuRad / 2), cats[i][1], sel ? ClientState.withAlpha(accent, 45) : 0x00000000, sel ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    activeMenu = cats[i][0]; editingModId = null; listeningKeyOptId = null; searchFocused = false; accountInputFocused = false; waypointInputFocused = false; resetScroll(); return true;
                }
            }
            cx = px + sideW + 16; cw = panelW - sideW - 28;
        } else {
            int navX = px + 116, navRight = layX - 6;
            for (int i = 0; i < cats.length; i++) {
                String id = cats[i][0], lbl = cats[i][1]; int bw = g.textWidth(lbl) + 10; if (navX + bw > navRight) break;
                boolean sel = (editingModId == null) && activeMenu.equals(id), hov = mx >= navX && mx <= navX + bw && my >= py + 9 && my <= py + 29;
                if (!click) {
                    float ht = animHover("nav_" + id, hov);
                    int bg = sel ? ClientState.withAlpha(accent, 40) : ClientState.lerpColor(0x00000000, 0x22FFFFFF, ht), brd = sel ? accent : ClientState.lerpColor(0x00000000, ClientState.UI_BORDER, ht);
                    if ((bg >>> 24) > 4 || (brd >>> 24) > 4) g.drawGlassCard(navX, py + 9, bw, 20, Math.max(2, menuRad / 2), bg, brd);
                    g.text(lbl, navX + 5, py + 15, sel || hov ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false);
                } else if (hov) { g.playClickSound(); activeMenu = id; editingModId = null; listeningKeyOptId = null; searchFocused = false; accountInputFocused = false; waypointInputFocused = false; resetScroll(); return true; }
                navX += bw + 3;
            }
        }
        return renderActiveContent(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad, "palette".equals(layout));
    }

    private boolean renderOneClientFrame(UiBridge.UiDraw g, int px, int py, int panelW, int panelH, int mx, int my, boolean click, Actions actions, int accent, int accentFg, int menuRad) {
        int sideW = 142, sx = px + 8, topH = 38;
        if (!click) {
            g.drawRoundedRect(px + 1, py + 1, sideW, panelH - 2, Math.max(0, menuRad - 1), 0xEE0A0A0E);
            g.fill(px + sideW + 1, py + 1, px + sideW + 2, py + panelH - 1, ClientState.UI_BORDER);
            g.fill(px + sideW + 2, py + topH, px + panelW - 1, py + topH + 1, ClientState.UI_BORDER);
            g.text("AETHERIA", px + 14, py + 13, ClientState.UI_TEXT, false);
            g.drawRoundedRect(px + 72, py + 10, 38, 13, 4, ClientState.withAlpha(accent, 38));
            g.text(ClientConfig.get().activeProfile.toUpperCase(), px + 76, py + 13, accent, false);
        }
        int sy = py + 34, sbW = sideW - 14, accCardH = 36, sideMaxY = py + panelH - accCardH - 10;
        if (drawBtn(g, "1c_h_mods", sx, sy, sbW, 15, 4, (foldMods ? "> " : "v ") + "MODS", 0x00000000, ClientState.UI_TEXT_DIM, false, mx, my, click)) { foldMods = !foldMods; return true; }
        sy += 17;
        String[][] modCats = {{"all","All Mods"},{"favorites","Favorites"},{"pvp","PvP & Combat"},{"visuals","Visuals & HUD"},{"world","World & Map"},{"system","System & Perf"},{"server","Server"},{"utility","Utilities"}};
        if (!foldMods) {
            for (int i = 0; i < modCats.length; i++) {
                if (sy + 18 > sideMaxY) break;
                boolean sel = editingModId == null && activeMenu.equals(modCats[i][0]);
                if (drawSideItem(g, "1cm_" + modCats[i][0], sx, sy, sbW, 18, Math.max(3, menuRad / 2), modCats[i][1], sel, accent, mx, my, click)) {
                    activeMenu = modCats[i][0]; editingModId = null; listeningKeyOptId = null; searchFocused = false; accountInputFocused = false; waypointInputFocused = false; resetScroll(); return true;
                }
                sy += 19;
            }
            sy += 4;
        }
        if (sy + 16 <= sideMaxY) { if (drawBtn(g, "1c_h_pers", sx, sy, sbW, 15, 4, (foldPersonalize ? "> " : "v ") + "PERSONALIZE", 0x00000000, ClientState.UI_TEXT_DIM, false, mx, my, click)) { foldPersonalize = !foldPersonalize; return true; } sy += 17; }
        if (!foldPersonalize) {
            if (sy + 18 <= sideMaxY && drawSideItem(g, "1cp_hud", sx, sy, sbW, 18, Math.max(3, menuRad / 2), "Edit HUD Layout", false, accent, mx, my, click)) { listeningKeyOptId = null; if (actions != null) actions.openHudEditor(); return true; }
            sy += 19;
            String[][] pers = {{"waypoints","Waypoints"},{"profiles","HUD Profiles"},{"ui_engine","Themes & UI"},{"accounts","Accounts"}};
            for (int i = 0; i < pers.length; i++) {
                if (sy + 18 > sideMaxY) break;
                boolean sel = editingModId == null && activeMenu.equals(pers[i][0]);
                if (drawSideItem(g, "1cp_" + pers[i][0], sx, sy, sbW, 18, Math.max(3, menuRad / 2), pers[i][1], sel, accent, mx, my, click)) {
                    activeMenu = pers[i][0]; editingModId = null; listeningKeyOptId = null; searchFocused = false; accountInputFocused = false; waypointInputFocused = false; resetScroll(); return true;
                }
                sy += 19;
            }
        }
        int acX = px + 8, acY = py + panelH - accCardH - 6, acW = sideW - 14;
        boolean acSel = editingModId == null && "accounts".equals(activeMenu), acHov = mx >= acX && mx <= acX + acW && my >= acY && my <= acY + accCardH;
        String curUser = g.currentUsername();
        if (!click) {
            if (acHov) hoveredTooltip = "Open Account Organizer";
            g.drawGlassCard(acX, acY, acW, accCardH, Math.max(4, menuRad - 2), acSel ? 0xFF1A1B24 : (acHov ? 0xFF16161F : 0xFF121218), acSel ? accent : ClientState.UI_BORDER);
            g.drawRoundedRect(acX + 6, acY + 6, 24, 24, 5, ClientState.withAlpha(accent, 45));
            String ini = curUser.isEmpty() ? "P" : curUser.substring(0, 1).toUpperCase();
            g.text(ini, acX + 18 - g.textWidth(ini) / 2, acY + 14, accent, false);
            g.drawCircleFilled(acX + 27, acY + 27, 3, 0xFF10B981);
            g.text(trimToWidth(g, curUser, acW - 40), acX + 36, acY + 9, ClientState.UI_TEXT, false);
            g.text("Accounts >", acX + 36, acY + 21, ClientState.UI_TEXT_DIM, false);
        } else if (acHov) { g.playClickSound(); activeMenu = "accounts"; editingModId = null; listeningKeyOptId = null; searchFocused = false; accountInputFocused = false; waypointInputFocused = false; resetScroll(); return true; }

        int cx = px + sideW + 12, cw = panelW - sideW - 22, cy = py + topH + 8, ch = panelH - topH - 16;
        if (!click) {
            String crumb = editingModId != null ? ("Mods > " + getModTitle(editingModId)) : ("accounts".equals(activeMenu) ? "Personalize > Accounts" : ("waypoints".equals(activeMenu) ? "Personalize > Waypoints" : ("profiles".equals(activeMenu) ? "Personalize > Profiles" : ("ui_engine".equals(activeMenu) ? "Personalize > UI" : ("Mods > " + getMenuTitle(activeMenu))))));
            g.text(trimToWidth(g, crumb, cw - 230), cx, py + 15, ClientState.UI_TEXT, false);
        }
        int closeX = px + panelW - 26; boolean closeHov = mx >= closeX && mx <= closeX + 18 && my >= py + 10 && my <= py + 28;
        if (!click) { if (closeHov) { hoveredTooltip = "Close Menu (ESC)"; g.drawRoundedRect(closeX, py + 10, 18, 18, 4, ClientState.UI_DANGER); } g.text("x", closeX + 6, py + 15, closeHov ? 0xFFFFFFFF : ClientState.UI_TEXT_MUTED, false); }
        else if (closeHov) { g.playClickSound(); listeningKeyOptId = null; ClientConfig.get().save(); if (actions != null) actions.closeMenu(); return true; }

        int layX = closeX - 38;
        if (mx >= layX && mx <= layX + 34 && my >= py + 10 && my <= py + 28) hoveredTooltip = "Switch Menu Layout Mode";
        if (drawBtn(g, "1c_lay", layX, py + 10, 34, 18, 5, "1C", 0xFF191922, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { ClientConfig.get().setOption("ui_menu_layout", "topbar"); return true; }
        boolean showModTools = editingModId == null && !"accounts".equals(activeMenu) && !"waypoints".equals(activeMenu) && !"profiles".equals(activeMenu) && !"ui_engine".equals(activeMenu);
        if (showModTools) {
            int sbW2 = 122, sbX = layX - sbW2 - 5; boolean sHov = mx >= sbX && mx <= sbX + sbW2 && my >= py + 10 && my <= py + 28;
            if (!click) {
                g.drawGlassCard(sbX, py + 10, sbW2, 18, 5, ClientState.UI_INPUT, searchFocused ? accent : (sHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
                g.text(searchQuery.isEmpty() ? (searchFocused ? "_" : "Search...") : trimToWidth(g, searchQuery + (searchFocused ? "_" : ""), sbW2 - 18), sbX + 6, py + 15, searchQuery.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
                if (!searchQuery.isEmpty()) g.text("x", sbX + sbW2 - 10, py + 15, ClientState.UI_TEXT_MUTED, false);
            } else if (sHov) { g.playClickSound(); accountInputFocused = false; waypointInputFocused = false; if (!searchQuery.isEmpty() && mx >= sbX + sbW2 - 14) { searchQuery = ""; resetScroll(); } else searchFocused = true; return true; }
            int actX = sbX - 48;
            if (drawBtn(g, "1c_on", actX, py + 10, 44, 18, 5, onlyEnabledFilter ? "ON:Y" : "All", onlyEnabledFilter ? ClientState.withAlpha(accent, 45) : 0xFF191922, onlyEnabledFilter ? accent : ClientState.UI_TEXT_MUTED, false, mx, my, click)) { onlyEnabledFilter = !onlyEnabledFilter; resetScroll(); return true; }
        }
        if (editingModId != null) return renderModSettingsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("waypoints".equals(activeMenu)) return renderWaypointsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("accounts".equals(activeMenu)) return renderAccountsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("ui_engine".equals(activeMenu)) return renderUiEnginePage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("profiles".equals(activeMenu)) return renderProfilesPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        return renderModCardGrid(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad, false);
    }

    private boolean renderActiveContent(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad, boolean isPalette) {
        if (editingModId != null) return renderModSettingsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("waypoints".equals(activeMenu)) return renderWaypointsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("accounts".equals(activeMenu)) return renderAccountsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("ui_engine".equals(activeMenu)) return renderUiEnginePage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("profiles".equals(activeMenu)) return renderProfilesPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if (isPalette && !click) searchFocused = true;
        if (!click) g.text(getMenuTitle(activeMenu) + (isPalette ? " (Command Palette)" : ""), cx + 2, cy + 4, ClientState.UI_TEXT, false);
        int sbW = isPalette ? Math.min(240, cw - 110) : Math.min(165, cw / 2), sbX = cx + cw - sbW, sbY = cy; boolean sHov = mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + 20;
        if (!click) {
            g.drawGlassCard(sbX, sbY, sbW, 20, Math.max(2, menuRad / 2), ClientState.UI_INPUT, searchFocused ? accent : (sHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            g.text(searchQuery.isEmpty() ? (searchFocused ? "_" : "Search modules...") : (searchQuery + (searchFocused ? "_" : "")), sbX + 8, sbY + 6, searchQuery.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
            if (!searchQuery.isEmpty()) g.text("x", sbX + sbW - 12, sbY + 6, ClientState.UI_TEXT_MUTED, false);
        } else if (sHov) { g.playClickSound(); accountInputFocused = false; waypointInputFocused = false; if (!searchQuery.isEmpty() && mx >= sbX + sbW - 16) { searchQuery = ""; resetScroll(); } else searchFocused = true; return true; } else searchFocused = false;
        return renderModCardGrid(g, cx, cy + 26, cw, ch - 26, mx, my, click, accent, accentFg, menuRad, isPalette);
    }

    private boolean renderWaypointsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        ClientConfig cfg = ClientConfig.get(); String curW = ClientState.getCurrentWorldKey();
        if (!click) {
            g.drawGlassCard(cx, cy, cw, 34, menuRad, 0xFF14141B, accent);
            g.text("Waypoints (" + curW + ")  •  XYZ: " + ClientState.getPlayerX() + ", " + ClientState.getPlayerY() + ", " + ClientState.getPlayerZ(), cx + 10, cy + 8, ClientState.UI_TEXT, false);
            g.text("Isolated per server/world so waypoints never overlap across servers", cx + 10, cy + 20, ClientState.UI_TEXT_MUTED, false);
        }
        int addY = cy + 40, inW = cw - 92; boolean inHov = mx >= cx && mx <= cx + inW && my >= addY && my <= addY + 22;
        if (!click) {
            g.drawGlassCard(cx, addY, inW, 22, Math.max(3, menuRad / 2), ClientState.UI_INPUT, waypointInputFocused ? accent : (inHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            g.text(waypointNameInput.isEmpty() ? (waypointInputFocused ? "_" : "Type waypoint name & click + Save Here...") : (waypointNameInput + (waypointInputFocused ? "_" : "")), cx + 8, addY + 7, waypointNameInput.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
        } else if (inHov) { g.playClickSound(); waypointInputFocused = true; accountInputFocused = false; searchFocused = false; return true; }
        else if (mx < cx || mx > cx + cw || my < addY || my > addY + 22) waypointInputFocused = false;

        if (drawBtn(g, "wp_save_here", cx + inW + 6, addY, 86, 22, Math.max(3, menuRad / 2), "+ Save Here", accent, accentFg, true, mx, my, click)) {
            cfg.addWaypointAt(waypointNameInput.trim(), ClientState.getPlayerX(), ClientState.getPlayerY(), ClientState.getPlayerZ(), ClientState.getPlayerDim(), curW);
            waypointNameInput = ""; waypointInputFocused = false; return true;
        }
        int fy = cy + 68;
        String st = cfg.getString("wp_style", "lunar");
        if (drawBtn(g, "wp_f_scope", cx, fy, 118, 18, 5, wpShowAllWorlds ? "Scope: All Worlds" : "Scope: This World", wpShowAllWorlds ? 0xFF1A1A24 : ClientState.withAlpha(accent, 40), ClientState.UI_TEXT, false, mx, my, click)) { wpShowAllWorlds = !wpShowAllWorlds; resetScroll(); return true; }
        if (drawBtn(g, "wp_f_style", cx + 124, fy, 92, 18, 5, "Style: " + st.toUpperCase(), 0xFF1A1A24, ClientState.UI_TEXT, false, mx, my, click)) { int nx = 0; for (int i = 0; i < WP_STYLES.length; i++) if (WP_STYLES[i].equalsIgnoreCase(st)) nx = (i + 1) % WP_STYLES.length; cfg.setOption("wp_style", WP_STYLES[nx]); return true; }
        boolean ad = cfg.getBool("wp_auto_death", true);
        if (drawBtn(g, "wp_f_death", cx + 222, fy, 102, 18, 5, "AutoDeath: " + (ad ? "ON" : "OFF"), ad ? ClientState.withAlpha(accent, 40) : 0xFF1A1A24, ClientState.UI_TEXT, false, mx, my, click)) { cfg.setOption("wp_auto_death", Boolean.valueOf(!ad)); return true; }
        boolean ns = cfg.getBool("wp_nether_sync", true);
        if (drawBtn(g, "wp_f_nether", cx + 330, fy, 92, 18, 5, "Nether 8:1: " + (ns ? "ON" : "OFF"), ns ? ClientState.withAlpha(accent, 40) : 0xFF1A1A24, ClientState.UI_TEXT, false, mx, my, click)) { cfg.setOption("wp_nether_sync", Boolean.valueOf(!ns)); return true; }

        List<ClientConfig.Waypoint> shown = new ArrayList<ClientConfig.Waypoint>();
        for (int i = 0; i < cfg.waypoints.size(); i++) {
            ClientConfig.Waypoint wp = cfg.waypoints.get(i);
            if (wpShowAllWorlds || ClientState.isWaypointInWorld(wp, curW)) shown.add(wp);
        }
        int listY = cy + 92, listH = ch - 92, rowH = 38, gap = 6, rw = cw - 10;
        int totalH = Math.max(0, shown.size() * (rowH + gap) - gap); maxScrollPx = Math.max(0, totalH - listH); if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx);
        if (!click && shown.isEmpty()) g.text("No waypoints in this server/world yet. Type a name and click + Save Here!", cx + 8, listY + 14, ClientState.UI_TEXT_MUTED, false);
        for (int i = 0; i < shown.size(); i++) {
            int ry = listY + i * (rowH + gap) - curScroll;
            if (ry >= listY && ry + rowH <= listY + listH && waypointRow(shown.get(i)).renderOrHandle(g, cx, ry, rw, mx, my, click, accent, accentFg, menuRad)) return true;
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, curScroll, accent);
        return false;
    }

    private boolean renderAccountsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        ClientConfig cfg = ClientConfig.get(); String curName = g.currentUsername();
        if (!click) {
            g.drawGlassCard(cx, cy, cw, 38, menuRad, 0xFF14141B, accent);
            g.drawRoundedRect(cx + 8, cy + 7, 24, 24, 5, ClientState.withAlpha(accent, 45));
            String ini = curName.isEmpty() ? "P" : curName.substring(0, 1).toUpperCase();
            g.text(ini, cx + 20 - g.textWidth(ini) / 2, cy + 15, accent, false);
            g.text("Active Session: " + curName, cx + 40, cy + 9, ClientState.UI_TEXT, false);
            g.text(accStatusMsg.isEmpty() ? "Organize offline alts with tags, pins & 1-click swap" : accStatusMsg, cx + 40, cy + 22, ClientState.UI_TEXT_MUTED, false);
        }
        if (drawBtn(g, "acc_savecur", cx + cw - 88, cy + 9, 78, 20, Math.max(2, menuRad / 2), "Save Current", 0xFF1E1E28, ClientState.UI_TEXT, false, mx, my, click)) { cfg.addAccount(curName, "offline", "Main"); accStatusMsg = "Saved " + curName + " to organizer."; return true; }
        int addY = cy + 44, inW = cw - 78; boolean inHov = mx >= cx && mx <= cx + inW && my >= addY && my <= addY + 22;
        if (!click) {
            g.drawGlassCard(cx, addY, inW, 22, Math.max(3, menuRad / 2), ClientState.UI_INPUT, accountInputFocused ? accent : (inHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            g.text(accountInput.isEmpty() ? (accountInputFocused ? "_" : "Click here & type Alt username...") : (accountInput + (accountInputFocused ? "_" : "")), cx + 8, addY + 7, accountInput.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
        } else if (inHov) { g.playClickSound(); accountInputFocused = true; searchFocused = false; waypointInputFocused = false; return true; }
        else if (mx < cx || mx > cx + cw || my < addY || my > addY + 22) accountInputFocused = false;
        if (drawBtn(g, "acc_add", cx + inW + 6, addY, 72, 22, Math.max(3, menuRad / 2), "+ Add Alt", accent, accentFg, true, mx, my, click)) {
            if (!accountInput.trim().isEmpty()) { String added = accountInput.trim(); cfg.addAccount(added, "offline", "Alt"); accountInput = ""; accountInputFocused = false; accStatusMsg = g.switchAccount(added, "") ? ("Switched live session to " + added) : ("Saved " + added + "!"); }
            return true;
        }
        int fx = cx, fy = cy + 72;
        for (int i = 0; i < ACC_FILTER_TAGS.length; i++) {
            String t = ACC_FILTER_TAGS[i]; int tw = g.textWidth(t) + 14; boolean sel = accTagFilter.equalsIgnoreCase(t);
            if (drawBtn(g, "accf_" + t, fx, fy, tw, 17, 5, t, sel ? ClientState.withAlpha(accent, 45) : 0xFF15151D, sel ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false, mx, my, click)) { accTagFilter = t; resetScroll(); return true; }
            fx += tw + 4;
        }
        List<ClientConfig.AccountEntry> shown = new ArrayList<ClientConfig.AccountEntry>();
        for (int pass = 0; pass < 2; pass++) for (ClientConfig.AccountEntry a : cfg.accounts) { if ((pass == 0) != a.pinned) continue; if (!"All".equalsIgnoreCase(accTagFilter) && !accTagFilter.equalsIgnoreCase(a.tag)) continue; shown.add(a); }
        int listY = cy + 94, listH = ch - 94, rowH = 34, gap = 5, rw = cw - 10, totalH = Math.max(0, shown.size() * (rowH + gap) - gap);
        maxScrollPx = Math.max(0, totalH - listH); if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx; int curScroll = Math.round(scrollPx);
        if (!click && shown.isEmpty()) g.text("No accounts in this folder yet. Add an Alt above!", cx + 8, listY + 14, ClientState.UI_TEXT_MUTED, false);
        for (int i = 0; i < shown.size(); i++) {
            ClientConfig.AccountEntry a = shown.get(i); int ry = listY + i * (rowH + gap) - curScroll; if (ry < listY || ry + rowH > listY + listH) continue;
            boolean inUse = a.name.equalsIgnoreCase(curName);
            if (!click) { g.drawGlassCard(cx, ry, rw, rowH, Math.max(3, menuRad / 2), 0xFF14141B, inUse ? accent : ClientState.UI_BORDER); g.text(a.name + (inUse ? "  [IN USE]" : ""), cx + 28, ry + 7, inUse ? accent : ClientState.UI_TEXT, false); g.text(a.type.toUpperCase() + " account", cx + 28, ry + 19, ClientState.UI_TEXT_DIM, false); }
            if (drawBtn(g, "apin_" + a.id, cx + 6, ry + 8, 16, 18, 4, a.pinned ? "*" : ".", 0x00000000, a.pinned ? accent : ClientState.UI_TEXT_DIM, false, mx, my, click)) { a.pinned = !a.pinned; cfg.save(); return true; }
            if (drawBtn(g, "atag_" + a.id, cx + rw - 172, ry + 8, 42, 18, 4, a.tag.toUpperCase(), 0xFF1E1E28, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { cfg.cycleAccountTag(a); return true; }
            if (drawBtn(g, "aup_" + a.id, cx + rw - 126, ry + 8, 16, 18, 4, "^", 0xFF191922, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { cfg.moveAccount(a.id, -1); return true; }
            if (drawBtn(g, "adn_" + a.id, cx + rw - 108, ry + 8, 16, 18, 4, "v", 0xFF191922, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { cfg.moveAccount(a.id, 1); return true; }
            if (drawBtn(g, "ause_" + a.id, cx + rw - 88, ry + 8, 46, 18, 4, inUse ? "Active" : "Use", inUse ? accent : 0xFF1E293B, inUse ? accentFg : ClientState.UI_TEXT, inUse, mx, my, click)) { cfg.activeAccountId = a.id; cfg.save(); accStatusMsg = g.switchAccount(a.name, a.uuid) ? ("Switched session to " + a.name) : ("Selected " + a.name + " (next launch)"); return true; }
            if (drawBtn(g, "adel_" + a.id, cx + rw - 38, ry + 8, 30, 18, 4, "Del", 0xFF3B1A1E, 0xFFFFFFFF, false, mx, my, click)) { cfg.removeAccount(a.id); return true; }
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, curScroll, accent);
        return false;
    }

    private boolean drawSideItem(UiBridge.UiDraw g, String id, int x, int y, int w, int h, int rad, String label, boolean sel, int accent, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            int bg = sel ? ClientState.withAlpha(accent, 34) : ClientState.lerpColor(0x00000000, 0x1AFFFFFF, animHover(id, hov));
            if ((bg >>> 24) > 3) g.drawRoundedRect(x, y, w, h, rad, bg);
            if (sel) g.drawRoundedRect(x + 2, y + 3, 2, h - 6, 1, accent);
            g.text(label, x + 9, y + (h - 7) / 2, sel || hov ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false);
        }
        if (hov && click) { g.playClickSound(); return true; }
        return false;
    }

    private boolean renderModCardGrid(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad, boolean paletteList) {
        List<ModEntry> list = new ArrayList<ModEntry>(); String q = searchQuery.trim().toLowerCase();
        for (ModEntry m : ALL_MODS) {
            if ("favorites".equals(activeMenu)) { ClientConfig.HudModule hm = ClientConfig.get().hudModules.get(m.id); if (hm == null || !hm.favorite) continue; }
            else if (!"all".equals(activeMenu) && !m.category.equals(activeMenu)) continue;
            if (onlyEnabledFilter && !isModEnabled(m.id)) continue;
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.desc.toLowerCase().contains(q) && !m.category.toLowerCase().contains(q)) continue;
            list.add(m);
        }
        if (paletteList) {
            int rowH = 34, gap = 5, totalH = Math.max(0, list.size() * (rowH + gap) - gap); maxScrollPx = Math.max(0, totalH - ch); if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
            int curScroll = Math.round(scrollPx), rw = cw - 10;
            for (int i = 0; i < list.size(); i++) {
                ModEntry m = list.get(i); int ry = cy + i * (rowH + gap) - curScroll; if (ry < cy || ry + rowH > cy + ch) continue;
                boolean en = isModEnabled(m.id);
                if (!click) { if (mx >= cx && mx <= cx + rw && my >= ry && my <= ry + rowH) hoveredTooltip = m.desc; g.drawGlassCard(cx, ry, rw, rowH, Math.max(2, menuRad / 2), ClientState.UI_CARD, en ? accent : ClientState.UI_BORDER); g.text(m.name + "  [" + m.category.toUpperCase() + "]", cx + 10, ry + 7, ClientState.UI_TEXT, false); g.text(trimToWidth(g, m.desc, rw - 150), cx + 10, ry + 19, ClientState.UI_TEXT_MUTED, false); }
                if (drawBtn(g, "pcfg_" + m.id, cx + rw - 118, ry + 8, 58, 18, Math.max(2, menuRad / 2), "Config", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { editingModId = m.id; resetScroll(); return true; }
                if (drawSwitch(g, "ptog_" + m.id, cx + rw - 54, ry + 8, 44, 18, en, accent, accentFg, mx, my, click)) { toggleMod(m.id); return true; }
            }
            if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, curScroll, accent);
            return false;
        }
        int cols = Math.max(2, Math.min(4, (cw - 6) / Math.max(105, (int) Math.round(ClientConfig.get().getDouble("ui_card_width", 138)))));
        int gap = 6, cardW = (cw - 6 - gap * (cols - 1)) / cols, cardH = 118, totalH = Math.max(0, ((int) Math.ceil(list.size() / (double) cols)) * (cardH + gap) - gap);
        maxScrollPx = Math.max(0, totalH - ch); if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx; int curScroll = Math.round(scrollPx);
        if (!click && list.isEmpty()) g.text("No matching modules.", cx + 8, cy + 16, ClientState.UI_TEXT_MUTED, false);
        for (int i = 0; i < list.size(); i++) {
            ModEntry m = list.get(i); int bx = cx + (i % cols) * (cardW + gap), by = cy + (i / cols) * (cardH + gap) - curScroll;
            if (by + cardH < cy || by > cy + ch) continue;
            int topVis = Math.max(cy, by), botVis = Math.min(cy + ch, by + cardH); if (botVis - topVis < 18) continue;
            boolean enabled = isModEnabled(m.id), hov = mx >= bx && mx <= bx + cardW && my >= topVis && my <= botVis;
            ClientConfig.HudModule hm = ClientConfig.get().hudModules.get(m.id); boolean fav = hm != null && hm.favorite;
            if (!click) {
                if (hov) hoveredTooltip = m.name + " - " + m.desc;
                g.drawGlassCard(bx, topVis, cardW, botVis - topVis, menuRad, ClientState.lerpColor(0xFF131319, 0xFF191922, animHover("card_" + m.id, hov)), enabled ? accent : ClientState.UI_BORDER);
                if (by + 8 >= cy && by + 18 <= cy + ch) { g.drawCircleFilled(bx + 9, by + 13, 3, enabled ? accent : ClientState.UI_TEXT_DIM); g.text(m.category.toUpperCase(), bx + 16, by + 9, ClientState.UI_TEXT_DIM, false); }
                if (by + 22 >= cy && by + 32 <= cy + ch) g.text(trimToWidth(g, m.name, cardW - 14), bx + 8, by + 22, ClientState.UI_TEXT, false);
                if (by + 35 >= cy && by + 68 <= cy + ch) { List<String> w = wrapText(g, m.desc, cardW - 16, 3); for (int l = 0; l < w.size(); l++) g.text(w.get(l), bx + 8, by + 36 + l * 10, ClientState.UI_TEXT_MUTED, false); }
            }
            if (hm != null && by + 6 >= cy && by + 20 <= cy + ch && drawBtn(g, "fav_" + m.id, bx + cardW - 20, by + 6, 14, 14, 7, fav ? "*" : ".", 0x00000000, fav ? accent : ClientState.UI_TEXT_DIM, false, mx, my, click)) { hm.favorite = !hm.favorite; ClientConfig.get().save(); return true; }
            if (by + 72 >= cy && by + 114 <= cy + ch) {
                int btnW = cardW - 14;
                if (drawBtn(g, "cfg_" + m.id, bx + 7, by + 72, btnW, 17, Math.max(2, menuRad / 2), "Config", 0xFF1B1B24, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { editingModId = m.id; resetScroll(); return true; }
                if (drawSwitch(g, "tog_" + m.id, bx + 7, by + 93, btnW, 18, enabled, accent, accentFg, mx, my, click)) { toggleMod(m.id); return true; }
            }
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, curScroll, accent);
        return false;
    }

    private boolean renderModSettingsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        ClientConfig cfg = ClientConfig.get(); boolean isHud = cfg.hudModules.containsKey(editingModId), enabled = isModEnabled(editingModId);
        if (drawBtn(g, "set_back", cx, cy, 52, 20, Math.max(2, menuRad / 2), "< Back", 0xFF191922, ClientState.UI_TEXT, false, mx, my, click)) { editingModId = null; listeningKeyOptId = null; resetScroll(); return true; }
        if (!click) g.text(getModTitle(editingModId), cx + 60, cy + 6, ClientState.UI_TEXT, false);
        if (isHud && drawBtn(g, "set_rst", cx + cw - 110, cy, 56, 20, Math.max(2, menuRad / 2), "Reset", 0xFF191922, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { cfg.resetModule(editingModId); return true; }
        if (drawSwitch(g, "set_tog_" + editingModId, cx + cw - 46, cy + 1, 46, 18, enabled, accent, accentFg, mx, my, click)) { toggleMod(editingModId); return true; }
        int listTop = cy + 25;
        if (isHud) {
            String[][] tabs = {{"geom","Geometry"},{"style","Style"},{"text","Text"},{"vis","Visibility"}}; int tx = cx;
            for (int i = 0; i < tabs.length; i++) {
                int tw = g.textWidth(tabs[i][1]) + 16; boolean sel = inspectorTab.equals(tabs[i][0]);
                if (drawBtn(g, "itab_" + tabs[i][0], tx, listTop, tw, 18, Math.max(2, menuRad / 2), tabs[i][1], sel ? ClientState.withAlpha(accent, 45) : 0xFF16161E, sel ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false, mx, my, click)) { inspectorTab = tabs[i][0]; resetScroll(); return true; }
                tx += tw + 4;
            }
            listTop += 24;
        }
        List<SettingRow> rows = new ArrayList<SettingRow>();
        if ("crosshair".equals(editingModId)) buildCrosshairRows(rows);
        else if ("zoom".equals(editingModId)) buildZoomRows(rows);
        else if ("freelook".equals(editingModId)) buildFreeLookRows(rows);
        else if ("fullbright".equals(editingModId)) buildFullbrightRows(rows);
        else if ("fov_mod".equals(editingModId)) buildFovRows(rows);
        else if ("hurtcam".equals(editingModId)) buildHurtCamRows(rows);
        else if ("low_fire".equals(editingModId)) buildLowFireRows(rows);
        else if ("low_shield".equals(editingModId)) buildLowShieldRows(rows);
        else if ("viewmodel".equals(editingModId)) buildViewmodelRows(rows);
        else if ("hit_color".equals(editingModId)) buildHitColorRows(rows);
        else if ("hitboxes".equals(editingModId)) buildHitboxRows(rows);
        else if ("block_break".equals(editingModId)) buildBlockBreakRows(rows);
        else if (isHud) buildHudInspectorRows(rows, cfg.hudModules.get(editingModId));
        return renderScrollableRows(g, rows, cx, listTop, cw - 10, (cy + ch) - listTop, cw, mx, my, click, accent, accentFg, menuRad);
    }
    private void buildHudInspectorRows(List<SettingRow> rows, ClientConfig.HudModule m) {
        if ("waypoints".equals(m.id) && "geom".equals(inspectorTab)) {
            rows.add(accordionRow("wp_mgr", "WAYPOINTS MANAGER"));
            if (!foldedAccordions.contains("wp_mgr")) {
                rows.add(actionRow("+ Add Waypoint Here", "Save XYZ (" + ClientState.getPlayerX() + "," + ClientState.getPlayerY() + "," + ClientState.getPlayerZ() + ")", "wp_add"));
                for (int i = 0; i < ClientConfig.get().waypoints.size(); i++) {
                    ClientConfig.Waypoint w = ClientConfig.get().waypoints.get(i);
                    if (ClientState.isWaypointInWorld(w, ClientState.getCurrentWorldKey())) rows.add(waypointRow(w));
                }
                rows.add(cycleRow("Waypoint Style", "Lunar Card, Block Highlighter, Dot, or Tall Beacon", "wp_style", WP_STYLES));
                rows.add(boolRow("Auto Death Waypoint", "Automatically mark where you died", "wp_auto_death", true));
                rows.add(boolRow("World Markers", "Show floating markers / block highlights in world", "wp_world_markers", true));
                rows.add(boolRow("Vertical Beacon Beams", "Draw translucent beam above marker", "wp_beam_beacon", true));
                rows.add(boolRow("Show Distance (m)", "Display meter distance inside tag", "wp_show_dist", true));
                rows.add(boolRow("Overworld <-> Nether Sync", "Auto-convert 8:1 coordinates", "wp_nether_sync", true));
                rows.add(boolRow("Offscreen Edge Pointers", "Show directional arrow when off-screen", "wp_offscreen_arrows", true));
            }
        }
        if ("geom".equals(inspectorTab)) {
            rows.add(accordionRow("hud_pos", "POSITION & SIZE"));
            if (!foldedAccordions.contains("hud_pos")) {
                rows.add(modCycleRow("Anchor Lock (9-Point)", "Reference anchor point on screen", m.id, "anchor", ANCHORS));
                rows.add(sliderRow("Scale Multiplier", "Resize HUD from 0.25x to 4.0x", "mod_scale:" + m.id, 0.25, 4.0, 0.05, false));
                rows.add(sliderRow("X Coordinate", "Horizontal anchor position (0-100%)", "mod_x:" + m.id, 0.0, 1.0, 0.005, false));
                rows.add(sliderRow("Y Coordinate", "Vertical anchor position (0-100%)", "mod_y:" + m.id, 0.0, 1.0, 0.005, false));
                rows.add(sliderRow("Width Override (0=Auto)", "Fixed box width in pixels", "mod_w:" + m.id, 0, 320, 4, true));
                rows.add(sliderRow("Height Override (0=Auto)", "Fixed box height in pixels", "mod_h:" + m.id, 0, 180, 2, true));
                rows.add(sliderRow("Horizontal Padding", "Inner X spacing (1-24px)", "mod_padx:" + m.id, 1, 24, 1, true));
                rows.add(sliderRow("Vertical Padding", "Inner Y spacing (1-24px)", "mod_pady:" + m.id, 1, 24, 1, true));
                rows.add(sliderRow("Margin Offset", "Outer offset from anchor (0-16px)", "mod_margin:" + m.id, 0, 16, 1, true));
            }
        } else if ("style".equals(inspectorTab)) {
            rows.add(accordionRow("hud_bg", "BACKGROUND & BORDER"));
            if (!foldedAccordions.contains("hud_bg")) {
                rows.add(sliderRow("Individual Corner Roundness", "0px sharp to 24px pill (Global must be INDIV)", "mod_rad:" + m.id, 0, 24, 1, true));
                rows.add(modBoolRow("Background Box", "Draw clean backdrop container", m.id, "bg"));
                rows.add(colorRow("Background Color", "mod_bg:" + m.id));
                rows.add(sliderRow("Individual BG Opacity", "Backdrop alpha (Global must be INDIV)", "mod_opacity:" + m.id, 0.0, 1.0, 0.05, false));
                rows.add(modBoolRow("Border Outline", "Draw outline border around module", m.id, "border"));
                rows.add(sliderRow("Border Thickness", "Stroke width (0-3px)", "mod_bw:" + m.id, 0, 3, 1, true));
                rows.add(modCycleRow("Border Style", "Solid, Dashed, or Gradient", m.id, "borderStyle", BORDER_STYLES));
                rows.add(colorRow("Border Color 1", "mod_brd:" + m.id));
                rows.add(colorRow("Border Color 2 (Grad)", "mod_brd2:" + m.id));
            }
            rows.add(accordionRow("hud_rgb", "CHROMA RGB"));
            if (!foldedAccordions.contains("hud_rgb")) {
                rows.add(modBoolRow("Chroma RGB Wave", "Animated rainbow color wave", m.id, "chroma"));
                rows.add(sliderRow("Chroma Wave Speed", "Cycle speed multiplier", "mod_cspeed:" + m.id, 0.2, 4.0, 0.1, false));
                rows.add(sliderRow("Chroma Saturation", "Color intensity", "mod_csat:" + m.id, 0.1, 1.0, 0.05, false));
                rows.add(sliderRow("Chroma Angle", "Wave direction in degrees", "mod_cang:" + m.id, 0, 360, 15, true));
            }
        } else if ("text".equals(inspectorTab)) {
            if ("scoreboard".equals(m.id)) { rows.add(boolRow("Hide Vanilla Scoreboard", "Replace sidebar with Aetheria Scoreboard", "sb_hide_vanilla", true)); rows.add(boolRow("Hide Red Score Numbers", "Hide right-side numeric scores", "sb_hide_numbers", false)); }
            if ("saturation".equals(m.id)) { rows.add(cycleRow("Saturation Style", "AppleSkin / Bar / Text", "saturation_style", SAT_STYLES)); rows.add(boolRow("AppleSkin Glint", "Pulse golden outlines", "saturation_glint", true)); }
            if ("potions".equals(m.id)) {
                rows.add(boolRow("Hide Vanilla Potions", "Remove top-right vanilla icons", "potions_hide_vanilla", true));
                rows.add(boolRow("Auto-Hide When Empty", "Hide when no effects active", "potions_auto_hide", true));
                rows.add(cycleRow("Potion Display Style", "Vanilla, Compact, or Cards", "potions_style", POT_STYLES));
                rows.add(boolRow("Show Duration Timers", "Display remaining time", "potions_timers", true));
                rows.add(boolRow("Show Roman Numerals", "E.g. Speed II", "potions_numerals", true));
                rows.add(boolRow("Show Status Icons", "Render effect icons", "potions_show_icon", true));
                rows.add(boolRow("Blink Low Duration", "Flash under 10s left", "potions_blink", true));
            }
            if ("armor".equals(m.id)) { rows.add(boolRow("Horizontal Layout", "Side-by-side armor", "armor_horizontal", false)); rows.add(boolRow("Show Durability Text", "Show % or uses", "armor_show_text", true)); rows.add(boolRow("Percentage Mode", "Durability % vs uses", "armor_percent", true)); rows.add(boolRow("Text-Only Mode", "Text instead of icons", "armor_text_mode", false)); }
            if ("togglesprint".equals(m.id)) { rows.add(boolRow("Hide HUD Badge", "Keep active without box", "togglesprint_hide_hud", false)); rows.add(boolRow("ToggleSneak", "Tap Sneak to lock crouch", "pvp_togglesneak", false)); }
            if ("pearl_cd".equals(m.id)) rows.add(boolRow("Auto-Hide When Ready", "Hide when ready", "pearl_auto_hide", true));
            if ("shield_cd".equals(m.id)) rows.add(boolRow("Auto-Hide When Ready", "Hide when shield is ready", "shield_auto_hide", true));
            if ("kill_counter".equals(m.id)) rows.add(actionRow("Reset K/D Stats", "Zero session kills & deaths", "kd_reset"));
            if ("fps".equals(m.id)) { rows.add(boolRow("Show 'FPS' Label", "Display 'FPS' suffix", "fps_show_label", true)); rows.add(boolRow("Show 1% Low & Min", "Detailed FPS stats", "fps_detailed", false)); }
            if ("coords".equals(m.id)) { rows.add(boolRow("Show Facing", "Include direction", "coords_facing", true)); rows.add(boolRow("Nether 8:1 Coords", "Auto-convert X/Z", "coords_nether", true)); rows.add(boolRow("Compact Line", "Single-line XYZ", "coords_compact", false)); }
            if ("clock".equals(m.id)) { rows.add(boolRow("24-Hour Format", "24h vs AM/PM", "clock_24h", false)); rows.add(boolRow("Show Seconds", "Display :SS", "clock_seconds", false)); }
            if ("speedometer".equals(m.id)) rows.add(cycleRow("Speed Unit", "b/s, km/h, or mph", "speed_unit", SPEED_UNITS));
            if ("keystrokes".equals(m.id)) { rows.add(boolRow("Show CPS", "LMB/RMB counters", "keystrokes_cps", true)); rows.add(boolRow("Show Spacebar", "Space key", "keystrokes_space", true)); rows.add(boolRow("Show Shift/Ctrl", "Modifier keys", "keystrokes_mods", false)); }
            rows.add(colorRow("Text / Icon Color", "mod:" + m.id));
            rows.add(sliderRow("Text Opacity", "Foreground alpha (10-100%)", "mod_topac:" + m.id, 0.1, 1.0, 0.05, false));
            rows.add(modBoolRow("Text Shadow", "Drop shadow behind text", m.id, "shadow"));
            rows.add(sliderRow("Letter Spacing", "Pixel tracking (0-3px)", "mod_lspace:" + m.id, 0, 3, 1, true));
            rows.add(modCycleRow("Bracket Style", "Wrap text in [], (), <>, {}", m.id, "bracketStyle", BRACKETS));
        } else {
            rows.add(modBoolRow("Hide in F1 Mode", "Auto-hide during F1", m.id, "hideInF1"));
            rows.add(modBoolRow("Hide in Chat", "Auto-hide while chat is open", m.id, "hideInChat"));
            rows.add(modBoolRow("Hide in Inventory", "Auto-hide inside menus", m.id, "hideInInventory"));
            rows.add(modBoolRow("Hide in F3 Debug", "Auto-hide when F3 overlay is open", m.id, "hideInF3"));
            if ("stopwatch".equals(m.id)) { rows.add(actionRow("Stopwatch Toggle", "Start or pause stopwatch", "sw_toggle")); rows.add(actionRow("Stopwatch Reset", "Reset to 00:00.0", "sw_reset")); }
            if ("countdown".equals(m.id)) rows.add(actionRow("Start 60s Countdown", "Starts a 1-minute alert", "cd_60"));
        }
    }

    private boolean renderUiEnginePage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        List<SettingRow> rows = new ArrayList<SettingRow>();
        rows.add(accordionRow("ui_theme", "THEMES, FONT & TITLE SCREEN"));
        if (!foldedAccordions.contains("ui_theme")) {
            rows.add(cycleRow("Menu Layout Mode", "OneClient, Topbar, Sidebar, Grid, or Palette", "ui_menu_layout", MENU_LAYOUTS));
            rows.add(boolRow("Clean Sans-Serif Font", "Smooth crisp non-Minecrafty font", "ui_clean_font", true));
            rows.add(boolRow("Custom Title Screen", "10 movable widgets + account manager", "ui_custom_title", true));
            rows.add(actionRow("Customize Title Screen", "Drag & toggle all 10 Title Screen widgets", "title_editor"));
            rows.add(accentRow("Accent Color"));
            rows.add(colorRow("Custom Accent HEX", "opt:ui_accent_custom"));
            rows.add(sliderRow("Global Corner Rounding", "INDIV = per-HUD | 0-24 = force all", "global_hud_rounding", -1, 24, 1, true));
            rows.add(sliderRow("Global Background Opacity", "INDIV = per-HUD | 0%-100% = force all", "global_hud_opacity", -0.05, 1.0, 0.05, false));
            rows.add(sliderRow("Menu Corner Rounding", "ClickGUI roundness (when Global is INDIV)", "ui_menu_rounding", 0, 24, 1, true));
            rows.add(cycleRow("Toggle Switch Style", "Pill, iOS Switch, or Checkbox", "ui_switch_style", SWITCH_STYLES));
            rows.add(sliderRow("Module Card Width", "Grid card width (105-200px)", "ui_card_width", 105, 200, 5, true));
            rows.add(boolRow("UI Click Sounds", "Subtle click audio on buttons", "ui_click_sound", true));
            rows.add(boolRow("Hover Tooltips", "Show descriptive tooltips on hover", "ui_tooltips", true));
        }
        rows.add(accordionRow("ui_perf", "PERFORMANCE & ANIMATIONS"));
        if (!foldedAccordions.contains("ui_perf")) {
            rows.add(boolRow("Unfocused FPS Saver", "Cap FPS when game window is unfocused", "perf_unfocused_fps", true));
            rows.add(boolRow("HUD String Cache Boost", "Throttle RAM/CPU/Clock polling for FPS", "perf_hud_throttle", true));
            rows.add(boolRow("UI Animations", "Smooth fade & spring easing", "ui_animations", true));
            rows.add(sliderRow("Animation Speed", "Transition speed (0.5x-2.5x)", "ui_anim_speed", 0.5, 2.5, 0.1, false));
            rows.add(cycleRow("Animation Curve", "Smooth vs Snappy easing", "ui_anim_curve", ANIM_CURVES));
            rows.add(boolRow("HUD Magnetic Snap Grid", "Snap to 40-step grid", "hud_snap_grid", true));
            rows.add(boolRow("HUD Alignment Guides", "Show center snap lines", "hud_align_guides", true));
            rows.add(boolRow("Global HUD Text Shadow", "Clean drop shadow toggle", "hud_shadow", true));
        }
        rows.add(accordionRow("ui_keys", "KEYBINDS & RESET"));
        if (!foldedAccordions.contains("ui_keys")) {
            rows.add(keybindRow("Menu Keybind", "Click and press ANY key", "ctl_menu_key", "RSHIFT"));
            rows.add(keybindRow("Waypoints Menu Keybind", "Open dedicated Waypoints manager", "ctl_waypoints_key", "B"));
            rows.add(keybindRow("Fullbright Toggle Key", "Quick-toggle Fullbright in-game", "ctl_fullbright_key", "F7"));
            rows.add(keybindRow("Profile Cycle Keybind", "Click and press ANY key", "ctl_profile_key", "F6"));
            rows.add(keybindRow("Zoom Keybind", "Click and press ANY key", "cam_zoom_key", "C"));
            rows.add(keybindRow("FreeLook Keybind", "Click and press ANY key", "util_freelook_key", "LALT"));
            rows.add(actionRow("Reset All HUD Positions", "Restore default positions", "reset_layout"));
            rows.add(actionRow("Reset All Client Settings", "Factory reset all options", "reset_all"));
        }
        return renderScrollableRows(g, rows, cx, cy, cw - 10, ch, cw, mx, my, click, accent, accentFg, menuRad);
    }

    private boolean renderProfilesPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        List<SettingRow> rows = new ArrayList<SettingRow>();
        rows.add(profileRow("PvP", "FPS, Ping, CPS, Keystrokes, Target HUD, Combo, Reach, Potions, Armor & Scoreboard"));
        rows.add(profileRow("Survival", "Coords + Nether, Biome, Light Level, Day/Weather, Waypoints, Scoreboard & WAILA"));
        rows.add(profileRow("SMP", "Coords, Ping, TPS, Server IP, Players, Waypoints, Death Tracker, Totems & Scoreboard"));
        rows.add(profileRow("Minimal", "Ultra-clean FPS, Coordinates & Ping only"));
        rows.add(profileRow("Cinematic", "Zero clutter - hides HUDs except active Zoom badge"));
        return renderScrollableRows(g, rows, cx, cy, cw - 10, ch, cw, mx, my, click, accent, accentFg, menuRad);
    }

    private boolean renderScrollableRows(UiBridge.UiDraw g, List<SettingRow> rows, int cx, int listY, int rw, int listH, int cw, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        int totalH = 0; for (int i = 0; i < rows.size(); i++) totalH += rows.get(i).height() + 6;
        maxScrollPx = Math.max(0, totalH - listH); if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx), ry = listY - curScroll;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i); int rh = row.height();
            if (ry >= listY && ry + rh <= listY + listH && row.renderOrHandle(g, cx, ry, rw, mx, my, click, accent, accentFg, menuRad)) return true;
            ry += rh + 6;
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, curScroll, accent);
        return false;
    }

    private void buildCrosshairRows(List<SettingRow> rows) {
        rows.add(xhairPreviewRow()); rows.add(xhairPixelEditorRow());
        rows.add(accordionRow("xh_gen", "SHAPE & COLORS"));
        if (!foldedAccordions.contains("xh_gen")) {
            rows.add(cycleRow("Default Shape", "Choose 'CUSTOM' to use Default Pixel Grid", "pvp_xhair_style", XHAIR_STYLES));
            rows.add(sliderRow("Custom Pixel Size", "Scale (1x-3x) for CUSTOM", "xhair_pixel_scale", 1, 3, 1, true));
            rows.add(sliderRow("Size", "Arm length or radius", "xhair_size", 2, 16, 1, true));
            rows.add(sliderRow("Center Gap", "Center spacing", "xhair_gap", 0, 8, 1, true));
            rows.add(sliderRow("Thickness", "Pixel width (1-3px)", "xhair_thick", 1, 3, 1, true));
            rows.add(colorRow("Crosshair Color", "opt:pvp_xhair_color"));
        }
        rows.add(accordionRow("xh_atk", "ATTACK INDICATOR & TARGET"));
        if (!foldedAccordions.contains("xh_atk")) {
            rows.add(cycleRow("Attack Indicator", "Cooldown bar or dot below crosshair", "pvp_xhair_atk_ind", ATK_IND_MODES));
            rows.add(boolRow("Always Show Indicator", "Show even when 100% charged", "pvp_xhair_atk_always", false));
            rows.add(colorRow("Attack Indicator Color", "opt:pvp_xhair_atk_color"));
            rows.add(boolRow("Player-in-View Switch", "Change shape/color on target", "pvp_xhair_target", true));
            rows.add(boolRow("Players Only", "ON = Players | OFF = All Mobs", "pvp_xhair_target_players_only", true));
            rows.add(sliderRow("Detection Range", "Max distance in blocks", "xhair_range", 3, 32, 1, true));
            rows.add(cycleRow("In-View Shape", "Choose 'CUSTOM' for Player-in-View Grid", "pvp_xhair_target_style", TARGET_STYLES));
            rows.add(colorRow("In-View Color", "opt:pvp_xhair_target_color"));
        }
    }
    private void buildZoomRows(List<SettingRow> rows) { rows.add(sliderRow("Zoom Magnification", "Base zoom (2x-12x)", "zoom_fac", 2, 12, 1, true)); rows.add(boolRow("Scroll Wheel Zoom", "Scroll while zooming to adjust", "cam_zoom_scroll", true)); rows.add(boolRow("Reset Scroll on Release", "Reset when released", "cam_zoom_reset_scroll", true)); rows.add(boolRow("Smooth Zoom Easing", "Smooth FOV transitions", "cam_zoom_ease", true)); rows.add(boolRow("Slow Mouse While Zoomed", "Precision aiming", "cam_zoom_slow", true)); rows.add(keybindRow("Zoom Keybind", "Click and press ANY key", "cam_zoom_key", "C")); }
    private void buildFreeLookRows(List<SettingRow> rows) { rows.add(boolRow("Enable FreeLook 360", "Rotate camera freely 360 degrees", "util_freelook", true)); rows.add(keybindRow("FreeLook Keybind", "Click and press ANY key", "util_freelook_key", "LALT")); }
    private void buildFullbrightRows(List<SettingRow> rows) { rows.add(boolRow("Fullbright", "Max in-world brightness", "util_fullbright", false)); rows.add(sliderRow("Brightness Level", "Gamma multiplier", "util_fullbright_level", 2.0, 15.0, 1.0, false)); rows.add(keybindRow("Fullbright Toggle Key", "Quick-toggle Fullbright in-game", "ctl_fullbright_key", "F7")); }
    private void buildFovRows(List<SettingRow> rows) { rows.add(boolRow("Dynamic FOV", "Shift FOV on sprint/bow", "cam_fov_dynamic", true)); rows.add(sliderRow("FOV Effect Intensity", "0.0x static to 1.5x", "cam_fov_factor", 0.0, 1.5, 0.05, false)); rows.add(boolRow("Sprint FOV Shift", "Zoom out when sprinting", "cam_fov_sprint", true)); rows.add(boolRow("Bow Drawing FOV Zoom", "Zoom in when drawing bow", "cam_fov_bow", true)); }
    private void buildHurtCamRows(List<SettingRow> rows) { rows.add(boolRow("No Hurt Cam", "Disable damage screen shake", "pvp_no_hurtcam", false)); rows.add(sliderRow("Hurt Cam Shake Strength", "0% none to 100% vanilla", "pvp_hurtcam_factor", 0.0, 1.0, 0.05, false)); }
    private void buildLowFireRows(List<SettingRow> rows) { rows.add(lowFirePreviewRow()); rows.add(boolRow("Low Fire Overlay", "Lower first-person flame overlay", "pvp_low_fire", true)); rows.add(sliderRow("Fire Vertical Offset", "Shift flame downward", "fire_offset", -0.70, 0.0, 0.02, false)); }
    private void buildLowShieldRows(List<SettingRow> rows) { rows.add(lowShieldPreviewRow()); rows.add(boolRow("Enable Low Shield", "Lower & resize first-person shield", "pvp_low_shield", false)); rows.add(boolRow("Only While Blocking", "Apply offset only when holding RMB", "pvp_low_shield_blocking", false)); rows.add(sliderRow("Shield Scale", "Resize shield (0.40x - 1.20x)", "pvp_low_shield_scale", 0.40, 1.20, 0.05, false)); rows.add(sliderRow("Vertical Y Offset", "Move shield down/up (-0.60 to 0.20)", "pvp_low_shield_y", -0.60, 0.20, 0.02, false)); rows.add(sliderRow("Horizontal X Offset", "Shift inward/outward (-0.30 to 0.30)", "pvp_low_shield_x", -0.30, 0.30, 0.02, false)); }
    private void buildViewmodelRows(List<SettingRow> rows) {
        rows.add(viewmodelPreviewRow()); rows.add(accordionRow("vm_totem", "SMALL TOTEM & GENERAL"));
        if (!foldedAccordions.contains("vm_totem")) { rows.add(boolRow("Enable Hand / Viewmodel", "Customize main & off-hand positions", "vm_enabled", false)); rows.add(boolRow("Small Totem", "Shrink Totem of Undying in hand", "pvp_small_totem", false)); rows.add(sliderRow("Small Totem Scale", "Totem size (0.35x - 1.00x)", "pvp_small_totem_scale", 0.35, 1.00, 0.05, false)); rows.add(sliderRow("Small Totem Y Offset", "Lower totem height (-0.45 to 0.15)", "pvp_small_totem_y", -0.45, 0.15, 0.02, false)); }
        rows.add(accordionRow("vm_main", "MAIN HAND POSITION"));
        if (!foldedAccordions.contains("vm_main")) { rows.add(sliderRow("Main Hand X", "Left/Right (-0.50 to 0.50)", "vm_main_x", -0.50, 0.50, 0.02, false)); rows.add(sliderRow("Main Hand Y", "Down/Up (-0.50 to 0.50)", "vm_main_y", -0.50, 0.50, 0.02, false)); rows.add(sliderRow("Main Hand Z", "Forward/Back (-0.50 to 0.50)", "vm_main_z", -0.50, 0.50, 0.02, false)); rows.add(sliderRow("Main Hand Scale", "Item scale (0.40x - 1.40x)", "vm_main_scale", 0.40, 1.40, 0.05, false)); }
        rows.add(accordionRow("vm_off", "OFF-HAND POSITION"));
        if (!foldedAccordions.contains("vm_off")) { rows.add(sliderRow("Off-Hand X", "Left/Right (-0.50 to 0.50)", "vm_off_x", -0.50, 0.50, 0.02, false)); rows.add(sliderRow("Off-Hand Y", "Down/Up (-0.50 to 0.50)", "vm_off_y", -0.50, 0.50, 0.02, false)); rows.add(sliderRow("Off-Hand Z", "Forward/Back (-0.50 to 0.50)", "vm_off_z", -0.50, 0.50, 0.02, false)); rows.add(sliderRow("Off-Hand Scale", "Item scale (0.40x - 1.40x)", "vm_off_scale", 0.40, 1.40, 0.05, false)); }
    }
    private void buildHitColorRows(List<SettingRow> rows) { rows.add(boolRow("Enable Hit Colorizer", "Replace vanilla red damage flash", "pvp_hit_color", false)); rows.add(boolRow("Chroma RGB Hit Flash", "Cycle rainbow colors on damage", "pvp_hit_color_chroma", false)); rows.add(sliderRow("Tint Intensity", "Blend strength (10% - 100%)", "pvp_hit_color_alpha", 0.10, 1.00, 0.05, false)); rows.add(colorRow("Hit Flash Color", "opt:pvp_hit_color_hex")); }
    private void buildHitboxRows(List<SettingRow> rows) {
        rows.add(hitboxPreviewRow()); rows.add(accordionRow("hb_style", "STYLE & COLOR"));
        if (!foldedAccordions.contains("hb_style")) { rows.add(boolRow("Enable Custom Hitboxes", "Render customized 3D entity hitboxes", "hb_enabled", false)); rows.add(boolRow("Chroma RGB Hitboxes", "Rainbow animated hitbox outlines", "hb_chroma", false)); rows.add(colorRow("Hitbox Color", "opt:hb_color")); rows.add(sliderRow("Line Width", "Stroke thickness (1.0 - 4.0)", "hb_line_width", 1.0, 4.0, 0.5, false)); rows.add(sliderRow("Fill Opacity", "Translucent inner box fill (0% - 50%)", "hb_fill_alpha", 0.0, 0.50, 0.02, false)); }
        rows.add(accordionRow("hb_targets", "TARGETS & LINES"));
        if (!foldedAccordions.contains("hb_targets")) { rows.add(boolRow("Show Eye-Height Line", "Red eye-level bounding ring", "hb_eye_line", true)); rows.add(boolRow("Show Look Vector", "Blue facing sightline", "hb_look_vec", true)); rows.add(boolRow("Players", "Show on other players", "hb_players", true)); rows.add(boolRow("Mobs / Living", "Show on mobs & animals", "hb_mobs", true)); rows.add(boolRow("Projectiles", "Show on arrows, pearls & fireballs", "hb_projectiles", true)); rows.add(boolRow("Dropped Items", "Show on item drops", "hb_items", false)); rows.add(boolRow("Hide Self (3rd Person)", "Hide your own hitbox", "hb_hide_self", true)); }
    }
    private void buildBlockBreakRows(List<SettingRow> rows) { rows.add(blockBreakPreviewRow()); rows.add(boolRow("Enable Block Break FX", "Render custom 3D animation while mining", "bb_enabled", false)); rows.add(cycleRow("Animation Style (5x)", "Shrink, Expand, Rise, Slide Down, or Pulse", "bb_style", BB_STYLES)); rows.add(boolRow("Progress Color Gradient", "Shift Green -> Yellow -> Red as block breaks", "bb_progress_color", true)); rows.add(colorRow("Static Accent Color", "opt:bb_color")); rows.add(sliderRow("Fill Opacity", "Inner block highlight alpha (0% - 60%)", "bb_fill_alpha", 0.0, 0.60, 0.05, false)); rows.add(sliderRow("Outline Width", "Wireframe thickness (1.0 - 4.0)", "bb_line_width", 1.0, 4.0, 0.5, false)); rows.add(boolRow("Hide Vanilla Cracks", "Disable default crack texture when enabled", "bb_hide_vanilla", false)); }

    private interface SettingRow { int height(); boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad); }
    private SettingRow accordionRow(final String key, final String title) {
        return new SettingRow() {
            @Override public int height() { return 22; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                boolean folded = foldedAccordions.contains(key), hov = mx >= x && mx <= x + w && my >= y && my <= y + 22;
                if (!click) { g.drawGlassCard(x, y, w, 22, 6, hov ? 0xFF1A1B24 : 0xFF121218, ClientState.UI_BORDER); g.text((folded ? "> " : "v ") + title, x + 10, y + 7, accent, false); }
                else if (hov) { g.playClickSound(); if (folded) foldedAccordions.remove(key); else foldedAccordions.add(key); return true; }
                return false;
            }
        };
    }
    private SettingRow hitboxPreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 84; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get(); boolean ch = cfg.getBool("hb_chroma", false), eye = cfg.getBool("hb_eye_line", true), look = cfg.getBool("hb_look_vec", true);
                int col = ch ? ClientState.getChromaColor(2.0, 0.85, 45.0, 0.5, 0.5) : ClientState.parseHexColor(cfg.getString("hb_color", "#22d3ee"), 0xFF22D3EE);
                double fillA = cfg.getDouble("hb_fill_alpha", 0.12); int thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("hb_line_width", 2.0) * 0.7)));
                g.drawGlassCard(x, y, w, 84, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text("3D HITBOX PREVIEW", x + 12, y + 7, ClientState.UI_TEXT_MUTED, false);
                int px = x + 12, py = y + 20, pw = w - 24, ph = 56, cx = px + pw / 2, bx = cx - 14, by = py + 8, bw = 28, bh = 42, ox = 8, oy = -4;
                g.drawRoundedRect(px, py, pw, ph, 6, 0xFF0C0D12);
                if (fillA > 0.01) g.fill(bx, by, bx + bw, by + bh, ClientState.withAlpha(col, (int) Math.round(fillA * 255.0)));
                g.drawBorder(bx + ox, by + oy, bw, bh, ClientState.withAlpha(col, 140));
                g.drawLine(bx, by, bx + ox, by + oy, 1, ClientState.withAlpha(col, 160)); g.drawLine(bx + bw, by, bx + bw + ox, by + oy, 1, ClientState.withAlpha(col, 160));
                g.drawLine(bx, by + bh, bx + ox, by + bh + oy, 1, ClientState.withAlpha(col, 160)); g.drawLine(bx + bw, by + bh, bx + bw + ox, by + bh + oy, 1, ClientState.withAlpha(col, 160));
                g.drawRoundedOutline(bx, by, bw, bh, 0, thick, "solid", col, col);
                if (eye) g.fill(bx - 1, by + 9, bx + bw + 1, by + 11, 0xFFEF4444); if (look) g.drawLine(cx + 2, by + 10, cx + 30, by + 16, 2, 0xFF3B82F6);
                g.text(ch ? "Chroma" : ClientState.toHexColor(col).toUpperCase(), px + 8, py + 24, ClientState.UI_TEXT_DIM, false); return false;
            }
        };
    }
    private SettingRow blockBreakPreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 84; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get(); String style = cfg.getString("bb_style", "shrink"); boolean progCol = cfg.getBool("bb_progress_color", true); double fillA = cfg.getDouble("bb_fill_alpha", 0.30);
                float p = (System.currentTimeMillis() % 1800L) / 1800.0F;
                int col = progCol ? (p < 0.5F ? ClientState.lerpColor(0xFF10B981, 0xFFF59E0B, p * 2.0F) : ClientState.lerpColor(0xFFF59E0B, 0xFFEF4444, (p - 0.5F) * 2.0F)) : ClientState.parseHexColor(cfg.getString("bb_color", "#22d3ee"), 0xFF22D3EE);
                g.drawGlassCard(x, y, w, 84, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text("BLOCK BREAK PREVIEW: " + style.toUpperCase() + " (" + Math.round(p * 100) + "%)", x + 12, y + 7, ClientState.UI_TEXT_MUTED, false);
                int px = x + 12, py = y + 20, pw = w - 24, ph = 56, sz = 40, bx = px + pw / 2 - sz / 2, by = py + 8;
                g.drawRoundedRect(px, py, pw, ph, 6, 0xFF0C0D12); g.fill(bx, by, bx + sz, by + sz, 0xFF3F3F46); g.drawBorder(bx, by, sz, sz, 0x66FFFFFF);
                int rx = bx, ry = by, rw = sz, rh = sz;
                if ("shrink".equals(style)) { int ins = Math.round((sz * 0.45F) * p); rx += ins; ry += ins; rw = Math.max(4, sz - ins * 2); rh = Math.max(4, sz - ins * 2); }
                else if ("expand".equals(style)) { int ins = Math.round((sz * 0.45F) * (1.0F - p)); rx += ins; ry += ins; rw = Math.max(4, sz - ins * 2); rh = Math.max(4, sz - ins * 2); }
                else if ("rise".equals(style)) { rh = Math.max(3, Math.round(sz * p)); ry = by + sz - rh; }
                else if ("slide_down".equals(style)) { rh = Math.max(3, Math.round(sz * (1.0F - p))); ry = by + sz - rh; }
                else { int ins = Math.round(4.0F * (0.5F + 0.5F * (float) Math.sin(p * Math.PI * 6.0))); rx += ins; ry += ins; rw -= ins * 2; rh -= ins * 2; }
                g.fill(rx, ry, rx + rw, ry + rh, ClientState.withAlpha(col, (int) Math.round(fillA * 255.0))); g.drawBorder(rx, ry, rw, rh, col); return false;
            }
        };
    }
    private SettingRow viewmodelPreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 86; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get(); boolean vmOn = cfg.getBool("vm_enabled", false), smTotem = cfg.getBool("pvp_small_totem", false);
                g.drawGlassCard(x, y, w, 86, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text("FIRST-PERSON HANDS PREVIEW" + (vmOn ? " (ACTIVE)" : " (VANILLA)"), x + 12, y + 7, ClientState.UI_TEXT_MUTED, false);
                int px = x + 12, py = y + 20, pw = w - 24, ph = 58; g.drawRoundedRect(px, py, pw, ph, 6, 0xFF0C0D12); g.fill(px + pw / 2 - 1, py + ph / 2 - 1, px + pw / 2 + 1, py + ph / 2 + 1, 0x55FFFFFF);
                int rx = px + pw - 38 + (int) Math.round((vmOn ? cfg.getDouble("vm_main_x", 0.0) : 0.0) * 36.0), ry = py + ph - 26 - (int) Math.round((vmOn ? cfg.getDouble("vm_main_y", 0.0) : 0.0) * 30.0), rsz = Math.max(8, (int) Math.round(18.0 * (vmOn ? cfg.getDouble("vm_main_scale", 1.0) : 1.0)));
                g.drawRoundedRect(rx, ry, rsz, rsz, 3, 0xFF22D3EE); g.text("MAIN", rx, ry - 9, ClientState.UI_TEXT_DIM, false);
                double osc = (vmOn ? cfg.getDouble("vm_off_scale", 1.0) : 1.0) * (smTotem ? cfg.getDouble("pvp_small_totem_scale", 0.60) : 1.0), tyOff = smTotem ? cfg.getDouble("pvp_small_totem_y", -0.12) : 0.0;
                int lx = px + 24 + (int) Math.round((vmOn ? cfg.getDouble("vm_off_x", 0.0) : 0.0) * 36.0), ly = py + ph - 24 - (int) Math.round(((vmOn ? cfg.getDouble("vm_off_y", 0.0) : 0.0) + tyOff) * 30.0), lsz = Math.max(6, (int) Math.round(16.0 * osc));
                g.drawRoundedRect(lx, ly, lsz, lsz, 3, smTotem ? 0xFFFACC15 : 0xFFA1A1AA); g.text(smTotem ? "TOTEM" : "OFF", lx, ly - 9, ClientState.UI_TEXT_DIM, false); return false;
            }
        };
    }
    private SettingRow lowShieldPreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 80; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get(); boolean en = cfg.getBool("pvp_low_shield", false); double sc = en ? cfg.getDouble("pvp_low_shield_scale", 0.85) : 1.0, yOff = en ? cfg.getDouble("pvp_low_shield_y", -0.22) : 0.0, xOff = en ? cfg.getDouble("pvp_low_shield_x", 0.0) : 0.0;
                g.drawGlassCard(x, y, w, 80, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text("FIRST-PERSON SHIELD PREVIEW" + (en ? " (LOWERED)" : " (VANILLA)"), x + 12, y + 7, ClientState.UI_TEXT_MUTED, false);
                int px = x + 12, py = y + 20, pw = w - 24, ph = 52, sw0 = Math.max(12, (int) Math.round(22.0 * sc)), sh0 = Math.max(18, (int) Math.round(36.0 * sc));
                g.drawRoundedRect(px, py, pw, ph, 6, 0xFF0C0D12); g.fill(px + pw / 2 - 1, py + ph / 2 - 1, px + pw / 2 + 1, py + ph / 2 + 1, 0x55FFFFFF);
                int sx = px + 26 + (int) Math.round(xOff * 42.0), sy = py + ph - sh0 - 4 - (int) Math.round(yOff * 36.0);
                g.drawGlassCard(sx, sy, sw0, sh0, 3, 2, "solid", 0xFF785234, 0xFFD4D4D8, 0xFFD4D4D8); g.fill(sx + sw0 / 2 - 2, sy + sh0 / 2 - 3, sx + sw0 / 2 + 2, sy + sh0 / 2 + 3, 0xFFD4D4D8);
                g.text("H: " + Math.round(sc * 100) + "%", px + pw - 52, py + 20, ClientState.UI_TEXT_MUTED, false); return false;
            }
        };
    }
    private SettingRow lowFirePreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 76; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get(); double offset = cfg.getDouble("pvp_low_fire_offset", -0.32); boolean en = cfg.getBool("pvp_low_fire", true);
                g.drawGlassCard(x, y, w, 76, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text("FIRST-PERSON FIRE PREVIEW" + (en ? " (LOW FIRE)" : " (VANILLA)"), x + 12, y + 7, ClientState.UI_TEXT_MUTED, false);
                int pw = w - 24, ph = 48, px0 = x + 12, py0 = y + 20; g.drawRoundedRect(px0, py0, pw, ph, 6, 0xFF0C0D12); g.fill(px0 + pw / 2 - 1, py0 + ph / 2 - 1, px0 + pw / 2 + 1, py0 + ph / 2 + 1, 0x55FFFFFF);
                float bob = (float) Math.sin(System.currentTimeMillis() / 130.0) * 2.0F; int baseH = en ? Math.max(4, (int) Math.round(ph * (0.68 + offset))) : Math.round(ph * 0.88F);
                for (int k = 0; k < 6; k++) { int fx = px0 + (k * pw) / 6, fw = pw / 6 + 2, fh = Math.max(4, baseH + ((k % 2 == 0) ? Math.round(bob) : -Math.round(bob))); g.fill(fx, py0 + ph - fh, fx + fw, py0 + ph, 0xB8EA580C); g.fill(fx + 3, py0 + ph - Math.max(2, fh - 4), fx + fw - 3, py0 + ph, 0xCCFACC15); }
                g.text("Screen Covered: " + Math.round((baseH / (float) ph) * 100) + "%", px0 + 8, py0 + 8, 0xFFFFFFFF, true); return false;
            }
        };
    }
    private SettingRow waypointRow(final ClientConfig.Waypoint wp) {
        return new SettingRow() {
            @Override public int height() { return 38; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                int col = ClientState.parseHexColor(wp.color, 0xFF22D3EE);
                if (!click) { g.drawGlassCard(x, y, w, 38, menuRad, 0xFF14141B, wp.enabled ? col : ClientState.UI_BORDER); g.text(wp.x + ", " + wp.y + ", " + wp.z + " • " + wp.dimension.replace("minecraft:", "") + " • " + (wp.worldId == null ? "global" : wp.worldId), x + 30, y + 22, ClientState.UI_TEXT_DIM, false); }
                if (mx >= x + 6 && mx <= x + 24 && my >= y + 9 && my <= y + 29) { hoveredTooltip = "Cycle Waypoint Color"; if (click) { g.playClickSound(); ClientConfig.get().cycleWaypointColor(wp); return true; } }
                if (!click) g.drawCircleFilled(x + 15, y + 19, 6, col);
                if (drawBtn(g, "wpn_" + wp.id, x + 28, y + 5, 92, 15, 4, wp.name, 0xFF1E1E28, ClientState.UI_TEXT, false, mx, my, click)) { int idx = 0; for (int i = 0; i < WP_NAME_PRESETS.length; i++) if (WP_NAME_PRESETS[i].equalsIgnoreCase(wp.name)) idx = (i + 1) % WP_NAME_PRESETS.length; wp.name = WP_NAME_PRESETS[idx]; ClientConfig.get().save(); return true; }
                if (drawSwitch(g, "wpt_" + wp.id, x + w - 82, y + 10, 38, 18, wp.enabled, accent, accentFg, mx, my, click)) { wp.enabled = !wp.enabled; ClientConfig.get().save(); return true; }
                if (drawBtn(g, "wpd_" + wp.id, x + w - 38, y + 10, 28, 18, 4, "Del", 0xFF3B1A1E, 0xFFFFFFFF, false, mx, my, click)) { ClientConfig.get().removeWaypoint(wp.id); return true; }
                return false;
            }
        };
    }
    private SettingRow keybindRow(final String label, final String desc, final String optId, final String def) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                boolean listening = optId.equals(listeningKeyOptId); String cur = ClientConfig.get().getString(optId, def).toUpperCase();
                if (!click) { g.drawGlassCard(x, y, w, 36, menuRad, 0xFF14141B, listening ? accent : ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false); g.text(desc, x + 12, y + 20, ClientState.UI_TEXT_DIM, false); }
                int bw = listening ? 104 : 84;
                if (drawBtn(g, "kb_" + optId, x + w - bw - 10, y + 8, bw, 20, Math.max(2, menuRad / 2), listening ? "> PRESS KEY <" : cur, listening ? accent : 0xFF1E1E28, listening ? accentFg : ClientState.UI_TEXT, listening, mx, my, click)) { listeningKeyOptId = listening ? null : optId; searchFocused = false; accountInputFocused = false; waypointInputFocused = false; return true; }
                return false;
            }
        };
    }
    private SettingRow profileRow(final String name, final String desc) {
        return new SettingRow() {
            @Override public int height() { return 38; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                boolean active = name.equalsIgnoreCase(ClientConfig.get().activeProfile);
                if (!click) { g.drawGlassCard(x, y, w, 38, menuRad, 0xFF14141B, active ? accent : ClientState.UI_BORDER); g.text(name + " Profile", x + 12, y + 8, ClientState.UI_TEXT, false); g.text(trimToWidth(g, desc, w - 105), x + 12, y + 21, ClientState.UI_TEXT_MUTED, false); }
                if (drawBtn(g, "prof_" + name, x + w - 76, y + 9, 66, 20, Math.max(2, menuRad / 2), active ? "ACTIVE" : "Apply", active ? accent : 0xFF1E1E28, active ? accentFg : ClientState.UI_TEXT, active, mx, my, click)) { ClientConfig.get().applyProfilePreset(name); return true; }
                return false;
            }
        };
    }
    private SettingRow boolRow(final String label, final String desc, final String optId, final boolean def) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                boolean val = ClientConfig.get().getBool(optId, def);
                if (!click) { if (mx >= x && mx <= x + w && my >= y && my <= y + 36) hoveredTooltip = desc; g.drawGlassCard(x, y, w, 36, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false); g.text(desc, x + 12, y + 20, ClientState.UI_TEXT_DIM, false); }
                if (drawSwitch(g, "opt_" + optId, x + w - 48, y + 9, 38, 18, val, accent, accentFg, mx, my, click)) { ClientConfig.get().setOption(optId, Boolean.valueOf(!val)); return true; }
                return false;
            }
        };
    }
    private SettingRow modBoolRow(final String label, final String desc, final String modId, final String field) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                ClientConfig.HudModule m = ClientConfig.get().hudModules.get(modId); if (m == null) return false;
                boolean val = "bg".equals(field) ? m.bg : "border".equals(field) ? m.border : "chroma".equals(field) ? m.chroma : "hideInF1".equals(field) ? m.hideInF1 : "hideInChat".equals(field) ? m.hideInChat : "hideInInventory".equals(field) ? m.hideInInventory : "hideInF3".equals(field) ? m.hideInF3 : m.shadow;
                if (!click) { g.drawGlassCard(x, y, w, 36, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false); g.text(desc, x + 12, y + 20, ClientState.UI_TEXT_DIM, false); }
                if (drawSwitch(g, "mb_" + modId + "_" + field, x + w - 48, y + 9, 38, 18, val, accent, accentFg, mx, my, click)) {
                    if ("bg".equals(field)) m.bg = !m.bg; else if ("border".equals(field)) m.border = !m.border; else if ("chroma".equals(field)) m.chroma = !m.chroma;
                    else if ("hideInF1".equals(field)) m.hideInF1 = !m.hideInF1; else if ("hideInChat".equals(field)) m.hideInChat = !m.hideInChat;
                    else if ("hideInInventory".equals(field)) m.hideInInventory = !m.hideInInventory; else if ("hideInF3".equals(field)) m.hideInF3 = !m.hideInF3; else m.shadow = !m.shadow;
                    ClientConfig.get().save(); return true;
                }
                return false;
            }
        };
    }
    private SettingRow modCycleRow(final String label, final String desc, final String modId, final String field, final String[] vals) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                ClientConfig.HudModule m = ClientConfig.get().hudModules.get(modId); if (m == null) return false;
                String cur = "anchor".equals(field) ? m.anchor : "borderStyle".equals(field) ? m.borderStyle : "bracketStyle".equals(field) ? m.bracketStyle : "displayMode".equals(field) ? m.displayMode : m.fontWeight;
                if (!click) { g.drawGlassCard(x, y, w, 36, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false); g.text(desc, x + 12, y + 20, ClientState.UI_TEXT_DIM, false); }
                if (drawBtn(g, "mc_" + modId + "_" + field, x + w - 102, y + 8, 92, 20, Math.max(2, menuRad / 2), cur.toUpperCase(), 0xFF1E1E28, ClientState.UI_TEXT, false, mx, my, click)) {
                    int next = 0; for (int i = 0; i < vals.length; i++) if (vals[i].equalsIgnoreCase(cur)) next = (i + 1) % vals.length;
                    if ("anchor".equals(field)) m.anchor = vals[next]; else if ("borderStyle".equals(field)) m.borderStyle = vals[next]; else if ("bracketStyle".equals(field)) m.bracketStyle = vals[next]; else if ("displayMode".equals(field)) m.displayMode = vals[next]; else m.fontWeight = vals[next];
                    ClientConfig.get().save(); return true;
                }
                return false;
            }
        };
    }
    private SettingRow cycleRow(final String label, final String desc, final String optId, final String[] values) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                String cur = ClientConfig.get().getString(optId, values[0]);
                if (!click) { if (mx >= x && mx <= x + w && my >= y && my <= y + 36) hoveredTooltip = desc; g.drawGlassCard(x, y, w, 36, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false); g.text(desc, x + 12, y + 20, ClientState.UI_TEXT_DIM, false); }
                if (drawBtn(g, "cyc_" + optId, x + w - 94, y + 8, 84, 20, Math.max(2, menuRad / 2), cur.toUpperCase(), 0xFF1E1E28, ClientState.UI_TEXT, false, mx, my, click)) {
                    int next = 0; for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(cur)) next = (i + 1) % values.length; ClientConfig.get().setOption(optId, values[next]); return true;
                }
                return false;
            }
        };
    }
    private SettingRow sliderRow(final String label, final String desc, final String sliderKey, final double min, final double max, final double step, final boolean isInt) {
        return new SettingRow() {
            @Override public int height() { return 44; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                double val = readSliderValue(sliderKey), pct = Math.max(0.0, Math.min(1.0, (val - min) / Math.max(0.0001, max - min)));
                int trackX = x + 12, trackY = y + 31, trackW = w - 24;
                if (!click) {
                    if (mx >= x && mx <= x + w && my >= y && my <= y + 44) hoveredTooltip = desc;
                    g.drawGlassCard(x, y, w, 44, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false);
                    String valStr = (("global_hud_rounding".equals(sliderKey) || "global_hud_opacity".equals(sliderKey)) && val < 0.0) ? "INDIV" : (isInt ? String.valueOf(Math.round(val)) : ((sliderKey.contains("opacity") || sliderKey.contains("alpha") || sliderKey.contains("topac") || sliderKey.contains("mod_x:") || sliderKey.contains("mod_y:") || "pvp_hurtcam_factor".equals(sliderKey)) ? (Math.round(val * 100) + "%") : String.format("%.2f", Double.valueOf(val))));
                    g.text(valStr, x + w - g.textWidth(valStr) - 12, y + 8, accent, false); g.text(desc, x + 12, y + 18, ClientState.UI_TEXT_DIM, false);
                    g.drawRoundedRect(trackX, trackY, trackW, 4, 2, ClientState.UI_BORDER); int fillW = (int) Math.round(trackW * pct); if (fillW > 2) g.drawRoundedRect(trackX, trackY, fillW, 4, 2, accent);
                    g.drawCircleFilled(trackX + fillW, trackY + 2, 4, ClientState.UI_TEXT);
                } else if (mx >= trackX - 5 && mx <= trackX + trackW + 5 && my >= trackY - 7 && my <= trackY + 11) { activeSliderId = sliderKey; sliderX = trackX; sliderW = trackW; sliderMin = min; sliderMax = max; sliderStep = step; applySliderValue(sliderKey, mx); return true; }
                return false;
            }
        };
    }
    private SettingRow colorRow(final String label, final String target) {
        return new SettingRow() {
            @Override public int height() { return 60; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                int curArgb = readColorTarget(target); String hex = ClientState.toHexColor(curArgb);
                if (!click) { g.drawGlassCard(x, y, w, 60, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label + "  " + hex.toUpperCase(), x + 12, y + 7, ClientState.UI_TEXT, false); g.drawCircleFilled(x + w - 16, y + 11, 5, curArgb); }
                int sx = x + 14, sy = y + 26;
                for (int i = 0; i < SWATCHES.length; i++) {
                    int swCol = ClientState.parseHexColor(SWATCHES[i], 0xFFFFFFFF); boolean sel = SWATCHES[i].equalsIgnoreCase(hex);
                    if (!click) { if (sel) g.drawCircleFilled(sx, sy, 7, accent); g.drawCircleFilled(sx, sy, 5, swCol); }
                    else if (mx >= sx - 6 && mx <= sx + 6 && my >= sy - 6 && my <= sy + 6) { g.playClickSound(); writeColorTarget(target, SWATCHES[i]); return true; }
                    sx += 18;
                }
                int rgbW = (w - 36) / 3; String[] chIds = {"rgb_r:" + target, "rgb_g:" + target, "rgb_b:" + target}; int[] chCols = {0xFFEF4444, 0xFF10B981, 0xFF3B82F6}, chVals = {(curArgb >>> 16) & 0xFF, (curArgb >>> 8) & 0xFF, curArgb & 0xFF};
                for (int c = 0; c < 3; c++) {
                    int rx = x + 12 + c * (rgbW + 6), ry = y + 46;
                    if (!click) { g.drawRoundedRect(rx, ry, rgbW, 4, 2, ClientState.UI_BORDER); int fw = (int) Math.round(rgbW * (chVals[c] / 255.0)); if (fw > 2) g.drawRoundedRect(rx, ry, fw, 4, 2, chCols[c]); g.drawCircleFilled(rx + fw, ry + 2, 4, ClientState.UI_TEXT); }
                    else if (mx >= rx - 4 && mx <= rx + rgbW + 4 && my >= ry - 6 && my <= ry + 10) { activeSliderId = chIds[c]; sliderX = rx; sliderW = rgbW; sliderMin = 0; sliderMax = 255; sliderStep = 1; applySliderValue(chIds[c], mx); return true; }
                }
                return false;
            }
        };
    }
    private SettingRow xhairPreviewRow() {
        return new SettingRow() {
            @Override public int height() { return 62; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (click) return false;
                ClientConfig cfg = ClientConfig.get(); int halfW = (w - 8) / 2;
                String baseStyle = cfg.getString("pvp_xhair_style", "classic"), rawTgtStyle = cfg.getString("pvp_xhair_target_style", "same");
                int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6)), gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2)), thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1))));
                int baseCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF), tgtCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);
                boolean tgtUseOwnCustom = "custom".equalsIgnoreCase(rawTgtStyle); String tgtStyle = "same".equalsIgnoreCase(rawTgtStyle) ? baseStyle : rawTgtStyle;
                for (int i = 0; i < 2; i++) {
                    int bx = x + i * (halfW + 8); g.drawGlassCard(bx, y, halfW, 62, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(i == 0 ? "DEFAULT" : "PLAYER IN VIEW", bx + 10, y + 6, ClientState.UI_TEXT_MUTED, false);
                    String st = i == 0 ? baseStyle : tgtStyle; if (!"vanilla".equalsIgnoreCase(st)) SharedHudRenderer.drawCrosshairShape(g, bx + halfW / 2, y + 36, st, sz, gap, thick, i == 0 ? baseCol : tgtCol, i == 1 && tgtUseOwnCustom); else g.text("Vanilla", bx + halfW / 2 - 14, y + 32, ClientState.UI_TEXT_DIM, false);
                }
                return false;
            }
        };
    }
    private SettingRow xhairPixelEditorRow() {
        return new SettingRow() {
            @Override public int height() { return 174; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                ClientConfig cfg = ClientConfig.get(); int n = SharedHudRenderer.XHAIR_GRID_SIZE; String optKey = editingTargetPixels ? "pvp_xhair_target_custom_pixels" : "pvp_xhair_custom_pixels";
                String pixels = normalizePixelGrid(cfg.getString(optKey, editingTargetPixels ? SharedHudRenderer.DEFAULT_TARGET_CUSTOM_PIXELS : SharedHudRenderer.DEFAULT_CUSTOM_PIXELS), n);
                int drawCol = ClientState.parseHexColor(cfg.getString(editingTargetPixels ? "pvp_xhair_target_color" : "pvp_xhair_color", editingTargetPixels ? "#ef4444" : "#ffffff"), editingTargetPixels ? 0xFFEF4444 : 0xFFFFFFFF);
                int cell = 10, gx = x + 12, gy = y + 19, gridPx = n * cell;
                if (!click) {
                    g.drawGlassCard(x, y, w, 174, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text("Custom Pixel Crosshair (15x15)", x + 12, y + 6, ClientState.UI_TEXT, false); g.drawRoundedRect(gx - 1, gy - 1, gridPx + 2, gridPx + 2, 4, ClientState.UI_BORDER);
                    for (int r = 0; r < n; r++) for (int c = 0; c < n; c++) {
                        int cx0 = gx + c * cell, cy0 = gy + r * cell; boolean on = pixels.charAt(r * n + c) == '1';
                        g.fill(cx0, cy0, cx0 + cell - 1, cy0 + cell - 1, on ? drawCol : ((r == n / 2 && c == n / 2) ? 0xFF2B2B38 : ((r + c) % 2 == 0 ? 0xFF181822 : 0xFF13131A)));
                        if (mx >= cx0 && mx < cx0 + cell && my >= cy0 && my < cy0 + cell) g.drawBorder(cx0, cy0, cell - 1, cell - 1, accent);
                    }
                } else if (mx >= gx && mx < gx + gridPx && my >= gy && my < gy + gridPx) {
                    g.playClickSound(); int col = (mx - gx) / cell, row = (my - gy) / cell; char[] arr = pixels.toCharArray(); char nv = arr[row * n + col] == '1' ? '0' : '1'; arr[row * n + col] = nv;
                    if (pixelMirrorX) arr[row * n + (n - 1 - col)] = nv; if (pixelMirrorY) arr[(n - 1 - row) * n + col] = nv; if (pixelMirrorX && pixelMirrorY) arr[(n - 1 - row) * n + (n - 1 - col)] = nv;
                    cfg.setOption(optKey, new String(arr)); return true;
                }
                int rx = gx + gridPx + 14, bw = Math.min(136, Math.max(90, w - (rx - x) - 12)), halfBw = (bw - 4) / 2, rBtn = Math.max(2, menuRad / 2);
                if (drawBtn(g, "px_mode", rx, gy, bw, 20, rBtn, editingTargetPixels ? "Edit: IN VIEW" : "Edit: DEFAULT", editingTargetPixels ? 0xFF7F1D1D : ClientState.withAlpha(accent, 45), ClientState.UI_TEXT, false, mx, my, click)) { editingTargetPixels = !editingTargetPixels; return true; }
                if (drawBtn(g, "px_mx", rx, gy + 24, halfBw, 18, rBtn, "MirX:" + (pixelMirrorX ? "ON" : "OFF"), pixelMirrorX ? ClientState.withAlpha(accent, 42) : 0xFF1A1A24, ClientState.UI_TEXT, false, mx, my, click)) { pixelMirrorX = !pixelMirrorX; return true; }
                if (drawBtn(g, "px_my", rx + halfBw + 4, gy + 24, bw - halfBw - 4, 18, rBtn, "MirY:" + (pixelMirrorY ? "ON" : "OFF"), pixelMirrorY ? ClientState.withAlpha(accent, 42) : 0xFF1A1A24, ClientState.UI_TEXT, false, mx, my, click)) { pixelMirrorY = !pixelMirrorY; return true; }
                if (drawBtn(g, "px_inv", rx, gy + 46, halfBw, 18, rBtn, "Invert", 0xFF1A1A24, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { char[] arr = pixels.toCharArray(); for (int i = 0; i < arr.length; i++) arr[i] = (arr[i] == '1') ? '0' : '1'; cfg.setOption(optKey, new String(arr)); return true; }
                if (drawBtn(g, "px_clr", rx + halfBw + 4, gy + 46, bw - halfBw - 4, 18, rBtn, "Clear", 0xFF3B1A1E, ClientState.UI_TEXT, false, mx, my, click)) { char[] arr = new char[n * n]; Arrays.fill(arr, '0'); cfg.setOption(optKey, new String(arr)); return true; }
                if (drawBtn(g, "px_pre1", rx, gy + 68, bw, 18, rBtn, "Preset: Cross", 0xFF1A1A24, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { cfg.setOption(optKey, SharedHudRenderer.DEFAULT_CUSTOM_PIXELS); return true; }
                if (drawBtn(g, "px_pre2", rx, gy + 90, bw, 18, rBtn, "Preset: Dot-X", 0xFF1A1A24, ClientState.UI_TEXT_MUTED, false, mx, my, click)) { cfg.setOption(optKey, SharedHudRenderer.DEFAULT_TARGET_CUSTOM_PIXELS); return true; }
                if (drawBtn(g, "px_use", rx, gy + 112, bw, 20, rBtn, editingTargetPixels ? "Use for In-View" : "Use for Default", accent, accentFg, true, mx, my, click)) { cfg.setOption(editingTargetPixels ? "pvp_xhair_target_style" : "pvp_xhair_style", "custom"); return true; }
                if (!click) { String activeSt = cfg.getString(editingTargetPixels ? "pvp_xhair_target_style" : "pvp_xhair_style", editingTargetPixels ? "same" : "classic"); g.text("Active: " + activeSt.toUpperCase(), rx, gy + 138, "custom".equalsIgnoreCase(activeSt) ? accent : ClientState.UI_TEXT_DIM, false); }
                return false;
            }
        };
    }
    private String normalizePixelGrid(String raw, int n) { int len = n * n; char[] out = new char[len]; Arrays.fill(out, '0'); if (raw != null) { int copy = Math.min(len, raw.length()); for (int i = 0; i < copy; i++) if (raw.charAt(i) == '1') out[i] = '1'; } return new String(out); }
    private SettingRow accentRow(final String label) {
        return new SettingRow() {
            @Override public int height() { return 46; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                String cur = ClientConfig.get().getString("ui_accent", "white");
                if (!click) { g.drawGlassCard(x, y, w, 46, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 7, ClientState.UI_TEXT, false); }
                int bx = x + 18, by = y + 28;
                int[] cols = {0xFFFFFFFF, 0xFFFF3333, 0xFF22D3EE, 0xFF10B981, 0xFFA855F7, 0xFFF59E0B, 0xFF84CC16, ClientState.parseHexColor(ClientConfig.get().getString("ui_accent_custom", "#ff3333"), 0xFFFF3333)};
                for (int i = 0; i < ACCENTS.length; i++) {
                    boolean sel = ACCENTS[i][0].equals(cur);
                    if (!click) { if (sel) g.drawCircleFilled(bx, by, 8, 0x55FFFFFF); g.drawCircleFilled(bx, by, 6, cols[i]); if (sel) g.drawCircleFilled(bx, by, 2, 0xFF09090B); }
                    else if (mx >= bx - 8 && mx <= bx + 8 && my >= by - 8 && my <= by + 8) { g.playClickSound(); ClientConfig.get().setOption("ui_accent", ACCENTS[i][0]); return true; }
                    bx += 22;
                }
                return false;
            }
        };
    }
    private SettingRow actionRow(final String label, final String desc, final String actId) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                if (!click) { g.drawGlassCard(x, y, w, 36, menuRad, 0xFF14141B, ClientState.UI_BORDER); g.text(label, x + 12, y + 8, ClientState.UI_TEXT, false); g.text(desc, x + 12, y + 20, ClientState.UI_TEXT_DIM, false); }
                boolean prim = "wp_add".equals(actId) || "title_editor".equals(actId);
                if (drawBtn(g, "act_" + actId, x + w - 74, y + 8, 64, 20, Math.max(2, menuRad / 2), "wp_add".equals(actId) ? "Add" : ("title_editor".equals(actId) ? "Edit" : "Run"), prim ? accent : 0xFF3B1A1E, prim ? accentFg : ClientState.UI_TEXT, prim, mx, my, click)) {
                    if ("wp_add".equals(actId)) ClientConfig.get().addWaypointAt("WP #" + (ClientConfig.get().waypoints.size() + 1), ClientState.getPlayerX(), ClientState.getPlayerY(), ClientState.getPlayerZ(), ClientState.getPlayerDim(), ClientState.getCurrentWorldKey());
                    else if ("title_editor".equals(actId)) { if (activeActions != null) activeActions.openTitleEditor(); }
                    else if ("kd_reset".equals(actId)) ClientState.resetKd();
                    else if ("reset_layout".equals(actId)) ClientConfig.get().resetLayout();
                    else if ("reset_all".equals(actId)) ClientConfig.get().resetAll();
                    else if ("sw_toggle".equals(actId)) ClientState.toggleStopwatch();
                    else if ("sw_reset".equals(actId)) ClientState.resetStopwatch();
                    else if ("cd_60".equals(actId)) ClientState.startCountdown(60);
                    return true;
                }
                return false;
            }
        };
    }

    private double readSliderValue(String key) {
        ClientConfig cfg = ClientConfig.get();
        if ("global_hud_rounding".equals(key)) return cfg.getDouble("global_hud_rounding", -1.0);
        if ("global_hud_opacity".equals(key)) return cfg.getDouble("global_hud_opacity", -1.0);
        if ("ui_menu_rounding".equals(key)) return cfg.getDouble("ui_menu_rounding", 10.0);
        if ("ui_card_width".equals(key)) return cfg.getDouble("ui_card_width", 138);
        if ("ui_anim_speed".equals(key)) return cfg.getDouble("ui_anim_speed", 1.0);
        if ("util_fullbright_level".equals(key)) return cfg.getDouble("util_fullbright_level", 12.0);
        if ("xhair_pixel_scale".equals(key)) return cfg.getDouble("pvp_xhair_pixel_scale", 1);
        if ("xhair_size".equals(key)) return cfg.getDouble("pvp_xhair_size", 6);
        if ("xhair_gap".equals(key)) return cfg.getDouble("pvp_xhair_gap", 2);
        if ("xhair_thick".equals(key)) return cfg.getDouble("pvp_xhair_thick", 1);
        if ("xhair_range".equals(key)) return cfg.getDouble("pvp_xhair_target_range", 12);
        if ("zoom_fac".equals(key)) return cfg.getDouble("cam_zoom_fac", 4);
        if ("cam_fov_factor".equals(key)) return cfg.getDouble("cam_fov_factor", 1.0);
        if ("pvp_hurtcam_factor".equals(key)) return cfg.getDouble("pvp_hurtcam_factor", 1.0);
        if ("fire_offset".equals(key)) return cfg.getDouble("pvp_low_fire_offset", -0.32);
        if ("pvp_low_shield_scale".equals(key)) return cfg.getDouble("pvp_low_shield_scale", 0.85);
        if ("pvp_low_shield_y".equals(key)) return cfg.getDouble("pvp_low_shield_y", -0.22);
        if ("pvp_low_shield_x".equals(key)) return cfg.getDouble("pvp_low_shield_x", 0.0);
        if ("pvp_hit_color_alpha".equals(key)) return cfg.getDouble("pvp_hit_color_alpha", 0.55);
        if ("hb_line_width".equals(key)) return cfg.getDouble("hb_line_width", 2.0);
        if ("hb_fill_alpha".equals(key)) return cfg.getDouble("hb_fill_alpha", 0.12);
        if ("bb_line_width".equals(key)) return cfg.getDouble("bb_line_width", 2.5);
        if ("bb_fill_alpha".equals(key)) return cfg.getDouble("bb_fill_alpha", 0.30);
        if ("pvp_small_totem_scale".equals(key)) return cfg.getDouble("pvp_small_totem_scale", 0.60);
        if ("pvp_small_totem_y".equals(key)) return cfg.getDouble("pvp_small_totem_y", -0.12);
        if (key.startsWith("vm_")) return cfg.getDouble(key, key.endsWith("_scale") ? 1.0 : 0.0);
        if (key.startsWith("mod_")) {
            ClientConfig.HudModule m = cfg.hudModules.get(key.substring(key.indexOf(':') + 1)); if (m == null) return 0;
            if (key.startsWith("mod_scale:")) return m.scale; if (key.startsWith("mod_x:")) return m.x; if (key.startsWith("mod_y:")) return m.y;
            if (key.startsWith("mod_w:")) return m.width; if (key.startsWith("mod_h:")) return m.height;
            if (key.startsWith("mod_padx:")) return m.paddingX; if (key.startsWith("mod_pady:")) return m.paddingY; if (key.startsWith("mod_margin:")) return m.margin;
            if (key.startsWith("mod_rad:")) return m.borderRadius; if (key.startsWith("mod_opacity:")) return m.opacity; if (key.startsWith("mod_topac:")) return m.textOpacity;
            if (key.startsWith("mod_bw:")) return m.borderWidth; if (key.startsWith("mod_cspeed:")) return m.chromaSpeed;
            if (key.startsWith("mod_csat:")) return m.chromaSat; if (key.startsWith("mod_cang:")) return m.chromaAngle; if (key.startsWith("mod_lspace:")) return m.letterSpacing;
        }
        return 0;
    }

    private void applySliderValue(String key, int mx) {
        double pct = Math.max(0.0, Math.min(1.0, (mx - sliderX) / (double) Math.max(1, sliderW)));
        double snapped = Math.max(sliderMin, Math.min(sliderMax, Math.round((sliderMin + pct * (sliderMax - sliderMin)) / sliderStep) * sliderStep));
        ClientConfig cfg = ClientConfig.get();
        if ("global_hud_rounding".equals(key)) cfg.setOption("global_hud_rounding", Integer.valueOf(snapped < 0 ? -1 : (int) Math.round(snapped)));
        else if ("global_hud_opacity".equals(key)) cfg.setOption("global_hud_opacity", Double.valueOf(snapped < 0 ? -1.0 : Math.round(snapped * 100.0) / 100.0));
        else if ("ui_menu_rounding".equals(key)) cfg.setOption("ui_menu_rounding", Integer.valueOf((int) Math.round(snapped)));
        else if ("ui_card_width".equals(key)) cfg.setOption("ui_card_width", Integer.valueOf((int) Math.round(snapped)));
        else if ("ui_anim_speed".equals(key)) cfg.setOption("ui_anim_speed", Double.valueOf(Math.round(snapped * 10.0) / 10.0));
        else if ("util_fullbright_level".equals(key)) cfg.setOption("util_fullbright_level", Double.valueOf(Math.round(snapped * 10.0) / 10.0));
        else if ("xhair_pixel_scale".equals(key)) cfg.setOption("pvp_xhair_pixel_scale", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_size".equals(key)) cfg.setOption("pvp_xhair_size", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_gap".equals(key)) cfg.setOption("pvp_xhair_gap", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_thick".equals(key)) cfg.setOption("pvp_xhair_thick", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_range".equals(key)) cfg.setOption("pvp_xhair_target_range", Integer.valueOf((int) Math.round(snapped)));
        else if ("zoom_fac".equals(key)) cfg.setOption("cam_zoom_fac", Integer.valueOf((int) Math.round(snapped)));
        else if ("fire_offset".equals(key)) cfg.setOption("pvp_low_fire_offset", Double.valueOf(Math.round(snapped * 100.0) / 100.0));
        else if ("cam_fov_factor".equals(key) || "pvp_hurtcam_factor".equals(key) || key.startsWith("pvp_low_shield_") || "pvp_hit_color_alpha".equals(key) || key.startsWith("hb_") || key.startsWith("bb_") || key.startsWith("vm_") || key.startsWith("pvp_small_totem_")) {
            cfg.setOption(key, Double.valueOf(Math.round(snapped * 100.0) / 100.0));
        } else if (key.startsWith("mod_")) {
            ClientConfig.HudModule m = cfg.hudModules.get(key.substring(key.indexOf(':') + 1));
            if (m != null) {
                int iv = (int) Math.round(snapped);
                if (key.startsWith("mod_scale:")) m.scale = snapped; else if (key.startsWith("mod_x:")) m.x = snapped; else if (key.startsWith("mod_y:")) m.y = snapped;
                else if (key.startsWith("mod_w:")) m.width = iv; else if (key.startsWith("mod_h:")) m.height = iv;
                else if (key.startsWith("mod_padx:")) m.paddingX = iv; else if (key.startsWith("mod_pady:")) m.paddingY = iv; else if (key.startsWith("mod_margin:")) m.margin = iv;
                else if (key.startsWith("mod_rad:")) m.borderRadius = iv; else if (key.startsWith("mod_opacity:")) m.opacity = snapped; else if (key.startsWith("mod_topac:")) m.textOpacity = snapped;
                else if (key.startsWith("mod_bw:")) m.borderWidth = iv; else if (key.startsWith("mod_cspeed:")) m.chromaSpeed = snapped;
                else if (key.startsWith("mod_csat:")) m.chromaSat = snapped; else if (key.startsWith("mod_cang:")) m.chromaAngle = iv; else if (key.startsWith("mod_lspace:")) m.letterSpacing = iv;
            }
        } else if (key.startsWith("rgb_")) {
            String target = key.substring(6); int cur = readColorTarget(target), r = (cur >>> 16) & 0xFF, g = (cur >>> 8) & 0xFF, b = cur & 0xFF, val = (int) Math.round(snapped);
            if (key.startsWith("rgb_r:")) r = val; else if (key.startsWith("rgb_g:")) g = val; else b = val;
            writeColorTarget(target, String.format("#%02x%02x%02x", Integer.valueOf(r), Integer.valueOf(g), Integer.valueOf(b)));
        }
    }

    private int readColorTarget(String target) {
        if (target.startsWith("opt:")) return ClientState.parseHexColor(ClientConfig.get().getString(target.substring(4), "#ffffff"), 0xFFFFFFFF);
        if (target.startsWith("mod_bg:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(7)); return m != null ? ClientState.parseHexColor(m.bgColor, 0xFF09090B) : 0xFF09090B; }
        if (target.startsWith("mod_brd2:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(9)); return m != null ? ClientState.parseHexColor(m.borderColor2, 0xFFFFFFFF) : 0xFFFFFFFF; }
        if (target.startsWith("mod_brd:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(8)); return m != null ? ClientState.parseHexColor(m.borderColor, 0xFF27272A) : 0xFF27272A; }
        if (target.startsWith("mod:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(4)); return m != null ? ClientState.parseHexColor(m.color, 0xFFFFFFFF) : 0xFFFFFFFF; }
        return 0xFFFFFFFF;
    }

    private void writeColorTarget(String target, String hex) {
        if (target.startsWith("opt:")) ClientConfig.get().setOption(target.substring(4), hex);
        else if (target.startsWith("mod_bg:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(7)); if (m != null) { m.bgColor = hex; ClientConfig.get().save(); } }
        else if (target.startsWith("mod_brd2:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(9)); if (m != null) { m.borderColor2 = hex; ClientConfig.get().save(); } }
        else if (target.startsWith("mod_brd:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(8)); if (m != null) { m.borderColor = hex; ClientConfig.get().save(); } }
        else if (target.startsWith("mod:")) { ClientConfig.HudModule m = ClientConfig.get().hudModules.get(target.substring(4)); if (m != null) { m.color = hex; m.chroma = false; ClientConfig.get().save(); } }
    }

    private boolean isModEnabled(String id) {
        ClientConfig cfg = ClientConfig.get();
        if ("crosshair".equals(id)) return !"vanilla".equalsIgnoreCase(cfg.getString("pvp_xhair_style", "classic"));
        if ("zoom".equals(id)) return cfg.getBool("cam_zoom", true);
        if ("freelook".equals(id)) return cfg.getBool("util_freelook", true);
        if ("fullbright".equals(id)) return cfg.getBool("util_fullbright", false);
        if ("fov_mod".equals(id)) return !cfg.getBool("cam_fov_dynamic", true) || cfg.getDouble("cam_fov_factor", 1.0) != 1.0 || !cfg.getBool("cam_fov_sprint", true) || !cfg.getBool("cam_fov_bow", true);
        if ("hurtcam".equals(id)) return cfg.getBool("pvp_no_hurtcam", false) || cfg.getDouble("pvp_hurtcam_factor", 1.0) < 0.99;
        if ("low_fire".equals(id)) return cfg.getBool("pvp_low_fire", true);
        if ("low_shield".equals(id)) return cfg.getBool("pvp_low_shield", false);
        if ("viewmodel".equals(id)) return cfg.getBool("vm_enabled", false) || cfg.getBool("pvp_small_totem", false);
        if ("hit_color".equals(id)) return cfg.getBool("pvp_hit_color", false);
        if ("hitboxes".equals(id)) return cfg.getBool("hb_enabled", false);
        if ("block_break".equals(id)) return cfg.getBool("bb_enabled", false);
        ClientConfig.HudModule m = cfg.hudModules.get(id); return m != null && m.enabled;
    }

    private void toggleMod(String id) {
        ClientConfig cfg = ClientConfig.get();
        if ("crosshair".equals(id)) cfg.setOption("pvp_xhair_style", "vanilla".equalsIgnoreCase(cfg.getString("pvp_xhair_style", "classic")) ? "classic" : "vanilla");
        else if ("zoom".equals(id)) cfg.setOption("cam_zoom", Boolean.valueOf(!cfg.getBool("cam_zoom", true)));
        else if ("freelook".equals(id)) cfg.setOption("util_freelook", Boolean.valueOf(!cfg.getBool("util_freelook", true)));
        else if ("fullbright".equals(id)) cfg.setOption("util_fullbright", Boolean.valueOf(!cfg.getBool("util_fullbright", false)));
        else if ("fov_mod".equals(id)) cfg.setOption("cam_fov_dynamic", Boolean.valueOf(!cfg.getBool("cam_fov_dynamic", true)));
        else if ("hurtcam".equals(id)) cfg.setOption("pvp_no_hurtcam", Boolean.valueOf(!cfg.getBool("pvp_no_hurtcam", false)));
        else if ("low_fire".equals(id)) cfg.setOption("pvp_low_fire", Boolean.valueOf(!cfg.getBool("pvp_low_fire", true)));
        else if ("low_shield".equals(id)) cfg.setOption("pvp_low_shield", Boolean.valueOf(!cfg.getBool("pvp_low_shield", false)));
        else if ("viewmodel".equals(id)) cfg.setOption("vm_enabled", Boolean.valueOf(!cfg.getBool("vm_enabled", false)));
        else if ("hit_color".equals(id)) cfg.setOption("pvp_hit_color", Boolean.valueOf(!cfg.getBool("pvp_hit_color", false)));
        else if ("hitboxes".equals(id)) cfg.setOption("hb_enabled", Boolean.valueOf(!cfg.getBool("hb_enabled", false)));
        else if ("block_break".equals(id)) cfg.setOption("bb_enabled", Boolean.valueOf(!cfg.getBool("bb_enabled", false)));
        else { ClientConfig.HudModule m = cfg.hudModules.get(id); if (m != null) cfg.toggleModule(m); }
    }

    private String getModTitle(String id) { for (ModEntry m : ALL_MODS) if (m.id.equals(id)) return m.name; return "Module Settings"; }
    private String getMenuTitle(String id) { return "favorites".equals(id) ? "Favorites" : "waypoints".equals(id) ? "Waypoints" : "system".equals(id) ? "System & Perf" : "pvp".equals(id) ? "PvP & Combat" : "world".equals(id) ? "World & Survival" : "server".equals(id) ? "Server & Minigames" : "visuals".equals(id) ? "Visuals & HUD" : "utility".equals(id) ? "Core Utilities" : "All Mods"; }
    private String trimToWidth(UiBridge.UiDraw g, String s, int maxW) { if (g.textWidth(s) <= maxW) return s; while (s.length() > 3 && g.textWidth(s + "..") > maxW) s = s.substring(0, s.length() - 1); return s + ".."; }
    private List<String> wrapText(UiBridge.UiDraw g, String text, int maxW, int maxLines) {
        List<String> lines = new ArrayList<String>(); String[] words = text.split(" "); StringBuilder cur = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String test = cur.length() == 0 ? words[i] : (cur.toString() + " " + words[i]);
            if (g.textWidth(test) <= maxW) cur.append(cur.length() == 0 ? "" : " ").append(words[i]);
            else { if (cur.length() > 0) lines.add(cur.toString()); if (lines.size() >= maxLines - 1) { lines.add(trimToWidth(g, words[i], maxW)); return lines; } cur = new StringBuilder(words[i]); }
        }
        if (cur.length() > 0 && lines.size() < maxLines) lines.add(cur.toString()); return lines;
    }
    private void drawScrollbar(UiBridge.UiDraw g, int x, int y, int viewH, int totalH, int curScroll, int accent) {
        g.drawRoundedRect(x, y, 4, viewH, 2, ClientState.UI_BORDER);
        int thumbH = Math.max(18, (viewH * viewH) / Math.max(1, totalH));
        g.drawRoundedRect(x, y + (int) Math.round(((viewH - thumbH) * (double) curScroll) / Math.max(1, maxScrollPx)), 4, thumbH, 2, accent);
    }
    private boolean drawBtn(UiBridge.UiDraw g, String id, int x, int y, int w, int h, int rad, String txt, int bg, int fg, boolean solidAccent, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) { g.drawGlassCard(x, y, w, h, rad, ClientState.lerpColor(bg, ClientState.brightenArgb(bg, solidAccent ? -20 : 18), animHover(id, hov)), solidAccent ? bg : ClientState.UI_BORDER); g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 7) / 2, fg, false); }
        if (hov && click) { g.playClickSound(); return true; } return false;
    }
    private boolean drawSwitch(UiBridge.UiDraw g, String id, int x, int y, int w, int h, boolean on, int accent, int accentFg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            String style = ClientConfig.get().getString("ui_switch_style", "pill"); float t = animPill(id, on);
            if ("checkbox".equalsIgnoreCase(style)) {
                int bx = x + w - h; g.drawGlassCard(bx, y, h, h, 4, ClientState.lerpColor(ClientState.UI_INPUT, accent, t), on ? accent : ClientState.UI_BORDER_HOV);
                if (on) g.text("v", bx + (h - g.textWidth("v")) / 2, y + (h - 7) / 2, accentFg, false);
                g.text(on ? "ON" : "OFF", x + 2, y + (h - 7) / 2, on ? accent : ClientState.UI_TEXT_DIM, false);
            } else {
                int r = h / 2; g.drawGlassCard(x, y, w, h, r, ClientState.lerpColor(0xFF181822, accent, t), ClientState.lerpColor(ClientState.UI_BORDER, accent, t));
                g.drawCircleFilled(Math.round((x + 2 + (r - 2)) + (w - 4 - (r - 2) * 2) * t), y + r, r - 2, 0xFFFFFFFF);
            }
        }
        if (hov && click) { g.playClickSound(); return true; } return false;
    }
}