package dev.aetheria.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedHudEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
    private final SharedHudEditor editor = new SharedHudEditor();
    public HudEditorScreen() { super(Component.literal("Aetheria HUD Editor")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        editor.setShiftHeld(InputConstants.isKeyDown(win, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(win, InputConstants.KEY_RSHIFT));
        editor.render(McBridge.draw(g, mc), McBridge.data(mc), this.width, this.height, mx, my);
    }
    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 || btn == 1) {
            Minecraft mc = Minecraft.getInstance();
            if (editor.mouseClicked(McBridge.draw(null, mc), McBridge.data(mc), this.width, this.height, mx, my, btn, new SharedHudEditor.Actions() {
                @Override public void backToMenu() { mc.setScreen(new ModMenuScreen()); }
                @Override public void openModSettings(String modId) { mc.setScreen(new ModMenuScreen(modId)); }
            })) return true;
        }
        return super.mouseClicked(mx, my, btn);
    }
    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0) { Minecraft mc = Minecraft.getInstance(); if (editor.mouseDragged(McBridge.draw(null, mc), McBridge.data(mc), this.width, this.height, mx, my)) return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) { if (btn == 0 && editor.mouseReleased()) return true; return super.mouseReleased(mx, my, btn); }
}