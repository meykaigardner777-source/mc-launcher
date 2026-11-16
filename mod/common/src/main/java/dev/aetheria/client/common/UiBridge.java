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
            if (r == 2) {
                fill(x + 1, y, x + w - 1, y + 1, argb);
                fill(x, y + 1, x + w, y + h - 1, argb);
                fill(x + 1, y + h - 1, x + w - 1, y + h, argb);
                return;
            }
            // Fast 0-lag 3-slice rounded box (Level 3-12 supported instantly)
            fill(x + 2, y, x + w - 2, y + 1, argb);
            fill(x + 1, y + 1, x + w - 1, y + 2, argb);
            fill(x, y + 2, x + w, y + h - 2, argb);
            fill(x + 1, y + h - 2, x + w - 1, y + h - 1, argb);
            fill(x + 2, y + h - 1, x + w - 2, y + h, argb);
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int r, int argb) {
            if (w <= 1 || h <= 1 || ((argb >>> 24) & 0xFF) == 0) return;
            r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
            if (r <= 1) { drawBorder(x, y, w, h, argb); return; }
            fill(x + 2, y, x + w - 2, y + 1, argb);
            fill(x + 2, y + h - 1, x + w - 2, y + h, argb);
            fill(x, y + 2, x + 1, y + h - 2, argb);
            fill(x + w - 1, y + 2, x + w, y + h - 2, argb);
            fill(x + 1, y + 1, x + 2, y + 2, argb);
            fill(x + w - 2, y + 1, x + w - 1, y + 2, argb);
            fill(x + 1, y + h - 2, x + 2, y + h - 1, argb);
            fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, argb);
        }

        // Clean flat Obsidian glass card — NO reflective sheen lines
        default void drawGlassCard(int x, int y, int w, int h, int radius, int bgArgb, int borderArgb) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (((borderArgb >>> 24) & 0xFF) > 0) drawRoundedOutline(x, y, w, h, radius, borderArgb);
        }

        default void drawSoftShadow(int x, int y, int w, int h, int radius) {
            drawRoundedRect(x - 2, y - 1, w + 4, h + 4, radius + 2, 0x1A000000);
        }

        default void drawCircleFilled(int cx, int cy, int r, int argb) {
            drawRoundedRect(cx - r, cy - r, r * 2, r * 2, r, argb);
        }

        default void drawCircle(int cx, int cy, int r, int t, int col) {
            drawRoundedOutline(cx - r, cy - r, r * 2, r * 2, r, col);
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
    }

    public interface HudData {
        boolean hasWorld(); int fps(); int x(); int y(); int z();
        String facing(); String biome(); int pingMs(); boolean isSprinting();
        String[] activePotions(); float yaw(); String serverIp(); long dayCount();
        String heldToolDurability(); String armorStatus();
        boolean isMoveW(); boolean isMoveA(); boolean isMoveS(); boolean isMoveD(); boolean isJumpDown();
        boolean targetPlayerInView(double range); boolean targetEntityInView(double range);
    }
}