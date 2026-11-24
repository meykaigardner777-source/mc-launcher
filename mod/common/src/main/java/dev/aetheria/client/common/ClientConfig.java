package dev.aetheria.client.common;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ClientConfig {
    private static final ClientConfig INSTANCE = new ClientConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] WP_COLORS = {"#22d3ee", "#10b981", "#f59e0b", "#ef4444", "#a855f7", "#ffffff"};
    private static final String[] ACC_TAGS = {"Main", "PvP", "SMP", "Alt", "Test"};

    private Path configPath;
    private long lastPollMs = 0L, lastFileMtimeMs = 0L;
    public String menuKey = "RSHIFT", activeProfile = "PvP", activeAccountId = "";
    public long updatedAt = 0L;
    public final Map<String, HudModule> hudModules = new LinkedHashMap<String, HudModule>();
    public final Map<String, TitleWidget> titleWidgets = new LinkedHashMap<String, TitleWidget>();
    public final Map<String, JsonElement> options = new LinkedHashMap<String, JsonElement>();
    public final List<Waypoint> waypoints = new ArrayList<Waypoint>();
    public final List<AccountEntry> accounts = new ArrayList<AccountEntry>();

    public static class Waypoint {
        public String id, name, dimension = "minecraft:overworld", worldId = "global", color = "#22d3ee";
        public int x, y, z;
        public boolean enabled = true;
    }
    public static class AccountEntry {
        public String id, name, type = "offline", tag = "Alt", uuid = "";
        public boolean pinned = false;
    }
    public static class TitleWidget {
        public String id, label, desc = "";
        public boolean enabled = true;
        public double x, y, scale = 1.0, opacity = 0.88;
        public int borderRadius = 8;
    }
    public static class HudModule {
        public String id, label, category = "system", optId, desc = "", anchor = "top-left";
        public String color = "#ffffff", bgColor = "#09090b", borderColor = "#27272a", borderColor2 = "#ffffff";
        public String borderStyle = "solid", fontFamily = "default", fontWeight = "normal", prefix = "", suffix = "", bracketStyle = "none", displayMode = "icon_text", customText = "Aetheria Client", dropShadowColor = "#000000";
        public boolean enabled, favorite = false, bg = true, border = false, chroma = false, shadow = true, hideInF1 = true, hideInChat = false, hideInInventory = false, hideInF3 = true;
        public double x, y, scale = 1.0, opacity = 0.35, textOpacity = 1.0, chromaSpeed = 1.0, chromaSat = 0.85, chromaAngle = 45.0;
        public int width = 0, height = 0, borderRadius = 8, borderWidth = 1, paddingX = 6, paddingY = 4, margin = 0, blurRadius = 8, letterSpacing = 0, dropShadowX = 0, dropShadowY = 2, dropShadowBlur = 0;
    }

    public static ClientConfig get() { return INSTANCE; }
    public synchronized void init(Path configDir) { this.configPath = configDir.resolve("aetheria-client.json"); load(); }
    public synchronized void reloadIfChanged() {
        if (configPath == null) return;
        long now = System.currentTimeMillis();
        if (now - lastPollMs < 500L) return;
        lastPollMs = now;
        try { if (Files.exists(configPath) && Files.getLastModifiedTime(configPath).toMillis() > lastFileMtimeMs) load(); } catch (IOException ignored) {}
    }

    private void addTitle(String id, String label, String desc, boolean en, double x, double y) {
        TitleWidget tw = new TitleWidget(); tw.id = id; tw.label = label; tw.desc = desc; tw.enabled = en; tw.x = x; tw.y = y; titleWidgets.put(id, tw);
    }
    public void resetTitleLayout() {
        titleWidgets.clear();
        addTitle("logo", "Aetheria Banner", "Client title & active profile badge", true, 0.50, 0.18);
        addTitle("quick_play", "Play Button", "Quick launch into recent world/menu", true, 0.50, 0.33);
        addTitle("singleplayer", "Singleplayer", "Local worlds & saves button", true, 0.50, 0.42);
        addTitle("multiplayer", "Servers / Multiplayer", "Server browser & quick-join", true, 0.50, 0.50);
        addTitle("realms", "Realms Button", "Minecraft Realms screen", true, 0.50, 0.58);
        addTitle("options", "Options / Settings", "Game video, audio & keybinds", true, 0.41, 0.67);
        addTitle("client_menu", "Aetheria Menu", "Open Aetheria Client settings", true, 0.59, 0.67);
        addTitle("quit", "Quit / ESC Button", "Exit Minecraft to desktop", true, 0.50, 0.76);
        addTitle("account", "Account Widget", "Switch, add & erase alt accounts", true, 0.02, 0.04);
        addTitle("background", "Background Widget", "Switch animated title wallpaper", true, 0.98, 0.04);
    }

    private void initDefaults() {
        if (titleWidgets.isEmpty()) resetTitleLayout();
        hudModules.clear();
        add("fps", "FPS Counter", "system", "hud_fps", true, 0.015, 0.025, "top-left", "Current, Min & 1% Low FPS");
        add("ping", "Ping / Latency", "system", "pvp_ping_hud", true, 0.015, 0.075, "top-left", "Live ms latency");
        add("coords", "Coordinates XYZ", "world", "hud_coords", true, 0.015, 0.125, "top-left", "Overworld + Nether 8:1 coords");
        add("compass", "Compass", "world", "hud_compass", true, 0.500, 0.020, "top-center", "Facing direction & degrees");
        add("session", "Session Timer", "system", "hud_session", true, 0.985, 0.025, "top-right", "Current play session duration");
        add("potions", "Potion Effects", "pvp", "pvp_potion_hud", true, 0.015, 0.450, "mid-left", "Active effects (auto-hides when empty)");
        add("keystrokes", "Keystrokes", "pvp", "pvp_keystrokes", true, 0.015, 0.870, "bottom-left", "WASD, Space & Mouse buttons");
        add("cps", "CPS Counter", "pvp", "pvp_cps", true, 0.015, 0.975, "bottom-left", "LMB & RMB clicks per second");
        add("togglesprint", "ToggleSprint", "pvp", "pvp_togglesprint", true, 0.500, 0.770, "bottom-center", "Sprint & sneak state badge");
        add("armor", "Armor Status", "pvp", "pvp_armor_hud", true, 0.985, 0.975, "bottom-right", "Equipped armor durability");
        add("durability", "Held Durability", "pvp", "hud_durability", true, 0.985, 0.915, "bottom-right", "Main-hand & off-hand durability");
        add("hotbar", "Hotbar HUD", "visuals", "hud_hotbar", true, 0.500, 1.000, "bottom-center", "Reposition & resize your hotbar + bars");
        add("scoreboard", "Scoreboard", "server", "hud_scoreboard", true, 0.985, 0.500, "mid-right", "Movable & scalable custom Scoreboard");
        add("frametime_graph", "Frametime Graph", "system", "hud_ft_graph", false, 0.015, 0.185, "top-left", "Rolling frame ms spike chart");
        add("ram_usage", "RAM / Memory", "system", "hud_ram", false, 0.015, 0.255, "top-left", "JVM heap MB used & % bar");
        add("cpu_load", "CPU / Threads", "system", "hud_cpu", false, 0.015, 0.315, "top-left", "Cores & active threads");
        add("ping_graph", "Ping Graph", "system", "hud_ping_graph", false, 0.015, 0.365, "top-left", "Rolling latency graph");
        add("server_tps", "Server TPS", "system", "hud_tps", false, 0.015, 0.435, "top-left", "Estimated server TPS & tick ms");
        add("packet_counter", "Packet Counter", "system", "hud_packets", false, 0.015, 0.485, "top-left", "World tick rate");
        add("total_playtime", "Total Playtime", "system", "hud_total_time", false, 0.985, 0.075, "top-right", "Session duration");
        add("clock", "Real-World Clock", "system", "hud_clock", false, 0.985, 0.125, "top-right", "12h/24h local time");
        add("stopwatch", "Stopwatch Timer", "system", "hud_stopwatch", false, 0.985, 0.175, "top-right", "Start/Pause/Reset stopwatch");
        add("countdown", "Countdown Timer", "system", "hud_countdown", false, 0.985, 0.225, "top-right", "Event/pearl alert timer");
        add("gc_monitor", "Java / GC Monitor", "system", "hud_gc", false, 0.985, 0.275, "top-right", "Garbage collection pauses");
        add("chunk_updates", "Chunk Pos", "system", "hud_chunks_upd", false, 0.985, 0.325, "top-right", "Current chunk region");
        add("entity_counter", "Entity Counter", "system", "hud_entities", false, 0.985, 0.375, "top-right", "Rendered entity count");
        add("cps_graph", "CPS Graph", "pvp", "pvp_cps_graph", false, 0.140, 0.975, "bottom-left", "Live click-speed waveform");
        add("mouse_visualizer", "Mouse Visualizer", "pvp", "pvp_mouse_viz", false, 0.140, 0.870, "bottom-left", "Live cursor velocity pad");
        add("combo", "Combo Counter", "pvp", "pvp_combo_hud", false, 0.500, 0.620, "center", "Consecutive melee hits");
        add("reach", "Reach Display", "pvp", "pvp_reach_hud", false, 0.500, 0.670, "center", "Last melee hit distance");
        add("hit_accuracy", "Hit Accuracy %", "pvp", "pvp_accuracy", false, 0.500, 0.720, "center", "Swings landed ratio");
        add("sprint_reset", "Sprint Reset", "pvp", "pvp_sprint_reset", false, 0.500, 0.815, "bottom-center", "Sprint-reset status");
        add("target_hud", "Target HUD", "pvp", "pvp_target_hud", false, 0.580, 0.560, "top-left", "Target HP bar & distance");
        add("potion_count", "Potion Counter", "pvp", "pvp_pot_count", false, 0.160, 0.540, "top-left", "Potions in inventory");
        add("heal_count", "GApple Counter", "pvp", "pvp_heal_count", false, 0.160, 0.590, "top-left", "Golden apples in inventory");
        add("arrow_count", "Arrow Counter", "pvp", "pvp_arrow_count", false, 0.160, 0.640, "top-left", "Arrows in inventory");
        add("totem_count", "Totem Counter", "pvp", "pvp_totem_count", false, 0.160, 0.690, "top-left", "Totems in inventory");
        add("kill_counter", "K/D Counter", "pvp", "pvp_kills_hud", false, 0.160, 0.740, "top-left", "Session kills & deaths");
        add("pearl_cd", "Pearl Cooldown", "pvp", "pvp_pearl_cd", false, 0.500, 0.855, "bottom-center", "Pearl cooldown (auto-hides)");
        add("shield_cd", "Shield Cooldown", "pvp", "pvp_shield_cd", false, 0.500, 0.895, "bottom-center", "5s shield cooldown (auto-hides)");
        add("attack_charge", "Attack / Bow Bar", "pvp", "pvp_atk_bar", false, 0.500, 0.545, "center", "1.9+ attack recharge bar");
        add("speedometer", "Speedometer", "pvp", "pvp_speed", false, 0.015, 0.720, "bottom-left", "Blocks/sec, km/h or mph");
        add("chunk_coords", "Chunk Coords", "world", "hud_chunk_xyz", false, 0.015, 0.180, "top-left", "Chunk X/Y/Z");
        add("biome", "Biome Display", "world", "hud_biome", false, 0.015, 0.230, "top-left", "Current world biome");
        add("light_level", "Light Level", "world", "hud_light", false, 0.180, 0.075, "top-left", "Block & sky light level");
        add("slime_chunk", "Slime Chunk", "world", "hud_slime", false, 0.180, 0.125, "top-left", "Slime chunk detector");
        add("day_count", "Day & Time", "world", "hud_day_count", false, 0.985, 0.425, "top-right", "World day count & time");
        add("moon_phase", "Moon Phase", "world", "hud_moon", false, 0.985, 0.475, "top-right", "Current moon phase");
        add("weather", "Weather", "world", "hud_weather", false, 0.985, 0.525, "top-right", "Clear / Rain / Thunder");
        add("minimap", "Radar Minimap", "world", "hud_minimap", false, 0.985, 0.620, "mid-right", "Live entity & waypoint radar");
        add("waypoints", "Waypoints", "world", "hud_waypoints", false, 0.720, 0.025, "top-left", "Per-world Lunar/Dot/Block/Beacon waypoints");
        add("spawn_dist", "Spawn Distance", "world", "hud_spawn_dist", false, 0.720, 0.075, "top-left", "Distance to spawn");
        add("death_loc", "Death Tracker", "world", "hud_death_loc", false, 0.720, 0.125, "top-left", "Last death coordinates");
        add("y_alert", "Y-Level Alert", "world", "hud_y_alert", false, 0.180, 0.175, "top-left", "Diamond / Debris Y alert");
        add("saturation", "Saturation Bar", "world", "hud_saturation", false, 0.500, 0.730, "bottom-center", "AppleSkin-style saturation readout");
        add("oxygen", "Oxygen Timer", "world", "hud_oxygen", false, 0.500, 0.690, "bottom-center", "Air remaining underwater");
        add("elytra_guide", "Elytra Pitch", "world", "hud_elytra", false, 0.500, 0.415, "center", "Optimal glide pitch readout");
        add("villager_timer", "Villager Restock", "world", "hud_villager", false, 0.720, 0.225, "top-left", "Day-cycle restock window");
        add("waila_block", "Looking-At Block", "world", "hud_waila_blk", false, 0.500, 0.075, "top-center", "Targeted block name");
        add("waila_entity", "Looking-At Entity", "world", "hud_waila_ent", false, 0.500, 0.125, "top-center", "Targeted entity name");
        add("inv_space", "Inventory Space", "world", "hud_inv_space", false, 0.985, 0.855, "bottom-right", "Used vs free slots");
        add("mini_inventory", "Mini Inventory", "world", "hud_mini_inv", false, 0.985, 0.805, "bottom-right", "Compact backpack summary");
        add("pickup_feed", "Item Pickups", "world", "hud_pickups", false, 0.985, 0.720, "bottom-right", "Recent picked up items");
        add("server_ip", "Server IP", "server", "hud_srv_ip", false, 0.985, 0.575, "top-right", "Connected server IP");
        add("player_count", "Online Players", "server", "hud_players", false, 0.985, 0.625, "top-right", "Connected player count");
        add("bw_resources", "BW Resources", "server", "hud_bw_res", false, 0.160, 0.820, "top-left", "Iron, Gold, Diamond & Emerald count");
        add("uhc_border", "0,0 Distance", "server", "hud_uhc_border", false, 0.380, 0.310, "top-left", "Distance from 0,0");
        add("paperdoll", "Paperdoll Player", "visuals", "hud_paperdoll", false, 0.985, 0.720, "mid-right", "Live 3D Bedrock-style MC skin preview");
        add("zoom_indicator", "Zoom Indicator", "visuals", "hud_zoom_ind", false, 0.500, 0.360, "center", "Live zoom magnification");
        add("gamma_level", "Gamma / Bright", "visuals", "hud_gamma", false, 0.560, 0.025, "top-left", "Current brightness %");
        add("fov_indicator", "FOV Indicator", "visuals", "hud_fov", false, 0.560, 0.070, "top-left", "Current FOV readout");
        add("watermark", "Watermark Text", "visuals", "hud_watermark", false, 0.015, 0.005, "top-left", "Custom client text");
        add("low_hp_vignette", "Low-HP Pulse", "visuals", "hud_low_hp", false, 0.500, 0.500, "center", "Red screen edge pulse at low HP");

        HudModule hb = hudModules.get("hotbar"); if (hb != null) { hb.bg = false; hb.border = false; }
        for (HudModule m : hudModules.values()) if (m.optId != null) setDefaultOpt(m.optId, Boolean.valueOf(m.enabled));

        setDefaultOpt("global_hud_rounding", Integer.valueOf(-1)); setDefaultOpt("global_hud_opacity", Double.valueOf(-1.0));
        setDefaultOpt("ui_menu_layout", "oneclient"); setDefaultOpt("ui_clean_font", Boolean.TRUE);
        setDefaultOpt("ui_custom_title", Boolean.TRUE); setDefaultOpt("title_bg_style", "waves"); setDefaultOpt("title_bg_dim", Double.valueOf(0.55));
        setDefaultOpt("ui_switch_style", "pill"); setDefaultOpt("ui_card_width", Integer.valueOf(138));
        setDefaultOpt("ui_menu_rounding", Integer.valueOf(10)); setDefaultOpt("ui_menu_opacity", Double.valueOf(0.92));
        setDefaultOpt("ui_accent", "white"); setDefaultOpt("ui_accent_custom", "#ff3333");
        setDefaultOpt("ui_animations", Boolean.TRUE); setDefaultOpt("ui_anim_speed", Double.valueOf(1.0)); setDefaultOpt("ui_anim_curve", "smooth");
        setDefaultOpt("ui_click_sound", Boolean.TRUE); setDefaultOpt("ui_tooltips", Boolean.TRUE);
        setDefaultOpt("perf_unfocused_fps", Boolean.TRUE); setDefaultOpt("perf_hud_throttle", Boolean.TRUE);
        setDefaultOpt("hud_snap_grid", Boolean.TRUE); setDefaultOpt("hud_align_guides", Boolean.TRUE); setDefaultOpt("hud_shadow", Boolean.TRUE);
        setDefaultOpt("sb_hide_vanilla", Boolean.TRUE); setDefaultOpt("sb_hide_numbers", Boolean.FALSE);
        setDefaultOpt("pvp_low_fire", Boolean.TRUE); setDefaultOpt("pvp_low_fire_offset", Double.valueOf(-0.32));
        setDefaultOpt("pvp_low_shield", Boolean.FALSE); setDefaultOpt("pvp_low_shield_blocking", Boolean.FALSE);
        setDefaultOpt("pvp_low_shield_scale", Double.valueOf(0.85)); setDefaultOpt("pvp_low_shield_y", Double.valueOf(-0.22)); setDefaultOpt("pvp_low_shield_x", Double.valueOf(0.0));
        setDefaultOpt("vm_enabled", Boolean.FALSE); setDefaultOpt("vm_main_x", Double.valueOf(0.0)); setDefaultOpt("vm_main_y", Double.valueOf(0.0)); setDefaultOpt("vm_main_z", Double.valueOf(0.0)); setDefaultOpt("vm_main_scale", Double.valueOf(1.0));
        setDefaultOpt("vm_off_x", Double.valueOf(0.0)); setDefaultOpt("vm_off_y", Double.valueOf(0.0)); setDefaultOpt("vm_off_z", Double.valueOf(0.0)); setDefaultOpt("vm_off_scale", Double.valueOf(1.0));
        setDefaultOpt("pvp_small_totem", Boolean.FALSE); setDefaultOpt("pvp_small_totem_scale", Double.valueOf(0.60)); setDefaultOpt("pvp_small_totem_y", Double.valueOf(-0.12));
        setDefaultOpt("pvp_hit_color", Boolean.FALSE); setDefaultOpt("pvp_hit_color_hex", "#22d3ee"); setDefaultOpt("pvp_hit_color_alpha", Double.valueOf(0.55)); setDefaultOpt("pvp_hit_color_chroma", Boolean.FALSE);
        setDefaultOpt("hb_enabled", Boolean.FALSE); setDefaultOpt("hb_only_f3b", Boolean.FALSE); setDefaultOpt("hb_players", Boolean.TRUE); setDefaultOpt("hb_mobs", Boolean.TRUE);
        setDefaultOpt("hb_items", Boolean.FALSE); setDefaultOpt("hb_projectiles", Boolean.TRUE); setDefaultOpt("hb_hide_self", Boolean.TRUE); setDefaultOpt("hb_color", "#22d3ee");
        setDefaultOpt("hb_fill_alpha", Double.valueOf(0.12)); setDefaultOpt("hb_line_width", Double.valueOf(2.0)); setDefaultOpt("hb_chroma", Boolean.FALSE); setDefaultOpt("hb_eye_line", Boolean.TRUE); setDefaultOpt("hb_look_vec", Boolean.TRUE);
        setDefaultOpt("bb_enabled", Boolean.FALSE); setDefaultOpt("bb_style", "shrink"); setDefaultOpt("bb_hide_vanilla", Boolean.FALSE); setDefaultOpt("bb_progress_color", Boolean.TRUE);
        setDefaultOpt("bb_color", "#22d3ee"); setDefaultOpt("bb_fill_alpha", Double.valueOf(0.30)); setDefaultOpt("bb_line_width", Double.valueOf(2.5));
        setDefaultOpt("pvp_no_hurtcam", Boolean.FALSE); setDefaultOpt("pvp_hurtcam_factor", Double.valueOf(1.0));
        setDefaultOpt("cam_fov_dynamic", Boolean.TRUE); setDefaultOpt("cam_fov_factor", Double.valueOf(1.0)); setDefaultOpt("cam_fov_sprint", Boolean.TRUE); setDefaultOpt("cam_fov_bow", Boolean.TRUE);
        setDefaultOpt("pvp_xhair_style", "classic"); setDefaultOpt("pvp_xhair_size", Integer.valueOf(6)); setDefaultOpt("pvp_xhair_gap", Integer.valueOf(2)); setDefaultOpt("pvp_xhair_thick", Integer.valueOf(1));
        setDefaultOpt("pvp_xhair_color", "#ffffff"); setDefaultOpt("pvp_xhair_target", Boolean.TRUE); setDefaultOpt("pvp_xhair_target_players_only", Boolean.TRUE); setDefaultOpt("pvp_xhair_target_range", Integer.valueOf(12));
        setDefaultOpt("pvp_xhair_target_style", "same"); setDefaultOpt("pvp_xhair_target_color", "#ef4444"); setDefaultOpt("pvp_xhair_pixel_scale", Integer.valueOf(1));
        setDefaultOpt("pvp_xhair_atk_ind", "bar"); setDefaultOpt("pvp_xhair_atk_always", Boolean.FALSE); setDefaultOpt("pvp_xhair_atk_color", "#ffffff");
        setDefaultOpt("potions_hide_vanilla", Boolean.TRUE); setDefaultOpt("armor_horizontal", Boolean.FALSE); setDefaultOpt("armor_show_text", Boolean.TRUE);
        setDefaultOpt("saturation_glint", Boolean.TRUE); setDefaultOpt("saturation_style", "appleskin"); setDefaultOpt("potions_timers", Boolean.TRUE);
        setDefaultOpt("potions_numerals", Boolean.TRUE); setDefaultOpt("potions_show_icon", Boolean.TRUE); setDefaultOpt("potions_blink", Boolean.TRUE); setDefaultOpt("potions_auto_hide", Boolean.TRUE);
        setDefaultOpt("armor_text_mode", Boolean.FALSE); setDefaultOpt("pearl_auto_hide", Boolean.TRUE); setDefaultOpt("shield_auto_hide", Boolean.TRUE);
        setDefaultOpt("wp_style", "lunar"); setDefaultOpt("wp_auto_death", Boolean.TRUE); setDefaultOpt("wp_world_markers", Boolean.TRUE); setDefaultOpt("wp_beam_beacon", Boolean.TRUE);
        setDefaultOpt("wp_show_dist", Boolean.TRUE); setDefaultOpt("wp_nether_sync", Boolean.TRUE); setDefaultOpt("wp_offscreen_arrows", Boolean.TRUE);
        setDefaultOpt("cam_zoom", Boolean.TRUE); setDefaultOpt("cam_zoom_fac", Integer.valueOf(4)); setDefaultOpt("cam_zoom_ease", Boolean.TRUE); setDefaultOpt("cam_zoom_slow", Boolean.TRUE);
        setDefaultOpt("cam_zoom_scroll", Boolean.TRUE); setDefaultOpt("cam_zoom_reset_scroll", Boolean.TRUE); setDefaultOpt("cam_zoom_key", "C");
        setDefaultOpt("util_fullbright", Boolean.FALSE); setDefaultOpt("util_fullbright_level", Double.valueOf(12.0));
        setDefaultOpt("util_freelook", Boolean.TRUE); setDefaultOpt("util_freelook_key", "LALT");
        setDefaultOpt("ctl_menu_key", "RSHIFT"); setDefaultOpt("ctl_waypoints_key", "B"); setDefaultOpt("ctl_fullbright_key", "F7"); setDefaultOpt("ctl_profile_key", "F6");
        setDefaultOpt("pvp_togglesprint", Boolean.TRUE); setDefaultOpt("pvp_togglesneak", Boolean.FALSE);
        setDefaultOpt("fps_show_label", Boolean.TRUE); setDefaultOpt("fps_detailed", Boolean.FALSE);
        setDefaultOpt("coords_facing", Boolean.TRUE); setDefaultOpt("coords_compact", Boolean.FALSE); setDefaultOpt("coords_nether", Boolean.TRUE);
        setDefaultOpt("clock_24h", Boolean.FALSE); setDefaultOpt("clock_seconds", Boolean.FALSE); setDefaultOpt("compass_degrees", Boolean.TRUE);
        setDefaultOpt("armor_percent", Boolean.TRUE); setDefaultOpt("keystrokes_cps", Boolean.TRUE); setDefaultOpt("keystrokes_space", Boolean.TRUE);
        setDefaultOpt("keystrokes_mods", Boolean.FALSE); setDefaultOpt("togglesprint_hide_hud", Boolean.FALSE); setDefaultOpt("speed_unit", "bps");
    }

    private void add(String id, String label, String cat, String optId, boolean en, double x, double y, String anc, String desc) {
        HudModule m = new HudModule(); m.id = id; m.label = label; m.category = cat; m.optId = optId; m.enabled = en; m.x = x; m.y = y; m.anchor = anc; m.desc = desc; hudModules.put(id, m);
    }
    public synchronized void addAccount(String name, String type, String tag) {
        if (name == null || name.trim().isEmpty()) return;
        String clean = name.trim();
        for (AccountEntry a : accounts) if (clean.equalsIgnoreCase(a.name)) { activeAccountId = a.id; save(); return; }
        AccountEntry a = new AccountEntry(); a.id = "acc_" + System.currentTimeMillis() + "_" + accounts.size(); a.name = clean;
        a.type = (type == null || type.isEmpty()) ? "offline" : type; a.tag = (tag == null || tag.isEmpty()) ? "Alt" : tag;
        a.uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8)).toString();
        accounts.add(a); activeAccountId = a.id; save();
    }
    public synchronized void removeAccount(String id) {
        for (int i = accounts.size() - 1; i >= 0; i--) if (id.equals(accounts.get(i).id)) accounts.remove(i);
        if (id.equals(activeAccountId)) activeAccountId = accounts.isEmpty() ? "" : accounts.get(0).id; save();
    }
    public synchronized void moveAccount(String id, int delta) {
        for (int i = 0; i < accounts.size(); i++) if (id.equals(accounts.get(i).id)) {
            int j = Math.max(0, Math.min(accounts.size() - 1, i + delta));
            if (i != j) { AccountEntry tmp = accounts.remove(i); accounts.add(j, tmp); save(); } return;
        }
    }
    public synchronized void cycleAccountTag(AccountEntry a) {
        int next = 0; for (int i = 0; i < ACC_TAGS.length; i++) if (ACC_TAGS[i].equalsIgnoreCase(a.tag)) next = (i + 1) % ACC_TAGS.length; a.tag = ACC_TAGS[next]; save();
    }
    public synchronized void addWaypointAt(String name, int x, int y, int z, String dim) { addWaypointAt(name, x, y, z, dim, ClientState.getCurrentWorldKey()); }
    public synchronized void addWaypointAt(String name, int x, int y, int z, String dim, String worldId) {
        Waypoint w = new Waypoint(); w.id = "wp_" + System.currentTimeMillis() + "_" + waypoints.size();
        w.name = (name == null || name.trim().isEmpty()) ? ("WP #" + (waypoints.size() + 1)) : name.trim();
        w.x = x; w.y = y; w.z = z; w.dimension = (dim == null || dim.isEmpty()) ? "minecraft:overworld" : dim;
        w.worldId = (worldId == null || worldId.isEmpty()) ? "global" : worldId;
        w.color = WP_COLORS[waypoints.size() % WP_COLORS.length]; w.enabled = true; waypoints.add(w); save();
    }
    public synchronized void removeWaypoint(String id) { for (int i = waypoints.size() - 1; i >= 0; i--) if (id.equals(waypoints.get(i).id)) waypoints.remove(i); save(); }
    public synchronized void cycleWaypointColor(Waypoint w) {
        int next = 0; for (int i = 0; i < WP_COLORS.length; i++) if (WP_COLORS[i].equalsIgnoreCase(w.color)) next = (i + 1) % WP_COLORS.length; w.color = WP_COLORS[next]; save();
    }
    public void applyProfilePreset(String name) {
        activeProfile = name;
        for (HudModule m : hudModules.values()) if (!"hotbar".equals(m.id)) setModuleEnabledSilent(m, false);
        if ("PvP".equalsIgnoreCase(name)) enableOnly(new String[]{"fps","ping","cps","keystrokes","togglesprint","potions","armor","durability","combo","reach","hit_accuracy","target_hud","compass","session","pearl_cd","shield_cd","hotbar","scoreboard"});
        else if ("Survival".equalsIgnoreCase(name)) enableOnly(new String[]{"fps","coords","biome","light_level","day_count","weather","durability","armor","saturation","inv_space","y_alert","waila_block","compass","waypoints","hotbar","scoreboard"});
        else if ("SMP".equalsIgnoreCase(name)) enableOnly(new String[]{"fps","coords","ping","server_tps","server_ip","player_count","waypoints","death_loc","totem_count","armor","potions","session","hotbar","scoreboard"});
        else if ("Minimal".equalsIgnoreCase(name)) enableOnly(new String[]{"fps","coords","ping","hotbar"});
        else if ("Cinematic".equalsIgnoreCase(name)) enableOnly(new String[]{"zoom_indicator"});
        save();
    }
    public void cycleProfilePreset() {
        String[] presets = {"PvP","Survival","SMP","Minimal","Cinematic"}; int idx = 0;
        for (int i = 0; i < presets.length; i++) if (presets[i].equalsIgnoreCase(activeProfile)) idx = (i + 1) % presets.length; applyProfilePreset(presets[idx]);
    }
    private void enableOnly(String[] ids) { for (int i = 0; i < ids.length; i++) { HudModule m = hudModules.get(ids[i]); if (m != null) setModuleEnabledSilent(m, true); } }
    private void setModuleEnabledSilent(HudModule m, boolean en) { m.enabled = en; if (m.optId != null) options.put(m.optId, new JsonPrimitive(Boolean.valueOf(en))); }
    public void resetModule(String id) {
        HudModule old = hudModules.get(id); Map<String, HudModule> snap = new LinkedHashMap<String, HudModule>(hudModules);
        initDefaults(); HudModule fresh = hudModules.get(id); hudModules.clear(); hudModules.putAll(snap);
        if (fresh != null) { if (old != null) fresh.enabled = old.enabled; hudModules.put(id, fresh); } save();
    }
    private void setDefaultOpt(String id, Object val) { if (!options.containsKey(id)) options.put(id, GSON.toJsonTree(val)); }

    public synchronized void load() {
        resetTitleLayout(); initDefaults();
        if (configPath == null || !Files.exists(configPath)) { if (configPath != null) save(); return; }
        try {
            lastFileMtimeMs = Files.getLastModifiedTime(configPath).toMillis();
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("menuKey") && !root.get("menuKey").isJsonNull()) menuKey = root.get("menuKey").getAsString();
            if (root.has("activeProfile") && !root.get("activeProfile").isJsonNull()) activeProfile = root.get("activeProfile").getAsString();
            if (root.has("activeAccountId") && !root.get("activeAccountId").isJsonNull()) activeAccountId = root.get("activeAccountId").getAsString();
            if (root.has("updatedAt")) updatedAt = root.get("updatedAt").getAsLong();
            if (root.has("options") && root.get("options").isJsonObject()) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("options").entrySet()) options.put(e.getKey(), e.getValue());
            if (root.has("titleWidgets") && root.get("titleWidgets").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("titleWidgets").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    TitleWidget tw = titleWidgets.containsKey(e.getKey()) ? titleWidgets.get(e.getKey()) : new TitleWidget();
                    tw.id = e.getKey();
                    if (o.has("label")) tw.label = o.get("label").getAsString(); if (o.has("desc")) tw.desc = o.get("desc").getAsString();
                    if (o.has("enabled")) tw.enabled = o.get("enabled").getAsBoolean(); if (o.has("x")) tw.x = o.get("x").getAsDouble();
                    if (o.has("y")) tw.y = o.get("y").getAsDouble(); if (o.has("scale")) tw.scale = o.get("scale").getAsDouble();
                    if (o.has("opacity")) tw.opacity = o.get("opacity").getAsDouble(); if (o.has("borderRadius")) tw.borderRadius = o.get("borderRadius").getAsInt();
                    titleWidgets.put(e.getKey(), tw);
                }
            }
            if (root.has("accounts") && root.get("accounts").isJsonArray()) {
                accounts.clear();
                for (JsonElement el : root.getAsJsonArray("accounts")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject(); AccountEntry a = new AccountEntry();
                    a.id = o.has("id") ? o.get("id").getAsString() : ("acc_" + accounts.size()); a.name = o.has("name") ? o.get("name").getAsString() : "Player";
                    a.type = o.has("type") ? o.get("type").getAsString() : "offline"; a.tag = o.has("tag") ? o.get("tag").getAsString() : "Alt";
                    a.uuid = o.has("uuid") ? o.get("uuid").getAsString() : ""; a.pinned = o.has("pinned") && o.get("pinned").getAsBoolean();
                    accounts.add(a);
                }
            }
            if (root.has("waypoints") && root.get("waypoints").isJsonArray()) {
                waypoints.clear();
                for (JsonElement el : root.getAsJsonArray("waypoints")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject(); Waypoint w = new Waypoint();
                    w.id = o.has("id") ? o.get("id").getAsString() : ("wp_" + waypoints.size()); w.name = o.has("name") ? o.get("name").getAsString() : "Waypoint";
                    w.x = o.has("x") ? o.get("x").getAsInt() : 0; w.y = o.has("y") ? o.get("y").getAsInt() : 64; w.z = o.has("z") ? o.get("z").getAsInt() : 0;
                    w.dimension = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
                    w.worldId = o.has("worldId") ? o.get("worldId").getAsString() : "global";
                    w.color = o.has("color") ? o.get("color").getAsString() : "#22d3ee"; w.enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
                    waypoints.add(w);
                }
            }
            if (root.has("hudModules") && root.get("hudModules").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("hudModules").entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    HudModule m = hudModules.containsKey(e.getKey()) ? hudModules.get(e.getKey()) : new HudModule(); m.id = e.getKey();
                    if (o.has("label")) m.label = o.get("label").getAsString(); if (o.has("category")) m.category = o.get("category").getAsString();
                    if (o.has("optId") && !o.get("optId").isJsonNull()) m.optId = o.get("optId").getAsString();
                    if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean(); if (o.has("favorite")) m.favorite = o.get("favorite").getAsBoolean();
                    if (o.has("x")) m.x = o.get("x").getAsDouble(); if (o.has("y")) m.y = o.get("y").getAsDouble();
                    if (o.has("width")) m.width = Math.max(0, Math.min(400, o.get("width").getAsInt())); if (o.has("height")) m.height = Math.max(0, Math.min(240, o.get("height").getAsInt()));
                    if (o.has("scale")) m.scale = Math.max(0.25, Math.min(4.0, o.get("scale").getAsDouble()));
                    if (o.has("opacity")) m.opacity = Math.max(0.0, Math.min(1.0, o.get("opacity").getAsDouble()));
                    if (o.has("textOpacity")) m.textOpacity = Math.max(0.1, Math.min(1.0, o.get("textOpacity").getAsDouble()));
                    if (o.has("borderRadius")) m.borderRadius = Math.max(0, Math.min(30, o.get("borderRadius").getAsInt()));
                    if (o.has("borderWidth")) m.borderWidth = Math.max(0, Math.min(5, o.get("borderWidth").getAsInt()));
                    if (o.has("borderStyle")) m.borderStyle = o.get("borderStyle").getAsString();
                    if (o.has("paddingX")) m.paddingX = Math.max(1, Math.min(24, o.get("paddingX").getAsInt())); if (o.has("paddingY")) m.paddingY = Math.max(1, Math.min(24, o.get("paddingY").getAsInt()));
                    if (o.has("margin")) m.margin = Math.max(0, Math.min(16, o.get("margin").getAsInt())); if (o.has("blurRadius")) m.blurRadius = Math.max(0, Math.min(30, o.get("blurRadius").getAsInt()));
                    if (o.has("bg")) m.bg = o.get("bg").getAsBoolean(); if (o.has("border")) m.border = o.get("border").getAsBoolean();
                    if (o.has("chroma")) m.chroma = o.get("chroma").getAsBoolean(); if (o.has("chromaSpeed")) m.chromaSpeed = o.get("chromaSpeed").getAsDouble();
                    if (o.has("chromaSat")) m.chromaSat = o.get("chromaSat").getAsDouble(); if (o.has("chromaAngle")) m.chromaAngle = o.get("chromaAngle").getAsDouble();
                    if (o.has("shadow")) m.shadow = o.get("shadow").getAsBoolean(); if (o.has("color")) m.color = o.get("color").getAsString();
                    if (o.has("bgColor")) m.bgColor = o.get("bgColor").getAsString(); if (o.has("borderColor")) m.borderColor = o.get("borderColor").getAsString();
                    if (o.has("borderColor2")) m.borderColor2 = o.get("borderColor2").getAsString(); if (o.has("anchor")) m.anchor = o.get("anchor").getAsString();
                    if (o.has("letterSpacing")) m.letterSpacing = o.get("letterSpacing").getAsInt(); if (o.has("prefix")) m.prefix = o.get("prefix").getAsString();
                    if (o.has("suffix")) m.suffix = o.get("suffix").getAsString(); if (o.has("bracketStyle")) m.bracketStyle = o.get("bracketStyle").getAsString();
                    if (o.has("customText")) m.customText = o.get("customText").getAsString();
                    if (o.has("hideInF1")) m.hideInF1 = o.get("hideInF1").getAsBoolean(); if (o.has("hideInChat")) m.hideInChat = o.get("hideInChat").getAsBoolean();
                    if (o.has("hideInInventory")) m.hideInInventory = o.get("hideInInventory").getAsBoolean(); if (o.has("hideInF3")) m.hideInF3 = o.get("hideInF3").getAsBoolean();
                    if (m.optId != null && options.containsKey(m.optId)) m.enabled = getBool(m.optId, m.enabled);
                    hudModules.put(e.getKey(), m);
                }
            }
            menuKey = getString("ctl_menu_key", menuKey);
        } catch (Exception ignored) {}
    }

    public synchronized void save() {
        if (configPath == null) return;
        updatedAt = System.currentTimeMillis(); menuKey = getString("ctl_menu_key", menuKey);
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", Integer.valueOf(2)); root.addProperty("updatedAt", Long.valueOf(updatedAt));
        root.addProperty("menuKey", menuKey); root.addProperty("activeProfile", activeProfile); root.addProperty("activeAccountId", activeAccountId);

        JsonObject twObj = new JsonObject();
        for (Map.Entry<String, TitleWidget> e : titleWidgets.entrySet()) {
            TitleWidget tw = e.getValue(); JsonObject o = new JsonObject();
            o.addProperty("label", tw.label); o.addProperty("desc", tw.desc); o.addProperty("enabled", Boolean.valueOf(tw.enabled));
            o.addProperty("x", Double.valueOf(Math.round(tw.x * 1000.0) / 1000.0)); o.addProperty("y", Double.valueOf(Math.round(tw.y * 1000.0) / 1000.0));
            o.addProperty("scale", Double.valueOf(Math.round(tw.scale * 100.0) / 100.0)); o.addProperty("opacity", Double.valueOf(Math.round(tw.opacity * 100.0) / 100.0));
            o.addProperty("borderRadius", Integer.valueOf(tw.borderRadius)); twObj.add(e.getKey(), o);
        }
        root.add("titleWidgets", twObj);

        JsonObject mods = new JsonObject();
        for (Map.Entry<String, HudModule> e : hudModules.entrySet()) {
            HudModule m = e.getValue(); if (m.optId != null) m.enabled = getBool(m.optId, m.enabled);
            JsonObject o = new JsonObject();
            o.addProperty("label", m.label); o.addProperty("category", m.category); if (m.optId != null) o.addProperty("optId", m.optId);
            o.addProperty("enabled", Boolean.valueOf(m.enabled)); o.addProperty("favorite", Boolean.valueOf(m.favorite));
            o.addProperty("x", Double.valueOf(Math.round(m.x * 1000.0) / 1000.0)); o.addProperty("y", Double.valueOf(Math.round(m.y * 1000.0) / 1000.0));
            o.addProperty("width", Integer.valueOf(m.width)); o.addProperty("height", Integer.valueOf(m.height));
            o.addProperty("scale", Double.valueOf(Math.round(m.scale * 100.0) / 100.0)); o.addProperty("opacity", Double.valueOf(Math.round(m.opacity * 100.0) / 100.0));
            o.addProperty("textOpacity", Double.valueOf(Math.round(m.textOpacity * 100.0) / 100.0)); o.addProperty("borderRadius", Integer.valueOf(m.borderRadius));
            o.addProperty("borderWidth", Integer.valueOf(m.borderWidth)); o.addProperty("borderStyle", m.borderStyle);
            o.addProperty("paddingX", Integer.valueOf(m.paddingX)); o.addProperty("paddingY", Integer.valueOf(m.paddingY));
            o.addProperty("margin", Integer.valueOf(m.margin)); o.addProperty("blurRadius", Integer.valueOf(m.blurRadius));
            o.addProperty("bg", Boolean.valueOf(m.bg)); o.addProperty("border", Boolean.valueOf(m.border));
            o.addProperty("chroma", Boolean.valueOf(m.chroma)); o.addProperty("chromaSpeed", Double.valueOf(m.chromaSpeed));
            o.addProperty("chromaSat", Double.valueOf(m.chromaSat)); o.addProperty("chromaAngle", Double.valueOf(m.chromaAngle));
            o.addProperty("shadow", Boolean.valueOf(m.shadow)); o.addProperty("color", m.color); o.addProperty("bgColor", m.bgColor);
            o.addProperty("borderColor", m.borderColor); o.addProperty("borderColor2", m.borderColor2); o.addProperty("anchor", m.anchor);
            o.addProperty("letterSpacing", Integer.valueOf(m.letterSpacing)); o.addProperty("prefix", m.prefix); o.addProperty("suffix", m.suffix);
            o.addProperty("bracketStyle", m.bracketStyle); o.addProperty("customText", m.customText);
            o.addProperty("hideInF1", Boolean.valueOf(m.hideInF1)); o.addProperty("hideInChat", Boolean.valueOf(m.hideInChat));
            o.addProperty("hideInInventory", Boolean.valueOf(m.hideInInventory)); o.addProperty("hideInF3", Boolean.valueOf(m.hideInF3));
            mods.add(e.getKey(), o);
        }
        root.add("hudModules", mods);

        JsonArray accArr = new JsonArray();
        for (AccountEntry a : accounts) {
            JsonObject ao = new JsonObject(); ao.addProperty("id", a.id); ao.addProperty("name", a.name); ao.addProperty("type", a.type);
            ao.addProperty("tag", a.tag); ao.addProperty("uuid", a.uuid); ao.addProperty("pinned", Boolean.valueOf(a.pinned)); accArr.add(ao);
        }
        root.add("accounts", accArr);

        JsonArray wpArr = new JsonArray();
        for (Waypoint w : waypoints) {
            JsonObject wo = new JsonObject(); wo.addProperty("id", w.id); wo.addProperty("name", w.name);
            wo.addProperty("x", Integer.valueOf(w.x)); wo.addProperty("y", Integer.valueOf(w.y)); wo.addProperty("z", Integer.valueOf(w.z));
            wo.addProperty("dimension", w.dimension); wo.addProperty("worldId", w.worldId == null ? "global" : w.worldId);
            wo.addProperty("color", w.color); wo.addProperty("enabled", Boolean.valueOf(w.enabled)); wpArr.add(wo);
        }
        root.add("waypoints", wpArr);

        JsonObject opts = new JsonObject();
        for (Map.Entry<String, JsonElement> e : options.entrySet()) opts.add(e.getKey(), e.getValue());
        root.add("options", opts);

        try {
            Files.createDirectories(configPath.getParent());
            Files.write(configPath, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            lastFileMtimeMs = Files.getLastModifiedTime(configPath).toMillis();
        } catch (IOException ignored) {}
    }

    public boolean getBool(String id, boolean def) { JsonElement el = options.get(id); return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) ? el.getAsBoolean() : def; }
    public double getDouble(String id, double def) { JsonElement el = options.get(id); return (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) ? el.getAsDouble() : def; }
    public String getString(String id, String def) { JsonElement el = options.get(id); return (el != null && el.isJsonPrimitive()) ? el.getAsString() : def; }
    public void setOption(String id, Object val) {
        options.put(id, GSON.toJsonTree(val));
        for (HudModule m : hudModules.values()) if (id.equals(m.optId) && val instanceof Boolean) m.enabled = ((Boolean) val).booleanValue();
        save();
    }
    public void toggleModule(HudModule m) { m.enabled = !m.enabled; if (m.optId != null) options.put(m.optId, new JsonPrimitive(Boolean.valueOf(m.enabled))); save(); }
    public double getZoomFactor() { return Math.max(1.5, getDouble("cam_zoom_fac", 4.0)); }
    public void resetLayout() { Map<String, JsonElement> kept = new LinkedHashMap<String, JsonElement>(options); initDefaults(); options.putAll(kept); save(); }
    public void resetAll() { options.clear(); waypoints.clear(); resetTitleLayout(); initDefaults(); save(); }
}