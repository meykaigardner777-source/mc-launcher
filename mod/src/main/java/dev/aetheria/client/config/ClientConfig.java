package dev.aetheria.client.hud;

import dev.aetheria.client.AetheriaClientMod;
import dev.aetheria.client.config.ClientConfig;
import dev.aetheria.client.gui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class HudManager {
    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static boolean prevLmb = false;
    private static boolean prevRmb = false;
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    public static void pollClicks(Minecraft mc) {
        if (AetheriaClientMod.windowHandle == 0L || mc.screen != null) return;
        long now = System.currentTimeMillis();
        boolean lmb = GLFW.glfwGetMouseButton(AetheriaClientMod.windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmb = GLFW.glfwGetMouseButton(AetheriaClientMod.windowHandle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (lmb && !prevLmb) LEFT_CLICKS.addLast(now);
        if (rmb && !prevRmb) RIGHT_CLICKS.addLast(now);
        prevLmb = lmb;
        prevRmb = rmb;
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000L) LEFT_CLICKS.removeFirst();
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000L) RIGHT_CLICKS.removeFirst();
    }

    public static int getLeftCps() { return LEFT_CLICKS.size(); }
    public static int getRightCps() { return RIGHT_CLICKS.size(); }

    public static void renderInGame(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        AetheriaClientMod.captureWindow();
        pollClicks(mc);
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen instanceof HudEditorScreen) return;

        renderCrosshair(g, mc);

        ClientConfig cfg = ClientConfig.get();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean shadow = cfg.getBool("hud_shadow", true);

        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (!m.enabled) continue;
            renderModule(g, mc, m, sw, sh, shadow, false);
        }
    }

    public static int[] getModulePixelBounds(Minecraft mc, ClientConfig.HudModule m, int sw, int sh) {
        String[] lines = getModuleLines(mc, m);
        Font font = mc.font;
        int w = 44;
        int h = Math.max(14, lines.length * 11 + 6);
        if ("keystrokes".equals(m.id)) {
            w = 78; h = 68;
        } else {
            int maxW = 0;
            for (String ln : lines) maxW = Math.max(maxW, font.width(ln));
            w = Math.max(36, maxW + 10);
        }
        int px = (int) Math.round(m.x * sw);
        int py = (int) Math.round(m.y * sh);
        return new int[]{px, py, (int) Math.round(w * m.scale), (int) Math.round(h * m.scale)};
    }

    public static void renderModule(GuiGraphics g, Minecraft mc, ClientConfig.HudModule m, int sw, int sh, boolean shadow, boolean editorPreview) {
        int px = (int) Math.round(m.x * sw);
        int py = (int) Math.round(m.y * sh);
        float scale = (float) Math.max(0.5, Math.min(2.5, m.scale));
        int textColor = resolveColor(m);

        g.pose().pushMatrix();
        g.pose().translate((float) px, (float) py);
        g.pose().scale(scale, scale);

        if ("keystrokes".equals(m.id)) {
            renderKeystrokes(g, mc, m, textColor, shadow);
        } else {
            String[] lines = getModuleLines(mc, m);
            int maxW = 0;
            for (String ln : lines) maxW = Math.max(maxW, mc.font.width(ln));
            int boxW = Math.max(36, maxW + 10);
            int boxH = Math.max(14, lines.length * 11 + 6);

            if (m.bg) {
                int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, m.opacity)) * 180.0);
                g.fill(0, 0, boxW, boxH, (alpha << 24) | 0x0B0E14);
            }
            if (m.border) {
                drawBorder(g, 0, 0, boxW, boxH, editorPreview && !m.enabled ? 0x88FFAA44 : 0x55FFFFFF);
            }
            for (int i = 0; i < lines.length; i++) {
                g.drawString(mc.font, lines[i], 5, 4 + i * 11, textColor, shadow);
            }
        }
        g.pose().popMatrix();
    }

    private static void renderKeystrokes(GuiGraphics g, Minecraft mc, ClientConfig.HudModule m, int textColor, boolean shadow) {
        long h = AetheriaClientMod.windowHandle;
        boolean w = h != 0 && GLFW.glfwGetKey(h, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean a = h != 0 && GLFW.glfwGetKey(h, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
        boolean s = h != 0 && GLFW.glfwGetKey(h, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
        boolean d = h != 0 && GLFW.glfwGetKey(h, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
        boolean sp = h != 0 && GLFW.glfwGetKey(h, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean lmb = h != 0 && GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rmb = h != 0 && GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        drawKeyBox(g, mc.font, 26, 0, 24, 20, "W", w, m, textColor, shadow);
        drawKeyBox(g, mc.font, 0, 22, 24, 20, "A", a, m, textColor, shadow);
        drawKeyBox(g, mc.font, 26, 22, 24, 20, "S", s, m, textColor, shadow);
        drawKeyBox(g, mc.font, 52, 22, 24, 20, "D", d, m, textColor, shadow);
        drawKeyBox(g, mc.font, 0, 44, 37, 14, "LMB " + getLeftCps(), lmb, m, textColor, shadow);
        drawKeyBox(g, mc.font, 39, 44, 37, 14, "RMB " + getRightCps(), rmb, m, textColor, shadow);
        drawKeyBox(g, mc.font, 0, 60, 76, 12, "SPACE", sp, m, textColor, shadow);
    }

    private static void drawKeyBox(GuiGraphics g, Font f, int x, int y, int w, int h, String txt, boolean down, ClientConfig.HudModule m, int col, boolean shadow) {
        int bgCol = down ? 0xCCFFFFFF : (((int) (m.opacity * 170) << 24) | 0x0B0E14);
        int fgCol = down ? 0xFF101318 : col;
        if (m.bg || down) g.fill(x, y, x + w, y + h, bgCol);
        if (m.border) drawBorder(g, x, y, w, h, 0x55FFFFFF);
        int tw = f.width(txt);
        g.drawString(f, txt, x + (w - tw) / 2, y + (h - 8) / 2, fgCol, shadow && !down);
    }

    private static String[] getModuleLines(Minecraft mc, ClientConfig.HudModule m) {
        if (mc.player == null || mc.level == null) return new String[]{m.label};
        switch (m.id) {
            case "fps":
                return new String[]{mc.getFps() + " FPS"};
            case "coords": {
                BlockPos pos = mc.player.blockPosition();
                Direction dir = mc.player.getDirection();
                return new String[]{
                    "XYZ: " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ(),
                    "Facing: " + dir.getName().substring(0, 1).toUpperCase() + dir.getName().substring(1)
                };
            }
            case "biome": {
                String key = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("unknown");
                return new String[]{titleCase(key.replace('_', ' '))};
            }
            case "ping": {
                int ms = 0;
                if (mc.getConnection() != null) {
                    PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (pi != null) ms = pi.getLatency();
                }
                return new String[]{ms + " ms"};
            }
            case "cps":
                return new String[]{getLeftCps() + " | " + getRightCps() + " CPS"};
            case "togglesprint":
                return new String[]{mc.player.isSprinting() ? "[Sprinting (Toggled)]" : "[Walking]"};
            case "potions": {
                List<String> out = new ArrayList<>();
                for (MobEffectInstance eff : mc.player.getActiveEffects()) {
                    int sec = Math.max(0, eff.getDuration() / 20);
                    String name = eff.getEffect().value().getDisplayName().getString();
                    out.add(name + " " + (sec / 60) + ":" + String.format("%02d", sec % 60));
                }
                return out.isEmpty() ? new String[]{"No Potions"} : out.toArray(new String[0]);
            }
            case "compass": {
                float yaw = ((mc.player.getYRot() % 360f) + 360f) % 360f;
                String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
                String dir = dirs[Math.round(yaw / 45f) % 8];
                return new String[]{"<<  " + dir + " (" + Math.round(yaw) + "°)  >>"};
            }
            case "clock":
                return new String[]{LocalTime.now().format(CLOCK_FMT)};
            case "session": {
                long s = Math.max(0, (System.currentTimeMillis() - AetheriaClientMod.START_TIME_MS) / 1000L);
                return new String[]{String.format("Session: %02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)};
            }
            case "server_ip":
                return new String[]{mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer"};
            case "day_count":
                return new String[]{"Day " + (mc.level.getDayTime() / 24000L)};
            case "durability": {
                ItemStack hand = mc.player.getMainHandItem();
                if (!hand.isEmpty() && hand.isDamageableItem()) {
                    int left = hand.getMaxDamage() - hand.getDamageValue();
                    return new String[]{hand.getHoverName().getString() + ": " + left + "/" + hand.getMaxDamage()};
                }
                return new String[]{"Tool: --"};
            }
            case "armor": {
                List<String> list = new ArrayList<>();
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack st = mc.player.getItemBySlot(slot);
                    if (!st.isEmpty() && st.isDamageableItem()) {
                        int pct = (int) Math.round(100.0 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
                        list.add(slot.getName().substring(0, 1).toUpperCase() + ": " + pct + "%");
                    }
                }
                return list.isEmpty() ? new String[]{"Armor: None"} : new String[]{String.join(" | ", list)};
            }
            case "combo":
                return new String[]{AetheriaClientMod.getComboCount() + " Hit Combo"};
            case "reach":
                return new String[]{String.format("%.2f blocks", AetheriaClientMod.getLastReach())};
            default:
                return new String[]{m.label};
        }
    }

    private static void renderCrosshair(GuiGraphics g, Minecraft mc) {
        ClientConfig cfg = ClientConfig.get();
        String style = cfg.getString("pvp_xhair_style", "classic");
        if ("vanilla".equalsIgnoreCase(style)) return;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6));
        int gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2));
        int col = 0xFFFFFFFF;

        if ("dot".equalsIgnoreCase(style)) {
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
        } else {
            if (!"t_shape".equalsIgnoreCase(style)) {
                g.fill(cx, cy - gap - sz, cx + 1, cy - gap, col);
            }
            g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + sz, col);
            g.fill(cx - gap - sz, cy, cx - gap, cy + 1, col);
            g.fill(cx + gap + 1, cy, cx + gap + 1 + sz, cy + 1, col);
        }
    }

    private static int resolveColor(ClientConfig.HudModule m) {
        if (m.chroma) {
            float hue = (System.currentTimeMillis() % 3000L) / 3000.0f;
            return 0xFF000000 | (Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0x00FFFFFF);
        }
        try {
            String hex = m.color.startsWith("#") ? m.color.substring(1) : m.color;
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col);
        g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col);
        g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static String titleCase(String s) {
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
