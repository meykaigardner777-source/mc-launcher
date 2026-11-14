package dev.aetheria.client.gui;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedModMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ModMenuScreen extends Screen {
    private final SharedModMenu menu = new SharedModMenu();
    public ModMenuScreen() { super(Component.literal("Aetheria Client")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) { menu.render(McBridge.draw(g, Minecraft.getInstance()), this.width, this.height, mx, my); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Minecraft mc = Minecraft.getInstance();
            if (menu.mouseClicked(McBridge.draw(null, mc), this.width, this.height, event.x(), event.y(), new SharedModMenu.Actions() {
                @Override public void openHudEditor() { mc.setScreen(new HudEditorScreen()); }
                @Override public void closeMenu() { mc.setScreen(null); }
            })) return true;
        }
        return super.mouseClicked(event, dbl);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return menu.mouseScrolled(this.width, this.height, sy) || super.mouseScrolled(mx, my, sx, sy); }
}