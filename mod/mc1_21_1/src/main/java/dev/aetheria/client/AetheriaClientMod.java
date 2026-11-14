package dev.aetheria.client;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class AetheriaClientMod implements ClientModInitializer {
    private static boolean prevMenuDown = false;
    @Override public void onInitializeClient() { ClientConfig.get().init(FabricLoader.getInstance().getConfigDir()); }

    public static void onClientTick(Minecraft mc) {
        ClientConfig cfg = ClientConfig.get(); cfg.reloadIfChanged();
        long win = mc.getWindow().getWindow();
        boolean menuDown = InputConstants.isKeyDown(win, AetheriaKeyMap.map(cfg.menuKey, InputConstants.KEY_RSHIFT));
        if (menuDown && !prevMenuDown && mc.screen == null) mc.setScreen(new ModMenuScreen());
        prevMenuDown = menuDown;

        ClientState.setZooming(mc.screen == null && cfg.getBool("cam_zoom", true)
                && InputConstants.isKeyDown(win, AetheriaKeyMap.map(cfg.getString("cam_zoom_key", "C"), InputConstants.KEY_C)));

        if (mc.player != null && mc.screen == null && cfg.getBool("pvp_togglesprint", true)) {
            if (mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision
                    && !mc.player.isUsingItem() && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly)) {
                mc.player.setSprinting(true);
            }
        }
        ClientState.tickComboTimeout();
    }
}