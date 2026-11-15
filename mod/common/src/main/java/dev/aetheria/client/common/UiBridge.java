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

        default void drawCircle(int cx, int cy, int r, int t, int col) {
            int steps = Math.max(28, r * 8);
            int half = Math.max(0, (t - 1) / 2);
            for (int i = 0; i < steps; i++) {
                double a = (Math.PI * 2.0 * i) / steps;
                int px = (int) Math.round(cx + Math.cos(a) * r);
                int py = (int) Math.round(cy + Math.sin(a) * r);
                fill(px - half, py - half, px - half + t, py - half + t, col);
            }
        }

        default void drawLine(int x0, int y0, int x1, int y1, int t, int col) {
            int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
            int steps = Math.max(1, Math.max(dx, dy));
            int half = Math.max(0, (t - 1) / 2);
            for (int i = 0; i <= steps; i++) {
                int px = x0 + ((x1 - x0) * i) / steps;
                int py = y0 + ((y1 - y0) * i) / steps;
                fill(px - half, py - half, px - half + t, py - half + t, col);
            }
        }
    }

    public interface HudData {
        boolean hasWorld();
        int fps();
        int x();
        int y();
        int z();
        String facing();
        String biome();
        int pingMs();
        boolean isSprinting();
        String[] activePotions();
        float yaw();
        String serverIp();
        long dayCount();
        String heldToolDurability();
        String armorStatus();
        boolean isMoveW();
        boolean isMoveA();
        boolean isMoveS();
        boolean isMoveD();
        boolean isJumpDown();
        boolean targetPlayerInView(double range);
        boolean targetEntityInView(double range);
    }
}
