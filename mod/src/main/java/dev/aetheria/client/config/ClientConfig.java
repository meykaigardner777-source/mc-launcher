package dev.aetheria.client.config;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientConfig {
    private static final ClientConfig INSTANCE = new ClientConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String menuKey = "RSHIFT";
    public long updatedAt = 0L;
    public final Map<String, HudModule> hudModules = new LinkedHashMap<>();
    public final Map<String, JsonElement> options = new LinkedHashMap<>();

    public static class HudModule {
        public String id;
        public String label;
        public String optId;
        public boolean enabled;
        public double x;
        public double y;
        public double scale = 1.0;
        public double opacity = 0.85;
        public boolean bg = true;
        public boolean border = true;
        public boolean chroma = false;
        public String color = "#ffffff";
        public String anchor = "top-left";
    }

    public static ClientConfig get() {
        return INSTANCE;
    }

    private Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("aetheria-client.json");
    }

    private void initDefaults() {
        hudModules.clear();
        addDefault("fps", "FPS Counter", "hud_fps", true, 0.015, 0.025, "#ffffff");
        addDefault("coords", "Coordinates", "hud_coords", true, 0.015, 0.085, "#ffffff");
        addDefault("biome", "Biome", "hud_biome", true, 0.015, 0.175, "#a5f3fc");
        addDefault("ping", "Live Ping", "pvp_ping_hud", true, 0.015, 0.230, "#86efac");
        addDefault("cps", "CPS Counter", "pvp_cps", true, 0.015, 0.285, "#ffffff");
        addDefault("keystrokes", "Keystrokes", "pvp_keystrokes", true, 0.015, 0.345, "#ffffff");
        addDefault("togglesprint", "ToggleSprint", "pvp_togglesprint", true, 0.015, 0.545, "#fde68a");
        addDefault("potions", "Potion Effects", "pvp_potion_hud", true, 0.015, 0.615, "#c4b5fd");
        addDefault("compass", "Compass Strip", "hud_compass", true, 0.340, 0.020, "#ffffff");
        addDefault("clock", "System Clock", "hud_clock", false, 0.855, 0.025, "#ffffff");
        addDefault("session", "Session Timer", "hud_session", true, 0.835, 0.080, "#ffffff");
        addDefault("server_ip", "Server IP", "hud_srv_ip", false, 0.840, 0.135, "#93c5fd");
        addDefault("day_count", "Day Counter", "hud_day_count", false, 0.875, 0.190, "#fde047");
        addDefault("durability", "Tool Durability", "hud_durability", true, 0.825, 0.650, "#ffffff");
        addDefault("armor", "Armor Status", "pvp_armor_hud", true, 0.790, 0.720, "#ffffff");
        addDefault("combo", "Combo Counter", "pvp_combo_hud", false, 0.435, 0.600, "#fca5a5");
        addDefault("reach", "Reach Display", "pvp_reach_hud", false, 0.435, 0.660, "#fca5a5");

        setDefaultOpt("hud_fps", true);
        setDefaultOpt("hud_coords", true);
        setDefaultOpt("hud_biome", true);
        setDefaultOpt("hud_compass", true);
        setDefaultOpt("hud_session", true);
        setDefaultOpt("hud_durability", true);
        setDefaultOpt("hud_snap_grid", true);
        setDefaultOpt("hud_shadow", true);
        setDefaultOpt("pvp_keystrokes", true);
        setDefaultOpt("pvp_cps", true);
        setDefaultOpt("pvp_togglesprint", true);
        setDefaultOpt("pvp_armor_hud", true);
        setDefaultOpt("pvp_potion_hud", true);
        setDefaultOpt("pvp_ping_hud", true);
        setDefaultOpt("pvp_low_fire", true);
        setDefaultOpt("pvp_xhair_style", "classic");
        setDefaultOpt("pvp_xhair_size", 6);
        setDefaultOpt("pvp_xhair_gap", 2);
        setDefaultOpt("cam_zoom", true);
        setDefaultOpt("cam_zoom_fac", 4);
        setDefaultOpt("cam_zoom_key", "C");
        setDefaultOpt("ctl_menu_key", "RSHIFT");
    }

    private void addDefault(String id, String label, String optId, boolean en, double x, double y, String col) {
        HudModule m = new HudModule();
        m.id = id; m.label = label; m.optId = optId; m.enabled = en;
        m.x = x; m.y = y; m.color = col;
        hudModules.put(id, m);
    }

    private void setDefaultOpt(String id, Object val) {
        options.putIfAbsent(id, GSON.toJsonTree(val));
    }

    public synchronized void load() {
        initDefaults();
        Path p = getConfigPath();
        if (!Files.exists(p)) {
            save();
            return;
        }
        try {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.has("menuKey")) menuKey = root.get("menuKey").getAsString();
            if (root.has("updatedAt")) updatedAt = root.get("updatedAt").getAsLong();

            if (root.has("options") && root.get("options").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("options").entrySet()) {
                    options.put(e.getKey(), e.getValue());
                }
            }
            if (root.has("hudModules") && root.get("hudModules").isJsonObject()) {
                JsonObject mods = root.getAsJsonObject("hudModules");
                for (Map.Entry<String, JsonElement> e : mods.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    HudModule m = hudModules.getOrDefault(e.getKey(), new HudModule());
                    m.id = e.getKey();
                    if (o.has("label")) m.label = o.get("label").getAsString();
                    if (o.has("optId") && !o.get("optId").isJsonNull()) m.optId = o.get("optId").getAsString();
                    if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
                    if (o.has("x")) m.x = o.get("x").getAsDouble();
                    if (o.has("y")) m.y = o.get("y").getAsDouble();
                    if (o.has("scale")) m.scale = o.get("scale").getAsDouble();
                    if (o.has("opacity")) m.opacity = o.get("opacity").getAsDouble();
                    if (o.has("bg")) m.bg = o.get("bg").getAsBoolean();
                    if (o.has("border")) m.border = o.get("border").getAsBoolean();
                    if (o.has("chroma")) m.chroma = o.get("chroma").getAsBoolean();
                    if (o.has("color")) m.color = o.get("color").getAsString();
                    if (o.has("anchor")) m.anchor = o.get("anchor").getAsString();
                    if (m.optId != null && options.containsKey(m.optId)) {
                        m.enabled = getBool(m.optId, m.enabled);
                    }
                    hudModules.put(e.getKey(), m);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void save() {
        updatedAt = System.currentTimeMillis();
        menuKey = getString("ctl_menu_key", menuKey);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        root.addProperty("updatedAt", updatedAt);
        root.addProperty("menuKey", menuKey);

        JsonObject mods = new JsonObject();
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = e.getValue();
            if (m.optId != null) m.enabled = getBool(m.optId, m.enabled);
            JsonObject o = new JsonObject();
            o.addProperty("label", m.label);
            if (m.optId != null) o.addProperty("optId", m.optId);
            o.addProperty("enabled", m.enabled);
            o.addProperty("x", Math.round(m.x * 1000.0) / 1000.0);
            o.addProperty("y", Math.round(m.y * 1000.0) / 1000.0);
            o.addProperty("scale", Math.round(m.scale * 100.0) / 100.0);
            o.addProperty("opacity", Math.round(m.opacity * 100.0) / 100.0);
            o.addProperty("bg", m.bg);
            o.addProperty("border", m.border);
            o.addProperty("chroma", m.chroma);
            o.addProperty("color", m.color);
            o.addProperty("anchor", m.anchor);
            mods.add(e.getKey(), o);
        }
        root.add("hudModules", mods);

        JsonObject opts = new JsonObject();
        for (Map.Entry<String, JsonElement> e : options.entrySet()) {
            opts.add(e.getKey(), e.getValue());
        }
        root.add("options", opts);

        try {
            Path p = getConfigPath();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public boolean getBool(String id, boolean def) {
        JsonElement el = options.get(id);
        return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) ? el.getAsBoolean() : def;
    }

    public double getDouble(String id, double def) {
        JsonElement el = options.get(id);
        return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) ? el.getAsDouble() : def;
    }

    public String getString(String id, String def) {
        JsonElement el = options.get(id);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : def;
    }

    public void setOption(String id, Object val) {
        options.put(id, GSON.toJsonTree(val));
        for (HudModule m : hudModules.values()) {
            if (id.equals(m.optId) && val instanceof Boolean b) m.enabled = b;
        }
        save();
    }

    public void toggleModule(HudModule m) {
        m.enabled = !m.enabled;
        if (m.optId != null) options.put(m.optId, new JsonPrimitive(m.enabled));
        save();
    }

    public double getZoomFactor() {
        return Math.max(2.0, getDouble("cam_zoom_fac", 4.0));
    }

    public void resetLayout() {
        Map<String, JsonElement> kept = new LinkedHashMap<>(options);
        initDefaults();
        options.putAll(kept);
        save();
    }
}
