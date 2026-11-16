package dev.aetheria.client.common;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ClientConfig {
    private static final ClientConfig INSTANCE = new ClientConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path configPath;
    private long lastPollMs = 0L, lastFileMtimeMs = 0L;

    public String menuKey = "RSHIFT";
    public long updatedAt = 0L;
    public String activeProfile = "Default";
    public final Map<String, Profile> profiles = new LinkedHashMap<String, Profile>();
    public final Map<String, HudModule> hudModules = new LinkedHashMap<String, HudModule>();
    public final Map<String, JsonElement> options = new LinkedHashMap<String, JsonElement>();

    public static class Profile {
        public String name;
        public Map<String, HudModule> modules = new LinkedHashMap<String, HudModule>();
        public Profile() {}
        public Profile(String name) { this.name = name; }
    }

    public static class HudModule {
        public String id, label, category, optId, anchor = "top-left";
        public String color = "#ffffff";
        public String bgColor = "#09090b";
        public String borderColor = "#27272a";
        public boolean enabled, bg = true, border = true, chroma = false, shadow = true;
        public double x, y, scale = 1.0, opacity = 0.75, textOpacity = 1.0;
        public int widthOverride = 0, heightOverride = 0;
        public int borderRadius = 6, borderThickness = 1;
        public int paddingX = 6, paddingY = 4;
        public String bracketStyle = "none"; // "none", "brackets", "parentheses", "pipes"
        public String prefixText = "", suffixText = "";
        public boolean hideInF1 = true, hideInChat = false, hideInInventory = false, hideInF3 = true;
        public String renderMode = "both"; // "both", "text", "icon"
    }

    public static ClientConfig get() { return INSTANCE; }

    public synchronized void init(Path configDir) {
        this.configPath = configDir.resolve("aetheria-client.json");
        load();
    }

    public synchronized void reloadIfChanged() {
        if (configPath == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPollMs < 500L) return;
        lastPollMs = now;
        try {
            if (!Files.exists(configPath)) return;
            long mtime = Files.getLastModifiedTime(configPath).toMillis();
            if (mtime > lastFileMtimeMs) load();
        } catch (IOException ignored) {}
    }

    private void addModule(String id, String label, String cat, boolean defEnabled, double x, double y) {
        HudModule m = new HudModule();
        m.id = id; m.label = label; m.category = cat; m.optId = "hud_" + id;
        m.enabled = defEnabled; m.x = x; m.y = y;
        hudModules.put(id, m);
    }

    private void initDefaults() {
        hudModules.clear();
        // A. SYSTEM, PERFORMANCE & NETWORK (18 HUDs)
        addModule("fps", "FPS Counter", "system", true, 0.015, 0.020);
        addModule("frametime", "Frametime Graph", "system", false, 0.015, 0.065);
        addModule("ram", "RAM Usage", "system", false, 0.015, 0.120);
        addModule("cpu", "CPU/GPU Load", "system", false, 0.015, 0.160);
        addModule("ping", "Live Ping", "system", true, 0.015, 0.200);
        addModule("ping_graph", "Ping Graph", "system", false, 0.015, 0.240);
        addModule("tps", "Server TPS", "system", false, 0.015, 0.295);
        addModule("packets", "Packet Counter", "system", false, 0.015, 0.335);
        addModule("bandwidth", "Bandwidth Rate", "system", false, 0.015, 0.375);
        addModule("session", "Session Timer", "system", true, 0.835, 0.020);
        addModule("total_playtime", "Total Playtime", "system", false, 0.835, 0.060);
        addModule("clock", "Real-World Clock", "system", false, 0.835, 0.100);
        addModule("stopwatch", "Stopwatch / Lap", "system", false, 0.835, 0.140);
        addModule("countdown", "Countdown Timer", "system", false, 0.835, 0.180);
        addModule("battery", "Battery Status", "system", false, 0.835, 0.220);
        addModule("java_gc", "Java GC Monitor", "system", false, 0.835, 0.260);
        addModule("chunk_updates", "Chunk Updates", "system", false, 0.835, 0.300);
        addModule("entity_count", "Entity / Particle", "system", false, 0.835, 0.340);

        // B. PVP, COMBAT & MOVEMENT (22 HUDs)
        addModule("cps", "CPS Counter", "pvp", true, 0.015, 0.420);
        addModule("cps_graph", "CPS Waveform", "pvp", false, 0.015, 0.465);
        addModule("keystrokes", "Keystrokes", "pvp", true, 0.015, 0.520);
        addModule("mouse_vis", "Mouse Movement Pad", "pvp", false, 0.015, 0.690);
        addModule("combo", "Combo Counter", "pvp", false, 0.435, 0.600);
        addModule("reach", "Reach Display", "pvp", false, 0.435, 0.645);
        addModule("accuracy", "Hit Accuracy %", "pvp", false, 0.435, 0.690);
        addModule("crit_ind", "Crit / Sprint-Reset", "pvp", false, 0.435, 0.735);
        addModule("target_hud", "Target HUD", "pvp", true, 0.410, 0.520);
        addModule("armor", "Armor Status", "pvp", true, 0.790, 0.720);
        addModule("armor_prot", "Damage Reduction %", "pvp", false, 0.790, 0.680);
        addModule("durability", "Held Item Durability", "pvp", true, 0.810, 0.635);
        addModule("potions", "Potion Effects", "pvp", true, 0.015, 0.760);
        addModule("potion_counter", "Potions Counter", "pvp", false, 0.280, 0.900);
        addModule("soup_gapple", "GApple / Soup Counter", "pvp", false, 0.340, 0.900);
        addModule("arrow_counter", "Arrow / Projectile", "pvp", true, 0.535, 0.510);
        addModule("totem_counter", "Totem Counter", "pvp", false, 0.400, 0.900);
        addModule("pearl_cd", "Ender Pearl Cooldown", "pvp", false, 0.460, 0.900);
        addModule("shield_cd", "Shield Stun Cooldown", "pvp", false, 0.520, 0.900);
        addModule("crosshair_cd", "Crosshair Charge", "pvp", false, 0.485, 0.530);
        addModule("togglesprint", "ToggleSprint", "pvp", true, 0.015, 0.940);
        addModule("speedometer", "Player Speedometer", "pvp", false, 0.435, 0.850);

        // C. WORLD, NAVIGATION & SURVIVAL (24 HUDs)
        addModule("coords", "Coordinates (XYZ)", "world", true, 0.015, 0.065);
        addModule("chunk_coords", "Chunk Coordinates", "world", false, 0.015, 0.105);
        addModule("compass", "Cardinal Compass", "world", true, 0.340, 0.015);
        addModule("biome", "Biome Display", "world", true, 0.015, 0.145);
        addModule("light_level", "Light Level", "world", false, 0.015, 0.185);
        addModule("slime_chunk", "Slime Chunk Check", "world", false, 0.015, 0.225);
        addModule("world_time", "World Clock & Day", "world", false, 0.835, 0.380);
        addModule("moon_phase", "Moon Phase", "world", false, 0.835, 0.420);
        addModule("weather", "Weather Forecast", "world", false, 0.835, 0.460);
        addModule("minimap", "Minimap Compass", "world", false, 0.835, 0.500);
        addModule("waypoints", "Waypoint Distance", "world", false, 0.340, 0.055);
        addModule("spawn_distance", "Spawn Point Distance", "world", false, 0.340, 0.090);
        addModule("death_tracker", "Death Location Tracker", "world", false, 0.340, 0.125);
        addModule("elevation_alert", "Ore-Layer Guide", "world", false, 0.015, 0.265);
        addModule("saturation", "Saturation & Exhaustion", "world", true, 0.580, 0.900);
        addModule("oxygen", "Oxygen / Air Timer", "world", true, 0.580, 0.865);
        addModule("mount_stats", "Mount Speed & Jump", "world", false, 0.435, 0.810);
        addModule("elytra_pitch", "Elytra Pitch Guide", "world", false, 0.435, 0.770);
        addModule("villager_trade", "Villager Restock Timer", "world", false, 0.790, 0.590);
        addModule("local_blocks", "Spawner / Beehive Radar", "world", false, 0.790, 0.550);
        addModule("waila_block", "Looking-At Block (WAILA)", "world", false, 0.435, 0.060);
        addModule("waila_entity", "Looking-At Entity Info", "world", false, 0.435, 0.100);
        addModule("inventory_space", "Inventory Free Slots", "world", false, 0.790, 0.900);
        addModule("mini_inventory", "Mini-Inventory Grid", "world", false, 0.770, 0.380);

        // D. MINIGAMES, SERVER & SOCIAL (18 HUDs)
        addModule("server_ip", "Server IP & Ping", "social", false, 0.835, 0.020);
        addModule("online_players", "Online Players", "social", false, 0.835, 0.055);
        addModule("friends_counter", "Friends In Lobby", "social", false, 0.835, 0.090);
        addModule("scoreboard", "Custom Scoreboard", "social", true, 0.840, 0.280);
        addModule("bossbar", "BossBar Scaler", "social", true, 0.300, 0.040);
        addModule("actionbar", "Action Bar Style", "social", true, 0.350, 0.720);
        addModule("titles", "Title Scaler", "social", true, 0.300, 0.300);
        addModule("bedwars_res", "BedWars Resources", "social", false, 0.015, 0.650);
        addModule("gen_timers", "Generator Timers", "social", false, 0.015, 0.690);
        addModule("uhc_border", "UHC Border Timer", "social", false, 0.435, 0.140);
        addModule("splits_timer", "Speedrun Splits", "social", false, 0.015, 0.320);
        addModule("kdr_tracker", "Session KDR & Streak", "social", false, 0.015, 0.280);
        addModule("autogg_status", "AutoGG / AutoTip", "social", false, 0.015, 0.900);
        addModule("queue_pos", "Queue Position", "social", false, 0.835, 0.130);
        addModule("rank_tag", "LevelHead / Rank", "social", false, 0.835, 0.170);
        addModule("chat_alerts", "Chat Alerts Feed", "social", false, 0.015, 0.720);
        addModule("voice_hud", "Proximity Voice HUD", "social", false, 0.015, 0.820);
        addModule("party_list", "Party & Team Status", "social", false, 0.015, 0.240);

        // E. VISUALS, MEDIA & AESTHETIC (18 HUDs)
        addModule("resource_pack", "Active Texture Pack", "aesthetic", false, 0.835, 0.210);
        addModule("shader_pack", "Shaderpack Profile", "aesthetic", false, 0.835, 0.250);
        addModule("now_playing", "Now Playing (Music)", "aesthetic", false, 0.360, 0.020);
        addModule("audio_vis", "Audio Visualizer Bars", "aesthetic", false, 0.360, 0.065);
        addModule("paperdoll", "Paperdoll 3D Model", "aesthetic", false, 0.015, 0.120);
        addModule("item_feed", "Item Pickup Feed", "aesthetic", true, 0.835, 0.650);
        addModule("achievement_toast", "Toast Customizer", "aesthetic", true, 0.780, 0.020);
        addModule("sound_vis", "Directional Subtitles", "aesthetic", false, 0.780, 0.800);
        addModule("zoom_indicator", "Zoom FOV Multiplier", "aesthetic", false, 0.465, 0.550);
        addModule("gamma_level", "Brightness Readout", "aesthetic", false, 0.435, 0.880);
        addModule("fov_indicator", "Dynamic FOV Readout", "aesthetic", false, 0.435, 0.915);
        addModule("freelook_indicator", "FreeLook 360 Indicator", "aesthetic", true, 0.450, 0.420);
        addModule("ss_toast", "Screenshot Thumbnail", "aesthetic", true, 0.015, 0.850);
        addModule("active_mods", "Active Modules List", "aesthetic", false, 0.840, 0.340);
        addModule("watermark", "Aetheria Watermark", "aesthetic", true, 0.015, 0.005);
        addModule("custom_image", "Custom Logo / PNG", "aesthetic", false, 0.870, 0.010);
        addModule("screen_vignette", "Low-HP Pulse Vignette", "aesthetic", true, 0.000, 0.000);
        addModule("notebook", "On-Screen Notebook", "aesthetic", false, 0.810, 0.450);

        for (HudModule m : hudModules.values()) {
            if (m.optId != null) setDefaultOpt(m.optId, Boolean.valueOf(m.enabled));
        }

        // Global Engine Options
        setDefaultOpt("ui_menu_rounding", Integer.valueOf(8));
        setDefaultOpt("ui_accent", "white");
        setDefaultOpt("ui_animations", Boolean.TRUE);
        setDefaultOpt("hud_snap_grid", Boolean.FALSE);
        setDefaultOpt("hud_shadow", Boolean.TRUE);
        setDefaultOpt("ctl_menu_key", "RSHIFT");

        // Movement & QoL
        setDefaultOpt("pvp_togglesprint", Boolean.TRUE);
        setDefaultOpt("pvp_togglesneak", Boolean.FALSE);
        setDefaultOpt("cam_zoom", Boolean.TRUE);
        setDefaultOpt("cam_zoom_fac", Integer.valueOf(4));
        setDefaultOpt("cam_zoom_ease", Boolean.TRUE);
        setDefaultOpt("cam_zoom_slow", Boolean.TRUE);
        setDefaultOpt("cam_zoom_key", "C");
        setDefaultOpt("cam_freelook", Boolean.TRUE);
        setDefaultOpt("cam_freelook_key", "ALT");

        // Crosshairs & Combat
        setDefaultOpt("pvp_low_fire", Boolean.TRUE);
        setDefaultOpt("pvp_low_fire_offset", Double.valueOf(-0.32));
        setDefaultOpt("pvp_xhair_style", "classic");
        setDefaultOpt("pvp_xhair_size", Integer.valueOf(6));
        setDefaultOpt("pvp_xhair_gap", Integer.valueOf(2));
        setDefaultOpt("pvp_xhair_thick", Integer.valueOf(1));
        setDefaultOpt("pvp_xhair_color", "#ffffff");
        setDefaultOpt("pvp_xhair_target", Boolean.TRUE);
        setDefaultOpt("pvp_xhair_target_players_only", Boolean.TRUE);
        setDefaultOpt("pvp_xhair_target_range", Integer.valueOf(12));
        setDefaultOpt("pvp_xhair_target_style", "same");
        setDefaultOpt("pvp_xhair_target_color", "#ef4444");

        // Profiles Setup
        initDefaultProfiles();
    }

    private void initDefaultProfiles() {
        profiles.clear();
        Profile def = new Profile("Default");
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) def.modules.put(e.getKey(), cloneModule(e.getValue()));
        profiles.put("Default", def);

        Profile pvp = new Profile("PvP Minimal");
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = cloneModule(e.getValue());
            m.enabled = ("fps".equals(m.id) || "ping".equals(m.id) || "cps".equals(m.id) || "keystrokes".equals(m.id) || "target_hud".equals(m.id) || "armor".equals(m.id) || "potions".equals(m.id) || "togglesprint".equals(m.id));
            pvp.modules.put(e.getKey(), m);
        }
        profiles.put("PvP Minimal", pvp);

        Profile smp = new Profile("Survival / SMP");
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = cloneModule(e.getValue());
            m.enabled = ("coords".equals(m.id) || "biome".equals(m.id) || "compass".equals(m.id) || "clock".equals(m.id) || "durability".equals(m.id) || "saturation".equals(m.id) || "oxygen".equals(m.id) || "day_count".equals(m.id) || "session".equals(m.id));
            smp.modules.put(e.getKey(), m);
        }
        profiles.put("Survival / SMP", smp);

        Profile cin = new Profile("Cinematic");
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = cloneModule(e.getValue());
            m.enabled = ("watermark".equals(m.id));
            cin.modules.put(e.getKey(), m);
        }
        profiles.put("Cinematic", cin);
    }

    public void applyProfile(String profileName) {
        Profile p = profiles.get(profileName);
        if (p == null) return;
        activeProfile = profileName;
        for (Map.Entry<String, HudModule> e : p.modules.entrySet()) {
            if (hudModules.containsKey(e.getKey())) {
                HudModule target = hudModules.get(e.getKey());
                copyModuleProps(e.getValue(), target);
            }
        }
        save();
    }

    private HudModule cloneModule(HudModule s) {
        HudModule m = new HudModule();
        copyModuleProps(s, m);
        return m;
    }

    private void copyModuleProps(HudModule s, HudModule d) {
        d.id = s.id; d.label = s.label; d.category = s.category; d.optId = s.optId; d.anchor = s.anchor;
        d.color = s.color; d.bgColor = s.bgColor; d.borderColor = s.borderColor;
        d.enabled = s.enabled; d.bg = s.bg; d.border = s.border; d.chroma = s.chroma; d.shadow = s.shadow;
        d.x = s.x; d.y = s.y; d.scale = s.scale; d.opacity = s.opacity; d.textOpacity = s.textOpacity;
        d.widthOverride = s.widthOverride; d.heightOverride = s.heightOverride;
        d.borderRadius = s.borderRadius; d.borderThickness = s.borderThickness;
        d.paddingX = s.paddingX; d.paddingY = s.paddingY;
        d.bracketStyle = s.bracketStyle; d.prefixText = s.prefixText; d.suffixText = s.suffixText;
        d.hideInF1 = s.hideInF1; d.hideInChat = s.hideInChat; d.hideInInventory = s.hideInInventory; d.hideInF3 = s.hideInF3;
        d.renderMode = s.renderMode;
    }

    private void setDefaultOpt(String id, Object val) {
        if (!options.containsKey(id)) options.put(id, GSON.toJsonTree(val));
    }

    public synchronized void load() {
        initDefaults();
        if (configPath == null || !Files.exists(configPath)) { if (configPath != null) save(); return; }
        try {
            lastFileMtimeMs = Files.getLastModifiedTime(configPath).toMillis();
            String raw = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
            if (root.has("menuKey")) menuKey = root.get("menuKey").getAsString();
            if (root.has("activeProfile")) activeProfile = root.get("activeProfile").getAsString();
            if (root.has("updatedAt")) updatedAt = root.get("updatedAt").getAsLong();

            if (root.has("options") && root.get("options").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("options").entrySet()) options.put(e.getKey(), e.getValue());
            }
            if (root.has("hudModules") && root.get("hudModules").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("hudModules").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    HudModule m = hudModules.containsKey(e.getKey()) ? hudModules.get(e.getKey()) : new HudModule();
                    m.id = e.getKey();
                    if (o.has("label")) m.label = o.get("label").getAsString();
                    if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
                    if (o.has("x")) m.x = o.get("x").getAsDouble();
                    if (o.has("y")) m.y = o.get("y").getAsDouble();
                    if (o.has("scale")) m.scale = o.get("scale").getAsDouble();
                    if (o.has("opacity")) m.opacity = o.get("opacity").getAsDouble();
                    if (o.has("borderRadius")) m.borderRadius = Math.max(0, Math.min(16, o.get("borderRadius").getAsInt()));
                    if (o.has("borderThickness")) m.borderThickness = Math.max(0, Math.min(5, o.get("borderThickness").getAsInt()));
                    if (o.has("paddingX")) m.paddingX = Math.max(0, Math.min(24, o.get("paddingX").getAsInt()));
                    if (o.has("paddingY")) m.paddingY = Math.max(0, Math.min(24, o.get("paddingY").getAsInt()));
                    if (o.has("bg")) m.bg = o.get("bg").getAsBoolean();
                    if (o.has("border")) m.border = o.get("border").getAsBoolean();
                    if (o.has("chroma")) m.chroma = o.get("chroma").getAsBoolean();
                    if (o.has("shadow")) m.shadow = o.get("shadow").getAsBoolean();
                    if (o.has("color")) m.color = o.get("color").getAsString();
                    if (o.has("bgColor")) m.bgColor = o.get("bgColor").getAsString();
                    if (o.has("borderColor")) m.borderColor = o.get("borderColor").getAsString();
                    if (o.has("anchor")) m.anchor = o.get("anchor").getAsString();
                    if (o.has("bracketStyle")) m.bracketStyle = o.get("bracketStyle").getAsString();
                    if (o.has("prefixText")) m.prefixText = o.get("prefixText").getAsString();
                    if (o.has("suffixText")) m.suffixText = o.get("suffixText").getAsString();
                    if (o.has("hideInF1")) m.hideInF1 = o.get("hideInF1").getAsBoolean();
                    if (o.has("hideInChat")) m.hideInChat = o.get("hideInChat").getAsBoolean();
                    if (o.has("hideInInventory")) m.hideInInventory = o.get("hideInInventory").getAsBoolean();
                    if (o.has("hideInF3")) m.hideInF3 = o.get("hideInF3").getAsBoolean();
                    hudModules.put(e.getKey(), m);
                }
            }
            menuKey = getString("ctl_menu_key", menuKey);
        } catch (Exception ignored) {}
    }

    public synchronized void save() {
        if (configPath == null) return;
        updatedAt = System.currentTimeMillis();
        menuKey = getString("ctl_menu_key", menuKey);

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("updatedAt", updatedAt);
        root.addProperty("menuKey", menuKey);
        root.addProperty("activeProfile", activeProfile);

        JsonObject mods = new JsonObject();
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("label", m.label);
            o.addProperty("enabled", m.enabled);
            o.addProperty("x", Math.round(m.x * 1000.0) / 1000.0);
            o.addProperty("y", Math.round(m.y * 1000.0) / 1000.0);
            o.addProperty("scale", Math.round(m.scale * 100.0) / 100.0);
            o.addProperty("opacity", Math.round(m.opacity * 100.0) / 100.0);
            o.addProperty("borderRadius", m.borderRadius);
            o.addProperty("borderThickness", m.borderThickness);
            o.addProperty("paddingX", m.paddingX);
            o.addProperty("paddingY", m.paddingY);
            o.addProperty("bg", m.bg);
            o.addProperty("border", m.border);
            o.addProperty("chroma", m.chroma);
            o.addProperty("shadow", m.shadow);
            o.addProperty("color", m.color);
            o.addProperty("bgColor", m.bgColor);
            o.addProperty("borderColor", m.borderColor);
            o.addProperty("anchor", m.anchor);
            o.addProperty("bracketStyle", m.bracketStyle);
            o.addProperty("prefixText", m.prefixText);
            o.addProperty("suffixText", m.suffixText);
            o.addProperty("hideInF1", m.hideInF1);
            o.addProperty("hideInChat", m.hideInChat);
            o.addProperty("hideInInventory", m.hideInInventory);
            o.addProperty("hideInF3", m.hideInF3);
            mods.add(e.getKey(), o);
        }
        root.add("hudModules", mods);

        JsonObject opts = new JsonObject();
        for (Map.Entry<String, JsonElement> e : options.entrySet()) opts.add(e.getKey(), e.getValue());
        root.add("options", opts);

        try {
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            lastFileMtimeMs = Files.getLastModifiedTime(configPath).toMillis();
        } catch (IOException ignored) {}
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
        for (HudModule m : hudModules.values()) if (id.equals(m.optId) && val instanceof Boolean) m.enabled = ((Boolean) val).booleanValue();
        save();
    }
    public void toggleModule(HudModule m) {
        m.enabled = !m.enabled;
        if (m.optId != null) options.put(m.optId, new JsonPrimitive(Boolean.valueOf(m.enabled)));
        save();
    }
    public double getZoomFactor() { return Math.max(2.0, getDouble("cam_zoom_fac", 4.0)); }
    public void resetLayout() { initDefaults(); save(); }
    public void resetAll() { options.clear(); initDefaults(); save(); }
}