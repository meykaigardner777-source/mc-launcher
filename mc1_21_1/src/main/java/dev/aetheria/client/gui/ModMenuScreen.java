package dev.aetheria.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedModMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModMenuScreen extends Screen {
    private final SharedModMenu menu;
    public ModMenuScreen() { this(null); }
    public ModMenuScreen(String initialModId) { super(Component.literal("Aetheria Client")); this.menu = new SharedModMenu(initialModId); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) { menu.render(McBridge.draw(g, Minecraft.getInstance()), this.width, this.height, mx, my); }
    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (menu.mouseClicked(McBridge.draw(null, mc), this.width, this.height, mx, my, new SharedModMenu.Actions() {
                @Override public void openHudEditor() { mc.setScreen(new HudEditorScreen()); }
                @Override public void closeMenu() { mc.setScreen(null); }
            })) return true;
        }
        return super.mouseClicked(mx, my, btn);
    }
    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && menu.mouseDragged(mx)) return true;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0 && menu.mouseReleased()) return true;
        return super.mouseReleased(mx, my, btn);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return menu.mouseScrolled(sy) || super.mouseScrolled(mx, my, sx, sy); }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menu.keyPressed(keyCode == InputConstants.KEY_BACKSPACE, keyCode == InputConstants.KEY_ESCAPE)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean charTyped(char codePoint, int modifiers) {
        if (menu.charTyped(codePoint)) return true;
        return super.charTyped(codePoint, modifiers);
    }
}
