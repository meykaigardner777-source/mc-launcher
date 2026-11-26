package dev.aetheria.client.common;

import java.text.SimpleDateFormat;
import java.util.*;

public class SharedTitleScreen {
    public interface HostActions {
        void openSingleplayer();
        void openMultiplayer();
        void openRealms();
        void openOptions();
        void openClientMenu();
        void quitGame();
        void exitEditor();
    }

    public static final String[][] BG_PRESETS = {
        {"waves", "1. Obsidian Waves"},
        {"starfield", "2. Deep Space Nebula"},
        {"grid", "3. Neon Horizon"},
        {"embers", "4. Volcanic Embers"},
        {"aurora", "5. Northern Aurora"},
        {"mc_overworld", "6. MC: Overworld Sunset"},
        {"mc_nether", "7. MC: Nether Crimson"},
        {"mc_end", "8. MC: The End Void"},
        {"mc_deepdark", "9. MC: Deep Dark Sculk"},
        {"mc_daynight", "10. MC: Celestial Sky"}
    };

    private static final SimpleDateFormat CLOCK_FMT = new SimpleDateFormat("HH:mm:ss");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MMM dd, yyyy");

    private boolean editorMode, catalogOpen = false, accountOpen = false, accInputFocused = false;
    private String selectedId = "logo", draggingId = null, resizingId = null, accInput = "";
    private double dragStartMx = 0, dragStartMy = 0, dragOrigX = 0, dragOrigY = 0, resizeStartScale = 1.0, resizeStartX = 0;
    private int lastSw = 854, lastSh = 480;
    private long lastCacheMs = 0L;
    private String cachedTime = "00:00:00", cachedDate = "", cachedRam = "RAM --";
    private final float[] ptData = new float[360];

    public SharedTitleScreen() { this(false); }
    public SharedTitleScreen(boolean editorMode) {
        this.editorMode = editorMode;
        Random rng = new Random(260209L);
        for (int i = 0; i < ptData.length; i += 3) {
            ptData[i] = rng.nextFloat();
            ptData[i + 1] = rng.nextFloat();
            ptData[i + 2] = 0.25F + rng.nextFloat() * 0.75F;
        }
    }

    private void refreshCachedText() {
        long now = System.currentTimeMillis();
        if (now - lastCacheMs < 500L) return;
        lastCacheMs = now;
        Date d = new Date(now);
        cachedTime = CLOCK_FMT.format(d);
        cachedDate = DATE_FMT.format(d);
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long max = Math.max(1L, rt.maxMemory() / (1024L * 1024L));
        cachedRam = used + "/" + max + "MB (" + Math.round((used * 100.0) / max) + "%)";
    }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        this.lastSw = Math.max(1, sw); this.lastSh = Math.max(1, sh);
        refreshCachedText();
        ClientConfig cfg = ClientConfig.get();
        if (cfg.titleWidgets.isEmpty()) cfg.resetTitleLayout();
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();

        renderAnimatedBackground(g, sw, sh, cfg, accent);

        if (editorMode) {
            int stepX = Math.max(18, sw / 40), stepY = Math.max(18, sh / 40);
            for (int gx = stepX; gx < sw; gx += stepX) g.fill(gx, 0, gx + 1, sh, 0x0CFFFFFF);
            for (int gy = stepY; gy < sh; gy += stepY) g.fill(0, gy, sw, gy + 1, 0x0CFFFFFF);
            g.fill(sw / 2, 0, sw / 2 + 1, sh, 0x26FFFFFF);
            g.fill(0, sh / 2, sw, sh / 2 + 1, 0x26FFFFFF);
        }

