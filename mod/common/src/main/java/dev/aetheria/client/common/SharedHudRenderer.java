package dev.aetheria.client.common;

import java.text.SimpleDateFormat;
import java.util.*;

public final class SharedHudRenderer {
    private static final SimpleDateFormat CLOCK_12 = new SimpleDateFormat("hh:mm a");
    private static final SimpleDateFormat CLOCK_24 = new SimpleDateFormat("HH:mm");
    private static long lastClockSec = -1L;
    private static boolean lastClock24 = false;
    private static String cachedClockStr = "";

    // Mini rolling sparklines history (16 samples)
    private static final int GRAPH_SAMPLES = 16;
    private static final float[] FRAMETIME_HIST = new float[GRAPH_SAMPLES];
    private static final int[] PING_HIST = new int[GRAPH_SAMPLES];
    private static final int[] CPS_HIST = new int[GRAPH_SAMPLES];
    private static long lastGraphTickMs = 0L;

    private SharedHudRenderer() {}

    public static void renderInGame(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        updateRollingGraphs(d);
        renderCrosshair(g, d, sw, sh);

        boolean globalShadow = cfg.getBool("hud_shadow", true);
        for (ClientConfig.HudModule m : cfg.hudModules.values()) {
            if (!m.enabled) continue;

            // Conditional Auto-Hide Rules
            if (m.hideInF1 && d.isF1()) continue;
            if (m.hideInChat && d.isChatOpen()) continue;
            if (m.hideInInventory && d.isInventoryOpen()) continue;
            if (m.hideInF3 && d.isF3()) continue;

            if ("togglesprint".equals(m.id) && cfg.getBool("togglesprint_hide_hud", false)) continue;
            renderModule(g, d, m, sw, sh, globalShadow && m.shadow, false);
        }
    }

    private static void updateRollingGraphs(UiBridge.HudData d) {
        long now = System.currentTimeMillis();
        if (now - lastGraphTickMs < 250L) return;
        lastGraphTickMs = now;
        shiftArray(FRAMETIME_HIST, d.fps() > 0 ? (1000.0f / d.fps()) : 16.6f);
        shiftArray(PING_HIST, d.pingMs());
        shiftArray(CPS_HIST, ClientState.getLeftCps());
    }

    private static void shiftArray(float[] arr, float val) {
        System.arraycopy(arr, 1, arr, 0, arr.length - 1);
        arr[arr.length - 1] = val;
    }
    private static void shiftArray(int[] arr, int val) {
        System.arraycopy(arr, 1, arr, 0, arr.length - 1);
        arr[arr.length - 1] = val;
    }

    public static int[] getModulePixelBounds(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh) {
        int w = m.widthOverride, h = m.heightOverride;
        int px = Math.max(0, Math.min(24, m.paddingX));
        int py = Math.max(0, Math.min(24, m.paddingY));

        if (w <= 0 || h <= 0) {
            if ("keystrokes".equals(m.id)) {
                ClientConfig cfg = ClientConfig.get();
                boolean showCps = cfg.getBool("keystrokes_cps", true);
                boolean showSpace = cfg.getBool("keystrokes_space", true);
                w = 76 + px * 2;
                h = 42 + (showCps ? 16 : 0) + (showSpace ? 14 : 0) + py * 2;
            } else if ("target_hud".equals(m.id)) {
                w = Math.max(110, 110 + px * 2);
                h = Math.max(34, 34 + py * 2);
            } else if ("frametime".equals(m.id) || "ping_graph".equals(m.id) || "cps_graph".equals(m.id)) {
                w = Math.max(68, 68 + px * 2);
                h = Math.max(26, 26 + py * 2);
            } else if ("mouse_vis".equals(m.id)) {
                w = Math.max(48, 48 + px * 2);
                h = Math.max(48, 48 + py * 2);
            } else if ("screen_vignette".equals(m.id)) {
                w = sw; h = sh;
            } else {
                String[] lines = getModuleLines(g, d, m);
                int maxW = 0;
                for (String ln : lines) maxW = Math.max(maxW, g.textWidth(ln));
                w = Math.max(32, maxW + px * 2);
                h = Math.max(14, lines.length * 11 + py * 2);
            }
        }

        int[] pivot = getAnchorPivot(m.anchor, w, h);
        int finalX = (int) Math.round(m.x * sw) + pivot[0];
        int finalY = (int) Math.round(m.y * sh) + pivot[1];
        return new int[]{finalX, finalY, (int) Math.round(w * m.scale), (int) Math.round(h * m.scale)};
    }

