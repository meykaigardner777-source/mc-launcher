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
    public String activeProfile = "PvP";
    public long updatedAt = 0L;
    public final Map<String, HudModule> hudModules = new LinkedHashMap<String, HudModule>();
    public final Map<String, JsonElement> options = new LinkedHashMap<String, JsonElement>();

    public static class HudModule {
        public String id, label, category = "system", optId, desc = "";
        public String anchor = "top-left";
        public String color = "#ffffff", bgColor = "#09090b", borderColor = "#27272a", borderColor2 = "#ffffff";
        public String borderStyle = "solid";
        public String fontFamily = "default", fontWeight = "normal";
        public String prefix = "", suffix = "", bracketStyle = "none", displayMode = "icon_text";
        public String customText = "Aetheria Client";
        public String dropShadowColor = "#000000";

        public boolean enabled, favorite = false;
        public boolean bg = true, border = true, chroma = false, shadow = true;
        public boolean hideInF1 = true, hideInChat = false, hideInInventory = false, hideInF3 = true;

        public double x, y, scale = 1.0, opacity = 0.75, textOpacity = 1.0;
        public double chromaSpeed = 1.0, chromaSat = 0.85, chromaAngle = 45.0;
        public int width = 0, height = 0;
        public int borderRadius = 10, borderWidth = 1, paddingX = 6, paddingY = 4, margin = 0, blurRadius = 8;
        public int letterSpacing = 0, dropShadowX = 0, dropShadowY = 2, dropShadowBlur = 0;
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

    private void initDefaults() {
        hudModules.clear();

        // === A. SYSTEM, PERFORMANCE & NETWORK (18) ===
        add("fps", "FPS Counter", "system", "hud_fps", true, 0.015, 0.025, "Current, Min, Max & 1% Low FPS");
        add("frametime_graph", "Frametime Graph", "system", "hud_ft_graph", false, 0.015, 0.075, "Live rolling frame ms spike chart");
        add("ram_usage", "RAM / Memory", "system", "hud_ram", false, 0.015, 0.145, "JVM heap MB/GB used & % bar");
        add("cpu_load", "CPU / Threads", "system", "hud_cpu", false, 0.015, 0.205, "Available cores & active threads");
        add("ping", "Ping / Latency", "system", "pvp_ping_hud", true, 0.015, 0.230, "Live ms latency with quality dot");
        add("ping_graph", "Ping Graph", "system", "hud_ping_graph", false, 0.015, 0.275, "Rolling latency stability line");
        add("server_tps", "Server TPS", "system", "hud_tps", false, 0.015, 0.335, "Server ticks per second & tick ms");
        add("packet_counter", "Packet Counter", "system", "hud_packets", false, 0.015, 0.385, "Tick & IO activity rate");
        add("bandwidth", "Bandwidth Rate", "system", "hud_bandwidth", false, 0.015, 0.435, "Estimated KB/s session rate");
        add("session", "Session Playtime", "system", "hud_session", true, 0.835, 0.080, "Current launch duration timer");
        add("total_playtime", "Total Playtime", "system", "hud_total_time", false, 0.835, 0.125, "Cumulative world/session hours");
        add("clock", "Real-World Clock", "system", "hud_clock", false, 0.855, 0.025, "12h/24h local time + seconds");
        add("stopwatch", "Stopwatch Timer", "system", "hud_stopwatch", false, 0.855, 0.170, "Start/Pause/Reset lap stopwatch");
        add("countdown", "Countdown Timer", "system", "hud_countdown", false, 0.855, 0.215, "Event/pot/pearl alert timer");
        add("battery", "Battery Status", "system", "hud_battery", false, 0.855, 0.260, "System power & runtime status");
        add("gc_monitor", "Java / GC Monitor", "system", "hud_gc", false, 0.855, 0.305, "Garbage collection pause tracker");
        add("chunk_updates", "Chunk Updates", "system", "hud_chunks_upd", false, 0.855, 0.350, "Active world render region updates");
        add("entity_counter", "Entity Counter", "system", "hud_entities", false, 0.855, 0.395, "Rendered vs total entities");

        // === B. PVP, COMBAT & MOVEMENT MECHANICS (22) ===
        add("cps", "CPS Counter", "pvp", "pvp_cps", true, 0.015, 0.285, "LMB & RMB clicks per second");
        add("cps_graph", "CPS Graph", "pvp", "pvp_cps_graph", false, 0.015, 0.335, "Live click-speed waveform");
        add("keystrokes", "Keystrokes", "pvp", "pvp_keystrokes", true, 0.015, 0.395, "WASD, Space, Shift, Ctrl, LMB, RMB");
        add("mouse_visualizer", "Mouse Visualizer", "pvp", "pvp_mouse_viz", false, 0.115, 0.395, "Live cursor velocity/strafe pad");
        add("combo", "Combo Counter", "pvp", "pvp_combo_hud", false, 0.435, 0.600, "Consecutive melee hits & timer");
        add("reach", "Reach Display", "pvp", "pvp_reach_hud", false, 0.435, 0.660, "Last melee hit distance in blocks");
        add("hit_accuracy", "Hit Accuracy %", "pvp", "pvp_accuracy", false, 0.435, 0.715, "Swings landed vs missed ratio");
        add("sprint_reset", "Crit / W-Tap", "pvp", "pvp_sprint_reset", false, 0.435, 0.770, "Visual sprint-reset / hit flash");
        add("target_hud", "Target HUD", "pvp", "pvp_target_hud", false, 0.540, 0.560, "Opponent HP bar, distance & name");
        add("armor", "Armor Status", "pvp", "pvp_armor_hud", true, 0.790, 0.720, "Equipped armor durability readout");
        add("armor_prot", "Armor Protection", "pvp", "pvp_armor_prot", false, 0.790, 0.775, "Total damage reduction % & EPF");
        add("durability", "Held Durability", "pvp", "hud_durability", true, 0.825, 0.650, "Main-hand & off-hand uses left");
        add("potions", "Potion Effects", "pvp", "pvp_potion_hud", true, 0.015, 0.615, "Active buffs/debuffs with timers");
        add("potion_count", "Potion Counter", "pvp", "pvp_pot_count", false, 0.150, 0.615, "Splash/healing potions in bag");
        add("heal_count", "GApple / Soup", "pvp", "pvp_heal_count", false, 0.150, 0.665, "Golden apples & soups in inventory");
        add("arrow_count", "Arrow Counter", "pvp", "pvp_arrow_count", false, 0.150, 0.715, "Arrows & projectiles in bag");
        add("totem_count", "Totem Counter", "pvp", "pvp_totem_count", false, 0.150, 0.765, "Totems of Undying in inventory");
        add("pearl_cd", "Pearl Cooldown", "pvp", "pvp_pearl_cd", false, 0.540, 0.650, "Ender pearl throw recovery timer");
        add("shield_cd", "Shield Cooldown", "pvp", "pvp_shield_cd", false, 0.540, 0.700, "Axe-disable recovery countdown");
        add("attack_charge", "Attack / Bow Bar", "pvp", "pvp_atk_bar", false, 0.455, 0.545, "1.9+ recharge & bow draw %");
        add("togglesprint", "ToggleSprint", "pvp", "pvp_togglesprint", true, 0.015, 0.545, "Current sprint/sneak state badge");
        add("speedometer", "Speedometer", "pvp", "pvp_speed", false, 0.015, 0.785, "Blocks/sec, km/h or mph velocity");

        // === C. WORLD, NAVIGATION & SURVIVAL (24) ===
        add("coords", "Coordinates XYZ", "world", "hud_coords", true, 0.015, 0.085, "Overworld + Nether 8:1 conversion");
        add("chunk_coords", "Chunk Coords", "world", "hud_chunk_xyz", false, 0.015, 0.135, "Current Chunk X/Z & section Y");
        add("compass", "Compass Strip", "world", "hud_compass", true, 0.340, 0.020, "Top scrolling N/E/S/W + degrees");
        add("biome", "Biome Display", "world", "hud_biome", true, 0.015, 0.175, "Current world biome name");
        add("light_level", "Light Level", "world", "hud_light", false, 0.180, 0.085, "Block & sky light (spawn-proofing)");
        add("slime_chunk", "Slime Chunk", "world", "hud_slime", false, 0.180, 0.135, "Slime chunk detector status");
        add("day_count", "Day & Time", "world", "hud_day_count", false, 0.875, 0.190, "World day count & 24h game time");
        add("moon_phase", "Moon Phase", "world", "hud_moon", false, 0.875, 0.240, "Moon phase & slime spawn rate");
        add("weather", "Weather Forecast", "world", "hud_weather", false, 0.875, 0.290, "Clear / Rain / Thunder state");
        add("minimap", "Radar Minimap", "world", "hud_minimap", false, 0.855, 0.445, "Live terrain & entity radar");
        add("waypoints", "Waypoints HUD", "world", "hud_waypoints", false, 0.720, 0.025, "Nearest saved waypoint & distance");
        add("spawn_dist", "Spawn Distance", "world", "hud_spawn_dist", false, 0.720, 0.075, "Distance & direction to spawn/bed");
        add("death_loc", "Death Tracker", "world", "hud_death_loc", false, 0.720, 0.125, "Last death coordinates & distance");
        add("y_alert", "Y-Level Ore Alert", "world", "hud_y_alert", false, 0.180, 0.185, "Optimal Diamond / Debris Y alert");
        add("saturation", "Saturation Bar", "world", "hud_saturation", false, 0.540, 0.750, "Food saturation & exhaustion");
        add("oxygen", "Oxygen Timer", "world", "hud_oxygen", false, 0.540, 0.800, "Seconds of air remaining");
        add("mount_stats", "Mount Stats", "world", "hud_mount", false, 0.720, 0.175, "Speed, jump & HP of ridden mount");
        add("elytra_guide", "Elytra Guide", "world", "hud_elytra", false, 0.440, 0.415, "Optimal 40/-40 deg glide pitch");
        add("villager_timer", "Villager Restock", "world", "hud_villager", false, 0.720, 0.225, "Workstation day-cycle restock");
        add("spawner_info", "Local Block Info", "world", "hud_spawner", false, 0.720, 0.275, "Nearby hive/spawner state");
        add("waila_block", "Looking-At Block", "world", "hud_waila_blk", false, 0.415, 0.075, "WAILA targeted block name");
        add("waila_entity", "Looking-At Entity", "world", "hud_waila_ent", false, 0.415, 0.125, "Targeted mob name & distance");
        add("inv_space", "Inventory Space", "world", "hud_inv_space", false, 0.790, 0.830, "Occupied vs free bag slots");
        add("mini_inventory", "Mini Inventory", "world", "hud_mini_inv", false, 0.750, 0.880, "Compact backpack item summary");

        // === D. MINIGAMES, SERVER & SOCIAL (18) ===
        add("server_ip", "Server IP", "server", "hud_srv_ip", false, 0.840, 0.135, "Connected server address badge");
        add("player_count", "Online Players", "server", "hud_players", false, 0.840, 0.440, "Current / Max lobby players");
        add("friends_online", "Friends Online", "server", "hud_friends", false, 0.840, 0.485, "Added friends online tracker");
        add("scoreboard_mod", "Scoreboard Style", "server", "hud_scoreboard", false, 0.840, 0.530, "Sidebar cleaner & custom theme");
        add("bossbar_mod", "BossBar Style", "server", "hud_bossbar", false, 0.380, 0.175, "BossBar scale & clean toggle");
        add("actionbar_mod", "Action Bar Style", "server", "hud_actionbar", false, 0.380, 0.220, "Repositioned action-bar badge");
        add("title_scaler", "Title Scaler", "server", "hud_title_scale", false, 0.380, 0.265, "Title/subtitle sizing status");
        add("bw_resources", "BW Resources", "server", "hud_bw_res", false, 0.150, 0.820, "Iron, Gold, Diamond & Emerald count");
        add("bw_gens", "BW Gen Timers", "server", "hud_bw_gens", false, 0.150, 0.875, "Diamond/Emerald upgrade timer");
        add("uhc_border", "UHC Border Timer", "server", "hud_uhc_border", false, 0.380, 0.310, "Border shrink & safe distance");
        add("speedrun_splits", "Speedrun Splits", "server", "hud_splits", false, 0.015, 0.835, "Nether/Bastion/Fortress splits");
        add("kdr_tracker", "K/D/R Tracker", "server", "hud_kdr", false, 0.015, 0.890, "Session Kills, Deaths & KDR");
        add("autogg_status", "AutoGG / Tip", "server", "hud_autogg", false, 0.380, 0.355, "Active AutoGG & AutoTip badge");
        add("queue_pos", "Queue Position", "server", "hud_queue", false, 0.380, 0.400, "Server queue wait estimator");
        add("level_head", "LevelHead Badge", "server", "hud_levelhead", false, 0.380, 0.445, "Player rank & level preview");
        add("chat_mentions", "Chat Mentions", "server", "hud_mentions", false, 0.300, 0.820, "Mini-box for DMs & keyword pings");
        add("voice_hud", "Voice Chat HUD", "server", "hud_voice", false, 0.300, 0.875, "Active speakers & mic status");
        add("party_list", "Party / Team", "server", "hud_party", false, 0.720, 0.330, "Teammates HP & dimension list");

        // === E. VISUALS, MEDIA & AESTHETIC WIDGETS (18) ===
        add("pack_display", "Resource Pack", "visuals", "hud_pack", false, 0.015, 0.935, "Active resource pack badge");
        add("shader_display", "Shader Pack", "visuals", "hud_shader", false, 0.180, 0.935, "Active shader profile status");
        add("now_playing", "Now Playing", "visuals", "hud_media", false, 0.350, 0.935, "Media track & progress indicator");
        add("audio_viz", "Audio Visualizer", "visuals", "hud_audioviz", false, 0.520, 0.915, "Reactive frequency bars widget");
        add("paperdoll", "Paperdoll Player", "visuals", "hud_paperdoll", false, 0.910, 0.560, "Mini live player stance preview");
        add("pickup_feed", "Item Pickup Feed", "visuals", "hud_pickups", false, 0.760, 0.600, "Scrolling collected item feed");
        add("toast_style", "Toast Customizer", "visuals", "hud_toasts", false, 0.540, 0.850, "Themed advancement toast style");
        add("subtitles_mod", "Sound Visualizer", "visuals", "hud_subtitles", false, 0.540, 0.895, "Directional sound cues widget");
        add("zoom_indicator", "Zoom Indicator", "visuals", "hud_zoom_ind", false, 0.460, 0.360, "Live FOV zoom multiplier badge");
        add("gamma_level", "Gamma / Bright", "visuals", "hud_gamma", false, 0.560, 0.025, "Current brightness % readout");
        add("fov_indicator", "FOV Indicator", "visuals", "hud_fov", false, 0.560, 0.070, "Dynamic vs static FOV readout");
        add("freelook_badge", "FreeLook Badge", "visuals", "hud_freelook", false, 0.455, 0.475, "Detached 360 camera alert");
        add("screenshot_toast", "Capture Preview", "visuals", "hud_ss_toast", false, 0.560, 0.115, "Screenshot capture status badge");
        add("arraylist", "Active Modules", "visuals", "hud_arraylist", false, 0.850, 0.020, "Compact list of active utilities");
        add("watermark", "Watermark Text", "visuals", "hud_watermark", false, 0.015, 0.005, "Custom client branding text");
        add("custom_image", "Custom Overlay", "visuals", "hud_custom_img", false, 0.640, 0.025, "Custom PNG/badge overlay slot");
        add("low_hp_vignette", "Low-HP Pulse", "visuals", "hud_low_hp", false, 0.455, 0.520, "Screen edge pulse at low health");
        add("notebook", "Quick Notebook", "visuals", "hud_notebook", false, 0.720, 0.385, "Pinned survival tasks & notes");

        for (HudModule m : hudModules.values()) if (m.optId != null) setDefaultOpt(m.optId, Boolean.valueOf(m.enabled));

        // === GLOBAL UI ENGINE & MASTER SLIDERS ===
        setDefaultOpt("global_hud_rounding", Integer.valueOf(-1)); // -1 = per-hud, 0 = sharp, 12 = round
        setDefaultOpt("global_hud_opacity", Double.valueOf(-1.0)); // -1 = per-hud, 0.10 to 1.00
        setDefaultOpt("ui_menu_layout", "topbar");
        setDefaultOpt("ui_switch_style", "pill");
        setDefaultOpt("ui_card_width", Integer.valueOf(140));
        setDefaultOpt("ui_menu_rounding", Integer.valueOf(12));
        setDefaultOpt("ui_menu_opacity", Double.valueOf(0.95));
        setDefaultOpt("ui_menu_blur", Integer.valueOf(14));
        setDefaultOpt("ui_world_blur", Integer.valueOf(10));
        setDefaultOpt("ui_accent", "white");
        setDefaultOpt("ui_accent_custom", "#ff3333");
        setDefaultOpt("ui_animations", Boolean.TRUE);
        setDefaultOpt("ui_anim_speed", Double.valueOf(1.0));
        setDefaultOpt("ui_anim_curve", "smooth");
        setDefaultOpt("ui_font_scale", Double.valueOf(1.0));
        setDefaultOpt("ui_letter_spacing", Integer.valueOf(0));

        setDefaultOpt("hud_snap_grid", Boolean.TRUE);
        setDefaultOpt("hud_align_guides", Boolean.TRUE);
        setDefaultOpt("hud_shadow", Boolean.TRUE);

        setDefaultOpt("esc_overhaul", Boolean.TRUE);
        setDefaultOpt("esc_blur", Integer.valueOf(12));
        setDefaultOpt("container_theme", Boolean.TRUE);
        setDefaultOpt("container_opacity", Double.valueOf(0.80));

        // === LOW FIRE & LOW SHIELD VIEWMODEL ===
        setDefaultOpt("pvp_low_fire", Boolean.TRUE);
        setDefaultOpt("pvp_low_fire_offset", Double.valueOf(-0.32));
        setDefaultOpt("pvp_low_fire_opacity", Double.valueOf(0.70));
        setDefaultOpt("pvp_low_shield", Boolean.TRUE);
        setDefaultOpt("pvp_low_shield_scale", Double.valueOf(0.85));
        setDefaultOpt("pvp_low_shield_x", Double.valueOf(0.0));
        setDefaultOpt("pvp_low_shield_y", Double.valueOf(-0.25));

        // === NO HURT CAM & DYNAMIC FOV ===
        setDefaultOpt("pvp_no_hurtcam", Boolean.FALSE);
        setDefaultOpt("pvp_hurtcam_factor", Double.valueOf(0.0)); // 0.0 = completely off, 1.0 = vanilla
        setDefaultOpt("cam_fov_dynamic", Boolean.TRUE);
        setDefaultOpt("cam_fov_factor", Double.valueOf(1.0));
        setDefaultOpt("cam_fov_sprint", Boolean.TRUE);
        setDefaultOpt("cam_fov_bow", Boolean.TRUE);

        setDefaultOpt("util_fullbright", Boolean.FALSE);
        setDefaultOpt("util_freelook", Boolean.TRUE);
        setDefaultOpt("util_freelook_key", "ALT");
        setDefaultOpt("util_time_lock", "off");
        setDefaultOpt("util_weather_lock", "off");

        // === CROSSHAIR & CUSTOM PIXELS ===
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
        setDefaultOpt("pvp_xhair_custom_pixels",
            "000000000000000" + "000000000000000" + "000000000000000" +
            "000000000000000" + "000000010000000" + "000000010000000" +
            "000000000000000" + "000011000110000" + "000000000000000" +
            "000000010000000" + "000000010000000" + "000000000000000" +
            "000000000000000" + "000000000000000" + "000000000000000");
        setDefaultOpt("pvp_xhair_target_custom_pixels",
            "000000000000000" + "000000000000000" + "000000000000000" +
            "000000000000000" + "000010010010000" + "000001010100000" +
            "000000000000000" + "000011010110000" + "000000000000000" +
            "000001010100000" + "000010010010000" + "000000000000000" +
            "000000000000000" + "000000000000000" + "000000000000000");
        setDefaultOpt("pvp_xhair_pixel_scale", Integer.valueOf(1));

        // === SATURATION & POTIONS CUSTOMIZATION ===
        setDefaultOpt("saturation_style", "appleskin"); // appleskin, bar, text
        setDefaultOpt("saturation_glint", Boolean.TRUE);
        setDefaultOpt("potions_style", "vanilla");      // vanilla, compact, cards
        setDefaultOpt("potions_timers", Boolean.TRUE);
        setDefaultOpt("potions_numerals", Boolean.TRUE);
        setDefaultOpt("potions_show_icon", Boolean.TRUE);
        setDefaultOpt("potions_blink", Boolean.TRUE);

        setDefaultOpt("cam_zoom", Boolean.TRUE);
        setDefaultOpt("cam_zoom_fac", Integer.valueOf(4));
        setDefaultOpt("cam_zoom_ease", Boolean.TRUE);
        setDefaultOpt("cam_zoom_slow", Boolean.TRUE);
        setDefaultOpt("cam_zoom_key", "C");
        setDefaultOpt("ctl_menu_key", "RSHIFT");
        setDefaultOpt("ctl_profile_key", "F6");

        setDefaultOpt("fps_show_label", Boolean.TRUE);
        setDefaultOpt("fps_detailed", Boolean.FALSE);
        setDefaultOpt("coords_facing", Boolean.TRUE);
        setDefaultOpt("coords_compact", Boolean.FALSE);
        setDefaultOpt("coords_nether", Boolean.TRUE);
        setDefaultOpt("clock_24h", Boolean.FALSE);
        setDefaultOpt("clock_seconds", Boolean.FALSE);
        setDefaultOpt("compass_degrees", Boolean.TRUE);
        setDefaultOpt("armor_percent", Boolean.TRUE);
        setDefaultOpt("keystrokes_cps", Boolean.TRUE);
        setDefaultOpt("keystrokes_space", Boolean.TRUE);
        setDefaultOpt("keystrokes_mods", Boolean.FALSE);
        setDefaultOpt("togglesprint_hide_hud", Boolean.FALSE);
        setDefaultOpt("speed_unit", "bps");
    }

    private void add(String id, String label, String cat, String optId, boolean en, double x, double y, String desc) {
        HudModule m = new HudModule();
        m.id = id; m.label = label; m.category = cat; m.optId = optId; m.enabled = en;
        m.x = x; m.y = y; m.desc = desc;
        hudModules.put(id, m);
    }

    public void applyProfilePreset(String name) {
        activeProfile = name;
        for (HudModule m : hudModules.values()) setModuleEnabledSilent(m, false);
        if ("PvP".equalsIgnoreCase(name)) {
            enableOnly(new String[]{"fps", "ping", "cps", "keystrokes", "togglesprint", "potions", "armor", "durability", "combo", "reach", "hit_accuracy", "target_hud", "compass", "session"});
        } else if ("Survival".equalsIgnoreCase(name)) {
            enableOnly(new String[]{"fps", "coords", "biome", "light_level", "day_count", "weather", "durability", "armor", "saturation", "inv_space", "y_alert", "waila_block", "compass"});
        } else if ("SMP".equalsIgnoreCase(name)) {
            enableOnly(new String[]{"fps", "coords", "ping", "server_tps", "server_ip", "player_count", "waypoints", "death_loc", "totem_count", "armor", "potions", "session"});
        } else if ("Minimal".equalsIgnoreCase(name)) {
            enableOnly(new String[]{"fps", "coords", "ping"});
        } else if ("Cinematic".equalsIgnoreCase(name)) {
            enableOnly(new String[]{"zoom_indicator"});
        }
        save();
    }

    public void cycleProfilePreset() {
        String[] presets = {"PvP", "Survival", "SMP", "Minimal", "Cinematic"};
        int idx = 0;
        for (int i = 0; i < presets.length; i++) if (presets[i].equalsIgnoreCase(activeProfile)) idx = (i + 1) % presets.length;
        applyProfilePreset(presets[idx]);
    }

    private void enableOnly(String[] ids) {
        for (int i = 0; i < ids.length; i++) {
            HudModule m = hudModules.get(ids[i]);
            if (m != null) setModuleEnabledSilent(m, true);
        }
    }

    private void setModuleEnabledSilent(HudModule m, boolean en) {
        m.enabled = en;
        if (m.optId != null) options.put(m.optId, new JsonPrimitive(Boolean.valueOf(en)));
    }

    public void resetModule(String id) {
        HudModule old = hudModules.get(id);
        Map<String, HudModule> snap = new LinkedHashMap<String, HudModule>(hudModules);
        initDefaults();
        HudModule fresh = hudModules.get(id);
        hudModules.clear();
        hudModules.putAll(snap);
        if (fresh != null) {
            if (old != null) fresh.enabled = old.enabled;
            hudModules.put(id, fresh);
        }
        save();
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
            if (root.has("menuKey") && !root.get("menuKey").isJsonNull()) menuKey = root.get("menuKey").getAsString();
            if (root.has("activeProfile") && !root.get("activeProfile").isJsonNull()) activeProfile = root.get("activeProfile").getAsString();
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
                    if (o.has("category")) m.category = o.get("category").getAsString();
                    if (o.has("optId") && !o.get("optId").isJsonNull()) m.optId = o.get("optId").getAsString();
                    if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
                    if (o.has("favorite")) m.favorite = o.get("favorite").getAsBoolean();
                    if (o.has("x")) m.x = o.get("x").getAsDouble();
                    if (o.has("y")) m.y = o.get("y").getAsDouble();
                    if (o.has("width")) m.width = Math.max(0, Math.min(400, o.get("width").getAsInt()));
                    if (o.has("height")) m.height = Math.max(0, Math.min(240, o.get("height").getAsInt()));
                    if (o.has("scale")) m.scale = Math.max(0.25, Math.min(4.0, o.get("scale").getAsDouble()));
                    if (o.has("opacity")) m.opacity = Math.max(0.0, Math.min(1.0, o.get("opacity").getAsDouble()));
                    if (o.has("textOpacity")) m.textOpacity = Math.max(0.1, Math.min(1.0, o.get("textOpacity").getAsDouble()));
                    if (o.has("borderRadius")) m.borderRadius = Math.max(0, Math.min(30, o.get("borderRadius").getAsInt()));
                    if (o.has("borderWidth")) m.borderWidth = Math.max(0, Math.min(5, o.get("borderWidth").getAsInt()));
                    if (o.has("borderStyle")) m.borderStyle = o.get("borderStyle").getAsString();
                    if (o.has("paddingX")) m.paddingX = Math.max(1, Math.min(24, o.get("paddingX").getAsInt()));
                    if (o.has("paddingY")) m.paddingY = Math.max(1, Math.min(24, o.get("paddingY").getAsInt()));
                    if (o.has("margin")) m.margin = Math.max(0, Math.min(16, o.get("margin").getAsInt()));
                    if (o.has("blurRadius")) m.blurRadius = Math.max(0, Math.min(30, o.get("blurRadius").getAsInt()));
                    if (o.has("bg")) m.bg = o.get("bg").getAsBoolean();
                    if (o.has("border")) m.border = o.get("border").getAsBoolean();
                    if (o.has("chroma")) m.chroma = o.get("chroma").getAsBoolean();
                    if (o.has("chromaSpeed")) m.chromaSpeed = o.get("chromaSpeed").getAsDouble();
                    if (o.has("chromaSat")) m.chromaSat = o.get("chromaSat").getAsDouble();
                    if (o.has("chromaAngle")) m.chromaAngle = o.get("chromaAngle").getAsDouble();
                    if (o.has("shadow")) m.shadow = o.get("shadow").getAsBoolean();
                    if (o.has("dropShadowX")) m.dropShadowX = o.get("dropShadowX").getAsInt();
                    if (o.has("dropShadowY")) m.dropShadowY = o.get("dropShadowY").getAsInt();
                    if (o.has("dropShadowBlur")) m.dropShadowBlur = o.get("dropShadowBlur").getAsInt();
                    if (o.has("dropShadowColor")) m.dropShadowColor = o.get("dropShadowColor").getAsString();
                    if (o.has("color")) m.color = o.get("color").getAsString();
                    if (o.has("bgColor")) m.bgColor = o.get("bgColor").getAsString();
                    if (o.has("borderColor")) m.borderColor = o.get("borderColor").getAsString();
                    if (o.has("borderColor2")) m.borderColor2 = o.get("borderColor2").getAsString();
                    if (o.has("anchor")) m.anchor = o.get("anchor").getAsString();
                    if (o.has("fontFamily")) m.fontFamily = o.get("fontFamily").getAsString();
                    if (o.has("fontWeight")) m.fontWeight = o.get("fontWeight").getAsString();
                    if (o.has("letterSpacing")) m.letterSpacing = o.get("letterSpacing").getAsInt();
                    if (o.has("prefix")) m.prefix = o.get("prefix").getAsString();
                    if (o.has("suffix")) m.suffix = o.get("suffix").getAsString();
                    if (o.has("bracketStyle")) m.bracketStyle = o.get("bracketStyle").getAsString();
                    if (o.has("displayMode")) m.displayMode = o.get("displayMode").getAsString();
                    if (o.has("customText")) m.customText = o.get("customText").getAsString();
                    if (o.has("hideInF1")) m.hideInF1 = o.get("hideInF1").getAsBoolean();
                    if (o.has("hideInChat")) m.hideInChat = o.get("hideInChat").getAsBoolean();
                    if (o.has("hideInInventory")) m.hideInInventory = o.get("hideInInventory").getAsBoolean();
                    if (o.has("hideInF3")) m.hideInF3 = o.get("hideInF3").getAsBoolean();
                    if (m.optId != null && options.containsKey(m.optId)) m.enabled = getBool(m.optId, m.enabled);
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
        root.addProperty("schemaVersion", Integer.valueOf(2));
        root.addProperty("updatedAt", Long.valueOf(updatedAt));
        root.addProperty("menuKey", menuKey);
        root.addProperty("activeProfile", activeProfile);

        JsonObject mods = new JsonObject();
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = e.getValue();
            if (m.optId != null) m.enabled = getBool(m.optId, m.enabled);
            JsonObject o = new JsonObject();
            o.addProperty("label", m.label);
            o.addProperty("category", m.category);
            if (m.optId != null) o.addProperty("optId", m.optId);
            o.addProperty("enabled", Boolean.valueOf(m.enabled));
            o.addProperty("favorite", Boolean.valueOf(m.favorite));
            o.addProperty("x", Double.valueOf(Math.round(m.x * 1000.0) / 1000.0));
            o.addProperty("y", Double.valueOf(Math.round(m.y * 1000.0) / 1000.0));
            o.addProperty("width", Integer.valueOf(m.width));
            o.addProperty("height", Integer.valueOf(m.height));
            o.addProperty("scale", Double.valueOf(Math.round(m.scale * 100.0) / 100.0));
            o.addProperty("opacity", Double.valueOf(Math.round(m.opacity * 100.0) / 100.0));
            o.addProperty("textOpacity", Double.valueOf(Math.round(m.textOpacity * 100.0) / 100.0));
            o.addProperty("borderRadius", Integer.valueOf(m.borderRadius));
            o.addProperty("borderWidth", Integer.valueOf(m.borderWidth));
            o.addProperty("borderStyle", m.borderStyle);
            o.addProperty("paddingX", Integer.valueOf(m.paddingX));
            o.addProperty("paddingY", Integer.valueOf(m.paddingY));
            o.addProperty("margin", Integer.valueOf(m.margin));
            o.addProperty("blurRadius", Integer.valueOf(m.blurRadius));
            o.addProperty("bg", Boolean.valueOf(m.bg));
            o.addProperty("border", Boolean.valueOf(m.border));
            o.addProperty("chroma", Boolean.valueOf(m.chroma));
            o.addProperty("chromaSpeed", Double.valueOf(m.chromaSpeed));
            o.addProperty("chromaSat", Double.valueOf(m.chromaSat));
            o.addProperty("chromaAngle", Double.valueOf(m.chromaAngle));
            o.addProperty("shadow", Boolean.valueOf(m.shadow));
            o.addProperty("dropShadowX", Integer.valueOf(m.dropShadowX));
            o.addProperty("dropShadowY", Integer.valueOf(m.dropShadowY));
            o.addProperty("dropShadowBlur", Integer.valueOf(m.dropShadowBlur));
            o.addProperty("dropShadowColor", m.dropShadowColor);
            o.addProperty("color", m.color);
            o.addProperty("bgColor", m.bgColor);
            o.addProperty("borderColor", m.borderColor);
            o.addProperty("borderColor2", m.borderColor2);
            o.addProperty("anchor", m.anchor);
            o.addProperty("fontFamily", m.fontFamily);
            o.addProperty("fontWeight", m.fontWeight);
            o.addProperty("letterSpacing", Integer.valueOf(m.letterSpacing));
            o.addProperty("prefix", m.prefix);
            o.addProperty("suffix", m.suffix);
            o.addProperty("bracketStyle", m.bracketStyle);
            o.addProperty("displayMode", m.displayMode);
            o.addProperty("customText", m.customText);
            o.addProperty("hideInF1", Boolean.valueOf(m.hideInF1));
            o.addProperty("hideInChat", Boolean.valueOf(m.hideInChat));
            o.addProperty("hideInInventory", Boolean.valueOf(m.hideInInventory));
            o.addProperty("hideInF3", Boolean.valueOf(m.hideInF3));
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
    public void resetLayout() {
        Map<String, JsonElement> kept = new LinkedHashMap<String, JsonElement>(options);
        initDefaults(); options.putAll(kept); save();
    }
    public void resetAll() { options.clear(); initDefaults(); save(); }
}
