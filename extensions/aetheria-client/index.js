'use strict';
const fs = require('fs');
const path = require('path');
const net = require('net');

// Optional: put your GitHub Raw URL to index.js here (or paste it inside the Client tab)
const DEFAULT_GITHUB_RAW_URL = '';

const CLIENT_VERSIONS = [
  '26.1.3', '26.1.2', '26.1.1', '26.1',
  '1.21.5', '1.21.4', '1.21.3', '1.21.1', '1.21',
  '1.20.6', '1.20.4', '1.20.1', '1.19.4', '1.19.2',
  '1.18.2', '1.17.1', '1.16.5', '1.12.2', '1.8.9'
];

const CORE_HYBRID_MODS = [
  { id: 'P7dR8mda', name: 'Fabric API', desc: 'Lightweight Fabric hooks' },
  { id: 'AANobbMI', name: 'Sodium', desc: 'High-FPS rendering engine' },
  { id: 'gvQqBUqZ', name: 'Lithium', desc: 'Physics & tick optimization' },
  { id: 'uXXizFIs', name: 'FerriteCore', desc: 'Memory footprint reduction' },
  { id: '5ZwdcRci', name: 'ImmediatelyFast', desc: 'Immediate-mode HUD speedup' },
  { id: 'YL57xq9U', name: 'Iris Shaders', desc: 'Shader pipeline for Sodium' }
];

const DEFAULT_SERVERS = [
  { name: 'Hypixel Network', ip: 'mc.hypixel.net', note: 'Minigames / BedWars' },
  { name: 'Minemen Club', ip: 'na.minemen.club', note: 'Practice PvP' },
  { name: 'MCC Island', ip: 'play.mccisland.net', note: 'Modern 1.20+ Minigames' },
  { name: '2b2t', ip: '2b2t.org', note: 'Anarchy Survival' },
  { name: 'DonutSMP', ip: 'donutsmp.net', note: 'Hardcore SMP' }
];

const CLIENT_PRESETS = {
  pvp: {
    id: 'pvp', name: 'PvP Competitive', desc: '260 FPS cap, Fullbright, Low Fire, No HurtCam, Raw Mouse',
    ov: { fps_cap: 260, fps_vsync: false, fps_entity_shadows: false, fps_particles: 'minimal', dsp_gamma: 15, dsp_fov: 95, cam_hurt_shake: 0, cam_fov_fx: 0.25, pvp_low_fire: true, pvp_keystrokes: true, pvp_cps: true, pvp_togglesprint: true, pvp_armor_hud: true, pvp_ping_hud: true, ctl_raw_input: true, ctl_auto_jump: false }
  },
  fps: {
    id: 'fps', name: 'Max ARM64 FPS', desc: 'Snapdragon Oryon culling, G1GC, low particles & fast clouds',
    ov: { fps_cap: 260, fps_vsync: false, fps_render_dist: 8, fps_sim_dist: 6, fps_clouds: 'off', fps_entity_shadows: false, fps_particles: 'minimal', fps_ao: false, fps_mipmaps: 0, fps_oryon_pretouch: true, fps_string_dedup: true, vis_animations: false, vis_vignette: false, dsp_entity_dist: 75 }
  },
  visuals: {
    id: 'visuals', name: 'Visuals & Shaders', desc: 'Fancy graphics, smooth lighting, dynamic clouds & 3D skins',
    ov: { fps_clouds: 'fancy', fps_particles: 'all', fps_ao: true, fps_entity_shadows: true, fps_mipmaps: 4, fps_render_dist: 16, dsp_graphics: 'fancy', vis_leaves_fancy: true, vis_sky_grad: true, vis_water_clr: true, vis_vignette: true, cam_bobbing: true }
  },
  smp: {
    id: 'smp', name: 'Survival & SMP', desc: '12-chunk view, coordinates, saturation, durability & subtitles',
    ov: { fps_render_dist: 12, fps_sim_dist: 10, hud_coords: true, hud_biome: true, hud_durability: true, hud_saturation: true, hud_day_count: true, aud_subtitles: true, ctl_auto_jump: false, chat_timestamps: true }
  },
  vanilla_plus: {
    id: 'vanilla_plus', name: 'Clean Vanilla+', desc: 'Minimal HUD clutter with zoom, raw mouse & clean defaults',
    ov: { fps_render_dist: 12, fps_vsync: true, dsp_gamma: 1, cam_zoom_key: 'C', pvp_keystrokes: false, pvp_cps: false, hud_fps: true, ctl_raw_input: true }
  }
};

