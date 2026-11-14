package dev.aetheria.client;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class AetheriaClientMod implements ClientModInitializer {
    private static boolean prevMenuDown = false;
    @Override public void onInitializeClient() { ClientConfig.get().init(FabricLoader.getInstance().getConfigDir()); }

    public static void onClientTick(MinecraftClient mc) {
        ClientConfig cfg = ClientConfig.get(); cfg.reloadIfChanged();
        boolean menuDown = Keyboard.isKeyDown(mapKey(cfg.menuKey, Keyboard.KEY_RSHIFT));
        if (menuDown && !prevMenuDown && mc.currentScreen == null) mc.setScreen(new ModMenuScreen());
        prevMenuDown = menuDown;

        ClientState.setZooming(mc.currentScreen == null && cfg.getBool("cam_zoom", true)
                && Keyboard.isKeyDown(mapKey(cfg.getString("cam_zoom_key", "C"), Keyboard.KEY_C)));

        boolean inWorld = mc.currentScreen == null;
        ClientState.setMouseButton(true, Mouse.isButtonDown(0), inWorld);
        ClientState.setMouseButton(false, Mouse.isButtonDown(1), inWorld);

        if (mc.player != null && mc.currentScreen == null && cfg.getBool("pvp_togglesprint", true)) {
            if (mc.options.keyForward.isPressed() && !mc.player.isSneaking() && !mc.player.horizontalCollision
                    && !mc.player.isUsingItem() && (mc.player.getHungerManager().getFoodLevel() > 6 || mc.player.abilities.allowFlying)) {
                mc.player.setSprinting(true);
            }
        }
        ClientState.tickComboTimeout();
    }

    public static int mapKey(String name, int fallback) {
        if (name == null) return fallback;
        String u = name.toUpperCase();
        if ("RSHIFT".equals(u)) return Keyboard.KEY_RSHIFT; if ("LSHIFT".equals(u)) return Keyboard.KEY_LSHIFT;
        if ("M".equals(u)) return Keyboard.KEY_M; if ("H".equals(u)) return Keyboard.KEY_H;
        if ("K".equals(u)) return Keyboard.KEY_K; if ("P".equals(u)) return Keyboard.KEY_P;
        if ("INSERT".equals(u)) return Keyboard.KEY_INSERT; if ("C".equals(u)) return Keyboard.KEY_C;
        if ("V".equals(u)) return Keyboard.KEY_V; if ("Z".equals(u)) return Keyboard.KEY_Z;
        if ("X".equals(u)) return Keyboard.KEY_X; if ("G".equals(u)) return Keyboard.KEY_G;
        if ("R".equals(u)) return Keyboard.KEY_R; if ("TAB".equals(u)) return Keyboard.KEY_TAB;
        if ("ALT".equals(u)) return Keyboard.KEY_LMENU;
        return fallback;
    }
}