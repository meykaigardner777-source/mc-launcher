package dev.aetheria.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aetheria.client.common.ClientConfig;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), require = 0)
    private void aetheria$viewmodelAndItems(AbstractClientPlayer player, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress, PoseStack ps, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.getBool("vm_enabled", false)) {
            boolean main = hand == InteractionHand.MAIN_HAND;
            double vx = cfg.getDouble(main ? "vm_main_x" : "vm_off_x", 0.0);
            double vy = cfg.getDouble(main ? "vm_main_y" : "vm_off_y", 0.0);
            double vz = cfg.getDouble(main ? "vm_main_z" : "vm_off_z", 0.0);
            float vs = (float) Math.max(0.3, Math.min(2.0, cfg.getDouble(main ? "vm_main_scale" : "vm_off_scale", 1.0)));
            ps.translate(vx, vy, vz);
            if (Math.abs(vs - 1.0F) > 0.01F) ps.scale(vs, vs, vs);
        }
        if (stack.is(Items.SHIELD) && cfg.getBool("pvp_low_shield", false)) {
            if (!cfg.getBool("pvp_low_shield_blocking", false) || player.isBlocking()) {
                float sc = (float) Math.max(0.4, Math.min(1.2, cfg.getDouble("pvp_low_shield_scale", 0.85)));
                ps.translate(cfg.getDouble("pvp_low_shield_x", 0.0), cfg.getDouble("pvp_low_shield_y", -0.22), 0.0);
                ps.scale(sc, sc, sc);
            }
        }
        if (stack.is(Items.TOTEM_OF_UNDYING) && cfg.getBool("pvp_small_totem", false)) {
            float ts = (float) Math.max(0.3, Math.min(1.2, cfg.getDouble("pvp_small_totem_scale", 0.60)));
            ps.translate(0.0, cfg.getDouble("pvp_small_totem_y", -0.12), 0.0);
            ps.scale(ts, ts, ts);
        }
    }
}