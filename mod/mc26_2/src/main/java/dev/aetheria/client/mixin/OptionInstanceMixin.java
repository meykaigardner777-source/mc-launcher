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
        ClientConfig cfg = ClientConfig.get(); Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (cfg.getBool("util_fullbright", false) && mc.level != null && (Object) this == mc.options.gamma()) {
            cir.setReturnValue(Double.valueOf(cfg.getDouble("util_fullbright_level", 12.0)));
        } else if (cfg.getBool("perf_unfocused_fps", true) && !mc.isWindowActive() && (Object) this == mc.options.framerateLimit()) {
            cir.setReturnValue(Integer.valueOf(30));
        }
    }
}