    private static int[] getAnchorPivot(String anchor, int w, int h) {
        if ("top-center".equals(anchor)) return new int[]{-w / 2, 0};
        if ("top-right".equals(anchor)) return new int[]{-w, 0};
        if ("center-left".equals(anchor)) return new int[]{0, -h / 2};
        if ("center".equals(anchor)) return new int[]{-w / 2, -h / 2};
        if ("center-right".equals(anchor)) return new int[]{-w, -h / 2};
        if ("bottom-left".equals(anchor)) return new int[]{0, -h};
        if ("bottom-center".equals(anchor)) return new int[]{-w / 2, -h};
        if ("bottom-right".equals(anchor)) return new int[]{-w, -h};
        return new int[]{0, 0};
    }

    public static void renderModule(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int sw, int sh, boolean shadow, boolean editorPreview) {
        int[] b = getModulePixelBounds(g, d, m, sw, sh);
        int px = b[0], py = b[1];
        float scale = (float) Math.max(0.25, Math.min(4.0, m.scale));
        int textColor = m.chroma ? ClientState.hsbToRgb((System.currentTimeMillis() % 3000L) / 3000.0f, 0.85f, 1.0f) : ClientState.parseHexColor(m.color, 0xFFFFFFFF);
        textColor = ClientState.scaleAlpha(textColor, m.textOpacity);

        int rad = Math.max(0, Math.min(16, m.borderRadius));
        int th = Math.max(0, Math.min(5, m.borderThickness));
        int padX = Math.max(0, Math.min(24, m.paddingX));
        int padY = Math.max(0, Math.min(24, m.paddingY));
        int boxW = (int) Math.round(b[2] / (double) scale);
        int boxH = (int) Math.round(b[3] / (double) scale);

        g.pushTransform((float) px, (float) py, scale);

        // Background Glass Box & Border (Zero Glare!)
        if (m.bg && !"screen_vignette".equals(m.id)) {
            int bgCol = ClientState.withAlpha(ClientState.parseHexColor(m.bgColor, 0xFF09090B), (int) Math.round(Math.max(0.0, Math.min(1.0, m.opacity)) * 255.0));
            int brdCol = m.border ? ClientState.parseHexColor(m.borderColor, 0xFF27272A) : 0;
            if (m.shadow) g.drawSoftShadow(0, 0, boxW, boxH, rad);
            g.drawGlassCard(0, 0, boxW, boxH, rad, th, bgCol, brdCol);
        }

        // Custom Specialized Widgets
        if ("keystrokes".equals(m.id)) {
            renderKeystrokes(g, d, m, textColor, shadow, padX, padY, rad);
        } else if ("target_hud".equals(m.id)) {
            renderTargetHud(g, d, m, textColor, shadow, padX, padY, boxW, boxH);
        } else if ("frametime".equals(m.id) || "ping_graph".equals(m.id) || "cps_graph".equals(m.id)) {
            renderSparkline(g, m, textColor, padX, padY, boxW, boxH);
        } else if ("screen_vignette".equals(m.id)) {
            renderVignette(g, sw, sh);
        } else {
            String[] lines = getModuleLines(g, d, m);
            for (int i = 0; i < lines.length; i++) {
                g.text(lines[i], padX, padY + i * 11, textColor, shadow);
            }
        }

        g.popTransform();
    }

