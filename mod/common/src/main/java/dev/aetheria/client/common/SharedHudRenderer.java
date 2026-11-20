package dev.aetheria.client.common;

import java.text.SimpleDateFormat;
import java.util.*;

public final class SharedHudRenderer {
    public static final int XHAIR_GRID_SIZE = 15;
    public static final String DEFAULT_CUSTOM_PIXELS =
        "000000000000000000000000000000000000000000000" +
        "000000000000000000000010000000000000010000000" +
        "000000000000000000011000110000000000000000000" +
        "000000010000000000000010000000000000000000000" +
        "000000000000000000000000000000000000000000000";
    public static final String DEFAULT_TARGET_CUSTOM_PIXELS =
        "000000000000000000000000000000000000000000000" +
        "000000000000000000010010010000000001010100000" +
        "000000000000000000011010110000000000000000000" +
        "000001010100000000010010010000000000000000000" +
        "000000000000000000000000000000000000000000000";

    private static final SimpleDateFormat CLOCK_12 = new SimpleDateFormat("hh:mm a"), CLOCK_12S = new SimpleDateFormat("hh:mm:ss a");
    private static final SimpleDateFormat CLOCK_24 = new SimpleDateFormat("HH:mm"), CLOCK_24S = new SimpleDateFormat("HH:mm:ss");
    private static long lastClockSec = -1L, lastSysCacheMs = 0L;
    private static boolean lastClock24 = false, lastClockSecFlag = false;
    private static String cachedClockStr = "", cachedRamStr = "RAM: --", cachedCpuStr = "CPU: --", cachedGcStr = "GC: --";

    private SharedHudRenderer() {}

