package dev.aetheria.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedHudEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
    private final SharedHudEditor editor = new SharedHudEditor();
    public HudEditorScreen() { super(Component.literal("Aetheria HUD Editor")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        editor.setShiftHeld(InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT));
        editor.render(McBridge.draw(g, mc), McBridge.data(mc), this.width, this.height, mx, my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int btn = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? 0 : (event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : -1);
        if (btn >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (editor.mouseClicked(McBridge.draw(null, mc), McBridge.data(mc), this.width, this.height, event.x(), event.y(), btn, new SharedHudEditor.Actions() {
                @Override public void backToMenu() { mc.gui.setScreen(new ModMenuScreen()); }
                @Override public void openModSettings(String modId) { mc.gui.setScreen(new ModMenuScreen(modId)); }
            })) return true;
        }
        return super.mouseClicked(event, dbl);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return (event.button() == InputConstants.MOUSE_BUTTON_LEFT && editor.mouseDragged(McBridge.draw(null, Minecraft.getInstance()), McBridge.data(Minecraft.getInstance()), this.width, this.height, event.x(), event.y())) || super.mouseDragged(event, dx, dy); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return (event.button() == InputConstants.MOUSE_BUTTON_LEFT && editor.mouseReleased()) || super.mouseReleased(event); }
    @Override public boolean keyPressed(KeyEvent event) { return editor.keyPressed(event.key(), this.width, this.height) || super.keyPressed(event); }
}
