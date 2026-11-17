package dev.aetheria.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedModMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

public class ModMenuScreen extends Screen {
    private final SharedModMenu menu;

    public ModMenuScreen() { this(null); }
    public ModMenuScreen(String initialModId) {
        super(Component.literal("Aetheria Client"));
        this.menu = new SharedModMenu(initialModId);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        menu.render(McBridge.draw(g, Minecraft.getInstance()), this.width, this.height, mx, my);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Minecraft mc = Minecraft.getInstance();
            if (menu.mouseClicked(McBridge.draw(null, mc), this.width, this.height, event.x(), event.y(), new SharedModMenu.Actions() {
                @Override public void openHudEditor() { mc.gui.setScreen(new HudEditorScreen()); }
                @Override public void closeMenu() { mc.gui.setScreen(null); }
            })) return true;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && menu.mouseDragged(event.x())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && menu.mouseReleased()) return true;
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return menu.mouseScrolled(sy) || super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (menu.keyPressedCode(event.key())) return true;
        if (menu.keyPressed(event.key() == InputConstants.KEY_BACKSPACE, event.key() == InputConstants.KEY_ESCAPE)) return true;
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        String s = event.codepointAsString();
        if (s != null && s.length() == 1 && menu.charTyped(s.charAt(0))) return true;
        return super.charTyped(event);
    }
}