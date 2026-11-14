package dev.aetheria.client;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.HudEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import java.util.*;

public final class McBridge {
    private static int framesThisSecond = 0, measuredFps = 0;
    private static long lastFpsMs = System.currentTimeMillis();

    public static void renderInGame() {
        framesThisSecond++;
        long now = System.currentTimeMillis();
        if (now - lastFpsMs >= 1000L) { measuredFps = framesThisSecond; framesThisSecond = 0; lastFpsMs = now; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;
        Window win = new Window(mc);
        SharedHudRenderer.renderInGame(draw(mc), data(mc), win.getWidth(), win.getHeight());
    }

    public static UiBridge.UiDraw draw(MinecraftClient mc) {
        return new UiBridge.UiDraw() {
            @Override public void fill(int x1, int y1, int x2, int y2, int argb) { DrawableHelper.fill(x1, y1, x2, y2, argb); }
            @Override public void text(String txt, int x, int y, int argb, boolean shadow) {
                if (shadow) mc.textRenderer.drawWithShadow(txt, x, y, argb); else mc.textRenderer.draw(txt, x, y, argb);
            }
            @Override public int textWidth(String txt) { return mc.textRenderer.getStringWidth(txt); }
            @Override public void pushTransform(float tx, float ty, float scale) { GlStateManager.pushMatrix(); GlStateManager.translatef(tx, ty, 0.0F); GlStateManager.scalef(scale, scale, 1.0F); }
            @Override public void popTransform() { GlStateManager.popMatrix(); }
        };
    }

    public static UiBridge.HudData data(MinecraftClient mc) {
        return new UiBridge.HudData() {
            @Override public boolean hasWorld() { return mc.player != null && mc.world != null; }
            @Override public int fps() { return measuredFps; }
            @Override public int x() { return (int) Math.floor(mc.player.x); }
            @Override public int y() { return (int) Math.floor(mc.player.y); }
            @Override public int z() { return (int) Math.floor(mc.player.z); }
            @Override public String facing() { String n = mc.player.getHorizontalDirection().asString(); return n.substring(0, 1).toUpperCase() + n.substring(1); }
            @Override public String biome() { return mc.world.getBiome(new BlockPos(x(), y(), z())).name; }
            @Override public int pingMs() {
                if (mc.getNetworkHandler() == null) return 0;
                PlayerListEntry pe = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                return pe != null ? pe.getLatency() : 0;
            }
            @Override public boolean isSprinting() { return mc.player.isSprinting(); }
            @Override public String[] activePotions() {
                List<String> out = new ArrayList<String>();
                for (Object o : mc.player.getStatusEffectInstances()) {
                    StatusEffectInstance eff = (StatusEffectInstance) o;
                    int s = Math.max(0, eff.getDuration() / 20);
                    out.add("Effect#" + eff.getEffectId() + " " + (s / 60) + ":" + String.format("%02d", s % 60));
                }
                return out.isEmpty() ? new String[]{"No Potions"} : out.toArray(new String[0]);
            }
            @Override public float yaw() { return mc.player.yaw; }
            @Override public String serverIp() { return mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer"; }
            @Override public long dayCount() { return mc.world.getTimeOfDay() / 24000L; }
            @Override public String heldToolDurability() {
                ItemStack h = mc.player.inventory.getMainHandStack();
                return (h != null && h.isDamageable()) ? h.getName() + ": " + (h.getMaxDamage() - h.getDamage()) + "/" + h.getMaxDamage() : "Tool: --";
            }
            @Override public String armorStatus() {
                String[] names = {"F", "L", "C", "H"}; List<String> list = new ArrayList<String>();
                ItemStack[] arm = mc.player.inventory.armor;
                for (int i = arm.length - 1; i >= 0; i--) {
                    if (arm[i] != null && arm[i].isDamageable()) list.add(names[i] + ": " + Math.round(100.0 * (arm[i].getMaxDamage() - arm[i].getDamage()) / arm[i].getMaxDamage()) + "%");
                }
                return list.isEmpty() ? "Armor: None" : String.join(" | ", list);
            }
            @Override public boolean isMoveW() { return Keyboard.isKeyDown(mc.options.keyForward.getCode()); }
            @Override public boolean isMoveA() { return Keyboard.isKeyDown(mc.options.keyLeft.getCode()); }
            @Override public boolean isMoveS() { return Keyboard.isKeyDown(mc.options.keyBack.getCode()); }
            @Override public boolean isMoveD() { return Keyboard.isKeyDown(mc.options.keyRight.getCode()); }
            @Override public boolean isJumpDown() { return Keyboard.isKeyDown(mc.options.keyJump.getCode()); }
        };
    }
}