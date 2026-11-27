package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$freeLookTurn(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && ClientState.isFreeLook()) {
            ClientState.addFreeLookDelta((float) (yRot * 0.15), (float) (xRot * 0.15));
            ci.cancel();
        }
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
    private double aetheria$slowYaw(double yRot) {
        return ((Object) this == Minecraft.getInstance().player && ClientState.shouldSlowMouse()) ? yRot * 0.35 : yRot;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0)
    private double aetheria$slowPitch(double xRot) {
        return ((Object) this == Minecraft.getInstance().player && ClientState.shouldSlowMouse()) ? xRot * 0.35 : xRot;
    }
}