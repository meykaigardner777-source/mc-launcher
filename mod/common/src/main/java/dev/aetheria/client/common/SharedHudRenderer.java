package dev.aetheria.client.common;

import java.text.SimpleDateFormat;
import java.util.*;

public final class SharedHudRenderer {
    public static final int XHAIR_GRID_SIZE = 15;
    public static final String DEFAULT_CUSTOM_PIXELS =
        "000000000000000" +
        "000000000000000" +
        "000000000000000" +
        "000000000000000" +
        "000000010000000" +
        "000000010000000" +
        "000000000000000" +
        "000011000110000" +
        "000000000000000" +
        "000000010000000" +
        "000000010000000" +
        "000000000000000" +
        "000000000000000" +
        "000000000000000" +
        "000000000000000";
    public static final String DEFAULT_TARGET_CUSTOM_PIXELS =
        "000000000000000" +
        "000000000000000" +
        "000000000000000" +
        "000000000000000" +
        "000010010010000" +
        "000001010100000" +
        "000000000000000" +
        "000011010110000" +
        "000000000000000" +
        "000001010100000" +
        "000010010010000" +
        "000000000000000" +
        "000000000000000" +
        "000000000000000" +
        "000000000000000";

    private static final SimpleDateFormat CLOCK_12 = new SimpleDateFormat("hh:mm a");
    private static final SimpleDateFormat CLOCK_12S = new SimpleDateFormat("hh:mm:ss a");
    private static final SimpleDateFormat CLOCK_24 = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat CLOCK_24S = new SimpleDateFormat("HH:mm:ss");
    private static long lastClockSec = -1L;
    private static boolean lastClock24 = false, lastClockSecFlag = false;
    private static String cachedClockStr = "";

    private SharedHudRenderer() {}

