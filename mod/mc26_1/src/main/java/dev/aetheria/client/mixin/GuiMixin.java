package dev.aetheria.client.mixin;
import dev.aetheria.client.McBridge;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aetheria$renderHud(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) { McBridge.renderInGame(g); }
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void aetheria$cancelCrosshair(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) { if (ClientState.shouldCancelVanillaCrosshair()) ci.cancel(); }
}