// 10 Categories x 25 Options = 250 Homemade Options
// [id, label, type('b'|'r'|'s'), default, extra, desc, mcKey?]
const RAW_CATS = [
  ['FPS & Oryon', [
    ['fps_cap', 'Max Framerate', 'r', 165, [30, 260, 5], 'Target FPS limit (260=Unlimited)', 'maxFps'],
    ['fps_vsync', 'VSync', 'b', false, 0, 'Sync frames to display refresh', 'enableVsync'],
    ['fps_render_dist', 'Render Distance', 'r', 12, [2, 32, 1], 'Loaded chunk radius', 'renderDistance'],
    ['fps_sim_dist', 'Simulation Distance', 'r', 8, [5, 24, 1], 'Active tick simulation radius', 'simulationDistance'],
    ['fps_entity_dist', 'Entity Distance %', 'r', 100, [50, 500, 25], 'Entity render scaling', 'entityDistanceScaling'],
    ['fps_ao', 'Smooth Lighting', 'b', true, 0, 'Ambient occlusion across blocks', 'ao'],
    ['fps_biome_blend', 'Biome Blend', 'r', 2, [0, 7, 1], 'Color transition radius', 'biomeBlendRadius'],
    ['fps_mipmaps', 'Mipmap Levels', 'r', 4, [0, 4, 1], 'Texture downsampling smoothness', 'mipmapLevels'],
    ['fps_clouds', 'Cloud Quality', 's', 'fast', ['off', 'fast', 'fancy'], 'Sky cloud complexity', 'renderClouds'],
    ['fps_particles', 'Particle Density', 's', 'decreased', ['all', 'decreased', 'minimal'], 'Global world particle budget', 'particles'],
    ['fps_entity_shadows', 'Entity Shadows', 'b', true, 0, 'Shadows under mobs & players', 'entityShadows'],
    ['fps_chunk_fade', 'Chunk Fade Time', 'r', 0.75, [0, 2, 0.25], 'Smooth chunk pop-in fade', 'chunkSectionFadeInTime'],
    ['fps_gc_mode', 'ARM64 GC Engine', 's', 'G1GC', ['G1GC', 'ZGC', 'GenerationalZGC'], 'JVM collector for Oryon cores'],
    ['fps_oryon_pretouch', 'AlwaysPreTouch Heap', 'b', true, 0, 'Pre-fault RAM pages at startup'],
    ['fps_string_dedup', 'String Deduplication', 'b', true, 0, 'Reduces duplicate Java string RAM'],
    ['fps_gc_pause', 'Max GC Pause (ms)', 'r', 20, [5, 100, 5], 'G1GC pause target budget'],
    ['fps_parallel_ref', 'Parallel Ref Cleanup', 'b', true, 0, 'Multi-core JVM reference processing'],
    ['fps_netty_threads', 'Netty IO Boost', 'b', true, 0, 'Optimizes Netty network buffers'],
    ['fps_unfocused_fps', 'Background Throttle', 'b', true, 0, 'Lowers GPU load when minimized'],
    ['fps_sodium_leaves', 'Smart Leaf Culling', 'b', true, 0, 'Skips hidden interior leaf faces'],
    ['fps_sodium_fog', 'Sodium Fog Occlusion', 'b', true, 0, 'Skips chunks hidden behind fog'],
    ['fps_sodium_threads', 'Mesher Threads (0=Auto)', 'r', 0, [0, 12, 1], 'Worker threads for chunk meshing'],
    ['fps_fast_math', 'Oryon SIMD Fast Math', 'b', true, 0, 'Enables fast JVM math intrinsics'],
    ['fps_mem_guard', 'Low-Mem Spike Guard', 'b', true, 0, 'Caps young-gen allocation spikes'],
    ['fps_compact_hdr', 'Compact Headers (JDK25)', 'b', false, 0, 'Experimental compact object headers']
  ]],
  ['Display & Render', [
    ['dsp_graphics', 'Graphics Mode', 's', 'fancy', ['fast', 'fancy', 'fabulous'], 'Master Minecraft render preset', 'graphicsMode'],
    ['dsp_gui_scale', 'GUI Scale (0=Auto)', 'r', 0, [0, 6, 1], 'Interface scale factor', 'guiScale'],
    ['dsp_gamma', 'Brightness / Fullbright', 'r', 1.0, [0, 15, 0.5], '1.0=Vanilla, >1.0=Fullbright', 'gamma'],
    ['dsp_fov', 'Field of View', 'r', 85, [30, 110, 1], 'Base camera FOV in degrees', 'fov'],
    ['dsp_fullscreen', 'Start Fullscreen', 'b', false, 0, 'Boot directly into fullscreen', 'fullscreen'],
    ['dsp_custom_res', 'Custom Resolution', 'b', false, 0, 'Override launch width & height'],
    ['dsp_width', 'Window Width (px)', 'r', 1280, [800, 3840, 10], 'Custom startup width'],
    ['dsp_height', 'Window Height (px)', 'r', 720, [600, 2160, 10], 'Custom startup height'],
    ['dsp_attack_ind', 'Attack Indicator', 's', 'crosshair', ['off', 'crosshair', 'hotbar'], 'Melee cooldown indicator', 'attackIndicator'],
    ['dsp_glint_speed', 'Glint Speed', 'r', 0.5, [0, 1, 0.1], 'Enchantment shimmer speed', 'glintSpeed'],
    ['dsp_glint_str', 'Glint Strength', 'r', 0.75, [0, 1, 0.05], 'Enchantment shimmer brightness', 'glintStrength'],
    ['dsp_darkness', 'Darkness Pulse Scale', 'r', 1.0, [0, 1, 0.1], 'Warden darkness pulse intensity', 'darknessEffectScale'],
    ['dsp_high_contrast', 'High Contrast UI', 'b', false, 0, 'Boosts UI border & text contrast', 'highContrast'],
    ['dsp_dark_splash', 'Dark Loading Screen', 'b', true, 0, 'OLED dark Mojang splash screen', 'darkMojangStudiosBackground'],
    ['dsp_no_lightning', 'Hide Lightning Flash', 'b', false, 0, 'Disables bright sky strobing', 'hideLightningFlashes'],
    ['dsp_no_telemetry', 'Minimal Telemetry', 'b', true, 0, 'Minimizes diagnostic overhead', 'telemetryOptInExtra'],
    ['dsp_menu_blur', 'Menu Blur Intensity', 'r', 5, [0, 10, 1], 'Gaussian blur behind pause menus', 'menuBackgroundBlurriness'],
    ['dsp_text_bg_op', 'Text Backdrop Opacity', 'r', 0.5, [0, 1, 0.05], 'Opacity behind HUD text', 'textBackgroundOpacity'],
    ['dsp_bg_all', 'Global Text Backdrop', 'b', false, 0, 'Show backdrop behind scoreboard too', 'backgroundForChatOnly'],
    ['dsp_uni_font', 'Force Unicode Font', 'b', false, 0, 'Thin crisp Unicode typeface', 'forceUnicodeFont'],
    ['dsp_lang', 'Client Language', 's', 'en_us', ['en_us', 'en_gb', 'es_es', 'fr_fr', 'de_de', 'ja_jp', 'zh_cn'], 'Active game locale', 'lang'],
    ['dsp_skip_narr', 'Skip Narrator Prompt', 'b', true, 0, 'Disables first-run narrator prompt', 'onboardAccessibility'],
    ['dsp_skip_mp', 'Skip MP Warning', 'b', true, 0, 'Jump straight to server list', 'skipMultiplayerWarning'],
    ['dsp_no_realms', 'Hide Realm Invites', 'b', true, 0, 'Removes menu realm notifications', 'realmsNotifications'],
    ['dsp_skip_tut', 'Skip Tutorial Hints', 'b', true, 0, 'Marks vanilla tutorial complete', 'tutorialStep']
  ]],
  ['PvP & Combat', [
    ['pvp_keystrokes', 'Keystrokes HUD', 'b', true, 0, 'WASD, Space & LMB/RMB overlay'],
    ['pvp_key_chroma', 'Keystrokes Chroma', 'b', false, 0, 'Cycling RGB accent on keystrokes'],
    ['pvp_cps', 'CPS Counter', 'b', true, 0, 'Live Left/Right clicks per sec'],
    ['pvp_cps_warn', 'CPS Spike Highlight', 'b', false, 0, 'Accent glow above 15 CPS'],
    ['pvp_togglesprint', 'ToggleSprint', 'b', true, 0, 'Persistent sprint status module'],
    ['pvp_togglesneak', 'ToggleSneak', 'b', false, 0, 'Persistent crouch toggle module'],
    ['pvp_armor_hud', 'Armor Status HUD', 'b', true, 0, 'Shows equipped armor durability'],
    ['pvp_potion_hud', 'Potion Effects HUD', 'b', true, 0, 'Compact active potion timers'],
    ['pvp_ping_hud', 'Live Ping HUD', 'b', true, 0, 'Shows server latency in ms'],
    ['pvp_reach_hud', 'Reach Indicator', 'b', false, 0, 'Last hit distance in blocks'],
    ['pvp_combo_hud', 'Combo Counter', 'b', false, 0, 'Tracks consecutive melee hits'],
    ['pvp_low_fire', 'Low Fire Overlay', 'b', true, 0, 'Lowers first-person fire flames'],
    ['pvp_hit_color', 'Custom Hit Flash', 'b', true, 0, 'Lunar-style entity damage tint'],
    ['pvp_no_tilt', 'Zero Hurt Tilt', 'b', false, 0, 'Removes camera roll on damage'],
    ['pvp_outline', 'Custom Block Outline', 'b', true, 0, 'Accented targeted-block wireframe'],
    ['pvp_xhair_style', 'Crosshair Shape', 's', 'classic', ['vanilla', 'classic', 'dot', 'circle', 't_shape'], 'Lunar crosshair preset'],
    ['pvp_xhair_size', 'Crosshair Size', 'r', 6, [2, 16, 1], 'Crosshair arm length (px)'],
    ['pvp_xhair_gap', 'Crosshair Gap', 'r', 2, [0, 8, 1], 'Center spacing in pixels'],
    ['pvp_xhair_dyn', 'Dynamic Crosshair', 'b', false, 0, 'Expands crosshair on swing'],
    ['pvp_item_phys', 'Flat Item Drops', 'b', false, 0, 'Dropped items rest flat'],
    ['pvp_totem_pop', 'Totem Pop Counter', 'b', true, 0, 'Counts crystal PvP totem pops'],
    ['pvp_pearl_cd', 'Pearl Cooldown Ring', 'b', true, 0, 'Visual ender pearl cooldown'],
    ['pvp_shield_ind', 'Shield Stun Alert', 'b', true, 0, 'Alert when shield is disabled'],
    ['pvp_tag_bg', 'Clean Nametag Plate', 'b', true, 0, 'High-contrast nameplate backdrop'],
    ['pvp_self_tag', 'Own Nametag in F5', 'b', true, 0, 'Renders own tag in third-person']
  ]],
  ['HUD & Overlays', [
    ['hud_fps', 'FPS Counter', 'b', true, 0, 'Displays live framerate pill'],
    ['hud_coords', 'Coordinates HUD', 'b', true, 0, 'X / Y / Z and facing direction'],
    ['hud_biome', 'Biome Display', 'b', true, 0, 'Shows current biome name'],
    ['hud_compass', 'Compass Strip', 'b', true, 0, 'Top-center N/E/S/W bar'],
    ['hud_clock', 'System Clock HUD', 'b', false, 0, 'Local time overlay in-game'],
    ['hud_session', 'Session Timer HUD', 'b', true, 0, 'Elapsed current session clock'],
    ['hud_srv_ip', 'Server IP HUD', 'b', false, 0, 'Displays active server IP'],
    ['hud_durability', 'Tool Durability HUD', 'b', true, 0, 'Exact uses left on held gear'],
    ['hud_saturation', 'Saturation Overlay', 'b', true, 0, 'Shows hidden food saturation'],
    ['hud_day_count', 'World Day Counter', 'b', false, 0, 'Displays current Minecraft day'],
    ['hud_mem_pill', 'JVM Memory Pill', 'b', false, 0, 'Shows live heap MB used'],
    ['hud_pack_name', 'Resource Pack HUD', 'b', false, 0, 'Shows active texture pack'],
    ['hud_bossbar', 'Compact Boss Bar', 'b', true, 0, 'Slims wide boss health bars'],
    ['hud_sb_nums', 'Hide Scoreboard #s', 'b', true, 0, 'Removes red sidebar numbers'],
    ['hud_sb_scale', 'Scoreboard Scale %', 'r', 100, [50, 140, 5], 'Sidebar scoreboard sizing'],
    ['hud_hotbar_clr', 'Translucent Hotbar', 'b', false, 0, 'Frosted glass hotbar plate'],
    ['hud_arrows', 'Arrow Counter', 'b', true, 0, 'Shows total arrows near crosshair'],
    ['hud_elytra', 'Elytra Telemetry', 'b', true, 0, 'Blocks/sec & Y-level while gliding'],
    ['hud_chunks', 'Clean Chunk Grid', 'b', false, 0, 'Minimal F3+G chunk border style'],
    ['hud_light_lvl', 'Spawn Light Overlay', 'b', false, 0, 'Marks mob-spawnable dark blocks'],
    ['hud_snap_grid', 'HUD Editor Snap Grid', 'b', true, 0, 'Snaps HUD widgets cleanly'],
    ['hud_shadow', 'HUD Text Drop Shadow', 'b', true, 0, 'Crisp shadow behind HUD labels'],
    ['hud_scale', 'Global HUD Scale %', 'r', 100, [50, 150, 5], 'Scales all HUD modules uniformly'],
    ['hud_min_f3', 'Clean Minimal F3', 'b', true, 0, 'Strips clutter from F3 screen'],
    ['hud_toasts', 'Dark Toast Pills', 'b', true, 0, 'Sleek achievement notifications']
  ]],
  ['Camera & Zoom', [
    ['cam_zoom', 'Cinematic Zoom', 'b', true, 0, 'Smooth OptiFine/Lunar zoom'],
    ['cam_zoom_fac', 'Zoom Magnification', 'r', 4, [2, 12, 1], 'Optical zoom multiplier'],
    ['cam_zoom_ease', 'Smooth Zoom Curve', 'b', true, 0, 'Animated zoom-in/out easing'],
    ['cam_zoom_scr', 'Scroll Zoom Adjust', 'b', true, 0, 'Change zoom level with wheel'],
    ['cam_zoom_slow', 'Slow Mouse on Zoom', 'b', true, 0, 'Smooth tracking while zoomed'],
    ['cam_zoom_key', 'Zoom Keybind', 's', 'C', ['C', 'V', 'Z', 'X', 'ALT'], 'Keyboard trigger for zoom'],
    ['cam_freelook', '360° Freelook Orbit', 'b', true, 0, 'Orbit camera without turning'],
    ['cam_fl_key', 'Freelook Keybind', 's', 'ALT', ['ALT', 'V', 'G', 'R', 'TAB'], 'Hold key for 360 orbit'],
    ['cam_bobbing', 'View Bobbing', 'b', true, 0, 'Walking head-bob motion', 'bobView'],
    ['cam_fov_fx', 'Dynamic FOV Effects', 'r', 0.5, [0, 1, 0.05], 'Sprint/speed FOV warp scale', 'fovEffectScale'],
    ['cam_scr_fx', 'Portal/Nausea Warp', 'r', 0.5, [0, 1, 0.05], 'Screen distortion intensity', 'screenEffectScale'],
    ['cam_hurt_shake', 'Damage Tilt Scale', 'r', 0.5, [0, 1, 0.05], 'Camera flinch on hit', 'damageTiltStrength'],
    ['cam_f5_clip', 'No F5 Wall Clip', 'b', false, 0, 'Ignore wall collision in F5'],
    ['cam_f5_dist', 'F5 Camera Distance', 'r', 4.0, [2, 8, 0.5], 'Third-person camera distance'],
    ['cam_snaplook', 'Quick Rear Snaplook', 'b', false, 0, 'Instant peek behind keybind'],
    ['cam_no_pump', 'Clear Pumpkin Blur', 'b', true, 0, 'Removes carved pumpkin overlay'],
    ['cam_no_spy', 'Minimal Spyglass', 'b', false, 0, 'Clean border in spyglass'],
    ['cam_water_fov', 'Underwater FOV Fix', 'b', true, 0, 'Prevents harsh water FOV shift'],
    ['cam_elytra_roll', 'Elytra Bank Roll', 'b', true, 0, 'Subtle flight banking tilt'],
    ['cam_cine_inertia', 'Cinematic Inertia', 'r', 5, [1, 10, 1], 'Smoothing for cinematic pans'],
    ['cam_hand_sway', 'First-Person Sway', 'b', true, 0, 'Subtle hand inertia on turn'],
    ['cam_f1_xhair', 'Keep Crosshair in F1', 'b', false, 0, 'Optional minimal F1 crosshair'],
    ['cam_death_pan', 'Smooth Death Pan', 'b', true, 0, 'Gentle orbit on respawn screen'],
    ['cam_spec_spd', 'Spectator Fly Speed', 'r', 1.0, [0.5, 3, 0.25], 'Spectator scroll speed factor'],
    ['cam_fly_fov', 'Creative Flight FOV', 'b', true, 0, 'Slight FOV boost when flying']
  ]],
  ['Visuals & Shaders', [
    ['vis_shaders_enable', 'Iris Shader Pipeline', 'b', true, 0, 'Writes iris.properties state'],
    ['vis_shader_pack', 'Preferred Shaderpack', 's', 'none', ['none', 'ComplementaryReimagined', 'BSL', 'SildursVibrant', 'MakeUpUltraFast'], 'Target Iris shader profile'],
    ['vis_dyn_lights', 'Dynamic Hand Lights', 'b', true, 0, 'Held torches light surroundings'],
    ['vis_3d_skins', '3D Skin Layers', 'b', true, 0, 'Extruded hats, jackets & sleeves'],
    ['vis_wave_capes', 'Wavey Physics Capes', 'b', true, 0, 'Segmented fluid cloak physics'],
    ['vis_animations', '1.7 Old Animations', 'b', true, 0, 'Classic block-hit & rod visuals'],
    ['vis_leaves_fancy', 'Transparent Leaves', 'b', true, 0, 'See-through fancy leaf blocks'],
    ['vis_water_clr', 'Clear Water Sight', 'b', true, 0, 'Reduces murky underwater fog'],
    ['vis_lava_clr', 'Clear Lava Fog', 'b', true, 0, 'Clearer lava with Fire Resistance'],
    ['vis_vignette', 'Cinema Edge Vignette', 'b', false, 0, 'Soft corner darkening'],
    ['vis_sky_grad', 'Rich Sunset Sky', 'b', true, 0, 'Enhanced horizon gradients'],
    ['vis_stars', 'Crisp Night Stars', 'b', true, 0, 'Boosted night sky star contrast'],
    ['vis_weather_alpha', 'Rain Opacity %', 'r', 75, [10, 100, 5], 'Softens heavy rain streaks'],
    ['vis_portal_clr', 'Clean Portal Swirl', 'b', true, 0, 'Lower purple swirl opacity'],
    ['vis_shiny_pots', 'Classic Potion Glint', 'b', true, 0, 'Restores glint on potion bottles'],
    ['vis_conn_glass', 'Connected Glass', 'b', true, 0, 'Seamless glass block panes'],
    ['vis_emissive_ore', 'Emissive Ores', 'b', false, 0, 'Subtle ore vein glow in caves'],
    ['vis_hit_sparks', 'Sharpness Sparks', 'b', true, 0, 'Crisp impact particle bursts'],
    ['vis_crit_scale', 'Crit Particle Scale %', 'r', 100, [0, 200, 10], 'Adjusts crit particle sizing'],
    ['vis_item_bob', 'Smooth Item Bob', 'b', true, 0, 'Silky dropped item motion'],
    ['vis_beacon_alpha', 'Beacon Opacity %', 'r', 85, [20, 100, 5], 'Beacon beam translucency'],
    ['vis_snow_fog', 'Powder Snow Fog %', 'r', 50, [10, 100, 10], 'Softens frost overlay'],
    ['vis_barriers', 'Visible Barriers', 'b', false, 0, 'Show barrier icons in creative'],
    ['vis_time_lock', 'Client Time Override', 's', 'server', ['server', 'day', 'sunset', 'midnight'], 'Visual-only sky time'],
    ['vis_wx_lock', 'Client Weather Lock', 's', 'server', ['server', 'clear', 'rain', 'thunder'], 'Visual-only weather']
  ]],
  ['Controls & Keys', [
    ['ctl_raw_input', 'Raw Mouse Input', 'b', true, 0, 'Bypass Windows pointer accel', 'rawMouseInput'],
    ['ctl_sens', 'Mouse Sensitivity', 'r', 0.5, [0.05, 1, 0.05], 'Look sensitivity (0.5=100%)', 'mouseSensitivity'],
    ['ctl_wheel', 'Scroll Sensitivity', 'r', 1.0, [0.5, 3, 0.25], 'Hotbar scroll speed', 'mouseWheelSensitivity'],
    ['ctl_disc_scroll', 'Discrete Scroll', 'b', false, 0, '1 slot per scroll notch', 'discrete_mouse_scroll'],
    ['ctl_invert_y', 'Invert Y-Axis', 'b', false, 0, 'Flip vertical mouse pitch', 'invertYMouse'],
    ['ctl_auto_jump', 'Auto-Jump', 'b', false, 0, 'Auto hop 1-block ledges', 'autoJump'],
    ['ctl_tog_crouch', 'Vanilla Toggle Crouch', 'b', false, 0, 'Tap to crouch', 'toggleCrouch'],
    ['ctl_tog_sprint', 'Vanilla Toggle Sprint', 'b', false, 0, 'Tap to sprint', 'toggleSprint'],
    ['ctl_touch', 'Touchscreen Mode', 'b', false, 0, 'Windows ARM64 tablet touch', 'touchscreen'],
    ['ctl_pause_bg', 'Pause on Alt-Tab', 'b', false, 0, 'Pause when window unfocused', 'pauseOnLostFocus'],
    ['ctl_adv_tips', 'Advanced Tooltips', 'b', true, 0, 'Item IDs & durability (F3+H)', 'advancedItemTooltips'],
    ['ctl_held_tips', 'Hotbar Swap Tooltip', 'b', true, 0, 'Item name above hotbar', 'heldItemTooltips'],
    ['ctl_op_tab', 'Operator Items Tab', 'b', true, 0, 'Command blocks in creative', 'operatorItemsTab'],
    ['ctl_key_sprint', 'Sprint Keybind', 's', 'key.keyboard.left.control', ['key.keyboard.left.control', 'key.keyboard.r', 'key.keyboard.left.shift'], 'Primary sprint key', 'key_key.sprint'],
    ['ctl_key_sneak', 'Sneak Keybind', 's', 'key.keyboard.left.shift', ['key.keyboard.left.shift', 'key.keyboard.left.control', 'key.keyboard.c'], 'Primary crouch key', 'key_key.sneak'],
    ['ctl_key_swap', 'Offhand Swap Key', 's', 'key.keyboard.f', ['key.keyboard.f', 'key.keyboard.g', 'key.keyboard.v'], 'Swap offhand key', 'key_key.swapOffhand'],
    ['ctl_key_drop', 'Drop Item Key', 's', 'key.keyboard.q', ['key.keyboard.q', 'key.keyboard.g', 'key.keyboard.backspace'], 'Drop item key', 'key_key.drop'],
    ['ctl_key_f5', 'Perspective Key', 's', 'key.keyboard.f5', ['key.keyboard.f5', 'key.keyboard.r', 'key.keyboard.v'], 'F5 view toggle key', 'key_key.togglePerspective'],
    ['ctl_drop_guard', 'Safe Tool Drop Guard', 'b', true, 0, 'Double-tap protection for gear'],
    ['ctl_drag_stack', 'Drag-Stack Helper', 'b', true, 0, 'Smooth inventory drag splitting'],
    ['ctl_mid_sort', 'Middle-Click Sort', 'b', true, 0, '1-click chest sorting hint'],
    ['ctl_menu_key', 'Client Overlay Key', 's', 'RSHIFT', ['RSHIFT', 'M', 'H', 'INSERT'], 'In-game Lunar menu key'],
    ['ctl_clip_ss', 'Copy F2 to Clipboard', 'b', true, 0, 'Auto-copy screenshots'],
    ['ctl_fast_alt', 'Instant Alt-Tab', 'b', true, 0, 'Borderless flicker prevention'],
    ['ctl_gamepad', 'ARM64 Gamepad Poll', 'b', false, 0, 'Controller pass-through support']
  ]],
  ['Chat & Social', [
    ['chat_vis', 'Chat Visibility', 's', '0', ['0', '1', '2'], '0=All, 1=Commands, 2=Hidden', 'chatVisibility'],
    ['chat_opacity', 'Chat Text Opacity', 'r', 1.0, [0.1, 1, 0.1], 'Message transparency', 'chatOpacity'],
    ['chat_scale', 'Chat Font Scale', 'r', 1.0, [0.3, 1, 0.05], 'Size of chat messages', 'chatScale'],
    ['chat_width', 'Chat Box Width', 'r', 1.0, [0.2, 1, 0.05], 'Horizontal chat width', 'chatWidth'],
    ['chat_h_open', 'Open Chat Height', 'r', 1.0, [0.2, 1, 0.05], 'Height when typing', 'chatHeightFocused'],
    ['chat_h_idle', 'Idle Chat Height', 'r', 0.45, [0.1, 1, 0.05], 'Height during gameplay', 'chatHeightUnfocused'],
    ['chat_spacing', 'Line Spacing', 'r', 0.0, [0, 1, 0.1], 'Vertical message padding', 'chatLineSpacing'],
    ['chat_delay', 'Message Delay (s)', 'r', 0.0, [0, 6, 0.5], 'Display delay in seconds', 'chatDelay'],
    ['chat_colors', 'Colored Chat Codes', 'b', true, 0, 'Render & and section colors', 'chatColors'],
    ['chat_links', 'Clickable Links', 'b', true, 0, 'Allow clicking chat URLs', 'chatLinks'],
    ['chat_prompt', 'Link Safety Prompt', 'b', true, 0, 'Confirm before opening URLs', 'chatLinksPrompt'],
    ['chat_suggest', 'Command Auto-Suggest', 'b', true, 0, 'Tab completion popup', 'autoSuggestions'],
    ['chat_timestamps', 'Chat Timestamps', 'b', true, 0, 'Prepend [HH:MM] to lines'],
    ['chat_stack', 'Stack Spam [x2]', 'b', true, 0, 'Collapse duplicate lines'],
    ['chat_heads', 'Chat Skin Heads', 'b', true, 0, 'Player faces beside messages'],
    ['chat_scroll', '16k Line Scrollback', 'b', true, 0, 'Extended chat history buffer'],
    ['chat_mention', 'Mention Sound Ping', 'b', true, 0, 'Chime when your name appears'],
    ['chat_no_rep', 'Hide Report Banner', 'b', true, 0, 'Suppresses unverified toast', 'onlyShowSecureChat'],
    ['chat_filter', 'Strict Word Filter', 'b', false, 0, 'Mask common profanity'],
    ['chat_autogg', 'Auto-GG on Match End', 'b', false, 0, 'Send gg automatically'],
    ['chat_friend_toast', 'Friend Join Toasts', 'b', true, 0, 'Notify when roster friends join'],
    ['chat_dm_ping', 'Private Message Chime', 'b', true, 0, 'Distinct ding on /msg'],
    ['chat_keep_hist', 'Keep Chat Across Servers', 'b', false, 0, 'Preserve chat on reconnect'],
    ['chat_copy', 'Alt-Click Copy Line', 'b', true, 0, 'Copy any chat line cleanly'],
    ['chat_streamer', 'Streamer Privacy Mode', 'b', false, 0, 'Masks server IPs & DMs']
  ]],
  ['Audio & Mixer', [
    ['aud_master', 'Master Volume', 'r', 0.8, [0, 1, 0.05], 'Global game volume', 'soundCategory_master'],
    ['aud_music', 'Music Volume', 'r', 0.25, [0, 1, 0.05], 'Background soundtrack', 'soundCategory_music'],
    ['aud_records', 'Jukebox & Note Blocks', 'r', 0.7, [0, 1, 0.05], 'Music discs volume', 'soundCategory_records'],
    ['aud_weather', 'Weather Volume', 'r', 0.4, [0, 1, 0.05], 'Rain & thunder volume', 'soundCategory_weather'],
    ['aud_blocks', 'Blocks Volume', 'r', 0.8, [0, 1, 0.05], 'Footsteps & block sounds', 'soundCategory_blocks'],
    ['aud_hostile', 'Hostile Mobs', 'r', 0.85, [0, 1, 0.05], 'Creepers & threat cues', 'soundCategory_hostile'],
    ['aud_neutral', 'Friendly Mobs', 'r', 0.65, [0, 1, 0.05], 'Passive mob volume', 'soundCategory_neutral'],
    ['aud_players', 'Players & Combat', 'r', 0.9, [0, 1, 0.05], 'Swings, hits & bows', 'soundCategory_players'],
    ['aud_ambient', 'Cave Ambience', 'r', 0.5, [0, 1, 0.05], 'Environmental soundscapes', 'soundCategory_ambient'],
    ['aud_voice', 'Voice Channel', 'r', 0.8, [0, 1, 0.05], 'In-game voice volume', 'soundCategory_voice'],
    ['aud_subtitles', 'Show Subtitles', 'b', false, 0, 'Directional captions', 'showSubtitles'],
    ['aud_hrtf', 'Spatial HRTF Audio', 'b', true, 0, 'OpenAL 3D headphone staging', 'directionalAudio'],
    ['aud_freq', 'Music Frequency', 's', 'default', ['default', 'frequent', 'constant'], 'Gap between music tracks', 'musicFrequency'],
    ['aud_mute_bg', 'Mute When Minimized', 'b', false, 0, 'Silence game while Alt-Tabbed'],
    ['aud_quiet_portal', 'Soften Portal Hum', 'b', true, 0, 'Lowers Nether portal noise'],
    ['aud_quiet_elytra', 'Soften Elytra Wind', 'b', true, 0, 'Reduces high-speed wind rush'],
    ['aud_quiet_anvil', 'Soften Anvil & Pistons', 'b', false, 0, 'Tones down redstone clank'],
    ['aud_bow_pitch', 'Bow Hit Ding Pitch', 'r', 1.0, [0.5, 2, 0.1], 'Ranged hit ding tuning'],
    ['aud_xp_soft', 'Smooth XP Chimes', 'b', true, 0, 'Prevents loud XP orb stacking'],
    ['aud_ui_clicks', 'Client UI Sounds', 'b', true, 0, 'Subtle click feedback in tab'],
    ['aud_ready_chime', 'Boot Ready Chime', 'b', false, 0, 'Ping when MC finishes loading'],
    ['aud_crash_tone', 'Crash Alert Tone', 'b', true, 0, 'Alert tone on unexpected exit'],
    ['aud_ptt_hint', 'Mic PTT Indicator', 'b', true, 0, 'Shows mic active badge'],
    ['aud_rain_roof', 'Muffle Indoor Rain', 'b', true, 0, 'Softens rain under roofs'],
    ['aud_mono_toggle', 'Mono Accessibility Mix', 'b', false, 0, 'Sums stereo channels to mono']
  ]],
  ['Cosmetics & Client', [
    ['cos_cape_enable', 'Enable Aetheria Cloak', 'b', true, 0, 'Activates selected cloak theme'],
    ['cos_cape_style', 'Cloak Design', 's', 'Obsidian Star', ['Obsidian Star', 'Lunar Crescent', 'Snapdragon Teal', 'Amethyst Void', 'Crimson Phoenix'], 'Active cloak cosmetic'],
    ['cos_wings', 'Back Wings Cosmetic', 'b', false, 0, 'Geometric back wings'],
    ['cos_wings_style', 'Wings Variant', 's', 'Dragon Obsidian', ['Dragon Obsidian', 'Angel Frost', 'Cyber Cyan', 'Solar Gold'], 'Wing model style'],
    ['cos_halo', 'Overhead Halo', 'b', false, 0, 'Floating minimal ring'],
    ['cos_bandana', 'Aetheria Bandanna', 'b', false, 0, 'Head cosmetic wrap'],
    ['cos_badge', 'Tab Name Star Badge', 'b', true, 0, 'Star badge beside your name'],
    ['cos_badge_col', 'Badge Color', 's', 'Accent', ['Accent', 'Emerald', 'Cyan', 'Gold', 'Violet'], 'Badge accent hue'],
    ['cos_emotes', 'Emote Wheel (B)', 'b', true, 0, 'Wave, Salute & Victory emotes'],
    ['cos_slim', 'Prefer Slim (3px) Arms', 'b', false, 0, 'Default Alex arm model'],
    ['cos_rpc', 'Session Playtime Tracker', 'b', true, 0, 'Logs playtime per MC version'],
    ['cos_auto_ss', 'Auto-Index Captures', 'b', true, 0, 'Indexes F2 shots to Vault'],
    ['cos_pt_goal', 'Break Reminder (min)', 'r', 120, [0, 480, 15], 'Optional session reminder (0=off)'],
    ['cos_auto_reconn', 'Auto-Reconnect Hint', 'b', false, 0, 'Retries dropped connections'],
    ['cos_hide_launch', 'Minimize on Launch', 'b', false, 0, 'Hides launcher while playing'],
    ['cos_backup_worlds', 'Pre-Launch Options Bak', 'b', true, 0, 'Backs up options.txt on boot'],
    ['cos_hybrid_mods', 'Hybrid Profile JARs', 'b', true, 0, 'Runs local /mods alongside client'],
    ['cos_arm64_gpu', 'Force High-Perf GPU', 'b', true, 0, 'Requests max Adreno GPU perf'],
    ['cos_log_prune', 'Auto-Prune Old Logs', 'b', true, 0, 'Keeps profile logs clean'],
    ['cos_auto_ping', 'Auto-Ping Servers', 'b', true, 0, 'Refreshes server status on open'],
    ['cos_friend_heads', 'Load Friend Avatars', 'b', true, 0, 'Fetches skin heads in roster'],
    ['cos_compact_w', 'Compact Widget Text', 'b', false, 0, 'Streamlines Overview widget'],
    ['cos_sync_ver', 'Carry Options Across Ver', 'b', true, 0, 'Keeps 250 options across 1.8.9-26.1.3'],
    ['cos_confirm_stop', 'Confirm Force-Stop', 'b', true, 0, 'Ask before killing game PID'],
    ['cos_dev_overlay', 'Log Synced Args to Console', 'b', false, 0, 'Prints launch sync summary']
  ]]
];

