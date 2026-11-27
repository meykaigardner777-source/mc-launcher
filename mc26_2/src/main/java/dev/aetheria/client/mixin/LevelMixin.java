package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$overrideRain(float delta, CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || (Object) this != mc.level) return;
        String w = ClientConfig.get().getString("vis_weather_mode", "vanilla");
        if ("clear".equalsIgnoreCase(w)) cir.setReturnValue(Float.valueOf(0.0F));
        else if ("rain".equalsIgnoreCase(w) || "thunder".equalsIgnoreCase(w)) cir.setReturnValue(Float.valueOf(1.0F));
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true, require = 0)
    private void aetheria$overrideThunder(float delta, CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || (Object) this != mc.level) return;
        String w = ClientConfig.get().getString("vis_weather_mode", "vanilla");
        if ("clear".equalsIgnoreCase(w) || "rain".equalsIgnoreCase(w)) cir.setReturnValue(Float.valueOf(0.0F));
        else if ("thunder".equalsIgnoreCase(w)) cir.setReturnValue(Float.valueOf(1.0F));
    }
}