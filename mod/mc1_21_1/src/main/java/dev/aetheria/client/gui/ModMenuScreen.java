package dev.aetheria.client.gui;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedModMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModMenuScreen extends Screen {
    private final SharedModMenu menu = new SharedModMenu();
    public ModMenuScreen() { super(Component.literal("Aetheria Client")); }
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
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return menu.mouseScrolled(this.width, this.height, sy) || super.mouseScrolled(mx, my, sx, sy); }
}