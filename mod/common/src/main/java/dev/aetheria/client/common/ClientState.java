package dev.aetheria.client.common;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
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
    private static volatile boolean leftDown = false, rightDown = false, zooming = false, freeLook = false;
    private static volatile float currentZoomDivisor = 1.0f, scrollZoomOffset = 0.0f;
    private static long lastZoomFrameNs = 0L, lastHitTimeMs = 0L;
    private static int comboCount = 0, swingsTotal = 0, swingsLanded = 0, killCount = 0, deathCount = 0;
    private static double lastReachBlocks = 0.0;

    private static final float[] FRAMETIMES = new float[48];
    private static int frameIdx = 0;
    private static long prevFrameNs = 0L;
    private static int minFps = 9999, maxFps = 0, onePctLowFps = 0;

    private static final float[] PING_HISTORY = new float[32];
    private static int pingIdx = 0;
    private static long lastPingSampleMs = 0L;

    private static final float[] CPS_HISTORY = new float[32];
    private static int cpsIdx = 0;
    private static long lastCpsSampleMs = 0L;

    private static long lastWorldTickSampleMs = 0L, lastWorldTickVal = -1L;
    private static double estimatedTps = 20.0, estimatedTickMs = 50.0;
    private static double prevPosX = 0, prevPosY = 0, prevPosZ = 0, speedBps = 0, speed3dBps = 0;
    private static long prevPosMs = 0L;
    private static float prevYaw = 0f, prevPitch = 0f, mouseVelX = 0f, mouseVelY = 0f;

    private static boolean swRunning = false;
    private static long swAccumMs = 0L, swStartMs = 0L;
    private static long countdownEndMs = 0L, pearlCooldownEndMs = 0L, shieldCooldownEndMs = 0L;
    private static int splitIndex = 0;
    private static long splitStartMs = START_TIME_MS;
    private static final String[] SPLIT_NAMES = {"Overworld", "Nether", "Bastion", "Fortress", "Stronghold", "End"};

    private static int lastDeathX = 0, lastDeathY = 0, lastDeathZ = 0;
    private static boolean hasDeathLoc = false, wasDead = false;
    private static int spawnX = 0, spawnY = 64, spawnZ = 0;
    private static final List<int[]> WAYPOINTS_COORDS = new ArrayList<int[]>();
    private static final List<String> WAYPOINTS_NAMES = new ArrayList<String>();
    private static final Deque<String> PICKUP_FEED = new ArrayDeque<String>();
    private static final Deque<String> CHAT_MENTIONS = new ArrayDeque<String>();

    private static final Map<String, Integer> NAME_TO_GLFW = new LinkedHashMap<String, Integer>();
    private static final Map<Integer, String> GLFW_TO_NAME = new LinkedHashMap<Integer, String>();

    static {
        WAYPOINTS_NAMES.add("Origin");
        WAYPOINTS_COORDS.add(new int[]{0, 64, 0});

        regKey("NONE", -1);
        for (char c = 'A'; c <= 'Z'; c++) regKey(String.valueOf(c), (int) c);
        for (char c = '0'; c <= '9'; c++) regKey(String.valueOf(c), (int) c);
        for (int i = 1; i <= 12; i++) regKey("F" + i, 289 + i);
        regKey("SPACE", 32); regKey("APOSTROPHE", 39); regKey("COMMA", 44); regKey("MINUS", 45);
        regKey("PERIOD", 46); regKey("SLASH", 47); regKey("SEMICOLON", 59); regKey("EQUAL", 61);
        regKey("LBRACKET", 91); regKey("BACKSLASH", 92); regKey("RBRACKET", 93); regKey("GRAVE", 96);
        regKey("TAB", 258); regKey("ENTER", 257); regKey("BACKSPACE", 259);
        regKey("INSERT", 260); regKey("DELETE", 261);
        regKey("RIGHT", 262); regKey("LEFT", 263); regKey("DOWN", 264); regKey("UP", 265);
        regKey("PAGEUP", 266); regKey("PAGEDOWN", 267); regKey("HOME", 268); regKey("END", 269);
        regKey("CAPSLOCK", 280);
        regKey("LSHIFT", 340); regKey("LCTRL", 341); regKey("LALT", 342);
        regKey("RSHIFT", 344); regKey("RCTRL", 345); regKey("RALT", 346);
        for (int i = 0; i <= 9; i++) regKey("KP" + i, 320 + i);
        // Legacy aliases
        NAME_TO_GLFW.put("ALT", Integer.valueOf(342));
        NAME_TO_GLFW.put("CTRL", Integer.valueOf(341));
        NAME_TO_GLFW.put("SHIFT", Integer.valueOf(340));
    }

    private static void regKey(String name, int code) {
        NAME_TO_GLFW.put(name, Integer.valueOf(code));
        if (code > 0) GLFW_TO_NAME.put(Integer.valueOf(code), name);
    }

    public static String glfwKeyToName(int code) {
        String s = GLFW_TO_NAME.get(Integer.valueOf(code));
        return s != null ? s : ("KEY_" + code);
    }

    public static int nameToGlfwKey(String name, int fallback) {
        if (name == null || name.isEmpty()) return fallback;
        String u = name.trim().toUpperCase();
        Integer found = NAME_TO_GLFW.get(u);
        if (found != null) return found.intValue();
        if (u.startsWith("KEY_")) {
            try { return Integer.parseInt(u.substring(4)); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private ClientState() {}

    public static int getAccentColor() {
        String theme = ClientConfig.get().getString("ui_accent", "white");
        if ("custom".equals(theme)) return parseHexColor(ClientConfig.get().getString("ui_accent_custom", "#ff3333"), 0xFFFF3333);
        if ("crimson".equals(theme)) return 0xFFFF3333;
        if ("cyan".equals(theme)) return 0xFF22D3EE;
        if ("emerald".equals(theme)) return 0xFF10B981;
        if ("amethyst".equals(theme)) return 0xFFA855F7;
        if ("amber".equals(theme)) return 0xFFF59E0B;
        if ("lime".equals(theme)) return 0xFF84CC16;
        return 0xFFFFFFFF;
    }

    public static int getAccentFg() {
        String theme = ClientConfig.get().getString("ui_accent", "white");
        if ("amethyst".equals(theme) || "crimson".equals(theme) || "custom".equals(theme)) return 0xFFFFFFFF;
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
        if (isLeft) {
            if (down && !leftDown && inWorld) {
                LEFT_CLICKS.addLast(Long.valueOf(System.currentTimeMillis()));
                swingsTotal++;
            }
            leftDown = down;
        } else {
            if (down && !rightDown && inWorld) RIGHT_CLICKS.addLast(Long.valueOf(System.currentTimeMillis()));
            rightDown = down;
        }
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

    public static void setZooming(boolean active) {
        if (!active && zooming && ClientConfig.get().getBool("cam_zoom_reset_scroll", true)) {
            scrollZoomOffset = 0.0f;
        }
        zooming = active;
    }
    public static boolean isZooming() { return zooming; }
    public static void adjustZoomScroll(double verticalDelta) {
        if (!zooming) return;
        float step = (float) (verticalDelta * Math.max(0.5, (ClientConfig.get().getZoomFactor() + scrollZoomOffset) * 0.2));
        scrollZoomOffset = Math.max(-2.5f, Math.min(20.0f, scrollZoomOffset + step));
    }
        private static volatile float freeLookYaw = 0.0f, freeLookPitch = 0.0f;
    public static void initFreeLook(float yaw, float pitch) { freeLookYaw = yaw; freeLookPitch = Math.max(-90.0f, Math.min(90.0f, pitch)); }
    public static void addFreeLookDelta(float dYaw, float dPitch) { freeLookYaw += dYaw; freeLookPitch = Math.max(-90.0f, Math.min(90.0f, freeLookPitch + dPitch)); }
    public static float getFreeLookYaw() { return freeLookYaw; }
    public static float getFreeLookPitch() { return freeLookPitch; }
    public static void setFreeLook(boolean active) { freeLook = active; }
    public static boolean isFreeLook() { return freeLook; }

    public static float getSmoothedZoomDivisor() {
        ClientConfig cfg = ClientConfig.get();
        float target = zooming ? Math.max(1.5f, Math.min(24.0f, (float) cfg.getZoomFactor() + scrollZoomOffset)) : 1.0f;
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
        swingsLanded++;
    }

    public static void tickComboTimeout() { if (comboCount > 0 && System.currentTimeMillis() - lastHitTimeMs > 4000L) comboCount = 0; }
    public static int getComboCount() { return comboCount; }
    public static double getLastReach() { return lastReachBlocks; }
    public static int getHitAccuracyPct() { return swingsTotal <= 0 ? 100 : Math.min(100, (int) Math.round((swingsLanded * 100.0) / swingsTotal)); }

    public static void recordFrameSample(int currentFps) {
        long now = System.nanoTime();
        if (prevFrameNs > 0L) {
            float ms = Math.min(100.0f, (now - prevFrameNs) / 1_000_000.0f);
            FRAMETIMES[frameIdx] = ms;
            frameIdx = (frameIdx + 1) % FRAMETIMES.length;
            float[] sorted = FRAMETIMES.clone();
            Arrays.sort(sorted);
            float highMs = Math.max(1.0f, sorted[sorted.length - 1]);
            onePctLowFps = Math.max(1, Math.round(1000.0f / highMs));
        }
        prevFrameNs = now;
        if (currentFps > 0) {
            if (currentFps < minFps) minFps = currentFps;
            if (currentFps > maxFps) maxFps = currentFps;
        }
    }

    public static float[] getFrametimeHistory() {
        float[] out = new float[FRAMETIMES.length];
        for (int i = 0; i < FRAMETIMES.length; i++) out[i] = FRAMETIMES[(frameIdx + i) % FRAMETIMES.length];
        return out;
    }

    public static int getMinFps() { return minFps == 9999 ? 0 : minFps; }
    public static int getMaxFps() { return maxFps; }
    public static int getOnePercentLowFps() { return onePctLowFps; }

    public static void sampleTelemetry(int pingMs, long worldTicks, double px, double py, double pz, float yaw, float pitch, float health) {
        long now = System.currentTimeMillis();
        if (now - lastPingSampleMs >= 400L) {
            lastPingSampleMs = now;
            PING_HISTORY[pingIdx] = Math.max(0, pingMs);
            pingIdx = (pingIdx + 1) % PING_HISTORY.length;
        }
        if (now - lastCpsSampleMs >= 150L) {
            lastCpsSampleMs = now;
            CPS_HISTORY[cpsIdx] = getLeftCps() + getRightCps();
            cpsIdx = (cpsIdx + 1) % CPS_HISTORY.length;
        }
        if (lastWorldTickVal >= 0 && now - lastWorldTickSampleMs >= 1000L) {
            long dtMs = Math.max(1L, now - lastWorldTickSampleMs);
            long dTicks = Math.max(0L, worldTicks - lastWorldTickVal);
            double tps = Math.min(20.0, (dTicks * 1000.0) / dtMs);
            if (dTicks > 0) {
                estimatedTps = estimatedTps * 0.7 + tps * 0.3;
                estimatedTickMs = dtMs / (double) Math.max(1L, dTicks);
            }
            lastWorldTickSampleMs = now;
            lastWorldTickVal = worldTicks;
        } else if (lastWorldTickVal < 0) {
            lastWorldTickSampleMs = now;
            lastWorldTickVal = worldTicks;
        }
        if (prevPosMs > 0L && now - prevPosMs >= 40L) {
            double dtSec = (now - prevPosMs) / 1000.0;
            double dx = px - prevPosX, dy = py - prevPosY, dz = pz - prevPosZ;
            speedBps = Math.hypot(dx, dz) / dtSec;
            speed3dBps = Math.sqrt(dx * dx + dy * dy + dz * dz) / dtSec;
            mouseVelX = Math.max(-1.0f, Math.min(1.0f, (yaw - prevYaw) / 25.0f));
            mouseVelY = Math.max(-1.0f, Math.min(1.0f, (pitch - prevPitch) / 25.0f));
            prevPosX = px; prevPosY = py; prevPosZ = pz; prevPosMs = now; prevYaw = yaw; prevPitch = pitch;
        } else if (prevPosMs == 0L) {
            prevPosX = px; prevPosY = py; prevPosZ = pz; prevPosMs = now; prevYaw = yaw; prevPitch = pitch;
        } else {
            mouseVelX *= 0.85f; mouseVelY *= 0.85f;
        }
        if (health <= 0.0f && !wasDead) {
            wasDead = true;
            deathCount++;
            lastDeathX = (int) Math.floor(px); lastDeathY = (int) Math.floor(py); lastDeathZ = (int) Math.floor(pz);
            hasDeathLoc = true;
        } else if (health > 0.0f) {
            wasDead = false;
        }
    }

    public static float[] getPingHistory() {
        float[] out = new float[PING_HISTORY.length];
        for (int i = 0; i < PING_HISTORY.length; i++) out[i] = PING_HISTORY[(pingIdx + i) % PING_HISTORY.length];
        return out;
    }

    public static float[] getCpsHistory() {
        float[] out = new float[CPS_HISTORY.length];
        for (int i = 0; i < CPS_HISTORY.length; i++) out[i] = CPS_HISTORY[(cpsIdx + i) % CPS_HISTORY.length];
        return out;
    }

    public static double getEstimatedTps() { return Math.max(0.0, Math.min(20.0, estimatedTps)); }
    public static double getEstimatedTickMs() { return Math.min(200.0, estimatedTickMs); }
    public static double getSpeedBps() { return speedBps; }
    public static double getSpeed3dBps() { return speed3dBps; }
    public static float getMouseVelX() { return mouseVelX; }
    public static float getMouseVelY() { return mouseVelY; }
    public static int getKills() { return killCount; }
    public static int getDeaths() { return deathCount; }
    public static void recordKill() { killCount++; }
    public static boolean hasDeathLocation() { return hasDeathLoc; }
    public static String getLastDeathCoords(int curX, int curZ) {
        if (!hasDeathLoc) return "No Deaths";
        int dist = (int) Math.round(Math.hypot(curX - lastDeathX, curZ - lastDeathZ));
        return lastDeathX + "," + lastDeathY + "," + lastDeathZ + " (" + dist + "m)";
    }
    public static void setSpawnPoint(int x, int y, int z) { spawnX = x; spawnY = y; spawnZ = z; }
    public static String getSpawnDistance(int cx, int cz) {
        int d = (int) Math.round(Math.hypot(cx - spawnX, cz - spawnZ));
        return "Spawn: " + d + "m (" + spawnX + "," + spawnZ + ")";
    }

    public static void toggleStopwatch() {
        if (swRunning) { swAccumMs += System.currentTimeMillis() - swStartMs; swRunning = false; }
        else { swStartMs = System.currentTimeMillis(); swRunning = true; }
    }
    public static void resetStopwatch() { swRunning = false; swAccumMs = 0L; }
    public static String getStopwatchText() {
        long ms = swAccumMs + (swRunning ? (System.currentTimeMillis() - swStartMs) : 0L);
        long s = ms / 1000L;
        return String.format("%02d:%02d.%d", Long.valueOf(s / 60), Long.valueOf(s % 60), Long.valueOf((ms % 1000) / 100));
    }

    public static void startCountdown(int seconds) { countdownEndMs = System.currentTimeMillis() + Math.max(1, seconds) * 1000L; }
    public static String getCountdownText() {
        if (countdownEndMs <= 0L) return "Timer: Ready";
        long rem = Math.max(0L, countdownEndMs - System.currentTimeMillis());
        if (rem == 0L) return "Timer: DONE!";
        long s = (rem + 999L) / 1000L;
        return String.format("Timer: %02d:%02d", Long.valueOf(s / 60), Long.valueOf(s % 60));
    }

    public static void triggerPearlCooldown() { pearlCooldownEndMs = System.currentTimeMillis() + 1000L; }
    public static double getPearlCooldownSec() { return Math.max(0.0, (pearlCooldownEndMs - System.currentTimeMillis()) / 1000.0); }
    public static void triggerShieldCooldown() { shieldCooldownEndMs = System.currentTimeMillis() + 5000L; }
    public static double getShieldCooldownSec() { return Math.max(0.0, (shieldCooldownEndMs - System.currentTimeMillis()) / 1000.0); }

    public static void advanceSplit() { splitIndex = (splitIndex + 1) % SPLIT_NAMES.length; }
    public static void resetSplits() { splitIndex = 0; splitStartMs = System.currentTimeMillis(); }
    public static String getSplitText() {
        long s = Math.max(0L, (System.currentTimeMillis() - splitStartMs) / 1000L);
        return SPLIT_NAMES[splitIndex] + " " + String.format("%02d:%02d", Long.valueOf(s / 60), Long.valueOf(s % 60));
    }

    public static synchronized void addWaypoint(String name, int x, int y, int z) {
        WAYPOINTS_NAMES.add(name); WAYPOINTS_COORDS.add(new int[]{x, y, z});
    }
    public static synchronized String getNearestWaypointText(int cx, int cy, int cz) {
        if (WAYPOINTS_COORDS.isEmpty()) return "No Waypoints";
        int bestIdx = 0; double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < WAYPOINTS_COORDS.size(); i++) {
            int[] c = WAYPOINTS_COORDS.get(i);
            double d = Math.sqrt(Math.pow(c[0] - cx, 2) + Math.pow(c[1] - cy, 2) + Math.pow(c[2] - cz, 2));
            if (d < bestDist) { bestDist = d; bestIdx = i; }
        }
        return WAYPOINTS_NAMES.get(bestIdx) + ": " + Math.round(bestDist) + "m";
    }

    public static synchronized void pushPickup(String txt) {
        PICKUP_FEED.addFirst(txt);
        while (PICKUP_FEED.size() > 4) PICKUP_FEED.removeLast();
    }
    public static synchronized String[] getRecentPickups() {
        return PICKUP_FEED.isEmpty() ? new String[]{"No Recent Pickups"} : PICKUP_FEED.toArray(new String[0]);
    }
    public static synchronized String[] getChatMentions() {
        return CHAT_MENTIONS.isEmpty() ? new String[]{"No New Mentions"} : CHAT_MENTIONS.toArray(new String[0]);
    }

    public static String getGcSummary() {
        long count = 0, timeMs = 0;
        try {
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                long c = gc.getCollectionCount(), t = gc.getCollectionTime();
                if (c > 0) count += c;
                if (t > 0) timeMs += t;
            }
        } catch (Throwable ignored) {}
        return "GC: " + count + " (" + timeMs + "ms)";
    }

    public static int getChromaColor(double speed, double sat, double angleDeg, double xNorm, double yNorm) {
        double rad = Math.toRadians(angleDeg);
        double offset = (xNorm * Math.cos(rad) + yNorm * Math.sin(rad)) * 0.65;
        long period = Math.max(400L, Math.round(3000.0 / Math.max(0.2, speed)));
        float hue = (float) (((System.currentTimeMillis() % period) / (double) period + offset) % 1.0);
        return hsbToRgb(hue, (float) Math.max(0.1, Math.min(1.0, sat)), 1.0f);
    }

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