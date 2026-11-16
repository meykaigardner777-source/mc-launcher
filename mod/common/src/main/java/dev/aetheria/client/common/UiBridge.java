package dev.aetheria.client.common;

public final class UiBridge {
    private UiBridge() {}

    // Precomputed unit-circle trig table for 16-segment quarter circles (true round arcs)
    private static final float[] COS_LUT = new float[17];
    private static final float[] SIN_LUT = new float[17];
    static {
        for (int i = 0; i <= 16; i++) {
            double rad = (i / 16.0) * (Math.PI / 2.0);
            COS_LUT[i] = (float) Math.cos(rad);
            SIN_LUT[i] = (float) Math.sin(rad);
        }
    }

    public interface UiDraw {
        void fill(int x1, int y1, int x2, int y2, int argb);
        void text(String txt, int x, int y, int argb, boolean shadow);
        int textWidth(String txt);
        void pushTransform(float tx, float ty, float scale);
        void popTransform();

        // Optional high-quality triangle fan arc. Default implementation renders smooth geometric trapezoid strips.
        default void drawCornerSector(float cx, float cy, float r, int quad, int argb) {
            if (r <= 0.5f) return;
            // 4 quadrants: 0 = Top-Right, 1 = Bottom-Right, 2 = Bottom-Left, 3 = Top-Left
            float sx = (quad == 0 || quad == 1) ? 1.0f : -1.0f;
            float sy = (quad == 1 || quad == 2) ? 1.0f : -1.0f;
            for (int i = 0; i < 16; i++) {
                float x1 = cx + sx * COS_LUT[i] * r;
                float y1 = cy + sy * SIN_LUT[i] * r;
                float x2 = cx + sx * COS_LUT[i + 1] * r;
                float y2 = cy + sy * SIN_LUT[i + 1] * r;
                int minX = (int) Math.floor(Math.min(cx, Math.min(x1, x2)));
                int maxX = (int) Math.ceil(Math.max(cx, Math.max(x1, x2)));
                int minY = (int) Math.floor(Math.min(cy, Math.min(y1, y2)));
                int maxY = (int) Math.ceil(Math.max(cy, Math.max(y1, y2)));
                fill(minX, minY, maxX, maxY, argb);
            }
        }

        default void drawBorder(int x, int y, int w, int h, int col) {
            fill(x, y, x + w, y + 1, col);
            fill(x, y + h - 1, x + w, y + h, col);
            fill(x, y + 1, x + 1, y + h - 1, col);
            fill(x + w - 1, y + 1, x + w, y + h - 1, col);
        }

        // True smooth rounded rectangle
        default void drawRoundedRect(int x, int y, int w, int h, int r, int argb) {
            if (w <= 0 || h <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
            if (r <= 1) { fill(x, y, x + w, y + h, argb); return; }

            // Core body quads
            fill(x + r, y, x + w - r, y + h, argb);
            fill(x, y + r, x + r, y + h - r, argb);
            fill(x + w - r, y + r, x + w, y + h - r, argb);

            // True smooth circular corners
            drawCornerSector(x + w - r, y + r, r, 0, argb);
            drawCornerSector(x + w - r, y + h - r, r, 1, argb);
            drawCornerSector(x + r, y + h - r, r, 2, argb);
            drawCornerSector(x + r, y + r, r, 3, argb);
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int r, int argb) {
            drawRoundedOutline(x, y, w, h, r, 1, "solid", argb, argb);
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int r, int thickness, String style, int argb, int argb2) {
            if (w <= 1 || h <= 1 || thickness <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            int t = Math.max(1, Math.min(3, thickness));
            r = Math.max(0, Math.min(r, Math.min(w, h) / 2));

            if (r <= 1) {
                fill(x, y, x + w, y + t, argb);
                fill(x, y + h - t, x + w, y + h, argb2);
                fill(x, y + t, x + t, y + h - t, argb);
                fill(x + w - t, y + t, x + w, y + h - t, argb2);
                return;
            }

            // Clean 4 flat outer borders
            fill(x + r, y, x + w - r, y + t, argb);
            fill(x + r, y + h - t, x + w - r, y + h, argb2);
            fill(x, y + r, x + t, y + h - r, argb);
            fill(x + w - t, y + r, x + w, y + h - r, argb2);

            // Curved outline corners
            for (int i = 0; i < 16; i++) {
                float c1 = COS_LUT[i], s1 = SIN_LUT[i];
                float c2 = COS_LUT[i + 1], s2 = SIN_LUT[i + 1];
                // TR
                fill((int)(x + w - r + c1 * (r - t)), (int)(y + r - s1 * r), (int)(x + w - r + c2 * r + 1), (int)(y + r - s2 * (r - t) + 1), argb);
                // BR
                fill((int)(x + w - r + c1 * (r - t)), (int)(y + h - r + s1 * (r - t)), (int)(x + w - r + c2 * r + 1), (int)(y + h - r + s2 * r + 1), argb2);
                // BL
                fill((int)(x + r - c1 * r), (int)(y + h - r + s1 * (r - t)), (int)(x + r - c2 * (r - t) + 1), (int)(y + h - r + s2 * r + 1), argb2);
                // TL
                fill((int)(x + r - c1 * r), (int)(y + r - s1 * r), (int)(x + r - c2 * (r - t) + 1), (int)(y + r - s2 * (r - t) + 1), argb);
            }
        }

        // Clean Modern Flat Card (no weird reflections or muddy gradients)
        default void drawGlassCard(int x, int y, int w, int h, int radius, int bgArgb, int borderArgb) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (((borderArgb >>> 24) & 0xFF) > 0) {
                drawRoundedOutline(x, y, w, h, radius, 1, "solid", borderArgb, borderArgb);
            }
        }

        default void drawGlassCard(int x, int y, int w, int h, int radius, int borderThick, String borderStyle, int bgArgb, int borderArgb, int borderArgb2) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (borderThick > 0 && ((borderArgb >>> 24) & 0xFF) > 0) {
                drawRoundedOutline(x, y, w, h, radius, borderThick, borderStyle, borderArgb, borderArgb2);
            }
        }

