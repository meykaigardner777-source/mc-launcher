package dev.aetheria.client.common;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class SharedHudRenderer {
    private static final SimpleDateFormat CLOCK_FMT = new SimpleDateFormat("hh:mm a");
    private static long lastClockSec = -1L;
    private static String cachedClockStr = "";

    private SharedHudRenderer() {}

    public static void renderInGame(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        renderCrosshair(g, sw, sh);
        boolean shadow = cfg.getBool("hud_shadow", true);
        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (m.enabled) renderModule(g, d, m, sw, sh, shadow, false);
        }
    }

    public static int[] getModulePixelBounds(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh) {
        int w, h;
        if ("keystrokes".equals(m.id)) { w = 76; h = 72; }
        else {
            String[] lines = getModuleLines(d, m);
            int maxW = 0; for (String ln : lines) maxW = Math.max(maxW, g.textWidth(ln));
            w = Math.max(36, maxW + 10);
            h = Math.max(14, lines.length * 11 + 6);
        }
        return new int[]{(int) Math.round(m.x * sw), (int) Math.round(m.y * sh), (int) Math.round(w * m.scale), (int) Math.round(h * m.scale)};
    }

    public static void renderModule(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh, boolean shadow, boolean editorPreview) {
        int px = (int) Math.round(m.x * sw), py = (int) Math.round(m.y * sh);
        float scale = (float) Math.max(0.5, Math.min(2.5, m.scale));
        int textColor = m.chroma ? ClientState.hsbToRgb((System.currentTimeMillis() % 3000L) / 3000.0f, 0.85f, 1.0f) : ClientState.parseHexColor(m.color, 0xFFFFFFFF);

        g.pushTransform((float) px, (float) py, scale);
        if ("keystrokes".equals(m.id)) {
            drawKeyBox(g, 26, 0, 24, 20, "W", d.isMoveW(), m, textColor, shadow);
            drawKeyBox(g, 0, 22, 24, 20, "A", d.isMoveA(), m, textColor, shadow);
            drawKeyBox(g, 26, 22, 24, 20, "S", d.isMoveS(), m, textColor, shadow);
            drawKeyBox(g, 52, 22, 24, 20, "D", d.isMoveD(), m, textColor, shadow);
            drawKeyBox(g, 0, 44, 37, 14, "LMB " + ClientState.getLeftCps(), ClientState.isLeftDown(), m, textColor, shadow);
            drawKeyBox(g, 39, 44, 37, 14, "RMB " + ClientState.getRightCps(), ClientState.isRightDown(), m, textColor, shadow);
            drawKeyBox(g, 0, 60, 76, 12, "SPACE", d.isJumpDown(), m, textColor, shadow);
        } else {
            String[] lines = getModuleLines(d, m);
            int maxW = 0; for (String ln : lines) maxW = Math.max(maxW, g.textWidth(ln));
            int boxW = Math.max(36, maxW + 10), boxH = Math.max(14, lines.length * 11 + 6);
            if (m.bg) { int a = (int) Math.round(Math.max(0.0, Math.min(1.0, m.opacity)) * 180.0); g.fill(0, 0, boxW, boxH, (a << 24) | 0x0B0E14); }
            if (m.border) g.drawBorder(0, 0, boxW, boxH, editorPreview && !m.enabled ? 0x88FFAA44 : 0x55FFFFFF);
            for (int i = 0; i < lines.length; i++) g.text(lines[i], 5, 4 + i * 11, textColor, shadow);
        }
        g.popTransform();
    }

    private static void drawKeyBox(UiBridge.UiDraw g, int x, int y, int w, int h, String txt, boolean down, ClientConfig.HudModule m, int col, boolean shadow) {
        int bgCol = down ? 0xCCFFFFFF : (((int) (m.opacity * 170) << 24) | 0x0B0E14);
        int fgCol = down ? 0xFF101318 : col;
        if (m.bg || down) g.fill(x, y, x + w, y + h, bgCol);
        if (m.border) g.drawBorder(x, y, w, h, 0x55FFFFFF);
        g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fgCol, shadow && !down);
    }

    public static String[] getModuleLines(UiBridge.HudData d, ClientConfig.HudModule m) {
        if (!d.hasWorld()) return new String[]{m.label};
        String id = m.id;
        if ("fps".equals(id)) return new String[]{d.fps() + " FPS"};
        if ("coords".equals(id)) return new String[]{"XYZ: " + d.x() + " / " + d.y() + " / " + d.z(), "Facing: " + d.facing()};
        if ("biome".equals(id)) return new String[]{d.biome()};
        if ("ping".equals(id)) return new String[]{d.pingMs() + " ms"};
        if ("cps".equals(id)) return new String[]{ClientState.getLeftCps() + " | " + ClientState.getRightCps() + " CPS"};
        if ("togglesprint".equals(id)) return new String[]{d.isSprinting() ? "[Sprinting (Toggled)]" : "[Walking]"};
        if ("potions".equals(id)) return d.activePotions();
        if ("compass".equals(id)) {
            float yaw = ((d.yaw() % 360f) + 360f) % 360f;
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            return new String[]{"<<  " + dirs[Math.round(yaw / 45f) % 8] + " (" + Math.round(yaw) + " deg)  >>"};
        }
        if ("clock".equals(id)) {
            long sec = System.currentTimeMillis() / 1000L;
            if (sec != lastClockSec) { lastClockSec = sec; cachedClockStr = CLOCK_FMT.format(new Date(sec * 1000L)); }
            return new String[]{cachedClockStr};
        }
        if ("session".equals(id)) {
            long s = Math.max(0, (System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L);
            return new String[]{String.format("Session: %02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)};
        }
        if ("server_ip".equals(id)) return new String[]{d.serverIp()};
        if ("day_count".equals(id)) return new String[]{"Day " + d.dayCount()};
        if ("durability".equals(id)) return new String[]{d.heldToolDurability()};
        if ("armor".equals(id)) return new String[]{d.armorStatus()};
        if ("combo".equals(id)) return new String[]{ClientState.getComboCount() + " Hit Combo"};
        if ("reach".equals(id)) return new String[]{String.format("%.2f blocks", ClientState.getLastReach())};
        return new String[]{m.label};
    }

    private static void renderCrosshair(UiBridge.UiDraw g, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        String style = cfg.getString("pvp_xhair_style", "classic");
        if ("vanilla".equalsIgnoreCase(style)) return;
        int cx = sw / 2, cy = sh / 2, sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6)), gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2)), col = 0xFFFFFFFF;
        if ("dot".equalsIgnoreCase(style)) g.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
        else if ("circle".equalsIgnoreCase(style)) { int r = sz + gap; g.drawBorder(cx - r, cy - r, r * 2 + 1, r * 2 + 1, col); }
        else {
            if (!"t_shape".equalsIgnoreCase(style)) g.fill(cx, cy - gap - sz, cx + 1, cy - gap, col);
            g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + sz, col);
            g.fill(cx - gap - sz, cy, cx - gap, cy + 1, col);
            g.fill(cx + gap + 1, cy, cx + gap + 1 + sz, cy + 1, col);
        }
    }
}