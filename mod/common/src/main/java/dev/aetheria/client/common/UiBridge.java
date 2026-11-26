package dev.aetheria.client.common;

public final class UiBridge {
    private UiBridge() {}

    public interface UiDraw {
        void fill(int x1, int y1, int x2, int y2, int argb);
        void text(String txt, int x, int y, int argb, boolean shadow);
        int textWidth(String txt);
        void pushTransform(float tx, float ty, float scale);
        void popTransform();

        default void playClickSound() {}
        default String currentUsername() { return "Player"; }
        default String currentUuid() { return ""; }
        default boolean switchAccount(String username, String uuid) { return false; }
        default boolean switchAccount(String username, String uuid, String accessToken, String accountType) { return switchAccount(username, uuid); }
        default String currentAccountType() { return "offline"; }
        default int liveFps() { return 0; }
        default String lastServerIp() { return "Singleplayer"; }
        default void openUrl(String url) {}
        default String clipboardText() { return ""; }
        default void drawEntity(int x, int y, int size, float mouseX, float mouseY) {}
        default void drawPaperdoll(int x1, int y1, int x2, int y2, int size, float mouseX, float mouseY) {
            pushTransform(x1, y1, 1.0F); drawEntity((x2 - x1) / 2, Math.max(12, y2 - y1 - 8), size, mouseX, mouseY); popTransform();
        }
        default boolean supportsItemIcons() { return false; }
        default void drawArmorSlot(int slotIndex, int x, int y) {}

        default void drawBorder(int x, int y, int w, int h, int col) {
            fill(x, y, x + w, y + 1, col); fill(x, y + h - 1, x + w, y + h, col);
            fill(x, y + 1, x + 1, y + h - 1, col); fill(x + w - 1, y + 1, x + w, y + h - 1, col);
        }

        default void drawRoundedRect(int x, int y, int w, int h, int r, int argb) {
            if (w <= 0 || h <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
            if (r <= 1) { fill(x, y, x + w, y + h, argb); return; }
            fill(x + r, y, x + w - r, y + h, argb); fill(x, y + r, x + r, y + h - r, argb); fill(x + w - r, y + r, x + w, y + h - r, argb);
            for (int dy = 0; dy < r; dy++) {
                int span = (int) Math.round(Math.sqrt(Math.max(0.0, r * r - Math.pow(r - dy - 0.5, 2))));
                fill(x + r - span, y + dy, x + r, y + dy + 1, argb); fill(x + w - r, y + dy, x + w - r + span, y + dy + 1, argb);
                fill(x + r - span, y + h - 1 - dy, x + r, y + h - dy, argb); fill(x + w - r, y + h - 1 - dy, x + w - r + span, y + h - dy, argb);
            }
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int r, int argb) { drawRoundedOutline(x, y, w, h, r, 1, "solid", argb, argb); }
        default void drawRoundedOutline(int x, int y, int w, int h, int r, int thickness, String style, int argb, int argb2) {
            if (w <= 1 || h <= 1 || thickness <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            int t = Math.max(1, Math.min(3, thickness)); r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
            fill(x + r, y, x + w - r, y + t, argb); fill(x + r, y + h - t, x + w - r, y + h, argb2);
            fill(x, y + r, x + t, y + h - r, argb); fill(x + w - t, y + r, x + w, y + h - r, argb2);
        }

        default void drawGlassCard(int x, int y, int w, int h, int radius, int bgArgb, int borderArgb) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (((borderArgb >>> 24) & 0xFF) > 0) drawRoundedOutline(x, y, w, h, radius, 1, "solid", borderArgb, borderArgb);
        }

        default void drawGlassCard(int x, int y, int w, int h, int radius, int borderThick, String borderStyle, int bgArgb, int borderArgb, int borderArgb2) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (borderThick > 0 && ((borderArgb >>> 24) & 0xFF) > 0) drawRoundedOutline(x, y, w, h, radius, borderThick, borderStyle, borderArgb, borderArgb2);
        }

        default void drawSoftShadow(int x, int y, int w, int h, int radius) {}
        default void drawSoftShadow(int x, int y, int w, int h, int radius, int offX, int offY, int blur, int col) {}
        default void drawCircleFilled(int cx, int cy, int r, int argb) { drawRoundedRect(cx - r, cy - r, r * 2, r * 2, r, argb); }
        default void drawCircle(int cx, int cy, int r, int t, int col) { drawRoundedOutline(cx - r, cy - r, r * 2, r * 2, r, t, "solid", col, col); }

        default void drawLine(int x0, int y0, int x1, int y1, int t, int col) {
            if (x0 == x1) fill(x0, Math.min(y0, y1), x0 + t, Math.max(y0, y1) + 1, col);
            else if (y0 == y1) fill(Math.min(x0, x1), y0, Math.max(x0, x1) + 1, y0 + t, col);
            else {
                int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
                for (int i = 0; i <= steps; i++) {
                    int px = x0 + ((x1 - x0) * i) / steps, py = y0 + ((y1 - y0) * i) / steps;
                    fill(px, py, px + t, py + t, col);
                }
            }
        }

