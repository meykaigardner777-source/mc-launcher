package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow private BlockPos destroyBlockPos;
    @Shadow private float destroyProgress;
    @Shadow private boolean isDestroying;

    @Inject(method = "startDestroyBlock", at = @At("RETURN"), require = 0)
    private void aetheria$onStartBreak(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        ClientState.setBlockBreak(this.isDestroying, this.destroyBlockPos.getX(), this.destroyBlockPos.getY(), this.destroyBlockPos.getZ(), this.destroyProgress);
    }

    @Inject(method = "continueDestroyBlock", at = @At("RETURN"), require = 0)
    private void aetheria$onContinueBreak(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        ClientState.setBlockBreak(this.isDestroying, this.destroyBlockPos.getX(), this.destroyBlockPos.getY(), this.destroyBlockPos.getZ(), this.destroyProgress);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), require = 0)
    private void aetheria$onStopBreak(CallbackInfo ci) {
        ClientState.setBlockBreak(false, 0, 0, 0, 0.0F);
    }
}