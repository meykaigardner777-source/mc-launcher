package dev.aetheria.client.common;

public final class UiBridge {
    private UiBridge() {}

    public interface UiDraw {
        void fill(int x1, int y1, int x2, int y2, int argb);
        void text(String txt, int x, int y, int argb, boolean shadow);
        int textWidth(String txt);
        void pushTransform(float tx, float ty, float scale);
        void popTransform();

        default void drawBorder(int x, int y, int w, int h, int col) {
            fill(x, y, x + w, y + 1, col);
            fill(x, y + h - 1, x + w, y + h, col);
            fill(x, y + 1, x + 1, y + h - 1, col);
            fill(x + w - 1, y + 1, x + w, y + h - 1, col);
        }

        default void drawRoundedRect(int x, int y, int w, int h, int r, int argb) {
            if (w <= 0 || h <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
            if (r <= 1) { fill(x, y, x + w, y + h, argb); return; }
            for (int dy = 0; dy < r; dy++) {
                double py = r - dy - 0.5;
                int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - py * py)));
                fill(x + inset, y + dy, x + w - inset, y + dy + 1, argb);
                fill(x + inset, y + h - 1 - dy, x + w - inset, y + h - dy, argb);
            }
            if (h > r * 2) fill(x, y + r, x + w, y + h - r, argb);
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int r, int argb) {
            drawRoundedOutline(x, y, w, h, r, 1, "solid", argb, argb);
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int r, int thickness, String style, int argb, int argb2) {
            if (w <= 1 || h <= 1 || thickness <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            int t = Math.max(1, Math.min(5, thickness));
            r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
            boolean dashed = "dashed".equalsIgnoreCase(style);
            boolean grad = "gradient".equalsIgnoreCase(style);

            if (r <= 1) {
                for (int px = 0; px < w; px++) {
                    if (dashed && ((px / 4) % 2 == 1)) continue;
                    int col = grad ? ClientState.lerpColor(argb, argb2, px / (float) Math.max(1, w - 1)) : argb;
                    fill(x + px, y, x + px + 1, y + t, col);
                    fill(x + px, y + h - t, x + px + 1, y + h, col);
                }
                for (int py = t; py < h - t; py++) {
                    if (dashed && ((py / 4) % 2 == 1)) continue;
                    int colL = grad ? argb : argb;
                    int colR = grad ? argb2 : argb;
                    fill(x, y + py, x + t, y + py + 1, colL);
                    fill(x + w - t, y + py, x + w, y + py + 1, colR);
                }
                return;
            }

            // Straight segments
            for (int px = r; px < w - r; px++) {
                if (dashed && ((px / 4) % 2 == 1)) continue;
                int col = grad ? ClientState.lerpColor(argb, argb2, px / (float) Math.max(1, w - 1)) : argb;
                fill(x + px, y, x + px + 1, y + t, col);
                fill(x + px, y + h - t, x + px + 1, y + h, col);
            }
            for (int py = r; py < h - r; py++) {
                if (dashed && ((py / 4) % 2 == 1)) continue;
                fill(x, y + py, x + t, y + py + 1, argb);
                fill(x + w - t, y + py, x + w, y + py + 1, grad ? argb2 : argb);
            }
            // Rounded corners
            for (int dy = 0; dy < r; dy++) {
                double py = r - dy - 0.5;
                int outerIn = (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - py * py)));
                double innerR = Math.max(0.0, r - t);
                int innerIn = dy >= t ? (int) Math.round(r - Math.sqrt(Math.max(0.0, innerR * innerR - py * py))) : r;
                int span = Math.max(1, innerIn - outerIn);
                int colR = grad ? argb2 : argb;
                fill(x + outerIn, y + dy, x + outerIn + span, y + dy + 1, argb);
                fill(x + w - outerIn - span, y + dy, x + w - outerIn, y + dy + 1, colR);
                fill(x + outerIn, y + h - 1 - dy, x + outerIn + span, y + h - dy, argb);
                fill(x + w - outerIn - span, y + h - 1 - dy, x + w - outerIn, y + h - dy, colR);
            }
        }

        default void drawGlassCard(int x, int y, int w, int h, int radius, int bgArgb, int borderArgb) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (((borderArgb >>> 24) & 0xFF) > 0) drawRoundedOutline(x, y, w, h, radius, borderArgb);
        }

        default void drawGlassCard(int x, int y, int w, int h, int radius, int borderThick, String borderStyle, int bgArgb, int borderArgb, int borderArgb2) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (borderThick > 0 && ((borderArgb >>> 24) & 0xFF) > 0) {
                drawRoundedOutline(x, y, w, h, radius, borderThick, borderStyle, borderArgb, borderArgb2);
            }
        }

        default void drawSoftShadow(int x, int y, int w, int h, int radius) {
            drawRoundedRect(x - 2, y - 1, w + 4, h + 4, radius + 2, 0x1A000000);
        }

        default void drawSoftShadow(int x, int y, int w, int h, int radius, int offX, int offY, int blur, int col) {
            int b = Math.max(1, Math.min(8, blur));
            int baseAlpha = (col >>> 24) & 0xFF;
            if (baseAlpha == 0) return;
            for (int i = b; i >= 1; i--) {
                int a = Math.max(4, (baseAlpha * (b - i + 1)) / (b * (b + 1) / 2 + 1));
                drawRoundedRect(x + offX - i, y + offY - i, w + i * 2, h + i * 2, radius + i, ClientState.withAlpha(col, a));
            }
        }

        default void drawCircleFilled(int cx, int cy, int r, int argb) {
            drawRoundedRect(cx - r, cy - r, r * 2, r * 2, r, argb);
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