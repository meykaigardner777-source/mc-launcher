package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$toggleSneak(CallbackInfoReturnable<Boolean> cir) {
        if (!ClientState.isBypassToggleSneak() && ClientState.isToggleSneaking() && ClientConfig.get().getBool("pvp_togglesneak", false)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && (Object) this == mc.options.keyShift && mc.gui.screen() == null) {
                cir.setReturnValue(Boolean.TRUE);
            }
        }
    }
}
