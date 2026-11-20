package dev.aetheria.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

public class AetheriaTitleScreen extends Screen {
    private final SharedTitleScreen titleUi;
    public AetheriaTitleScreen() { this(false); }
    public AetheriaTitleScreen(boolean editorMode) { super(Component.literal("Aetheria Title")); this.titleUi = new SharedTitleScreen(editorMode); }
    @Override public boolean isPauseScreen() { return false; }

    private SharedTitleScreen.HostActions actions(Minecraft mc) {
        return new SharedTitleScreen.HostActions() {
            @Override public void openSingleplayer() { mc.gui.setScreen(new SelectWorldScreen(AetheriaTitleScreen.this)); }
            @Override public void openMultiplayer() { mc.gui.setScreen(new JoinMultiplayerScreen(AetheriaTitleScreen.this)); }
            @Override public void openRealms() {
                try { mc.gui.setScreen((Screen) Class.forName("com.mojang.realmsclient.RealmsMainScreen").getConstructor(Screen.class).newInstance(AetheriaTitleScreen.this)); }
                catch (Throwable ignored) { mc.gui.setScreen(new JoinMultiplayerScreen(AetheriaTitleScreen.this)); }
            }
            @Override public void openOptions() { mc.gui.setScreen(new OptionsScreen(AetheriaTitleScreen.this, mc.options)); }
            @Override public void openClientMenu() { mc.gui.setScreen(new ModMenuScreen()); }
            @Override public void quitGame() { mc.gui.setScreen(new ConfirmScreen(b -> { if (b) mc.stop(); else mc.gui.setScreen(AetheriaTitleScreen.this); }, Component.literal("Quit Game"), Component.literal("Exit Minecraft?"))); }
            @Override public void exitEditor() { mc.gui.setScreen(new AetheriaTitleScreen(false)); }
        };
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) { titleUi.render(McBridge.draw(g, Minecraft.getInstance()), this.width, this.height, mx, my); }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && titleUi.mouseClicked(McBridge.draw(null, Minecraft.getInstance()), this.width, this.height, event.x(), event.y(), actions(Minecraft.getInstance()))) return true;
        return super.mouseClicked(event, dbl);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return (event.button() == InputConstants.MOUSE_BUTTON_LEFT && titleUi.mouseDragged(event.x(), event.y())) || super.mouseDragged(event, dx, dy); }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return (event.button() == InputConstants.MOUSE_BUTTON_LEFT && titleUi.mouseReleased()) || super.mouseReleased(event); }
    @Override public boolean keyPressed(KeyEvent event) { return titleUi.keyPressedCode(event.key()) || titleUi.keyPressed(event.key() == InputConstants.KEY_BACKSPACE, event.key() == InputConstants.KEY_ESCAPE) || super.keyPressed(event); }
    @Override public boolean charTyped(CharacterEvent event) { String s = event.codepointAsString(); return (s != null && s.length() == 1 && titleUi.charTyped(s.charAt(0))) || super.charTyped(event); }
}