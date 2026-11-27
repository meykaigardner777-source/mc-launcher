package dev.aetheria.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void aetheria$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.gui.screen() == null;
        if (info.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ClientState.setMouseButton(true, action != 0, inWorld);
        } else if (info.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            ClientState.setMouseButton(false, action != 0, inWorld);
            if (action != 0 && inWorld && mc.player != null) {
                if (mc.player.getMainHandItem().is(Items.ENDER_PEARL) || mc.player.getOffhandItem().is(Items.ENDER_PEARL)) {
                    ClientState.triggerPearlCooldown();
                }
            }
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$onScroll(long handle, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && ClientState.isZooming() && ClientConfig.get().getBool("cam_zoom_scroll", true)) {
            ClientState.adjustZoomScroll(vertical);
            ci.cancel();
        }
    }
}