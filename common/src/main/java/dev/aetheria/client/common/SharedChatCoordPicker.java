package dev.aetheria.client.common;

import java.util.*;

public class SharedChatCoordPicker {
    private boolean expanded = false, nameFocused = false, initializedEntry = false;
    private int entryIdx = 0, assignSlot = 0; // 0=X, 1=Z, 2=Y
    private int valX = 0, valY = 64, valZ = 0;
    private String wpName = "Chat WP", savedMsg = "";
    private long lastTopTimeMs = 0L, savedMsgUntilMs = 0L;

    public boolean isInputFocused() { return expanded && nameFocused; }

    private void syncFromEntry(ClientState.ChatCoordEntry e) {
        if (e == null || e.numbers.length < 2) return;
        valX = e.numbers[0];
        if (e.numbers.length >= 3) {
            valY = e.numbers[1];
            valZ = e.numbers[2];
        } else {
            valY = ClientState.getPlayerY();
            valZ = e.numbers[1];
        }
        assignSlot = 0;
        initializedEntry = true;
    }

    public void render(UiBridge.UiDraw g, int sw, int sh, int mx, int my) {
        if (!ClientConfig.get().getBool("chat_coord_picker", true)) return;
        List<ClientState.ChatCoordEntry> list = ClientState.getChatCoordEntries();
        if (list.isEmpty()) return;

        long topTime = list.get(0).timeMs;
        if (topTime != lastTopTimeMs) {
            lastTopTimeMs = topTime;
            entryIdx = 0;
            initializedEntry = false;
        }
        if (entryIdx >= list.size()) entryIdx = 0;
        ClientState.ChatCoordEntry cur = list.get(entryIdx);
        if (!initializedEntry) syncFromEntry(cur);

        int accent = ClientState.getAccentColor(), accentFg = ClientState.getAccentFg();
        if (!expanded) {
            int bw = 104, bh = 18, bx = sw - bw - 8, by = sh - 38;
            boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            g.drawRoundedRect(bx, by, bw, bh, 3, hov ? 0xE81C1C26 : 0xD809090B);
            g.drawRoundedOutline(bx, by, bw, bh, 3, 1, "solid", hov ? accent : ClientState.UI_BORDER, hov ? accent : ClientState.UI_BORDER);
            g.text("+ WP from Chat", bx + 8, by + 5, hov ? accent : ClientState.UI_TEXT, false);
            return;
        }

        int pw = Math.min(370, sw - 16), ph = 108, px = sw - pw - 8, py = sh - ph - 38;
        g.drawGlassCard(px, py, pw, ph, 3, 0xEE0D0D14, accent);

        // Header: source message + prev/next + close
        g.text("Chat Coords (" + (entryIdx + 1) + "/" + list.size() + ")", px + 8, py + 7, accent, false);
        drawMiniBtn(g, px + pw - 62, py + 4, 18, 15, "<", 0xFF1A1A24, ClientState.UI_TEXT, mx, my);
        drawMiniBtn(g, px + pw - 42, py + 4, 18, 15, ">", 0xFF1A1A24, ClientState.UI_TEXT, mx, my);
        drawMiniBtn(g, px + pw - 20, py + 4, 15, 15, "x", 0xFF3B1A1E, 0xFFFFFFFF, mx, my);

        g.text(cur.preview, px + 8, py + 22, ClientState.UI_TEXT_MUTED, false);

        // Detected numbers row
        g.text("Nums:", px + 8, py + 38, ClientState.UI_TEXT_DIM, false);
        int nx = px + 38;
        for (int i = 0; i < cur.numbers.length; i++) {
            String ns = String.valueOf(cur.numbers[i]);
            int nw = Math.max(22, g.textWidth(ns) + 10);
            if (nx + nw > px + pw - 8) break;
            boolean isUsed = (cur.numbers[i] == valX || cur.numbers[i] == valZ || cur.numbers[i] == valY);
            drawMiniBtn(g, nx, py + 35, nw, 15, ns, isUsed ? ClientState.withAlpha(accent, 42) : 0xFF191924, ClientState.UI_TEXT, mx, my);
            nx += nw + 4;
        }

        // X / Y / Z slot selector + Swap X/Z
        int sy = py + 55;
        String[] slotLabels = {"X:" + valX, "Z:" + valZ, "Y:" + valY};
        int[] slotCols = {0xFFEF4444, 0xFF3B82F6, 0xFF10B981};
        int sx = px + 8;
        for (int s = 0; s < 3; s++) {
            int sw0 = Math.max(48, g.textWidth(slotLabels[s]) + 12);
            boolean sel = (assignSlot == s);
            g.drawRoundedRect(sx, sy, sw0, 17, 3, sel ? ClientState.withAlpha(slotCols[s], 60) : 0xFF161620);
            if (sel) g.drawRoundedOutline(sx, sy, sw0, 17, 3, 1, "solid", slotCols[s], slotCols[s]);
            g.text(slotLabels[s], sx + 6, sy + 5, sel ? 0xFFFFFFFF : ClientState.UI_TEXT_MUTED, false);
            sx += sw0 + 4;
        }
        drawMiniBtn(g, sx + 2, sy, 60, 17, "Swap X/Z", 0xFF1E1E28, ClientState.UI_TEXT, mx, my);

        // Bottom row: Name input + Save WP button
        int by = py + 78, inW = pw - 96;
        g.drawGlassCard(px + 8, by, inW, 20, 3, ClientState.UI_INPUT, nameFocused ? accent : ClientState.UI_BORDER);
        String disp = wpName.isEmpty() ? (nameFocused ? "_" : "Waypoint name...") : (wpName + (nameFocused ? "_" : ""));
        g.text(disp, px + 14, by + 6, wpName.isEmpty() ? ClientState.UI_TEXT_DIM : ClientState.UI_TEXT, false);

        boolean savedFlash = System.currentTimeMillis() < savedMsgUntilMs;
        drawMiniBtn(g, px + 12 + inW, by, 76, 20, savedFlash ? "Saved!" : "Save WP", savedFlash ? 0xFF10B981 : accent, savedFlash ? 0xFF042F2E : accentFg, mx, my);
    }

