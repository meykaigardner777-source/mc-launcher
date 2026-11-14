package dev.aetheria.client.mixin;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void aetheria$renderHud(float tickDelta, CallbackInfo ci) { McBridge.renderInGame(); }
    @Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$cancelCrosshair(CallbackInfoReturnable<Boolean> cir) { if (ClientState.shouldCancelVanillaCrosshair()) cir.setReturnValue(false); }
}