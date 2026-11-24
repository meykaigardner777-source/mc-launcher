package dev.aetheria.client.mixin;
import dev.aetheria.client.AetheriaClientMod;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void aetheria$onTick(CallbackInfo ci) { AetheriaClientMod.onClientTick((Minecraft) (Object) this); }
    @Inject(method = "startAttack", at = @At("RETURN"))
    private void aetheria$onAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (Boolean.TRUE.equals(cir.getReturnValue()) && mc.player != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult ehr) {
            ClientState.recordAttack(mc.player.getEyePosition().distanceTo(ehr.getLocation()));
        }
    }
}