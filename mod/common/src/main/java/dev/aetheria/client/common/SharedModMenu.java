package dev.aetheria.client.common;

import java.util.*;

public class SharedModMenu {
    public interface Actions {
        void openHudEditor();
        void closeMenu();
    }

    private static final class ModEntry {
        final String id, name, category, desc;
        final boolean isHud;
        ModEntry(String id, String name, String category, String desc, boolean isHud) {
            this.id = id; this.name = name; this.category = category; this.desc = desc; this.isHud = isHud;
        }
    }

    private static final List<ModEntry> ALL_MODS = new ArrayList<ModEntry>();
    private static boolean initialized = false;

    private static void ensureMods() {
        if (initialized) return;
        initialized = true;
        ALL_MODS.add(new ModEntry("crosshair", "Crosshair", "utility", "Dynamic shapes, pixel editor & Player-in-View switch", false));
        ALL_MODS.add(new ModEntry("zoom", "Optical Zoom", "utility", "Telescopic FOV zoom with smooth easing", false));
        ALL_MODS.add(new ModEntry("fullbright", "Fullbright", "utility", "Clean max gamma night vision boost", false));
        ALL_MODS.add(new ModEntry("freelook", "FreeLook 360", "utility", "Detached 360-degree perspective camera", false));
        ALL_MODS.add(new ModEntry("low_fire", "Low Fire", "utility", "Lowers first-person fire screen overlay", false));
        ALL_MODS.add(new ModEntry("low_shield", "Low Shield", "utility", "Lowers off-hand shield blocking viewmodel", false));
        ALL_MODS.add(new ModEntry("time_lock", "Client Time", "utility", "Client-side time of day visual override", false));
        ALL_MODS.add(new ModEntry("weather_lock", "Client Weather", "utility", "Client-side clear/rain visual override", false));
        for (ClientConfig.HudModule m : ClientConfig.get().hudModules.values()) {
            ALL_MODS.add(new ModEntry(m.id, m.label, m.category, m.desc, true));
        }
    }

    private static final String[] SWATCHES = {
        "#ffffff", "#ff3333", "#09090b", "#27272a", "#a1a1aa",
        "#22d3ee", "#10b981", "#a855f7", "#f43f5e", "#f59e0b"
    };
    private static final String[] ANCHORS = {"top-left","top-center","top-right","mid-left","center","mid-right","bottom-left","bottom-center","bottom-right"};
    private static final String[] BORDER_STYLES = {"solid", "dashed", "gradient"};
    private static final String[] BRACKETS = {"none", "square", "paren", "pipe", "angle"};
    private static final String[] DISP_MODES = {"icon_text", "text_only", "icon_only"};
    private static final String[] FONT_WEIGHTS = {"normal", "bold"};
    private static final String[] XHAIR_STYLES = {"vanilla", "classic", "dot", "circle", "t_shape", "arrows", "x_cross", "custom"};
    private static final String[] TARGET_STYLES = {"same", "classic", "dot", "circle", "t_shape", "arrows", "x_cross", "custom"};
    private static final String[] MENU_LAYOUTS = {"topbar", "sidebar", "grid", "palette"};
    private static final String[] SWITCH_STYLES = {"pill", "ios", "checkbox"};
    private static final String[] ANIM_CURVES = {"smooth", "snappy"};
    private static final String[] TIME_LOCKS = {"off", "day", "noon", "sunset", "night"};
    private static final String[] WEATHER_LOCKS = {"off", "clear", "rain"};
    private static final String[] SPEED_UNITS = {"bps", "kmh", "mph"};
    private static final String[] ZOOM_KEYS = {"C", "V", "Z", "X", "G", "R", "ALT"};
    private static final String[] MENU_KEYS = {"RSHIFT", "M", "H", "INSERT", "K", "P"};
    private static final String[] PROF_KEYS = {"F6", "F7", "F8", "P", "K"};
    private static final String[][] ACCENTS = {
        {"white", "Obsidian"}, {"crimson", "Crimson"}, {"cyan", "Cyan"}, {"emerald", "Emerald"},
        {"amethyst", "Amethyst"}, {"amber", "Amber"}, {"lime", "Lime"}, {"custom", "Custom"}
    };

    private String activeMenu = "all", editingModId = null, inspectorTab = "geom", searchQuery = "";
    private boolean searchFocused = false;
    private boolean editingTargetPixels = false, pixelMirrorX = true, pixelMirrorY = false;
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

    public SharedModMenu() { ensureMods(); }
    public SharedModMenu(String initialModId) { ensureMods(); this.editingModId = initialModId; }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        ensureMods();
        long now = System.nanoTime();
        frameDt = Math.min(0.05F, Math.max(0.001F, (now - lastFrameNs) / 1_000_000_000.0F));
        lastFrameNs = now;

        ClientConfig cfg = ClientConfig.get();
        boolean anim = cfg.getBool("ui_animations", true);
        double spd = Math.max(0.5, cfg.getDouble("ui_anim_speed", 1.0));
        boolean snappy = "snappy".equalsIgnoreCase(cfg.getString("ui_anim_curve", "smooth"));
        float mult = (float) (spd * (snappy ? 1.5 : 1.0));

        if (anim) scrollPx += (targetScrollPx - scrollPx) * Math.min(1.0F, frameDt * 18.0F * mult);
        else scrollPx = targetScrollPx;

        float openT = anim ? Math.min(1.0F, ((now - openedNs) / 150_000_000.0F) * mult) : 1.0F;
        float ease = 1.0F - (float) Math.pow(1.0F - openT, 3);
        float scale = 0.95F + 0.05F * ease;

        int worldBlur = (int) Math.round(cfg.getDouble("ui_world_blur", 10));
        int bgAlpha = Math.min(195, 70 + worldBlur * 4);
        g.fill(0, 0, sw, sh, ClientState.scaleAlpha((bgAlpha << 24), ease));

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
        targetScrollPx = Math.max(0.0F, Math.min(maxScrollPx, targetScrollPx - (float) (scrollY * 36.0)));
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
        ClientConfig cfg = ClientConfig.get();
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();
        int menuRad = Math.max(0, Math.min(24, (int) Math.round(cfg.getDouble("ui_menu_rounding", 8))));
        int menuAlpha = (int) Math.round(Math.max(0.4, Math.min(1.0, cfg.getDouble("ui_menu_opacity", 0.86))) * 255.0);
        int panelBg = ClientState.withAlpha(0xFF09090B, menuAlpha);
        String layout = cfg.getString("ui_menu_layout", "topbar");

        int panelW = Math.min(640, sw - 16), panelH = Math.min(390, sh - 16);
        int px = (sw - panelW) / 2, py = (sh - panelH) / 2;
        int topH = 38;

        if (!click) {
            g.drawSoftShadow(px, py, panelW, panelH, menuRad);
            g.drawGlassCard(px, py, panelW, panelH, menuRad, panelBg, ClientState.UI_BORDER_HOV);
            g.drawRoundedRect(px + 1, py + 1, panelW - 2, topH - 1, Math.max(0, menuRad - 1), ClientState.UI_TITLEBAR);
            g.fill(px + 1, py + topH, px + panelW - 1, py + topH + 1, ClientState.UI_BORDER);
            g.text("AETHERIA", px + 12, py + 15, ClientState.UI_TEXT, true);
            g.drawGlassCard(px + 64, py + 12, 42, 13, 4, ClientState.withAlpha(accent, 30), ClientState.UI_BORDER);
            g.text(cfg.activeProfile.toUpperCase(), px + 68, py + 15, accent, false);
        }

        int closeX = px + panelW - 28, closeY = py + 9;
        boolean closeHov = mx >= closeX && mx <= closeX + 20 && my >= closeY && my <= closeY + 20;
        if (!click) {
            float ht = animHover("win_close", closeHov);
            int cBg = ClientState.lerpColor(0x00000000, ClientState.UI_DANGER, ht);
            if ((cBg >>> 24) > 4) g.drawRoundedRect(closeX, closeY, 20, 20, 5, cBg);
            g.text("x", closeX + 7, closeY + 6, closeHov ? 0xFFFFFFFF : ClientState.UI_TEXT_MUTED, false);
        } else if (closeHov) {
            cfg.save(); if (actions != null) actions.closeMenu(); return true;
        }

