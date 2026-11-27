package dev.aetheria.client.mixin;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"))
    private void aetheria$onPress(long window, int button, int action, int mods, CallbackInfo ci) {
        boolean inWorld = Minecraft.getInstance().screen == null;
        if (button == 0) ClientState.setMouseButton(true, action != 0, inWorld);
        else if (button == 1) ClientState.setMouseButton(false, action != 0, inWorld);
    }
}