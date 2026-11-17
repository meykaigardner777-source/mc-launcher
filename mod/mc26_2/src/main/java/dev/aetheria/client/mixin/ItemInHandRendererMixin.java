package dev.aetheria.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void aetheria$lowShield(net.minecraft.client.player.AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack ps, Object buffers, int light, CallbackInfo ci) {
        if (stack.is(Items.SHIELD) && ClientConfig.get().getBool("pvp_low_shield", true)) {
            ClientConfig cfg = ClientConfig.get();
            double scale = cfg.getDouble("pvp_low_shield_scale", 0.85);
            double y = cfg.getDouble("pvp_low_shield_y", -0.25);
            double x = cfg.getDouble("pvp_low_shield_x", 0.0);

            ps.translate(x, y, 0.0);
            ps.scale((float) scale, (float) scale, (float) scale);
        }
    }
}
