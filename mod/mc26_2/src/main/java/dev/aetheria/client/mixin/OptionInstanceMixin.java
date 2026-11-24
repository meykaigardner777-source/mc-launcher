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
    private void aetheria$optOverrides(CallbackInfoReturnable<Object> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if ((Object) this == mc.options.gamma()) {
            ClientConfig cfg = ClientConfig.get();
            if (mc.level != null && cfg.getBool("util_fullbright", false)) {
                cir.setReturnValue(Double.valueOf(cfg.getDouble("util_fullbright_level", 12.0)));
            }
        } else if ((Object) this == mc.options.framerateLimit()) {
            if (mc.getWindow() != null && !mc.isWindowActive() && ClientConfig.get().getBool("perf_unfocused_fps", true)) {
                cir.setReturnValue(Integer.valueOf(30));
            }
        }
    }
}