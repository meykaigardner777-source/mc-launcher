package dev.aetheria.client;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import java.util.*;

public final class McBridge {
    private McBridge() {}
    public static void renderInGame(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof HudEditorScreen) return;
        SharedHudRenderer.renderInGame(draw(g, mc), data(mc), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }
    private static boolean targetInView(Minecraft mc, double range, boolean playersOnly) {
        if (mc.player == null || mc.level == null) return false;
        if (mc.hitResult instanceof EntityHitResult ehr) {
            Entity hit = ehr.getEntity();
            if (hit != null && hit != mc.player && (!playersOnly || hit instanceof Player) && mc.player.distanceTo(hit) <= range) return true;
        }
        Vec3 eye = mc.player.getEyePosition(1.0F), look = mc.player.getViewVector(1.0F), end = eye.add(look.scale(range));
        double maxDistSq = range * range;
        for (Entity e : (playersOnly ? mc.level.players() : mc.level.entitiesForRendering())) {
            if (e == mc.player || !e.isAlive() || e.isSpectator()) continue;
            if (playersOnly && !(e instanceof Player)) continue;
            if (!playersOnly && !(e instanceof LivingEntity)) continue;
            if (e.distanceToSqr(mc.player) <= maxDistSq && e.getBoundingBox().inflate(0.18).clip(eye, end).isPresent() && mc.player.hasLineOfSight(e)) return true;
        }
        return false;
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
            @Override public String biome() { String k = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(x -> x.identifier().getPath()).orElse("unknown"); StringBuilder sb = new StringBuilder(); for (String p : k.replace('_', ' ').split(" ")) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' '); return sb.toString().trim(); }
            @Override public int pingMs() { PlayerInfo pi = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(mc.player.getUUID()) : null; return pi != null ? pi.getLatency() : 0; }
            @Override public boolean isSprinting() { return mc.player.isSprinting(); }
            @Override public String[] activePotions() { List<String> out = new ArrayList<String>(); for (MobEffectInstance eff : mc.player.getActiveEffects()) { String name = eff.getEffect().value().getDisplayName().getString(); if (eff.isInfiniteDuration()) out.add(name + " **:**"); else { int s = Math.max(0, eff.getDuration() / 20); out.add(name + " " + (s / 60) + ":" + String.format("%02d", s % 60)); } } return out.isEmpty() ? new String[]{"No Potions"} : out.toArray(new String[0]); }
            @Override public float yaw() { return mc.player.getYRot(); }
            @Override public String serverIp() { return mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer"; }
            @Override public long dayCount() { return mc.level.getDayTime() / 24000L; }
            @Override public long worldTime() { return mc.level.getDayTime(); }
            @Override public String heldToolDurability() { ItemStack h = mc.player.getMainHandItem(); return (!h.isEmpty() && h.isDamageableItem()) ? h.getHoverName().getString() + ": " + (h.getMaxDamage() - h.getDamageValue()) + "/" + h.getMaxDamage() : "Tool: --"; }
            @Override public String armorStatus() { boolean pct = ClientConfig.get().getBool("armor_percent", true); List<String> list = new ArrayList<String>(); for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) { ItemStack st = mc.player.getItemBySlot(s); if (!st.isEmpty() && st.isDamageableItem()) { int rem = st.getMaxDamage() - st.getDamageValue(); String tag = s.getName().substring(0, 1).toUpperCase(); list.add(pct ? (tag + ": " + Math.round(100.0 * rem / st.getMaxDamage()) + "%") : (tag + ": " + rem + "/" + st.getMaxDamage())); } } return list.isEmpty() ? "Armor: None" : String.join(" | ", list); }
            @Override public boolean isMoveW() { return mc.options.keyUp.isDown(); } @Override public boolean isMoveA() { return mc.options.keyLeft.isDown(); }
            @Override public boolean isMoveS() { return mc.options.keyDown.isDown(); } @Override public boolean isMoveD() { return mc.options.keyRight.isDown(); }
            @Override public boolean isJumpDown() { return mc.options.keyJump.isDown(); }
            @Override public boolean targetPlayerInView(double range) { return targetInView(mc, range, true); }
            @Override public boolean targetEntityInView(double range) { return targetInView(mc, range, false); }            @Override public double posX() { return mc.player.getX(); }
            @Override public double posY() { return mc.player.getY(); }
            @Override public double posZ() { return mc.player.getZ(); }
            @Override public float pitch() { return mc.player.getXRot(); }
            @Override public boolean isSneaking() { return mc.player.isShiftKeyDown(); }
            @Override public boolean isMoveCtrl() { return mc.options.keySprint.isDown(); }
            @Override public int moonPhase() { return (int) (((worldTime() / 24000L) % 8L + 8L) % 8L); }
            @Override public String weather() { return mc.level.isThundering() ? "Thunder" : (mc.level.isRaining() ? "Rain" : "Clear"); }
            @Override public int blockLight() { return mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, mc.player.blockPosition()); }
            @Override public int skyLight() { return mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, mc.player.blockPosition()); }
            @Override public float health() { return mc.player.getHealth(); }
            @Override public float maxHealth() { return mc.player.getMaxHealth(); }
            @Override public float absorption() { return mc.player.getAbsorptionAmount(); }
            @Override public int foodLevel() { return mc.player.getFoodData().getFoodLevel(); }
            @Override public float saturation() { return mc.player.getFoodData().getSaturationLevel(); }
            @Override public int airSupply() { return mc.player.getAirSupply(); }
            @Override public int maxAirSupply() { return mc.player.getMaxAirSupply(); }
            @Override public int onlinePlayers() { return mc.getConnection() != null ? mc.getConnection().getOnlinePlayers().size() : 1; }
            @Override public String lookingAtBlock() { return mc.hitResult instanceof BlockHitResult bhr ? mc.level.getBlockState(bhr.getBlockPos()).getBlock().getName().getString() : "Air"; }
            @Override public String lookingAtEntity() { return mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getName().getString() : "None"; }
            @Override public String targetName() { return mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getName().getString() : ""; }
            @Override public float targetHealth() { return (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) ? le.getHealth() : 0.0F; }
            @Override public float targetMaxHealth() { return (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) ? le.getMaxHealth() : 20.0F; }
            @Override public int countItem(String kw) {
                int c = 0; String l = kw.toLowerCase();
                for (int i = 0; i < 36; i++) { ItemStack s = mc.player.getInventory().getItem(i); if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains(l)) c += s.getCount(); }
                ItemStack off = mc.player.getOffhandItem(); if (!off.isEmpty() && off.getItem().toString().toLowerCase().contains(l)) c += off.getCount();
                return c;
            }
            @Override public int freeSlots() { int f = 0; for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) f++; return f; }
            @Override public int usedSlots() { return 36 - freeSlots(); }
            @Override public float attackCooldownPct() { return mc.player.getAttackStrengthScale(0.0F); }
            @Override public boolean isBlockingOrCooling() { return mc.player.isBlocking(); }
            @Override public double fovDegrees() { return ((Number) mc.options.fov().get()).doubleValue(); }
            @Override public double gammaPercent() { return ((Number) mc.options.gamma().get()).doubleValue() * 100.0; }        };
    }
}