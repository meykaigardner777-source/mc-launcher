package dev.aetheria.client.gui;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedModMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Mouse;

public class ModMenuScreen extends Screen {
    private final SharedModMenu menu = new SharedModMenu();
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
    @Override public void handleMouse() {
        super.handleMouse();
        int dw = Mouse.getEventDWheel();
        if (dw != 0) menu.mouseScrolled(this.width, this.height, dw);
    }
}