package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$filterParticles(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        if (options == null) return;
        ClientConfig cfg = ClientConfig.get();
        boolean noRain = cfg.getBool("part_hide_rain", false);
        boolean noPortal = cfg.getBool("part_hide_portal", false);
        boolean noDust = cfg.getBool("part_hide_dust", false);
        if (!noRain && !noPortal && !noDust) return;
        try {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
            String path = id != null ? id.getPath() : "";
            if (noRain && path.contains("rain")) cir.setReturnValue(null);
            else if (noPortal && path.contains("portal")) cir.setReturnValue(null);
            else if (noDust && (path.equals("block") || path.contains("block_crumble") || path.contains("dust"))) cir.setReturnValue(null);
        } catch (Throwable ignored) {}
    }
}