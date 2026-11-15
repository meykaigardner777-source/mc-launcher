package dev.aetheria.client.gui;

import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedHudEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

public class HudEditorScreen extends Screen {
    private final SharedHudEditor editor = new SharedHudEditor();
    @Override public boolean shouldPauseGame() { return false; }

    @Override public void render(int mx, int my, float pt) { MinecraftClient mc = MinecraftClient.getInstance(); editor.render(McBridge.draw(mc), McBridge.data(mc), this.width, this.height, mx, my); }
    @Override protected void mouseClicked(int mx, int my, int btn) {
        if (btn == 0 || btn == 1) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (editor.mouseClicked(McBridge.draw(mc), McBridge.data(mc), this.width, this.height, mx, my, btn, new SharedHudEditor.Actions() {
                @Override public void backToMenu() { mc.setScreen(new ModMenuScreen()); }
                @Override public void openModSettings(String modId) { mc.setScreen(new ModMenuScreen(modId)); }
            })) return;
        }
        super.mouseClicked(mx, my, btn);
    }
    @Override protected void mouseDragged(int mx, int my, int btn, long time) {
        if (btn == 0) { MinecraftClient mc = MinecraftClient.getInstance(); if (editor.mouseDragged(McBridge.draw(mc), McBridge.data(mc), this.width, this.height, mx, my)) return; }
        super.mouseDragged(mx, my, btn, time);
    }
    @Override protected void mouseReleased(int mx, int my, int btn) { if (btn == 0 && editor.mouseReleased()) return; super.mouseReleased(mx, my, btn); }
}
