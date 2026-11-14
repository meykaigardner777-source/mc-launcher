package dev.aetheria.client;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class McBridge {
    public static void renderInGame(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof HudEditorScreen) return;
        SharedHudRenderer.renderInGame(draw(g, mc), data(mc), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
    public static UiBridge.UiDraw draw(GuiGraphics g, Minecraft mc) {
        return new UiBridge.UiDraw() {
            @Override public void fill(int x1, int y1, int x2, int y2, int argb) { g.fill(x1, y1, x2, y2, argb); }
            @Override public void text(String txt, int x, int y, int argb, boolean shadow) { g.drawString(mc.font, txt, x, y, argb, shadow); }
            @Override public int textWidth(String txt) { return mc.font.width(txt); }
            @Override public void pushTransform(float tx, float ty, float scale) { g.pose().pushMatrix(); g.pose().translate(tx, ty); g.pose().scale(scale, scale); }
            @Override public void popTransform() { g.pose().popMatrix(); }
        };
    }
    public static UiBridge.HudData data(Minecraft mc) {
        return new UiBridge.HudData() {
            @Override public boolean hasWorld() { return mc.player != null && mc.level != null; }
            @Override public int fps() { return mc.getFps(); }
            @Override public int x() { return mc.player.blockPosition().getX(); }
            @Override public int y() { return mc.player.blockPosition().getY(); }
            @Override public int z() { return mc.player.blockPosition().getZ(); }
            @Override public String facing() { String n = mc.player.getDirection().getName(); return n.substring(0, 1).toUpperCase() + n.substring(1); }
            @Override public String biome() {
                String k = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(x -> x.identifier().getPath()).orElse("unknown");
                StringBuilder sb = new StringBuilder();
                for (String p : k.replace('_', ' ').split(" ")) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
                return sb.toString().trim();
            }
            @Override public int pingMs() { PlayerInfo pi = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null; return pi != null ? pi.getLatency() : 0; }
            @Override public boolean isSprinting() { return mc.player.isSprinting(); }
            @Override public String[] activePotions() {
                List<String> out = new ArrayList<String>();
                for (MobEffectInstance eff : mc.player.getActiveEffects()) {
                    String name = eff.getEffect().value().getDisplayName().getString();
                    if (eff.isInfiniteDuration()) out.add(name + " **:**");
                    else { int s = Math.max(0, eff.getDuration() / 20); out.add(name + " " + (s / 60) + ":" + String.format("%02d", s % 60)); }
                }
                return out.isEmpty() ? new String[]{"No Potions"} : out.toArray(new String[0]);
            }
            @Override public float yaw() { return mc.player.getYRot(); }
            @Override public String serverIp() { return mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer"; }
            @Override public long dayCount() { return mc.level.getDayTime() / 24000L; }
            @Override public String heldToolDurability() { ItemStack h = mc.player.getMainHandItem(); return (!h.isEmpty() && h.isDamageableItem()) ? h.getHoverName().getString() + ": " + (h.getMaxDamage() - h.getDamageValue()) + "/" + h.getMaxDamage() : "Tool: --"; }
            @Override public String armorStatus() {
                List<String> list = new ArrayList<String>();
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack st = mc.player.getItemBySlot(s);
                    if (!st.isEmpty() && st.isDamageableItem()) list.add(s.getName().substring(0, 1).toUpperCase() + ": " + Math.round(100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage()) + "%");
                }
                return list.isEmpty() ? "Armor: None" : String.join(" | ", list);
            }
            @Override public boolean isMoveW() { return mc.options.keyUp.isDown(); } @Override public boolean isMoveA() { return mc.options.keyLeft.isDown(); }
            @Override public boolean isMoveS() { return mc.options.keyDown.isDown(); } @Override public boolean isMoveD() { return mc.options.keyRight.isDown(); }
            @Override public boolean isJumpDown() { return mc.options.keyJump.isDown(); }
        };
    }
}