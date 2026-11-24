package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.gui.AetheriaTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "init", at = @At("HEAD"), cancellable = true, require = 0)
    private void aetheria$customTitle(CallbackInfo ci) {
        if (ClientConfig.get().getBool("ui_custom_title", true)) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> { if (mc.gui.screen() instanceof TitleScreen) mc.gui.setScreen(new AetheriaTitleScreen(false)); });
            ci.cancel();
        }
    }
}