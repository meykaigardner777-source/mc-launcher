package dev.aetheria.client;
import com.mojang.blaze3d.platform.InputConstants;
public final class AetheriaKeyMap {
    public static int map(String name, int fallback) {
        if (name == null) return fallback;
        switch (name.toUpperCase()) {
            case "RSHIFT": return InputConstants.KEY_RSHIFT; case "LSHIFT": return InputConstants.KEY_LSHIFT;
            case "M": return InputConstants.KEY_M; case "H": return InputConstants.KEY_H;
            case "K": return InputConstants.KEY_K; case "P": return InputConstants.KEY_P;
            case "F6": return InputConstants.KEY_F6; case "F7": return InputConstants.KEY_F7; case "F8": return InputConstants.KEY_F8;
            case "INSERT": return InputConstants.KEY_INSERT; case "C": return InputConstants.KEY_C;
            case "V": return InputConstants.KEY_V; case "Z": return InputConstants.KEY_Z;
            case "X": return InputConstants.KEY_X; case "G": return InputConstants.KEY_G;
            case "R": return InputConstants.KEY_R; case "TAB": return InputConstants.KEY_TAB;
            case "ALT": return InputConstants.KEY_LALT; default: return fallback;
        }
    }
}