    public boolean mouseClicked(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh, double mx, double my, int btn) {
        if (btn != 0 || !ClientConfig.get().getBool("chat_coord_picker", true)) return false;
        List<ClientState.ChatCoordEntry> list = ClientState.getChatCoordEntries();
        if (list.isEmpty()) return false;

        if (!expanded) {
            int bw = 104, bh = 18, bx = sw - bw - 8, by = sh - 38;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                g.playClickSound();
                expanded = true;
                syncFromEntry(list.get(Math.min(entryIdx, list.size() - 1)));
                return true;
            }
            return false;
        }

        int pw = Math.min(370, sw - 16), ph = 108, px = sw - pw - 8, py = sh - ph - 38;
        if (mx < px || mx > px + pw || my < py || my > py + ph) {
            nameFocused = false;
            return false;
        }

        if (hit(mx, my, px + pw - 20, py + 4, 15, 15)) { g.playClickSound(); expanded = false; nameFocused = false; return true; }
        if (hit(mx, my, px + pw - 62, py + 4, 18, 15)) {
            g.playClickSound();
            entryIdx = (entryIdx + 1) % list.size();
            syncFromEntry(list.get(entryIdx));
            return true;
        }
        if (hit(mx, my, px + pw - 42, py + 4, 18, 15)) {
            g.playClickSound();
            entryIdx = (entryIdx - 1 + list.size()) % list.size();
            syncFromEntry(list.get(entryIdx));
            return true;
        }

        ClientState.ChatCoordEntry cur = list.get(Math.min(entryIdx, list.size() - 1));
        int nx = px + 38;
        for (int i = 0; i < cur.numbers.length; i++) {
            String ns = String.valueOf(cur.numbers[i]);
            int nw = Math.max(22, g.textWidth(ns) + 10);
            if (nx + nw > px + pw - 8) break;
            if (hit(mx, my, nx, py + 35, nw, 15)) {
                g.playClickSound();
                if (assignSlot == 0) { valX = cur.numbers[i]; assignSlot = 1; }
                else if (assignSlot == 1) { valZ = cur.numbers[i]; assignSlot = 0; }
                else { valY = cur.numbers[i]; assignSlot = 0; }
                return true;
            }
            nx += nw + 4;
        }

        int sy = py + 55;
        String[] slotLabels = {"X:" + valX, "Z:" + valZ, "Y:" + valY};
        int sx = px + 8;
        for (int s = 0; s < 3; s++) {
            int sw0 = Math.max(48, g.textWidth(slotLabels[s]) + 12);
            if (hit(mx, my, sx, sy, sw0, 17)) { g.playClickSound(); assignSlot = s; return true; }
            sx += sw0 + 4;
        }
        if (hit(mx, my, sx + 2, sy, 60, 17)) {
            g.playClickSound();
            int tmp = valX; valX = valZ; valZ = tmp;
            return true;
        }

        int by = py + 78, inW = pw - 96;
        if (hit(mx, my, px + 8, by, inW, 20)) { g.playClickSound(); nameFocused = true; return true; }
        nameFocused = false;
        if (hit(mx, my, px + 12 + inW, by, 76, 20)) {
            g.playClickSound();
            if (d.hasWorld()) {
                ClientConfig.get().addWaypointAt(wpName.trim().isEmpty() ? "Chat WP" : wpName.trim(), valX, valY, valZ, d.dimensionId(), d.worldKey());
                savedMsgUntilMs = System.currentTimeMillis() + 1800L;
                expanded = false;
            }
            return true;
        }
        return true;
    }

    public boolean keyPressed(boolean isBackspace, boolean isEscape, boolean isEnter, UiBridge.HudData d) {
        if (!expanded || !nameFocused) return false;
        if (isEscape) { nameFocused = false; return true; }
        if (isBackspace && wpName.length() > 0) { wpName = wpName.substring(0, wpName.length() - 1); return true; }
        if (isEnter && d.hasWorld()) {
            ClientConfig.get().addWaypointAt(wpName.trim().isEmpty() ? "Chat WP" : wpName.trim(), valX, valY, valZ, d.dimensionId(), d.worldKey());
            savedMsgUntilMs = System.currentTimeMillis() + 1800L;
            nameFocused = false;
            expanded = false;
            return true;
        }
        return true;
    }

    public boolean charTyped(char ch) {
        if (!expanded || !nameFocused) return false;
        if (ch >= 32 && ch != 127 && wpName.length() < 24) wpName += ch;
        return true;
    }

    private boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawMiniBtn(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, int bg, int fg, int mx, int my) {
        boolean hov = hit(mx, my, x, y, w, h);
        g.drawRoundedRect(x, y, w, h, 3, hov ? ClientState.brightenArgb(bg, 18) : bg);
        g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 7) / 2, fg, false);
    }
}