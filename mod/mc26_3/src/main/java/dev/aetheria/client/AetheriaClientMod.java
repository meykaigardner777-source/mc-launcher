package dev.aetheria.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class AetheriaClientMod implements ClientModInitializer {
    private static boolean prevMenuDown = false;

    @Override
    public void onInitializeClient() { ClientConfig.get().init(FabricLoader.getInstance().getConfigDir()); }

    public static void onClientTick(Minecraft mc) {
        ClientConfig cfg = ClientConfig.get();
        cfg.reloadIfChanged();
        boolean menuDown = InputConstants.isKeyDown(mapKey(cfg.menuKey, InputConstants.KEY_RSHIFT));
        if (menuDown && !prevMenuDown && mc.gui.screen() == null) mc.gui.setScreen(new ModMenuScreen());
        prevMenuDown = menuDown;

        ClientState.setZooming(mc.gui.screen() == null && cfg.getBool("cam_zoom", true)
                && InputConstants.isKeyDown(mapKey(cfg.getString("cam_zoom_key", "C"), InputConstants.KEY_C)));

        if (mc.player != null && mc.gui.screen() == null && cfg.getBool("pvp_togglesprint", true)) {
            if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision
                    && !mc.player.isUsingItem() && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly)) {
                mc.player.setSprinting(true);
            }
        }
        ClientState.tickComboTimeout();
    }

    public static int mapKey(String name, int fallback) {
        if (name == null) return fallback;
        return switch (name.toUpperCase()) {
            case "RSHIFT" -> InputConstants.KEY_RSHIFT; case "LSHIFT" -> InputConstants.KEY_LSHIFT;
            case "M" -> InputConstants.KEY_M; case "H" -> InputConstants.KEY_H;
            case "K" -> InputConstants.KEY_K; case "P" -> InputConstants.KEY_P;
            case "INSERT" -> InputConstants.KEY_INSERT; case "C" -> InputConstants.KEY_C;
            case "V" -> InputConstants.KEY_V; case "Z" -> InputConstants.KEY_Z;
            case "X" -> InputConstants.KEY_X; case "G" -> InputConstants.KEY_G;
            case "R" -> InputConstants.KEY_R; case "TAB" -> InputConstants.KEY_TAB;
            case "ALT" -> InputConstants.KEY_LALT; default -> fallback;
        };
    }
}