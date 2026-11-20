package dev.aetheria.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class AetheriaCleanFont {
    private static final Identifier ATLAS_TEX = Identifier.fromNamespaceAndPath("aetheria", "clean_font_atlas");
    private static final int TEX_W = 1024, TEX_H = 256, CELL = 32, COLS = 32;
    private static final int[] MC_COLORS = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final int[] DRAW_W = new int[128];
    private static final int[] ADV_W = new int[128];
    private static boolean init = false, failed = false;

    private AetheriaCleanFont() {}

    private static synchronized void ensureInit(Minecraft mc) {
        if (init || failed || mc == null) return;
        init = true;
        try {
            BufferedImage bi = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = bi.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            Font font = new Font("Segoe UI", Font.PLAIN, 16);
            if (!"Segoe UI".equalsIgnoreCase(font.getFamily())) font = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
            g2.setFont(font);
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int baseLine = 15;
            for (int c = 32; c <= 127; c++) {
                char ch = (c == 127) ? '\u00b0' : (char) c;
                int idx = c - 32, col = idx % COLS, row = idx / COLS;
                int adv2 = Math.max(2, fm.charWidth(ch));
                ADV_W[idx] = Math.max(1, (int) Math.round(adv2 / 2.0));
                DRAW_W[idx] = Math.max(2, (adv2 + 3) / 2);
                g2.drawString(String.valueOf(ch), col * CELL + 1, row * CELL + baseLine);
            }
            g2.dispose();
            NativeImage ni = new NativeImage(NativeImage.Format.RGBA, TEX_W, TEX_H, false);
            for (int y = 0; y < TEX_H; y++) {
                for (int x = 0; x < TEX_W; x++) ni.setPixel(x, y, bi.getRGB(x, y));
            }
            mc.getTextureManager().register(ATLAS_TEX, new DynamicTexture(() -> "aetheria_clean_font", ni));
        } catch (Throwable t) {
            failed = true;
        }
    }

    private static int glyphIndex(char ch) {
        if (ch >= 32 && ch <= 126) return ch - 32;
        if (ch == '\u00b0') return 95;
        return -1;
    }

    private static boolean canRenderAll(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\u00a7' && i + 1 < s.length()) { i++; continue; }
            if (glyphIndex(ch) < 0) return false;
        }
        return true;
    }

    public static int width(Minecraft mc, String txt) {
        if (txt == null || txt.isEmpty()) return 0;
        ensureInit(mc);
        if (failed || !canRenderAll(txt)) return mc.font.width(txt);
        int w = 0;
        for (int i = 0; i < txt.length(); i++) {
            char ch = txt.charAt(i);
            if (ch == '\u00a7' && i + 1 < txt.length()) { i++; continue; }
            int idx = glyphIndex(ch);
            w += (idx >= 0) ? ADV_W[idx] : 4;
        }
        return w;
    }

    public static void draw(GuiGraphicsExtractor g, Minecraft mc, String txt, int x, int y, int argb, boolean shadow) {
        if (g == null || txt == null || txt.isEmpty() || ((argb >>> 24) & 0xFF) == 0) return;
        ensureInit(mc);
        if (failed || !canRenderAll(txt)) { g.text(mc.font, txt, x, y, argb, shadow); return; }
        int alpha = (argb >>> 24) & 0xFF, curCol = argb;
        int shCol = ((Math.max(0, alpha / 3) & 0xFF) << 24);
        int cx = x, dy = y - 1;
        for (int i = 0; i < txt.length(); i++) {
            char ch = txt.charAt(i);
            if (ch == '\u00a7' && i + 1 < txt.length()) {
                char code = Character.toLowerCase(txt.charAt(++i));
                int hex = "0123456789abcdef".indexOf(code);
                if (hex >= 0) curCol = (alpha << 24) | MC_COLORS[hex];
                else if (code == 'r') curCol = argb;
                continue;
            }
            int idx = glyphIndex(ch);
            if (idx < 0) continue;
            float u = (idx % COLS) * CELL, v = (idx / COLS) * CELL;
            int dw = DRAW_W[idx], uW = dw * 2;
            if (ch != ' ') {
                if (shadow) g.blit(RenderPipelines.GUI_TEXTURED, ATLAS_TEX, cx + 1, dy + 1, u, v, dw, 10, uW, 20, TEX_W, TEX_H, shCol);
                g.blit(RenderPipelines.GUI_TEXTURED, ATLAS_TEX, cx, dy, u, v, dw, 10, uW, 20, TEX_W, TEX_H, curCol);
            }
            cx += ADV_W[idx];
        }
    }
}