package dev.aetheria.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aetheria.client.common.ClientConfig;
import dev.aetheria.client.common.ClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.*;

public final class MapEngine {
    private static final int MINI_SZ = 192;
    private static final int WORLD_W = 384, WORLD_H = 256;
    private static final Identifier MINI_TEX = Identifier.fromNamespaceAndPath("aetheria", "minimap_terrain_hq");
    private static final Identifier WORLD_TEX = Identifier.fromNamespaceAndPath("aetheria", "worldmap_terrain_hq");

    private static final Map<Long, int[]> CHUNK_PIXELS = new HashMap<Long, int[]>();
    private static final Map<Long, int[]> CHUNK_HEIGHTS = new HashMap<Long, int[]>();
    private static final Set<Long> SCANNED_STRUCT_CHUNKS = new HashSet<Long>();

    private static DynamicTexture miniDyn = null, worldDyn = null;
    private static String activeCacheKey = "";
    private static long lastTickScanMs = 0L, lastMiniUploadMs = 0L, lastWorldUploadMs = 0L, lastStructUpdateMs = 0L;
    private static double lastMiniX = 1e9, lastMiniZ = 1e9, lastMiniZoom = -1.0;
    private static float lastMiniYaw = 999F;
    private static boolean lastMiniCircle = true;
    private static double lastWmCx = 1e9, lastWmCz = 1e9, lastWmBpp = -1.0;
    private static int cachedSeedChunkX = Integer.MIN_VALUE, cachedSeedChunkZ = Integer.MIN_VALUE;
    private static String cachedSeedStr = "__unset__";

    private MapEngine() {}

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    private static synchronized void ensureContext(Minecraft mc) {
        if (mc == null || mc.level == null) return;
        String dim = mc.level.dimension().identifier().toString();
        String wKey = McBridge.data(mc).worldKey();
        String fullKey = wKey + "|" + dim;
        if (!fullKey.equals(activeCacheKey)) {
            activeCacheKey = fullKey;
            CHUNK_PIXELS.clear();
            CHUNK_HEIGHTS.clear();
            SCANNED_STRUCT_CHUNKS.clear();
            cachedSeedChunkX = Integer.MIN_VALUE;
            cachedSeedChunkZ = Integer.MIN_VALUE;
            lastMiniX = 1e9;
            lastWmCx = 1e9;
        }
    }