const OPTIONS_CATALOG = [];
const DEFAULT_OPTIONS_MAP = {};
for (const [cat, items] of RAW_CATS) {
  for (const [id, label, type, def, extra, desc, mcKey] of items) {
    OPTIONS_CATALOG.push({ id, label, category: cat, type, default: def, extra, desc, mcKey: mcKey || null });
    DEFAULT_OPTIONS_MAP[id] = def;
  }
}

const getStateFile = api => path.join(api.paths.appDir, 'aetheria-client-state.json');

function loadState(api) {
  const fp = getStateFile(api);
  const base = {
    selectedMcVersion: '1.21.4',
    selectedLoader: 'fabric',
    activePreset: 'pvp',
    pendingQuickJoin: '',
    githubRawUrl: DEFAULT_GITHUB_RAW_URL,
    options: { ...DEFAULT_OPTIONS_MAP },
    servers: DEFAULT_SERVERS.map(s => ({ ...s })),
    friends: [
      { name: 'Notch', note: 'Classic Builder' },
      { name: 'Technoblade', note: 'Legend' }
    ],
    playtime: { totalSeconds: 0, sessions: 0, lastPlayedAt: null, byVersion: {}, history: [] }
  };
  if (fs.existsSync(fp)) {
    try {
      const p = JSON.parse(fs.readFileSync(fp, 'utf8'));
      return {
        ...base, ...p,
        options: { ...DEFAULT_OPTIONS_MAP, ...(p.options || {}) },
        servers: Array.isArray(p.servers) ? p.servers : base.servers,
        friends: Array.isArray(p.friends) ? p.friends : base.friends,
        playtime: { ...base.playtime, ...(p.playtime || {}) }
      };
    } catch (e) {}
  }
  return base;
}