        int ehW = 58, ehX = closeX - ehW - 6;
        if (drawBtn(g, "btn_edithud", ehX, py + 9, ehW, 20, 6, "Edit HUD", accent, accentFg, true, mx, my, click)) {
            if (actions != null) actions.openHudEditor(); return true;
        }
        int layW = 52, layX = ehX - layW - 5;
        if (drawBtn(g, "btn_lay", layX, py + 9, layW, 20, 6, layout.toUpperCase(), ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
            int next = 0; for (int i = 0; i < MENU_LAYOUTS.length; i++) if (MENU_LAYOUTS[i].equalsIgnoreCase(layout)) next = (i + 1) % MENU_LAYOUTS.length;
            cfg.setOption("ui_menu_layout", MENU_LAYOUTS[next]); return true;
        }

        String[][] cats = {{"all","All"},{"favorites","Fav"},{"system","Sys"},{"pvp","PvP"},{"world","World"},{"server","Srv"},{"visuals","Vis"},{"utility","Util"},{"profiles","Prof"},{"ui_engine","UI"}};
        int cx = px + 12, cy = py + topH + 8, cw = panelW - 24, ch = panelH - topH - 16;

        if ("sidebar".equals(layout)) {
            int sideW = 86;
            if (!click) g.fill(px + sideW + 8, py + topH + 4, px + sideW + 9, py + panelH - 6, ClientState.UI_BORDER);
            for (int i = 0; i < cats.length; i++) {
                int by = py + topH + 8 + i * 24;
                if (by + 20 > py + panelH - 6) break;
                boolean sel = editingModId == null && activeMenu.equals(cats[i][0]);
                if (drawBtn(g, "sb_" + cats[i][0], px + 10, by, sideW - 6, 20, 5, cats[i][1], sel ? ClientState.withAlpha(accent, 40) : 0x00000000, sel ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    activeMenu = cats[i][0]; editingModId = null; searchFocused = false; resetScroll(); return true;
                }
            }
            cx = px + sideW + 16; cw = panelW - sideW - 28;
        } else {
            int navX = px + 112, navRight = layX - 6;
            for (int i = 0; i < cats.length; i++) {
                String id = cats[i][0], lbl = cats[i][1];
                int bw = g.textWidth(lbl) + 10;
                if (navX + bw > navRight) break;
                boolean sel = (editingModId == null) && activeMenu.equals(id);
                boolean hov = mx >= navX && mx <= navX + bw && my >= py + 9 && my <= py + 29;
                if (!click) {
                    float ht = animHover("nav_" + id, hov);
                    int bg = sel ? ClientState.withAlpha(accent, 34) : ClientState.lerpColor(0x00000000, ClientState.withAlpha(accent, 18), ht);
                    int brd = sel ? accent : ClientState.lerpColor(0x00000000, ClientState.UI_BORDER, ht);
                    if ((bg >>> 24) > 4 || (brd >>> 24) > 4) g.drawGlassCard(navX, py + 9, bw, 20, 6, bg, brd);
                    g.text(lbl, navX + 5, py + 15, sel || hov ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false);
                } else if (hov) {
                    activeMenu = id; editingModId = null; searchFocused = false; resetScroll(); return true;
                }
                navX += bw + 2;
            }
        }

        if (editingModId != null) return renderModSettingsPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("ui_engine".equals(activeMenu)) return renderUiEnginePage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);
        if ("profiles".equals(activeMenu)) return renderProfilesPage(g, cx, cy, cw, ch, mx, my, click, accent, accentFg, menuRad);

        boolean isPalette = "palette".equals(layout);
        if (isPalette && !click) searchFocused = true;
        if (!click) g.text(getMenuTitle(activeMenu) + (isPalette ? " (Command Palette)" : ""), cx + 2, cy + 4, ClientState.UI_TEXT, true);
        int sbW = isPalette ? Math.min(240, cw - 110) : Math.min(165, cw / 2), sbX = cx + cw - sbW, sbY = cy;
        boolean sHov = mx >= sbX && mx <= sbX + sbW && my >= sbY && my <= sbY + 20;
        if (!click) {
            g.drawGlassCard(sbX, sbY, sbW, 20, 6, ClientState.UI_INPUT, searchFocused ? accent : (sHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            String disp = searchQuery.isEmpty() ? (searchFocused ? "_" : "Search 100+ HUDs...") : (searchQuery + (searchFocused ? "_" : ""));
            g.text(disp, sbX + 7, sbY + 6, searchQuery.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
            if (!searchQuery.isEmpty()) g.text("x", sbX + sbW - 12, sbY + 6, ClientState.UI_TEXT_MUTED, false);
        } else if (sHov) {
            if (!searchQuery.isEmpty() && mx >= sbX + sbW - 16) { searchQuery = ""; resetScroll(); }
            else searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        return renderModCardGrid(g, cx, cy + 26, cw, ch - 26, mx, my, click, accent, accentFg, menuRad, isPalette);
    }

    private boolean renderModCardGrid(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad, boolean paletteList) {
        List<ModEntry> list = new ArrayList<ModEntry>();
        String q = searchQuery.trim().toLowerCase();
        for (ModEntry m : ALL_MODS) {
            if ("favorites".equals(activeMenu)) {
                ClientConfig.HudModule hm = ClientConfig.get().hudModules.get(m.id);
                if (hm == null || !hm.favorite) continue;
            } else if (!"all".equals(activeMenu) && !m.category.equals(activeMenu)) continue;
            if (!q.isEmpty() && !m.name.toLowerCase().contains(q) && !m.desc.toLowerCase().contains(q) && !m.category.toLowerCase().contains(q)) continue;
            list.add(m);
        }

        if (paletteList) {
            int rowH = 34, gap = 5, totalH = Math.max(0, list.size() * (rowH + gap) - gap);
            maxScrollPx = Math.max(0, totalH - ch);
            if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
            int curScroll = Math.round(scrollPx), rw = cw - 10;
            for (int i = 0; i < list.size(); i++) {
                ModEntry m = list.get(i);
                int ry = cy + i * (rowH + gap) - curScroll;
                if (ry < cy || ry + rowH > cy + ch) continue;
                boolean en = isModEnabled(m.id);
                if (!click) {
                    g.drawGlassCard(cx, ry, rw, rowH, Math.min(7, menuRad), ClientState.UI_CARD, en ? accent : ClientState.UI_BORDER);
                    g.text(m.name + "  [" + m.category.toUpperCase() + "]", cx + 10, ry + 7, ClientState.UI_TEXT, true);
                    g.text(trimToWidth(g, m.desc, rw - 150), cx + 10, ry + 19, ClientState.UI_TEXT_MUTED, false);
                }
                if (drawBtn(g, "pcfg_" + m.id, cx + rw - 118, ry + 8, 58, 18, 5, "Config", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    editingModId = m.id; resetScroll(); return true;
                }
                if (drawSwitch(g, "ptog_" + m.id, cx + rw - 54, ry + 8, 44, 18, en, accent, accentFg, mx, my, click)) {
                    toggleMod(m.id); return true;
                }
            }
            if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, curScroll, accent);
            return false;
        }

        int targetCardW = (int) Math.round(ClientConfig.get().getDouble("ui_card_width", 140));
        int cols = Math.max(2, Math.min(5, (cw - 6) / Math.max(105, targetCardW)));
        int gap = 6, trackW = 6, cardW = (cw - trackW - gap * (cols - 1)) / cols, cardH = 128;
        int rows = (int) Math.ceil(list.size() / (double) cols);
        int totalH = Math.max(0, rows * (cardH + gap) - gap);
        maxScrollPx = Math.max(0, totalH - ch);
        if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx);

        if (!click && list.isEmpty()) g.text("No matching modules.", cx + 8, cy + 16, ClientState.UI_TEXT_MUTED, false);

        for (int i = 0; i < list.size(); i++) {
            ModEntry m = list.get(i);
            int r = i / cols, c = i % cols;
            int bx = cx + c * (cardW + gap), by = cy + r * (cardH + gap) - curScroll;
            if (by + cardH < cy || by > cy + ch) continue;
            int topVis = Math.max(cy, by), botVis = Math.min(cy + ch, by + cardH);
            if (botVis - topVis < 18) continue;

            boolean enabled = isModEnabled(m.id);
            ClientConfig.HudModule hm = ClientConfig.get().hudModules.get(m.id);
            boolean fav = hm != null && hm.favorite;
            boolean hov = mx >= bx && mx <= bx + cardW && my >= topVis && my <= botVis;
            int cardRad = Math.min(12, menuRad);

            if (!click) {
                float ht = animHover("card_" + m.id, hov);
                int bg = ClientState.lerpColor(ClientState.UI_CARD, ClientState.UI_CARD_HOVER, ht);
                int brd = enabled ? ClientState.lerpColor(ClientState.withAlpha(accent, 150), accent, ht) : ClientState.lerpColor(ClientState.UI_BORDER, ClientState.UI_BORDER_HOV, ht);
                g.drawGlassCard(bx, topVis, cardW, botVis - topVis, cardRad, bg, brd);

                if (by + 8 >= cy && by + 18 <= cy + ch) {
                    g.drawCircleFilled(bx + 8, by + 12, 3, enabled ? accent : ClientState.UI_TEXT_DIM);
                    g.text(m.category.toUpperCase(), bx + 15, by + 9, ClientState.UI_TEXT_DIM, false);
                }
                if (by + 23 >= cy && by + 33 <= cy + ch) g.text(trimToWidth(g, m.name, cardW - 14), bx + 8, by + 23, ClientState.UI_TEXT, true);
                if (by + 36 >= cy && by + 72 <= cy + ch) {
                    List<String> wrap = wrapText(g, m.desc, cardW - 16, 3);
                    for (int l = 0; l < wrap.size(); l++) g.text(wrap.get(l), bx + 8, by + 37 + l * 10, ClientState.UI_TEXT_MUTED, false);
                }
            }
            if (hm != null && by + 6 >= cy && by + 20 <= cy + ch) {
                if (drawBtn(g, "fav_" + m.id, bx + cardW - 20, by + 6, 14, 14, 4, fav ? "*" : ".", 0x00000000, fav ? accent : ClientState.UI_TEXT_DIM, false, mx, my, click)) {
                    hm.favorite = !hm.favorite; ClientConfig.get().save(); return true;
                }
            }
            if (by + 80 >= cy && by + 124 <= cy + ch) {
                int btnW = cardW - 14;
                if (drawBtn(g, "cfg_" + m.id, bx + 7, by + 80, btnW, 18, 5, "Configure", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    editingModId = m.id; resetScroll(); return true;
                }
                if (drawSwitch(g, "tog_" + m.id, bx + 7, by + 102, btnW, 18, enabled, accent, accentFg, mx, my, click)) {
                    toggleMod(m.id); return true;
                }
            }
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, cy, ch, totalH, curScroll, accent);
        return false;
    }

    private boolean renderModSettingsPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        ClientConfig cfg = ClientConfig.get();
        boolean isHud = cfg.hudModules.containsKey(editingModId), enabled = isModEnabled(editingModId);

        if (drawBtn(g, "set_back", cx, cy, 48, 20, 6, "< Back", ClientState.UI_CARD, ClientState.UI_TEXT, false, mx, my, click)) {
            editingModId = null; resetScroll(); return true;
        }
        if (!click) g.text(getModTitle(editingModId), cx + 56, cy + 6, ClientState.UI_TEXT, true);
        if (isHud && drawBtn(g, "set_rst", cx + cw - 110, cy, 56, 20, 6, "Reset", ClientState.UI_CARD, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
            cfg.resetModule(editingModId); return true;
        }
        if (drawSwitch(g, "set_tog_" + editingModId, cx + cw - 46, cy + 1, 46, 18, enabled, accent, accentFg, mx, my, click)) {
            toggleMod(editingModId); return true;
        }

        int listTop = cy + 25;
        if (isHud) {
            String[][] tabs = {{"geom","Geometry"},{"style","Style & Glass"},{"text","Text & Format"},{"vis","Visibility"}};
            int tx = cx;
            for (int i = 0; i < tabs.length; i++) {
                int tw = g.textWidth(tabs[i][1]) + 14;
                boolean sel = inspectorTab.equals(tabs[i][0]);
                if (drawBtn(g, "itab_" + tabs[i][0], tx, listTop, tw, 18, 5, tabs[i][1], sel ? ClientState.withAlpha(accent, 42) : ClientState.UI_INPUT, sel ? ClientState.UI_TEXT : ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    inspectorTab = tabs[i][0]; resetScroll(); return true;
                }
                tx += tw + 4;
            }
            listTop += 24;
        }

        int listH = (cy + ch) - listTop, rw = cw - 10;
        List<SettingRow> rows = new ArrayList<SettingRow>();
        if ("crosshair".equals(editingModId)) buildCrosshairRows(rows);
        else if ("zoom".equals(editingModId)) buildZoomRows(rows);
        else if ("low_fire".equals(editingModId)) buildLowFireRows(rows);
        else if ("low_shield".equals(editingModId)) rows.add(boolRow("Low Shield Viewmodel", "Lowers off-hand shield while blocking", "pvp_low_shield", true));
        else if ("fullbright".equals(editingModId)) rows.add(boolRow("Fullbright", "Clean maximum gamma night vision", "util_fullbright", false));
        else if ("freelook".equals(editingModId)) {
            rows.add(boolRow("FreeLook 360", "Allow 360-degree camera detachment", "util_freelook", true));
            rows.add(cycleRow("FreeLook Keybind", "Hold key to look around freely", "util_freelook_key", ZOOM_KEYS));
        } else if ("time_lock".equals(editingModId)) rows.add(cycleRow("Client Time Lock", "Visual-only world time override", "util_time_lock", TIME_LOCKS));
        else if ("weather_lock".equals(editingModId)) rows.add(cycleRow("Client Weather Lock", "Visual-only weather override", "util_weather_lock", WEATHER_LOCKS));
        else if (isHud) buildHudInspectorRows(rows, cfg.hudModules.get(editingModId));

        return renderScrollableRows(g, rows, cx, listTop, rw, listH, cw, mx, my, click, accent, accentFg, menuRad);
    }

    private void buildHudInspectorRows(List<SettingRow> rows, ClientConfig.HudModule m) {
        if ("geom".equals(inspectorTab)) {
            rows.add(modCycleRow("Anchor Lock (9-Point)", "Reference anchor point on screen", m.id, "anchor", ANCHORS));
            rows.add(sliderRow("Scale Multiplier", "Resize HUD from 0.25x to 4.0x", "mod_scale:" + m.id, 0.25, 4.0, 0.05, false));
            rows.add(sliderRow("X Coordinate", "Horizontal anchor position (0-100%)", "mod_x:" + m.id, 0.0, 1.0, 0.005, false));
            rows.add(sliderRow("Y Coordinate", "Vertical anchor position (0-100%)", "mod_y:" + m.id, 0.0, 1.0, 0.005, false));
            rows.add(sliderRow("Width Override (0=Auto)", "Fixed box width in pixels", "mod_w:" + m.id, 0, 320, 4, true));
            rows.add(sliderRow("Height Override (0=Auto)", "Fixed box height in pixels", "mod_h:" + m.id, 0, 180, 2, true));
            rows.add(sliderRow("Horizontal Padding", "Inner X spacing (1-24px)", "mod_padx:" + m.id, 1, 24, 1, true));
            rows.add(sliderRow("Vertical Padding", "Inner Y spacing (1-24px)", "mod_pady:" + m.id, 1, 24, 1, true));
            rows.add(sliderRow("Margin Offset", "Outer offset from anchor (0-16px)", "mod_margin:" + m.id, 0, 16, 1, true));
        } else if ("style".equals(inspectorTab)) {
            rows.add(sliderRow("Corner Rounding", "0px sharp to 30px pill", "mod_rad:" + m.id, 0, 30, 1, true));
            rows.add(modBoolRow("Background Box", "Draw glass backdrop container", m.id, "bg"));
            rows.add(colorRow("Background Color", "mod_bg:" + m.id));
            rows.add(sliderRow("Background Opacity", "Backdrop transparency (0-100%)", "mod_opacity:" + m.id, 0.0, 1.0, 0.05, false));
            rows.add(sliderRow("Backdrop Blur Intensity", "Glassmorphism blur hint (0-30px)", "mod_blur:" + m.id, 0, 30, 1, true));
            rows.add(modBoolRow("Border Outline", "Draw border around module", m.id, "border"));
            rows.add(sliderRow("Border Thickness", "Stroke width (0-5px)", "mod_bw:" + m.id, 0, 5, 1, true));
            rows.add(modCycleRow("Border Style", "Solid, Dashed, or Gradient", m.id, "borderStyle", BORDER_STYLES));
            rows.add(colorRow("Border Color 1", "mod_brd:" + m.id));
            rows.add(colorRow("Border Color 2 (Grad)", "mod_brd2:" + m.id));
            rows.add(sliderRow("Drop Shadow Blur", "Soft shadow spread (0-8px)", "mod_dsblur:" + m.id, 0, 8, 1, true));
            rows.add(sliderRow("Drop Shadow X", "Horizontal shadow offset", "mod_dsx:" + m.id, -6, 6, 1, true));
            rows.add(sliderRow("Drop Shadow Y", "Vertical shadow offset", "mod_dsy:" + m.id, -6, 6, 1, true));
            rows.add(modBoolRow("Chroma RGB Wave", "Animated rainbow color wave", m.id, "chroma"));
            rows.add(sliderRow("Chroma Wave Speed", "Cycle speed multiplier", "mod_cspeed:" + m.id, 0.2, 4.0, 0.1, false));
            rows.add(sliderRow("Chroma Saturation", "Color intensity", "mod_csat:" + m.id, 0.1, 1.0, 0.05, false));
            rows.add(sliderRow("Chroma Angle", "Wave direction in degrees", "mod_cang:" + m.id, 0, 360, 15, true));
        } else if ("text".equals(inspectorTab)) {
            if ("fps".equals(m.id)) { rows.add(boolRow("Show 'FPS' Label", "Display 'FPS' suffix", "fps_show_label", true)); rows.add(boolRow("Show 1% Low & Min", "Two-line detailed FPS stats", "fps_detailed", false)); }
            if ("coords".equals(m.id)) { rows.add(boolRow("Show Facing", "Include direction", "coords_facing", true)); rows.add(boolRow("Nether 8:1 Coords", "Auto-convert X/Z", "coords_nether", true)); rows.add(boolRow("Compact Line", "Single-line XYZ", "coords_compact", false)); }
            if ("clock".equals(m.id)) { rows.add(boolRow("24-Hour Format", "24h vs AM/PM", "clock_24h", false)); rows.add(boolRow("Show Seconds", "Display :SS", "clock_seconds", false)); }
            if ("speedometer".equals(m.id)) rows.add(cycleRow("Speed Unit", "b/s, km/h, or mph", "speed_unit", SPEED_UNITS));
            if ("keystrokes".equals(m.id)) { rows.add(boolRow("Show CPS", "LMB/RMB counters", "keystrokes_cps", true)); rows.add(boolRow("Show Spacebar", "Space key", "keystrokes_space", true)); rows.add(boolRow("Show Shift/Ctrl", "Modifier keys", "keystrokes_mods", false)); }
            rows.add(colorRow("Text / Icon Color", "mod:" + m.id));
            rows.add(sliderRow("Text Opacity", "Foreground text alpha (10-100%)", "mod_topac:" + m.id, 0.1, 1.0, 0.05, false));
            rows.add(modBoolRow("Text Drop Shadow", "Shadow behind text", m.id, "shadow"));
            rows.add(modCycleRow("Font Weight", "Normal or Bold", m.id, "fontWeight", FONT_WEIGHTS));
            rows.add(sliderRow("Letter Spacing", "Extra pixel tracking (0-3px)", "mod_lspace:" + m.id, 0, 3, 1, true));
            rows.add(modCycleRow("Bracket Style", "Wrap text in [], (), ||, <>", m.id, "bracketStyle", BRACKETS));
            rows.add(modCycleRow("Display Mode", "Icon+Text, Text Only, Icon Only", m.id, "displayMode", DISP_MODES));
        } else {
            rows.add(modBoolRow("Hide in F1 Mode", "Auto-hide during cinematic F1", m.id, "hideInF1"));
            rows.add(modBoolRow("Hide in Chat", "Auto-hide when chat is open", m.id, "hideInChat"));
            rows.add(modBoolRow("Hide in Inventory", "Auto-hide inside containers", m.id, "hideInInventory"));
            rows.add(modBoolRow("Hide in F3 Debug", "Auto-hide when F3 overlay is open", m.id, "hideInF3"));
            if ("stopwatch".equals(m.id)) {
                rows.add(actionRow("Stopwatch Toggle", "Start or pause stopwatch timer", "sw_toggle"));
                rows.add(actionRow("Stopwatch Reset", "Reset stopwatch to 00:00.0", "sw_reset"));
            }
            if ("countdown".equals(m.id)) rows.add(actionRow("Start 60s Countdown", "Starts a 1-minute countdown alert", "cd_60"));
        }
    }

    private boolean renderUiEnginePage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        if (!click) g.text("Global UI Engine & Theme", cx + 2, cy + 4, ClientState.UI_TEXT, true);
        List<SettingRow> rows = new ArrayList<SettingRow>();
        rows.add(cycleRow("Menu Layout Mode", "Topbar, Sidebar, Grid, or Command Palette", "ui_menu_layout", MENU_LAYOUTS));
        rows.add(cycleRow("Toggle Switch Style", "Pill, iOS Switch, or Checkbox", "ui_switch_style", SWITCH_STYLES));
        rows.add(sliderRow("Module Card Width", "Grid card width (105-200px)", "ui_card_width", 105, 200, 5, true));
        rows.add(sliderRow("Menu Corner Rounding", "0px sharp to 24px rounded", "menu_rounding", 0, 24, 1, true));
        rows.add(sliderRow("Menu BG Opacity", "ClickGUI glass opacity (40-100%)", "ui_menu_opacity", 0.4, 1.0, 0.05, false));
        rows.add(sliderRow("World Background Blur", "Depth-of-field dim behind ClickGUI", "ui_world_blur", 0, 30, 1, true));
        rows.add(accentRow("Accent Color (Incl. #FF3333 Crimson)"));
        rows.add(colorRow("Custom Accent HEX", "opt:ui_accent_custom"));
        rows.add(boolRow("UI Animations", "Smooth fade, scale & spring easing", "ui_animations", true));
        rows.add(sliderRow("Animation Speed", "Transition speed (0.5x-2.5x)", "ui_anim_speed", 0.5, 2.5, 0.1, false));
        rows.add(cycleRow("Animation Curve", "Smooth vs Snappy easing", "ui_anim_curve", ANIM_CURVES));
        rows.add(boolRow("Escape Menu Overhaul", "Styled pause menu & quick shortcuts", "esc_overhaul", true));
        rows.add(boolRow("Container Theming", "Custom glass tint for chests/inventories", "container_theme", true));
        rows.add(boolRow("HUD Magnetic Snap Grid", "Snap to 40-step grid in HUD Editor", "hud_snap_grid", true));
        rows.add(boolRow("HUD Alignment Guides", "Show center snap lines while dragging", "hud_align_guides", true));
        rows.add(boolRow("Global HUD Text Shadow", "Master drop shadow toggle", "hud_shadow", true));
        rows.add(cycleRow("Menu Keybind", "Open Aetheria ClickGUI", "ctl_menu_key", MENU_KEYS));
        rows.add(cycleRow("Profile Cycle Keybind", "Instant hotkey to switch HUD profile", "ctl_profile_key", PROF_KEYS));
        rows.add(actionRow("Reset All HUD Positions", "Restore default positions & layout", "reset_layout"));
        rows.add(actionRow("Reset All Client Settings", "Factory reset all options", "reset_all"));
        return renderScrollableRows(g, rows, cx, cy + 22, cw - 10, ch - 22, cw, mx, my, click, accent, accentFg, menuRad);
    }

    private boolean renderProfilesPage(UiBridge.UiDraw g, int cx, int cy, int cw, int ch, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        if (!click) g.text("HUD Profiles (Active: " + ClientConfig.get().activeProfile + ")", cx + 2, cy + 4, ClientState.UI_TEXT, true);
        List<SettingRow> rows = new ArrayList<SettingRow>();
        rows.add(profileRow("PvP", "FPS, Ping, CPS, Keystrokes, Target HUD, Combo, Reach, Potions & Armor"));
        rows.add(profileRow("Survival", "Coords + Nether, Biome, Light Level, Day/Weather, Y-Alert, WAILA & Bag"));
        rows.add(profileRow("SMP", "Coords, Ping, TPS, Server IP, Players, Waypoints, Death Tracker & Totems"));
        rows.add(profileRow("Minimal", "Ultra-clean FPS, Coordinates & Ping only"));
        rows.add(profileRow("Cinematic", "Zero clutter - hides HUDs except active Zoom badge"));
        return renderScrollableRows(g, rows, cx, cy + 22, cw - 10, ch - 22, cw, mx, my, click, accent, accentFg, menuRad);
    }

    private boolean renderScrollableRows(UiBridge.UiDraw g, List<SettingRow> rows, int cx, int listY, int rw, int listH, int cw, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
        int totalH = 0;
        for (int i = 0; i < rows.size(); i++) totalH += rows.get(i).height() + 6;
        maxScrollPx = Math.max(0, totalH - listH);
        if (targetScrollPx > maxScrollPx) targetScrollPx = maxScrollPx;
        int curScroll = Math.round(scrollPx), ry = listY - curScroll;
        for (int i = 0; i < rows.size(); i++) {
            SettingRow row = rows.get(i);
            int rh = row.height();
            if (ry >= listY && ry + rh <= listY + listH) {
                if (row.renderOrHandle(g, cx, ry, rw, mx, my, click, accent, accentFg, menuRad)) return true;
            }
            ry += rh + 6;
        }
        if (!click && maxScrollPx > 0) drawScrollbar(g, cx + cw - 4, listY, listH, totalH, curScroll, accent);
        return false;
    }

    private void buildCrosshairRows(List<SettingRow> rows) {
        rows.add(xhairPreviewRow());
        rows.add(xhairPixelEditorRow());
        rows.add(cycleRow("Default Shape", "Choose 'CUSTOM' to use your Default Pixel Grid", "pvp_xhair_style", XHAIR_STYLES));
        rows.add(sliderRow("Custom Pixel Size", "Scale of each pixel (1x-3x) when using CUSTOM", "xhair_pixel_scale", 1, 3, 1, true));
        rows.add(sliderRow("Size", "Arm length or radius (preset shapes)", "xhair_size", 2, 16, 1, true));
        rows.add(sliderRow("Center Gap", "Center spacing (preset shapes)", "xhair_gap", 0, 8, 1, true));
        rows.add(sliderRow("Thickness", "Pixel width (1-3px, preset shapes)", "xhair_thick", 1, 3, 1, true));
        rows.add(colorRow("Crosshair Color", "opt:pvp_xhair_color"));
        rows.add(boolRow("Player-in-View Switch", "Change shape/color when aiming at a target", "pvp_xhair_target", true));
        rows.add(boolRow("Players Only", "ON = Players | OFF = All Mobs", "pvp_xhair_target_players_only", true));
        rows.add(sliderRow("Detection Range", "Max distance in blocks", "xhair_range", 3, 32, 1, true));
        rows.add(cycleRow("In-View Shape", "Choose 'CUSTOM' to use your Player-in-View Pixel Grid", "pvp_xhair_target_style", TARGET_STYLES));
        rows.add(colorRow("In-View Color", "opt:pvp_xhair_target_color"));
    }

    private void buildZoomRows(List<SettingRow> rows) {
        rows.add(sliderRow("Zoom Magnification", "Optical zoom (2x-12x)", "zoom_fac", 2, 12, 1, true));
        rows.add(boolRow("Smooth Zoom Easing", "Smooth FOV transitions", "cam_zoom_ease", true));
        rows.add(boolRow("Slow Mouse While Zoomed", "Precision aiming sensitivity", "cam_zoom_slow", true));
        rows.add(cycleRow("Zoom Keybind", "Hold key to zoom", "cam_zoom_key", ZOOM_KEYS));
    }

    private void buildLowFireRows(List<SettingRow> rows) {
        rows.add(boolRow("Low Fire Overlay", "Lower first-person flame height", "pvp_low_fire", true));
        rows.add(sliderRow("Fire Vertical Offset", "Flame height offset", "fire_offset", -0.50, 0.0, 0.02, false));
    }

    private interface SettingRow {
        int height();
        boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad);
    }

    private SettingRow profileRow(final String name, final String desc) {
        return new SettingRow() {
            @Override public int height() { return 38; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                boolean active = name.equalsIgnoreCase(ClientConfig.get().activeProfile);
                if (!click) {
                    g.drawGlassCard(x, y, w, 38, Math.min(8, menuRad), ClientState.UI_CARD, active ? accent : ClientState.UI_BORDER);
                    g.text(name + " Profile", x + 10, y + 8, ClientState.UI_TEXT, true);
                    g.text(trimToWidth(g, desc, w - 105), x + 10, y + 21, ClientState.UI_TEXT_MUTED, false);
                }
                if (drawBtn(g, "prof_" + name, x + w - 76, y + 9, 66, 20, 6, active ? "ACTIVE" : "Apply", active ? accent : ClientState.UI_INPUT, active ? accentFg : ClientState.UI_TEXT, active, mx, my, click)) {
                    ClientConfig.get().applyProfilePreset(name); return true;
                }
                return false;
            }
        };
    }

    private SettingRow boolRow(final String label, final String desc, final String optId, final boolean def) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                boolean val = ClientConfig.get().getBool(optId, def);
                if (!click) {
                    g.drawGlassCard(x, y, w, 36, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 20, ClientState.UI_TEXT_DIM, false);
                }
                if (drawSwitch(g, "opt_" + optId, x + w - 48, y + 9, 38, 16, val, accent, accentFg, mx, my, click)) {
                    ClientConfig.get().setOption(optId, Boolean.valueOf(!val)); return true;
                }
                return false;
            }
        };
    }