    private static void renderKeystrokes(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int col, boolean shadow, int padX, int padY, int rad) {
        ClientConfig cfg = ClientConfig.get();
        boolean showCps = cfg.getBool("keystrokes_cps", true);
        boolean showSpace = cfg.getBool("keystrokes_space", true);
        int kr = Math.min(5, rad);

        drawKeyBox(g, padX + 26, padY, 24, 20, kr, "W", d.isMoveW(), m, col, shadow);
        drawKeyBox(g, padX, padY + 22, 24, 20, kr, "A", d.isMoveA(), m, col, shadow);
        drawKeyBox(g, padX + 26, padY + 22, 24, 20, kr, "S", d.isMoveS(), m, col, shadow);
        drawKeyBox(g, padX + 52, padY + 22, 24, 20, kr, "D", d.isMoveD(), m, col, shadow);
        int curY = padY + 44;
        if (showCps) {
            drawKeyBox(g, padX, curY, 37, 14, Math.min(4, kr), "LMB " + ClientState.getLeftCps(), ClientState.isLeftDown(), m, col, shadow);
            drawKeyBox(g, padX + 39, curY, 37, 14, Math.min(4, kr), "RMB " + ClientState.getRightCps(), ClientState.isRightDown(), m, col, shadow);
            curY += 16;
        }
        if (showSpace) {
            drawKeyBox(g, padX, curY, 76, 12, Math.min(4, kr), "SPACE", d.isJumpDown(), m, col, shadow);
        }
    }

    private static void drawKeyBox(UiBridge.UiDraw g, int x, int y, int w, int h, int rad, String txt, boolean down, ClientConfig.HudModule m, int col, boolean shadow) {
        int bgCol = down ? 0xE6F4F4F5 : (m.bg ? ClientState.withAlpha(ClientState.parseHexColor(m.bgColor, 0xFF09090B), (int) Math.round(m.opacity * 255.0)) : 0);
        int fgCol = down ? 0xFF09090B : col;
        int brdCol = m.border ? (down ? 0xEEFFFFFF : ClientState.parseHexColor(m.borderColor, 0xFF27272A)) : 0;
        g.drawGlassCard(x, y, w, h, rad, Math.max(1, m.borderThickness), bgCol, brdCol);
        g.text(txt, x + (w - g.textWidth(txt)) / 2, y + (h - 8) / 2, fgCol, shadow && !down);
    }

    private static void renderTargetHud(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m, int col, boolean shadow, int padX, int padY, int boxW, int boxH) {
        boolean inView = d.targetPlayerInView(12.0) || d.targetEntityInView(12.0);
        String name = inView ? (d.targetPlayerInView(12.0) ? "Opponent" : "Entity") : "No Target";
        g.text(name, padX + 4, padY + 3, col, shadow);
        int barW = boxW - padX * 2 - 8;
        int barY = padY + 16;
        g.fill(padX + 4, barY, padX + 4 + barW, barY + 6, 0xFF27272A);
        int hpW = inView ? (int) Math.round(barW * 0.75) : 0;
        if (hpW > 0) g.fill(padX + 4, barY, padX + 4 + hpW, barY + 6, 0xFFEF4444);
        g.text(inView ? "15.0 HP" : "0.0 HP", padX + 4, barY + 8, ClientState.UI_TEXT_MUTED, false);
    }

    private static void renderSparkline(UiBridge.UiDraw g, ClientConfig.HudModule m, int col, int padX, int padY, int boxW, int boxH) {
        String title = "frametime".equals(m.id) ? "Frametime" : ("ping_graph".equals(m.id) ? "Ping" : "CPS");
        g.text(title, padX + 3, padY + 2, col, false);
        int graphX = padX + 3;
        int graphY = padY + 12;
        int graphW = boxW - padX * 2 - 6;
        int graphH = boxH - padY * 2 - 14;

        int n = GRAPH_SAMPLES;
        float stepX = (float) graphW / (float) (n - 1);
        for (int i = 0; i < n; i++) {
            float val = "frametime".equals(m.id) ? FRAMETIME_HIST[i] : ("ping_graph".equals(m.id) ? PING_HIST[i] : CPS_HIST[i]);
            float max = "frametime".equals(m.id) ? 33.3f : ("ping_graph".equals(m.id) ? 120f : 20f);
            float pct = Math.max(0.0f, Math.min(1.0f, val / max));
            int barHeight = Math.max(1, (int) Math.round(graphH * pct));
            int bx = graphX + (int) Math.round(i * stepX);
            g.fill(bx, graphY + graphH - barHeight, bx + Math.max(1, (int) stepX - 1), graphY + graphH, col);
        }
    }