function saveState(api, st) {
  api.utils.safeMkdirSync(api.paths.appDir);
  fs.writeFileSync(getStateFile(api), JSON.stringify(st, null, 2));
}

function writeVarInt(val) {
  const b = [];
  let v = val >>> 0;
  while (true) {
    if ((v & ~0x7f) === 0) { b.push(v); break; }
    b.push((v & 0x7f) | 0x80);
    v >>>= 7;
  }
  return Buffer.from(b);
}
function readVarInt(buf, off = 0) {
  let n = 0, res = 0, byte = 0;
  do {
    if (off + n >= buf.length) return null;
    byte = buf[off + n];
    res |= (byte & 0x7f) << (7 * n++);
    if (n > 5) throw new Error('VarInt');
  } while ((byte & 0x80) !== 0);
  return { value: res, size: n };
}
const packSlp = (id, data) => { const i = writeVarInt(id); return Buffer.concat([writeVarInt(i.length + data.length), i, data]); };
const parseMotd = d => !d ? '' : typeof d === 'string' ? d.replace(/\u00a7./gi, '').replace(/§./g, '').trim() : ((d.text || '') + (Array.isArray(d.extra) ? d.extra.map(parseMotd).join('') : '')).replace(/§./g, '').trim();

function pingServer(addr, timeoutMs = 3500) {
  return new Promise(resolve => {
    const [host, rawPort] = String(addr || '').trim().split(':');
    const port = parseInt(rawPort, 10) || 25565;
    if (!host) return resolve({ online: false, error: 'Empty host' });
    const t0 = Date.now();
    let done = false, chunks = Buffer.alloc(0);
    const sock = net.createConnection({ host, port });
    const end = r => { if (!done) { done = true; try { sock.destroy(); } catch (e) {} resolve(r); } };
    sock.setTimeout(timeoutMs);
    sock.on('timeout', () => end({ online: false, error: 'Timeout' }));
    sock.on('error', err => end({ online: false, error: err.code || err.message }));
    sock.on('connect', () => {
      const hb = Buffer.from(host, 'utf8'), pb = Buffer.alloc(2); pb.writeUInt16BE(port, 0);
      sock.write(packSlp(0, Buffer.concat([writeVarInt(765), writeVarInt(hb.length), hb, pb, writeVarInt(1)])));
      sock.write(packSlp(0, Buffer.alloc(0)));
      sock._lat = Date.now() - t0;
    });
    sock.on('data', c => {
      chunks = Buffer.concat([chunks, c]);
      try {
        const l = readVarInt(chunks, 0); if (!l || chunks.length < l.size + l.value) return;
        const pid = readVarInt(chunks, l.size); if (!pid || pid.value !== 0) return end({ online: false, error: 'Bad pkt' });
        const sl = readVarInt(chunks, l.size + pid.size); if (!sl) return;
        const start = l.size + pid.size + sl.size; if (chunks.length < start + sl.value) return;
        const j = JSON.parse(chunks.toString('utf8', start, start + sl.value));
        end({ online: true, latencyMs: sock._lat || (Date.now() - t0), playersOnline: j.players?.online ?? 0, playersMax: j.players?.max ?? 0, versionName: j.version?.name || '', motd: parseMotd(j.description) });
      } catch (e) {}
    });
  });
}

