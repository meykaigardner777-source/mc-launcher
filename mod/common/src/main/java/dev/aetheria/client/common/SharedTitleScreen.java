package dev.aetheria.client.common;

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

    private static final String[] BG_STYLES = {"waves", "starfield", "grid", "embers", "aurora"};
    private boolean editorMode, catalogOpen = false, accountOpen = false, accInputFocused = false;
    private String selectedId = "logo", draggingId = null, resizingId = null, accInput = "";
    private double dragStartMx = 0, dragStartMy = 0, dragOrigX = 0, dragOrigY = 0, resizeStartScale = 1.0, resizeStartX = 0;
    private int lastSw = 854, lastSh = 480;
    private final float[] stars = new float[180];

    public SharedTitleScreen() { this(false); }
    public SharedTitleScreen(boolean editorMode) {
        this.editorMode = editorMode;
        Random rng = new Random(2602L);
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = rng.nextFloat(); stars[i + 1] = rng.nextFloat(); stars[i + 2] = 0.25F + rng.nextFloat() * 0.75F;
        }
    }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        this.lastSw = Math.max(1, sw); this.lastSh = Math.max(1, sh);
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
        if (catalogOpen) renderCatalogPopup(g, sw, sh, mx, my, false, accent, accentFg);
        drawTopToolbar(g, sw, mx, my, false, null, accent, accentFg);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, int sw, int sh, double mx, double my, HostActions actions) {
        this.lastSw = Math.max(1, sw); this.lastSh = Math.max(1, sh);
        ClientConfig cfg = ClientConfig.get();
        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();
        int ix = (int) mx, iy = (int) my;

        if (drawTopToolbar(g, sw, ix, iy, true, actions, accent, accentFg)) return true;
        if (catalogOpen) {
            if (renderCatalogPopup(g, sw, sh, ix, iy, true, accent, accentFg)) return true;
            catalogOpen = false; return true;
        }
        if (!editorMode && accountOpen) {
            if (renderAccountPopup(g, sw, sh, ix, iy, true, accent, accentFg)) return true;
            accountOpen = false; accInputFocused = false;
        }

        if (editorMode) {
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

        List<ClientConfig.TitleWidget> list = new ArrayList<ClientConfig.TitleWidget>(cfg.titleWidgets.values());
        for (int i = list.size() - 1; i >= 0; i--) {
            ClientConfig.TitleWidget tw = list.get(i); if (!tw.enabled) continue;
            int[] b = getWidgetBounds(tw, sw, sh);
            if (ix >= b[0] && ix <= b[0] + b[2] && iy >= b[1] && iy <= b[1] + b[3]) {
                g.playClickSound();
                handleWidgetClick(g, tw, actions);
                return true;
            }
        }
        return false;
    }

    private void handleWidgetClick(UiBridge.UiDraw g, ClientConfig.TitleWidget tw, HostActions actions) {
        if ("quick_play".equals(tw.id) || "singleplayer".equals(tw.id)) { if (actions != null) actions.openSingleplayer(); }
        else if ("multiplayer".equals(tw.id)) { if (actions != null) actions.openMultiplayer(); }
        else if ("realms".equals(tw.id)) { if (actions != null) actions.openRealms(); }
        else if ("options".equals(tw.id)) { if (actions != null) actions.openOptions(); }
        else if ("client_menu".equals(tw.id)) { if (actions != null) actions.openClientMenu(); }
        else if ("quit".equals(tw.id)) { if (actions != null) actions.quitGame(); }
        else if ("account".equals(tw.id)) { accountOpen = !accountOpen; accInputFocused = false; }
        else if ("background".equals(tw.id)) {
            ClientConfig cfg = ClientConfig.get(); String cur = cfg.getString("title_bg_style", "waves");
            int next = 0; for (int i = 0; i < BG_STYLES.length; i++) if (BG_STYLES[i].equalsIgnoreCase(cur)) next = (i + 1) % BG_STYLES.length;
            cfg.setOption("title_bg_style", BG_STYLES[next]);
        }
    }

    public boolean mouseDragged(double mx, double my) {
        if (!editorMode) return false;
        ClientConfig cfg = ClientConfig.get();
        if (resizingId != null) {
            ClientConfig.TitleWidget tw = cfg.titleWidgets.get(resizingId); if (tw == null) return false;
            tw.scale = Math.max(0.6, Math.min(1.8, Math.round((resizeStartScale + (mx - resizeStartX) / 120.0) * 20.0) / 20.0));
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
        if ("logo".equals(tw.id)) { baseW = 210; baseH = 44; }
        else if ("quick_play".equals(tw.id)) { baseW = 164; baseH = 26; }
        else if ("options".equals(tw.id) || "client_menu".equals(tw.id)) { baseW = 80; baseH = 22; }
        else if ("account".equals(tw.id) || "background".equals(tw.id)) { baseW = 148; baseH = 28; }
        int w = (int) Math.round(baseW * tw.scale), h = (int) Math.round(baseH * tw.scale);
        int x = (int) Math.round(tw.x * sw) - w / 2, y = (int) Math.round(tw.y * sh) - h / 2;
        return new int[]{Math.max(4, Math.min(sw - w - 4, x)), Math.max(4, Math.min(sh - h - 4, y)), w, h, baseW, baseH};
    }

    private void renderWidget(UiBridge.UiDraw g, ClientConfig.TitleWidget tw, int sw, int sh, int mx, int my, int accent, int accentFg) {
        int[] b = getWidgetBounds(tw, sw, sh);
        int x = b[0], y = b[1], w = b[2], h = b[3], bw = b[4], bh = b[5];
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.pushTransform(x, y, (float) tw.scale);
        try {
            int bg = ClientState.withAlpha(hov ? 0xFF1D1E28 : 0xFF121219, (int) Math.round(tw.opacity * 230.0));
            int brd = hov ? accent : ClientState.UI_BORDER;
            if ("logo".equals(tw.id)) {
                g.drawGlassCard(0, 0, bw, bh, tw.borderRadius, ClientState.withAlpha(0xFF0A0A0F, 145), ClientState.withAlpha(accent, 90));
                String t = "AETHERIA CLIENT"; g.text(t, (bw - g.textWidth(t)) / 2, 11, 0xFFF4F4F5, true);
                String sub = ClientConfig.get().activeProfile.toUpperCase() + "  •  MC 26.2";
                g.text(sub, (bw - g.textWidth(sub)) / 2, 26, accent, false);
            } else if ("quick_play".equals(tw.id)) {
                g.drawGlassCard(0, 0, bw, bh, tw.borderRadius, hov ? ClientState.brightenArgb(accent, -25) : accent, accent);
                String lbl = "PLAY MINECRAFT"; g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, accentFg, false);
            } else if ("account".equals(tw.id)) {
                g.drawGlassCard(0, 0, bw, bh, tw.borderRadius, bg, accountOpen ? accent : brd);
                g.drawCircleFilled(13, bh / 2, 6, ClientState.withAlpha(accent, 60));
                g.drawCircleFilled(13, bh / 2, 3, 0xFF10B981);
                g.text(g.currentUsername(), 24, 6, ClientState.UI_TEXT, false);
                g.text("Click to switch/add", 24, 16, ClientState.UI_TEXT_DIM, false);
            } else if ("background".equals(tw.id)) {
                String st = ClientConfig.get().getString("title_bg_style", "waves").toUpperCase();
                g.drawGlassCard(0, 0, bw, bh, tw.borderRadius, bg, brd);
                g.text("BG: " + st, 10, 6, ClientState.UI_TEXT, false);
                g.text("Click to cycle style", 10, 16, ClientState.UI_TEXT_DIM, false);
            } else {
                String lbl = "singleplayer".equals(tw.id) ? "Singleplayer" : "multiplayer".equals(tw.id) ? "Multiplayer"
                    : "realms".equals(tw.id) ? "Minecraft Realms" : "options".equals(tw.id) ? "Options"
                    : "client_menu".equals(tw.id) ? "Client Menu" : "Quit (ESC)";
                int textCol = "quit".equals(tw.id) && hov ? 0xFFFCA5A5 : ClientState.UI_TEXT;
                g.drawGlassCard(0, 0, bw, bh, tw.borderRadius, bg, "quit".equals(tw.id) && hov ? 0xFFEF4444 : brd);
                g.text(lbl, (bw - g.textWidth(lbl)) / 2, (bh - 8) / 2, textCol, false);
            }
        } finally { g.popTransform(); }
    }

    private boolean drawTopToolbar(UiBridge.UiDraw g, int sw, int mx, int my, boolean click, HostActions actions, int accent, int accentFg) {
        int barW = editorMode ? 240 : 158, bx = sw / 2 - barW / 2, by = 6, bh = 20;
        if (click && (mx < bx || mx > bx + barW || my < by || my > by + bh)) return false;
        if (!click) g.drawGlassCard(bx, by, barW, bh, 10, 0xCC09090B, ClientState.UI_BORDER);
        if (editorMode) {
            int active = 0; for (ClientConfig.TitleWidget tw : ClientConfig.get().titleWidgets.values()) if (tw.enabled) active++;
            if (miniBtn(g, bx + 4, by + 2, 84, 16, "Widgets (" + active + "/10)", 0xFF181822, ClientState.UI_TEXT, mx, my, click)) { catalogOpen = !catalogOpen; return true; }
            if (miniBtn(g, bx + 92, by + 2, 54, 16, "Reset", 0xFF28181B, ClientState.UI_TEXT_MUTED, mx, my, click)) { ClientConfig.get().resetTitleLayout(); ClientConfig.get().save(); return true; }
            if (miniBtn(g, bx + 150, by + 2, 86, 16, "Done Editing", accent, accentFg, mx, my, click)) { editorMode = false; catalogOpen = false; ClientConfig.get().save(); if (actions != null) actions.exitEditor(); return true; }
        } else {
            if (miniBtn(g, bx + 4, by + 2, 74, 16, "Edit Layout", 0xFF181822, ClientState.UI_TEXT, mx, my, click)) { editorMode = true; accountOpen = false; return true; }
            if (miniBtn(g, bx + 82, by + 2, 72, 16, "Catalog", accent, accentFg, mx, my, click)) { catalogOpen = !catalogOpen; accountOpen = false; return true; }
        }
        return false;
    }

    private boolean renderAccountPopup(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, int accent, int accentFg) {
        int pw = Math.min(280, sw - 20), ph = Math.min(230, sh - 60), px = Math.max(10, (sw - pw) / 2), py = Math.max(32, (sh - ph) / 2);
        if (click && (mx < px || mx > px + pw || my < py || my > py + ph)) return false;
        ClientConfig cfg = ClientConfig.get();
        if (!click) {
            g.drawGlassCard(px, py, pw, ph, 10, 0xEE0D0D14, accent);
            g.text("Account Manager (" + g.currentUsername() + ")", px + 10, py + 9, ClientState.UI_TEXT, false);
        }
        if (miniBtn(g, px + pw - 22, py + 6, 16, 16, "x", 0xFF28181B, 0xFFFFFFFF, mx, my, click)) { accountOpen = false; accInputFocused = false; return true; }
        int iy = py + 28, inW = pw - 74;
        boolean inHov = mx >= px + 10 && mx <= px + 10 + inW && my >= iy && my <= iy + 18;
        if (!click) {
            g.drawGlassCard(px + 10, iy, inW, 18, 5, ClientState.UI_INPUT, accInputFocused ? accent : (inHov ? ClientState.UI_BORDER_HOV : ClientState.UI_BORDER));
            g.text(accInput.isEmpty() ? (accInputFocused ? "_" : "Type username...") : (accInput + (accInputFocused ? "_" : "")), px + 16, iy + 5, accInput.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);
        } else if (inHov) { accInputFocused = true; return true; } else accInputFocused = false;
        if (miniBtn(g, px + pw - 58, iy, 48, 18, "+ Add", accent, accentFg, mx, my, click)) {
            if (!accInput.trim().isEmpty()) { cfg.addAccount(accInput.trim(), "offline", "Alt"); g.switchAccount(accInput.trim(), ""); accInput = ""; }
            return true;
        }
        int ry = iy + 24;
        for (int i = 0; i < cfg.accounts.size() && ry + 22 <= py + ph - 8; i++) {
            ClientConfig.AccountEntry a = cfg.accounts.get(i);
            boolean cur = a.name.equalsIgnoreCase(g.currentUsername());
            if (!click) { g.drawGlassCard(px + 10, ry, pw - 20, 20, 5, 0xFF15151E, cur ? accent : ClientState.UI_BORDER); g.text(a.name, px + 16, ry + 6, cur ? accent : ClientState.UI_TEXT, false); }
            if (miniBtn(g, px + pw - 80, ry + 2, 42, 16, cur ? "Active" : "Use", cur ? accent : 0xFF1E293B, cur ? accentFg : ClientState.UI_TEXT, mx, my, click)) {
                cfg.activeAccountId = a.id; cfg.save(); g.switchAccount(a.name, a.uuid); return true;
            }
            if (miniBtn(g, px + pw - 34, ry + 2, 20, 16, "Del", 0xFF3B1A1E, 0xFFFFFFFF, mx, my, click)) { cfg.removeAccount(a.id); return true; }
            ry += 23;
        }
        return true;
    }

    private boolean renderCatalogPopup(UiBridge.UiDraw g, int sw, int sh, int mx, int my, boolean click, int accent, int accentFg) {
        int pw = Math.min(380, sw - 20), ph = Math.min(210, sh - 40), px = (sw - pw) / 2, py = Math.max(30, (sh - ph) / 2);
        if (click && (mx < px || mx > px + pw || my < py || my > py + ph)) return false;
        if (!click) {
            g.drawGlassCard(px, py, pw, ph, 10, 0xEE0D0D14, accent);
            g.text("Title Widget Catalog (10 Widgets)", px + 12, py + 9, ClientState.UI_TEXT, false);
        }
        if (miniBtn(g, px + pw - 24, py + 6, 18, 16, "x", 0xFF28181B, 0xFFFFFFFF, mx, my, click)) { catalogOpen = false; return true; }
        
        List<ClientConfig.TitleWidget> list = new ArrayList<ClientConfig.TitleWidget>(ClientConfig.get().titleWidgets.values());
        int colW = (pw - 26) / 2;
        for (int i = 0; i < list.size(); i++) {
            ClientConfig.TitleWidget tw = list.get(i);
            int col = i % 2, row = i / 2;
            int wx = px + 10 + col * (colW + 6), wy = py + 28 + row * 23;
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

    private void renderAnimatedBackground(UiBridge.UiDraw g, int sw, int sh, ClientConfig cfg, int accent) {
        g.fill(0, 0, sw, sh, 0xFF07070B);
        String st = cfg.getString("title_bg_style", "waves").toLowerCase();
        long t = System.currentTimeMillis();
        if ("starfield".equals(st)) {
            for (int i = 0; i < stars.length; i += 3) {
                float sx = ((stars[i] * sw + (t * 0.015F * stars[i + 2])) % sw), sy = stars[i + 1] * sh;
                g.fill((int) sx, (int) sy, (int) sx + 2, (int) sy + 2, ClientState.withAlpha(accent, (int) (stars[i + 2] * 180)));
            }
        } else if ("grid".equals(st)) {
            int off = (int) ((t / 35L) % 32);
            for (int x = off; x < sw; x += 32) g.fill(x, 0, x + 1, sh, ClientState.withAlpha(accent, 24));
            for (int y = off; y < sh; y += 32) g.fill(0, y, sw, y + 1, ClientState.withAlpha(accent, 24));
        } else if ("embers".equals(st)) {
            for (int i = 0; i < stars.length; i += 3) {
                float ex = stars[i] * sw + (float) Math.sin(t * 0.002 + i) * 14.0F, ey = sh - ((stars[i + 1] * sh + t * 0.04F * stars[i + 2]) % sh);
                g.fill((int) ex, (int) ey, (int) ex + 3, (int) ey + 3, ClientState.withAlpha(0xFFF97316, 150));
            }
        } else if ("aurora".equals(st)) {
            for (int b = 0; b < 5; b++) {
                int y = (int) (sh * 0.3 + Math.sin(t * 0.001 + b) * 45 + b * 22);
                g.fill(0, y, sw, y + 18, ClientState.withAlpha(b % 2 == 0 ? accent : 0xFF22D3EE, 22));
            }
        } else {
            for (int x = 0; x < sw; x += 12) {
                int y = (int) (sh * 0.55 + Math.sin(x * 0.01 + t * 0.0018) * 34);
                g.fill(x, y, x + 10, y + 2, ClientState.withAlpha(accent, 75));
            }
        }
    }

    private boolean miniBtn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my, boolean click) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        if (!click) { g.drawGlassCard(x, y, w, h, 6, hov ? ClientState.brightenArgb(bg, 18) : bg, ClientState.UI_BORDER); g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 7) / 2, fg, false); }
        if (hov && click) { g.playClickSound(); return true; } return false;
    }
}