        default void drawSparkline(int x, int y, int w, int h, float[] vals, int count, float maxVal, int lineCol, int areaCol) {
            if (vals == null || count <= 1 || w <= 2 || h <= 2) return;
            float ceil = Math.max(1.0F, maxVal); int prevX = x, prevY = y + h - 1;
            for (int i = 0; i < count; i++) {
                float norm = Math.max(0.0F, Math.min(1.0F, vals[i] / ceil));
                int px = x + (i * (w - 1)) / Math.max(1, count - 1), py = y + h - 1 - Math.round(norm * (h - 2));
                if (((areaCol >>> 24) & 0xFF) > 0) fill(px, py, px + Math.max(1, w / count), y + h, areaCol);
                if (i > 0) drawLine(prevX, prevY, px, py, 1, lineCol);
                prevX = px; prevY = py;
            }
        }

        default void drawProgressBar(int x, int y, int w, int h, int r, float pct, int bgCol, int fgCol) {
            drawRoundedRect(x, y, w, h, r, bgCol);
            int fw = Math.round(w * Math.max(0.0F, Math.min(1.0F, pct)));
            if (fw >= 2) drawRoundedRect(x, y, fw, h, Math.min(r, fw / 2), fgCol);
        }

        default void drawProgressRing(int cx, int cy, int r, float pct, int bgCol, int fgCol) {
            int segs = 20, activeSegs = Math.round(segs * Math.max(0.0F, Math.min(1.0F, pct)));
            for (int i = 0; i < segs; i++) {
                double a = (i / (double) segs) * Math.PI * 2.0 - Math.PI / 2.0;
                fill(cx + (int) Math.round(Math.cos(a) * r) - 1, cy + (int) Math.round(Math.sin(a) * r) - 1,
                     cx + (int) Math.round(Math.cos(a) * r) + 1, cy + (int) Math.round(Math.sin(a) * r) + 1, i < activeSegs ? fgCol : bgCol);
            }
        }
    }

    public interface HudData {
        boolean hasWorld(); int fps(); int x(); int y(); int z();
        String facing(); String biome(); int pingMs(); boolean isSprinting();
        String[] activePotions(); float yaw(); String serverIp(); long dayCount();
        String heldToolDurability(); String armorStatus();
        boolean isMoveW(); boolean isMoveA(); boolean isMoveS(); boolean isMoveD(); boolean isJumpDown();
        boolean targetPlayerInView(double range); boolean targetEntityInView(double range);

        default String dimensionId() { return "minecraft:overworld"; }
        default String worldKey() { return isSingleplayer() ? "sp:local" : ("srv:" + (serverIp() == null || serverIp().isEmpty() ? "local" : serverIp().toLowerCase())); }
        default boolean isSingleplayer() { return false; }
        default boolean showSurvivalBars() { return true; }
        default String[] armorSlotReadouts() { return new String[0]; }
        default float pitch() { return 0.0F; }
        default boolean isSneaking() { return false; }
        default boolean isMoveCtrl() { return false; }
        default double posX() { return x(); } default double posY() { return y(); } default double posZ() { return z(); }
        default double camX() { return posX(); } default double camY() { return posY() + 1.62; } default double camZ() { return posZ(); }
        default float camYaw() { return yaw(); } default float camPitch() { return pitch(); }
        default String scoreboardTitle() { return ""; } default String[] scoreboardLines() { return new String[0]; }
        default int blockLight() { return 15; } default int skyLight() { return 15; }
        default long worldTime() { return 6000L; } default int moonPhase() { return 0; } default String weather() { return "Clear"; }
        default float health() { return 20.0F; } default float maxHealth() { return 20.0F; } default float absorption() { return 0.0F; }
        default int foodLevel() { return 20; } default float saturation() { return 5.0F; } default float exhaustion() { return 0.0F; }
        default int airSupply() { return 300; } default int maxAirSupply() { return 300; }
        default int onlinePlayers() { return 1; } default int maxPlayers() { return 20; }
        default int renderedEntities() { return 0; } default int totalEntities() { return 0; }
        default String lookingAtBlock() { return "Air"; } default String lookingAtEntity() { return "None"; }
        default String targetName() { return ""; } default float targetHealth() { return 0.0F; } default float targetMaxHealth() { return 20.0F; } default double targetDistance() { return 0.0; }
        default int countItem(String keyword) { return 0; } default int freeSlots() { return 36; } default int usedSlots() { return 0; }
        default String offhandDurability() { return "Off: --"; } default int armorReductionPct() { return 0; } default int armorEpf() { return 0; }
        default float attackCooldownPct() { return 1.0F; } default boolean isBlockingOrCooling() { return false; }
        default double fovDegrees() { return 70.0; } default double gammaPercent() { return 100.0; }
        default boolean isF1Hidden() { return false; } default boolean isChatOpen() { return false; }
        default boolean isInventoryOpen() { return false; } default boolean isF3Open() { return false; }
        default String resourcePacks() { return "Default"; } default int[] nearbyEntityRadar() { return new int[0]; } default String mountStats() { return "Not Riding"; }
    }
}