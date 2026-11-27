package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$hideVanillaCracks(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        if (progress >= 0 && cfg.getBool("bb_enabled", false) && cfg.getBool("bb_hide_vanilla", false)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && breakerId == mc.player.getId()) ci.cancel();
        }
    }
}