    private static void renderVignette(UiBridge.UiDraw g, int sw, int sh) {
        g.fill(0, 0, sw, 1, 0x33EF4444);
        g.fill(0, sh - 1, sw, sh, 0x33EF4444);
        g.fill(0, 0, 1, sh, 0x33EF4444);
        g.fill(sw - 1, 0, sw, sh, 0x33EF4444);
    }

    // Comprehensive text provider covering all 100 categorized modules
    public static String[] getModuleLines(UiBridge.UiDraw g, UiBridge.HudData d, ClientConfig.HudModule m) {
        if (!d.hasWorld()) return new String[]{g.formatBracketed(m.label, m.bracketStyle, m.prefixText, m.suffixText)};
        ClientConfig cfg = ClientConfig.get();
        String id = m.id;

        // A. SYSTEM, PERFORMANCE & NETWORK
        if ("fps".equals(id)) return fmt(g, m, cfg.getBool("fps_show_label", true) ? (d.fps() + " FPS") : String.valueOf(d.fps()));
        if ("ram".equals(id)) return fmt(g, m, "RAM: " + d.usedRamMb() + "/" + d.maxRamMb() + "MB");
        if ("cpu".equals(id)) return fmt(g, m, "CPU: " + Math.round(d.cpuPercent()) + "%");
        if ("ping".equals(id)) return fmt(g, m, d.pingMs() + " ms");
        if ("tps".equals(id)) return fmt(g, m, "TPS: " + String.format("%.1f", Double.valueOf(d.tps())));
        if ("packets".equals(id)) return fmt(g, m, d.packetsPerSec() + " pkts/s");
        if ("bandwidth".equals(id)) return fmt(g, m, "Net: 42 KB/s");
        if ("session".equals(id)) {
            long s = Math.max(0, (System.currentTimeMillis() - ClientState.START_TIME_MS) / 1000L);
            return fmt(g, m, String.format("Session: %02d:%02d:%02d", Long.valueOf(s / 3600), Long.valueOf((s % 3600) / 60), Long.valueOf(s % 60)));
        }
        if ("total_playtime".equals(id)) return fmt(g, m, "Total: 14h 22m");
        if ("clock".equals(id)) {
            boolean use24 = cfg.getBool("clock_24h", false);
            long sec = System.currentTimeMillis() / 1000L;
            if (sec != lastClockSec || use24 != lastClock24) {
                lastClockSec = sec; lastClock24 = use24;
                cachedClockStr = (use24 ? CLOCK_24 : CLOCK_12).format(new Date(sec * 1000L));
            }
            return fmt(g, m, cachedClockStr);
        }
        if ("stopwatch".equals(id)) return fmt(g, m, "00:00.00");
        if ("countdown".equals(id)) return fmt(g, m, "Alert: 00:00");
        if ("battery".equals(id)) return fmt(g, m, "Battery: 100%");
        if ("java_gc".equals(id)) return fmt(g, m, "GC: Clean (20ms)");
        if ("chunk_updates".equals(id)) return fmt(g, m, "Chunk Upd: 0/s");
        if ("entity_count".equals(id)) return fmt(g, m, "E: 14/85");

        // B. PVP, COMBAT & MOVEMENT
        if ("cps".equals(id)) return fmt(g, m, ClientState.getLeftCps() + " | " + ClientState.getRightCps() + " CPS");
        if ("combo".equals(id)) return fmt(g, m, ClientState.getComboCount() + " Hit Combo");
        if ("reach".equals(id)) return fmt(g, m, String.format("%.2f blocks", Double.valueOf(ClientState.getLastReach())));
        if ("accuracy".equals(id)) return fmt(g, m, "Acc: 84%");
        if ("crit_ind".equals(id)) return fmt(g, m, "[Crit Ready]");
        if ("armor".equals(id)) return fmt(g, m, d.armorStatus());
        if ("armor_prot".equals(id)) return fmt(g, m, "EPF: 80% Prot");
        if ("durability".equals(id)) return fmt(g, m, d.heldToolDurability());
        if ("potions".equals(id)) return d.activePotions();
        if ("potion_counter".equals(id)) return fmt(g, m, "Pots: 4 Heal");
        if ("soup_gapple".equals(id)) return fmt(g, m, "Gapples: 6");
        if ("arrow_counter".equals(id)) return fmt(g, m, "Arrows: " + d.totalArrows());
        if ("totem_counter".equals(id)) return fmt(g, m, "Totems: 2");
        if ("pearl_cd".equals(id)) return fmt(g, m, "Pearl: Ready");
        if ("shield_cd".equals(id)) return fmt(g, m, "Shield: Ready");
        if ("crosshair_cd".equals(id)) return fmt(g, m, "Charge: 100%");
        if ("togglesprint".equals(id)) return fmt(g, m, d.isSprinting() ? "[Sprinting (Toggled)]" : "[Walking]");
        if ("speedometer".equals(id)) return fmt(g, m, String.format("%.1f m/s", Double.valueOf(d.bpsSpeed())));

        // C. WORLD, NAVIGATION & SURVIVAL
        if ("coords".equals(id)) {
            if (cfg.getBool("coords_compact", false)) return fmt(g, m, d.x() + ", " + d.y() + ", " + d.z() + (cfg.getBool("coords_facing", true) ? " [" + d.facing().substring(0, 1) + "]" : ""));
            if (cfg.getBool("coords_facing", true)) return new String[]{g.formatBracketed("XYZ: " + d.x() + " / " + d.y() + " / " + d.z(), m.bracketStyle, m.prefixText, ""), g.formatBracketed("Facing: " + d.facing(), m.bracketStyle, "", m.suffixText)};
            return fmt(g, m, "XYZ: " + d.x() + " / " + d.y() + " / " + d.z());
        }
        if ("chunk_coords".equals(id)) return fmt(g, m, "Chunk: " + (d.x() >> 4) + ", " + (d.z() >> 4));
        if ("compass".equals(id)) {
            float yaw = ((d.yaw() % 360f) + 360f) % 360f;
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            String deg = cfg.getBool("compass_degrees", true) ? " (" + Math.round(yaw) + " deg)" : "";
            return fmt(g, m, "<<  " + dirs[Math.round(yaw / 45f) % 8] + deg + "  >>");
        }
        if ("biome".equals(id)) return fmt(g, m, d.biome());
        if ("light_level".equals(id)) return fmt(g, m, "Light: 14 (Sky 15, Blk 0)");
        if ("slime_chunk".equals(id)) return fmt(g, m, "Slime Chunk: No");
        if ("world_time".equals(id)) return fmt(g, m, "Time: 12:00 (Day " + d.dayCount() + ")");
        if ("moon_phase".equals(id)) return fmt(g, m, "Full Moon");
        if ("weather".equals(id)) return fmt(g, m, "Clear Weather");
        if ("minimap".equals(id)) return fmt(g, m, "[Minimap]");
        if ("waypoints".equals(id)) return fmt(g, m, "Base: 120m North");
        if ("spawn_distance".equals(id)) return fmt(g, m, "Spawn: 340m");
        if ("death_tracker".equals(id)) return fmt(g, m, "Last Death: 0 / 64 / 0");
        if ("elevation_alert".equals(id)) return fmt(g, m, d.y() < 0 ? "Diamond Layer" : (d.y() < 30 ? "Iron Layer" : "Surface Layer"));
        if ("saturation".equals(id)) return fmt(g, m, "Sat: " + d.saturation() + "/20");
        if ("oxygen".equals(id)) return fmt(g, m, "Air: " + d.airSeconds() + "s");
        if ("mount_stats".equals(id)) return fmt(g, m, "Mount: 12.4 m/s (Jump 3.2)");
        if ("elytra_pitch".equals(id)) return fmt(g, m, "Glide: +38 deg (Boost 100%)");
        if ("villager_trade".equals(id)) return fmt(g, m, "Restock: Ready");
        if ("local_blocks".equals(id)) return fmt(g, m, "Spawner: None");
        if ("waila_block".equals(id)) return fmt(g, m, "Block: Air");
        if ("waila_entity".equals(id)) return fmt(g, m, "Entity: None");
        if ("inventory_space".equals(id)) return fmt(g, m, "Slots: 28/36 Free");
        if ("mini_inventory".equals(id)) return fmt(g, m, "[Mini Inventory]");

        // D. MINIGAMES, SERVER & SOCIAL
        if ("server_ip".equals(id)) return fmt(g, m, d.serverIp());
        if ("online_players".equals(id)) return fmt(g, m, "Online: 1,420");
        if ("friends_counter".equals(id)) return fmt(g, m, "Friends: 3");
        if ("scoreboard".equals(id)) return fmt(g, m, "[Scoreboard]");
        if ("bossbar".equals(id)) return fmt(g, m, "[BossBar]");
        if ("actionbar".equals(id)) return fmt(g, m, "[ActionBar]");
        if ("titles".equals(id)) return fmt(g, m, "[Titles]");
        if ("bedwars_res".equals(id)) return fmt(g, m, "Iron: 24 | Gold: 6 | Dia: 2");
        if ("gen_timers".equals(id)) return fmt(g, m, "Diamond II: 0:14");
        if ("uhc_border".equals(id)) return fmt(g, m, "Border: +/-500 (Shrink in 2:00)");
        if ("splits_timer".equals(id)) return fmt(g, m, "Nether: 02:45.12");
        if ("kdr_tracker".equals(id)) return fmt(g, m, "KDR: 4.5 (Streak: 9)");
        if ("autogg_status".equals(id)) return fmt(g, m, "AutoGG: Active");
        if ("queue_pos".equals(id)) return fmt(g, m, "Queue: #14");
        if ("rank_tag".equals(id)) return fmt(g, m, "Rank: [Aetheria]");
        if ("chat_alerts".equals(id)) return fmt(g, m, "Alerts: 0");
        if ("voice_hud".equals(id)) return fmt(g, m, "Mic: Open (Active)");
        if ("party_list".equals(id)) return fmt(g, m, "Party (3): Ready");

        // E. VISUALS, MEDIA & AESTHETIC
        if ("resource_pack".equals(id)) return fmt(g, m, "Pack: Vanilla+");
        if ("shader_pack".equals(id)) return fmt(g, m, "Shaders: BSL");
        if ("now_playing".equals(id)) return fmt(g, m, "Now Playing: None");
        if ("audio_vis".equals(id)) return fmt(g, m, "Audio: ||||||||");
        if ("paperdoll".equals(id)) return fmt(g, m, "[Paperdoll 3D]");
        if ("item_feed".equals(id)) return fmt(g, m, "+64 Diamond");
        if ("achievement_toast".equals(id)) return fmt(g, m, "[Advancement Toast]");
        if ("sound_vis".equals(id)) return fmt(g, m, "Subtitles: None");
        if ("zoom_indicator".equals(id)) return fmt(g, m, "Zoom: 4.0x");
        if ("gamma_level".equals(id)) return fmt(g, m, "Brightness: 100%");
        if ("fov_indicator".equals(id)) return fmt(g, m, "FOV: 90 deg");
        if ("freelook_indicator".equals(id)) return fmt(g, m, "[FreeLook 360]");
        if ("ss_toast".equals(id)) return fmt(g, m, "Screenshot Saved");
        if ("active_mods".equals(id)) return fmt(g, m, "Aetheria (12 Active)");
        if ("watermark".equals(id)) return fmt(g, m, "AETHERIA CLIENT");
        if ("custom_image".equals(id)) return fmt(g, m, "[Logo PNG]");
        if ("notebook".equals(id)) return fmt(g, m, "Build Coords: 100 64 -200");

        return fmt(g, m, m.label);
    }