function syncOptionsToDisk(api, st, profileDir) {
  const optPath = path.join(profileDir, 'options.txt');
  if (st.options.cos_backup_worlds && fs.existsSync(optPath)) {
    try { fs.copyFileSync(optPath, path.join(profileDir, 'options.txt.aetheria-bak')); } catch (e) {}
  }
  const map = new Map();
  if (fs.existsSync(optPath)) {
    for (const ln of fs.readFileSync(optPath, 'utf8').split(/\r?\n/)) {
      const i = ln.indexOf(':');
      if (i > 0) map.set(ln.slice(0, i), ln.slice(i + 1));
    }
  }
  for (const d of OPTIONS_CATALOG) {
    if (!d.mcKey) continue;
    const v = st.options[d.id];
    if (v === undefined || v === null) continue;
    if (d.mcKey === 'renderClouds') map.set('renderClouds', v === 'off' ? 'false' : v === 'fast' ? '"fast"' : 'true');
    else if (d.mcKey === 'particles') map.set('particles', v === 'all' ? '0' : v === 'decreased' ? '1' : '2');
    else if (d.mcKey === 'graphicsMode') map.set('graphicsMode', v === 'fast' ? '0' : v === 'fabulous' ? '2' : '1');
    else if (d.mcKey === 'attackIndicator') map.set('attackIndicator', v === 'off' ? '0' : v === 'hotbar' ? '2' : '1');
    else if (d.mcKey === 'tutorialStep') { if (v) map.set('tutorialStep', 'none'); }
    else if (['realmsNotifications', 'onboardAccessibility', 'backgroundForChatOnly', 'telemetryOptInExtra'].includes(d.mcKey)) map.set(d.mcKey, v ? 'false' : 'true');
    else if (d.mcKey === 'fov') map.set('fov', ((Number(v) - 70) / 40).toFixed(2));
    else if (typeof v === 'boolean') map.set(d.mcKey, v ? 'true' : 'false');
    else map.set(d.mcKey, String(v));
  }
  fs.writeFileSync(optPath, Array.from(map.entries()).map(([k, v]) => k + ':' + v).join('\n') + '\n');

  const cfgDir = path.join(profileDir, 'config');
  api.utils.safeMkdirSync(cfgDir);
  const sPath = path.join(cfgDir, 'sodium-options.json');
  let sodium = {};
  if (fs.existsSync(sPath)) try { sodium = JSON.parse(fs.readFileSync(sPath, 'utf8')); } catch (e) {}
  sodium.quality = Object.assign(sodium.quality || {}, { enable_vignette: !!st.options.vis_vignette, weather_quality: st.options.vis_weather_alpha < 50 ? 'FAST' : 'DEFAULT' });
  sodium.performance = Object.assign(sodium.performance || {}, { chunk_builder_threads: Number(st.options.fps_sodium_threads || 0), use_block_face_culling: true, use_fog_occlusion: !!st.options.fps_sodium_fog, use_entity_culling: true });
  try { fs.writeFileSync(sPath, JSON.stringify(sodium, null, 2)); } catch (e) {}

  const irisLines = [
    'enableShaders=' + (st.options.vis_shaders_enable && st.options.vis_shader_pack !== 'none' ? 'true' : 'false'),
    'shaderPack=' + (st.options.vis_shader_pack && st.options.vis_shader_pack !== 'none' ? st.options.vis_shader_pack + '.zip' : '')
  ];
  try { fs.writeFileSync(path.join(cfgDir, 'iris.properties'), irisLines.join('\n') + '\n'); } catch (e) {}
}

function listLocalMods(dir) {
  const mDir = path.join(dir, 'mods');
  if (!fs.existsSync(mDir)) return [];
  try {
    return fs.readdirSync(mDir).filter(f => f.endsWith('.jar') || f.endsWith('.jar.disabled')).map(f => ({
      fileName: f, name: f.replace(/\.disabled$/, ''), enabled: !f.endsWith('.disabled'),
      sizeMb: (fs.statSync(path.join(mDir, f)).size / 1048576).toFixed(2)
    }));
  } catch (e) { return []; }
}

const TAB_HTML = `<div class="ac-root">
  <div class="ac-hero card">
    <div class="ac-hero-left">
      <div class="ac-emblem">✦</div>
      <div>
        <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
          <h2 style="font-size:1.2rem;font-weight:800;">Aetheria Client</h2>
          <span class="brand-badge" id="ac-ver-badge">MC 1.21.4 • FABRIC</span>
          <span class="brand-badge" id="ac-opt-badge">250 OPTIONS</span>
        </div>
        <p style="font-size:0.75rem;color:var(--text-muted);margin-top:3px;">Homemade 250-option Lunar engine • Zero mod bloat • 1.8.9–26.1.3</p>
      </div>
    </div>
    <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap;">
      <div id="ac-quick-vers" style="display:flex;gap:4px;flex-wrap:wrap;"></div>
      <button class="btn" id="ac-launch-btn" style="padding:10px 18px;font-weight:800;">▶ LAUNCH CLIENT</button>
    </div>
  </div>
  <div class="chip-group" id="ac-subnav" style="margin-bottom:0;">
    <button class="chip-btn active" data-sub="mods">Mod Menu (250)</button>
    <button class="chip-btn" data-sub="presets">Versions &amp; Hybrid</button>
    <button class="chip-btn" data-sub="config">options.txt</button>
    <button class="chip-btn" data-sub="servers">Servers</button>
    <button class="chip-btn" data-sub="social">Friends &amp; Cosmetics</button>
    <button class="chip-btn" data-sub="playtime">Playtime &amp; GitHub</button>
  </div>
  <div class="ac-sub active" id="ac-sub-mods">
    <div class="card flex-between" style="padding:12px;margin-bottom:0;flex-wrap:wrap;">
      <input type="text" class="input-field" id="ac-search" placeholder="Search 250 homemade options (fps, zoom, keystrokes, zgc, gamma)..." style="flex:1;min-width:220px;" />
      <div style="display:flex;gap:8px;"><button class="btn-secondary" id="ac-sync-btn">Sync to Profile</button><button class="btn-secondary" id="ac-reset-btn">Reset 250</button></div>
    </div>
    <div class="chip-group" id="ac-cats" style="margin-bottom:0;"></div>
    <div class="ac-grid" id="ac-opts-grid"></div>
  </div>
  <div class="ac-sub" id="ac-sub-presets">
    <div class="card" style="margin-bottom:0;">
      <div class="flex-between" style="margin-bottom:10px;flex-wrap:wrap;">
        <div><strong style="font-size:0.9rem;">Version Selector (1.8.9 – 26.1.3)</strong><span style="display:block;font-size:0.73rem;color:var(--text-muted);">Syncs active Aetheria profile while preserving all 250 options</span></div>
        <div style="display:flex;gap:6px;"><button class="chip-btn" id="ac-ld-fabric">Fabric</button><button class="chip-btn" id="ac-ld-vanilla">Vanilla</button></div>
      </div>
      <div id="ac-all-vers" style="display:flex;gap:6px;flex-wrap:wrap;"></div>
    </div>
    <div class="card" style="margin-bottom:0;"><strong style="font-size:0.9rem;display:block;margin-bottom:10px;">1-Click Client Presets</strong><div class="ac-pgrid" id="ac-presets-grid"></div></div>
    <div class="card" style="margin-bottom:0;">
      <div class="flex-between" style="margin-bottom:10px;flex-wrap:wrap;">
        <div><strong style="font-size:0.9rem;">Hybrid Engine: Optional 6-Mod ARM64 Core + Local JARs</strong><span style="display:block;font-size:0.73rem;color:var(--text-muted);">Keep zero bloat or install the lean 6-mod Sodium/Lithium/Iris pack</span></div>
        <button class="btn" id="ac-install-core">Install 6-Mod Core</button>
      </div>
      <div id="ac-core-preview" class="ac-cgrid"></div>
      <div style="margin-top:12px;border-top:1px solid var(--border);padding-top:10px;">
        <div class="flex-between" style="margin-bottom:8px;"><span style="font-size:0.76rem;color:var(--text-muted);font-weight:600;">Active Profile /mods</span><button class="btn-secondary" id="ac-ref-mods" style="padding:3px 9px;font-size:0.7rem;">Refresh</button></div>
        <div id="ac-local-mods" style="display:flex;flex-direction:column;gap:6px;"></div>
      </div>
    </div>
  </div>
  <div class="ac-sub" id="ac-sub-config">
    <div class="card" style="margin-bottom:0;">
      <div class="flex-between" style="margin-bottom:10px;flex-wrap:wrap;">
        <strong style="font-size:0.9rem;">Live options.txt Editor</strong>
        <div style="display:flex;gap:8px;"><button class="btn-secondary" id="ac-cfg-reload">Reload</button><button class="btn-secondary" id="ac-cfg-push">Push 250 Options</button><button class="btn" id="ac-cfg-save">Save File</button></div>
      </div>
      <textarea id="ac-raw-txt" class="input-field" rows="17" style="font-family:ui-monospace,monospace;font-size:0.75rem;"></textarea>
    </div>
  </div>
  <div class="ac-sub" id="ac-sub-servers">
    <div class="card" style="margin-bottom:0;">
      <div class="flex-between" style="margin-bottom:10px;flex-wrap:wrap;">
        <strong style="font-size:0.9rem;">Server Browser &amp; 1-Click Quick-Join</strong>
        <button class="btn-secondary" id="ac-ping-all">Ping All</button>
      </div>
      <div style="display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap;">
        <input type="text" id="ac-srv-name" class="input-field" placeholder="Server Name" style="max-width:190px;" />
        <input type="text" id="ac-srv-ip" class="input-field" placeholder="IP (e.g. mc.hypixel.net)" style="flex:1;min-width:180px;" />
        <button class="btn" id="ac-add-srv">+ Add</button>
      </div>
      <div id="ac-srv-list" style="display:flex;flex-direction:column;gap:8px;"></div>
    </div>
  </div>
  <div class="ac-sub" id="ac-sub-social">
    <div class="grid-2" style="align-items:start;margin-bottom:0;">
      <div class="card" style="margin-bottom:0;">
        <div class="flex-between" style="margin-bottom:10px;"><strong style="font-size:0.9rem;">Friends Roster</strong><span class="brand-badge" id="ac-fr-cnt">0</span></div>
        <div style="display:flex;gap:8px;margin-bottom:10px;"><input type="text" id="ac-fr-in" class="input-field" placeholder="Minecraft username..." /><button class="btn" id="ac-fr-add">Add</button></div>
        <div id="ac-fr-list" style="display:flex;flex-direction:column;gap:8px;"></div>
      </div>
      <div class="card" style="margin-bottom:0;">
        <div class="flex-between" style="margin-bottom:10px;"><strong style="font-size:0.9rem;">Cosmetics &amp; Skin Quick-Swap</strong><button class="btn-secondary" id="ac-go-skins" style="padding:3px 9px;font-size:0.7rem;">Skin Studio</button></div>
        <div id="ac-cos-box" style="display:flex;flex-direction:column;gap:8px;"></div>
      </div>
    </div>
  </div>
  <div class="ac-sub" id="ac-sub-playtime">
    <div class="grid-2" style="margin-bottom:0;">
      <div class="card" style="margin-bottom:0;"><span class="lbl">Total Playtime</span><strong id="ac-pt-tot" style="font-size:1.5rem;">0m</strong><span id="ac-pt-ses" style="display:block;font-size:0.74rem;color:var(--text-muted);margin-top:4px;">0 sessions</span></div>
      <div class="card" style="margin-bottom:0;"><span class="lbl">Last Played</span><strong id="ac-pt-last" style="font-size:1.05rem;">Never</strong><span style="display:block;font-size:0.74rem;color:var(--text-muted);margin-top:4px;">Recorded via onGameExit</span></div>
    </div>
    <div class="card" style="margin-bottom:0;"><strong style="font-size:0.9rem;display:block;margin-bottom:8px;">Version Playtime &amp; Recent Sessions</strong><div id="ac-pt-list" style="display:flex;flex-direction:column;gap:6px;"></div></div>
    <div class="card" style="margin-bottom:0;">
      <strong style="font-size:0.9rem;display:block;margin-bottom:6px;">GitHub Extension Self-Updater</strong>
      <p style="font-size:0.73rem;color:var(--text-muted);margin-bottom:8px;">Paste your raw GitHub URL for <code>index.js</code> to pull updates straight from your repo.</p>
      <div style="display:flex;gap:8px;flex-wrap:wrap;"><input type="text" id="ac-gh-url" class="input-field" placeholder="https://raw.githubusercontent.com/USER/REPO/main/index.js" style="flex:1;min-width:240px;" /><button class="btn" id="ac-gh-update">Update from GitHub</button></div>
    </div>
  </div>
</div>`;