        for (ClientConfig.TitleWidget tw : cfg.titleWidgets.values()) {
            if (!tw.enabled) continue;
            renderWidget(g, tw, sw, sh, mx, my, accent, accentFg);
            if (editorMode) {
                int[] b = getWidgetBounds(tw, sw, sh);
                boolean sel = tw.id.equals(selectedId);
                boolean hov = mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
                if (sel) {
                    g.drawRoundedOutline(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, tw.borderRadius + 2, 2, "solid", accent, accent);
                    g.drawCircleFilled(b[0] + b[2] + 1, b[1] + b[3] + 1, 4, accent);
                    int cx = b[0] + b[2] - 3, cy = b[1] - 4;
                    g.drawCircleFilled(cx, cy, 5, 0xFFEF4444); g.text("x", cx - 2, cy - 4, 0xFFFFFFFF, false);
                } else if (hov) {
                    g.drawRoundedOutline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, tw.borderRadius + 1, 1, "solid", ClientState.UI_BORDER_HOV, ClientState.UI_BORDER_HOV);
                }
            }
        }

        if (!editorMode && accountOpen) renderAccountPopup(g, sw, sh, mx, my, false, accent, accentFg);
        if (editorMode) {
            if (catalogOpen) renderCatalogPopup(g, sw, sh, mx, my, false, accent, accentFg);
            drawEditorToolbar(g, sw, mx, my, false, null, accent, accentFg);
        }
    }

    public boolean mouseClicked(UiBridge.UiDraw g, int sw, int sh, double mx, double my, HostActions actions) {
        this.lastSw = Math.max(1, sw); this.lastSh = Math.max(1, sh);
        ClientConfig cfg = ClientConfig.get();
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();
        int ix = (int) mx, iy = (int) my;

        if (editorMode) {
            if (drawEditorToolbar(g, sw, ix, iy, true, actions, accent, accentFg)) return true;
            if (catalogOpen) {
                if (renderCatalogPopup(g, sw, sh, ix, iy, true, accent, accentFg)) return true;
                catalogOpen = false; return true;
            }
            ClientConfig.TitleWidget sel = cfg.titleWidgets.get(selectedId);
            if (sel != null && sel.enabled) {
                int[] b = getWidgetBounds(sel, sw, sh);
                if (Math.hypot(mx - (b[0] + b[2] - 3), my - (b[1] - 4)) <= 7.0) {
                    g.playClickSound(); sel.enabled = false; cfg.save(); return true;
                }
                if (Math.hypot(mx - (b[0] + b[2] + 1), my - (b[1] + b[3] + 1)) <= 8.0) {
                    resizingId = sel.id; resizeStartX = mx; resizeStartScale = sel.scale; return true;
                }
            }
            List<ClientConfig.TitleWidget> list = new ArrayList<ClientConfig.TitleWidget>(cfg.titleWidgets.values());
            for (int i = list.size() - 1; i >= 0; i--) {
                ClientConfig.TitleWidget tw = list.get(i); if (!tw.enabled) continue;
                int[] b = getWidgetBounds(tw, sw, sh);
                if (ix >= b[0] - 2 && ix <= b[0] + b[2] + 4 && iy >= b[1] - 2 && iy <= b[1] + b[3] + 4) {
                    selectedId = tw.id; draggingId = tw.id; dragStartMx = mx; dragStartMy = my; dragOrigX = tw.x; dragOrigY = tw.y; return true;
                }
            }
            return false;
        }

        if (accountOpen) {
            if (renderAccountPopup(g, sw, sh, ix, iy, true, accent, accentFg)) return true;
            accountOpen = false; accInputFocused = false;
        }

        List<ClientConfig.TitleWidget> list = new ArrayList<ClientConfig.TitleWidget>(cfg.titleWidgets.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientConfig.TitleWidget tw = list.get(i); if (!tw.enabled) continue;
            int[] b = getWidgetBounds(tw, sw, sh);
            if (ix >= b[0] && ix <= b[0] + b[2] && iy >= b[1] && iy <= b[1] + b[3]) {
                if (handleWidgetClick(g, tw, actions)) return true;
            }
        }
        return false;
    }

    private boolean handleWidgetClick(UiBridge.UiDraw g, ClientConfig.TitleWidget tw, HostActions actions) {
        if ("quick_play".equals(tw.id) || "singleplayer".equals(tw.id)) { g.playClickSound(); if (actions != null) actions.openSingleplayer(); return true; }
        if ("multiplayer".equals(tw.id) || "last_server".equals(tw.id)) { g.playClickSound(); if (actions != null) actions.openMultiplayer(); return true; }
        if ("realms".equals(tw.id)) { g.playClickSound(); if (actions != null) actions.openRealms(); return true; }
        if ("options".equals(tw.id)) { g.playClickSound(); if (actions != null) actions.openOptions(); return true; }
        if ("client_menu".equals(tw.id)) { g.playClickSound(); if (actions != null) actions.openClientMenu(); return true; }
        if ("quit".equals(tw.id)) { g.playClickSound(); if (actions != null) actions.quitGame(); return true; }
        if ("account".equals(tw.id) || "skin_card".equals(tw.id)) { g.playClickSound(); accountOpen = !accountOpen; accInputFocused = false; return true; }
        return false;
    }

    public boolean mouseDragged(double mx, double my) {
        if (!editorMode) return false;
        ClientConfig cfg = ClientConfig.get();
        if (resizingId != null) {
            ClientConfig.TitleWidget tw = cfg.titleWidgets.get(resizingId); if (tw == null) return false;
            tw.scale = Math.max(0.5, Math.min(2.0, Math.round((resizeStartScale + (mx - resizeStartX) / 120.0) * 20.0) / 20.0));
            return true;
        }
        if (draggingId != null) {
            ClientConfig.TitleWidget tw = cfg.titleWidgets.get(draggingId); if (tw == null) return false;
            double nx = dragOrigX + (mx - dragStartMx) / Math.max(1.0, lastSw), ny = dragOrigY + (my - dragStartMy) / Math.max(1.0, lastSh);
            if (cfg.getBool("hud_snap_grid", true)) { nx = Math.round(nx * 40.0) / 40.0; ny = Math.round(ny * 40.0) / 40.0; }
            tw.x = Math.max(0.02, Math.min(0.98, nx)); tw.y = Math.max(0.03, Math.min(0.97, ny));
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        if (!editorMode) return false;
        if (draggingId != null || resizingId != null) { draggingId = null; resizingId = null; ClientConfig.get().save(); return true; }
        return false;
    }

    public boolean keyPressedCode(int keyCode) {
        if (accInputFocused && (keyCode == 257 || keyCode == 335)) {
            if (!accInput.trim().isEmpty()) { ClientConfig.get().addAccount(accInput.trim(), "offline", "Alt"); accInput = ""; }
            return true;
        }
        if (editorMode && selectedId != null) {
            ClientConfig.TitleWidget tw = ClientConfig.get().titleWidgets.get(selectedId); if (tw == null) return false;
            double stepX = 1.0 / lastSw, stepY = 1.0 / lastSh;
            if (keyCode == 262) tw.x = Math.min(0.98, tw.x + stepX);
            else if (keyCode == 263) tw.x = Math.max(0.02, tw.x - stepX);
            else if (keyCode == 264) tw.y = Math.min(0.97, tw.y + stepY);
            else if (keyCode == 265) tw.y = Math.max(0.03, tw.y - stepY);
            else return false;
            ClientConfig.get().save(); return true;
        }
        return false;
    }

    public boolean keyPressed(boolean isBackspace, boolean isEscape) {
        if (accInputFocused) {
            if (isBackspace && accInput.length() > 0) { accInput = accInput.substring(0, accInput.length() - 1); return true; }
            if (isEscape) { accInputFocused = false; return true; }
        }
        if (isEscape) {
            if (catalogOpen) { catalogOpen = false; return true; }
            if (accountOpen) { accountOpen = false; return true; }
            if (editorMode) { editorMode = false; ClientConfig.get().save(); return true; }
        }
        return false;
    }

    public boolean charTyped(char ch) {
        if (accInputFocused) { if (ch >= 33 && ch <= 126 && accInput.length() < 16) accInput += ch; return true; }
        return false;
    }

    private int[] getWidgetBounds(ClientConfig.TitleWidget tw, int sw, int sh) {
        int baseW = 164, baseH = 22;
        if ("logo".equals(tw.id)) { baseW = 216; baseH = 44; }
        else if ("quick_play".equals(tw.id)) { baseW = 164; baseH = 26; }
        else if ("options".equals(tw.id) || "client_menu".equals(tw.id)) { baseW = 80; baseH = 22; }
        else if ("account".equals(tw.id) || "clock".equals(tw.id) || "fps_badge".equals(tw.id)
              || "last_server".equals(tw.id) || "skin_card".equals(tw.id) || "system_info".equals(tw.id)) { baseW = 148; baseH = 28; }
        else if ("profile_pill".equals(tw.id) || "version_badge".equals(tw.id) || "session_timer".equals(tw.id)
              || "mod_count".equals(tw.id) || "waypoint_count".equals(tw.id)) { baseW = 134; baseH = 20; }
        else if ("custom_note".equals(tw.id)) { baseW = 220; baseH = 22; }
        int w = (int) Math.round(baseW * tw.scale), h = (int) Math.round(baseH * tw.scale);
        int x = (int) Math.round(tw.x * sw) - w / 2, y = (int) Math.round(tw.y * sh) - h / 2;
        return new int[]{Math.max(4, Math.min(sw - w - 4, x)), Math.max(4, Math.min(sh - h - 4, y)), w, h, baseW, baseH};
    }

    private boolean isClickable(String id) {
        return "quick_play".equals(id) || "singleplayer".equals(id) || "multiplayer".equals(id)
            || "realms".equals(id) || "options".equals(id) || "client_menu".equals(id)
            || "quit".equals(id) || "account".equals(id) || "last_server".equals(id) || "skin_card".equals(id);
    }

    private void renderWidget(UiBridge.UiDraw g, ClientConfig.TitleWidget tw, int sw, int sh, int mx, int my, int accent, int accentFg) {
        int[] b = getWidgetBounds(tw, sw, sh);
        int x = b[0], y = b[1], w = b[2], h = b[3], bw = b[4], bh = b[5];
        boolean hov = !editorMode && isClickable(tw.id) && mx >= x && mx <= x + w && my >= y && my <= y + h;
        ClientConfig cfg = ClientConfig.get();
        g.pushTransform(x, y, (float) tw.scale);
        try {
            int rawBg = ClientState.parseHexColor(tw.bgColor, 0xFF121219);
            if (hov) rawBg = ClientState.brightenArgb(rawBg, 16);
            int bg = ClientState.scaleAlpha(rawBg, tw.opacity);
            int baseTxt = tw.chroma ? ClientState.getChromaColor(1.2, 0.85, 45.0, tw.x, tw.y) : ClientState.parseHexColor(tw.textColor, 0xFFF4F4F5);
            int txt = ClientState.scaleAlpha(baseTxt, tw.textOpacity);
            int brdBase = tw.chroma ? baseTxt : (hov ? accent : ClientState.parseHexColor(tw.borderColor, ClientState.UI_BORDER));
            int bwThick = tw.showBorder ? Math.max(1, Math.min(3, tw.borderWidth)) : 0;

            g.drawGlassCard(0, 0, bw, bh, tw.borderRadius, bwThick, "solid", bg, brdBase, brdBase);

            if ("logo".equals(tw.id)) {
                String t = (tw.customText != null && !tw.customText.isEmpty()) ? tw.customText : "AETHERIA CLIENT";
                g.text(t, (bw - g.textWidth(t)) / 2, 11, txt, tw.shadow);
                String sub = cfg.activeProfile.toUpperCase() + " \u2022 MC 26.2";
                g.text(sub, (bw - g.textWidth(sub)) / 2, 26, tw.chroma ? txt : accent, tw.shadow);
            } else if ("quick_play".equals(tw.id)) {
                String lbl = (tw.customText != null && !tw.customText.isEmpty()) ? tw.customText : "PLAY MINECRAFT";
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else if ("account".equals(tw.id) || "skin_card".equals(tw.id)) {
                boolean isMsa = "msa".equalsIgnoreCase(g.currentAccountType());
                g.drawCircleFilled(13, bh / 2, 6, ClientState.withAlpha(isMsa ? 0xFF10B981 : accent, 60));
                g.drawCircleFilled(13, bh / 2, 3, isMsa ? 0xFF10B981 : 0xFFF59E0B);
                g.text(g.currentUsername(), 24, 6, txt, tw.shadow);
                String sub = isMsa ? "Microsoft (MSA)" : "Offline Alt";
                g.text(sub, 24, 16, ClientState.scaleAlpha(ClientState.UI_TEXT_DIM, tw.textOpacity), false);
            } else if ("clock".equals(tw.id)) {
                g.text(cachedTime, 10, 6, txt, tw.shadow);
                g.text(cachedDate, 10, 16, ClientState.scaleAlpha(ClientState.UI_TEXT_DIM, tw.textOpacity), false);
            } else if ("fps_badge".equals(tw.id)) {
                g.text("FPS: " + g.liveFps(), 10, 6, txt, tw.shadow);
                g.text(cachedRam, 10, 16, ClientState.scaleAlpha(ClientState.UI_TEXT_DIM, tw.textOpacity), false);
            } else if ("profile_pill".equals(tw.id)) {
                String lbl = "PROFILE: " + cfg.activeProfile.toUpperCase();
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else if ("version_badge".equals(tw.id)) {
                String lbl = "Aetheria 1.1 \u2022 26.2";
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else if ("last_server".equals(tw.id)) {
                g.text("Quick Multiplayer", 10, 6, txt, tw.shadow);
                g.text("Open server browser", 10, 16, ClientState.scaleAlpha(ClientState.UI_TEXT_DIM, tw.textOpacity), false);
            } else if ("session_timer".equals(tw.id)) {
                long s = (System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L;
                String lbl = String.format("Uptime %02d:%02d:%02d", Long.valueOf(s / 3600L), Long.valueOf((s % 3600L) / 60L), Long.valueOf(s % 60L));
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else if ("mod_count".equals(tw.id)) {
                int en = 0; for (ClientConfig.HudModule hm : cfg.hudModules.values()) if (hm.enabled) en++;
                String lbl = "HUD Mods: " + en + "/" + cfg.hudModules.size();
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else if ("waypoint_count".equals(tw.id)) {
                String lbl = "Waypoints: " + cfg.waypoints.size() + " Saved";
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else if ("system_info".equals(tw.id)) {
                g.text("CPU: " + Runtime.getRuntime().availableProcessors() + " Cores", 10, 6, txt, tw.shadow);
                g.text(cachedRam, 10, 16, ClientState.scaleAlpha(ClientState.UI_TEXT_DIM, tw.textOpacity), false);
            } else if ("custom_note".equals(tw.id)) {
                String lbl = (tw.customText != null && !tw.customText.isEmpty()) ? tw.customText : "Welcome to Aetheria Client";
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, txt, tw.shadow);
            } else {
                String lbl = "singleplayer".equals(tw.id) ? "Singleplayer" : "multiplayer".equals(tw.id) ? "Multiplayer"
                    : "realms".equals(tw.id) ? "Minecraft Realms" : "options".equals(tw.id) ? "Options"
                    : "client_menu".equals(tw.id) ? "Client Menu" : "Quit (ESC)";
                int finalTxt = ("quit".equals(tw.id) && hov) ? 0xFFFCA5A5 : txt;
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, finalTxt, tw.shadow);
            }
        } finally { g.popTransform(); }
    }

    private boolean drawEditorToolbar(UiBridge.UiDraw g, int sw, int mx, int my, boolean click, HostActions actions, int accent, int accentFg) {
        int barW = 246, bx = sw / 2 - barW / 2, by = 6, bh = 20;
        if (click && (mx < bx || mx > bx + barW || my < by || my > by + bh)) return false;
        if (!click) g.drawGlassCard(bx, by, barW, bh, 10, 0xCC09090B, ClientState.UI_BORDER);
        int active = 0; for (ClientConfig.TitleWidget tw : ClientConfig.get().titleWidgets.values()) if (tw.enabled) active++;
        if (miniBtn(g, bx + 4, by + 2, 90, 16, "Widgets (" + active + "/20)", 0xFF181822, ClientState.UI_TEXT, mx, my, click)) { catalogOpen = !catalogOpen; return true; }
        if (miniBtn(g, bx + 98, by + 2, 54, 16, "Reset", 0xFF28181B, ClientState.UI_TEXT_MUTED, mx, my, click)) { ClientConfig.get().resetTitleLayout(); ClientConfig.get().save(); return true; }
        if (miniBtn(g, bx + 156, by + 2, 86, 16, "Done Editing", accent, accentFg, mx, my, click)) { editorMode = false; catalogOpen = false; ClientConfig.get().save(); if (actions != null) actions.exitEditor(); return true; }
        return false;
    }

    private boolean renderAccountPopup(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, int accent, int accentFg) {
        int pw = Math.min(310, sw - 20), ph = Math.min(250, sh - 50), px = Math.max(10, (sw - pw) / 2), py = Math.max(28, (sh - ph) / 2);
        if (click && (mx < px || mx > px + pw || my < py || my > py + ph)) return false;
        ClientConfig cfg = ClientConfig.get();
        if (!click) {
            g.drawGlassCard(px, py, pw, ph, 10, 0xEE0D0D14, accent);
            g.text("Account Switcher (" + g.currentUsername() + ")", px + 10, py + 9, ClientState.UI_TEXT, false);
        }
        if (miniBtn(g, px + pw - 96, py + 6, 70, 16, "Sync MSA", 0xFF1A2E28, 0xFF6EE7B7, mx, my, click)) {
            MicrosoftAuth.syncFromLauncherAsync(null); return true;
        }
        if (miniBtn(g, px + pw - 22, py + 6, 16, 16, "x", 0xFF28181B, 0xFFFFFFFF, mx, my, click)) { accountOpen = false; accInputFocused = false; return true; }
        int iy = py + 28, inW = pw - 78;
        boolean inHov = mx >= px + 10 && mx <= px + 10 + inW && my >= iy && my <= iy + 18;
        if (!click) {
            g.drawGlassCard(px + 10, iy, inW, 18, 5, ClientState.UI_INPUT, accInputFocused ? accent : (inHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            g.text(accInput.isEmpty() ? (accInputFocused ? "_" : "Type offline alt username...") : (accInput + (accInputFocused ? "_" : "")), px + 16, iy + 5, accInput.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
        } else if (inHov) { accInputFocused = true; return true; } else accInputFocused = false;
        if (miniBtn(g, px + pw - 62, iy, 52, 18, "+ Alt", accent, accentFg, mx, my, click)) {
            if (!accInput.trim().isEmpty()) { cfg.addAccount(accInput.trim(), "offline", "Alt"); g.switchAccount(accInput.trim(), "", "", "offline"); accInput = ""; }
            return true;
        }
        int ry = iy + 24;
        for (int i = 0; i < cfg.accounts.size() && ry + 22 <= py + ph - 18; i++) {
            ClientConfig.AccountEntry a = cfg.accounts.get(i);
            boolean cur = a.name.equalsIgnoreCase(g.currentUsername());
            boolean isMsa = "msa".equalsIgnoreCase(a.type);
            if (!click) {
                g.drawGlassCard(px + 10, ry, pw - 20, 20, 5, 0xFF15151E, cur ? accent : ClientState.UI_BORDER);
                g.text((isMsa ? "[MSA] " : "[ALT] ") + a.name, px + 16, ry + 6, cur ? accent : (isMsa ? 0xFF6EE7B7 : ClientState.UI_TEXT), false);
            }
            if (miniBtn(g, px + pw - 80, ry + 2, 42, 16, cur ? "Active" : "Use", cur ? accent : 0xFF1E293B, cur ? accentFg : ClientState.UI_TEXT, mx, my, click)) {
                cfg.activeAccountId = a.id; cfg.save(); g.switchAccount(a.name, a.uuid, a.accessToken, a.type); return true;
            }
            if (miniBtn(g, px + pw - 34, ry + 2, 20, 16, "Del", 0xFF3B1A1E, 0xFFFFFFFF, mx, my, click)) { cfg.removeAccount(a.id); return true; }
            ry += 23;
        }
        if (!click) {
            String msg = !MicrosoftAuth.statusMsg.isEmpty() ? MicrosoftAuth.statusMsg : "For full Microsoft OAuth login, open Client Menu > Accounts";
            g.text(msg.length() > 54 ? msg.substring(0, 52) + ".." : msg, px + 10, py + ph - 13, ClientState.UI_TEXT_DIM, false);
        }
        return true;
    }

    private boolean renderCatalogPopup(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, int accent, int accentFg) {
        int pw = Math.min(410, sw - 20), ph = Math.min(270, sh - 40), px = (sw - pw) / 2, py = Math.max(30, (sh - ph) / 2);
        if (click && (mx < px || mx > px + pw || my < py || my > py + ph)) return false;
        if (!click) {
            g.drawGlassCard(px, py, pw, ph, 10, 0xEE0D0D14, accent);
            g.text("Title Widget Catalog (20 Widgets)", px + 12, py + 9, ClientState.UI_TEXT, false);
        }
        if (miniBtn(g, px + pw - 24, py + 6, 18, 16, "x", 0xFF28181B, 0xFFFFFFFF, mx, my, click)) { catalogOpen = false; return true; }
        List<ClientConfig.TitleWidget> list = new ArrayList<ClientConfig.TitleWidget>(ClientConfig.get().titleWidgets.values());
        int colW = (pw - 26) / 2;
        for (int i = 0; i < list.size(); i++) {
            ClientConfig.TitleWidget tw = list.get(i);
            int col = i % 2, row = i / 2;
            int wx = px + 10 + col * (colW + 6), wy = py + 28 + row * 23;
            if (wy + 20 > py + ph - 6) break;
            if (!click) {
                g.drawGlassCard(wx, wy, colW, 20, 5, 0xFF14141D, tw.enabled ? accent : ClientState.UI_BORDER);
                g.text(tw.label, wx + 6, wy + 6, ClientState.UI_TEXT, false);
            }
            if (miniBtn(g, wx + colW - 36, wy + 2, 32, 16, tw.enabled ? "ON" : "OFF", tw.enabled ? accent : 0xFF23232E, tw.enabled ? accentFg : ClientState.UI_TEXT_MUTED, mx, my, click)) {
                tw.enabled = !tw.enabled; ClientConfig.get().save(); return true;
            }
        }
        return true;
    }

    private void drawSkyGradient(UiBridge.UiDraw g, int sw, int sh, int topRgb, int botRgb) {
        int bands = 24;
        for (int i = 0; i < bands; i++) {
            int y0 = (i * sh) / bands, y1 = ((i + 1) * sh) / bands;
            float t = i / (float) (bands - 1);
            g.fill(0, y0, sw, y1, ClientState.lerpColor(0xFF000000 | topRgb, 0xFF000000 | botRgb, t));
        }
    }

    private void renderAnimatedBackground(UiBridge.UiDraw g, int sw, int sh, ClientConfig cfg, int accent) {
        String st = cfg.getString("title_bg_style", "waves").toLowerCase();
        long t = System.currentTimeMillis();

        if ("starfield".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x05050D, 0x0B0818);
            g.drawCircleFilled((int) (sw * 0.28), (int) (sh * 0.35), Math.min(sw, sh) / 3, ClientState.withAlpha(accent, 18));
            g.drawCircleFilled((int) (sw * 0.72), (int) (sh * 0.62), Math.min(sw, sh) / 3, ClientState.withAlpha(0xFFA855F7, 16));
            for (int i = 0; i < ptData.length; i += 3) {
                float spd = 0.008F + ptData[i + 2] * 0.025F;
                float sx = ((ptData[i] * sw + t * spd) % sw);
                float sy = ptData[i + 1] * sh;
                int sz = ptData[i + 2] > 0.82F ? 2 : 1;
                int twk = 110 + Math.round(130.0F * (0.5F + 0.5F * (float) Math.sin(t * 0.003 + i)));
                g.fill((int) sx, (int) sy, (int) sx + sz, (int) sy + sz, ClientState.withAlpha(i % 9 == 0 ? accent : 0xFFF4F4F5, twk));
            }
            int shootP = (int) ((t / 14L) % 600);
            if (shootP < 90) {
                int sx = (int) (sw * 0.15 + shootP * 5.2), sy = (int) (sh * 0.14 + shootP * 2.1);
                g.drawLine(sx - 24, sy - 10, sx, sy, 1, ClientState.withAlpha(0xFFFFFFFF, 180 - shootP * 2));
            }
        } else if ("grid".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x070712, 0x130D24);
            int hz = (int) (sh * 0.56);
            g.drawCircleFilled(sw / 2, hz - 6, Math.min(sw, sh) / 7, ClientState.withAlpha(accent, 34));
            g.drawCircleFilled(sw / 2, hz - 6, Math.min(sw, sh) / 11, ClientState.withAlpha(accent, 55));
            g.fill(0, hz, sw, hz + 2, ClientState.withAlpha(accent, 150));
            for (int k = -8; k <= 8; k++) g.drawLine(sw / 2 + k * 14, hz, sw / 2 + k * 78, sh, 1, ClientState.withAlpha(accent, 32));
            float phase = (t % 1800L) / 1800.0F;
            for (int r = 0; r < 10; r++) {
                float p = (r + phase) / 10.0F;
                int y = hz + Math.round((p * p) * (sh - hz));
                g.fill(0, y, sw, y + 1, ClientState.withAlpha(accent, Math.round(18 + p * 48)));
            }
        } else if ("embers".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x0A0507, 0x1F0C08);
            g.fill(0, (int) (sh * 0.85), sw, sh, 0x22EA580C);
            for (int i = 0; i < ptData.length; i += 3) {
                float ex = ptData[i] * sw + (float) Math.sin(t * 0.0018 + i) * 18.0F;
                float ey = sh - ((ptData[i + 1] * sh + t * (0.025F + ptData[i + 2] * 0.035F)) % sh);
                int r = ptData[i + 2] > 0.78F ? 3 : 2;
                int col = i % 6 == 0 ? 0xFFFACC15 : 0xFFF97316;
                g.drawCircleFilled((int) ex, (int) ey, r + 2, ClientState.withAlpha(col, 32));
                g.drawCircleFilled((int) ex, (int) ey, r, ClientState.withAlpha(col, 175));
            }
        } else if ("aurora".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x040711, 0x09131F);
            for (int i = 0; i < 120; i += 3) {
                g.fill((int) (ptData[i] * sw), (int) (ptData[i + 1] * sh * 0.65F), (int) (ptData[i] * sw) + 1, (int) (ptData[i + 1] * sh * 0.65F) + 1, 0x88FFFFFF);
            }
            for (int x = 0; x < sw; x += 4) {
                int y1 = (int) (sh * 0.28 + Math.sin(x * 0.007 + t * 0.0011) * 32 + Math.cos(x * 0.015 - t * 0.0007) * 18);
                int h1 = 55 + (int) (Math.sin(x * 0.012 + t * 0.0016) * 24);
                g.fill(x, y1, x + 4, y1 + h1, ClientState.withAlpha(0xFF10B981, 24));
                g.fill(x, y1 + 12, x + 4, y1 + h1 / 2 + 12, ClientState.withAlpha(0xFF22D3EE, 26));
                int y2 = (int) (sh * 0.36 + Math.cos(x * 0.006 - t * 0.0009) * 28);
                g.fill(x, y2, x + 4, y2 + 42, ClientState.withAlpha(0xFFA855F7, 18));
            }
        } else if ("mc_overworld".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x131736, 0x5B2E48);
            int sunX = (int) (sw * 0.74), sunY = (int) (sh * 0.34);
            g.drawCircleFilled(sunX, sunY, 46, 0x22FDBA74);
            g.drawCircleFilled(sunX, sunY, 26, 0x55FDE047);
            g.fill(sunX - 12, sunY - 12, sunX + 12, sunY + 12, 0xFFFDE047);
            for (int c = 0; c < 5; c++) {
                int cx0 = (int) (((c * sw * 0.24 + t * (0.012 + c * 0.003)) % (sw + 140)) - 120);
                int cy0 = (int) (sh * (0.14 + (c % 3) * 0.09));
                g.drawRoundedRect(cx0, cy0, 96, 14, 5, 0x28FDE68A);
                g.drawRoundedRect(cx0 + 18, cy0 - 7, 56, 10, 4, 0x20FFFFFF);
            }
            for (int x = 0; x < sw; x += 4) {
                int ridge = sh - (int) (sh * 0.32 + Math.sin(x * 0.008) * 28 + Math.cos(x * 0.019) * 14);
                g.fill(x, ridge, x + 4, sh, 0xFF1E2235);
                int hill = sh - (int) (sh * 0.20 + Math.sin(x * 0.013 + 1.4) * 18 + Math.cos(x * 0.025) * 8);
                g.fill(x, hill, x + 4, hill + 6, 0xFF15803D);
                g.fill(x, hill + 6, x + 4, sh, 0xFF29201B);
            }
            for (int i = 0; i < 60; i += 3) {
                int fx = (int) ((ptData[i] * sw + Math.sin(t * 0.002 + i) * 22 + sw) % sw);
                int fy = (int) (sh * 0.62 + ptData[i + 1] * sh * 0.28 + Math.cos(t * 0.0025 + i) * 10);
                g.drawCircleFilled(fx, fy, 2, ClientState.withAlpha(0xFFA3E635, 130 + (int) (90 * Math.sin(t * 0.005 + i))));
            }
        } else if ("mc_nether".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x160609, 0x450A0A);
            for (int p = 0; p < 7; p++) {
                int px = (int) ((p + 0.5) * (sw / 7.0));
                int pw = 22 + (p % 3) * 12;
                g.fill(px - pw / 2, 0, px + pw / 2, (int) (sh * 0.84), 0x66180B0E);
            }
            int lavaY = (int) (sh * 0.82);
            g.fill(0, lavaY, sw, sh, 0xFF9A3412);
            for (int x = 0; x < sw; x += 8) {
                int wv = (int) (Math.sin(x * 0.04 + t * 0.003) * 4);
                g.fill(x, lavaY + wv, x + 8, lavaY + wv + 5, 0xFFF97316);
                g.fill(x, lavaY + wv + 8, x + 8, lavaY + wv + 12, 0x66FACC15);
            }
            for (int i = 0; i < ptData.length; i += 3) {
                int sx = (int) ((ptData[i] * sw + Math.sin(t * 0.002 + i) * 16 + sw) % sw);
                int sy = (int) (sh - ((ptData[i + 1] * sh + t * 0.03 * ptData[i + 2]) % sh));
                int col = (i % 6 == 0) ? 0xFF2DD4BF : 0xFFFB7185;
                g.drawCircleFilled(sx, sy, 2, ClientState.withAlpha(col, 145));
            }
        } else if ("mc_end".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x06040C, 0x140A24);
            for (int i = 0; i < 150; i += 3) {
                g.fill((int) (ptData[i] * sw), (int) (ptData[i + 1] * sh), (int) (ptData[i] * sw) + 1, (int) (ptData[i + 1] * sh) + 1, 0x55E9D5FF);
            }
            for (int p = 0; p < 6; p++) {
                int px = (int) (sw * (0.12 + p * 0.15));
                int ph = 110 + (p % 3) * 55;
                g.fill(px - 14, sh - ph, px + 14, sh, 0xFF0E0B16);
                g.drawBorder(px - 14, sh - ph, 28, ph, 0x33A855F7);
                int cy = sh - ph - 20 + (int) Math.round(Math.sin(t * 0.0035 + p) * 5.0);
                g.drawCircleFilled(px, cy, 12, 0x26D946EF);
                g.fill(px - 5, cy - 5, px + 5, cy + 5, 0xFFE879F9);
            }
            for (int i = 0; i < 90; i += 3) {
                int ex = (int) ((ptData[i] * sw + t * 0.015 * ptData[i + 2]) % sw);
                int ey = (int) ((ptData[i + 1] * sh + Math.sin(t * 0.002 + i) * 18 + sh) % sh);
                g.drawCircleFilled(ex, ey, 2, 0x99C084FC);
            }
        } else if ("mc_deepdark".equals(st)) {
            drawSkyGradient(g, sw, sh, 0x03070B, 0x07151C);
            for (int n = 0; n < 5; n++) {
                int nx = (int) (sw * (0.14 + n * 0.18)), ny = (int) (sh * (0.28 + (n % 2) * 0.36));
                int ring = (int) ((t * 0.06 + n * 42) % 140);
                g.drawCircle(nx, ny, Math.max(4, ring), 1, ClientState.withAlpha(0xFF22D3EE, Math.max(0, 120 - ring)));
                g.drawCircleFilled(nx, ny, 8, 0x2822D3EE);
                g.drawCircleFilled(nx, ny, 3, 0xFF22D3EE);
            }
            for (int i = 0; i < 120; i += 3) {
                int sx = (int) (ptData[i] * sw);
                int sy = (int) ((ptData[i + 1] * sh + t * 0.012) % sh);
                g.drawCircleFilled(sx, sy, 2, ClientState.withAlpha(0xFF14B8A6, 95 + (int) (70 * Math.sin(t * 0.004 + i))));
            }
        } else if ("mc_daynight".equals(st)) {
            double ang = (t * 0.00022);
            float dayFactor = 0.5F + 0.5F * (float) Math.sin(ang);
            int topCol = ClientState.lerpColor(0xFF060816, 0xFF172554, dayFactor);
            int botCol = ClientState.lerpColor(0xFF111827, 0xFF3B4D7A, dayFactor);
            drawSkyGradient(g, sw, sh, topCol & 0x00FFFFFF, botCol & 0x00FFFFFF);
            int starA = Math.round((1.0F - dayFactor * 0.75F) * 180.0F);
            for (int i = 0; i < 120; i += 3) g.fill((int) (ptData[i] * sw), (int) (ptData[i + 1] * sh * 0.7F), (int) (ptData[i] * sw) + 1, (int) (ptData[i + 1] * sh * 0.7F) + 1, ClientState.withAlpha(0xFFFFFFFF, starA));
            int cx = sw / 2, cy = (int) (sh * 0.88), rad = Math.min(sw, sh) / 2;
            int sx = cx + (int) (Math.cos(ang) * rad), sy = cy - (int) (Math.sin(ang) * rad);
            int mx0 = cx - (int) (Math.cos(ang) * rad), my0 = cy + (int) (Math.sin(ang) * rad);
            if (sy < sh) { g.drawCircleFilled(sx, sy, 32, 0x28FDE047); g.fill(sx - 14, sy - 14, sx + 14, sy + 14, 0xFFFDE047); }
            if (my0 < sh) { g.drawCircleFilled(mx0, my0, 26, 0x24E4E4E7); g.fill(mx0 - 11, my0 - 11, mx0 + 11, my0 + 11, 0xFFF4F4F5); }
            for (int x = 0; x < sw; x += 4) {
                int mh = sh - (int) (sh * 0.18 + Math.sin(x * 0.01) * 26 + Math.cos(x * 0.023) * 12);
                g.fill(x, mh, x + 4, sh, 0xFF0A0D18);
            }
        } else {
            drawSkyGradient(g, sw, sh, 0x07070C, 0x10101A);
            for (int w = 0; w < 4; w++) {
                int alpha = 22 + w * 14;
                int col = ClientState.withAlpha(w % 2 == 0 ? accent : 0xFF22D3EE, alpha);
                for (int x = 0; x < sw; x += 4) {
                    int y = (int) (sh * (0.46 + w * 0.07) + Math.sin(x * 0.005 + t * (0.001 + w * 0.0003) + w) * 30 + Math.cos(x * 0.003 - t * 0.0007) * 16);
                    g.fill(x, y, x + 4, y + 2, col);
                    g.fill(x, y + 2, x + 4, Math.min(sh, y + 24), ClientState.withAlpha(col, 12));
                }
            }
            for (int i = 0; i < 60; i += 3) {
                int bx = (int) ((ptData[i] * sw + t * 0.01) % sw);
                int by = (int) (ptData[i + 1] * sh);
                g.drawCircleFilled(bx, by, 3, ClientState.withAlpha(accent, 30));
            }
        }

        double dim = Math.max(0.0, Math.min(0.85, cfg.getDouble("title_bg_dim", 0.35)));
        if (dim > 0.01) g.fill(0, 0, sw, sh, ClientState.withAlpha(0xFF000000, (int) Math.round(dim * 255.0)));
    }

    private boolean miniBtn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) { g.drawGlassCard(x, y, w, h, 6, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER); g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 7) / 2, fg, false); }
        if (hov && click) { g.playClickSound(); return true; } return false;
    }
}