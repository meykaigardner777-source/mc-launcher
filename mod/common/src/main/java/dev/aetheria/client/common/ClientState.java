package dev.aetheria.client.common;

import java.util.*;

public final class ClientState {
    public static final long START_TIME_MS = System.currentTimeMillis();

    public static final int UI_BACKDROP    = 0x7A000000;
    public static final int UI_PANEL       = 0xDC09090B;
    public static final int UI_TITLEBAR    = 0xCC121215;
    public static final int UI_CARD        = 0xA8121215;
    public static final int UI_CARD_HOVER  = 0xCE18181D;
    public static final int UI_INPUT       = 0x99121215;
    public static final int UI_BORDER      = 0xFF27272A;
    public static final int UI_BORDER_HOV  = 0xFF3F3F46;
    public static final int UI_TEXT        = 0xFFF4F4F5;
    public static final int UI_TEXT_MUTED  = 0xFFA1A1AA;
    public static final int UI_TEXT_DIM    = 0xFF71717A;
    public static final int UI_DANGER      = 0xFFEF4444;

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<Long>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<Long>();
    private static volatile boolean leftDown = false, rightDown = false, zooming = false;
    private static float currentZoomDivisor = 1.0f;
    private static long lastZoomFrameNs = 0L, lastHitTimeMs = 0L;
    private static int comboCount = 0;
    private static double lastReachBlocks = 0.0;

    private ClientState() {}

    public static int getAccentColor() {
        String theme = ClientConfig.get().getString("ui_accent", "white");
        if ("cyan".equals(theme)) return 0xFF22D3EE;
        if ("emerald".equals(theme)) return 0xFF10B981;
        if ("amethyst".equals(theme)) return 0xFFA855F7;
        if ("crimson".equals(theme)) return 0xFFF43F5E;
        if ("amber".equals(theme)) return 0xFFF59E0B;
        if ("lime".equals(theme)) return 0xFF84CC16;
        return 0xFFFFFFFF;
    }

    public static int getAccentFg() {
        String theme = ClientConfig.get().getString("ui_accent", "white");
        if ("amethyst".equals(theme) || "crimson".equals(theme)) return 0xFFFFFFFF;
        return 0xFF09090B;
    }

    public static int withAlpha(int rgbOrArgb, int alpha255) { return ((alpha255 & 0xFF) << 24) | (rgbOrArgb & 0x00FFFFFF); }

    public static int scaleAlpha(int argb, double factor) {
        int a = (int) Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0, Math.min(1.0, factor)));
        return ((a & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
             | (Math.round(ar + (br - ar) * t) << 16)
             | (Math.round(ag + (bg - ag) * t) << 8)
             | Math.round(ab + (bb - ab) * t);
    }

    public static synchronized void setMouseButton(boolean isLeft, boolean down, boolean inWorld) {
        if (isLeft) { if (down && !leftDown && inWorld) LEFT_CLICKS.addLast(Long.valueOf(System.currentTimeMillis())); leftDown = down; }
        else { if (down && !rightDown && inWorld) RIGHT_CLICKS.addLast(Long.valueOf(System.currentTimeMillis())); rightDown = down; }
    }

    public static boolean isLeftDown() { return leftDown; }
    public static boolean isRightDown() { return rightDown; }

    public static synchronized int getLeftCps() {
        long now = System.currentTimeMillis();
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst().longValue() > 1000L) LEFT_CLICKS.removeFirst();
        return LEFT_CLICKS.size();
    }

    public static synchronized int getRightCps() {
        long now = System.currentTimeMillis();
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst().longValue() > 1000L) RIGHT_CLICKS.removeFirst();
        return RIGHT_CLICKS.size();
    }

    public static void setZooming(boolean active) { zooming = active; }
    public static boolean isZooming() { return zooming; }

    public static float getSmoothedZoomDivisor() {
        ClientConfig cfg = ClientConfig.get();
        float target = zooming ? (float) cfg.getZoomFactor() : 1.0f;
        long now = System.nanoTime();
        float dt = lastZoomFrameNs == 0L ? 0.016f : Math.min(0.1f, (now - lastZoomFrameNs) / 1_000_000_000.0f);
        lastZoomFrameNs = now;
        if (!cfg.getBool("cam_zoom_ease", true)) { currentZoomDivisor = target; return currentZoomDivisor; }
        currentZoomDivisor += (target - currentZoomDivisor) * Math.min(1.0f, dt * 18.0f);
        if (Math.abs(target - currentZoomDivisor) < 0.01f) currentZoomDivisor = target;
        return Math.max(1.0f, currentZoomDivisor);
    }

    public static boolean shouldSlowMouse() { return zooming && ClientConfig.get().getBool("cam_zoom_slow", true); }
    public static boolean shouldCancelVanillaCrosshair() { return !"vanilla".equalsIgnoreCase(ClientConfig.get().getString("pvp_xhair_style", "classic")); }

    public static void recordAttack(double distance) {
        long now = System.currentTimeMillis();
        comboCount = (now - lastHitTimeMs < 4000L) ? comboCount + 1 : 1;
        lastHitTimeMs = now;
        lastReachBlocks = distance;
    }

    public static void tickComboTimeout() { if (comboCount > 0 && System.currentTimeMillis() - lastHitTimeMs > 4000L) comboCount = 0; }
    public static int getComboCount() { return comboCount; }
    public static double getLastReach() { return lastReachBlocks; }

    public static int brightenArgb(int argb, int delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.max(0, ((argb >>> 16) & 0xFF) + delta));
        int g = Math.min(255, Math.max(0, ((argb >>> 8) & 0xFF) + delta));
        int b = Math.min(255, Math.max(0, (argb & 0xFF) + delta));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int parseHexColor(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try { return 0xFF000000 | Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16); }
        catch (Exception e) { return fallback; }
    }

    public static String toHexColor(int argb) { return String.format("#%06x", Integer.valueOf(argb & 0x00FFFFFF)); }

    public static int hsbToRgb(float hue, float sat, float bri) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f, f = h - (float) Math.floor(h);
        float p = bri * (1.0f - sat), q = bri * (1.0f - sat * f), t = bri * (1.0f - sat * (1.0f - f));
        float r, g, b; int s = (int) h;
        if (s == 0) { r = bri; g = t; b = p; } else if (s == 1) { r = q; g = bri; b = p; }
        else if (s == 2) { r = p; g = bri; b = t; } else if (s == 3) { r = p; g = q; b = bri; }
        else if (s == 4) { r = t; g = p; b = bri; } else { r = bri; g = p; b = q; }
        return 0xFF000000 | ((Math.round(r * 255) & 0xFF) << 16) | ((Math.round(g * 255) & 0xFF) << 8) | (Math.round(b * 255) & 0xFF);
    }
}