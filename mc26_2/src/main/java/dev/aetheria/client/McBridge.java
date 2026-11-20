package dev.aetheria.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aetheria.client.common.*;
import dev.aetheria.client.gui.HudEditorScreen;
import dev.aetheria.client.mixin.MinecraftAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.scores.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class McBridge {
    private static final Identifier CIRCLE_TEX = Identifier.fromNamespaceAndPath("aetheria", "circle_aa_512");
    private static final Identifier RING_TEX_1 = Identifier.fromNamespaceAndPath("aetheria", "ring_aa_1_512");
    private static final Identifier RING_TEX_2 = Identifier.fromNamespaceAndPath("aetheria", "ring_aa_2_512");
    private static final Identifier RING_TEX_3 = Identifier.fromNamespaceAndPath("aetheria", "ring_aa_3_512");
    private static boolean texturesInit = false, debugChecked = false;
    private static Method debugMethod = null;
    private static Object debugTarget = null;

    private McBridge() {}

    private static void regAaTex(Minecraft mc, Identifier id, String name, NativeImage img) {
        DynamicTexture dt = new DynamicTexture(() -> name, img);
        AetheriaCleanFont.enableLinearFilter(dt);
        mc.getTextureManager().register(id, dt);
    }

    private static void ensureTextures(Minecraft mc) {
        if (texturesInit || mc == null) return;
        texturesInit = true;
        try {
            int s = 512; double center = 256.0, rad = 255.0;
            NativeImage circleImg = new NativeImage(NativeImage.Format.RGBA, s, s, false);
            NativeImage ring1 = new NativeImage(NativeImage.Format.RGBA, s, s, false);
            NativeImage ring2 = new NativeImage(NativeImage.Format.RGBA, s, s, false);
            NativeImage ring3 = new NativeImage(NativeImage.Format.RGBA, s, s, false);
            for (int py = 0; py < s; py++) {
                for (int px = 0; px < s; px++) {
                    double d = Math.hypot(px + 0.5 - center, py + 0.5 - center);
                    double aFill = Math.max(0.0, Math.min(1.0, (rad - d) * 0.5));
                    circleImg.setPixel(px, py, (((int) Math.round(aFill * 255.0)) << 24) | 0x00FFFFFF);
                    ring1.setPixel(px, py, (((int) Math.round(Math.min(aFill, Math.max(0.0, Math.min(1.0, (d - 224.0) * 0.5))) * 255.0)) << 24) | 0x00FFFFFF);
                    ring2.setPixel(px, py, (((int) Math.round(Math.min(aFill, Math.max(0.0, Math.min(1.0, (d - 200.0) * 0.5))) * 255.0)) << 24) | 0x00FFFFFF);
                    ring3.setPixel(px, py, (((int) Math.round(Math.min(aFill, Math.max(0.0, Math.min(1.0, (d - 176.0) * 0.5))) * 255.0)) << 24) | 0x00FFFFFF);
                }
            }
            regAaTex(mc, CIRCLE_TEX, "aetheria_circle_aa_512", circleImg);
            regAaTex(mc, RING_TEX_1, "aetheria_ring_aa_1_512", ring1);
            regAaTex(mc, RING_TEX_2, "aetheria_ring_aa_2_512", ring2);
            regAaTex(mc, RING_TEX_3, "aetheria_ring_aa_3_512", ring3);
        } catch (Throwable ignored) {}
    }

    private static boolean checkDebugOverlayVisible(Minecraft mc) {
        if (!debugChecked && mc != null) {
            debugChecked = true;
            try {
                Object entries = mc.getClass().getField("debugEntries").get(mc);
                if (entries != null) { debugTarget = entries; debugMethod = entries.getClass().getMethod("isOverlayVisible"); }
            } catch (Throwable ignored) {}
            if (debugMethod == null) {
                try {
                    Object ov = mc.getClass().getMethod("getDebugOverlay").invoke(mc);
                    if (ov != null) { debugTarget = ov; debugMethod = ov.getClass().getMethod("showDebugScreen"); }
                } catch (Throwable ignored) {}
            }
        }
        if (debugTarget != null && debugMethod != null) {
            try { return Boolean.TRUE.equals(debugMethod.invoke(debugTarget)); } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void emitWorldGizmos(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        ClientConfig cfg = ClientConfig.get();
        String curDim = mc.level.dimension().identifier().toString();

        if (cfg.getBool("wp_world_markers", true) && "block".equalsIgnoreCase(cfg.getString("wp_style", "lunar"))) {
            boolean sync = cfg.getBool("wp_nether_sync", true);
            for (ClientConfig.Waypoint wp : cfg.waypoints) {
                double[] pos = ClientState.resolveWaypointCoords(wp, curDim, sync);
                if (pos == null) continue;
                int col = ClientState.parseHexColor(wp.color, 0xFF22D3EE);
                AABB box = new AABB(pos[0], pos[1], pos[2], pos[0] + 1.0, pos[1] + 1.0, pos[2] + 1.0).inflate(0.015);
                Gizmos.cuboid(box, GizmoStyle.strokeAndFill(col, 2.5F, ClientState.withAlpha(col, 65))).setAlwaysOnTop();
            }
        }

        if (cfg.getBool("hb_enabled", false)) {
            int col = cfg.getBool("hb_chroma", false)
                ? ClientState.getChromaColor(1.5, 0.85, 45.0, 0.5, 0.5)
                : ClientState.parseHexColor(cfg.getString("hb_color", "#22d3ee"), 0xFF22D3EE);
            float lw = (float) Math.max(1.0, Math.min(5.0, cfg.getDouble("hb_line_width", 2.0)));
            int fill = ClientState.scaleAlpha(col, cfg.getDouble("hb_fill_alpha", 0.12));
            boolean doPlayers = cfg.getBool("hb_players", true), doMobs = cfg.getBool("hb_mobs", true);
            boolean doItems = cfg.getBool("hb_items", false), doProj = cfg.getBool("hb_projectiles", true);
            boolean hideSelf = cfg.getBool("hb_hide_self", true), eyeLine = cfg.getBool("hb_eye_line", true), lookVec = cfg.getBool("hb_look_vec", true);

            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player && hideSelf) continue;
                if (e.distanceToSqr(mc.player) > 64.0 * 64.0) continue;
                boolean match = (e instanceof Player && doPlayers) || (e instanceof ItemEntity && doItems)
                    || (e instanceof Projectile && doProj) || (e instanceof LivingEntity && !(e instanceof Player) && doMobs);
                if (!match) continue;
                AABB bb = e.getBoundingBox();
                Gizmos.cuboid(bb, (fill >>> 24) > 2 ? GizmoStyle.strokeAndFill(col, lw, fill) : GizmoStyle.stroke(col, lw));
                if (e instanceof LivingEntity) {
                    if (eyeLine) { double ey = e.getEyeY(); Gizmos.cuboid(new AABB(bb.minX, ey - 0.01, bb.minZ, bb.maxX, ey + 0.01, bb.maxZ), GizmoStyle.stroke(0xFFEF4444, Math.max(1.0F, lw * 0.8F))); }
                    if (lookVec) { Vec3 eyePos = e.getEyePosition(1.0F); Gizmos.line(eyePos, eyePos.add(e.getViewVector(1.0F).scale(2.0)), 0xFF3B82F6, Math.max(1.0F, lw * 0.8F)); }
                }
            }
        }

        if (cfg.getBool("bb_enabled", false) && ClientState.isBreakingActive()) {
            float p = ClientState.getBreakProgress();
            int bx = ClientState.getBreakX(), by = ClientState.getBreakY(), bz = ClientState.getBreakZ();
            String style = cfg.getString("bb_style", "shrink").toLowerCase();
            int col = cfg.getBool("bb_progress_color", true)
                ? (p < 0.5F ? ClientState.lerpColor(0xFF10B981, 0xFFF59E0B, p * 2.0F) : ClientState.lerpColor(0xFFF59E0B, 0xFFEF4444, (p - 0.5F) * 2.0F))
                : ClientState.parseHexColor(cfg.getString("bb_color", "#22d3ee"), 0xFF22D3EE);
            float lw = (float) Math.max(1.0, Math.min(5.0, cfg.getDouble("bb_line_width", 2.5)));
            int fill = ClientState.scaleAlpha(col, cfg.getDouble("bb_fill_alpha", 0.30));
            AABB box;
            if ("expand".equals(style)) { double ins = 0.46 * (1.0 - p); box = new AABB(bx + ins, by + ins, bz + ins, bx + 1 - ins, by + 1 - ins, bz + 1 - ins).inflate(0.003); }
            else if ("rise".equals(style)) box = new AABB(bx, by, bz, bx + 1.0, by + Math.max(0.05, p), bz + 1.0).inflate(0.003);
            else if ("slide_down".equals(style)) box = new AABB(bx, by, bz, bx + 1.0, by + Math.max(0.05, 1.0 - p), bz + 1.0).inflate(0.003);
            else if ("pulse".equals(style)) { double w = 0.03 * Math.sin(System.currentTimeMillis() / 70.0); box = new AABB(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0).inflate(0.006 + w); }
            else { double ins = 0.45 * p; box = new AABB(bx + ins, by + ins, bz + ins, bx + 1 - ins, by + 1 - ins, bz + 1 - ins).inflate(0.002); }
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(col, lw, fill));
        }
    }

    public static void renderInGame(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof HudEditorScreen) return;
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

    public static UiBridge.UiDraw draw(GuiGraphicsExtractor g, Minecraft mc) {
        return new UiBridge.UiDraw() {
            @Override public void fill(int x1, int y1, int x2, int y2, int argb) { if (g != null) g.fill(x1, y1, x2, y2, argb); }
            @Override public void text(String txt, int x, int y, int argb, boolean shadow) {
                if (g == null || txt == null) return;
                if (ClientConfig.get().getBool("ui_clean_font", true)) AetheriaCleanFont.draw(g, mc, txt, x, y, argb, shadow);
                else g.text(mc.font, txt, x, y, argb, shadow);
            }
            @Override public int textWidth(String txt) {
                if (txt == null) return 0;
                return ClientConfig.get().getBool("ui_clean_font", true) ? AetheriaCleanFont.width(mc, txt) : mc.font.width(txt);
            }
            @Override public void pushTransform(float tx, float ty, float scale) { if (g != null) { g.pose().pushMatrix(); g.pose().translate(tx, ty); g.pose().scale(scale, scale); } }
            @Override public void popTransform() { if (g != null) g.pose().popMatrix(); }
            @Override public boolean supportsItemIcons() { return true; }
            @Override public String currentUsername() { return mc.getUser() != null ? mc.getUser().getName() : "Player"; }
            @Override public String currentUuid() { return mc.getUser() != null && mc.getUser().getProfileId() != null ? mc.getUser().getProfileId().toString() : ""; }
            @Override public boolean switchAccount(String username, String uuidStr) {
                try {
                    if (username == null || username.trim().isEmpty()) return false;
                    String clean = username.trim();
                    UUID id = (uuidStr != null && !uuidStr.isEmpty()) ? UUID.fromString(uuidStr) : UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8));
                    for (Constructor<?> c : User.class.getConstructors()) {
                        Class<?>[] pt = c.getParameterTypes();
                        if (pt.length == 6 && pt[0] == String.class && pt[1] == UUID.class && pt[2] == String.class) {
                            ((MinecraftAccessor) (Object) mc).aetheria$setUser((User) c.newInstance(clean, id, "0", Optional.empty(), Optional.empty(), pt[5].isEnum() ? pt[5].getEnumConstants()[0] : null));
                            return true;
                        }
                        if (pt.length == 5 && pt[0] == String.class && pt[1] == UUID.class && pt[2] == String.class) {
                            ((MinecraftAccessor) (Object) mc).aetheria$setUser((User) c.newInstance(clean, id, "0", Optional.empty(), pt[4].isEnum() ? pt[4].getEnumConstants()[0] : null));
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}
                return false;
            }
            @Override public void playClickSound() { if (ClientConfig.get().getBool("ui_click_sound", true)) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); }
            @Override public void drawArmorSlot(int slotIndex, int x, int y) {
                if (g == null || mc.player == null) return;
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
                if (slotIndex < 0 || slotIndex >= slots.length) return;
                ItemStack st = mc.player.getItemBySlot(slots[slotIndex]);
                if (!st.isEmpty()) { g.item(st, x, y); g.itemDecorations(mc.font, st, x, y); }
            }
            @Override public void drawRoundedRect(int x, int y, int w, int h, int r, int argb) {
                if (g == null || w <= 0 || h <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
                r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
                if (r <= 1) { g.fill(x, y, x + w, y + h, argb); return; }
                ensureTextures(mc);
                g.fill(x + r, y, x + w - r, y + h, argb);
                if (h > r * 2) { g.fill(x, y + r, x + r, y + h - r, argb); g.fill(x + w - r, y + r, x + w, y + h - r, argb); }
                g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_TEX, x, y, 0.0F, 0.0F, r, r, 256, 256, 512, 512, argb);
                g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_TEX, x + w - r, y, 256.0F, 0.0F, r, r, 256, 256, 512, 512, argb);
                g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_TEX, x + w - r, y + h - r, 256.0F, 256.0F, r, r, 256, 256, 512, 512, argb);
                g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_TEX, x, y + h - r, 0.0F, 256.0F, r, r, 256, 256, 512, 512, argb);
            }
            @Override public void drawRoundedOutline(int x, int y, int w, int h, int r, int thickness, String style, int argb, int argb2) {
                if (g == null || w <= 1 || h <= 1 || thickness <= 0 || ((argb >>> 24) & 0xFF) == 0) return;
                int t = Math.max(1, Math.min(3, thickness)); r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
                g.fill(x + r, y, x + w - r, y + t, argb); g.fill(x + r, y + h - t, x + w - r, y + h, argb2);
                g.fill(x, y + r, x + t, y + h - r, argb); g.fill(x + w - t, y + r, x + w, y + h - r, argb2);
                if (r > 1) {
                    ensureTextures(mc);
                    Identifier ring = t >= 3 ? RING_TEX_3 : (t == 2 ? RING_TEX_2 : RING_TEX_1);
                    g.blit(RenderPipelines.GUI_TEXTURED, ring, x, y, 0.0F, 0.0F, r, r, 256, 256, 512, 512, argb);
                    g.blit(RenderPipelines.GUI_TEXTURED, ring, x + w - r, y, 256.0F, 0.0F, r, r, 256, 256, 512, 512, argb2);
                    g.blit(RenderPipelines.GUI_TEXTURED, ring, x + w - r, y + h - r, 256.0F, 256.0F, r, r, 256, 256, 512, 512, argb2);
                    g.blit(RenderPipelines.GUI_TEXTURED, ring, x, y + h - r, 0.0F, 256.0F, r, r, 256, 256, 512, 512, argb);
                }
            }
            @Override public void drawCircleFilled(int cx, int cy, int r, int argb) { if (g != null && r > 0 && ((argb >>> 24) & 0xFF) != 0) { ensureTextures(mc); g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_TEX, cx - r, cy - r, 0.0F, 0.0F, r * 2, r * 2, 512, 512, 512, 512, argb); } }
            @Override public void drawCircle(int cx, int cy, int r, int t, int col) { if (g != null && r > 0 && ((col >>> 24) & 0xFF) != 0) { ensureTextures(mc); g.blit(RenderPipelines.GUI_TEXTURED, t >= 3 ? RING_TEX_3 : (t == 2 ? RING_TEX_2 : RING_TEX_1), cx - r, cy - r, 0.0F, 0.0F, r * 2, r * 2, 512, 512, 512, 512, col); } }
            @Override public void drawPaperdoll(int x1, int y1, int x2, int y2, int size, float mouseX, float mouseY) {
                if (g == null || mc.player == null) return;
                try {
                    int sw = mc.getWindow().getGuiScaledWidth();
                    float lx = mouseX != 0.0F ? mouseX : (((x1 + x2) * 0.5F < sw * 0.5F) ? (x2 + 20.0F) : (x1 - 20.0F));
                    float ly = mouseY != 0.0F ? mouseY : ((y1 + y2) * 0.45F);
                    InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, Math.max(14, size), 0.0625F, lx, ly, mc.player);
                } catch (Throwable ignored) {}
            }
        };
    }

    public static UiBridge.HudData data(Minecraft mc) {
        return new UiBridge.HudData() {
            @Override public boolean hasWorld() { return mc.player != null && mc.level != null; }
            @Override public String dimensionId() { return mc.level.dimension().identifier().toString(); }
            @Override public boolean isSingleplayer() { return mc.getSingleplayerServer() != null; }
            @Override public String worldKey() {
                if (mc.getSingleplayerServer() != null) {
                    try { String n = mc.getSingleplayerServer().getWorldData().getLevelName(); if (n != null && !n.isEmpty()) return "sp:" + n.toLowerCase(); } catch (Throwable ignored) {}
                    return "sp:local";
                }
                if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null && !mc.getCurrentServer().ip.isEmpty()) {
                    return "srv:" + mc.getCurrentServer().ip.trim().toLowerCase();
                }
                return "global";
            }
            @Override public boolean showSurvivalBars() { return mc.player != null && !mc.player.getAbilities().instabuild && !mc.player.isSpectator(); }
            @Override public int fps() { return mc.getFps(); }
            @Override public int x() { return mc.player.blockPosition().getX(); } @Override public int y() { return mc.player.blockPosition().getY(); } @Override public int z() { return mc.player.blockPosition().getZ(); }
            @Override public String facing() { String n = mc.player.getDirection().getName(); return n.substring(0, 1).toUpperCase() + n.substring(1); }
            @Override public String biome() {
                String k = mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(x -> x.identifier().getPath()).orElse("unknown");
                StringBuilder sb = new StringBuilder(); for (String p : k.replace('_', ' ').split(" ")) if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
                return sb.toString().trim();
            }
            @Override public int pingMs() {
                if (mc.getConnection() == null || mc.player == null) return 0;
                PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (pi == null && mc.getUser() != null) pi = mc.getConnection().getPlayerInfo(mc.getUser().getProfileId());
                return pi != null ? pi.getLatency() : 0;
            }
            @Override public boolean isSprinting() { return mc.player.isSprinting(); }
            @Override public String[] activePotions() {
                List<String> out = new ArrayList<>();
                boolean numerals = ClientConfig.get().getBool("potions_numerals", true), timers = ClientConfig.get().getBool("potions_timers", true);
                for (MobEffectInstance eff : mc.player.getActiveEffects()) {
                    String name = eff.getEffect().value().getDisplayName().getString();
                    if (numerals && eff.getAmplifier() > 0) name += " " + toRoman(eff.getAmplifier() + 1);
                    if (timers) name += eff.isInfiniteDuration() ? " **:**" : (" " + (Math.max(0, eff.getDuration() / 20) / 60) + ":" + String.format("%02d", Math.max(0, eff.getDuration() / 20) % 60));
                    out.add(name);
                }
                return out.isEmpty() ? new String[]{"No Potions"} : out.toArray(new String[0]);
            }
            @Override public float yaw() { return mc.player.getYRot(); }
            @Override public String serverIp() { return mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer"; }
            @Override public long dayCount() { return mc.level.getOverworldClockTime() / 24000L; }
            @Override public long worldTime() { return mc.level.getOverworldClockTime(); }
            @Override public String heldToolDurability() { ItemStack h = mc.player.getMainHandItem(); return (!h.isEmpty() && h.isDamageableItem()) ? h.getHoverName().getString() + ": " + (h.getMaxDamage() - h.getDamageValue()) + "/" + h.getMaxDamage() : "Tool: --"; }
            @Override public String offhandDurability() { ItemStack h = mc.player.getOffhandItem(); return (!h.isEmpty() && h.isDamageableItem()) ? "Off: " + (h.getMaxDamage() - h.getDamageValue()) + "/" + h.getMaxDamage() : "Off: --"; }
            @Override public String[] armorSlotReadouts() {
                boolean pct = ClientConfig.get().getBool("armor_percent", true);
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                String[] out = new String[4];
                for (int i = 0; i < 4; i++) {
                    ItemStack st = mc.player.getItemBySlot(slots[i]);
                    if (st.isEmpty()) out[i] = null; else if (!st.isDamageableItem()) out[i] = "";
                    else { int rem = st.getMaxDamage() - st.getDamageValue(); out[i] = pct ? (Math.round(100.0 * rem / st.getMaxDamage()) + "%") : (rem + "/" + st.getMaxDamage()); }
                }
                return out;
            }
            @Override public String armorStatus() {
                boolean pct = ClientConfig.get().getBool("armor_percent", true); List<String> list = new ArrayList<>();
                for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack st = mc.player.getItemBySlot(s);
                    if (!st.isEmpty() && st.isDamageableItem()) { int rem = st.getMaxDamage() - st.getDamageValue(); list.add(s.getName().substring(0, 1).toUpperCase() + ": " + (pct ? (Math.round(100.0 * rem / st.getMaxDamage()) + "%") : (rem + "/" + st.getMaxDamage()))); }
                }
                return list.isEmpty() ? "Armor: None" : String.join(" | ", list);
            }
            @Override public boolean isMoveW() { return mc.options.keyUp.isDown(); } @Override public boolean isMoveA() { return mc.options.keyLeft.isDown(); }
            @Override public boolean isMoveS() { return mc.options.keyDown.isDown(); } @Override public boolean isMoveD() { return mc.options.keyRight.isDown(); }
            @Override public boolean isJumpDown() { return mc.options.keyJump.isDown(); }
            @Override public boolean targetPlayerInView(double range) { return targetInView(mc, range, true); }
            @Override public boolean targetEntityInView(double range) { return targetInView(mc, range, false); }
            @Override public double posX() { return mc.player.getX(); } @Override public double posY() { return mc.player.getY(); } @Override public double posZ() { return mc.player.getZ(); }
            @Override public float pitch() { return mc.player.getXRot(); }
            @Override public boolean isSneaking() { return mc.player.isShiftKeyDown(); }
            @Override public boolean isMoveCtrl() { return mc.options.keySprint.isDown(); }
            @Override public double camX() { Camera c = mc.gameRenderer.mainCamera(); return c != null ? c.position().x : posX(); }
            @Override public double camY() { Camera c = mc.gameRenderer.mainCamera(); return c != null ? c.position().y : posY() + 1.62; }
            @Override public double camZ() { Camera c = mc.gameRenderer.mainCamera(); return c != null ? c.position().z : posZ(); }
            @Override public float camYaw() { Camera c = mc.gameRenderer.mainCamera(); return c != null ? c.yRot() : yaw(); }
            @Override public float camPitch() { Camera c = mc.gameRenderer.mainCamera(); return c != null ? c.xRot() : pitch(); }
            @Override public String scoreboardTitle() {
                try {
                    if (mc.level == null) return "";
                    Objective obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
                    return obj != null ? obj.getDisplayName().getString() : "";
                } catch (Throwable t) { return ""; }
            }
            @Override public String[] scoreboardLines() {
                try {
                    if (mc.level == null) return new String[0];
                    Scoreboard sb = mc.level.getScoreboard();
                    Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
                    if (obj == null) return new String[0];
                    List<PlayerScoreEntry> scores = new ArrayList<>();
                    for (PlayerScoreEntry e : sb.listPlayerScores(obj)) if (!e.isHidden()) scores.add(e);
                    scores.sort((a, b) -> Integer.compare(b.value(), a.value()));
                    int limit = Math.min(15, scores.size()); String[] out = new String[limit];
                    for (int i = 0; i < limit; i++) {
                        PlayerScoreEntry e = scores.get(i);
                        Component nameComp = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(sb.getPlayersTeam(e.owner()), e.ownerName());
                        out[i] = nameComp.getString() + "\t" + e.value();
                    }
                    return out;
                } catch (Throwable t) { return new String[0]; }
            }
            @Override public int moonPhase() { return (int) (((worldTime() / 24000L) % 8L + 8L) % 8L); }
            @Override public String weather() { return mc.level.isThundering() ? "Thunder" : (mc.level.isRaining() ? "Rain" : "Clear"); }
            @Override public int blockLight() { return mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, mc.player.blockPosition()); }
            @Override public int skyLight() { return mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, mc.player.blockPosition()); }
            @Override public float health() { return mc.player.getHealth(); } @Override public float maxHealth() { return mc.player.getMaxHealth(); }
            @Override public float absorption() { return mc.player.getAbsorptionAmount(); }
            @Override public int foodLevel() { return mc.player.getFoodData().getFoodLevel(); } @Override public float saturation() { return mc.player.getFoodData().getSaturationLevel(); }
            @Override public int airSupply() { return mc.player.getAirSupply(); } @Override public int maxAirSupply() { return mc.player.getMaxAirSupply(); }
            @Override public int onlinePlayers() { return mc.getConnection() != null ? mc.getConnection().getOnlinePlayers().size() : 1; }
            @Override public int renderedEntities() { int c = 0; for (Entity ignored : mc.level.entitiesForRendering()) c++; return c; }
            @Override public int totalEntities() { return renderedEntities(); }
            @Override public String lookingAtBlock() { return mc.hitResult instanceof BlockHitResult bhr ? mc.level.getBlockState(bhr.getBlockPos()).getBlock().getName().getString() : "Air"; }
            @Override public String lookingAtEntity() { return mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getName().getString() : "None"; }
            @Override public String targetName() { return mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getName().getString() : ""; }
            @Override public float targetHealth() { return (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) ? le.getHealth() : 0.0F; }
            @Override public float targetMaxHealth() { return (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) ? le.getMaxHealth() : 20.0F; }
            @Override public double targetDistance() { return (mc.hitResult instanceof EntityHitResult ehr) ? mc.player.distanceTo(ehr.getEntity()) : 0.0; }
            @Override public int[] nearbyEntityRadar() {
                List<Integer> pts = new ArrayList<>(); double rad = Math.toRadians(mc.player.getYRot()), cos = Math.cos(rad), sin = Math.sin(rad);
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
                    double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                    if (dx * dx + dz * dz > 48.0 * 48.0) continue;
                    pts.add(-(int) Math.round((dx * cos + dz * sin) * 0.55)); pts.add((int) Math.round((-dx * sin + dz * cos) * 0.55));
                    if (pts.size() >= 64) break;
                }
                int[] out = new int[pts.size()]; for (int i = 0; i < pts.size(); i++) out[i] = pts.get(i); return out;
            }
            @Override public int countItem(String kw) {
                int c = 0; String l = kw.toLowerCase();
                for (int i = 0; i < 36; i++) { ItemStack s = mc.player.getInventory().getItem(i); if (!s.isEmpty() && s.getItem().toString().toLowerCase().contains(l)) c += s.getCount(); }
                ItemStack off = mc.player.getOffhandItem(); if (!off.isEmpty() && off.getItem().toString().toLowerCase().contains(l)) c += off.getCount(); return c;
            }
            @Override public int freeSlots() { int f = 0; for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) f++; return f; }
            @Override public int usedSlots() { return 36 - freeSlots(); }
            @Override public float attackCooldownPct() { return mc.player.getAttackStrengthScale(0.0F); }
            @Override public boolean isBlockingOrCooling() { return mc.player.isBlocking(); }
            @Override public double fovDegrees() { return ((Number) mc.options.fov().get()).doubleValue(); }
            @Override public double gammaPercent() { return ((Number) mc.options.gamma().get()).doubleValue() * 100.0; }
            @Override public boolean isF1Hidden() { return mc.gui.hud.isHidden(); }
            @Override public boolean isChatOpen() { return mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().contains("Chat"); }
            @Override public boolean isInventoryOpen() { return mc.gui.screen() != null && !isChatOpen(); }
            @Override public boolean isF3Open() { return checkDebugOverlayVisible(mc); }
        };
    }

    private static String toRoman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X"; default -> String.valueOf(n); };
    }
}