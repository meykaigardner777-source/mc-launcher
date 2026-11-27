package dev.aetheria.client.mixin;

import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow private void refreshTrimmedMessages() {}

    @Unique private static final SimpleDateFormat AETHERIA_CHAT_TIME = new SimpleDateFormat("HH:mm");
    @Unique private String aetheria$lastRawMsg = "";
    @Unique private long aetheria$lastMsgMs = 0L;
    @Unique private int aetheria$dupCount = 1;

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
    private Component aetheria$enhanceChatMessage(Component contents) {
        if (contents == null) return null;
        ClientConfig cfg = ClientConfig.get();
        Minecraft mc = Minecraft.getInstance();
        String raw = contents.getString();
        String selfName = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "";

        boolean mentioned = ClientState.processIncomingChat(raw, selfName);
        if (mentioned && cfg.getBool("chat_mention_sound", true) && mc != null && mc.getSoundManager() != null) {
            try { mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.15F)); } catch (Throwable ignored) {}
        }

        long now = System.currentTimeMillis();
        boolean isDup = false;
        if (cfg.getBool("chat_compact", true) && !raw.isEmpty() && raw.equals(aetheria$lastRawMsg)
                && (now - aetheria$lastMsgMs) < 15000L && !allMessages.isEmpty()) {
            allMessages.remove(0);
            aetheria$dupCount++;
            refreshTrimmedMessages();
            isDup = true;
        } else {
            aetheria$lastRawMsg = raw;
            aetheria$dupCount = 1;
        }
        aetheria$lastMsgMs = now;

        Component out = contents;
        if (mentioned && cfg.getBool("chat_mention_highlight", true)) {
            out = Component.literal("\u00a7e\u2605 \u00a7r").append(out);
        }
        if (cfg.getBool("chat_timestamps", false)) {
            out = Component.literal("\u00a77[" + AETHERIA_CHAT_TIME.format(new Date(now)) + "] \u00a7r").append(out);
        }
        if (isDup) {
            out = out.copy().append(Component.literal(" \u00a77[x" + aetheria$dupCount + "]"));
        }
        return out;
    }
}