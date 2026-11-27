package dev.aetheria.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import dev.aetheria.client.common.SharedWorldMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WorldMapScreen extends Screen {
    private final SharedWorldMap map = new SharedWorldMap();

    public WorldMapScreen() { super(Component.literal("Aetheria Worldmap")); }
    @Override public boolean isPauseScreen() { return false; }

    private SharedWorldMap.Actions actions(Minecraft mc) {
        return new SharedWorldMap.Actions() {
            @Override public void openMinimapSettings() { mc.gui.setScreen(new ModMenuScreen("minimap", "world")); }
            @Override public void closeMap() { mc.gui.setScreen(null); }
        };
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        map.render(McBridge.draw(g, mc), McBridge.data(mc), this.width, this.height, mx, my);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        int btn = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? 0 : (event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : -1);
        if (btn >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (map.mouseClicked(McBridge.draw(null, mc), McBridge.data(mc), this.width, this.height, event.x(), event.y(), btn, actions(mc))) return true;
        }
        return super.mouseClicked(event, dbl);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return (event.button() == InputConstants.MOUSE_BUTTON_LEFT && map.mouseDragged(event.x(), event.y())) || super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return (event.button() == InputConstants.MOUSE_BUTTON_LEFT && map.mouseReleased()) || super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return map.mouseScrolled(sy) || super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (map.keyPressed(event.key() == InputConstants.KEY_BACKSPACE, event.key() == InputConstants.KEY_ESCAPE, event.key() == 257 || event.key() == 335, McBridge.data(mc))) return true;
        int mapKey = ClientState.nameToGlfwKey(ClientConfig.get().getString("ctl_worldmap_key", "M"), InputConstants.KEY_M);
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == mapKey) {
            mc.gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean charTyped(CharacterEvent event) {
        String s = event.codepointAsString();
        if (s != null && s.length() == 1 && map.charTyped(s.charAt(0))) return true;
        return super.charTyped(event);
    }
}