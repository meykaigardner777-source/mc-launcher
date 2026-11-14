package dev.aetheria.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), require = 0)
    private static void aetheria$lowFire(Minecraft mc, PoseStack ps, CallbackInfo ci) {
        if (ClientConfig.get().getBool("pvp_low_fire", true)) ps.translate(0.0, -0.32, 0.0);
    }
}