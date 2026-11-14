package dev.aetheria.client.gui;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedHudEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
    private final SharedHudEditor editor = new SharedHudEditor();
    public HudEditorScreen() { super(Component.literal("Aetheria HUD Editor")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) { Minecraft mc = Minecraft.getInstance(); editor.render(McBridge.draw(g, mc), McBridge.data(mc), this.width, this.height, mx, my); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Minecraft mc = Minecraft.getInstance();
            if (editor.mouseClicked(McBridge.draw(null, mc), McBridge.data(mc), this.width, this.height, event.x(), event.y(), () -> mc.setScreen(new ModMenuScreen()))) return true;
        }
        return super.mouseClicked(event, dbl);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) { Minecraft mc = Minecraft.getInstance(); if (editor.mouseDragged(McBridge.draw(null, mc), McBridge.data(mc), this.width, this.height, event.x(), event.y())) return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && editor.mouseReleased()) return true; return super.mouseReleased(event); }
}