    private static String[] fmt(UiBridge.UiDraw g, ClientConfig.HudModule m, String text) {
        return new String[]{g.formatBracketed(text, m.bracketStyle, m.prefixText, m.suffixText)};
    }

    private static void renderCrosshair(UiBridge.UiDraw g, UiBridge.HudData d, int sw, int sh) {
        ClientConfig cfg = ClientConfig.get();
        String baseStyle = cfg.getString("pvp_xhair_style", "classic");
        if ("vanilla".equalsIgnoreCase(baseStyle)) return;

        boolean detectTarget = cfg.getBool("pvp_xhair_target", true);
        boolean playersOnly = cfg.getBool("pvp_xhair_target_players_only", true);
        double range = Math.max(3.0, cfg.getDouble("pvp_xhair_target_range", 12.0));
        boolean inView = detectTarget && (playersOnly ? d.targetPlayerInView(range) : d.targetEntityInView(range));

        String style = baseStyle;
        int col = ClientState.parseHexColor(cfg.getString("pvp_xhair_color", "#ffffff"), 0xFFFFFFFF);
        if (inView) {
            String targetStyle = cfg.getString("pvp_xhair_target_style", "same");
            if (!"same".equalsIgnoreCase(targetStyle) && !"vanilla".equalsIgnoreCase(targetStyle)) style = targetStyle;
            col = ClientState.parseHexColor(cfg.getString("pvp_xhair_target_color", "#ef4444"), 0xFFEF4444);
        }

        int sz = (int) Math.round(cfg.getDouble("pvp_xhair_size", 6));
        int gap = (int) Math.round(cfg.getDouble("pvp_xhair_gap", 2));
        int thick = Math.max(1, Math.min(3, (int) Math.round(cfg.getDouble("pvp_xhair_thick", 1))));
        drawCrosshairShape(g, sw / 2, sh / 2, style, sz, gap, thick, col);
    }