    public static void renderInGame(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientState.recordFrameSample(d.fps());
        ClientState.tickComboTimeout();
        if (d.hasWorld()) {
            ClientState.updatePlayerPos(d.x(), d.y(), d.z(), d.dimensionId(), d.worldKey());
            ClientState.sampleTelemetry(d.pingMs(), d.worldTime(), d.posX(), d.posY(), d.posZ(), d.yaw(), d.pitch(), d.health());
        }
        ClientConfig cfg = ClientConfig.get();
        renderLowHpVignette(g, d, cfg, sw, sh);
        if (!d.isF1Hidden()) {
            renderWaypointsInWorld(g, d, cfg, sw, sh);
            renderCrosshair(g, d, sw, sh);
            renderAppleSkinHungerOverlay(g, d, cfg, sw, sh);
        }

        boolean globalShadow = cfg.getBool("hud_shadow", true);
        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (!m.enabled || "hotbar".equals(m.id) || "low_hp_vignette".equals(m.id)) continue;
            if (m.hideInF1 && d.isF1Hidden()) continue;
            if (m.hideInChat && d.isChatOpen()) continue;
            if (m.hideInInventory && d.isInventoryOpen()) continue;
            if (m.hideInF3 && d.isF3Open()) continue;
            if ("togglesprint".equals(m.id) && cfg.getBool("togglesprint_hide_hud", false)) continue;
            if ("saturation".equals(m.id) && "appleskin".equalsIgnoreCase(cfg.getString("saturation_style", "appleskin"))) continue;
            renderModule(g, d, m, sw, sh, globalShadow && m.shadow, false);
        }
    }

    private static void renderWaypointsInWorld(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig cfg, int sw, int sh) {
        if (!d.hasWorld() || !cfg.getBool("wp_world_markers", true) || cfg.waypoints.isEmpty()) return;
        String style = cfg.getString("wp_style", "lunar").toLowerCase();
        boolean netherSync = cfg.getBool("wp_nether_sync", true), showDist = cfg.getBool("wp_show_dist", true);
        boolean beam = cfg.getBool("wp_beam_beacon", true), offscreen = cfg.getBool("wp_offscreen_arrows", true);

        double camX = d.camX(), camY = d.camY(), camZ = d.camZ();
        double yawRad = Math.toRadians(d.camYaw()), pitchRad = Math.toRadians(d.camPitch());
        double cosP = Math.cos(pitchRad), sinP = Math.sin(pitchRad), sinY = Math.sin(yawRad), cosY = Math.cos(yawRad);
        double fx = -sinY * cosP, fy = -sinP, fz = cosY * cosP, rx = -cosY, rz = -sinY, ux = -sinY * sinP, uy = cosP, uz = cosY * sinP;
        double effFov = Math.max(10.0, d.fovDegrees() / Math.max(1.0F, ClientState.getSmoothedZoomDivisor()));
        double tanHalf = Math.tan(Math.toRadians(effFov) * 0.5), halfH = sh * 0.5;
        String curDim = d.dimensionId();

        for (int i = 0; i < cfg.waypoints.size(); i++) {
            ClientConfig.Waypoint wp = cfg.waypoints.get(i);
            double[] pos = ClientState.resolveWaypointCoords(wp, curDim, netherSync);
            if (pos == null) continue;
            double dx = (pos[0] + 0.5) - camX, dy = (pos[1] + 1.1) - camY, dz = (pos[2] + 0.5) - camZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz), camFwd = dx * fx + dy * fy + dz * fz;
            double camRight = dx * rx + dz * rz, camUp = dx * ux + dy * uy + dz * uz;
            int col = ClientState.parseHexColor(wp.color, 0xFF22D3EE);
            String label = wp.name + (pos[3] == 1.0 ? " (OW)" : (pos[3] == 2.0 ? " (N)" : ""));
            String distStr = showDist ? (Math.round(dist) + "m") : "";

            if (camFwd > 0.35) {
                double sx = (sw * 0.5) + (camRight / (camFwd * tanHalf)) * halfH, sy = (sh * 0.5) - (camUp / (camFwd * tanHalf)) * halfH;
                if (sx >= -30 && sx <= sw + 30 && sy >= -30 && sy <= sh + 30) {
                    g.pushTransform((float) sx, (float) sy, 1.0F);
                    try {
                        if ("beacon".equals(style) || (beam && "lunar".equals(style))) {
                            int beamH = "beacon".equals(style) ? 140 : Math.max(22, Math.min(95, (int) Math.round(800.0 / Math.max(8.0, dist))));
                            g.fill(-1, -beamH, 1, -10, ClientState.withAlpha(col, "beacon".equals(style) ? 150 : 95));
                            g.fill(-2, -beamH + 8, 2, -10, ClientState.withAlpha(col, 40));
                        }
                        if ("dot".equals(style)) {
                            g.drawCircleFilled(0, 0, 5, ClientState.withAlpha(col, 70)); g.drawCircleFilled(0, 0, 3, col);
                            String txt = distStr.isEmpty() ? label : (label + " " + distStr); int tw = g.textWidth(txt) + 10;
                            g.drawGlassCard(-tw / 2, -18, tw, 13, 6, 0xA809090B, ClientState.withAlpha(col, 160));
                            g.text(txt, -tw / 2 + 5, -15, 0xFFF4F4F5, true);
                        } else if ("block".equals(style)) {
                            String txt = distStr.isEmpty() ? label : (label + " [" + distStr + "]"); int tw = g.textWidth(txt) + 12;
                            g.drawGlassCard(-tw / 2, -22, tw, 14, 5, 0xB809090B, col); g.text(txt, -tw / 2 + 6, -19, 0xFFF4F4F5, true);
                        } else {
                            String full = distStr.isEmpty() ? label : (label + "  " + distStr);
                            int dw = distStr.isEmpty() ? 0 : g.textWidth(distStr), pw = 18 + g.textWidth(full) + 8;
                            g.drawGlassCard(-pw / 2, -9, pw, 17, 6, 2, "solid", 0xD009090B, col, col);
                            g.drawRoundedRect(-pw / 2 + 3, -6, 11, 11, 3, col); g.fill(-pw / 2 + 7, -2, -pw / 2 + 10, 1, 0xFF09090B);
                            g.text(label, -pw / 2 + 18, -4, 0xFFF4F4F5, true);
                            if (!distStr.isEmpty()) g.text(distStr, pw / 2 - dw - 6, -4, col, false);
                        }
                    } finally { g.popTransform(); }
                    continue;
                }
            }
            if (offscreen) {
                double ang = Math.atan2(-camUp, camRight), edgeR = Math.min(sw, sh) * 0.43;
                int px = (int) Math.round(sw * 0.5 + Math.cos(ang) * edgeR), py = (int) Math.round(sh * 0.5 + Math.sin(ang) * edgeR);
                String arrow = (Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang))) ? (Math.cos(ang) > 0 ? ">" : "<") : (Math.sin(ang) > 0 ? "v" : "^");
                String tag = arrow + " " + wp.name + (distStr.isEmpty() ? "" : (" " + distStr)); int pw = g.textWidth(tag) + 14;
                int bx = Math.max(6, Math.min(sw - pw - 6, px - pw / 2)), by = Math.max(6, Math.min(sh - 18, py - 7));
                g.drawGlassCard(bx, by, pw, 14, 6, 0x9009090B, ClientState.withAlpha(col, 160));
                g.drawCircleFilled(bx + 6, by + 7, 2, col); g.text(tag, bx + 11, by + 3, 0xFFD4D4D8, false);
            }
        }
    }

    private static void renderAppleSkinHungerOverlay(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig cfg, int sw, int sh) {
        ClientConfig.HudModule m = cfg.hudModules.get("saturation");
        if (m == null || !m.enabled || !d.hasWorld() || !d.showSurvivalBars()) return;
        if (!"appleskin".equalsIgnoreCase(cfg.getString("saturation_style", "appleskin"))) return;
        float sat = d.saturation(); if (sat <= 0.01F) return;
        int alpha = cfg.getBool("saturation_glint", true) ? (175 + Math.round(65.0F * (0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 220.0)))) : 235;
        int gold = ClientState.withAlpha(0xFFFFD166, alpha);
        ClientConfig.HudModule hb = cfg.hudModules.get("hotbar");
        boolean customHb = hb != null && hb.enabled && (Math.abs(hb.x - 0.5) > 0.002 || Math.abs(hb.y - 1.0) > 0.002 || Math.abs(hb.scale - 1.0) > 0.01);
        int rightEdge = sw / 2 + 91, y = sh - 39;
        if (customHb) { int[] b = getModulePixelBounds(g, d, hb, sw, sh); g.pushTransform(b[0], b[1], (float) Math.max(0.25, Math.min(4.0, hb.scale))); rightEdge = 182; y = -17; }
        try {
            for (int i = 0; i < 10; i++) {
                float rem = sat - i * 2.0F; if (rem <= 0.0F) break;
                int sx = rightEdge - i * 8 - 9;
                if (rem >= 2.0F) g.drawBorder(sx, y, 9, 9, gold);
                else { g.fill(sx + 4, y, sx + 9, y + 1, gold); g.fill(sx + 4, y + 8, sx + 9, y + 9, gold); g.fill(sx + 8, y + 1, sx + 9, y + 8, gold); }
            }
        } finally { if (customHb) g.popTransform(); }
    }

    private static void renderLowHpVignette(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig cfg, int sw, int sh) {
        ClientConfig.HudModule m = cfg.hudModules.get("low_hp_vignette");
        if (m == null || !m.enabled || !d.hasWorld() || d.health() > 7.0F) return;
        int a = Math.max(10, Math.min(110, Math.round((30 + (0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 180.0)) * 45) * (float) m.opacity)));
        int col = ClientState.withAlpha(0xFFEF4444, a);
        g.fill(0, 0, sw, 6, col); g.fill(0, sh - 6, sw, sh, col); g.fill(0, 6, 6, sh - 6, col); g.fill(sw - 6, 6, sw, sh - 6, col);
    }

    public static int[] getBaseBoxSize(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m) { return getBaseBoxSize(g, d, m, false); }
    public static int[] getBaseBoxSize(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, boolean editorPreview) {
        int px = Math.max(1, Math.min(24, m.paddingX)), py = Math.max(1, Math.min(24, m.paddingY)), w, h;
        if ("hotbar".equals(m.id)) return new int[]{182, 22};
        if ("scoreboard".equals(m.id)) {
            String title = d.scoreboardTitle(); String[] lines = d.scoreboardLines();
            if ((lines == null || lines.length == 0) && !editorPreview) return new int[]{0, 0};
            if (title == null || title.isEmpty()) title = "Aetheria";
            if (lines == null || lines.length == 0) lines = new String[]{"Online\t42", "Kills\t12", "Ping\t24ms"};
            boolean hideNum = ClientConfig.get().getBool("sb_hide_numbers", false);
            int maxW = g.textWidth(title) + 12;
            for (int i = 0; i < lines.length; i++) {
                String r = lines[i] == null ? "" : lines[i]; int tab = r.indexOf('\t');
                String left = tab >= 0 ? r.substring(0, tab) : r, right = tab >= 0 ? r.substring(tab + 1) : "";
                int rw = g.textWidth(left) + (!hideNum && !right.isEmpty() ? g.textWidth(right) + 14 : 0);
                if (rw > maxW) maxW = rw;
            }
            w = Math.max(78, maxW + px * 2); h = 14 + lines.length * 10 + py * 2;
        } else if ("keystrokes".equals(m.id)) {
            ClientConfig cfg = ClientConfig.get();
            w = 64 + px * 2; h = 38 + (cfg.getBool("keystrokes_cps", true) ? 16 : 0) + (cfg.getBool("keystrokes_space", true) ? 14 : 0) + (cfg.getBool("keystrokes_mods", false) ? 14 : 0) + py * 2;
        } else if ("armor".equals(m.id) && g.supportsItemIcons() && !ClientConfig.get().getBool("armor_text_mode", false)) {
            String[] slots = d.armorSlotReadouts(); boolean horiz = ClientConfig.get().getBool("armor_horizontal", false), showTxt = ClientConfig.get().getBool("armor_show_text", true);
            int count = 0, maxTxtW = 0;
            for (int i = 0; i < slots.length; i++) if (slots[i] != null) { count++; if (showTxt && !slots[i].isEmpty()) maxTxtW = Math.max(maxTxtW, g.textWidth(slots[i])); }
            if (count == 0) { if (!editorPreview) return new int[]{0, 0}; w = g.textWidth("Armor HUD") + px * 2; h = 10 + py * 2; }
            else if (horiz) { int cellW = showTxt && maxTxtW > 0 ? Math.max(18, maxTxtW + 4) : 18; w = count * cellW - 2 + px * 2; h = (showTxt && maxTxtW > 0 ? 25 : 16) + py * 2; }
            else { w = 16 + (showTxt && maxTxtW > 0 ? (4 + maxTxtW) : 0) + px * 2; h = count * 17 - 1 + py * 2; }
        } else if ("potions".equals(m.id)) {
            String[] pots = d.activePotions();
            if (!editorPreview && ClientConfig.get().getBool("potions_auto_hide", true) && (pots == null || pots.length == 0 || "No Potions".equals(pots[0]))) return new int[]{0, 0};
            if (pots == null || pots.length == 0) pots = new String[]{"Potions: Empty"};
            boolean showIcon = ClientConfig.get().getBool("potions_show_icon", true);
            int maxW = 60; for (String p : pots) maxW = Math.max(maxW, textWidthSpaced(g, p, m.letterSpacing) + (showIcon ? 12 : 0));
            w = maxW + px * 2; h = (pots.length - 1) * 14 + 9 + py * 2;
        } else if ("pearl_cd".equals(m.id) && !editorPreview && ClientConfig.get().getBool("pearl_auto_hide", true) && ClientState.getPearlCooldownSec() <= 0.0) {
            return new int[]{0, 0};
        } else if ("shield_cd".equals(m.id) && !editorPreview && ClientConfig.get().getBool("shield_auto_hide", true) && ClientState.getShieldCooldownSec() <= 0.0) {
            return new int[]{0, 0};
        } else if ("target_hud".equals(m.id)) {
            if (!editorPreview && (d.targetName() == null || d.targetName().isEmpty() || d.targetHealth() <= 0.0F)) return new int[]{0, 0};
            w = 132; h = 38;
        } else if ("ping".equals(m.id)) { w = textWidthSpaced(g, getFormattedLines(d, m)[0], m.letterSpacing) + 10 + px * 2; h = 7 + py * 2; }
        else if ("frametime_graph".equals(m.id) || "ping_graph".equals(m.id) || "cps_graph".equals(m.id)) { w = 110; h = 42; }
        else if ("mouse_visualizer".equals(m.id)) { w = 54; h = 54; }
        else if ("minimap".equals(m.id)) { w = 66; h = 66; }
        else if ("paperdoll".equals(m.id)) { w = 56; h = 76; }
        else if ("ram_usage".equals(m.id) || "attack_charge".equals(m.id) || ("saturation".equals(m.id) && "bar".equalsIgnoreCase(ClientConfig.get().getString("saturation_style", "appleskin")))) {
            String[] lines = getFormattedLines(d, m); int maxW = 70; for (String ln : lines) maxW = Math.max(maxW, textWidthSpaced(g, ln, m.letterSpacing));
            w = maxW + px * 2; h = (lines.length - 1) * 10 + 15 + py * 2;
        } else {
            String[] lines = getFormattedLines(d, m); int maxW = 0; for (String ln : lines) maxW = Math.max(maxW, textWidthSpaced(g, ln, m.letterSpacing));
            w = Math.max(24, maxW + px * 2); h = Math.max(11, (lines.length - 1) * 10 + 7 + py * 2);
        }
        if (m.width > 0) w = Math.max(24, m.width);
        if (m.height > 0) h = Math.max(12, m.height);
        return new int[]{w, h};
    }

    public static int[] getModulePixelBounds(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh) { return getModulePixelBounds(g, d, m, sw, sh, false); }
    public static int[] getModulePixelBounds(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh, boolean editorPreview) {
        int[] base = getBaseBoxSize(g, d, m, editorPreview);
        if (base[0] <= 0 || base[1] <= 0) return new int[]{0, 0, 0, 0};
        double sc = Math.max(0.25, Math.min(4.0, m.scale));
        int scaledW = (int) Math.round(base[0] * sc), scaledH = (int) Math.round(base[1] * sc);
        int ax = (int) Math.round(m.x * sw), ay = (int) Math.round(m.y * sh);
        String anc = m.anchor == null ? "top-left" : m.anchor;
        if (anc.endsWith("-center") || "center".equals(anc)) ax -= scaledW / 2; else if (anc.endsWith("-right")) ax -= scaledW;
        if (anc.startsWith("mid-") || "center".equals(anc)) ay -= scaledH / 2; else if (anc.startsWith("bottom-")) ay -= scaledH;
        return new int[]{ax + m.margin, ay + m.margin, scaledW, scaledH};
    }

    public static void renderModule(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh, boolean shadow, boolean editorPreview) {
        int[] base = getBaseBoxSize(g, d, m, editorPreview);
        if (base[0] <= 0 || base[1] <= 0) return;
        int[] bounds = getModulePixelBounds(g, d, m, sw, sh, editorPreview);
        int boxW = base[0], boxH = base[1]; float scale = (float) Math.max(0.25, Math.min(4.0, m.scale));
        int baseTextCol = m.chroma ? ClientState.getChromaColor(m.chromaSpeed, m.chromaSat, m.chromaAngle, m.x, m.y) : ClientState.parseHexColor(m.color, 0xFFFFFFFF);
        int textCol = ClientState.scaleAlpha(baseTextCol, m.textOpacity);
        double gRad = ClientConfig.get().getDouble("global_hud_rounding", -1.0), gOpac = ClientConfig.get().getDouble("global_hud_opacity", -1.0);
        int rad = gRad >= 0 ? (int) Math.round(gRad) : Math.max(0, Math.min(30, m.borderRadius));
        int px = Math.max(1, Math.min(24, m.paddingX)), py = Math.max(1, Math.min(24, m.paddingY));

        g.pushTransform(bounds[0], bounds[1], scale);
        try {
            if ("hotbar".equals(m.id)) {
                if (editorPreview) {
                    g.drawGlassCard(0, 0, boxW, boxH, 5, 2, "solid", 0xB809090B, ClientState.getAccentColor(), ClientState.getAccentColor());
                    for (int s = 0; s < 9; s++) g.drawBorder(2 + s * 20, 2, 18, 18, s == 0 ? 0xFFFFFFFF : 0x44FFFFFF);
                    g.text("HOTBAR HUD", 62, 7, 0xFFF4F4F5, true);
                }
                return;
            }
            int bgCol = m.bg ? ClientState.scaleAlpha(ClientState.parseHexColor(m.bgColor, 0xFF09090B), gOpac >= 0 ? gOpac : m.opacity) : 0;
            int brdCol = m.border ? (m.chroma ? baseTextCol : ClientState.parseHexColor(m.borderColor, 0xFF27272A)) : 0;
            int brdCol2 = m.border ? (m.chroma ? ClientState.getChromaColor(m.chromaSpeed, m.chromaSat, m.chromaAngle, Math.min(1.0, m.x + 0.15), m.y) : ClientState.parseHexColor(m.borderColor2, brdCol)) : 0;
            if (m.bg || m.border) g.drawGlassCard(0, 0, boxW, boxH, rad, m.border ? Math.max(1, m.borderWidth) : 0, m.borderStyle, bgCol, brdCol, brdCol2);

            if ("scoreboard".equals(m.id)) renderScoreboard(g, d, px, py, boxW, textCol, shadow);
            else if ("keystrokes".equals(m.id)) renderKeystrokes(g, d, px, py, boxW, textCol, shadow, rad);
            else if ("armor".equals(m.id) && g.supportsItemIcons() && !ClientConfig.get().getBool("armor_text_mode", false)) renderArmorIcons(g, d, px, py, textCol, shadow, editorPreview);
            else if ("potions".equals(m.id)) renderPotionHud(g, d, px, py, boxW, textCol, shadow);
            else if ("ping".equals(m.id)) renderPingHud(g, d, m, px, py, textCol, shadow);
            else if ("saturation".equals(m.id) && editorPreview && "appleskin".equalsIgnoreCase(ClientConfig.get().getString("saturation_style", "appleskin"))) g.text("AppleSkin (On Hunger)", px, py, 0xFFFFD166, shadow);
            else if ("paperdoll".equals(m.id)) { /* screen-space 3D model drawn after popTransform */ }
            else if ("frametime_graph".equals(m.id)) { float[] hist = ClientState.getFrametimeHistory(); drawTextSpaced(g, String.format("FT %.1fms", Float.valueOf(hist.length > 0 ? hist[hist.length - 1] : 0.0F)), px, py, textCol, shadow, m.letterSpacing); g.drawSparkline(px, py + 11, boxW - px * 2, boxH - py * 2 - 11, hist, hist.length, 40.0F, textCol, ClientState.withAlpha(textCol, 40)); }
            else if ("ping_graph".equals(m.id)) { float[] hist = ClientState.getPingHistory(); drawTextSpaced(g, "Ping " + d.pingMs() + "ms", px, py, textCol, shadow, m.letterSpacing); g.drawSparkline(px, py + 11, boxW - px * 2, boxH - py * 2 - 11, hist, hist.length, 250.0F, textCol, ClientState.withAlpha(textCol, 40)); }
            else if ("cps_graph".equals(m.id)) { float[] hist = ClientState.getCpsHistory(); drawTextSpaced(g, "CPS " + (ClientState.getLeftCps() + ClientState.getRightCps()), px, py, textCol, shadow, m.letterSpacing); g.drawSparkline(px, py + 11, boxW - px * 2, boxH - py * 2 - 11, hist, hist.length, 20.0F, textCol, ClientState.withAlpha(textCol, 40)); }
            else if ("mouse_visualizer".equals(m.id)) renderMouseVisualizer(g, boxW, boxH, textCol);
            else if ("minimap".equals(m.id)) renderMinimap(g, d, boxW, boxH, textCol);
            else if ("target_hud".equals(m.id)) renderTargetHud(g, d, px, py, boxW, boxH, textCol, shadow, editorPreview);
            else if ("ram_usage".equals(m.id) || "attack_charge".equals(m.id) || ("saturation".equals(m.id) && "bar".equalsIgnoreCase(ClientConfig.get().getString("saturation_style", "appleskin")))) {
                String[] lines = getFormattedLines(d, m); for (int i = 0; i < lines.length; i++) drawTextSpaced(g, lines[i], px, py + i * 10, textCol, shadow, m.letterSpacing);
                g.drawProgressBar(px, boxH - py - 4, boxW - px * 2, 4, 2, getBarPercent(d, m.id), 0x33FFFFFF, "saturation".equals(m.id) ? 0xFFFFD166 : textCol);
            } else {
                String[] lines = getFormattedLines(d, m);
                for (int i = 0; i < lines.length; i++) drawTextSpaced(g, lines[i], (boxW - textWidthSpaced(g, lines[i], m.letterSpacing)) / 2, py + i * 10, textCol, shadow, m.letterSpacing);
            }
        } finally { g.popTransform(); }
        if ("paperdoll".equals(m.id)) g.drawPaperdoll(bounds[0] + 2, bounds[1] + 2, bounds[0] + bounds[2] - 2, bounds[1] + bounds[3] - 2, Math.max(18, (int) Math.round(Math.min(bounds[2], bounds[3]) * 0.44)), 0.0F, 0.0F);
    }

    private static void renderScoreboard(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int boxW, int textCol, boolean shadow) {
        String title = d.scoreboardTitle(); String[] lines = d.scoreboardLines();
        if (title == null || title.isEmpty()) title = "Aetheria";
        if (lines == null || lines.length == 0) lines = new String[]{"Online\t42", "Kills\t12", "Ping\t24ms"};
        boolean hideNum = ClientConfig.get().getBool("sb_hide_numbers", false);
        g.text(title, Math.max(px, (boxW - g.textWidth(title)) / 2), py, textCol, shadow);
        g.fill(px, py + 10, boxW - px, py + 11, 0x33FFFFFF);
        int y = py + 14;
        for (int i = 0; i < lines.length; i++) {
            String row = lines[i] == null ? "" : lines[i]; int tab = row.indexOf('\t');
            String left = tab >= 0 ? row.substring(0, tab) : row, right = tab >= 0 ? row.substring(tab + 1) : "";
            g.text(left, px, y, textCol, shadow);
            if (!hideNum && !right.isEmpty()) g.text(right, boxW - px - g.textWidth(right), y, 0xFFFF5555, shadow);
            y += 10;
        }
    }

    private static void renderPingHud(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int px, int py, int textCol, boolean shadow) {
        int ms = d.pingMs(); boolean sp = d.isSingleplayer() && ms == 0;
        g.drawCircleFilled(px + 3, py + 3, 3, sp ? 0xFF10B981 : (ms < 80 ? 0xFF10B981 : (ms < 160 ? 0xFFF59E0B : 0xFFEF4444)));
        drawTextSpaced(g, getFormattedLines(d, m)[0], px + 10, py, textCol, shadow, m.letterSpacing);
    }

    private static void renderArmorIcons(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int textCol, boolean shadow, boolean editorPreview) {
        String[] slots = d.armorSlotReadouts(); boolean horiz = ClientConfig.get().getBool("armor_horizontal", false), showTxt = ClientConfig.get().getBool("armor_show_text", true);
        int maxTxtW = 0, count = 0; for (int i = 0; i < slots.length; i++) if (slots[i] != null) { count++; if (showTxt && !slots[i].isEmpty()) maxTxtW = Math.max(maxTxtW, g.textWidth(slots[i])); }
        if (count == 0) { if (editorPreview) g.text("Armor HUD", px, py, textCol, shadow); return; }
        int idx = 0;
        if (horiz) {
            int cellW = showTxt && maxTxtW > 0 ? Math.max(18, maxTxtW + 4) : 18;
            for (int i = 0; i < slots.length; i++) if (slots[i] != null) {
                g.drawArmorSlot(i, px + idx * cellW + (cellW - 16) / 2, py);
                if (showTxt && !slots[i].isEmpty()) g.text(slots[i], px + idx * cellW + (cellW - g.textWidth(slots[i])) / 2, py + 18, textCol, shadow);
                idx++;
            }
        } else {
            for (int i = 0; i < slots.length; i++) if (slots[i] != null) {
                int iy = py + idx * 17; g.drawArmorSlot(i, px, iy); if (showTxt && !slots[i].isEmpty()) g.text(slots[i], px + 20, iy + 5, textCol, shadow); idx++;
            }
        }
    }

    private static float getBarPercent(UiBridge.HudData d, String id) {
        if ("ram_usage".equals(id)) { Runtime rt = Runtime.getRuntime(); return (float) (rt.totalMemory() - rt.freeMemory()) / (float) Math.max(1L, rt.maxMemory()); }
        if ("saturation".equals(id)) return Math.max(0.0F, Math.min(1.0F, d.saturation() / 20.0F));
        return Math.max(0.0F, Math.min(1.0F, d.attackCooldownPct()));
    }

    private static void renderPotionHud(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int boxW, int textCol, boolean shadow) {
        String[] pots = d.activePotions();
        if (pots == null || pots.length == 0 || "No Potions".equals(pots[0])) { g.text("Potions: Empty", px, py + 1, ClientState.UI_TEXT_MUTED, shadow); return; }
        ClientConfig cfg = ClientConfig.get(); boolean blink = cfg.getBool("potions_blink", true), showIcon = cfg.getBool("potions_show_icon", true), cards = "cards".equalsIgnoreCase(cfg.getString("potions_style", "vanilla"));
        for (int i = 0; i < pots.length; i++) {
            String pot = pots[i]; int lineY = py + i * 14; boolean flash = blink && (pot.contains(" 0:0") || pot.contains(" 0:00")) && ((System.currentTimeMillis() / 250) % 2 == 0);
            if (cards) g.drawRoundedRect(px - 2, lineY - 1, boxW - px * 2 + 4, 12, 4, 0x28FFFFFF);
            if (showIcon) { g.drawCircleFilled(px + 4, lineY + 4, 4, flash ? 0xFFEF4444 : 0xFF3B82F6); g.drawCircle(px + 4, lineY + 4, 4, 1, 0xFFFFFFFF); }
            g.text(pot, showIcon ? px + 12 : px, lineY + 1, flash ? 0xFFEF4444 : textCol, shadow);
        }
    }

    private static void renderKeystrokes(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int boxW, int textCol, boolean shadow, int parentRad) {
        ClientConfig cfg = ClientConfig.get(); boolean showCps = cfg.getBool("keystrokes_cps", true), showSpace = cfg.getBool("keystrokes_space", true), showMods = cfg.getBool("keystrokes_mods", false);
        int keyRad = Math.min(6, parentRad / 2), kw = 20, kh = 18, gap = 2, startX = (boxW - (kw * 3 + gap * 2)) / 2, y = py;
        drawKeyBox(g, startX + kw + gap, y, kw, kh, keyRad, "W", d.isMoveW(), textCol, shadow); y += kh + gap;
        drawKeyBox(g, startX, y, kw, kh, keyRad, "A", d.isMoveA(), textCol, shadow); drawKeyBox(g, startX + kw + gap, y, kw, kh, keyRad, "S", d.isMoveS(), textCol, shadow); drawKeyBox(g, startX + (kw + gap) * 2, y, kw, kh, keyRad, "D", d.isMoveD(), textCol, shadow); y += kh + gap;
        int fullW = kw * 3 + gap * 2;
        if (showCps) { int mw = (fullW - gap) / 2; drawKeyBox(g, startX, y, mw, 14, keyRad, "L:" + ClientState.getLeftCps(), ClientState.isLeftDown(), textCol, shadow); drawKeyBox(g, startX + mw + gap, y, fullW - mw - gap, 14, keyRad, "R:" + ClientState.getRightCps(), ClientState.isRightDown(), textCol, shadow); y += 16; }
        if (showSpace) { drawKeyBox(g, startX, y, fullW, 12, keyRad, "---", d.isJumpDown(), textCol, shadow); y += 14; }
        if (showMods) { int mw = (fullW - gap) / 2; drawKeyBox(g, startX, y, mw, 12, keyRad, "SPR", d.isSprinting(), textCol, shadow); drawKeyBox(g, startX + mw + gap, y, fullW - mw - gap, 12, keyRad, "SNK", d.isSneaking(), textCol, shadow); }
    }

    private static void drawKeyBox(UiBridge.UiDraw g, int x, int y, int w, int h, int rad, String label, boolean pressed, int textCol, boolean shadow) {
        g.drawGlassCard(x, y, w, h, rad, pressed ? ClientState.withAlpha(textCol, 195) : 0x44000000, pressed ? textCol : 0x33FFFFFF);
        g.text(label, x + (w - g.textWidth(label)) / 2, y + (h - 7) / 2, pressed ? 0xFF09090B : textCol, shadow && !pressed);
    }

    private static void renderMouseVisualizer(UiBridge.UiDraw g, int w, int h, int textCol) {
        int cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 6;
        g.drawCircle(cx, cy, r, 1, 0x44FFFFFF); g.fill(cx - r, cy, cx + r, cy + 1, 0x22FFFFFF); g.fill(cx, cy - r, cx + 1, cy + r, 0x22FFFFFF);
        g.drawCircleFilled(cx + Math.round(ClientState.getMouseVelX() * (r - 3)), cy + Math.round(ClientState.getMouseVelY() * (r - 3)), 3, textCol);
    }

    private static void renderMinimap(UiBridge.UiDraw g, UiBridge.HudData d, int w, int h, int textCol) {
        int cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 6;
        g.drawCircle(cx, cy, r, 1, 0x44FFFFFF); g.drawCircle(cx, cy, Math.max(4, r / 2), 1, 0x22FFFFFF);
        double rad = Math.toRadians(-d.yaw() - 90.0);
        g.text("N", cx + (int) Math.round(Math.cos(rad) * (r - 2)) - 2, cy + (int) Math.round(Math.sin(rad) * (r - 2)) - 4, 0xFFEF4444, false);
        int[] radar = d.nearbyEntityRadar();
        if (radar != null) for (int i = 0; i + 1 < radar.length; i += 2) g.drawCircleFilled(cx + Math.max(-r + 3, Math.min(r - 3, radar[i])), cy + Math.max(-r + 3, Math.min(r - 3, radar[i + 1])), 2, 0xFF22D3EE);
        ClientConfig cfg = ClientConfig.get(); double yRad = Math.toRadians(d.yaw()), cosY = Math.cos(yRad), sinY = Math.sin(yRad); boolean sync = cfg.getBool("wp_nether_sync", true);
        for (int i = 0; i < cfg.waypoints.size(); i++) {
            double[] pos = ClientState.resolveWaypointCoords(cfg.waypoints.get(i), d.dimensionId(), sync); if (pos == null) continue;
            double dx = pos[0] - d.posX(), dz = pos[2] - d.posZ();
            int rx = (int) Math.round(-(dx * cosY + dz * sinY) * 0.55), rz = (int) Math.round((-dx * sinY + dz * cosY) * 0.55);
            double len = Math.hypot(rx, rz); if (len > r - 3) { rx = (int) Math.round(rx * (r - 3) / len); rz = (int) Math.round(rz * (r - 3) / len); }
            g.drawCircleFilled(cx + rx, cy + rz, 2, ClientState.parseHexColor(cfg.waypoints.get(i).color, 0xFF22D3EE));
        }
        g.drawCircleFilled(cx, cy, 2, textCol);
    }

    private static void renderTargetHud(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int boxW, int boxH, int textCol, boolean shadow, boolean editorPreview) {
        String name = (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetName() : (editorPreview ? "Player" : "");
        float hp = (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetHealth() : 16.0F, maxHp = Math.max(1.0F, (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetMaxHealth() : 20.0F);
        double dist = (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetDistance() : 3.2;
        g.text(name, px, py, textCol, shadow); g.text(String.format("%.1f HP  %.1fm", Float.valueOf(hp), Double.valueOf(dist)), px, py + 10, ClientState.UI_TEXT_MUTED, shadow);
        float pct = Math.max(0.0F, Math.min(1.0F, hp / maxHp));
        g.drawProgressBar(px, boxH - py - 5, boxW - px * 2, 5, 2, pct, 0x33FFFFFF, pct > 0.5F ? 0xFF10B981 : (pct > 0.25F ? 0xFFF59E0B : 0xFFEF4444));
    }

    private static void renderCrosshair(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get(); String style = cfg.getString("pvp_xhair_style", "classic"); if ("vanilla".equalsIgnoreCase(style)) return;
        int size = Math.max(1, (int) Math.round(cfg.getDouble("pvp_xhair_size", 6.0))), gap = Math.max(0, (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2.0))), thick = Math.max(1, Math.min(4, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1.0))));
        int col = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF); boolean useTargetCustom = false;
        if (cfg.getBool("pvp_xhair_target", true)) {
            double range = Math.max(1.0, cfg.getDouble("pvp_xhair_target_range", 12.0));
            if (cfg.getBool("pvp_xhair_target_players_only", true) ? d.targetPlayerInView(range) : d.targetEntityInView(range)) {
                col = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);
                String tStyle = cfg.getString("pvp_xhair_target_style", "same");
                if ("custom".equalsIgnoreCase(tStyle)) { style = "custom"; useTargetCustom = true; } else if (!"same".equalsIgnoreCase(tStyle)) style = tStyle;
            }
        }
        int cx = sw / 2, cy = sh / 2;
        drawCrosshairShape(g, cx, cy, style, size, gap, thick, col, useTargetCustom);
        String atkMode = cfg.getString("pvp_xhair_atk_ind", "bar");
        if (!"none".equalsIgnoreCase(atkMode) && d.hasWorld()) {
            float atkPct = Math.max(0.0F, Math.min(1.0F, d.attackCooldownPct()));
            if (cfg.getBool("pvp_xhair_atk_always", false) || atkPct < 0.99F) {
                int atkCol = ClientState.parseHexColor(cfg.getString("pvp_xhair_atk_color", "#ffffff"), 0xFFFFFFFF), ay = cy + gap + size + 5;
                if ("dot".equalsIgnoreCase(atkMode)) g.drawCircleFilled(cx, ay + 2, Math.max(1, Math.round(atkPct * 3.0F)), atkPct >= 0.99F ? 0xFF10B981 : atkCol);
                else g.drawProgressBar(cx - 8, ay, 16, 2, 1, atkPct, 0x55000000, atkPct >= 0.99F ? 0xFF10B981 : atkCol);
            }
        }
    }

    public static void drawCrosshairShape(UiBridge.UiDraw g, int cx, int cy, String style, int size, int gap, int thick, int col) { drawCrosshairShape(g, cx, cy, style, size, gap, thick, col, false); }
    public static void drawCrosshairShape(UiBridge.UiDraw g, int cx, int cy, String style, int size, int gap, int thick, int col, boolean useTargetCustom) {
        if ("vanilla".equalsIgnoreCase(style)) return;
        if ("custom".equalsIgnoreCase(style)) {
            ClientConfig cfg = ClientConfig.get();
            drawCustomPixelCrosshair(g, cx, cy, cfg.getString(useTargetCustom ? "pvp_xhair_target_custom_pixels" : "pvp_xhair_custom_pixels", useTargetCustom ? DEFAULT_TARGET_CUSTOM_PIXELS : DEFAULT_CUSTOM_PIXELS), Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_pixel_scale", 1.0)))), col);
            return;
        }
        size = Math.max(1, size); gap = Math.max(0, gap); thick = Math.max(1, Math.min(4, thick)); int halfT = thick / 2;
        if ("dot".equalsIgnoreCase(style)) g.fill(cx - halfT - 1, cy - halfT - 1, cx - halfT + thick + 1, cy - halfT + thick + 1, col);
        else if ("circle".equalsIgnoreCase(style)) g.drawCircle(cx, cy, Math.max(2, size), thick, col);
        else if ("t_shape".equalsIgnoreCase(style)) { g.fill(cx - gap - size, cy - halfT, cx - gap, cy - halfT + thick, col); g.fill(cx + gap + thick, cy - halfT, cx + gap + thick + size, cy - halfT + thick, col); g.fill(cx - halfT, cy + gap + thick, cx - halfT + thick, cy + gap + thick + size, col); }
        else if ("x_cross".equalsIgnoreCase(style)) { g.drawLine(cx - gap - size, cy - gap - size, cx - gap, cy - gap, thick, col); g.drawLine(cx + gap + 1, cy - gap - size, cx + gap + 1 + size, cy - gap, thick, col); g.drawLine(cx - gap - size, cy + gap + 1 + size, cx - gap, cy + gap + 1, thick, col); g.drawLine(cx + gap + 1, cy + gap + 1 + size, cx + gap + 1 + size, cy + gap + 1, thick, col); }
        else if ("arrows".equalsIgnoreCase(style) || "chevron".equalsIgnoreCase(style)) { int s = Math.max(2, size / 2 + 1); g.drawLine(cx - gap - s, cy - s, cx - gap, cy, thick, col); g.drawLine(cx - gap - s, cy + s, cx - gap, cy, thick, col); g.drawLine(cx + gap + s, cy - s, cx + gap, cy, thick, col); g.drawLine(cx + gap + s, cy + s, cx + gap, cy, thick, col); }
        else { g.fill(cx - gap - size, cy - halfT, cx - gap, cy - halfT + thick, col); g.fill(cx + gap + thick, cy - halfT, cx + gap + thick + size, cy - halfT + thick, col); g.fill(cx - halfT, cy - gap - size, cx - halfT + thick, cy - gap, col); g.fill(cx - halfT, cy + gap + thick, cx - halfT + thick, cy + gap + thick + size, col); }
    }

    public static void drawCustomPixelCrosshair(UiBridge.UiDraw g, int cx, int cy, String pixels, int scale, int col) {
        if (pixels == null || pixels.isEmpty()) return;
        int n = XHAIR_GRID_SIZE, half = n / 2, sc = Math.max(1, Math.min(3, scale)), off = sc / 2, len = Math.min(pixels.length(), n * n);
        for (int idx = 0; idx < len; idx++) if (pixels.charAt(idx) == '1') g.fill(cx + ((idx % n) - half) * sc - off, cy + ((idx / n) - half) * sc - off, cx + ((idx % n) - half) * sc - off + sc, cy + ((idx / n) - half) * sc - off + sc, col);
    }

    private static void refreshSysStringsIfNeeded(boolean throttle) {
        long now = System.currentTimeMillis();
        if (throttle && now - lastSysCacheMs < 500L) return;
        lastSysCacheMs = now;
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L), maxMb = Math.max(1L, rt.maxMemory() / (1024L * 1024L));
        cachedRamStr = "RAM: " + usedMb + "/" + maxMb + "MB (" + Math.round((usedMb * 100.0) / maxMb) + "%)";
        cachedCpuStr = "CPU: " + rt.availableProcessors() + " Cores | " + Thread.activeCount() + " Thr";
        cachedGcStr = ClientState.getGcSummary();
    }

    private static String[] getFormattedLines(UiBridge.HudData d, ClientConfig.HudModule m) {
        ClientConfig cfg = ClientConfig.get(); String id = m.id; String[] raw;
        boolean throttle = cfg.getBool("perf_hud_throttle", true);
        if ("fps".equals(id)) { String l1 = (cfg.getBool("fps_show_label", true) ? "FPS: " : "") + d.fps(); raw = cfg.getBool("fps_detailed", false) ? new String[]{l1, "Min " + ClientState.getMinFps() + " | 1% " + ClientState.getOnePercentLowFps()} : new String[]{l1}; }
        else if ("armor".equals(id)) { String arm = d.armorStatus(); raw = cfg.getBool("armor_text_mode", false) ? new String[]{arm} : ((arm == null || arm.isEmpty()) ? new String[]{"Armor: None"} : arm.split(" \\| ")); }
        else if ("potions".equals(id)) { String[] pots = d.activePotions(); raw = (pots == null || pots.length == 0) ? new String[]{"No Potions"} : pots; }
        else if ("ram_usage".equals(id)) { refreshSysStringsIfNeeded(throttle); raw = new String[]{cachedRamStr}; }
        else if ("cpu_load".equals(id)) { refreshSysStringsIfNeeded(throttle); raw = new String[]{cachedCpuStr}; }
        else if ("gc_monitor".equals(id)) { refreshSysStringsIfNeeded(throttle); raw = new String[]{cachedGcStr}; }
        else if ("ping".equals(id)) raw = new String[]{(d.isSingleplayer() && d.pingMs() == 0) ? "Ping: Local" : ("Ping: " + d.pingMs() + "ms")};
        else if ("server_tps".equals(id)) raw = new String[]{String.format("TPS: %.1f (%.0fms)", Double.valueOf(ClientState.getEstimatedTps()), Double.valueOf(ClientState.getEstimatedTickMs()))};
        else if ("packet_counter".equals(id)) raw = new String[]{"Tick: " + (d.worldTime() % 1000L) + " | " + String.format("%.1f", Double.valueOf(ClientState.getEstimatedTps())) + "/s"};
        else if ("session".equals(id)) raw = new String[]{"Session: " + formatDuration((System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L)};
        else if ("total_playtime".equals(id)) raw = new String[]{"Playtime: " + formatDuration((System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L)};
        else if ("clock".equals(id)) raw = new String[]{getClockString(cfg.getBool("clock_24h", false), cfg.getBool("clock_seconds", false))};
        else if ("stopwatch".equals(id)) raw = new String[]{"SW: " + ClientState.getStopwatchText()};
        else if ("countdown".equals(id)) raw = new String[]{ClientState.getCountdownText()};
        else if ("chunk_updates".equals(id)) raw = new String[]{"Chunks: " + (d.x() >> 4) + "," + (d.z() >> 4)};
        else if ("entity_counter".equals(id)) raw = new String[]{"Entities: " + d.renderedEntities() + "/" + d.totalEntities()};
        else if ("cps".equals(id)) raw = new String[]{"CPS: " + ClientState.getLeftCps() + " | " + ClientState.getRightCps()};
        else if ("combo".equals(id)) { int c = ClientState.getComboCount(); raw = new String[]{c > 0 ? (c + " Combo") : "No Combo"}; }
        else if ("reach".equals(id)) raw = new String[]{String.format("Reach: %.2f blocks", Double.valueOf(ClientState.getLastReach()))};
        else if ("hit_accuracy".equals(id)) raw = new String[]{"Accuracy: " + ClientState.getHitAccuracyPct() + "%"};
        else if ("sprint_reset".equals(id)) raw = new String[]{d.isSprinting() ? "Sprint: Reset Ready" : "Sprint: Idle"};
        else if ("durability".equals(id)) raw = new String[]{d.heldToolDurability(), d.offhandDurability()};
        else if ("potion_count".equals(id)) raw = new String[]{"Potions: " + d.countItem("potion")};
        else if ("heal_count".equals(id)) raw = new String[]{"GApples: " + d.countItem("apple")};
        else if ("arrow_count".equals(id)) raw = new String[]{"Arrows: " + d.countItem("arrow")};
        else if ("totem_count".equals(id)) raw = new String[]{"Totems: " + d.countItem("totem")};
        else if ("kill_counter".equals(id)) raw = new String[]{"K: " + ClientState.getKills() + " | D: " + ClientState.getDeaths()};
        else if ("pearl_cd".equals(id)) { double sec = ClientState.getPearlCooldownSec(); raw = new String[]{sec > 0.0 ? String.format("Pearl: %.1fs", Double.valueOf(sec)) : "Pearl: Ready"}; }
        else if ("shield_cd".equals(id)) { double sec = ClientState.getShieldCooldownSec(); raw = new String[]{sec > 0.0 ? String.format("Shield: %.1fs", Double.valueOf(sec)) : "Shield: Ready"}; }
        else if ("attack_charge".equals(id)) raw = new String[]{"Attack: " + Math.round(d.attackCooldownPct() * 100.0F) + "%"};
        else if ("togglesprint".equals(id)) raw = new String[]{ClientState.isToggleSneaking() ? "[Sneaking (Toggled)]" : (d.isSprinting() ? "[Sprinting]" : (d.isSneaking() ? "[Sneaking]" : "[Walking]"))};
        else if ("speedometer".equals(id)) { String unit = cfg.getString("speed_unit", "bps"); double bps = ClientState.getSpeedBps(); raw = "kmh".equalsIgnoreCase(unit) ? new String[]{String.format("%.1f km/h", Double.valueOf(bps * 3.6))} : ("mph".equalsIgnoreCase(unit) ? new String[]{String.format("%.1f mph", Double.valueOf(bps * 2.23694))} : new String[]{String.format("%.2f b/s", Double.valueOf(bps))}); }
        else if ("coords".equals(id)) { boolean compact = cfg.getBool("coords_compact", false), facing = cfg.getBool("coords_facing", true), nether = cfg.getBool("coords_nether", true); String face = facing ? (" [" + d.facing() + "]") : "", main = compact ? (d.x() + " " + d.y() + " " + d.z() + face) : ("XYZ: " + d.x() + ", " + d.y() + ", " + d.z() + face); raw = nether ? new String[]{main, "Nether: " + (d.x() / 8) + ", " + (d.z() / 8)} : new String[]{main}; }
        else if ("chunk_coords".equals(id)) raw = new String[]{"Chunk: " + (d.x() >> 4) + ", " + (d.y() >> 4) + ", " + (d.z() >> 4)};
        else if ("compass".equals(id)) { int deg = ((Math.round(d.yaw()) % 360) + 360) % 360; raw = new String[]{cfg.getBool("compass_degrees", true) ? (d.facing() + " (" + deg + "\u00b0)") : d.facing()}; }
        else if ("biome".equals(id)) raw = new String[]{"Biome: " + d.biome()};
        else if ("light_level".equals(id)) raw = new String[]{"Light: B" + d.blockLight() + " / S" + d.skyLight()};
        else if ("slime_chunk".equals(id)) raw = new String[]{isSlimeChunk(d.x() >> 4, d.z() >> 4) ? "Slime Chunk: YES" : "Slime Chunk: No"};
        else if ("day_count".equals(id)) { long tod = d.worldTime() % 24000L; raw = new String[]{"Day " + d.dayCount() + String.format(" (%02d:%02d)", Integer.valueOf((int) (((tod / 1000L) + 6L) % 24L)), Integer.valueOf((int) ((tod % 1000L) * 60L / 1000L)))}; }
        else if ("moon_phase".equals(id)) { String[] phases = {"Full", "Waning Gibbous", "Last Qtr", "Waning Cres", "New", "Waxing Cres", "First Qtr", "Waxing Gibbous"}; raw = new String[]{"Moon: " + phases[Math.abs(d.moonPhase()) % phases.length]}; }
        else if ("weather".equals(id)) raw = new String[]{"Weather: " + d.weather()};
        else if ("waypoints".equals(id)) raw = new String[]{ClientState.getNearestWaypointText(d.x(), d.y(), d.z(), d.dimensionId())};
        else if ("spawn_dist".equals(id)) raw = new String[]{ClientState.getSpawnDistance(d.x(), d.z())};
        else if ("death_loc".equals(id)) raw = new String[]{"Death: " + ClientState.getLastDeathCoords(d.x(), d.z())};
        else if ("y_alert".equals(id)) { int y = d.y(); raw = new String[]{(y >= -64 && y <= -50) ? "Diamond Zone (Y" + y + ")" : ((y >= 12 && y <= 18) ? "Debris Zone (Y" + y + ")" : "Y-Level: " + y)}; }
        else if ("saturation".equals(id)) raw = new String[]{String.format("Sat: %.1f | Food: %d", Float.valueOf(d.saturation()), Integer.valueOf(d.foodLevel()))};
        else if ("oxygen".equals(id)) raw = new String[]{"Air: " + Math.max(0, Math.round(d.airSupply() / 20.0F)) + "s"};
        else if ("elytra_guide".equals(id)) raw = new String[]{String.format("Pitch: %.1f\u00b0 (Opt \u00b140\u00b0)", Float.valueOf(d.pitch()))};
        else if ("villager_timer".equals(id)) { long tod = d.worldTime() % 24000L; raw = new String[]{(tod >= 2000L && tod <= 9000L) ? "Restock: Window Open" : "Restock: Closed"}; }
        else if ("waila_block".equals(id)) raw = new String[]{"Block: " + d.lookingAtBlock()};
        else if ("waila_entity".equals(id)) raw = new String[]{"Entity: " + d.lookingAtEntity()};
        else if ("inv_space".equals(id)) raw = new String[]{"Slots: " + d.usedSlots() + " used / " + d.freeSlots() + " free"};
        else if ("mini_inventory".equals(id)) raw = new String[]{"Bag: " + d.usedSlots() + "/36 | Free " + d.freeSlots()};
        else if ("server_ip".equals(id)) raw = new String[]{d.serverIp() == null || d.serverIp().isEmpty() ? "Singleplayer" : d.serverIp()};
        else if ("player_count".equals(id)) raw = new String[]{"Players: " + d.onlinePlayers() + "/" + d.maxPlayers()};
        else if ("bw_resources".equals(id)) raw = new String[]{"I:" + d.countItem("iron") + " G:" + d.countItem("gold") + " D:" + d.countItem("diamond") + " E:" + d.countItem("emerald")};
        else if ("uhc_border".equals(id)) raw = new String[]{"Dist 0,0: " + Math.round(Math.hypot(d.x(), d.z())) + "m"};
        else if ("zoom_indicator".equals(id)) raw = new String[]{ClientState.isZooming() ? String.format("Zoom: %.1fx", Float.valueOf(ClientState.getSmoothedZoomDivisor())) : "Zoom: 1.0x"};
        else if ("gamma_level".equals(id)) raw = new String[]{"Gamma: " + Math.round(d.gammaPercent()) + "%"};
        else if ("fov_indicator".equals(id)) raw = new String[]{"FOV: " + Math.round(d.fovDegrees())};
        else if ("watermark".equals(id)) raw = new String[]{m.customText == null || m.customText.isEmpty() ? "Aetheria Client" : m.customText};
        else raw = new String[]{m.label != null ? m.label : id};
        String pre = m.prefix == null ? "" : m.prefix, suf = m.suffix == null ? "" : m.suffix;
        String[] out = new String[raw.length];
        for (int i = 0; i < raw.length; i++) out[i] = applyBrackets(pre + (raw[i] == null ? "" : raw[i]) + suf, m.bracketStyle);
        return out;
    }

    private static String applyBrackets(String s, String style) {
        if ("square".equalsIgnoreCase(style)) return "[" + s + "]"; if ("round".equalsIgnoreCase(style)) return "(" + s + ")";
        if ("angle".equalsIgnoreCase(style)) return "<" + s + ">"; if ("curly".equalsIgnoreCase(style)) return "{" + s + "}"; return s;
    }
    private static String getClockString(boolean use24h, boolean showSec) {
        long sec = System.currentTimeMillis() / 1000L;
        if (sec != lastClockSec || use24h != lastClock24 || showSec != lastClockSecFlag) {
            lastClockSec = sec; lastClock24 = use24h; lastClockSecFlag = showSec;
            cachedClockStr = (use24h ? (showSec ? CLOCK_24S : CLOCK_24) : (showSec ? CLOCK_12S : CLOCK_12)).format(new Date(sec * 1000L));
        }
        return cachedClockStr;
    }
    private static String formatDuration(long totalSec) { long h = totalSec / 3600L, m = (totalSec % 3600L) / 60L, s = totalSec % 60L; return h > 0L ? String.format("%d:%02d:%02d", Long.valueOf(h), Long.valueOf(m), Long.valueOf(s)) : String.format("%02d:%02d", Long.valueOf(m), Long.valueOf(s)); }
    private static boolean isSlimeChunk(int cx, int cz) { return new Random((((long) cx * cx * 0x4c1906L) + ((long) cx * 0x5ac0dbL) + ((long) cz * cz * 0x4307a7L) + ((long) cz * 0x5f24fL)) ^ 0x3ad8025fL).nextInt(10) == 0; }
    private static int textWidthSpaced(UiBridge.UiDraw g, String s, int spacing) { return (s == null || s.isEmpty()) ? 0 : (spacing <= 0 ? g.textWidth(s) : (g.textWidth(s) + (s.length() - 1) * spacing)); }
    private static void drawTextSpaced(UiBridge.UiDraw g, String s, int x, int y, int col, boolean shadow, int spacing) {
        if (s == null || s.isEmpty()) return; if (spacing <= 0) { g.text(s, x, y, col, shadow); return; }
        int cx = x; for (int i = 0; i < s.length(); i++) { String ch = s.substring(i, i + 1); g.text(ch, cx, y, col, shadow); cx += g.textWidth(ch) + spacing; }
    }
}