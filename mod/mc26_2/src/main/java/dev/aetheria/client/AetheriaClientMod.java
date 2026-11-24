package dev.aetheria.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.ModMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public class AetheriaClientMod implements ClientModInitializer {
    private static boolean prevMenuDown = false, prevWpDown = false, prevFbDown = false, prevProfDown = false, prevSneakDown = false, prevShieldCd = false;
    private static int lastAttackedId = -1;
    private static long lastAttackedMs = 0L;
    private static final Map<String, Integer> PREV_INV = new HashMap<String, Integer>();
    private static boolean invInit = false;

    @Override public void onInitializeClient() { ClientConfig.get().init(FabricLoader.getInstance().getConfigDir()); }

    public static void onEntityAttacked(int entityId) { lastAttackedId = entityId; lastAttackedMs = System.currentTimeMillis(); }

    private static boolean isKeyHeld(Minecraft mc, String optName, int fallback) {
        int code = ClientState.nameToGlfwKey(optName, fallback);
        return code > 0 && InputConstants.isKeyDown(mc.getWindow(), code);
    }

    public static void onClientTick(Minecraft mc) {
        ClientConfig cfg = ClientConfig.get(); cfg.reloadIfChanged();
        boolean menuDown = isKeyHeld(mc, cfg.menuKey, InputConstants.KEY_RSHIFT);
        if (menuDown && !prevMenuDown && mc.gui.screen() == null) mc.gui.setScreen(new ModMenuScreen());
        prevMenuDown = menuDown;

        boolean wpDown = isKeyHeld(mc, cfg.getString("ctl_waypoints_key", "B"), InputConstants.KEY_B);
        if (wpDown && !prevWpDown && mc.gui.screen() == null) mc.gui.setScreen(new ModMenuScreen(null, "waypoints"));
        prevWpDown = wpDown;

        boolean fbDown = isKeyHeld(mc, cfg.getString("ctl_fullbright_key", "F7"), InputConstants.KEY_F7);
        if (fbDown && !prevFbDown && mc.gui.screen() == null) cfg.setOption("util_fullbright", Boolean.valueOf(!cfg.getBool("util_fullbright", false)));
        prevFbDown = fbDown;

        boolean profDown = isKeyHeld(mc, cfg.getString("ctl_profile_key", "F6"), InputConstants.KEY_F6);
        if (profDown && !prevProfDown && mc.gui.screen() == null) cfg.cycleProfilePreset();
        prevProfDown = profDown;

        ClientState.setZooming(mc.gui.screen() == null && cfg.getBool("cam_zoom", true) && isKeyHeld(mc, cfg.getString("cam_zoom_key", "C"), InputConstants.KEY_C));
        boolean fl = mc.gui.screen() == null && cfg.getBool("util_freelook", true) && isKeyHeld(mc, cfg.getString("util_freelook_key", "LALT"), InputConstants.KEY_LALT);
        if (fl && !ClientState.isFreeLook() && mc.player != null) ClientState.initFreeLook(mc.player.getYRot(), mc.player.getXRot());
        ClientState.setFreeLook(fl);

        if (mc.player != null && mc.level != null) {
            if (lastAttackedId >= 0) {
                if (System.currentTimeMillis() - lastAttackedMs > 6000L) lastAttackedId = -1;
                else {
                    Entity t = mc.level.getEntity(lastAttackedId);
                    if (t == null || !t.isAlive() || (t instanceof LivingEntity le && le.isDeadOrDying())) { ClientState.recordKill(); lastAttackedId = -1; }
                }
            }
            ClientConfig.HudModule pf = cfg.hudModules.get("pickup_feed");
            if (pf != null && pf.enabled && mc.gui.screen() == null) {
                Map<String, Integer> cur = new HashMap<String, Integer>();
                for (int i = 0; i < 36; i++) {
                    ItemStack st = mc.player.getInventory().getItem(i);
                    if (!st.isEmpty()) { String n = st.getHoverName().getString(); cur.put(n, Integer.valueOf(cur.getOrDefault(n, Integer.valueOf(0)).intValue() + st.getCount())); }
                }
                if (invInit) {
                    for (Map.Entry<String, Integer> e : cur.entrySet()) {
                        int delta = e.getValue().intValue() - PREV_INV.getOrDefault(e.getKey(), Integer.valueOf(0)).intValue();
                        if (delta > 0 && delta <= 64) ClientState.pushPickup("+" + delta + " " + e.getKey());
                    }
                }
                PREV_INV.clear(); PREV_INV.putAll(cur); invInit = true;
            } else invInit = false;
        } else { invInit = false; lastAttackedId = -1; }

        if (mc.player != null && mc.gui.screen() == null) {
            if (cfg.getBool("pvp_togglesprint", true) && mc.options.keyUp.isDown() && !mc.player.isShiftKeyDown() && !mc.player.horizontalCollision
                    && !mc.player.isUsingItem() && (mc.player.getFoodData().getFoodLevel() > 6 || mc.player.getAbilities().mayfly)) {
                mc.player.setSprinting(true);
            }
            if (cfg.getBool("pvp_togglesneak", false)) {
                ClientState.setBypassToggleSneak(true);
                boolean sk = mc.options.keyShift.isDown();
                ClientState.setBypassToggleSneak(false);
                if (sk && !prevSneakDown) ClientState.toggleSneak();
                prevSneakDown = sk;
                if (ClientState.isToggleSneaking()) mc.player.setShiftKeyDown(true);
            } else { prevSneakDown = false; ClientState.setToggleSneaking(false); }

            try {
                boolean scd = mc.player.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD));
                if (scd && !prevShieldCd) ClientState.triggerShieldCooldown();
                prevShieldCd = scd;
            } catch (Throwable ignored) {}
        }
        if (mc.player != null && mc.level != null && (!cfg.getBool("perf_unfocused_fps", true) || mc.isWindowActive())) McBridge.emitWorldGizmos(mc);
        ClientState.tickComboTimeout();
    }
}