    private SettingRow modBoolRow(final String label, final String desc, final String modId, final String field) {
        return new SettingRow() {
            @Override public int height() { return 36; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                ClientConfig.HudModule m = ClientConfig.get().hudModules.get(modId);
                if (m == null) return false;
                boolean val = "bg".equals(field) ? m.bg : "border".equals(field) ? m.border : "chroma".equals(field) ? m.chroma : "hideInF1".equals(field) ? m.hideInF1 : "hideInChat".equals(field) ? m.hideInChat : "hideInInventory".equals(field) ? m.hideInInventory : "hideInF3".equals(field) ? m.hideInF3 : m.shadow;
                if (!click) {
                    g.drawGlassCard(x, y, w, 36, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 20, ClientState.UI_TEXT_DIM, false);
                }
                if (drawSwitch(g, "mb_" + modId + "_" + field, x + w - 48, y + 9, 38, 16, val, accent, accentFg, mx, my, click)) {
                    if ("bg".equals(field)) m.bg = !m.bg; else if ("border".equals(field)) m.border = !m.border; else if ("chroma".equals(field)) m.chroma = !m.chroma;
                    else if ("hideInF1".equals(field)) m.hideInF1 = !m.hideInF1; else if ("hideInChat".equals(field)) m.hideInChat = !m.hideInChat;
                    else if ("hideInInventory".equals(field)) m.hideInInventory = !m.hideInInventory; else if ("hideInF3".equals(field)) m.hideInF3 = !m.hideInF3;
                    else m.shadow = !m.shadow;
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
                ClientConfig.HudModule m = ClientConfig.get().hudModules.get(modId);
                if (m == null) return false;
                String cur = "anchor".equals(field) ? m.anchor : "borderStyle".equals(field) ? m.borderStyle : "bracketStyle".equals(field) ? m.bracketStyle : "displayMode".equals(field) ? m.displayMode : m.fontWeight;
                if (!click) {
                    g.drawGlassCard(x, y, w, 36, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 20, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, "mc_" + modId + "_" + field, x + w - 102, y + 8, 92, 19, 5, cur.toUpperCase(), ClientState.UI_INPUT, ClientState.UI_TEXT, false, mx, my, click)) {
                    int next = 0; for (int i = 0; i < vals.length; i++) if (vals[i].equalsIgnoreCase(cur)) next = (i + 1) % vals.length;
                    if ("anchor".equals(field)) m.anchor = vals[next]; else if ("borderStyle".equals(field)) m.borderStyle = vals[next];
                    else if ("bracketStyle".equals(field)) m.bracketStyle = vals[next]; else if ("displayMode".equals(field)) m.displayMode = vals[next];
                    else m.fontWeight = vals[next];
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
                if (!click) {
                    g.drawGlassCard(x, y, w, 36, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 20, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, "cyc_" + optId, x + w - 94, y + 8, 84, 19, 5, cur.toUpperCase(), ClientState.UI_INPUT, ClientState.UI_TEXT, false, mx, my, click)) {
                    int next = 0; for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(cur)) next = (i + 1) % values.length;
                    ClientConfig.get().setOption(optId, values[next]); return true;
                }
                return false;
            }
        };
    }

    private SettingRow sliderRow(final String label, final String desc, final String sliderKey, final double min, final double max, final double step, final boolean isInt) {
        return new SettingRow() {
            @Override public int height() { return 44; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                double val = readSliderValue(sliderKey);
                double pct = Math.max(0.0, Math.min(1.0, (val - min) / Math.max(0.0001, max - min)));
                int trackX = x + 10, trackY = y + 31, trackW = w - 20;
                if (!click) {
                    g.drawGlassCard(x, y, w, 44, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    String valStr = isInt ? String.valueOf(Math.round(val)) : (sliderKey.contains("opacity") || sliderKey.contains("topac") || sliderKey.contains("mod_x:") || sliderKey.contains("mod_y:") ? (Math.round(val * 100) + "%") : String.format("%.2f", Double.valueOf(val)));
                    g.text(valStr, x + w - g.textWidth(valStr) - 10, y + 8, accent, false);
                    g.text(desc, x + 10, y + 18, ClientState.UI_TEXT_DIM, false);
                    g.drawRoundedRect(trackX, trackY, trackW, 4, 2, ClientState.UI_BORDER);
                    int fillW = (int) Math.round(trackW * pct);
                    if (fillW > 2) g.drawRoundedRect(trackX, trackY, fillW, 4, 2, accent);
                    g.drawCircleFilled(trackX + fillW, trackY + 2, 4, ClientState.UI_TEXT);
                } else if (mx >= trackX - 5 && mx <= trackX + trackW + 5 && my >= trackY - 7 && my <= trackY + 11) {
                    activeSliderId = sliderKey; sliderX = trackX; sliderW = trackW; sliderMin = min; sliderMax = max; sliderStep = step;
                    applySliderValue(sliderKey, mx); return true;
                }
                return false;
            }
        };
    }

    private SettingRow colorRow(final String label, final String target) {
        return new SettingRow() {
            @Override public int height() { return 60; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                int curArgb = readColorTarget(target);
                String hex = ClientState.toHexColor(curArgb);
                if (!click) {
                    g.drawGlassCard(x, y, w, 60, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label + "  " + hex.toUpperCase(), x + 10, y + 7, ClientState.UI_TEXT, false);
                    g.drawCircleFilled(x + w - 16, y + 11, 5, curArgb);
                }
                int sx = x + 14, sy = y + 26;
                for (int i = 0; i < SWATCHES.length; i++) {
                    int swCol = ClientState.parseHexColor(SWATCHES[i], 0xFFFFFFFF);
                    boolean sel = SWATCHES[i].equalsIgnoreCase(hex);
                    if (!click) {
                        if (sel) g.drawCircleFilled(sx, sy, 7, accent);
                        g.drawCircleFilled(sx, sy, 5, swCol);
                    } else if (mx >= sx - 6 && mx <= sx + 6 && my >= sy - 6 && my <= sy + 6) {
                        writeColorTarget(target, SWATCHES[i]); return true;
                    }
                    sx += 18;
                }
                int rgbW = (w - 36) / 3;
                String[] chIds = {"rgb_r:" + target, "rgb_g:" + target, "rgb_b:" + target};
                int[] chCols = {0xFFEF4444, 0xFF10B981, 0xFF3B82F6};
                int[] chVals = {(curArgb >>> 16) & 0xFF, (curArgb >>> 8) & 0xFF, curArgb & 0xFF};
                for (int c = 0; c < 3; c++) {
                    int rx = x + 10 + c * (rgbW + 8), ry = y + 46;
                    if (!click) {
                        g.drawRoundedRect(rx, ry, rgbW, 4, 2, ClientState.UI_BORDER);
                        int fw = (int) Math.round(rgbW * (chVals[c] / 255.0));
                        if (fw > 2) g.drawRoundedRect(rx, ry, fw, 4, 2, chCols[c]);
                        g.drawCircleFilled(rx + fw, ry + 2, 4, ClientState.UI_TEXT);
                    } else if (mx >= rx - 4 && mx <= rx + rgbW + 4 && my >= ry - 6 && my <= ry + 10) {
                        activeSliderId = chIds[c]; sliderX = rx; sliderW = rgbW; sliderMin = 0; sliderMax = 255; sliderStep = 1;
                        applySliderValue(chIds[c], mx); return true;
                    }
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
                ClientConfig cfg = ClientConfig.get();
                int halfW = (w - 8) / 2;
                String baseStyle = cfg.getString("pvp_xhair_style", "classic");
                int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6)), gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2)), thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1))));
                int baseCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF);
                String rawTgtStyle = cfg.getString("pvp_xhair_target_style", "same");
                boolean tgtUseOwnCustom = "custom".equalsIgnoreCase(rawTgtStyle);
                String tgtStyle = "same".equalsIgnoreCase(rawTgtStyle) ? baseStyle : rawTgtStyle;
                int tgtCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);
                for (int i = 0; i < 2; i++) {
                    int bx = x + i * (halfW + 8);
                    g.drawGlassCard(bx, y, halfW, 62, 6, 0xCC161A22, ClientState.UI_BORDER);
                    g.text(i == 0 ? "DEFAULT" : "PLAYER IN VIEW", bx + 8, y + 6, ClientState.UI_TEXT_MUTED, false);
                    String st = i == 0 ? baseStyle : tgtStyle;
                    if (!"vanilla".equalsIgnoreCase(st)) SharedHudRenderer.drawCrosshairShape(g, bx + halfW / 2, y + 36, st, sz, gap, thick, i == 0 ? baseCol : tgtCol, i == 1 && tgtUseOwnCustom);
                    else g.text("Vanilla", bx + halfW / 2 - 14, y + 32, ClientState.UI_TEXT_DIM, false);
                }
                return false;
            }
        };
    }

    private SettingRow xhairPixelEditorRow() {
        return new SettingRow() {
            @Override public int height() { return 174; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                ClientConfig cfg = ClientConfig.get();
                int n = SharedHudRenderer.XHAIR_GRID_SIZE;
                String optKey = editingTargetPixels ? "pvp_xhair_target_custom_pixels" : "pvp_xhair_custom_pixels";
                String defVal = editingTargetPixels ? SharedHudRenderer.DEFAULT_TARGET_CUSTOM_PIXELS : SharedHudRenderer.DEFAULT_CUSTOM_PIXELS;
                String pixels = normalizePixelGrid(cfg.getString(optKey, defVal), n);
                int drawCol = ClientState.parseHexColor(
                    cfg.getString(editingTargetPixels ? "pvp_xhair_target_color" : "pvp_xhair_color", editingTargetPixels ? "#ef4444" : "#ffffff"),
                    editingTargetPixels ? 0xFFEF4444 : 0xFFFFFFFF
                );

                int cell = 10;
                int gx = x + 10, gy = y + 19;
                int gridPx = n * cell;

                if (!click) {
                    g.drawGlassCard(x, y, w, 174, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text("Custom Pixel Crosshair (15x15)", x + 10, y + 6, ClientState.UI_TEXT, true);
                    g.drawRoundedRect(gx - 1, gy - 1, gridPx + 2, gridPx + 2, 2, ClientState.UI_BORDER);
                    for (int r = 0; r < n; r++) {
                        for (int c = 0; c < n; c++) {
                            int cx0 = gx + c * cell, cy0 = gy + r * cell;
                            boolean on = pixels.charAt(r * n + c) == '1';
                            boolean isCenter = (r == n / 2 && c == n / 2);
                            boolean cellHov = mx >= cx0 && mx < cx0 + cell && my >= cy0 && my < cy0 + cell;
                            int bg = on ? drawCol : (isCenter ? 0xFF27272A : ((r + c) % 2 == 0 ? 0xFF141418 : 0xFF101014));
                            g.fill(cx0, cy0, cx0 + cell - 1, cy0 + cell - 1, bg);
                            if (cellHov) g.drawBorder(cx0, cy0, cell - 1, cell - 1, accent);
                        }
                    }
                } else if (mx >= gx && mx < gx + gridPx && my >= gy && my < gy + gridPx) {
                    int col = (mx - gx) / cell;
                    int row = (my - gy) / cell;
                    char[] arr = pixels.toCharArray();
                    char nextVal = arr[row * n + col] == '1' ? '0' : '1';
                    arr[row * n + col] = nextVal;
                    if (pixelMirrorX) arr[row * n + (n - 1 - col)] = nextVal;
                    if (pixelMirrorY) arr[(n - 1 - row) * n + col] = nextVal;
                    if (pixelMirrorX && pixelMirrorY) arr[(n - 1 - row) * n + (n - 1 - col)] = nextVal;
                    cfg.setOption(optKey, new String(arr));
                    return true;
                }

                int rx = gx + gridPx + 12;
                int bw = Math.min(136, Math.max(90, w - (rx - x) - 10));
                int halfBw = (bw - 4) / 2;

                if (drawBtn(g, "px_mode", rx, gy, bw, 19, 5, editingTargetPixels ? "Edit: PLAYER IN VIEW" : "Edit: DEFAULT", editingTargetPixels ? 0xCC7F1D1D : ClientState.withAlpha(accent, 45), ClientState.UI_TEXT, false, mx, my, click)) {
                    editingTargetPixels = !editingTargetPixels; return true;
                }
                if (drawBtn(g, "px_mx", rx, gy + 23, halfBw, 18, 5, "MirX:" + (pixelMirrorX ? "ON" : "OFF"), pixelMirrorX ? ClientState.withAlpha(accent, 42) : ClientState.UI_INPUT, ClientState.UI_TEXT, false, mx, my, click)) {
                    pixelMirrorX = !pixelMirrorX; return true;
                }
                if (drawBtn(g, "px_my", rx + halfBw + 4, gy + 23, bw - halfBw - 4, 18, 5, "MirY:" + (pixelMirrorY ? "ON" : "OFF"), pixelMirrorY ? ClientState.withAlpha(accent, 42) : ClientState.UI_INPUT, ClientState.UI_TEXT, false, mx, my, click)) {
                    pixelMirrorY = !pixelMirrorY; return true;
                }
                if (drawBtn(g, "px_inv", rx, gy + 45, halfBw, 18, 5, "Invert", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    char[] arr = pixels.toCharArray();
                    for (int i = 0; i < arr.length; i++) arr[i] = (arr[i] == '1') ? '0' : '1';
                    cfg.setOption(optKey, new String(arr)); return true;
                }
                if (drawBtn(g, "px_clr", rx + halfBw + 4, gy + 45, bw - halfBw - 4, 18, 5, "Clear", 0xCC3B1A1E, ClientState.UI_TEXT, false, mx, my, click)) {
                    char[] arr = new char[n * n];
                    Arrays.fill(arr, '0');
                    cfg.setOption(optKey, new String(arr)); return true;
                }
                if (drawBtn(g, "px_pre1", rx, gy + 67, bw, 18, 5, "Reset Default Cross", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    cfg.setOption(optKey, SharedHudRenderer.DEFAULT_CUSTOM_PIXELS); return true;
                }
                if (drawBtn(g, "px_pre2", rx, gy + 89, bw, 18, 5, "Reset In-View X", ClientState.UI_INPUT, ClientState.UI_TEXT_MUTED, false, mx, my, click)) {
                    cfg.setOption(optKey, SharedHudRenderer.DEFAULT_TARGET_CUSTOM_PIXELS); return true;
                }
                if (drawBtn(g, "px_use", rx, gy + 111, bw, 19, 5, editingTargetPixels ? "Use for In-View" : "Use for Default", accent, accentFg, true, mx, my, click)) {
                    cfg.setOption(editingTargetPixels ? "pvp_xhair_target_style" : "pvp_xhair_style", "custom"); return true;
                }
                if (!click) {
                    String activeSt = cfg.getString(editingTargetPixels ? "pvp_xhair_target_style" : "pvp_xhair_style", editingTargetPixels ? "same" : "classic");
                    g.text("Active: " + activeSt.toUpperCase(), rx, gy + 136, "custom".equalsIgnoreCase(activeSt) ? accent : ClientState.UI_TEXT_DIM, false);
                }
                return false;
            }
        };
    }

    private String normalizePixelGrid(String raw, int n) {
        int len = n * n;
        char[] out = new char[len];
        Arrays.fill(out, '0');
        if (raw != null) {
            int copy = Math.min(len, raw.length());
            for (int i = 0; i < copy; i++) if (raw.charAt(i) == '1') out[i] = '1';
        }
        return new String(out);
    }

    private SettingRow accentRow(final String label) {
        return new SettingRow() {
            @Override public int height() { return 46; }
            @Override public boolean renderOrHandle(UiBridge.UiDraw g, int x, int y, int w, int mx, int my, boolean click, int accent, int accentFg, int menuRad) {
                String cur = ClientConfig.get().getString("ui_accent", "white");
                if (!click) {
                    g.drawGlassCard(x, y, w, 46, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 7, ClientState.UI_TEXT, false);
                }
                int bx = x + 16, by = y + 28;
                int[] cols = {0xFFFFFFFF, 0xFFFF3333, 0xFF22D3EE, 0xFF10B981, 0xFFA855F7, 0xFFF59E0B, 0xFF84CC16, ClientState.parseHexColor(ClientConfig.get().getString("ui_accent_custom", "#ff3333"), 0xFFFF3333)};
                for (int i = 0; i < ACCENTS.length; i++) {
                    boolean sel = ACCENTS[i][0].equals(cur);
                    if (!click) {
                        if (sel) g.drawCircleFilled(bx, by, 8, 0x55FFFFFF);
                        g.drawCircleFilled(bx, by, 6, cols[i]);
                        if (sel) g.drawCircleFilled(bx, by, 2, 0xFF09090B);
                    } else if (mx >= bx - 8 && mx <= bx + 8 && my >= by - 8 && my <= by + 8) {
                        ClientConfig.get().setOption("ui_accent", ACCENTS[i][0]); return true;
                    }
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
                if (!click) {
                    g.drawGlassCard(x, y, w, 36, Math.min(8, menuRad), ClientState.UI_CARD, ClientState.UI_BORDER);
                    g.text(label, x + 10, y + 8, ClientState.UI_TEXT, false);
                    g.text(desc, x + 10, y + 20, ClientState.UI_TEXT_DIM, false);
                }
                if (drawBtn(g, "act_" + actId, x + w - 74, y + 8, 64, 19, 5, "Run", 0xCC3B1A1E, ClientState.UI_TEXT, false, mx, my, click)) {
                    if ("reset_layout".equals(actId)) ClientConfig.get().resetLayout();
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
        if ("menu_rounding".equals(key)) return cfg.getDouble("ui_menu_rounding", 8);
        if ("ui_card_width".equals(key)) return cfg.getDouble("ui_card_width", 140);
        if ("ui_menu_opacity".equals(key)) return cfg.getDouble("ui_menu_opacity", 0.86);
        if ("ui_world_blur".equals(key)) return cfg.getDouble("ui_world_blur", 10);
        if ("ui_anim_speed".equals(key)) return cfg.getDouble("ui_anim_speed", 1.0);
        if ("xhair_pixel_scale".equals(key)) return cfg.getDouble("pvp_xhair_pixel_scale", 1);
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
            if (key.startsWith("mod_x:")) return m.x;
            if (key.startsWith("mod_y:")) return m.y;
            if (key.startsWith("mod_w:")) return m.width;
            if (key.startsWith("mod_h:")) return m.height;
            if (key.startsWith("mod_padx:")) return m.paddingX;
            if (key.startsWith("mod_pady:")) return m.paddingY;
            if (key.startsWith("mod_margin:")) return m.margin;
            if (key.startsWith("mod_rad:")) return m.borderRadius;
            if (key.startsWith("mod_opacity:")) return m.opacity;
            if (key.startsWith("mod_topac:")) return m.textOpacity;
            if (key.startsWith("mod_blur:")) return m.blurRadius;
            if (key.startsWith("mod_bw:")) return m.borderWidth;
            if (key.startsWith("mod_dsblur:")) return m.dropShadowBlur;
            if (key.startsWith("mod_dsx:")) return m.dropShadowX;
            if (key.startsWith("mod_dsy:")) return m.dropShadowY;
            if (key.startsWith("mod_cspeed:")) return m.chromaSpeed;
            if (key.startsWith("mod_csat:")) return m.chromaSat;
            if (key.startsWith("mod_cang:")) return m.chromaAngle;
            if (key.startsWith("mod_lspace:")) return m.letterSpacing;
        }
        return 0;
    }

    private void applySliderValue(String key, int mx) {
        double pct = Math.max(0.0, Math.min(1.0, (mx - sliderX) / (double) Math.max(1, sliderW)));
        double raw = sliderMin + pct * (sliderMax - sliderMin);
        double snapped = Math.max(sliderMin, Math.min(sliderMax, Math.round(raw / sliderStep) * sliderStep));
        ClientConfig cfg = ClientConfig.get();
        if ("menu_rounding".equals(key)) cfg.setOption("ui_menu_rounding", Integer.valueOf((int) Math.round(snapped)));
        else if ("ui_card_width".equals(key)) cfg.setOption("ui_card_width", Integer.valueOf((int) Math.round(snapped)));
        else if ("ui_menu_opacity".equals(key)) cfg.setOption("ui_menu_opacity", Double.valueOf(Math.round(snapped * 100.0) / 100.0));
        else if ("ui_world_blur".equals(key)) cfg.setOption("ui_world_blur", Integer.valueOf((int) Math.round(snapped)));
        else if ("ui_anim_speed".equals(key)) cfg.setOption("ui_anim_speed", Double.valueOf(Math.round(snapped * 10.0) / 10.0));
        else if ("xhair_pixel_scale".equals(key)) cfg.setOption("pvp_xhair_pixel_scale", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_size".equals(key)) cfg.setOption("pvp_xhair_size", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_gap".equals(key)) cfg.setOption("pvp_xhair_gap", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_thick".equals(key)) cfg.setOption("pvp_xhair_thick", Integer.valueOf((int) Math.round(snapped)));
        else if ("xhair_range".equals(key)) cfg.setOption("pvp_xhair_target_range", Integer.valueOf((int) Math.round(snapped)));
        else if ("zoom_fac".equals(key)) cfg.setOption("cam_zoom_fac", Integer.valueOf((int) Math.round(snapped)));
        else if ("fire_offset".equals(key)) cfg.setOption("pvp_low_fire_offset", Double.valueOf(Math.round(snapped * 100.0) / 100.0));
        else if (key.startsWith("mod_")) {
            ClientConfig.HudModule m = cfg.hudModules.get(key.substring(key.indexOf(':') + 1));
            if (m != null) {
                int iv = (int) Math.round(snapped);
                if (key.startsWith("mod_scale:")) m.scale = snapped;
                else if (key.startsWith("mod_x:")) m.x = snapped;
                else if (key.startsWith("mod_y:")) m.y = snapped;
                else if (key.startsWith("mod_w:")) m.width = iv;
                else if (key.startsWith("mod_h:")) m.height = iv;
                else if (key.startsWith("mod_padx:")) m.paddingX = iv;
                else if (key.startsWith("mod_pady:")) m.paddingY = iv;
                else if (key.startsWith("mod_margin:")) m.margin = iv;
                else if (key.startsWith("mod_rad:")) m.borderRadius = iv;
                else if (key.startsWith("mod_opacity:")) m.opacity = snapped;
                else if (key.startsWith("mod_topac:")) m.textOpacity = snapped;
                else if (key.startsWith("mod_blur:")) m.blurRadius = iv;
                else if (key.startsWith("mod_bw:")) m.borderWidth = iv;
                else if (key.startsWith("mod_dsblur:")) m.dropShadowBlur = iv;
                else if (key.startsWith("mod_dsx:")) m.dropShadowX = iv;
                else if (key.startsWith("mod_dsy:")) m.dropShadowY = iv;
                else if (key.startsWith("mod_cspeed:")) m.chromaSpeed = snapped;
                else if (key.startsWith("mod_csat:")) m.chromaSat = snapped;
                else if (key.startsWith("mod_cang:")) m.chromaAngle = iv;
                else if (key.startsWith("mod_lspace:")) m.letterSpacing = iv;
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
        if ("low_fire".equals(id)) return cfg.getBool("pvp_low_fire", true);
        if ("low_shield".equals(id)) return cfg.getBool("pvp_low_shield", true);
        if ("fullbright".equals(id)) return cfg.getBool("util_fullbright", false);
        if ("freelook".equals(id)) return cfg.getBool("util_freelook", true);
        if ("time_lock".equals(id)) return !"off".equalsIgnoreCase(cfg.getString("util_time_lock", "off"));
        if ("weather_lock".equals(id)) return !"off".equalsIgnoreCase(cfg.getString("util_weather_lock", "off"));
        ClientConfig.HudModule m = cfg.hudModules.get(id);
        return m != null && m.enabled;
    }

    private void toggleMod(String id) {
        ClientConfig cfg = ClientConfig.get();
        if ("crosshair".equals(id)) cfg.setOption("pvp_xhair_style", "vanilla".equalsIgnoreCase(cfg.getString("pvp_xhair_style", "classic")) ? "classic" : "vanilla");
        else if ("zoom".equals(id)) cfg.setOption("cam_zoom", Boolean.valueOf(!cfg.getBool("cam_zoom", true)));
        else if ("low_fire".equals(id)) cfg.setOption("pvp_low_fire", Boolean.valueOf(!cfg.getBool("pvp_low_fire", true)));
        else if ("low_shield".equals(id)) cfg.setOption("pvp_low_shield", Boolean.valueOf(!cfg.getBool("pvp_low_shield", true)));
        else if ("fullbright".equals(id)) cfg.setOption("util_fullbright", Boolean.valueOf(!cfg.getBool("util_fullbright", false)));
        else if ("freelook".equals(id)) cfg.setOption("util_freelook", Boolean.valueOf(!cfg.getBool("util_freelook", true)));
        else if ("time_lock".equals(id)) cfg.setOption("util_time_lock", "off".equalsIgnoreCase(cfg.getString("util_time_lock", "off")) ? "day" : "off");
        else if ("weather_lock".equals(id)) cfg.setOption("util_weather_lock", "off".equalsIgnoreCase(cfg.getString("util_weather_lock", "off")) ? "clear" : "off");
        else { ClientConfig.HudModule m = cfg.hudModules.get(id); if (m != null) cfg.toggleModule(m); }
    }

    private String getModTitle(String id) { for (ModEntry m : ALL_MODS) if (m.id.equals(id)) return m.name; return "Module Settings"; }
    private String getMenuTitle(String id) { return "favorites".equals(id) ? "Favorites" : "system".equals(id) ? "System & Perf" : "pvp".equals(id) ? "PvP & Combat" : "world".equals(id) ? "World & Survival" : "server".equals(id) ? "Minigames & Server" : "visuals".equals(id) ? "Visuals & Media" : "utility".equals(id) ? "Core Utilities" : "All 100+ Modules"; }
    private String trimToWidth(UiBridge.UiDraw g, String s, int maxW) { if (g.textWidth(s) <= maxW) return s; while (s.length() > 3 && g.textWidth(s + "..") > maxW) s = s.substring(0, s.length() - 1); return s + ".."; }

    private List<String> wrapText(UiBridge.UiDraw g, String text, int maxW, int maxLines) {
        List<String> lines = new ArrayList<String>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String test = cur.length() == 0 ? words[i] : (cur.toString() + " " + words[i]);
            if (g.textWidth(test) <= maxW) cur.append(cur.length() == 0 ? "" : " ").append(words[i]);
            else {
                if (cur.length() > 0) lines.add(cur.toString());
                if (lines.size() >= maxLines - 1) { lines.add(trimToWidth(g, words[i], maxW)); return lines; }
                cur = new StringBuilder(words[i]);
            }
        }
        if (cur.length() > 0 && lines.size() < maxLines) lines.add(cur.toString());
        return lines;
    }

    private void drawScrollbar(UiBridge.UiDraw g, int x, int y, int viewH, int totalH, int curScroll, int accent) {
        g.drawRoundedRect(x, y, 4, viewH, 2, ClientState.UI_BORDER);
        int thumbH = Math.max(18, (viewH * viewH) / Math.max(1, totalH));
        int thumbY = y + (int) Math.round(((viewH - thumbH) * (double) curScroll) / Math.max(1, maxScrollPx));
        g.drawRoundedRect(x, thumbY, 4, thumbH, 2, ClientState.withAlpha(accent, 195));
    }

    private boolean drawBtn(UiBridge.UiDraw g, String id, int x, int y, int w, int h, int rad, String txt, int bg, int fg, boolean solidAccent, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            float ht = animHover(id, hov);
            int fillCol = ClientState.lerpColor(bg, ClientState.brightenArgb(bg, solidAccent ? -20 : 18), ht);
            int brdCol = solidAccent ? bg : ClientState.lerpColor(ClientState.UI_BORDER, ClientState.UI_BORDER_HOV, ht);
            if ((fillCol >>> 24) > 2 || solidAccent) g.drawGlassCard(x, y, w, h, rad, fillCol, brdCol);
            g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fg, false);
        }
        return hov && click;
    }

    private boolean drawSwitch(UiBridge.UiDraw g, String id, int x, int y, int w, int h, boolean on, int accent, int accentFg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) {
            String style = ClientConfig.get().getString("ui_switch_style", "pill");
            float t = animPill(id, on);
            if ("checkbox".equalsIgnoreCase(style)) {
                int bx = x + w - h, bw = h;
                g.drawGlassCard(bx, y, bw, bw, 4, ClientState.lerpColor(ClientState.UI_INPUT, accent, t), on ? accent : ClientState.UI_BORDER_HOV);
                if (on) g.text("v", bx + (bw - g.textWidth("v")) / 2, y + (bw - 8) / 2, accentFg, false);
                g.text(on ? "ON" : "OFF", x + 2, y + (h - 8) / 2, on ? accent : ClientState.UI_TEXT_DIM, false);
            } else if ("ios".equalsIgnoreCase(style)) {
                int sw = Math.min(34, w), sx = x + w - sw;
                g.drawRoundedRect(sx, y + 1, sw, h - 2, (h - 2) / 2, ClientState.lerpColor(0xFF27272A, accent, t));
                int r = (h - 6) / 2, kx = Math.round((sx + 3 + r) + (sw - 6 - r * 2) * t);
                g.drawCircleFilled(kx, y + h / 2, r, 0xFFFFFFFF);
                if (w > 44) g.text(on ? "ON" : "OFF", x + 2, y + (h - 8) / 2, on ? ClientState.UI_TEXT : ClientState.UI_TEXT_DIM, false);
            } else {
                int trackCol = ClientState.lerpColor(ClientState.UI_INPUT, accent, t);
                int brdCol = ClientState.lerpColor(ClientState.UI_BORDER_HOV, accent, t);
                g.drawRoundedRect(x, y, w, h, h / 2, trackCol);
                g.drawRoundedOutline(x, y, w, h, h / 2, brdCol);
                int r = (h - 4) / 2, kx = Math.round((x + 3 + r) + (w - 6 - r * 2) * t);
                g.drawCircleFilled(kx, y + h / 2, r, ClientState.lerpColor(ClientState.UI_TEXT_MUTED, accentFg, t));
            }
        }
        return hov && click;
    }
}