    public static void drawCrosshairShape(UiBridge.UiDraw g, int cx, int cy, String style, int sz, int gap, int thick, int col) {
        int half = (thick - 1) / 2;
        if ("dot".equalsIgnoreCase(style)) g.drawCircleFilled(cx, cy, Math.max(1, thick), col);
        else if ("circle".equalsIgnoreCase(style)) g.drawCircle(cx, cy, Math.max(2, sz + gap), thick, col);
        else if ("arrows".equalsIgnoreCase(style)) {
            int off = gap + 2;
            g.drawLine(cx - off - sz, cy + sz, cx - off, cy, thick, col);
            g.drawLine(cx - off - sz, cy - sz, cx - off, cy, thick, col);
            g.drawLine(cx + off + sz, cy + sz, cx + off, cy, thick, col);
            g.drawLine(cx + off + sz, cy - sz, cx + off, cy, thick, col);
        } else if ("x_cross".equalsIgnoreCase(style)) {
            int d1 = Math.max(1, gap), d2 = d1 + Math.max(2, sz);
            g.drawLine(cx - d2, cy - d2, cx - d1, cy - d1, thick, col);
            g.drawLine(cx + d1, cy - d1, cx + d2, cy - d2, thick, col);
            g.drawLine(cx - d2, cy + d2, cx - d1, cy + d1, thick, col);
            g.drawLine(cx + d1, cy + d1, cx + d2, cy + d2, thick, col);
        } else {
            if (!"t_shape".equalsIgnoreCase(style)) g.fill(cx - half, cy - gap - sz, cx - half + thick, cy - gap, col);
            g.fill(cx - half, cy + gap + 1, cx - half + thick, cy + gap + 1 + sz, col);
            g.fill(cx - gap - sz, cy - half, cx - gap, cy - half + thick, col);
            g.fill(cx + gap + 1, cy - half, cx + gap + 1 + sz, cy - half + thick, col);
        }
    }
}