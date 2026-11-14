package dev.aetheria.client.common;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ClientState {
    public static final long START_TIME_MS = System.currentTimeMillis();

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<Long>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<Long>();
    private static volatile boolean leftDown = false, rightDown = false, zooming = false;
    private static float currentZoomDivisor = 1.0f;
    private static long lastZoomFrameNs = 0L, lastHitTimeMs = 0L;
    private static int comboCount = 0;
    private static double lastReachBlocks = 0.0;

    private ClientState() {}

    public static synchronized void setMouseButton(boolean isLeft, boolean down, boolean inWorld) {
        if (isLeft) {
            if (down && !leftDown && inWorld) LEFT_CLICKS.addLast(System.currentTimeMillis());
            leftDown = down;
        } else {
            if (down && !rightDown && inWorld) RIGHT_CLICKS.addLast(System.currentTimeMillis());
            rightDown = down;
        }
    }

    public static boolean isLeftDown() { return leftDown; }
    public static boolean isRightDown() { return rightDown; }

    public static synchronized int getLeftCps() {
        long now = System.currentTimeMillis();
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000L) LEFT_CLICKS.removeFirst();
        return LEFT_CLICKS.size();
    }

    public static synchronized int getRightCps() {
        long now = System.currentTimeMillis();
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000L) RIGHT_CLICKS.removeFirst();
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