package dev.aetheria.client.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.*;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void aetheria$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        boolean inWorld = Minecraft.getInstance().gui.screen() == null;
        if (info.button() == InputConstants.MOUSE_BUTTON_LEFT) ClientState.setMouseButton(true, action != 0, inWorld);
        else if (info.button() == InputConstants.MOUSE_BUTTON_RIGHT) ClientState.setMouseButton(false, action != 0, inWorld);
    }
}