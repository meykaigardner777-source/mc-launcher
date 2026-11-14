package dev.aetheria.client.mixin;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Entity.class)
public class EntityMixin {
    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double aetheria$slowYaw(double yRot) { return ((Object) this == Minecraft.getInstance().player && ClientState.shouldSlowMouse()) ? yRot * 0.35 : yRot; }
    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double aetheria$slowPitch(double xRot) { return ((Object) this == Minecraft.getInstance().player && ClientState.shouldSlowMouse()) ? xRot * 0.35 : xRot; }
}