        // Subdued, clean drop shadow (no messy black blobs)
        default void drawSoftShadow(int x, int y, int w, int h, int radius) {
            fill(x + 1, y + h, x + w - 1, y + h + 2, 0x14000000);
        }

        default void drawSoftShadow(int x, int y, int w, int h, int radius, int offX, int offY, int blur, int col) {
            int a = (col >>> 24) & 0xFF;
            if (a == 0) return;
            drawRoundedRect(x + offX, y + offY, w, h, radius, ClientState.withAlpha(col, Math.min(a, 24)));
        }

        // True geometric circles
        default void drawCircleFilled(int cx, int cy, int r, int argb) {
            drawCornerSector(cx, cy, r, 0, argb);
            drawCornerSector(cx, cy, r, 1, argb);
            drawCornerSector(cx, cy, r, 2, argb);
            drawCornerSector(cx, cy, r, 3, argb);
        }

        default void drawCircle(int cx, int cy, int r, int t, int col) {
            drawRoundedOutline(cx - r, cy - r, r * 2, r * 2, r, t, "solid", col, col);
        }

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
            float ceil = Math.max(1.0F, maxVal);
            int prevX = x, prevY = y + h - 1;
            for (int i = 0; i < count; i++) {
                float norm = Math.max(0.0F, Math.min(1.0F, vals[i] / ceil));
                int px = x + (i * (w - 1)) / Math.max(1, count - 1);
                int py = y + h - 1 - Math.round(norm * (h - 2));
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
            int segs = 24;
            int activeSegs = Math.round(segs * Math.max(0.0F, Math.min(1.0F, pct)));
            for (int i = 0; i < segs; i++) {
                double a = (i / (double) segs) * Math.PI * 2.0 - Math.PI / 2.0;
                int px = cx + (int) Math.round(Math.cos(a) * r);
                int py = cy + (int) Math.round(Math.sin(a) * r);
                fill(px - 1, py - 1, px + 1, py + 1, i < activeSegs ? fgCol : bgCol);
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

        default float pitch() { return 0.0F; }
        default boolean isSneaking() { return false; }
        default boolean isMoveCtrl() { return false; }
        default double posX() { return x(); }
        default double posY() { return y(); }
        default double posZ() { return z(); }
        default int blockLight() { return 15; }
        default int skyLight() { return 15; }
        default long worldTime() { return 6000L; }
        default int moonPhase() { return 0; }
        default String weather() { return "Clear"; }
        default float health() { return 20.0F; }
        default float maxHealth() { return 20.0F; }
        default float absorption() { return 0.0F; }
        default int foodLevel() { return 20; }
        default float saturation() { return 5.0F; }
        default float exhaustion() { return 0.0F; }
        default int airSupply() { return 300; }
        default int maxAirSupply() { return 300; }
        default int onlinePlayers() { return 1; }
        default int maxPlayers() { return 20; }
        default int renderedEntities() { return 0; }
        default int totalEntities() { return 0; }
        default String lookingAtBlock() { return "Air"; }
        default String lookingAtEntity() { return "None"; }
        default String targetName() { return ""; }
        default float targetHealth() { return 0.0F; }
        default float targetMaxHealth() { return 20.0F; }
        default double targetDistance() { return 0.0; }
        default int countItem(String keyword) { return 0; }
        default int freeSlots() { return 36; }
        default int usedSlots() { return 0; }
        default String offhandDurability() { return "Offhand: --"; }
        default int armorReductionPct() { return 0; }
        default int armorEpf() { return 0; }
        default float attackCooldownPct() { return 1.0F; }
        default boolean isBlockingOrCooling() { return false; }
        default double fovDegrees() { return 70.0; }
        default double gammaPercent() { return 100.0; }
        default boolean isF1Hidden() { return false; }
        default boolean isChatOpen() { return false; }
        default boolean isInventoryOpen() { return false; }
        default boolean isF3Open() { return false; }
        default String resourcePacks() { return "Default"; }
        default int[] nearbyEntityRadar() { return new int[0]; }
        default String mountStats() { return "Not Riding"; }
    }
}
