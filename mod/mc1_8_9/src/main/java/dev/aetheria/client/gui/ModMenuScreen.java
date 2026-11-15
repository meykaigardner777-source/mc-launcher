package dev.aetheria.client.gui;

import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedModMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class ModMenuScreen extends Screen {
    private final SharedModMenu menu;
    public ModMenuScreen() { this(null); }
    public ModMenuScreen(String initialModId) { this.menu = new SharedModMenu(initialModId); }
    @Override public boolean shouldPauseGame() { return false; }

    @Override public void render(int mx, int my, float pt) { menu.render(McBridge.draw(MinecraftClient.getInstance()), this.width, this.height, mx, my); }
    @Override protected void mouseClicked(int mx, int my, int btn) {
        if (btn == 0) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (menu.mouseClicked(McBridge.draw(mc), this.width, this.height, mx, my, new SharedModMenu.Actions() {
                @Override public void openHudEditor() { mc.setScreen(new HudEditorScreen()); }
                @Override public void closeMenu() { mc.setScreen(null); }
            })) return;
        }
        super.mouseClicked(mx, my, btn);
    }
    @Override protected void mouseDragged(int mx, int my, int btn, long time) {
        if (btn == 0 && menu.mouseDragged(mx)) return;
        super.mouseDragged(mx, my, btn, time);
    }
    @Override protected void mouseReleased(int mx, int my, int btn) {
        if (btn == 0 && menu.mouseReleased()) return;
        super.mouseReleased(mx, my, btn);
    }
    @Override public void handleMouse() {
        super.handleMouse();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) menu.mouseScrolled(dw > 0 ? 1.0 : -1.0);
    }
    @Override protected void keyPressed(char id, int keyCode) {
        if (menu.keyPressed(keyCode == Keyboard.KEY_BACK, keyCode == Keyboard.KEY_ESCAPE)) return;
        if (menu.charTyped(id)) return;
        super.keyPressed(id, keyCode);
    }
}
