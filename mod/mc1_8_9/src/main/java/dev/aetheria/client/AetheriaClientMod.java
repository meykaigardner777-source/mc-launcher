package dev.aetheria.client;

import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class AetheriaClientMod implements ClientModInitializer {
    private static boolean prevMenuDown = false, prevProfDown = false;
    private static float savedGamma = -1.0F;

    @Override public void onInitializeClient() { ClientConfig.get().init(FabricLoader.getInstance().getConfigDir()); }

    public static void onClientTick(MinecraftClient mc) {
        ClientConfig cfg = ClientConfig.get(); cfg.reloadIfChanged();
        boolean menuDown = Keyboard.isKeyDown(mapKey(cfg.menuKey, Keyboard.KEY_RSHIFT));
        if (menuDown && !prevMenuDown && mc.currentScreen == null) mc.setScreen(new ModMenuScreen());
        prevMenuDown = menuDown;

        boolean profDown = Keyboard.isKeyDown(mapKey(cfg.getString("ctl_profile_key", "F6"), Keyboard.KEY_F6));
        if (profDown && !prevProfDown && mc.currentScreen == null) cfg.cycleProfilePreset();
        prevProfDown = profDown;

        ClientState.setZooming(mc.currentScreen == null && cfg.getBool("cam_zoom", true)
                && Keyboard.isKeyDown(mapKey(cfg.getString("cam_zoom_key", "C"), Keyboard.KEY_C)));
        ClientState.setFreeLook(mc.currentScreen == null && cfg.getBool("util_freelook", true)
                && Keyboard.isKeyDown(mapKey(cfg.getString("util_freelook_key", "ALT"), Keyboard.KEY_LMENU)));

        if (cfg.getBool("util_fullbright", false)) {
            if (savedGamma < 0) savedGamma = mc.options.gamma;
            mc.options.gamma = 16.0F;
        } else if (savedGamma >= 0) {
            mc.options.gamma = savedGamma;
            savedGamma = -1.0F;
        }

        boolean inWorld = mc.currentScreen == null;
        ClientState.setMouseButton(true, Mouse.isButtonDown(0), inWorld);
        ClientState.setMouseButton(false, Mouse.isButtonDown(1), inWorld);

        if (mc.player != null && mc.currentScreen == null && cfg.getBool("pvp_togglesprint", true)) {
            if (Keyboard.isKeyDown(McBridge.getKeyCode(mc.options, "key.forward", Keyboard.KEY_W))
                    && !mc.player.isSneaking() && !mc.player.horizontalCollision
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
        if ("F6".equals(u)) return Keyboard.KEY_F6; if ("F7".equals(u)) return Keyboard.KEY_F7; if ("F8".equals(u)) return Keyboard.KEY_F8;
        if ("INSERT".equals(u)) return Keyboard.KEY_INSERT; if ("C".equals(u)) return Keyboard.KEY_C;
        if ("V".equals(u)) return Keyboard.KEY_V; if ("Z".equals(u)) return Keyboard.KEY_Z;
        if ("X".equals(u)) return Keyboard.KEY_X; if ("G".equals(u)) return Keyboard.KEY_G;
        if ("R".equals(u)) return Keyboard.KEY_R; if ("TAB".equals(u)) return Keyboard.KEY_TAB;
        if ("ALT".equals(u)) return Keyboard.KEY_LMENU; return fallback;
    }
}