const TAB_CSS = `.ac-root{display:flex;flex-direction:column;gap:12px;padding-bottom:24px;}
.ac-hero{display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:0;background:linear-gradient(135deg,rgba(var(--accent-rgb),0.08),rgba(var(--card-rgb),0.65));}
.ac-hero-left{display:flex;align-items:center;gap:12px;}
.ac-emblem{width:44px;height:44px;border-radius:11px;display:flex;align-items:center;justify-content:center;font-size:1.35rem;background:var(--accent);color:var(--accent-fg);font-weight:800;flex-shrink:0;}
.ac-sub{display:none;flex-direction:column;gap:12px;} .ac-sub.active{display:flex;}
.ac-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(290px,1fr));gap:10px;}
.ac-ocard{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:11px 13px;display:flex;flex-direction:column;justify-content:space-between;gap:8px;backdrop-filter:blur(16px) saturate(145%);-webkit-backdrop-filter:blur(16px) saturate(145%);}
.ac-ocard:hover{border-color:var(--border-hover);}
.ac-pill{min-width:48px;padding:4px 10px;border-radius:99px;font-size:0.68rem;font-weight:700;border:1px solid var(--border);background:rgba(var(--card-rgb),0.6);color:var(--text-muted);cursor:pointer;}
.ac-pill.on{background:var(--accent);color:var(--accent-fg);border-color:var(--accent);}
.ac-cbtn{padding:3px 8px;border-radius:6px;font-size:0.68rem;border:1px solid var(--border);background:rgba(var(--card-rgb),0.5);color:var(--text-muted);cursor:pointer;}
.ac-cbtn.active{background:var(--accent-bg);border-color:var(--accent);color:var(--text);font-weight:600;}
.ac-pgrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:8px;}
.ac-cgrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(175px,1fr));gap:8px;}
.ac-pcard{padding:11px;border-radius:var(--radius);border:1px solid var(--border);background:rgba(var(--card-rgb),0.45);cursor:pointer;text-align:left;color:var(--text);}
.ac-pcard.active{border-color:var(--accent);background:var(--accent-bg);}`;

