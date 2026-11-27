package dev.aetheria.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.SharedChatCoordPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Unique private final SharedChatCoordPicker aetheria$picker = new SharedChatCoordPicker();

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void aetheria$renderCoordPicker(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        Screen scr = (Screen) (Object) this;
        aetheria$picker.render(McBridge.draw(g, Minecraft.getInstance()), scr.width, scr.height, mx, my);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$clickCoordPicker(MouseButtonEvent event, boolean dbl, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Minecraft mc = Minecraft.getInstance();
            Screen scr = (Screen) (Object) this;
            if (aetheria$picker.mouseClicked(McBridge.draw(null, mc), McBridge.data(mc), scr.width, scr.height, event.x(), event.y(), 0)) {
                cir.setReturnValue(Boolean.TRUE);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$keyCoordPicker(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (aetheria$picker.isInputFocused()) {
            Minecraft mc = Minecraft.getInstance();
            if (aetheria$picker.keyPressed(event.key() == InputConstants.KEY_BACKSPACE, event.key() == InputConstants.KEY_ESCAPE, event.key() == 257 || event.key() == 335, McBridge.data(mc))) {
                cir.setReturnValue(Boolean.TRUE);
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$charCoordPicker(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (aetheria$picker.isInputFocused()) {
            String s = event.codepointAsString();
            if (s != null && s.length() == 1 && aetheria$picker.charTyped(s.charAt(0))) {
                cir.setReturnValue(Boolean.TRUE);
            }
        }
    }
}