    private static void ensureMiniTex(Minecraft mc) {
        if (miniDyn != null || mc == null) return;
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, MINI_SZ, MINI_SZ, false);
        miniDyn = new DynamicTexture(() -> "aetheria_minimap_terrain_hq", ni);
        AetheriaCleanFont.enableLinearFilter(miniDyn);
        mc.getTextureManager().register(MINI_TEX, miniDyn);
    }

    private static void ensureWorldTex(Minecraft mc) {
        if (worldDyn != null || mc == null) return;
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, WORLD_W, WORLD_H, false);
        worldDyn = new DynamicTexture(() -> "aetheria_worldmap_terrain_hq", ni);
        AetheriaCleanFont.enableLinearFilter(worldDyn);
        mc.getTextureManager().register(WORLD_TEX, worldDyn);
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return;
        ensureContext(mc);
        long now = System.currentTimeMillis();
        if (now - lastTickScanMs < 110L) return;
        lastTickScanMs = now;

        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        String dim = mc.level.dimension().identifier().toString();
        String wKey = McBridge.data(mc).worldKey();
        boolean isNether = "minecraft:the_nether".equals(dim);

        int sampled = 0;
        for (int r = 0; r <= 7 && sampled < 10; r++) {
            for (int dx = -r; dx <= r && sampled < 10; dx++) {
                for (int dz = -r; dz <= r && sampled < 10; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long ck = chunkKey(cx, cz);
                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    if (!CHUNK_PIXELS.containsKey(ck)) {
                        sampleChunk(mc, chunk, cx, cz, isNether);
                        sampled++;
                    }
                    if (!SCANNED_STRUCT_CHUNKS.contains(Long.valueOf(ck))) {
                        SCANNED_STRUCT_CHUNKS.add(Long.valueOf(ck));
                        scanChunkStructures(chunk, dim, wKey);
                    }
                }
            }
        }

        if (now - lastStructUpdateMs > 1200L) {
            lastStructUpdateMs = now;
            refreshSeedStructures(mc, pcx, pcz, dim, wKey);
            List<ClientState.StructurePin> vis = ClientState.getVisibleStructures(dim, wKey);
            double px = mc.player.getX(), pz = mc.player.getZ();
            for (int i = 0; i < vis.size(); i++) {
                ClientState.StructurePin sp = vis.get(i);
                if (Math.hypot(sp.x - px, sp.z - pz) <= 36.0) {
                    ClientConfig.get().markStructureVisited(ClientState.structureKey(sp, wKey));
                }
            }
        }
    }

    private static synchronized void sampleChunk(Minecraft mc, LevelChunk chunk, int cx, int cz, boolean isNether) {
        int[] cols = new int[256];
        int[] hgts = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = mc.level.getMinY();

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = (cx << 4) + lx, wz = (cz << 4) + lz;
                int y = isNether ? findNetherSurface(chunk, pos, wx, wz) : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                int rgb = 0x1E222A;
                boolean isWater = false;
                int waterDepth = 0;

                for (int yy = y; yy >= Math.max(minY, y - 16); yy--) {
                    pos.set(wx, yy, wz);
                    BlockState st = chunk.getBlockState(pos);
                    if (st.isAir()) continue;
                    MapColor mcCol = st.getMapColor(mc.level, pos);
                    if (mcCol == null || mcCol.col == 0) continue;
                    rgb = mcCol.col;
                    y = yy;
                    if (!st.getFluidState().isEmpty() && st.getFluidState().toString().toLowerCase().contains("water")) {
                        isWater = true;
                        for (int d = 1; d <= 10 && yy - d >= minY; d++) {
                            pos.set(wx, yy - d, wz);
                            if (chunk.getBlockState(pos).getFluidState().isEmpty()) break;
                            waterDepth++;
                        }
                    }
                    break;
                }

                if (isWater) {
                    int base = rgb;
                    int r = (base >> 16) & 0xFF, g = (base >> 8) & 0xFF, b = base & 0xFF;
                    float factor = Math.max(0.45F, 1.05F - waterDepth * 0.055F);
                    rgb = (Math.min(255, Math.round(r * factor)) << 16)
                        | (Math.min(255, Math.round(g * factor)) << 8)
                        | Math.min(255, Math.round(b * Math.min(1.1F, factor + 0.12F)));
                }

                int idx = (lz << 4) | lx;
                cols[idx] = 0xFF000000 | (rgb & 0x00FFFFFF);
                hgts[idx] = y;
            }
        }
        long ck = chunkKey(cx, cz);
        CHUNK_PIXELS.put(ck, cols);
        CHUNK_HEIGHTS.put(ck, hgts);
        if (CHUNK_PIXELS.size() > 2800) {
            Iterator<Long> it = CHUNK_PIXELS.keySet().iterator();
            for (int k = 0; k < 400 && it.hasNext(); k++) {
                Long rm = it.next();
                it.remove();
                CHUNK_HEIGHTS.remove(rm);
            }
        }
    }

    private static int findNetherSurface(LevelChunk chunk, BlockPos.MutableBlockPos pos, int wx, int wz) {
        boolean sawAir = false;
        for (int y = 104; y >= 16; y--) {
            pos.set(wx, y, wz);
            BlockState st = chunk.getBlockState(pos);
            if (st.isAir()) { sawAir = true; continue; }
            if (sawAir) return y;
        }
        return 64;
    }

    private static void scanChunkStructures(LevelChunk chunk, String dim, String wKey) {
        try {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null) continue;
                String bn = be.getBlockState().getBlock().toString().toLowerCase();
                BlockPos bp = be.getBlockPos();
                if (bn.contains("trial_spawner")) ClientState.addScannedStructure("Trial Chamber", "trial_chamber", bp.getX(), bp.getZ(), dim, wKey);
                else if (bn.contains("spawner")) ClientState.addScannedStructure("minecraft:the_nether".equals(dim) ? "Nether Fortress" : "Dungeon / Spawner", "minecraft:the_nether".equals(dim) ? "fortress" : "stronghold", bp.getX(), bp.getZ(), dim, wKey);
                else if (bn.contains("sculk_shrieker")) ClientState.addScannedStructure("Ancient City", "ancient_city", bp.getX(), bp.getZ(), dim, wKey);
                else if (bn.contains("bell")) ClientState.addScannedStructure("Village", "village", bp.getX(), bp.getZ(), dim, wKey);
                else if (bn.contains("conduit")) ClientState.addScannedStructure("Ocean Monument", "monument", bp.getX(), bp.getZ(), dim, wKey);
            }
        } catch (Throwable ignored) {}
    }

    private static Long resolveSeed(Minecraft mc) {
        String raw = ClientConfig.get().getString("minimap_seed", "").trim();
        if (!raw.isEmpty()) {
            ClientState.setActiveSeedMode("Custom Seed: " + raw);
            try { return Long.valueOf(Long.parseLong(raw)); }
            catch (NumberFormatException e) { return Long.valueOf((long) raw.hashCode()); }
        }
        if (mc.getSingleplayerServer() != null) {
            try {
                long s = mc.getSingleplayerServer().overworld().getSeed();
                ClientState.setActiveSeedMode("Auto Seed (Singleplayer)");
                return Long.valueOf(s);
            } catch (Throwable ignored) {}
        }
        ClientState.setActiveSeedMode("No Seed (Scanned Only)");
        return null;
    }

    private static void refreshSeedStructures(Minecraft mc, int pcx, int pcz, String dim, String wKey) {
        Long seedObj = resolveSeed(mc);
        if (seedObj == null) {
            ClientState.setSeedStructures(Collections.<ClientState.StructurePin>emptyList());
            cachedSeedStr = "";
            return;
        }
        String seedTag = seedObj + "|" + dim;
        if (seedTag.equals(cachedSeedStr) && Math.abs(pcx - cachedSeedChunkX) < 6 && Math.abs(pcz - cachedSeedChunkZ) < 6) {
            return;
        }
        cachedSeedStr = seedTag;
        cachedSeedChunkX = pcx;
        cachedSeedChunkZ = pcz;

        long seed = seedObj.longValue();
        List<ClientState.StructurePin> out = new ArrayList<ClientState.StructurePin>();
        int radiusChunks = 64;

        if ("minecraft:overworld".equals(dim)) {
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 34, 8, 10387312, false, "Village", "village", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 32, 5, 10387313, true, "Ocean Monument", "monument", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 32, 8, 165745296, false, "Pillager Outpost", "outpost", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 80, 20, 10387319, true, "Woodland Mansion", "mansion", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 34, 12, 94251327, false, "Trial Chamber", "trial_chamber", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 24, 8, 20083232, false, "Ancient City", "ancient_city", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 40, 15, 34222645, false, "Ruined Portal", "portal", dim, wKey);
            addSpreadStructures(mc, out, seed, pcx, pcz, radiusChunks, 32, 8, 14357617, false, "Desert/Temple", "temple", dim, wKey);
        } else if ("minecraft:the_nether".equals(dim)) {
            int spacing = 27, sep = 4, salt = 30084232;
            int minR = Math.floorDiv(pcx - radiusChunks, spacing), maxR = Math.floorDiv(pcx + radiusChunks, spacing);
            int minRZ = Math.floorDiv(pcz - radiusChunks, spacing), maxRZ = Math.floorDiv(pcz + radiusChunks, spacing);
            for (int rx = minR; rx <= maxR; rx++) {
                for (int rz = minRZ; rz <= maxRZ; rz++) {
                    Random rng = new Random((long) rx * 341873128712L + (long) rz * 132897987541L + seed + salt);
                    int cx = rx * spacing + rng.nextInt(spacing - sep);
                    int cz = rz * spacing + rng.nextInt(spacing - sep);
                    boolean bastion = rng.nextInt(5) >= 2;
                    out.add(new ClientState.StructurePin(bastion ? "Bastion Remnant" : "Nether Fortress", bastion ? "bastion" : "fortress", (cx << 4) + 8, (cz << 4) + 8, dim, wKey, true));
                }
            }
        } else if ("minecraft:the_end".equals(dim)) {
            int spacing = 20, sep = 11, salt = 10387313;
            int minR = Math.floorDiv(pcx - radiusChunks, spacing), maxR = Math.floorDiv(pcx + radiusChunks, spacing);
            int minRZ = Math.floorDiv(pcz - radiusChunks, spacing), maxRZ = Math.floorDiv(pcz + radiusChunks, spacing);
            for (int rx = minR; rx <= maxR; rx++) {
                for (int rz = minRZ; rz <= maxRZ; rz++) {
                    Random rng = new Random((long) rx * 341873128712L + (long) rz * 132897987541L + seed + salt);
                    int b = spacing - sep;
                    int cx = rx * spacing + ((rng.nextInt(b) + rng.nextInt(b)) / 2);
                    int cz = rz * spacing + ((rng.nextInt(b) + rng.nextInt(b)) / 2);
                    int wx = (cx << 4) + 8, wz = (cz << 4) + 8;
                    if (Math.hypot(wx, wz) >= 1008.0) out.add(new ClientState.StructurePin("End City", "end_city", wx, wz, dim, wKey, true));
                }
            }
        }
        ClientState.setSeedStructures(out);
    }

    private static void addSpreadStructures(Minecraft mc, List<ClientState.StructurePin> out, long seed, int pcx, int pcz, int radChunks,
                                            int spacing, int sep, int salt, boolean tri, String name, String type, String dim, String wKey) {
        int minR = Math.floorDiv(pcx - radChunks, spacing), maxR = Math.floorDiv(pcx + radChunks, spacing);
        int minRZ = Math.floorDiv(pcz - radChunks, spacing), maxRZ = Math.floorDiv(pcz + radChunks, spacing);
        int b = spacing - sep;
        for (int rx = minR; rx <= maxR; rx++) {
            for (int rz = minRZ; rz <= maxRZ; rz++) {
                Random rng = new Random((long) rx * 341873128712L + (long) rz * 132897987541L + seed + salt);
                int ox = tri ? ((rng.nextInt(b) + rng.nextInt(b)) / 2) : rng.nextInt(b);
                int oz = tri ? ((rng.nextInt(b) + rng.nextInt(b)) / 2) : rng.nextInt(b);
                int cx = rx * spacing + ox, cz = rz * spacing + oz;
                int wx = (cx << 4) + 8, wz = (cz << 4) + 8;
                if (mc.level != null && mc.level.getChunkSource().getChunkNow(cx, cz) != null) {
                    String biome = mc.level.getBiome(new BlockPos(wx, 64, wz)).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
                    if ("monument".equals(type) && !biome.contains("ocean")) continue;
                    if ("village".equals(type) && (biome.contains("ocean") || biome.contains("forest") && !biome.contains("taiga"))) continue;
                    if ("mansion".equals(type) && !biome.contains("dark_forest")) continue;
                }
                out.add(new ClientState.StructurePin(name, type, wx, wz, dim, wKey, true));
            }
        }
    }

    private static synchronized int sampleShadedPixel(int wx, int wz) {
        int cx = wx >> 4, cz = wz >> 4;
        long ck = chunkKey(cx, cz);
        int[] cols = CHUNK_PIXELS.get(ck);
        if (cols == null) {
            return (((wx >> 4) + (wz >> 4)) & 1) == 0 ? 0xFF0C0D12 : 0xFF0F1117;
        }
        int idx = ((wz & 15) << 4) | (wx & 15);
        int argb = cols[idx];
        int[] hgts = CHUNK_HEIGHTS.get(ck);
        if (hgts == null) return argb;
        int y = hgts[idx];
        int yn = getH(wx, wz - 1, y);
        int yw = getH(wx - 1, wz, y);
        int diff = Math.max(-28, Math.min(28, (y - yn) * 8 + (y - yw) * 5));
        if (diff == 0) return argb;
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + diff));
        int g = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + diff));
        int b = Math.max(0, Math.min(255, (argb & 0xFF) + diff));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int getH(int wx, int wz, int defY) {
        int[] hgts = CHUNK_HEIGHTS.get(chunkKey(wx >> 4, wz >> 4));
        return hgts != null ? hgts[((wz & 15) << 4) | (wx & 15)] : defY;
    }

    public static void drawMinimap(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int size, boolean circle, double zoom) {
        if (g == null || mc == null || mc.player == null || mc.level == null) return;
        ensureContext(mc);
        ensureMiniTex(mc);
        long now = System.currentTimeMillis();
        double px = mc.player.getX(), pz = mc.player.getZ();
        float yaw = mc.player.getYRot();

        if (now - lastMiniUploadMs >= 75L || Math.abs(px - lastMiniX) > 0.35 || Math.abs(pz - lastMiniZ) > 0.35
                || Math.abs(yaw - lastMiniYaw) > 1.5F || Math.abs(zoom - lastMiniZoom) > 0.02 || circle != lastMiniCircle) {
            lastMiniUploadMs = now;
            lastMiniX = px; lastMiniZ = pz; lastMiniYaw = yaw; lastMiniZoom = zoom; lastMiniCircle = circle;
            NativeImage ni = miniDyn.getPixels();
            if (ni != null) {
                double blocksPerPx = (128.0 / Math.max(0.5, zoom)) / MINI_SZ;
                double yRad = Math.toRadians(yaw), cosY = Math.cos(yRad), sinY = Math.sin(yRad);
                double half = MINI_SZ * 0.5;
                double rSq = (half - 1.0) * (half - 1.0);
                for (int py = 0; py < MINI_SZ; py++) {
                    double dy = (py + 0.5 - half) * blocksPerPx;
                    for (int ppx = 0; ppx < MINI_SZ; ppx++) {
                        double dx = (ppx + 0.5 - half) * blocksPerPx;
                        if (circle) {
                            double d2 = Math.pow(ppx + 0.5 - half, 2) + Math.pow(py + 0.5 - half, 2);
                            if (d2 > rSq) { ni.setPixel(ppx, py, 0); continue; }
                        }
                        int wx = (int) Math.floor(px - dx * cosY + dy * sinY);
                        int wz = (int) Math.floor(pz - dx * sinY - dy * cosY);
                        ni.setPixel(ppx, py, sampleShadedPixel(wx, wz));
                    }
                }
                miniDyn.upload();
            }
        }
        g.blit(RenderPipelines.GUI_TEXTURED, MINI_TEX, x, y, 0.0F, 0.0F, size, size, MINI_SZ, MINI_SZ, MINI_SZ, MINI_SZ, 0xFFFFFFFF);
    }

    public static void drawWorldMap(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h, double centerX, double centerZ, double blocksPerPx) {
        if (g == null || mc == null || mc.level == null) return;
        ensureContext(mc);
        ensureWorldTex(mc);
        long now = System.currentTimeMillis();
        if (now - lastWorldUploadMs >= 90L || Math.abs(centerX - lastWmCx) > 0.25 || Math.abs(centerZ - lastWmCz) > 0.25 || Math.abs(blocksPerPx - lastWmBpp) > 0.005) {
            lastWorldUploadMs = now;
            lastWmCx = centerX; lastWmCz = centerZ; lastWmBpp = blocksPerPx;
            NativeImage ni = worldDyn.getPixels();
            if (ni != null) {
                double scaleX = w / (double) WORLD_W, scaleY = h / (double) WORLD_H;
                double halfW = WORLD_W * 0.5, halfH = WORLD_H * 0.5;
                for (int py = 0; py < WORLD_H; py++) {
                    int wz = (int) Math.floor(centerZ + (py + 0.5 - halfH) * scaleY * blocksPerPx);
                    for (int ppx = 0; ppx < WORLD_W; ppx++) {
                        int wx = (int) Math.floor(centerX + (ppx + 0.5 - halfW) * scaleX * blocksPerPx);
                        ni.setPixel(ppx, py, sampleShadedPixel(wx, wz));
                    }
                }
                worldDyn.upload();
            }
        }
        g.blit(RenderPipelines.GUI_TEXTURED, WORLD_TEX, x, y, 0.0F, 0.0F, w, h, WORLD_W, WORLD_H, WORLD_W, WORLD_H, 0xFFFFFFFF);
    }
}