const TAB_SCRIPT = `(async function() {
  const esc = Aetheria.escapeHtml, q = s => panelEl.querySelector(s);
  let data = await Aetheria.ipcRenderer.invoke('ac:get-state');
  window.__AetheriaClientCache = data;
  let activeCat = 'All', saveTimer = null;
  const pings = {};
  const fmtSec = sec => { const s = Math.max(0, Math.round(sec || 0)), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60); return h ? h + 'h ' + m + 'm' : m + 'm ' + (s % 60) + 's'; };

  function queueSave() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => Aetheria.ipcRenderer.invoke('ac:save-options', { options: data.state.options, activePreset: data.state.activePreset }), 180);
  }

  function renderHeader() {
    const st = data.state;
    q('#ac-ver-badge').innerText = 'MC ' + st.selectedMcVersion + ' • ' + String(st.selectedLoader || 'fabric').toUpperCase();
    const onCnt = data.catalog.options.filter(o => o.type === 'b' && st.options[o.id]).length;
    q('#ac-opt-badge').innerText = data.catalog.options.length + ' OPTIONS (' + onCnt + ' ON)';
    const qv = q('#ac-quick-vers'); qv.innerHTML = '';
    ['26.1.3', '1.21.4', '1.20.4', '1.8.9'].forEach(v => {
      const b = document.createElement('button');
      b.className = 'ac-cbtn ' + (st.selectedMcVersion === v ? 'active' : '');
      b.innerText = v; b.onclick = () => setVer(v, st.selectedLoader);
      qv.appendChild(b);
    });
  }

  async function setVer(version, loader) {
    const res = await Aetheria.ipcRenderer.invoke('ac:set-version', { version, loader });
    if (res && res.ok) { data.state = res.state; window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Client: MC ' + version + ' (' + loader.toUpperCase() + ')'); }
  }

  function renderCats() {
    const bar = q('#ac-cats'); bar.innerHTML = '';
    ['All', ...data.catalog.categories].forEach(c => {
      const b = document.createElement('button');
      b.className = 'chip-btn ' + (activeCat === c ? 'active' : '');
      b.innerText = c === 'All' ? 'All (' + data.catalog.options.length + ')' : c;
      b.onclick = () => { activeCat = c; renderCats(); renderOpts(); };
      bar.appendChild(b);
    });
  }

  function renderOpts() {
    const grid = q('#ac-opts-grid'), term = (q('#ac-search').value || '').trim().toLowerCase();
    const list = data.catalog.options.filter(o => (activeCat === 'All' || o.category === activeCat) && (!term || o.label.toLowerCase().includes(term) || o.desc.toLowerCase().includes(term) || o.category.toLowerCase().includes(term)));
    grid.innerHTML = '';
    if (!list.length) return (grid.innerHTML = '<div class="card" style="grid-column:1/-1;"><span style="font-size:0.8rem;color:var(--text-muted);">No matching options.</span></div>');
    const frag = document.createDocumentFragment();
    list.forEach(o => {
      const card = document.createElement('div'); card.className = 'ac-ocard';
      const val = data.state.options[o.id];
      const top = document.createElement('div'); top.style.cssText = 'display:flex;justify-content:space-between;align-items:flex-start;gap:10px;';
      top.innerHTML = '<div><span style="font-size:0.6rem;color:var(--text-dim);text-transform:uppercase;">' + esc(o.category) + '</span><strong style="display:block;font-size:0.8rem;">' + esc(o.label) + '</strong><p style="font-size:0.69rem;color:var(--text-muted);margin-top:2px;">' + esc(o.desc) + '</p></div>';
      if (o.type === 'b') {
        const p = document.createElement('button'); p.className = 'ac-pill ' + (val ? 'on' : ''); p.innerText = val ? 'ON' : 'OFF';
        p.onclick = () => { data.state.options[o.id] = !data.state.options[o.id]; data.state.activePreset = 'custom'; p.className = 'ac-pill ' + (data.state.options[o.id] ? 'on' : ''); p.innerText = data.state.options[o.id] ? 'ON' : 'OFF'; renderHeader(); queueSave(); };
        top.appendChild(p); card.appendChild(top);
      } else if (o.type === 'r') {
        const badge = document.createElement('span'); badge.className = 'brand-badge'; badge.innerText = String(val); top.appendChild(badge); card.appendChild(top);
        const sl = document.createElement('input'); sl.type = 'range'; sl.className = 'range-slider'; sl.min = o.extra[0]; sl.max = o.extra[1]; sl.step = o.extra[2]; sl.value = val;
        sl.oninput = () => { const n = Number(sl.value); data.state.options[o.id] = n; data.state.activePreset = 'custom'; badge.innerText = String(n); queueSave(); };
        card.appendChild(sl);
      } else {
        card.appendChild(top);
        const row = document.createElement('div'); row.style.cssText = 'display:flex;gap:4px;flex-wrap:wrap;';
        o.extra.forEach(ch => {
          const cb = document.createElement('button'); cb.className = 'ac-cbtn ' + (String(val) === String(ch) ? 'active' : ''); cb.innerText = ch;
          cb.onclick = () => { data.state.options[o.id] = ch; data.state.activePreset = 'custom'; row.querySelectorAll('button').forEach(x => x.classList.remove('active')); cb.classList.add('active'); queueSave(); };
          row.appendChild(cb);
        });
        card.appendChild(row);
      }
      frag.appendChild(card);
    });
    grid.appendChild(frag);
  }

  function renderPresetsAndMods() {
    const st = data.state;
    q('#ac-ld-fabric').classList.toggle('active', st.selectedLoader === 'fabric');
    q('#ac-ld-vanilla').classList.toggle('active', st.selectedLoader === 'vanilla');
    const vg = q('#ac-all-vers'); vg.innerHTML = '';
    data.catalog.versions.forEach(v => {
      const b = document.createElement('button'); b.className = 'chip-btn ' + (st.selectedMcVersion === v ? 'active' : ''); b.style.padding = '5px 10px'; b.innerText = v;
      b.onclick = () => setVer(v, st.selectedLoader); vg.appendChild(b);
    });
    const pg = q('#ac-presets-grid'); pg.innerHTML = '';
    Object.values(data.catalog.presets).forEach(p => {
      const b = document.createElement('button'); b.className = 'ac-pcard ' + (st.activePreset === p.id ? 'active' : '');
      b.innerHTML = '<strong style="font-size:0.82rem;display:block;">' + esc(p.name) + '</strong><span style="font-size:0.7rem;color:var(--text-muted);display:block;margin-top:3px;">' + esc(p.desc) + '</span>';
      b.onclick = async () => { const r = await Aetheria.ipcRenderer.invoke('ac:apply-preset', p.id); if (r && r.ok) { data.state = r.state; window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Applied preset: ' + p.name); } };
      pg.appendChild(b);
    });
    const cp = q('#ac-core-preview'); cp.innerHTML = '';
    data.catalog.coreMods.forEach(m => {
      const d = document.createElement('div'); d.style.cssText = 'padding:8px 10px;border:1px solid var(--border);border-radius:8px;background:rgba(var(--card-rgb),0.4);';
      d.innerHTML = '<strong style="font-size:0.78rem;display:block;">' + esc(m.name) + '</strong><span style="font-size:0.68rem;color:var(--text-muted);">' + esc(m.desc) + '</span>';
      cp.appendChild(d);
    });
    const lm = q('#ac-local-mods'); lm.innerHTML = '';
    if (!data.localMods.length) lm.innerHTML = '<span style="font-size:0.75rem;color:var(--text-muted);">No .jar mods in active profile — pure 250-option homemade mode active.</span>';
    else data.localMods.forEach(m => {
      const r = document.createElement('div'); r.className = 'mod-item'; r.style.marginBottom = '0';
      r.innerHTML = '<div><strong style="font-size:0.8rem;">' + esc(m.name) + '</strong><span style="font-size:0.68rem;color:var(--text-muted);margin-left:8px;">' + m.sizeMb + ' MB</span></div><button class="btn-secondary" style="font-size:0.7rem;padding:4px 10px;">' + (m.enabled ? 'Enabled' : 'Disabled') + '</button>';
      r.querySelector('button').onclick = async () => { data.localMods = await Aetheria.ipcRenderer.invoke('ac:toggle-local-mod', m.fileName); renderPresetsAndMods(); };
      lm.appendChild(r);
    });
  }

  function renderServers() {
    const list = q('#ac-srv-list'); list.innerHTML = '';
    if (!data.state.servers.length) return (list.innerHTML = '<span style="font-size:0.78rem;color:var(--text-muted);">No servers saved.</span>');
    data.state.servers.forEach((s, idx) => {
      const p = pings[s.ip];
      const row = document.createElement('div'); row.className = 'mod-item'; row.style.marginBottom = '0';
      row.innerHTML = '<div><div style="display:flex;align-items:center;gap:8px;"><strong style="font-size:0.84rem;">' + esc(s.name || s.ip) + '</strong><span class="brand-badge">' + (p ? (p.online ? p.playersOnline + '/' + p.playersMax + ' • ' + p.latencyMs + 'ms' : 'OFFLINE') : 'UNPINGED') + '</span></div><span style="font-size:0.72rem;color:var(--text-muted);display:block;margin-top:2px;">' + esc(s.ip) + (p && p.motd ? ' — ' + esc(p.motd.slice(0, 64)) : '') + '</span></div><div style="display:flex;gap:6px;"><button class="btn-secondary" style="font-size:0.72rem;" data-a="p">Ping</button><button class="btn" style="font-size:0.72rem;" data-a="j">Quick Join</button><button class="btn-secondary" style="font-size:0.72rem;color:var(--danger);" data-a="d">✕</button></div>';
      row.querySelector('[data-a="p"]').onclick = async () => { pings[s.ip] = await Aetheria.ipcRenderer.invoke('ac:ping-server', s.ip); renderServers(); };
      row.querySelector('[data-a="j"]').onclick = async () => { await Aetheria.ipcRenderer.invoke('ac:set-quick-join', s.ip); Aetheria.toast('Launching into ' + s.ip + '...'); Aetheria.ipcRenderer.send('launch-game', { selectedVersion: data.state.selectedMcVersion }); };
      row.querySelector('[data-a="d"]').onclick = async () => { data.state.servers.splice(idx, 1); await Aetheria.ipcRenderer.invoke('ac:save-servers', data.state.servers); renderServers(); };
      list.appendChild(row);
    });
  }

  function renderSocial() {
    const fl = q('#ac-fr-list'); q('#ac-fr-cnt').innerText = String(data.state.friends.length); fl.innerHTML = '';
    data.state.friends.forEach((f, idx) => {
      const r = document.createElement('div'); r.className = 'mod-item'; r.style.marginBottom = '0';
      r.innerHTML = '<div style="display:flex;align-items:center;gap:10px;"><img src="https://mc-heads.net/avatar/' + encodeURIComponent(f.name) + '/32" style="width:28px;height:28px;border-radius:6px;image-rendering:pixelated;" onerror="this.remove()" /><div><strong style="font-size:0.82rem;">' + esc(f.name) + '</strong><span style="display:block;font-size:0.68rem;color:var(--text-muted);">' + esc(f.note || 'Friend') + '</span></div></div><button class="btn-secondary" style="font-size:0.7rem;color:var(--danger);">Remove</button>';
      r.querySelector('button').onclick = async () => { data.state.friends.splice(idx, 1); await Aetheria.ipcRenderer.invoke('ac:save-friends', data.state.friends); renderSocial(); };
      fl.appendChild(r);
    });
    const cb = q('#ac-cos-box'); cb.innerHTML = '';
    ['cos_cape_enable', 'cos_cape_style', 'cos_wings', 'cos_wings_style', 'cos_halo', 'cos_badge', 'cos_slim'].forEach(id => {
      const o = data.catalog.options.find(x => x.id === id); if (!o) return;
      const r = document.createElement('div'); r.className = 'flex-between'; r.style.cssText = 'padding:6px 8px;border:1px solid var(--border);border-radius:8px;';
      r.innerHTML = '<span style="font-size:0.78rem;font-weight:600;">' + esc(o.label) + '</span>';
      if (o.type === 'b') {
        const p = document.createElement('button'); p.className = 'ac-pill ' + (data.state.options[id] ? 'on' : ''); p.innerText = data.state.options[id] ? 'ON' : 'OFF';
        p.onclick = () => { data.state.options[id] = !data.state.options[id]; queueSave(); renderSocial(); }; r.appendChild(p);
      } else {
        const w = document.createElement('div'); w.style.cssText = 'display:flex;gap:4px;flex-wrap:wrap;justify-content:flex-end;';
        o.extra.forEach(c => { const b = document.createElement('button'); b.className = 'ac-cbtn ' + (data.state.options[id] === c ? 'active' : ''); b.innerText = c; b.onclick = () => { data.state.options[id] = c; queueSave(); renderSocial(); }; w.appendChild(b); });
        r.appendChild(w);
      }
      cb.appendChild(r);
    });
  }

  function renderPlaytime() {
    const pt = data.state.playtime || { totalSeconds: 0, sessions: 0, byVersion: {}, history: [] };
    q('#ac-pt-tot').innerText = fmtSec(pt.totalSeconds);
    q('#ac-pt-ses').innerText = (pt.sessions || 0) + ' tracked sessions';
    q('#ac-pt-last').innerText = pt.lastPlayedAt ? new Date(pt.lastPlayedAt).toLocaleString() : 'Never';
    q('#ac-gh-url').value = data.state.githubRawUrl || '';
    const list = q('#ac-pt-list'); list.innerHTML = '';
    const entries = Object.entries(pt.byVersion || {});
    if (!entries.length) list.innerHTML = '<span style="font-size:0.76rem;color:var(--text-muted);">No playtime logged yet. Launch Minecraft to start tracking!</span>';
    else entries.sort((a, b) => b[1] - a[1]).forEach(([v, sec]) => {
      const d = document.createElement('div'); d.className = 'flex-between'; d.style.cssText = 'padding:6px 10px;border:1px solid var(--border);border-radius:8px;';
      d.innerHTML = '<strong style="font-size:0.8rem;">MC ' + esc(v) + '</strong><span class="brand-badge">' + fmtSec(sec) + '</span>';
      list.appendChild(d);
    });
  }

  function renderAll() { renderHeader(); renderCats(); renderOpts(); renderPresetsAndMods(); renderServers(); renderSocial(); renderPlaytime(); }

  q('#ac-subnav').querySelectorAll('button').forEach(btn => {
    btn.onclick = async () => {
      q('#ac-subnav').querySelectorAll('button').forEach(b => b.classList.remove('active'));
      panelEl.querySelectorAll('.ac-sub').forEach(p => p.classList.remove('active'));
      btn.classList.add('active'); q('#ac-sub-' + btn.dataset.sub).classList.add('active');
      if (btn.dataset.sub === 'config') q('#ac-raw-txt').value = await Aetheria.ipcRenderer.invoke('ac:read-options-txt');
    };
  });

  q('#ac-search').oninput = renderOpts;
  q('#ac-launch-btn').onclick = () => Aetheria.ipcRenderer.send('launch-game', { selectedVersion: data.state.selectedMcVersion });
  q('#ac-sync-btn').onclick = async () => { await Aetheria.ipcRenderer.invoke('ac:sync-now'); Aetheria.toast('Synced 250 options to profile!'); };
  q('#ac-reset-btn').onclick = async () => {
    if (await Aetheria.showConfirmModal('Reset all 250 client options to defaults?', 'Reset Aetheria Client')) {
      const r = await Aetheria.ipcRenderer.invoke('ac:reset-options'); data.state = r.state; window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('250 options reset.');
    }
  };
  q('#ac-ld-fabric').onclick = () => setVer(data.state.selectedMcVersion, 'fabric');
  q('#ac-ld-vanilla').onclick = () => setVer(data.state.selectedMcVersion, 'vanilla');
  q('#ac-install-core').onclick = async () => {
    Aetheria.toast('Installing 6-Mod ARM64 Core for ' + data.state.selectedMcVersion + '...');
    const res = await Aetheria.ipcRenderer.invoke('ac:install-core-mods', { mcVersion: data.state.selectedMcVersion });
    data.localMods = res.localMods || data.localMods; renderPresetsAndMods();
    Aetheria.toast('Installed ' + res.installed.length + ' core mod(s)' + (res.skipped.length ? ' (' + res.skipped.length + ' unavailable for version)' : ''));
  };
  q('#ac-ref-mods').onclick = async () => { data = await Aetheria.ipcRenderer.invoke('ac:get-state'); window.__AetheriaClientCache = data; renderPresetsAndMods(); };
  q('#ac-cfg-reload').onclick = async () => { q('#ac-raw-txt').value = await Aetheria.ipcRenderer.invoke('ac:read-options-txt'); };
  q('#ac-cfg-push').onclick = async () => { await Aetheria.ipcRenderer.invoke('ac:sync-now'); q('#ac-raw-txt').value = await Aetheria.ipcRenderer.invoke('ac:read-options-txt'); Aetheria.toast('Pushed 250 options to options.txt'); };
  q('#ac-cfg-save').onclick = async () => { await Aetheria.ipcRenderer.invoke('ac:write-options-txt', q('#ac-raw-txt').value); Aetheria.toast('Saved options.txt'); };
  q('#ac-add-srv').onclick = async () => {
    const ip = q('#ac-srv-ip').value.trim(); if (!ip) return;
    data.state.servers.push({ name: q('#ac-srv-name').value.trim() || ip, ip });
    q('#ac-srv-name').value = q('#ac-srv-ip').value = '';
    await Aetheria.ipcRenderer.invoke('ac:save-servers', data.state.servers); renderServers();
  };
  q('#ac-ping-all').onclick = async () => {
    Aetheria.toast('Pinging servers...');
    await Promise.all(data.state.servers.map(async s => { pings[s.ip] = await Aetheria.ipcRenderer.invoke('ac:ping-server', s.ip); }));
    renderServers();
  };
  q('#ac-fr-add').onclick = async () => {
    const n = q('#ac-fr-in').value.trim(); if (!n) return;
    data.state.friends.push({ name: n, note: 'Friend' }); q('#ac-fr-in').value = '';
    await Aetheria.ipcRenderer.invoke('ac:save-friends', data.state.friends); renderSocial();
  };
  q('#ac-go-skins').onclick = () => Aetheria.switchTab('skins');
  q('#ac-gh-update').onclick = async () => {
    const url = q('#ac-gh-url').value.trim();
    if (!url) return Aetheria.showAlertModal('Enter your GitHub raw index.js URL first.', 'GitHub Updater');
    try {
      const res = await Aetheria.ipcRenderer.invoke('ac:update-from-github', url);
      Aetheria.toast('Updated from GitHub (' + res.bytes + ' bytes)! Reloading extensions...');
      await Aetheria.ipcRenderer.invoke('reload-extensions');
    } catch (err) { Aetheria.showAlertModal(err.message, 'GitHub Update Failed'); }
  };

  Aetheria.ipcRenderer.on('ac:state-updated', (e, next) => { data = next; window.__AetheriaClientCache = next; renderAll(); });
  Aetheria.ipcRenderer.on('game-started', () => { q('#ac-launch-btn').disabled = true; q('#ac-launch-btn').innerText = 'RUNNING...'; });
  Aetheria.ipcRenderer.on('game-exited', () => { q('#ac-launch-btn').disabled = false; q('#ac-launch-btn').innerText = '▶ LAUNCH CLIENT'; });
  Aetheria.ipcRenderer.on('game-crashed', () => { q('#ac-launch-btn').disabled = false; q('#ac-launch-btn').innerText = '▶ LAUNCH CLIENT'; });
  renderAll();
})();`;

