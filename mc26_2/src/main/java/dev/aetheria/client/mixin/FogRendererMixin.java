package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.PowderedSnowFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AtmosphericFogEnvironment.class, WaterFogEnvironment.class, LavaFogEnvironment.class, PowderedSnowFogEnvironment.class})
public class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("TAIL"), require = 0)
    private void aetheria$clearFog(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (fog == null) return;
        ClientConfig cfg = ClientConfig.get();
        boolean isAtm = (Object) this instanceof AtmosphericFogEnvironment;
        if ((isAtm && cfg.getBool("vis_no_fog", false)) || (!isAtm && cfg.getBool("vis_clear_lava_water", false))) {
            fog.environmentalStart = 100000.0F;
            fog.environmentalEnd = 100000.0F;
            fog.renderDistanceStart = 100000.0F;
            fog.renderDistanceEnd = 100000.0F;
        }
    }
}