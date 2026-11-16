package dev.aetheria.client.common;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class SharedHudRenderer {
    private static final SimpleDateFormat CLOCK_12 = new SimpleDateFormat("hh:mm a");
    private static final SimpleDateFormat CLOCK_24 = new SimpleDateFormat("HH:mm");
    private static long lastClockSec = -1L;
    private static boolean lastClock24 = false;
    private static String cachedClockStr = "";

    private SharedHudRenderer() {}

    public static void renderInGame(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        renderCrosshair(g, d, sw, sh);
        boolean globalShadow = cfg.getBool("hud_shadow", true);
        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (!m.enabled) continue;
            if ("togglesprint".equals(m.id) && cfg.getBool("togglesprint_hide_hud", false)) continue;
            renderModule(g, d, m, sw, sh, globalShadow && m.shadow, false);
        }
    }

    public static int[] getModulePixelBounds(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh) {
        int w, h;
        int px = Math.max(2, Math.min(16, m.paddingX));
        int py = Math.max(2, Math.min(16, m.paddingY));
        if ("keystrokes".equals(m.id)) {
            ClientConfig cfg = ClientConfig.get();
            boolean showCps = cfg.getBool("keystrokes_cps", true), showSpace = cfg.getBool("keystrokes_space", true);
            w = 76 + (px - 6) * 2;
            h = 42 + (showCps ? 16 : 0) + (showSpace ? 14 : 0) + (py - 4) * 2;
        } else {
            String[] lines = getModuleLines(d, m);
            int maxW = 0; for (String ln : lines) maxW = Math.max(maxW, g.textWidth(ln));
            w = Math.max(34, maxW + px * 2);
            h = Math.max(14, lines.length * 11 + py * 2);
        }
        return new int[]{(int) Math.round(m.x * sw), (int) Math.round(m.y * sh), (int) Math.round(w * m.scale), (int) Math.round(h * m.scale)};
    }

    public static void renderModule(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh, boolean shadow, boolean editorPreview) {
        int px = (int) Math.round(m.x * sw), py = (int) Math.round(m.y * sh);
        float scale = (float) Math.max(0.5, Math.min(2.5, m.scale));
        int textColor = m.chroma ? ClientState.hsbToRgb((System.currentTimeMillis() % 3000L) / 3000.0f, 0.85f, 1.0f) : ClientState.parseHexColor(m.color, 0xFFFFFFFF);
        int rad = Math.max(0, Math.min(12, m.borderRadius));
        int padX = Math.max(2, Math.min(16, m.paddingX));
        int padY = Math.max(2, Math.min(16, m.paddingY));

        g.pushTransform((float) px, (float) py, scale);
        if ("keystrokes".equals(m.id)) {
            ClientConfig cfg = ClientConfig.get();
            boolean showCps = cfg.getBool("keystrokes_cps", true), showSpace = cfg.getBool("keystrokes_space", true);
            int kr = Math.min(5, rad);
            int ox = padX - 6, oy = padY - 4;
            drawKeyBox(g, ox + 26, oy, 24, 20, kr, "W", d.isMoveW(), m, textColor, shadow);
            drawKeyBox(g, ox, oy + 22, 24, 20, kr, "A", d.isMoveA(), m, textColor, shadow);
            drawKeyBox(g, ox + 26, oy + 22, 24, 20, kr, "S", d.isMoveS(), m, textColor, shadow);
            drawKeyBox(g, ox + 52, oy + 22, 24, 20, kr, "D", d.isMoveD(), m, textColor, shadow);
            int curY = oy + 44;
            if (showCps) {
                drawKeyBox(g, ox, curY, 37, 14, Math.min(4, kr), "LMB " + ClientState.getLeftCps(), ClientState.isLeftDown(), m, textColor, shadow);
                drawKeyBox(g, ox + 39, curY, 37, 14, Math.min(4, kr), "RMB " + ClientState.getRightCps(), ClientState.isRightDown(), m, textColor, shadow);
                curY += 16;
            }
            if (showSpace) drawKeyBox(g, ox, curY, 76, 12, Math.min(4, kr), "SPACE", d.isJumpDown(), m, textColor, shadow);
        } else {
            String[] lines = getModuleLines(d, m);
            int maxW = 0; for (String ln : lines) maxW = Math.max(maxW, g.textWidth(ln));
            int boxW = Math.max(34, maxW + padX * 2), boxH = Math.max(14, lines.length * 11 + padY * 2);
            int bgCol = m.bg ? ClientState.withAlpha(ClientState.parseHexColor(m.bgColor, 0xFF09090B), (int) Math.round(Math.max(0.0, Math.min(1.0, m.opacity)) * 255.0)) : 0;
            int brdCol = m.border ? ClientState.parseHexColor(m.borderColor, 0xFF27272A) : 0;
            g.drawGlassCard(0, 0, boxW, boxH, rad, bgCol, brdCol);
            for (int i = 0; i < lines.length; i++) g.text(lines[i], padX, padY + i * 11, textColor, shadow);
        }
        g.popTransform();
    }

    private static void drawKeyBox(UiBridge.UiDraw g, int x, int y, int w, int h, int rad, String txt, boolean down, ClientConfig.HudModule m, int col, boolean shadow) {
        int bgCol = down ? 0xE6F4F4F5 : (m.bg ? ClientState.withAlpha(ClientState.parseHexColor(m.bgColor, 0xFF09090B), (int) Math.round(m.opacity * 255.0)) : 0);
        int fgCol = down ? 0xFF09090B : col;
        int brdCol = m.border ? (down ? 0xEEFFFFFF : ClientState.parseHexColor(m.borderColor, 0xFF27272A)) : 0;
        g.drawGlassCard(x, y, w, h, rad, bgCol, brdCol);
        g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fgCol, shadow && !down);
    }

    public static String[] getModuleLines(UiBridge.HudData d, ClientConfig.HudModule m) {
        if (!d.hasWorld()) return new String[]{m.label};
        ClientConfig cfg = ClientConfig.get();
        String id = m.id;
        if ("fps".equals(id)) return new String[]{cfg.getBool("fps_show_label", true) ? (d.fps() + " FPS") : String.valueOf(d.fps())};
        if ("coords".equals(id)) {
            if (cfg.getBool("coords_compact", false)) return new String[]{d.x() + ", " + d.y() + ", " + d.z() + (cfg.getBool("coords_facing", true) ? " [" + d.facing().substring(0, 1) + "]" : "")};
            if (cfg.getBool("coords_facing", true)) return new String[]{"XYZ: " + d.x() + " / " + d.y() + " / " + d.z(), "Facing: " + d.facing()};
            return new String[]{"XYZ: " + d.x() + " / " + d.y() + " / " + d.z()};
        }
        if ("biome".equals(id)) return new String[]{d.biome()};
        if ("ping".equals(id)) return new String[]{d.pingMs() + " ms"};
        if ("cps".equals(id)) return new String[]{ClientState.getLeftCps() + " | " + ClientState.getRightCps() + " CPS"};
        if ("togglesprint".equals(id)) return new String[]{d.isSprinting() ? "[Sprinting (Toggled)]" : "[Walking]"};
        if ("potions".equals(id)) return d.activePotions();
        if ("compass".equals(id)) {
            float yaw = ((d.yaw() % 360f) + 360f) % 360f;
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            String deg = cfg.getBool("compass_degrees", true) ? " (" + Math.round(yaw) + " deg)" : "";
            return new String[]{"<<  " + dirs[Math.round(yaw / 45f) % 8] + deg + "  >>"};
        }
        if ("clock".equals(id)) {
            boolean use24 = cfg.getBool("clock_24h", false);
            long sec = System.currentTimeMillis() / 1000L;
            if (sec != lastClockSec || use24 != lastClock24) { lastClockSec = sec; lastClock24 = use24; cachedClockStr = (use24 ? CLOCK_24 : CLOCK_12).format(new Date(sec * 1000L)); }
            return new String[]{cachedClockStr};
        }
        if ("session".equals(id)) { long s = Math.max(0, (System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L); return new String[]{String.format("Session: %02d:%02d:%02d", Long.valueOf(s / 3600), Long.valueOf((s % 3600) / 60), Long.valueOf(s % 60))}; }
        if ("server_ip".equals(id)) return new String[]{d.serverIp()};
        if ("day_count".equals(id)) return new String[]{"Day " + d.dayCount()};
        if ("durability".equals(id)) return new String[]{d.heldToolDurability()};
        if ("armor".equals(id)) return new String[]{d.armorStatus()};
        if ("combo".equals(id)) return new String[]{ClientState.getComboCount() + " Hit Combo"};
        if ("reach".equals(id)) return new String[]{String.format("%.2f blocks", Double.valueOf(ClientState.getLastReach()))};
        return new String[]{m.label};
    }

    private static void renderCrosshair(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        String baseStyle = cfg.getString("pvp_xhair_style", "classic");
        if ("vanilla".equalsIgnoreCase(baseStyle)) return;
        boolean detectTarget = cfg.getBool("pvp_xhair_target", true), playersOnly = cfg.getBool("pvp_xhair_target_players_only", true);
        double range = Math.max(3.0, cfg.getDouble("pvp_xhair_target_range", 12.0));
        boolean inView = detectTarget && (playersOnly ? d.targetPlayerInView(range) : d.targetEntityInView(range));
        String style = baseStyle;
        int col = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF);
        if (inView) {
            String targetStyle = cfg.getString("pvp_xhair_target_style", "same");
            if (!"same".equalsIgnoreCase(targetStyle) && !"vanilla".equalsIgnoreCase(targetStyle)) style = targetStyle;
            col = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);
        }
        int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6)), gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2));
        int thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1))));
        drawCrosshairShape(g, sw / 2, sh / 2, style, sz, gap, thick, col);
    }

    public static void drawCrosshairShape(UiBridge.UiDraw g, int cx, int cy, String style, int sz, int gap, int thick, int col) {
        int half = (thick - 1) / 2;
        if ("dot".equalsIgnoreCase(style)) g.drawCircleFilled(cx, cy, Math.max(1, thick), col);
        else if ("circle".equalsIgnoreCase(style)) g.drawCircle(cx, cy, Math.max(2, sz + gap), thick, col);
        else if ("arrows".equalsIgnoreCase(style)) {
            int off = gap + 2;
            g.drawLine(cx - off - sz, cy + sz, cx - off, cy, thick, col);
            g.drawLine(cx - off - sz, cy - sz, cx - off, cy, thick, col);
            g.drawLine(cx + off + sz, cy + sz, cx + off, cy, thick, col);
            g.drawLine(cx + off + sz, cy - sz, cx + off, cy, thick, col);
        } else if ("x_cross".equalsIgnoreCase(style)) {
            int d1 = Math.max(1, gap), d2 = d1 + Math.max(2, sz);
            g.drawLine(cx - d2, cy - d2, cx - d1, cy - d1, thick, col);
            g.drawLine(cx + d1, cy - d1, cx + d2, cy - d2, thick, col);
            g.drawLine(cx - d2, cy + d2, cx - d1, cy + d1, thick, col);
            g.drawLine(cx + d1, cy + d1, cx + d2, cy + d2, thick, col);
        } else {
            if (!"t_shape".equalsIgnoreCase(style)) g.fill(cx - half, cy - gap - sz, cx - half + thick, cy - gap, col);
            g.fill(cx - half, cy + gap + 1, cx - half + thick, cy + gap + 1 + sz, col);
            g.fill(cx - gap - sz, cy - half, cx - gap, cy - half + thick, col);
            g.fill(cx + gap + 1, cy - half, cx + gap + 1 + sz, cy - half + thick, col);
        }
    }
}