    public static void renderInGame(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientState.recordFrameSample(d.fps());
        ClientState.tickComboTimeout();
        if (d.hasWorld()) {
            ClientState.sampleTelemetry(d.pingMs(), d.worldTime(), d.posX(), d.posY(), d.posZ(), d.yaw(), d.pitch(), d.health());
        }
        ClientConfig cfg = ClientConfig.get();
        renderLowHpVignette(g, d, cfg, sw, sh);
        if (!d.isF1Hidden()) renderCrosshair(g, d, sw, sh);

        boolean globalShadow = cfg.getBool("hud_shadow", true);
        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (!m.enabled) continue;
            if (m.hideInF1 && d.isF1Hidden()) continue;
            if (m.hideInChat && d.isChatOpen()) continue;
            if (m.hideInInventory && d.isInventoryOpen()) continue;
            if (m.hideInF3 && d.isF3Open()) continue;
            if ("togglesprint".equals(m.id) && cfg.getBool("togglesprint_hide_hud", false)) continue;
            if ("low_hp_vignette".equals(m.id)) continue;
            renderModule(g, d, m, sw, sh, globalShadow && m.shadow, false);
        }
    }

    private static void renderLowHpVignette(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig cfg, int sw, int sh) {
        ClientConfig.HudModule m = cfg.hudModules.get("low_hp_vignette");
        if (m == null || !m.enabled || !d.hasWorld() || d.health() > 7.0F) return;
        float pulse = 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int a = Math.round((30 + pulse * 45) * (float) m.opacity);
        int col = ClientState.withAlpha(0xFFEF4444, Math.max(10, Math.min(110, a)));
        g.fill(0, 0, sw, 6, col);
        g.fill(0, sh - 6, sw, sh, col);
        g.fill(0, 6, 6, sh - 6, col);
        g.fill(sw - 6, 6, sw, sh - 6, col);
    }

    public static int[] getBaseBoxSize(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m) {
        int px = Math.max(1, Math.min(24, m.paddingX));
        int py = Math.max(1, Math.min(24, m.paddingY));
        int w, h;
        if ("keystrokes".equals(m.id)) {
            ClientConfig cfg = ClientConfig.get();
            boolean showCps = cfg.getBool("keystrokes_cps", true), showSpace = cfg.getBool("keystrokes_space", true);
            boolean showMods = cfg.getBool("keystrokes_mods", false);
            w = 76 + (px - 6) * 2;
            h = 42 + (showCps ? 16 : 0) + (showSpace ? 14 : 0) + (showMods ? 14 : 0) + (py - 4) * 2;
        } else if ("frametime_graph".equals(m.id) || "ping_graph".equals(m.id) || "cps_graph".equals(m.id)) {
            w = 110; h = 42;
        } else if ("mouse_visualizer".equals(m.id)) {
            w = 54; h = 54;
        } else if ("minimap".equals(m.id)) {
            w = 66; h = 66;
        } else if ("paperdoll".equals(m.id)) {
            w = 46; h = 64;
        } else if ("audio_viz".equals(m.id)) {
            w = 88; h = 34;
        } else if ("target_hud".equals(m.id)) {
            w = 132; h = 38;
        } else if ("ram_usage".equals(m.id) || "saturation".equals(m.id) || "attack_charge".equals(m.id)) {
            String[] lines = getFormattedLines(d, m);
            int maxW = 70; for (String ln : lines) maxW = Math.max(maxW, textWidthSpaced(g, ln, m.letterSpacing));
            w = maxW + px * 2; h = lines.length * 11 + py * 2 + 6;
        } else {
            String[] lines = getFormattedLines(d, m);
            int maxW = 0; for (String ln : lines) maxW = Math.max(maxW, textWidthSpaced(g, ln, m.letterSpacing));
            w = Math.max(30, maxW + px * 2);
            h = Math.max(14, lines.length * 11 + py * 2);
        }
        if (m.width > 0) w = Math.max(24, m.width);
        if (m.height > 0) h = Math.max(14, m.height);
        return new int[]{w, h};
    }

    public static int[] getModulePixelBounds(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh) {
        int[] base = getBaseBoxSize(g, d, m);
        double sc = Math.max(0.25, Math.min(4.0, m.scale));
        int scaledW = (int) Math.round(base[0] * sc);
        int scaledH = (int) Math.round(base[1] * sc);
        int ax = (int) Math.round(m.x * sw);
        int ay = (int) Math.round(m.y * sh);
        String anc = m.anchor == null ? "top-left" : m.anchor;
        if (anc.endsWith("-center") || "center".equals(anc)) ax -= scaledW / 2;
        else if (anc.endsWith("-right")) ax -= scaledW;
        if (anc.startsWith("mid-") || "center".equals(anc)) ay -= scaledH / 2;
        else if (anc.startsWith("bottom-")) ay -= scaledH;
        ax += m.margin; ay += m.margin;
        return new int[]{ax, ay, scaledW, scaledH};
    }

    public static void renderModule(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh, boolean shadow, boolean editorPreview) {
        if ("target_hud".equals(m.id) && !editorPreview && (d.targetName() == null || d.targetName().isEmpty() || d.targetHealth() <= 0.0F)) {
            return;
        }

        int[] bounds = getModulePixelBounds(g, d, m, sw, sh);
        int[] base = getBaseBoxSize(g, d, m);
        int boxW = base[0], boxH = base[1];
        float scale = (float) Math.max(0.25, Math.min(4.0, m.scale));

        int baseTextCol = m.chroma
            ? ClientState.getChromaColor(m.chromaSpeed, m.chromaSat, m.chromaAngle, m.x, m.y)
            : ClientState.parseHexColor(m.color, 0xFFFFFFFF);
        int textCol = ClientState.scaleAlpha(baseTextCol, m.textOpacity);

        double gRad = ClientConfig.get().getDouble("global_hud_rounding", -1.0);
        int rad = gRad >= 0 ? (int) Math.round(gRad) : Math.max(0, Math.min(30, m.borderRadius));

        int px = Math.max(1, Math.min(24, m.paddingX));
        int py = Math.max(1, Math.min(24, m.paddingY));

        g.pushTransform(bounds[0], bounds[1], scale);
        try {
            double gOpac = ClientConfig.get().getDouble("global_hud_opacity", -1.0);
            double activeOpac = gOpac >= 0 ? gOpac : m.opacity;
            int bgCol = m.bg ? ClientState.scaleAlpha(ClientState.parseHexColor(m.bgColor, 0xFF09090B), activeOpac) : 0;
            int brdCol = m.border ? ClientState.parseHexColor(m.borderColor, 0xFF27272A) : 0;
            int brdCol2 = m.border ? ClientState.parseHexColor(m.borderColor2, brdCol) : 0;
            if (m.chroma && m.border) {
                brdCol = baseTextCol;
                brdCol2 = ClientState.getChromaColor(m.chromaSpeed, m.chromaSat, m.chromaAngle, Math.min(1.0, m.x + 0.15), m.y);
            }
            if (m.bg || m.border) {
                g.drawGlassCard(0, 0, boxW, boxH, rad, m.border ? m.borderWidth : 0, m.borderStyle, bgCol, brdCol, brdCol2);
            }

            if ("keystrokes".equals(m.id)) {
                renderKeystrokes(g, d, px, py, boxW, textCol, shadow, rad);
            } else if ("frametime_graph".equals(m.id)) {
                float[] hist = ClientState.getFrametimeHistory();
                float curMs = hist.length > 0 ? hist[hist.length - 1] : 0.0F;
                drawTextSpaced(g, String.format("FT %.1fms", Float.valueOf(curMs)), px, py, textCol, shadow, m.letterSpacing);
                g.drawSparkline(px, py + 11, boxW - px * 2, boxH - py * 2 - 11, hist, hist.length, 40.0F, textCol, ClientState.withAlpha(textCol, 40));
            } else if ("ping_graph".equals(m.id)) {
                float[] hist = ClientState.getPingHistory();
                drawTextSpaced(g, "Ping " + d.pingMs() + "ms", px, py, textCol, shadow, m.letterSpacing);
                g.drawSparkline(px, py + 11, boxW - px * 2, boxH - py * 2 - 11, hist, hist.length, 250.0F, textCol, ClientState.withAlpha(textCol, 40));
            } else if ("cps_graph".equals(m.id)) {
                float[] hist = ClientState.getCpsHistory();
                int cps = ClientState.getLeftCps() + ClientState.getRightCps();
                drawTextSpaced(g, "CPS " + cps, px, py, textCol, shadow, m.letterSpacing);
                g.drawSparkline(px, py + 11, boxW - px * 2, boxH - py * 2 - 11, hist, hist.length, 20.0F, textCol, ClientState.withAlpha(textCol, 40));
            } else if ("mouse_visualizer".equals(m.id)) {
                renderMouseVisualizer(g, boxW, boxH, textCol);
            } else if ("minimap".equals(m.id)) {
                renderMinimap(g, d, boxW, boxH, textCol);
            } else if ("paperdoll".equals(m.id)) {
                renderPaperdoll(g, d, boxW, boxH, textCol);
            } else if ("audio_viz".equals(m.id)) {
                renderAudioViz(g, px, py, boxW, boxH, textCol);
            } else if ("target_hud".equals(m.id)) {
                renderTargetHud(g, d, px, py, boxW, boxH, textCol, shadow, editorPreview);
            } else if ("ram_usage".equals(m.id) || "saturation".equals(m.id) || "attack_charge".equals(m.id)) {
                String[] lines = getFormattedLines(d, m);
                for (int i = 0; i < lines.length; i++) {
                    drawTextSpaced(g, lines[i], px, py + i * 11, textCol, shadow, m.letterSpacing);
                }
                float pct = getBarPercent(d, m.id);
                g.drawProgressBar(px, boxH - py - 4, boxW - px * 2, 4, 2, pct, 0x33FFFFFF, textCol);
            } else {
                String[] lines = getFormattedLines(d, m);
                for (int i = 0; i < lines.length; i++) {
                    drawTextSpaced(g, lines[i], px, py + i * 11, textCol, shadow, m.letterSpacing);
                }
            }
        } finally {
            g.popTransform();
        }
    }

    private static float getBarPercent(UiBridge.HudData d, String id) {
        if ("ram_usage".equals(id)) {
            Runtime rt = Runtime.getRuntime();
            long max = Math.max(1L, rt.maxMemory());
            long used = rt.totalMemory() - rt.freeMemory();
            return (float) used / (float) max;
        }
        if ("saturation".equals(id)) {
            return Math.max(0.0F, Math.min(1.0F, d.saturation() / 20.0F));
        }
        return Math.max(0.0F, Math.min(1.0F, d.attackCooldownPct()));
    }

    private static void renderKeystrokes(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int boxW, int textCol, boolean shadow, int parentRad) {
        ClientConfig cfg = ClientConfig.get();
        boolean showCps = cfg.getBool("keystrokes_cps", true);
        boolean showSpace = cfg.getBool("keystrokes_space", true);
        boolean showMods = cfg.getBool("keystrokes_mods", false);

        int keyRad = Math.min(6, parentRad / 2);
        int kw = 20, kh = 18, gap = 2;
        int startX = (boxW - (kw * 3 + gap * 2)) / 2;
        int y = py;

        drawKeyBox(g, startX + kw + gap, y, kw, kh, keyRad, "W", d.isMoveW(), textCol, shadow);
        y += kh + gap;
        drawKeyBox(g, startX, y, kw, kh, keyRad, "A", d.isMoveA(), textCol, shadow);
        drawKeyBox(g, startX + kw + gap, y, kw, kh, keyRad, "S", d.isMoveS(), textCol, shadow);
        drawKeyBox(g, startX + (kw + gap) * 2, y, kw, kh, keyRad, "D", d.isMoveD(), textCol, shadow);
        y += kh + gap;

        int fullW = kw * 3 + gap * 2;
        if (showCps) {
            int mw = (fullW - gap) / 2;
            drawKeyBox(g, startX, y, mw, 14, keyRad, "L:" + ClientState.getLeftCps(), ClientState.isLeftDown(), textCol, shadow);
            drawKeyBox(g, startX + mw + gap, y, fullW - mw - gap, 14, keyRad, "R:" + ClientState.getRightCps(), ClientState.isRightDown(), textCol, shadow);
            y += 16;
        }
        if (showSpace) {
            drawKeyBox(g, startX, y, fullW, 12, keyRad, "---", d.isJumpDown(), textCol, shadow);
            y += 14;
        }
        if (showMods) {
            int mw = (fullW - gap) / 2;
            drawKeyBox(g, startX, y, mw, 12, keyRad, "SPR", d.isSprinting(), textCol, shadow);
            drawKeyBox(g, startX + mw + gap, y, fullW - mw - gap, 12, keyRad, "SNK", d.isSneaking(), textCol, shadow);
        }
    }

    private static void drawKeyBox(UiBridge.UiDraw g, int x, int y, int w, int h, int rad, String label, boolean pressed, int textCol, boolean shadow) {
        int bg = pressed ? ClientState.withAlpha(textCol, 195) : 0x44000000;
        int fg = pressed ? 0xFF09090B : textCol;
        g.drawGlassCard(x, y, w, h, rad, bg, pressed ? textCol : 0x33FFFFFF);
        int tw = g.textWidth(label);
        g.text(label, x + (w - tw) / 2, y + (h - 8) / 2, fg, shadow && !pressed);
    }

    private static void renderMouseVisualizer(UiBridge.UiDraw g, int w, int h, int textCol) {
        int cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 6;
        g.drawCircle(cx, cy, r, 1, 0x44FFFFFF);
        g.fill(cx - r, cy, cx + r, cy + 1, 0x22FFFFFF);
        g.fill(cx, cy - r, cx + 1, cy + r, 0x22FFFFFF);
        int dx = Math.round(ClientState.getMouseVelX() * (r - 3));
        int dy = Math.round(ClientState.getMouseVelY() * (r - 3));
        g.drawCircleFilled(cx + dx, cy + dy, 3, textCol);
    }

    private static void renderMinimap(UiBridge.UiDraw g, UiBridge.HudData d, int w, int h, int textCol) {
        int cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 6;
        g.drawCircle(cx, cy, r, 1, 0x44FFFFFF);
        g.drawCircle(cx, cy, Math.max(4, r / 2), 1, 0x22FFFFFF);
        double rad = Math.toRadians(-d.yaw() - 90.0);
        int nx = cx + (int) Math.round(Math.cos(rad) * (r - 2));
        int ny = cy + (int) Math.round(Math.sin(rad) * (r - 2));
        g.text("N", nx - 2, ny - 4, 0xFFEF4444, false);

        int[] radar = d.nearbyEntityRadar();
        if (radar != null) {
            for (int i = 0; i + 1 < radar.length; i += 2) {
                int ex = Math.max(-r + 3, Math.min(r - 3, radar[i]));
                int ez = Math.max(-r + 3, Math.min(r - 3, radar[i + 1]));
                g.drawCircleFilled(cx + ex, cy + ez, 2, 0xFF22D3EE);
            }
        }
        g.drawCircleFilled(cx, cy, 2, textCol);
    }

    private static void renderPaperdoll(UiBridge.UiDraw g, UiBridge.HudData d, int w, int h, int textCol) {
        int cx = w / 2;
        int headY = d.isSneaking() ? 15 : 11;
        g.drawRoundedRect(cx - 5, headY, 10, 10, 2, textCol);
        int bodyH = d.isSneaking() ? 16 : 18;
        g.fill(cx - 4, headY + 11, cx + 4, headY + 11 + bodyH, ClientState.withAlpha(textCol, 210));
        int swing = d.isSprinting() ? (int) Math.round(Math.sin(System.currentTimeMillis() / 110.0) * 4.0) : 0;
        g.fill(cx - 7, headY + 11 + swing / 2, cx - 5, headY + 25 + swing / 2, ClientState.withAlpha(textCol, 175));
        g.fill(cx + 5, headY + 11 - swing / 2, cx + 7, headY + 25 - swing / 2, ClientState.withAlpha(textCol, 175));
        int legTop = headY + 11 + bodyH;
        g.fill(cx - 4, legTop, cx - 1, legTop + 14 + swing, ClientState.withAlpha(textCol, 185));
        g.fill(cx + 1, legTop, cx + 4, legTop + 14 - swing, ClientState.withAlpha(textCol, 185));
    }

    private static void renderAudioViz(UiBridge.UiDraw g, int px, int py, int w, int h, int textCol) {
        int bars = 12;
        int availW = Math.max(24, w - px * 2);
        int barW = Math.max(2, (availW - (bars - 1) * 2) / bars);
        int maxH = Math.max(8, h - py * 2);
        long t = System.currentTimeMillis();
        for (int i = 0; i < bars; i++) {
            double wave = 0.25 + 0.75 * Math.abs(Math.sin(t / 140.0 + i * 0.65));
            int bh = Math.max(2, (int) Math.round(wave * maxH));
            int bx = px + i * (barW + 2);
            g.fill(bx, h - py - bh, bx + barW, h - py, ClientState.withAlpha(textCol, 180 + (i % 3) * 25));
        }
    }

    private static void renderTargetHud(UiBridge.UiDraw g, UiBridge.HudData d, int px, int py, int boxW, int boxH, int textCol, boolean shadow, boolean editorPreview) {
        String name = (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetName() : (editorPreview ? "Player" : "");
        float hp = (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetHealth() : 16.0F;
        float maxHp = Math.max(1.0F, (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetMaxHealth() : 20.0F);
        double dist = (d.targetName() != null && !d.targetName().isEmpty()) ? d.targetDistance() : 3.2;

        g.text(name, px, py, textCol, shadow);
        String hpTxt = String.format("%.1f HP  %.1fm", Float.valueOf(hp), Double.valueOf(dist));
        g.text(hpTxt, px, py + 11, ClientState.UI_TEXT_MUTED, shadow);
        float pct = Math.max(0.0F, Math.min(1.0F, hp / maxHp));
        int barCol = pct > 0.5F ? 0xFF10B981 : (pct > 0.25F ? 0xFFF59E0B : 0xFFEF4444);
        g.drawProgressBar(px, boxH - py - 5, boxW - px * 2, 5, 2, pct, 0x33FFFFFF, barCol);
    }

    private static void renderCrosshair(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        String style = cfg.getString("pvp_xhair_style", "classic");
        if ("vanilla".equalsIgnoreCase(style)) return;

        int size = Math.max(1, (int) Math.round(cfg.getDouble("pvp_xhair_size", 6.0)));
        int gap = Math.max(0, (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2.0)));
        int thick = Math.max(1, Math.min(4, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1.0))));
        int col = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF);
        boolean useTargetCustom = false;

        if (cfg.getBool("pvp_xhair_target", true)) {
            double range = Math.max(1.0, cfg.getDouble("pvp_xhair_target_range", 12.0));
            boolean playersOnly = cfg.getBool("pvp_xhair_target_players_only", true);
            boolean hit = playersOnly ? d.targetPlayerInView(range) : d.targetEntityInView(range);
            if (hit) {
                col = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);
                String tStyle = cfg.getString("pvp_xhair_target_style", "same");
                if ("custom".equalsIgnoreCase(tStyle)) {
                    style = "custom";
                    useTargetCustom = true;
                } else if (!"same".equalsIgnoreCase(tStyle)) {
                    style = tStyle;
                }
            }
        }

        drawCrosshairShape(g, sw / 2, sh / 2, style, size, gap, thick, col, useTargetCustom);
    }

    public static void drawCrosshairShape(UiBridge.UiDraw g, int cx, int cy, String style, int size, int gap, int thick, int col) {
        drawCrosshairShape(g, cx, cy, style, size, gap, thick, col, false);
    }

    public static void drawCrosshairShape(UiBridge.UiDraw g, int cx, int cy, String style, int size, int gap, int thick, int col, boolean useTargetCustom) {
        if ("vanilla".equalsIgnoreCase(style)) return;
        if ("custom".equalsIgnoreCase(style)) {
            ClientConfig cfg = ClientConfig.get();
            String pixels = useTargetCustom
                ? cfg.getString("pvp_xhair_target_custom_pixels", DEFAULT_TARGET_CUSTOM_PIXELS)
                : cfg.getString("pvp_xhair_custom_pixels", DEFAULT_CUSTOM_PIXELS);
            int pScale = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_pixel_scale", 1.0))));
            drawCustomPixelCrosshair(g, cx, cy, pixels, pScale, col);
            return;
        }

        size = Math.max(1, size);
        gap = Math.max(0, gap);
        thick = Math.max(1, Math.min(4, thick));
        int halfT = thick / 2;

        if ("dot".equalsIgnoreCase(style)) {
            g.fill(cx - halfT - 1, cy - halfT - 1, cx - halfT + thick + 1, cy - halfT + thick + 1, col);
        } else if ("circle".equalsIgnoreCase(style)) {
            g.drawCircle(cx, cy, Math.max(2, size), thick, col);
        } else if ("t_shape".equalsIgnoreCase(style)) {
            g.fill(cx - gap - size, cy - halfT, cx - gap, cy - halfT + thick, col);
            g.fill(cx + gap + 1, cy - halfT, cx + gap + 1 + size, cy - halfT + thick, col);
            g.fill(cx - halfT, cy + gap + 1, cx - halfT + thick, cy + gap + 1 + size, col);
        } else if ("x_cross".equalsIgnoreCase(style)) {
            g.drawLine(cx - gap - size, cy - gap - size, cx - gap, cy - gap, thick, col);
            g.drawLine(cx + gap + 1, cy - gap - size, cx + gap + 1 + size, cy - gap, thick, col);
            g.drawLine(cx - gap - size, cy + gap + 1 + size, cx - gap, cy + gap + 1, thick, col);
            g.drawLine(cx + gap + 1, cy + gap + 1 + size, cx + gap + 1 + size, cy + gap + 1, thick, col);
        } else if ("arrows".equalsIgnoreCase(style) || "chevron".equalsIgnoreCase(style)) {
            int s = Math.max(2, size / 2 + 1);
            g.drawLine(cx - gap - s, cy - s, cx - gap, cy, thick, col);
            g.drawLine(cx - gap - s, cy + s, cx - gap, cy, thick, col);
            g.drawLine(cx + gap + s, cy - s, cx + gap, cy, thick, col);
            g.drawLine(cx + gap + s, cy + s, cx + gap, cy, thick, col);
        } else {
            g.fill(cx - gap - size, cy - halfT, cx - gap, cy - halfT + thick, col);
            g.fill(cx + gap + 1, cy - halfT, cx + gap + 1 + size, cy - halfT + thick, col);
            g.fill(cx - halfT, cy - gap - size, cx - halfT + thick, cy - gap, col);
            g.fill(cx - halfT, cy + gap + 1, cx - halfT + thick, cy + gap + 1 + size, col);
            if ("cross_dot".equalsIgnoreCase(style)) {
                g.fill(cx - halfT, cy - halfT, cx - halfT + thick, cy - halfT + thick, col);
            }
        }
    }

    public static void drawCustomPixelCrosshair(UiBridge.UiDraw g, int cx, int cy, String pixels, int scale, int col) {
        if (pixels == null || pixels.isEmpty()) return;
        int n = XHAIR_GRID_SIZE;
        int half = n / 2;
        int sc = Math.max(1, Math.min(3, scale));
        int off = sc / 2;
        int len = Math.min(pixels.length(), n * n);
        for (int idx = 0; idx < len; idx++) {
            if (pixels.charAt(idx) != '1') continue;
            int r = idx / n;
            int c = idx % n;
            int px = cx + (c - half) * sc - off;
            int py = cy + (r - half) * sc - off;
            g.fill(px, py, px + sc, py + sc, col);
        }
    }

    private static String[] getFormattedLines(UiBridge.HudData d, ClientConfig.HudModule m) {
        ClientConfig cfg = ClientConfig.get();
        String id = m.id;
        String[] raw;

        if ("fps".equals(id)) {
            boolean lbl = cfg.getBool("fps_show_label", true);
            boolean det = cfg.getBool("fps_detailed", false);
            String l1 = (lbl ? "FPS: " : "") + d.fps();
            raw = det
                ? new String[]{l1, "Min " + ClientState.getMinFps() + " | 1% " + ClientState.getOnePercentLowFps()}
                : new String[]{l1};
        } else if ("ram_usage".equals(id)) {
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
            long maxMb = Math.max(1L, rt.maxMemory() / (1024L * 1024L));
            int pct = (int) Math.round((usedMb * 100.0) / maxMb);
            raw = new String[]{"RAM: " + usedMb + "/" + maxMb + "MB (" + pct + "%)"};
        } else if ("cpu_load".equals(id)) {
            raw = new String[]{"CPU: " + Runtime.getRuntime().availableProcessors() + " Cores | " + Thread.activeCount() + " Thr"};
        } else if ("ping".equals(id)) {
            raw = new String[]{"Ping: " + d.pingMs() + "ms"};
        } else if ("server_tps".equals(id)) {
            raw = new String[]{String.format("TPS: %.1f (%.0fms)", Double.valueOf(ClientState.getEstimatedTps()), Double.valueOf(ClientState.getEstimatedTickMs()))};
        } else if ("packet_counter".equals(id)) {
            raw = new String[]{"Tick: " + (d.worldTime() % 1000L) + " | " + String.format("%.1f", Double.valueOf(ClientState.getEstimatedTps())) + "/s"};
        } else if ("bandwidth".equals(id)) {
            raw = new String[]{"Net: " + Math.max(1, d.onlinePlayers() * 2) + " KB/s"};
        } else if ("session".equals(id)) {
            raw = new String[]{"Session: " + formatDuration((System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L)};
        } else if ("total_playtime".equals(id)) {
            raw = new String[]{"Playtime: " + formatDuration((System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L)};
        } else if ("clock".equals(id)) {
            raw = new String[]{getClockString(cfg.getBool("clock_24h", false), cfg.getBool("clock_seconds", false))};
        } else if ("stopwatch".equals(id)) {
            raw = new String[]{"SW: " + ClientState.getStopwatchText()};
        } else if ("countdown".equals(id)) {
            raw = new String[]{ClientState.getCountdownText()};
        } else if ("battery".equals(id)) {
            raw = new String[]{"Power: AC / Runtime OK"};
        } else if ("gc_monitor".equals(id)) {
            raw = new String[]{ClientState.getGcSummary()};
        } else if ("chunk_updates".equals(id)) {
            raw = new String[]{"Chunks: " + (d.x() >> 4) + "," + (d.z() >> 4)};
        } else if ("entity_counter".equals(id)) {
            raw = new String[]{"Entities: " + d.renderedEntities() + "/" + d.totalEntities()};
        } else if ("cps".equals(id)) {
            raw = new String[]{"CPS: " + ClientState.getLeftCps() + " | " + ClientState.getRightCps()};
        } else if ("combo".equals(id)) {
            int c = ClientState.getComboCount();
            raw = new String[]{c > 0 ? (c + " Combo") : "No Combo"};
        } else if ("reach".equals(id)) {
            raw = new String[]{String.format("Reach: %.2f blocks", Double.valueOf(ClientState.getLastReach()))};
        } else if ("hit_accuracy".equals(id)) {
            raw = new String[]{"Accuracy: " + ClientState.getHitAccuracyPct() + "%"};
        } else if ("sprint_reset".equals(id)) {
            raw = new String[]{d.isSprinting() ? "Sprint: Reset Ready" : "Sprint: Idle"};
        } else if ("armor".equals(id)) {
            String arm = d.armorStatus();
            raw = (arm == null || arm.isEmpty()) ? new String[]{"Armor: None"} : arm.split("\n");
        } else if ("armor_prot".equals(id)) {
            raw = new String[]{"Prot: " + d.armorReductionPct() + "% (EPF " + d.armorEpf() + ")"};
        } else if ("durability".equals(id)) {
            raw = new String[]{d.heldToolDurability(), d.offhandDurability()};
        } else if ("potions".equals(id)) {
            String[] pots = d.activePotions();
            raw = (pots == null || pots.length == 0) ? new String[]{"No Potions"} : pots;
        } else if ("potion_count".equals(id)) {
            raw = new String[]{"Potions: " + d.countItem("potion")};
        } else if ("heal_count".equals(id)) {
            raw = new String[]{"GApples: " + d.countItem("apple")};
        } else if ("arrow_count".equals(id)) {
            raw = new String[]{"Arrows: " + d.countItem("arrow")};
        } else if ("totem_count".equals(id)) {
            raw = new String[]{"Totems: " + d.countItem("totem")};
        } else if ("pearl_cd".equals(id)) {
            double sec = ClientState.getPearlCooldownSec();
            raw = new String[]{sec > 0.0 ? String.format("Pearl: %.1fs", Double.valueOf(sec)) : "Pearl: Ready"};
        } else if ("shield_cd".equals(id)) {
            double sec = ClientState.getShieldCooldownSec();
            raw = new String[]{sec > 0.0 ? String.format("Shield: %.1fs", Double.valueOf(sec)) : "Shield: Ready"};
        } else if ("attack_charge".equals(id)) {
            int pct = Math.round(d.attackCooldownPct() * 100.0F);
            raw = new String[]{"Attack: " + pct + "%"};
        } else if ("togglesprint".equals(id)) {
            raw = new String[]{d.isSprinting() ? "[Sprinting (Toggled)]" : (d.isSneaking() ? "[Sneaking]" : "[Walking]")};
        } else if ("speedometer".equals(id)) {
            String unit = cfg.getString("speed_unit", "bps");
            double bps = ClientState.getSpeedBps();
            if ("kmh".equalsIgnoreCase(unit)) raw = new String[]{String.format("%.1f km/h", Double.valueOf(bps * 3.6))};
            else if ("mph".equalsIgnoreCase(unit)) raw = new String[]{String.format("%.1f mph", Double.valueOf(bps * 2.23694))};
            else raw = new String[]{String.format("%.2f b/s", Double.valueOf(bps))};
        } else if ("coords".equals(id)) {
            boolean compact = cfg.getBool("coords_compact", false);
            boolean facing = cfg.getBool("coords_facing", true);
            boolean nether = cfg.getBool("coords_nether", true);
            String face = facing ? (" [" + d.facing() + "]") : "";
            String main = compact
                ? (d.x() + " " + d.y() + " " + d.z() + face)
                : ("XYZ: " + d.x() + ", " + d.y() + ", " + d.z() + face);
            raw = nether
                ? new String[]{main, "Nether: " + (d.x() / 8) + ", " + d.z() / 8}
                : new String[]{main};
        } else if ("chunk_coords".equals(id)) {
            raw = new String[]{"Chunk: " + (d.x() >> 4) + ", " + (d.y() >> 4) + ", " + (d.z() >> 4)};
        } else if ("compass".equals(id)) {
            int deg = ((Math.round(d.yaw()) % 360) + 360) % 360;
            raw = new String[]{cfg.getBool("compass_degrees", true) ? (d.facing() + " (" + deg + "°)") : d.facing()};
        } else if ("biome".equals(id)) {
            raw = new String[]{"Biome: " + d.biome()};
        } else if ("light_level".equals(id)) {
            raw = new String[]{"Light: B" + d.blockLight() + " / S" + d.skyLight()};
        } else if ("slime_chunk".equals(id)) {
            raw = new String[]{isSlimeChunk(d.x() >> 4, d.z() >> 4) ? "Slime Chunk: YES" : "Slime Chunk: No"};
        } else if ("day_count".equals(id)) {
            long tod = d.worldTime() % 24000L;
            int hr = (int) (((tod / 1000L) + 6L) % 24L);
            int min = (int) ((tod % 1000L) * 60L / 1000L);
            raw = new String[]{"Day " + d.dayCount() + String.format(" (%02d:%02d)", Integer.valueOf(hr), Integer.valueOf(min))};
        } else if ("moon_phase".equals(id)) {
            String[] phases = {"Full", "Waning Gibbous", "Last Qtr", "Waning Cres", "New", "Waxing Cres", "First Qtr", "Waxing Gibbous"};
            int p = Math.abs(d.moonPhase()) % phases.length;
            raw = new String[]{"Moon: " + phases[p]};
        } else if ("weather".equals(id)) {
            raw = new String[]{"Weather: " + d.weather()};
        } else if ("waypoints".equals(id)) {
            raw = new String[]{ClientState.getNearestWaypointText(d.x(), d.y(), d.z())};
        } else if ("spawn_dist".equals(id)) {
            raw = new String[]{ClientState.getSpawnDistance(d.x(), d.z())};
        } else if ("death_loc".equals(id)) {
            raw = new String[]{"Death: " + ClientState.getLastDeathCoords(d.x(), d.z())};
        } else if ("y_alert".equals(id)) {
            int y = d.y();
            String msg = (y >= -64 && y <= -50) ? "Diamond Zone (Y" + y + ")" : ((y >= 12 && y <= 18) ? "Debris Zone (Y" + y + ")" : "Y-Level: " + y);
            raw = new String[]{msg};
        } else if ("saturation".equals(id)) {
            raw = new String[]{String.format("Sat: %.1f | Food: %d", Float.valueOf(d.saturation()), Integer.valueOf(d.foodLevel()))};
        } else if ("oxygen".equals(id)) {
            int sec = Math.max(0, Math.round(d.airSupply() / 20.0F));
            raw = new String[]{"Air: " + sec + "s"};
        } else if ("mount_stats".equals(id)) {
            raw = new String[]{d.mountStats()};
        } else if ("elytra_guide".equals(id)) {
            raw = new String[]{String.format("Pitch: %.1f° (Opt ±40°)", Float.valueOf(d.pitch()))};
        } else if ("villager_timer".equals(id)) {
            long tod = d.worldTime() % 24000L;
            raw = new String[]{(tod >= 2000L && tod <= 9000L) ? "Restock: Window Open" : "Restock: Closed"};
        } else if ("spawner_info".equals(id)) {
            raw = new String[]{"Block: " + d.lookingAtBlock()};
        } else if ("waila_block".equals(id)) {
            raw = new String[]{"Block: " + d.lookingAtBlock()};
        } else if ("waila_entity".equals(id)) {
            raw = new String[]{"Entity: " + d.lookingAtEntity()};
        } else if ("inv_space".equals(id)) {
            raw = new String[]{"Slots: " + d.usedSlots() + " used / " + d.freeSlots() + " free"};
        } else if ("mini_inventory".equals(id)) {
            raw = new String[]{"Bag: " + d.usedSlots() + "/36 | Free " + d.freeSlots()};
        } else if ("server_ip".equals(id)) {
            raw = new String[]{d.serverIp() == null || d.serverIp().isEmpty() ? "Singleplayer" : d.serverIp()};
        } else if ("player_count".equals(id)) {
            raw = new String[]{"Players: " + d.onlinePlayers() + "/" + d.maxPlayers()};
        } else if ("friends_online".equals(id)) {
            raw = new String[]{"Friends: 0 Online"};
        } else if ("scoreboard_mod".equals(id)) {
            raw = new String[]{"Scoreboard: Clean"};
        } else if ("bossbar_mod".equals(id)) {
            raw = new String[]{"BossBar: Styled"};
        } else if ("actionbar_mod".equals(id)) {
            raw = new String[]{"ActionBar: Custom"};
        } else if ("title_scaler".equals(id)) {
            raw = new String[]{"Title Scale: 1.0x"};
        } else if ("bw_resources".equals(id)) {
            raw = new String[]{"I:" + d.countItem("iron") + " G:" + d.countItem("gold") + " D:" + d.countItem("diamond") + " E:" + d.countItem("emerald")};
        } else if ("bw_gens".equals(id)) {
            raw = new String[]{"Gen Timer: Ready"};
        } else if ("uhc_border".equals(id)) {
            raw = new String[]{"Dist 0,0: " + Math.round(Math.hypot(d.x(), d.z())) + "m"};
        } else if ("speedrun_splits".equals(id)) {
            raw = new String[]{ClientState.getSplitText()};
        } else if ("kdr_tracker".equals(id)) {
            int k = ClientState.getKills(), dt = ClientState.getDeaths();
            double kdr = dt == 0 ? k : (k / (double) dt);
            raw = new String[]{String.format("K:%d D:%d (%.2f)", Integer.valueOf(k), Integer.valueOf(dt), Double.valueOf(kdr))};
        } else if ("autogg_status".equals(id)) {
            raw = new String[]{"AutoGG: Active"};
        } else if ("queue_pos".equals(id)) {
            raw = new String[]{"Queue: None"};
        } else if ("level_head".equals(id)) {
            raw = new String[]{"LevelHead: Active"};
        } else if ("chat_mentions".equals(id)) {
            raw = ClientState.getChatMentions();
        } else if ("voice_hud".equals(id)) {
            raw = new String[]{"Voice: Connected"};
        } else if ("party_list".equals(id)) {
            raw = new String[]{"Party: Solo"};
        } else if ("pack_display".equals(id)) {
            raw = new String[]{"Pack: " + d.resourcePacks()};
        } else if ("shader_display".equals(id)) {
            raw = new String[]{"Shader: Off"};
        } else if ("now_playing".equals(id)) {
            raw = new String[]{"Media: Idle"};
        } else if ("pickup_feed".equals(id)) {
            raw = ClientState.getRecentPickups();
        } else if ("toast_style".equals(id)) {
            raw = new String[]{"Toasts: Clean"};
        } else if ("subtitles_mod".equals(id)) {
            raw = new String[]{"Sounds: Clean"};
        } else if ("zoom_indicator".equals(id)) {
            raw = new String[]{ClientState.isZooming() ? String.format("Zoom: %.1fx", Float.valueOf(ClientState.getSmoothedZoomDivisor())) : "Zoom: 1.0x"};
        } else if ("gamma_level".equals(id)) {
            raw = new String[]{"Gamma: " + Math.round(d.gammaPercent()) + "%"};
        } else if ("fov_indicator".equals(id)) {
            raw = new String[]{"FOV: " + Math.round(d.fovDegrees())};
        } else if ("freelook_badge".equals(id)) {
            raw = new String[]{ClientState.isFreeLook() ? "FreeLook: ON" : "FreeLook: OFF"};
        } else if ("screenshot_toast".equals(id)) {
            raw = new String[]{"Capture: Ready"};
        } else if ("arraylist".equals(id)) {
            raw = new String[]{"Aetheria Client"};
        } else if ("watermark".equals(id)) {
            raw = new String[]{m.customText == null || m.customText.isEmpty() ? "Aetheria Client" : m.customText};
        } else if ("custom_image".equals(id)) {
            raw = new String[]{m.customText == null || m.customText.isEmpty() ? "Custom Overlay" : m.customText};
        } else if ("notebook".equals(id)) {
            raw = new String[]{m.customText == null || m.customText.isEmpty() ? "Notes" : m.customText};
        } else {
            raw = new String[]{m.label != null ? m.label : id};
        }

        String pre = m.prefix == null ? "" : m.prefix;
        String suf = m.suffix == null ? "" : m.suffix;
        String[] out = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String line = pre + (raw[i] == null ? "" : raw[i]) + suf;
            out[i] = applyBrackets(line, m.bracketStyle);
        }
        return out;
    }

    private static String applyBrackets(String s, String style) {
        if ("square".equalsIgnoreCase(style)) return "[" + s + "]";
        if ("round".equalsIgnoreCase(style)) return "(" + s + ")";
        if ("angle".equalsIgnoreCase(style)) return "<" + s + ">";
        if ("curly".equalsIgnoreCase(style)) return "{" + s + "}";
        return s;
    }

    private static String getClockString(boolean use24h, boolean showSec) {
        long sec = System.currentTimeMillis() / 1000L;
        if (sec != lastClockSec || use24h != lastClock24 || showSec != lastClockSecFlag) {
            lastClockSec = sec;
            lastClock24 = use24h;
            lastClockSecFlag = showSec;
            SimpleDateFormat fmt = use24h ? (showSec ? CLOCK_24S : CLOCK_24) : (showSec ? CLOCK_12S : CLOCK_12);
            cachedClockStr = fmt.format(new Date(sec * 1000L));
        }
        return cachedClockStr;
    }

    private static String formatDuration(long totalSec) {
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        if (h > 0L) return String.format("%d:%02d:%02d", Long.valueOf(h), Long.valueOf(m), Long.valueOf(s));
        return String.format("%02d:%02d", Long.valueOf(m), Long.valueOf(s));
    }

    private static boolean isSlimeChunk(int cx, int cz) {
        long seed = ((long) cx * cx * 0x4c1906L) + ((long) cx * 0x5ac0dbL)
            + ((long) cz * cz * 0x4307a7L) + ((long) cz * 0x5f24fL) ^ 0x3ad8025fL;
        return new Random(seed).nextInt(10) == 0;
    }

    private static int textWidthSpaced(UiBridge.UiDraw g, String s, int spacing) {
        if (s == null || s.isEmpty()) return 0;
        if (spacing <= 0) return g.textWidth(s);
        return g.textWidth(s) + (s.length() - 1) * spacing;
    }

    private static void drawTextSpaced(UiBridge.UiDraw g, String s, int x, int y, int col, boolean shadow, int spacing) {
        if (s == null || s.isEmpty()) return;
        if (spacing <= 0) {
            g.text(s, x, y, col, shadow);
            return;
        }
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            g.text(ch, cx, y, col, shadow);
            cx += g.textWidth(ch) + spacing;
        }
    }
}
