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

        default void drawRoundedRect(int x, int y, int w, int h, int radius, int argb) {
            if (w <= 0 || h <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
            int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
            if (r <= 0) { fill(x, y, x + w, y + h, argb); return; }
            int s = 4, sx = x * s, sy = y * s, sw = w * s, sh = h * s, sr = r * s;
            pushTransform(0.0F, 0.0F, 0.25F);
            if (sy + sh - sr > sy + sr) fill(sx, sy + sr, sx + sw, sy + sh - sr, argb);
            for (int row = 0; row < sr; row++) {
                double dy = sr - row - 0.5;
                double inset = sr - Math.sqrt(Math.max(0.0, (double) sr * sr - dy * dy));
                int inPx = (int) Math.floor(inset);
                double cov = 1.0 - (inset - inPx);
                int xL = sx + inPx + 1, xR = sx + sw - inPx - 1;
                if (xR > xL) {
                    fill(xL, sy + row, xR, sy + row + 1, argb);
                    fill(xL, sy + sh - row - 1, xR, sy + sh - row, argb);
                }
                int edge = ClientState.scaleAlpha(argb, cov);
                if (sx + inPx < sx + sw - inPx - 1) {
                    fill(sx + inPx, sy + row, sx + inPx + 1, sy + row + 1, edge);
                    fill(sx + sw - inPx - 1, sy + row, sx + sw - inPx, sy + row + 1, edge);
                    fill(sx + inPx, sy + sh - row - 1, sx + inPx + 1, sy + sh - row, edge);
                    fill(sx + sw - inPx - 1, sy + sh - row - 1, sx + sw - inPx, sy + sh - row, edge);
                }
            }
            popTransform();
        }

        default void drawRoundedOutline(int x, int y, int w, int h, int radius, int argb) {
            if (w <= 1 || h <= 1 || ((argb >>> 24) & 0xFF) == 0) return;
            int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
            if (r <= 0) { drawBorder(x, y, w, h, argb); return; }
            int s = 4, sx = x * s, sy = y * s, sw = w * s, sh = h * s, sr = r * s, th = 3;
            pushTransform(0.0F, 0.0F, 0.25F);
            if (sx + sw - sr > sx + sr) {
                fill(sx + sr, sy, sx + sw - sr, sy + th, argb);
                fill(sx + sr, sy + sh - th, sx + sw - sr, sy + sh, argb);
            }
            if (sy + sh - sr > sy + sr) {
                fill(sx, sy + sr, sx + th, sy + sh - sr, argb);
                fill(sx + sw - th, sy + sr, sx + sw, sy + sh - sr, argb);
            }
            int inR = Math.max(0, sr - th);
            for (int row = 0; row < sr; row++) {
                double dy = sr - row - 0.5;
                int outX = (int) Math.floor(sr - Math.sqrt(Math.max(0.0, (double) sr * sr - dy * dy)));
                int inX = (dy < inR) ? (int) Math.ceil(sr - Math.sqrt(Math.max(0.0, (double) inR * inR - dy * dy))) : sr;
                if (inX <= outX) inX = outX + 1;
                fill(sx + outX, sy + row, sx + inX, sy + row + 1, argb);
                fill(sx + sw - inX, sy + row, sx + sw - outX, sy + row + 1, argb);
                fill(sx + outX, sy + sh - row - 1, sx + inX, sy + sh - row, argb);
                fill(sx + sw - inX, sy + sh - row - 1, sx + sw - outX, sy + sh - row, argb);
            }
            popTransform();
        }

        default void drawGlassCard(int x, int y, int w, int h, int radius, int bgArgb, int borderArgb, boolean sheen) {
            drawRoundedRect(x, y, w, h, radius, bgArgb);
            if (sheen && w > radius * 2 + 4 && h > 4) {
                fill(x + Math.max(2, radius), y + 1, x + w - Math.max(2, radius), y + 2, 0x16FFFFFF);
            }
            if (((borderArgb >>> 24) & 0xFF) > 0) drawRoundedOutline(x, y, w, h, radius, borderArgb);
        }

        default void drawSoftShadow(int x, int y, int w, int h, int radius) {
            drawRoundedRect(x - 6, y - 4, w + 12, h + 12, radius + 5, 0x14000000);
            drawRoundedRect(x - 3, y - 1, w + 6, h + 6, radius + 2, 0x22000000);
        }

        default void drawCircleFilled(int cx, int cy, int r, int argb) {
            drawRoundedRect(cx - r, cy - r, r * 2, r * 2, r, argb);
        }

        default void drawCircle(int cx, int cy, int r, int t, int col) {
            int steps = Math.max(28, r * 8), half = Math.max(0, (t - 1) / 2);
            for (int i = 0; i < steps; i++) {
                double a = (Math.PI * 2.0 * i) / steps;
                int px = (int) Math.round(cx + Math.cos(a) * r), py = (int) Math.round(cy + Math.sin(a) * r);
                fill(px - half, py - half, px - half + t, py - half + t, col);
            }
        }

        default void drawLine(int x0, int y0, int x1, int y1, int t, int col) {
            int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0))), half = Math.max(0, (t - 1) / 2);
            for (int i = 0; i <= steps; i++) {
                int px = x0 + ((x1 - x0) * i) / steps, py = y0 + ((y1 - y0) * i) / steps;
                fill(px - half, py - half, px - half + t, py - half + t, col);
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