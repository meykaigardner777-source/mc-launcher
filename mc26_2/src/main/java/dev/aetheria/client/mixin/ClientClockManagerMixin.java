package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$overrideSkyTime(CallbackInfoReturnable<Long> cir) {
        String mode = ClientConfig.get().getString("vis_time_mode", "vanilla");
        if ("day".equalsIgnoreCase(mode)) cir.setReturnValue(Long.valueOf(1000L));
        else if ("noon".equalsIgnoreCase(mode)) cir.setReturnValue(Long.valueOf(6000L));
        else if ("sunset".equalsIgnoreCase(mode)) cir.setReturnValue(Long.valueOf(12800L));
        else if ("night".equalsIgnoreCase(mode)) cir.setReturnValue(Long.valueOf(15000L));
        else if ("midnight".equalsIgnoreCase(mode)) cir.setReturnValue(Long.valueOf(18000L));
    }
}