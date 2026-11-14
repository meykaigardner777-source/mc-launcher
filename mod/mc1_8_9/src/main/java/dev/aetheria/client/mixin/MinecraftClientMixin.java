package dev.aetheria.client.mixin;
import dev.aetheria.client.AetheriaClientMod;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void aetheria$onTick(CallbackInfo ci) { AetheriaClientMod.onClientTick((MinecraftClient) (Object) this); }
    @Inject(method = "doAttack", at = @At("HEAD"), require = 0)
    private void aetheria$onAttack(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player != null && mc.result != null && mc.result.type == HitResult.Type.ENTITY && mc.result.pos != null) {
            ClientState.recordAttack(mc.player.getCameraPosVec(1.0F).distanceTo(mc.result.pos));
        }
    }
}