const WIDGET_JS = `(function(Aetheria) {
  return function(wConf, container) {
    function draw(d) {
      var st = d && d.state ? d.state : { selectedMcVersion: '1.21.4', selectedLoader: 'fabric', activePreset: 'pvp', playtime: { totalSeconds: 0 } };
      var tiny = wConf.colSpan <= 3 || wConf.rowSpan <= 1;
      var min = Math.floor((st.playtime && st.playtime.totalSeconds ? st.playtime.totalSeconds : 0) / 60);
      container.innerHTML = '<div class="widget-card">' +
        '<div class="widget-head"><span class="widget-title">✦ Aetheria Client</span><span class="brand-badge">' + Aetheria.escapeHtml(st.selectedMcVersion) + '</span></div>' +
        (tiny ? '' : '<div style="font-size:0.72rem;color:var(--text-muted);">Preset: <strong style="color:var(--text);">' + Aetheria.escapeHtml(String(st.activePreset || 'pvp').toUpperCase()) + '</strong> • ' + min + 'm played</div>') +
        '<div style="display:flex;gap:5px;"><button class="btn" style="flex:1;padding:4px 8px;font-size:0.7rem;" data-act="play">▶ Play</button>' +
        (tiny ? '' : '<button class="btn-secondary" style="padding:4px 8px;font-size:0.7rem;" data-act="open">Client</button>') +
        '</div></div>';
      container.querySelector('[data-act="play"]').onclick = function() { Aetheria.ipcRenderer.send('launch-game', { selectedVersion: st.selectedMcVersion }); };
      var ob = container.querySelector('[data-act="open"]');
      if (ob) ob.onclick = function() { Aetheria.switchTab('ext-client'); };
    }
    draw(window.__AetheriaClientCache);
    if (!window.__AetheriaClientCache && !window.__AcWidgetLoading) {
      window.__AcWidgetLoading = true;
      Aetheria.ipcRenderer.invoke('ac:get-state').then(function(d) {
        window.__AetheriaClientCache = d;
        window.__AcWidgetLoading = false;
        draw(d);
      }).catch(function() { window.__AcWidgetLoading = false; });
    }
  };
})`;

async function activate(api) {
  let sessionStart = null;
  const pack = () => {
    const state = loadState(api);
    return {
      state,
      catalog: { versions: CLIENT_VERSIONS, presets: CLIENT_PRESETS, categories: RAW_CATS.map(c => c[0]), options: OPTIONS_CATALOG, coreMods: CORE_HYBRID_MODS },
      localMods: listLocalMods(api.getActiveProfileDir())
    };
  };

  api.registerIpc('ac:get-state', () => pack());
  api.registerIpc('ac:save-options', (e, { options, activePreset }) => {
    const st = loadState(api);
    if (options) st.options = { ...st.options, ...options };
    if (activePreset !== undefined) st.activePreset = activePreset;
    saveState(api, st);
    return { ok: true, state: st };
  });
  api.registerIpc('ac:reset-options', () => {
    const st = loadState(api); st.options = { ...DEFAULT_OPTIONS_MAP }; st.activePreset = 'custom'; saveState(api, st); return { ok: true, state: st };
  });
  api.registerIpc('ac:apply-preset', (e, id) => {
    const st = loadState(api), p = CLIENT_PRESETS[id]; if (!p) return { ok: false };
    st.activePreset = id; st.options = { ...st.options, ...p.ov }; saveState(api, st); return { ok: true, state: st };
  });
  api.registerIpc('ac:set-version', (e, { version, loader }) => {
    const st = loadState(api);
    if (version) st.selectedMcVersion = version;
    if (loader) st.selectedLoader = loader;
    saveState(api, st);
    const cfg = api.getConfig(), prof = (cfg.profiles || []).find(x => x.id === (cfg.activeProfileId || 'default'));
    if (prof) { prof.version = st.selectedMcVersion; prof.loader = st.selectedLoader; if (st.selectedLoader === 'vanilla') prof.loaderVersion = ''; cfg.selectedVersion = st.selectedMcVersion; api.saveConfig(cfg); }
    return { ok: true, state: st };
  });
  api.registerIpc('ac:set-quick-join', (e, ip) => { const st = loadState(api); st.pendingQuickJoin = String(ip || '').trim(); saveState(api, st); return { ok: true }; });
  api.registerIpc('ac:save-servers', (e, servers) => { const st = loadState(api); st.servers = Array.isArray(servers) ? servers : []; saveState(api, st); return { ok: true }; });
  api.registerIpc('ac:ping-server', (e, addr) => pingServer(addr));
  api.registerIpc('ac:save-friends', (e, friends) => { const st = loadState(api); st.friends = Array.isArray(friends) ? friends : []; saveState(api, st); return { ok: true }; });
  api.registerIpc('ac:read-options-txt', () => { const p = path.join(api.getActiveProfileDir(), 'options.txt'); return fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : ''; });
  api.registerIpc('ac:write-options-txt', (e, txt) => { const d = api.getActiveProfileDir(); api.utils.safeMkdirSync(d); fs.writeFileSync(path.join(d, 'options.txt'), String(txt || '')); return { ok: true }; });
  api.registerIpc('ac:sync-now', () => { syncOptionsToDisk(api, loadState(api), api.getActiveProfileDir()); return { ok: true }; });
  api.registerIpc('ac:toggle-local-mod', (e, fn) => {
    const mDir = path.join(api.getActiveProfileDir(), 'mods'), src = path.join(mDir, fn);
    if (fs.existsSync(src)) fs.renameSync(src, path.join(mDir, fn.endsWith('.disabled') ? fn.replace(/\.disabled$/, '') : fn + '.disabled'));
    return listLocalMods(api.getActiveProfileDir());
  });
  api.registerIpc('ac:install-core-mods', async (e, { mcVersion }) => {
    const clean = api.utils.extractMcVersion(mcVersion || loadState(api).selectedMcVersion || '1.21.4');
    const mDir = path.join(api.getActiveProfileDir(), 'mods'); api.utils.safeMkdirSync(mDir);
    const installed = [], skipped = [];
    const gv = encodeURIComponent(JSON.stringify([clean])), ld = encodeURIComponent(JSON.stringify(['fabric']));
    for (const m of CORE_HYBRID_MODS) {
      try {
        const vers = await api.utils.httpGet('https://api.modrinth.com/v2/project/' + m.id + '/version?game_versions=' + gv + '&loaders=' + ld);
        const f = Array.isArray(vers) && vers[0] && (vers[0].files.find(x => x.primary) || vers[0].files[0]);
        if (f) { const dest = path.join(mDir, f.filename); if (!fs.existsSync(dest)) await api.utils.downloadFile(f.url, dest); installed.push(f.filename); continue; }
      } catch (err) {}
      skipped.push(m.name);
    }
    return { installed, skipped, localMods: listLocalMods(api.getActiveProfileDir()) };
  });
  api.registerIpc('ac:update-from-github', async (e, url) => {
    const st = loadState(api); st.githubRawUrl = String(url || '').trim(); saveState(api, st);
    if (!/^https:\/\//i.test(st.githubRawUrl)) throw new Error('Must be an https:// raw GitHub URL.');
    const code = await api.utils.httpGetText(st.githubRawUrl);
    if (!code || !code.includes('activate')) throw new Error('Downloaded file does not look like a valid Aetheria Client index.js.');
    fs.writeFileSync(path.join(api.extensionDir, 'index.js'), code, 'utf8');
    return { ok: true, bytes: Buffer.byteLength(code, 'utf8') };
  });

  api.onBeforeLaunch(async (ctx) => {
    const st = loadState(api), o = st.options;
    sessionStart = Date.now();
    ctx.sendStatus('Aetheria Client: syncing 250 options...');
    syncOptionsToDisk(api, st, ctx.activeGameDir);
    const jvm = ctx.jvmArgs.filter(a => !a.startsWith('-XX:+UseG1GC') && !a.startsWith('-XX:+UseZGC') && !a.startsWith('-XX:MaxGCPauseMillis='));
    if (o.fps_gc_mode === 'ZGC' || o.fps_gc_mode === 'GenerationalZGC') { jvm.push('-XX:+UseZGC'); if (o.fps_gc_mode === 'GenerationalZGC') jvm.push('-XX:+ZGenerational'); }
    else jvm.push('-XX:+UseG1GC', '-XX:MaxGCPauseMillis=' + Number(o.fps_gc_pause || 20));
    if (o.fps_oryon_pretouch && !jvm.includes('-XX:+AlwaysPreTouch')) jvm.push('-XX:+AlwaysPreTouch');
    if (o.fps_string_dedup) jvm.push('-XX:+UseStringDeduplication');
    if (o.fps_parallel_ref && !jvm.includes('-XX:+ParallelRefProcEnabled')) jvm.push('-XX:+ParallelRefProcEnabled');
    if (o.fps_compact_hdr) jvm.push('-XX:+UnlockExperimentalVMOptions', '-XX:+UseCompactObjectHeaders');
    if (o.fps_netty_threads) jvm.push('-Dio.netty.allocator.maxOrder=9');
    ctx.jvmArgs.length = 0; ctx.jvmArgs.push(...jvm);
    if (o.dsp_fullscreen && !ctx.gameArgs.includes('--fullscreen')) ctx.gameArgs.push('--fullscreen');
    else if (o.dsp_custom_res) ctx.gameArgs.push('--width', String(o.dsp_width || 1280), '--height', String(o.dsp_height || 720));
    if (st.pendingQuickJoin) {
      const target = st.pendingQuickJoin; st.pendingQuickJoin = ''; saveState(api, st);
      const parts = String(ctx.baseGameVersion || '').split('.').map(n => parseInt(n, 10) || 0);
      if (parts[0] >= 25 || (parts[0] === 1 && parts[1] >= 20)) ctx.gameArgs.push('--quickPlayMultiplayer', target);
      else { const [h, p] = target.split(':'); ctx.gameArgs.push('--server', h); if (p) ctx.gameArgs.push('--port', p); }
      ctx.sendStatus('Aetheria Client: Quick-joining ' + target + '...');
    }
    if (o.cos_dev_overlay) api.appendLog('Synced 250 options | Preset=' + st.activePreset + ' | MC=' + ctx.baseGameVersion);
  });

  api.onGameExit(async ({ code, crashed, baseGameVersion }) => {
    if (!sessionStart) return;
    const sec = Math.max(1, Math.round((Date.now() - sessionStart) / 1000));
    sessionStart = null;
    const st = loadState(api), ver = baseGameVersion || st.selectedMcVersion || 'unknown';
    st.playtime.totalSeconds = (st.playtime.totalSeconds || 0) + sec;
    st.playtime.sessions = (st.playtime.sessions || 0) + 1;
    st.playtime.lastPlayedAt = new Date().toISOString();
    st.playtime.byVersion[ver] = (st.playtime.byVersion[ver] || 0) + sec;
    st.playtime.history = [{ at: st.playtime.lastPlayedAt, version: ver, seconds: sec, crashed: !!crashed, code }, ...(st.playtime.history || [])].slice(0, 20);
    saveState(api, st);
    api.sendToRenderer('ac:state-updated', pack());
  });

  api.registerTab({ id: 'client', label: 'Client', order: 5, html: TAB_HTML, css: TAB_CSS, script: TAB_SCRIPT });
  api.registerWidget({ id: 'aetheria-client-widget', name: 'Aetheria Client', desc: 'Lunar-style quick launch, version & 250-option status', defaultColSpan: 8, defaultRowSpan: 4, minColSpan: 2, maxColSpan: 21, minRowSpan: 1, maxRowSpan: 12, renderJs: WIDGET_JS });
}

module.exports = { activate };
