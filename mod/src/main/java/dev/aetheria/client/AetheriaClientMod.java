package dev.aetheria.client;

import dev.aetheria.client.config.ClientConfig;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class AetheriaClientMod implements ClientModInitializer {
    public static final long START_TIME_MS = System.currentTimeMillis();
    public static volatile long windowHandle = 0L;

    private static boolean prevMenuDown = false;
    private static boolean zooming = false;
    private static int comboCount = 0;
    private static long lastHitTimeMs = 0L;
    private static double lastReachBlocks = 0.0;

    @Override
    public void onInitializeClient() {
        ClientConfig.get().load();
    }

    public static void captureWindow() {
        if (windowHandle == 0L) {
            long ctx = GLFW.glfwGetCurrentContext();
            if (ctx != 0L) windowHandle = ctx;
        }
    }

    public static void onClientTick(Minecraft mc) {
        captureWindow();
        if (windowHandle == 0L) return;

        ClientConfig cfg = ClientConfig.get();
        int menuKeyCode = mapKeyName(cfg.menuKey, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean menuDown = GLFW.glfwGetKey(windowHandle, menuKeyCode) == GLFW.GLFW_PRESS;
        if (menuDown && !prevMenuDown && mc.gui.screen() == null) {
            mc.gui.setScreen(new ModMenuScreen());
        }
        prevMenuDown = menuDown;

        // Zoom hold check
        int zoomKeyCode = mapKeyName(cfg.getString("cam_zoom_key", "C"), GLFW.GLFW_KEY_C);
        zooming = mc.gui.screen() == null && cfg.getBool("cam_zoom", true)
                && GLFW.glfwGetKey(windowHandle, zoomKeyCode) == GLFW.GLFW_PRESS;

        // ToggleSprint
        if (mc.player != null && mc.gui.screen() == null && cfg.getBool("pvp_togglesprint", true)) {
            if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision) {
                mc.player.setSprinting(true);
            }
        }

        // Reset combo after 4s of inactivity
        if (comboCount > 0 && System.currentTimeMillis() - lastHitTimeMs > 4000L) {
            comboCount = 0;
        }
    }

    public static boolean isZooming() {
        return zooming;
    }

    public static void recordAttack(double distance) {
        long now = System.currentTimeMillis();
        comboCount = (now - lastHitTimeMs < 4000L) ? comboCount + 1 : 1;
        lastHitTimeMs = now;
        lastReachBlocks = distance;
    }

    public static int getComboCount() {
        return comboCount;
    }

    public static double getLastReach() {
        return lastReachBlocks;
    }

    public static int mapKeyName(String name, int fallback) {
        if (name == null) return fallback;
        return switch (name.toUpperCase()) {
            case "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case "M" -> GLFW.GLFW_KEY_M;
            case "H" -> GLFW.GLFW_KEY_H;
            case "INSERT" -> GLFW.GLFW_KEY_INSERT;
            case "C" -> GLFW.GLFW_KEY_C;
            case "V" -> GLFW.GLFW_KEY_V;
            case "Z" -> GLFW.GLFW_KEY_Z;
            case "X" -> GLFW.GLFW_KEY_X;
            case "G" -> GLFW.GLFW_KEY_G;
            case "R" -> GLFW.GLFW_KEY_R;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            default -> fallback;
        };
    }
}
