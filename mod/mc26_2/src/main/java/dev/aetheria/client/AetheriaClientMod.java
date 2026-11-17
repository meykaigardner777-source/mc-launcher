package dev.aetheria.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class AetheriaClientMod implements ClientModInitializer {
    private static boolean prevMenuDown = false, prevProfDown = false;
    @Override public void onInitializeClient() { ClientConfig.get().init(FabricLoader.getInstance().getConfigDir()); }

    private static boolean isKeyHeld(Minecraft mc, String optName, int fallback) {
        int code = ClientState.nameToGlfwKey(optName, fallback);
        return code > 0 && InputConstants.isKeyDown(mc.getWindow(), code);
    }

    public static void onClientTick(Minecraft mc) {
        ClientConfig cfg = ClientConfig.get(); cfg.reloadIfChanged();
        boolean menuDown = isKeyHeld(mc, cfg.menuKey, InputConstants.KEY_RSHIFT);
        if (menuDown && !prevMenuDown && mc.gui.screen() == null) mc.gui.setScreen(new ModMenuScreen());
        prevMenuDown = menuDown;

        boolean profDown = isKeyHeld(mc, cfg.getString("ctl_profile_key", "F6"), InputConstants.KEY_F6);
        if (profDown && !prevProfDown && mc.gui.screen() == null) cfg.cycleProfilePreset();
        prevProfDown = profDown;

        ClientState.setZooming(mc.gui.screen() == null && cfg.getBool("cam_zoom", true)
                && isKeyHeld(mc, cfg.getString("cam_zoom_key", "C"), InputConstants.KEY_C));
        ClientState.setFreeLook(mc.gui.screen() == null && cfg.getBool("util_freelook", true)
                && isKeyHeld(mc, cfg.getString("util_freelook_key", "LALT"), InputConstants.KEY_LALT));

        if (mc.player != null && mc.gui.screen() == null && cfg.getBool("pvp_togglesprint", true)) {
            if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision
                    && !mc.player.isUsingItem() && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly)) {
                mc.player.setSprinting(true);
            }
        }
        ClientState.tickComboTimeout();
    }
}