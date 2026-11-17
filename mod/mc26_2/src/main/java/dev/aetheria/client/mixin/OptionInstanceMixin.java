package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionInstance.class)
public class OptionInstanceMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$fullbright(CallbackInfoReturnable<Object> cir) {
        if (ClientConfig.get().getBool("util_fullbright", false)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null && mc.options != null && (Object) this == mc.options.gamma()) {
                cir.setReturnValue(Double.valueOf(ClientConfig.get().getDouble("util_fullbright_level", 12.0)));
            }
        }
    }
}