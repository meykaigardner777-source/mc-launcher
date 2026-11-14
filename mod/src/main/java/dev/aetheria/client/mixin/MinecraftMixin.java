package dev.aetheria.client.mixin;

import dev.aetheria.client.AetheriaClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void aetheria$onTick(CallbackInfo ci) {
        AetheriaClientMod.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void aetheria$onAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.player != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult ehr) {
            double dist = mc.player.getEyePosition().distanceTo(ehr.getLocation());
            AetheriaClientMod.recordAttack(dist);
        }
    }
}
