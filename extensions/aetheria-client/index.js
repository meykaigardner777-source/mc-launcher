'use strict';
const fs = require('fs');
const path = require('path');
const net = require('net');
const crypto = require('crypto');

const DEFAULT_GITHUB_RAW_URL = '';
const DEFAULT_MOD_JAR_URL = '';

const SUPPORTED_MOD_TARGETS = [
  { key: '26.3',    label: 'MC 26.3 (Fabric, Java 25, SDL3)' },
  { key: '26.2',    label: 'MC 26.2 (Fabric, Java 25)' },
  { key: '26.1',    label: 'MC 26.1 - 26.1.3 (Fabric, Java 25)' },
  { key: '1.21.11', label: 'MC 1.21.11 (Fabric, Java 21)' },
  { key: '1.21.1',  label: 'MC 1.21 - 1.21.1 (Fabric, Java 21)' },
  { key: '1.8.9',   label: 'MC 1.8.9 (Legacy Fabric, Java 8)' }
];

const DEFAULT_SERVERS = [
  { name: 'Hypixel Network', ip: 'mc.hypixel.net', note: 'Minigames / BedWars' },
  { name: 'Minemen Club', ip: 'na.minemen.club', note: 'Practice PvP' },
  { name: 'MCC Island', ip: 'play.mccisland.net', note: 'Modern 1.20+ Minigames' },
  { name: '2b2t', ip: '2b2t.org', note: 'Anarchy Survival' },
  { name: 'DonutSMP', ip: 'donutsmp.net', note: 'Hardcore SMP' }
];

const DEFAULT_HUD_LAYOUT = {
  fps:          { label: 'FPS Counter',     optId: 'hud_fps',          sample: '185 FPS',               x: 0.015, y: 0.025, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-left' },
  coords:       { label: 'Coordinates',     optId: 'hud_coords',       sample: 'XYZ: 142 / 64 / -88\nFacing: North', x: 0.015, y: 0.085, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-left' },
  biome:        { label: 'Biome',           optId: 'hud_biome',        sample: 'Cherry Grove',          x: 0.015, y: 0.175, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#a5f3fc', anchor: 'top-left' },
  ping:         { label: 'Live Ping',       optId: 'pvp_ping_hud',     sample: '24 ms',                 x: 0.015, y: 0.230, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#86efac', anchor: 'top-left' },
  cps:          { label: 'CPS Counter',     optId: 'pvp_cps',          sample: '12 | 9 CPS',            x: 0.015, y: 0.285, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-left' },
  keystrokes:   { label: 'Keystrokes',      optId: 'pvp_keystrokes',   sample: 'W\nA S D\n[SPACE]',      x: 0.015, y: 0.345, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-left' },
  togglesprint: { label: 'ToggleSprint',    optId: 'pvp_togglesprint', sample: '[Sprinting (Toggled)]', x: 0.015, y: 0.545, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#fde68a', anchor: 'top-left' },
  potions:      { label: 'Potion Effects',  optId: 'pvp_potion_hud',   sample: 'Speed II 1:42',         x: 0.015, y: 0.615, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#c4b5fd', anchor: 'top-left' },
  compass:      { label: 'Compass Strip',   optId: 'hud_compass',      sample: '<<  N (180)  >>',       x: 0.340, y: 0.020, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-center' },
  clock:        { label: 'System Clock',    optId: 'hud_clock',        sample: '10:42 PM',              x: 0.855, y: 0.025, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-right' },
  session:      { label: 'Session Timer',   optId: 'hud_session',      sample: 'Session: 00:41:18',     x: 0.835, y: 0.080, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'top-right' },
  server_ip:    { label: 'Server IP',       optId: 'hud_srv_ip',       sample: 'mc.hypixel.net',        x: 0.840, y: 0.135, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#93c5fd', anchor: 'top-right' },
  day_count:    { label: 'Day Counter',     optId: 'hud_day_count',    sample: 'Day 42',                x: 0.875, y: 0.190, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#fde047', anchor: 'top-right' },
  durability:   { label: 'Tool Durability', optId: 'hud_durability',   sample: 'Sword: 1492/1561',      x: 0.825, y: 0.650, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'bottom-right' },
  armor:        { label: 'Armor Status',    optId: 'pvp_armor_hud',    sample: 'H: 96% | C: 91%',       x: 0.790, y: 0.720, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#ffffff', anchor: 'bottom-right' },
  combo:        { label: 'Combo Counter',   optId: 'pvp_combo_hud',    sample: '4 Hit Combo',           x: 0.435, y: 0.600, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#fca5a5', anchor: 'center' },
  reach:        { label: 'Reach Display',   optId: 'pvp_reach_hud',    sample: '2.84 blocks',           x: 0.435, y: 0.660, scale: 1.0, opacity: 0.85, bg: true, border: true, chroma: false, color: '#fca5a5', anchor: 'center' }
};

const MOD_OPTIONS_CATS = [
  ['HUD Modules', [
    ['hud_fps', 'FPS Counter', 'b', true, 0, 'Displays live framerate'],
    ['hud_coords', 'Coordinates HUD', 'b', true, 0, 'X / Y / Z and facing direction'],
    ['hud_biome', 'Biome Display', 'b', true, 0, 'Shows current biome name'],
    ['hud_compass', 'Compass Strip', 'b', true, 0, 'Top-center N/E/S/W bar'],
    ['hud_clock', 'System Clock HUD', 'b', false, 0, 'Local time overlay in-game'],
    ['hud_session', 'Session Timer HUD', 'b', true, 0, 'Elapsed current session clock'],
    ['hud_srv_ip', 'Server IP HUD', 'b', false, 0, 'Displays active server IP'],
    ['hud_day_count', 'World Day Counter', 'b', false, 0, 'Displays current Minecraft day'],
    ['hud_durability', 'Tool Durability HUD', 'b', true, 0, 'Exact uses left on held gear'],
    ['hud_snap_grid', 'HUD Editor Snap Grid', 'b', true, 0, 'Snaps HUD widgets cleanly in-game'],
    ['hud_shadow', 'HUD Text Drop Shadow', 'b', true, 0, 'Crisp shadow behind HUD labels']
  ]],
  ['PvP & Combat', [
    ['pvp_keystrokes', 'Keystrokes HUD', 'b', true, 0, 'WASD, Space & LMB/RMB overlay'],
    ['pvp_cps', 'CPS Counter', 'b', true, 0, 'Live Left/Right clicks per second'],
    ['pvp_togglesprint', 'ToggleSprint', 'b', true, 0, 'Automatic sprint while moving forward'],
    ['pvp_armor_hud', 'Armor Status HUD', 'b', true, 0, 'Shows equipped armor durability %'],
    ['pvp_potion_hud', 'Potion Effects HUD', 'b', true, 0, 'Compact active potion timers'],
    ['pvp_ping_hud', 'Live Ping HUD', 'b', true, 0, 'Shows server latency in ms'],
    ['pvp_combo_hud', 'Combo Counter', 'b', false, 0, 'Tracks consecutive melee hits'],
    ['pvp_reach_hud', 'Reach Indicator', 'b', false, 0, 'Last hit distance in blocks'],
    ['pvp_low_fire', 'Low Fire Overlay', 'b', true, 0, 'Lowers first-person fire flames'],
    ['pvp_xhair_style', 'Crosshair Shape', 's', 'classic', ['vanilla', 'classic', 'dot', 'circle', 't_shape'], 'Custom in-game crosshair style'],
    ['pvp_xhair_size', 'Crosshair Size', 'r', 6, [2, 16, 1], 'Crosshair arm length (px)'],
    ['pvp_xhair_gap', 'Crosshair Gap', 'r', 2, [0, 8, 1], 'Center spacing in pixels']
  ]],
  ['Camera & Controls', [
    ['cam_zoom', 'Optical Zoom', 'b', true, 0, 'Hold zoom key for telescopic FOV'],
    ['cam_zoom_fac', 'Zoom Magnification', 'r', 4, [2, 12, 1], 'Optical zoom multiplier'],
    ['cam_zoom_ease', 'Smooth Zoom Easing', 'b', true, 0, 'Interpolates zoom smoothly'],
    ['cam_zoom_slow', 'Slow Mouse on Zoom', 'b', true, 0, 'Lowers look sensitivity while zoomed'],
    ['cam_zoom_key', 'Zoom Keybind', 's', 'C', ['C', 'V', 'Z', 'X', 'G', 'R', 'ALT'], 'Keyboard trigger for zoom'],
    ['ctl_menu_key', 'In-Game Menu Key', 's', 'RSHIFT', ['RSHIFT', 'M', 'H', 'INSERT', 'K', 'P'], 'Opens Aetheria Client menu in-game']
  ]]
];

const OPTIONS_CATALOG = [], DEFAULT_OPTIONS_MAP = {};
for (const [cat, items] of MOD_OPTIONS_CATS) {
  for (const [id, label, type, def, extra, desc] of items) {
    OPTIONS_CATALOG.push({ id, label, category: cat, type, default: def, extra, desc });
    DEFAULT_OPTIONS_MAP[id] = def;
  }
}

const MOD_PRESETS = {
  pvp: { id: 'pvp', name: 'PvP Competitive', desc: 'Low Fire, Keystrokes, CPS, ToggleSprint, Armor & Ping HUD', ov: { pvp_low_fire: true, pvp_keystrokes: true, pvp_cps: true, pvp_togglesprint: true, pvp_armor_hud: true, pvp_ping_hud: true, pvp_combo_hud: true, pvp_reach_hud: true, pvp_xhair_style: 'classic' } },
  clean: { id: 'clean', name: 'Clean Vanilla+', desc: 'Minimal FPS, Coords & Zoom with vanilla crosshair', ov: { hud_fps: true, hud_coords: true, hud_biome: false, pvp_keystrokes: false, pvp_cps: false, pvp_armor_hud: false, pvp_potion_hud: false, pvp_xhair_style: 'vanilla', cam_zoom: true } },
  smp: { id: 'smp', name: 'Survival & SMP', desc: 'Coords, Biome, Durability, Day Counter & Session Timer', ov: { hud_coords: true, hud_biome: true, hud_durability: true, hud_day_count: true, hud_session: true, hud_compass: true, pvp_combo_hud: false, pvp_reach_hud: false } }
};

const cloneDefaultHud = () => JSON.parse(JSON.stringify(DEFAULT_HUD_LAYOUT));
const getMetaFile = api => path.join(api.paths.appDir, 'aetheria-client-meta.json');
const getProfileModConfigFile = profileDir => path.join(profileDir, 'config', 'aetheria-client.json');

function sha1File(fp) {
  try { return crypto.createHash('sha1').update(fs.readFileSync(fp)).digest('hex'); }
  catch (e) { return ''; }
}

function loadMeta(api) {
  const fp = getMetaFile(api);
  const base = { activePreset: 'custom', autoDeployMod: true, pendingQuickJoin: '', githubRawUrl: DEFAULT_GITHUB_RAW_URL, modJarUrl: DEFAULT_MOD_JAR_URL, servers: DEFAULT_SERVERS.map(s => ({ ...s })), friends: [{ name: 'Notch', note: 'Classic Builder' }], playtime: { totalSeconds: 0, sessions: 0, lastPlayedAt: null, byVersion: {}, history: [] } };
  if (fs.existsSync(fp)) try { const p = JSON.parse(fs.readFileSync(fp, 'utf8')); return { ...base, ...p, servers: Array.isArray(p.servers) ? p.servers : base.servers, friends: Array.isArray(p.friends) ? p.friends : base.friends, playtime: { ...base.playtime, ...(p.playtime || {}) } }; } catch (e) {}
  return base;
}

function saveMeta(api, meta) { api.utils.safeMkdirSync(api.paths.appDir); fs.writeFileSync(getMetaFile(api), JSON.stringify(meta, null, 2)); }

function loadModConfig(api, profileDir) {
  const fp = getProfileModConfigFile(profileDir), hud = cloneDefaultHud(), opts = { ...DEFAULT_OPTIONS_MAP };
  let updatedAt = 0;
  if (fs.existsSync(fp)) try {
    const disk = JSON.parse(fs.readFileSync(fp, 'utf8'));
    updatedAt = Number(disk.updatedAt || 0);
    if (disk.options && typeof disk.options === 'object') Object.assign(opts, disk.options);
    if (disk.hudModules && typeof disk.hudModules === 'object') {
      for (const [k, m] of Object.entries(disk.hudModules)) {
        if (!m || typeof m !== 'object') continue;
        hud[k] = { ...(hud[k] || { label: m.label || k, sample: m.label || k }), ...m };
        if (hud[k].optId && typeof m.enabled === 'boolean' && !(hud[k].optId in (disk.options || {}))) opts[hud[k].optId] = m.enabled;
      }
    }
  } catch (e) {}
  return { schemaVersion: 2, updatedAt, menuKey: opts.ctl_menu_key || 'RSHIFT', hudLayout: hud, options: opts };
}

function saveModConfig(api, profileDir, modCfg) {
  const fp = getProfileModConfigFile(profileDir);
  api.utils.safeMkdirSync(path.dirname(fp));
  let disk = {};
  if (fs.existsSync(fp)) try { disk = JSON.parse(fs.readFileSync(fp, 'utf8')) || {}; } catch (e) {}
  const existingHud = (disk.hudModules && typeof disk.hudModules === 'object') ? disk.hudModules : {};
  const hudModules = { ...existingHud };
  for (const [k, m] of Object.entries(modCfg.hudLayout || {})) {
    const enabled = m.optId ? !!modCfg.options[m.optId] : (m.enabled !== false);
    hudModules[k] = {
      ...(existingHud[k] || {}),
      label: m.label || k,
      optId: m.optId || null,
      enabled,
      x: Number(m.x ?? 0.02),
      y: Number(m.y ?? 0.02),
      scale: Number(m.scale ?? 1.0),
      opacity: Number(m.opacity ?? 0.85),
      bg: m.bg !== false,
      border: m.border !== false,
      chroma: !!m.chroma,
      color: m.color || '#ffffff',
      anchor: m.anchor || 'top-left'
    };
  }
  const payload = {
    ...disk,
    schemaVersion: disk.schemaVersion || 2,
    updatedAt: Date.now(),
    menuKey: modCfg.options.ctl_menu_key || 'RSHIFT',
    hudModules,
    options: { ...(disk.options || {}), ...(modCfg.options || {}) }
  };
  fs.writeFileSync(fp, JSON.stringify(payload, null, 2));
  return { ...modCfg, updatedAt: payload.updatedAt, menuKey: payload.menuKey };
}

const apiExtractVer = str => { const m = String(str || '').match(/(?:fabric-loader-[^-]+-|neoforge-|forge-)?([0-9]+\.[0-9]+(?:\.[0-9]+)?)/); return m ? m[1] : String(str || ''); };

function resolveTargetKeyForMcVersion(mcVer, jars = {}) {
  const c = apiExtractVer(mcVer);
  if (c && jars[c]) return c;
  if (c === '1.21' && jars['1.21.1']) return '1.21.1';
  const parts = c.split('.');
  if (parts.length >= 2) {
    const mm = `${parts[0]}.${parts[1]}`;
    if (jars[mm]) return mm;
  }
  if (c === '1.8.9') return '1.8.9';
  if (c === '1.21' || c === '1.21.1') return '1.21.1';
  if (c === '1.21.11') return '1.21.11';
  if (c.startsWith('26.1')) return '26.1';
  if (c.startsWith('26.2')) return '26.2';
  if (c.startsWith('26.3')) return '26.3';
  return null;
}

function findAvailableBundledJars(api) {
  const out = {};
  for (const dir of [path.join(api.extensionDir, 'mods'), api.extensionDir]) {
    if (!fs.existsSync(dir)) continue;
    try {
      for (const f of fs.readdirSync(dir)) {
        if (!/^aetheria-client-mod.*\.jar$/i.test(f)) continue;
        const m = f.match(/aetheria-client-mod-([0-9]+\.[0-9]+(?:\.[0-9]+)?)\.jar$/i);
        if (m) out[m[1]] = path.join(dir, f);
        else if (!out.generic) out.generic = path.join(dir, f);
      }
    } catch (e) {}
  }
  return out;
}

function deployModJarForProfile(api, prof, profileDir, meta) {
  const mDir = path.join(profileDir, 'mods');
  api.utils.safeMkdirSync(mDir);
  const destActive = path.join(mDir, 'aetheria-client-mod.jar');
  const isFabric = String(prof?.loader || 'vanilla').toLowerCase() === 'fabric' || String(prof?.version || '').toLowerCase().includes('fabric');
  if (!isFabric || meta.autoDeployMod === false) return { deployed: false, reason: isFabric ? 'Auto-deploy disabled.' : 'Active profile is not Fabric.' };
  const jars = findAvailableBundledJars(api);
  const targetKey = resolveTargetKeyForMcVersion(prof?.version || '', jars);
  const srcJar = (targetKey && jars[targetKey]) || (targetKey === '26.2' && jars.generic) || null;
  if (!srcJar || !fs.existsSync(srcJar)) return { deployed: false, targetKey, reason: targetKey ? `No prebuilt JAR found for MC ${targetKey}.` : `Unsupported MC version for mod: ${apiExtractVer(prof?.version)}` };

  // Clean up any leftover version-named duplicates so Fabric never loads an old 1.1.0 jar
  try {
    for (const f of fs.readdirSync(mDir)) {
      if (/^aetheria-client-mod-.+\.jar(\.disabled)?$/i.test(f)) {
        try { fs.unlinkSync(path.join(mDir, f)); } catch (e) {}
      }
    }
  } catch (e) {}

  const stampFile = path.join(mDir, '.aetheria-client-version');
  const prevKey = fs.existsSync(stampFile) ? fs.readFileSync(stampFile, 'utf8').trim() : '';
  const srcHash = sha1File(srcJar);
  const destHash = fs.existsSync(destActive) ? sha1File(destActive) : '';
  if (!destHash || prevKey !== targetKey || destHash !== srcHash) {
    fs.copyFileSync(srcJar, destActive);
    fs.writeFileSync(stampFile, targetKey || 'generic');
  }
  return { deployed: true, targetKey, fileName: path.basename(srcJar) };
}

function writeVarInt(val) { const b = []; let v = val >>> 0; while (true) { if ((v & ~0x7f) === 0) { b.push(v); break; } b.push((v & 0x7f) | 0x80); v >>>= 7; } return Buffer.from(b); }
function readVarInt(buf, off = 0) { let n = 0, res = 0, byte = 0; do { if (off + n >= buf.length) return null; byte = buf[off + n]; res |= (byte & 0x7f) << (7 * n++); if (n > 5) throw new Error('VarInt'); } while ((byte & 0x80) !== 0); return { value: res, size: n }; }
const packSlp = (id, data) => { const i = writeVarInt(id); return Buffer.concat([writeVarInt(i.length + data.length), i, data]); };
const parseMotd = d => !d ? '' : typeof d === 'string' ? d.replace(/\u00a7./gi, '').replace(/§./g, '').trim() : ((d.text || '') + (Array.isArray(d.extra) ? d.extra.map(parseMotd).join('') : '')).replace(/§./g, '').trim();

function pingServer(addr, timeoutMs = 3500) {
  return new Promise(resolve => {
    const [host, rawPort] = String(addr || '').trim().split(':'), port = parseInt(rawPort, 10) || 25565;
    if (!host) return resolve({ online: false, error: 'Empty host' });
    const t0 = Date.now(); let done = false, chunks = Buffer.alloc(0);
    const sock = net.createConnection({ host, port });
    const end = r => { if (!done) { done = true; try { sock.destroy(); } catch (e) {} resolve(r); } };
    sock.setTimeout(timeoutMs);
    sock.on('timeout', () => end({ online: false, error: 'Timeout' }));
    sock.on('error', err => end({ online: false, error: err.code || err.message }));
    sock.on('connect', () => { const hb = Buffer.from(host, 'utf8'), pb = Buffer.alloc(2); pb.writeUInt16BE(port, 0); sock.write(packSlp(0, Buffer.concat([writeVarInt(765), writeVarInt(hb.length), hb, pb, writeVarInt(1)]))); sock.write(packSlp(0, Buffer.alloc(0))); sock._lat = Date.now() - t0; });
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

function listLocalMods(dir) {
  const mDir = path.join(dir, 'mods');
  if (!fs.existsSync(mDir)) return [];
  try { return fs.readdirSync(mDir).filter(f => f.endsWith('.jar') || f.endsWith('.jar.disabled')).map(f => ({ fileName: f, name: f.replace(/\.disabled$/, ''), enabled: !f.endsWith('.disabled'), sizeMb: (fs.statSync(path.join(mDir, f)).size / 1048576).toFixed(2) })); } catch (e) { return []; }
}

const TAB_HTML = `<div class="ac-root">
  <div class="ac-hero card">
    <div class="ac-hero-left"><div class="ac-emblem">✦</div><div>
      <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;"><h2 style="font-size:1.2rem;font-weight:800;">Aetheria Client Mod</h2><span class="brand-badge" id="ac-prof-badge">PROFILE</span><span class="brand-badge" id="ac-ver-badge">MC -</span><span class="brand-badge" id="ac-jar-badge">MOD JAR: CHECKING</span></div>
      <p style="font-size:0.75rem;color:var(--text-muted);margin-top:3px;">Per-profile mod settings manager (<code>config/aetheria-client.json</code>) • Hot-reloads in-game • Never overwrites your profile or <code>options.txt</code></p>
    </div></div>
    <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;"><button class="btn-secondary" id="ac-refresh-prof" style="padding:8px 12px;">↻ Sync Active Profile</button><button class="btn" id="ac-launch-btn" style="padding:10px 18px;font-weight:800;">▶ LAUNCH PROFILE</button></div>
  </div>
  <div class="chip-group" id="ac-subnav" style="margin-bottom:0;">
    <button class="chip-btn active" data-sub="mods">Mod Settings</button><button class="chip-btn" data-sub="hud">HUD Positions &amp; Style</button><button class="chip-btn" data-sub="presets">Presets &amp; Preinstalled JARs</button><button class="chip-btn" data-sub="servers">Servers</button><button class="chip-btn" data-sub="social">Friends</button><button class="chip-btn" data-sub="playtime">Playtime &amp; GitHub</button>
  </div>
  <div class="ac-sub active" id="ac-sub-mods">
    <div class="card flex-between" style="padding:12px;margin-bottom:0;flex-wrap:wrap;"><input type="text" class="input-field" id="ac-search" placeholder="Search mod options..." style="flex:1;min-width:220px;" /><div style="display:flex;gap:8px;"><button class="btn-secondary" id="ac-reload-cfg">Reload From Disk</button><button class="btn-secondary" id="ac-reset-btn">Reset Mod Config</button></div></div>
    <div class="chip-group" id="ac-cats" style="margin-bottom:0;"></div><div class="ac-grid" id="ac-opts-grid"></div>
  </div>
  <div class="ac-sub" id="ac-sub-hud">
    <div class="card flex-between" style="padding:12px;margin-bottom:0;flex-wrap:wrap;"><div><strong style="font-size:0.9rem;">Interactive HUD Position &amp; Style Editor</strong><span style="display:block;font-size:0.73rem;color:var(--text-muted);">Edits save straight to active profile <code>config/aetheria-client.json</code>.</span></div><div style="display:flex;gap:8px;flex-wrap:wrap;"><button class="btn-secondary" id="ac-hud-pull">Pull In-Game Edits</button><button class="btn-secondary" id="ac-hud-reset">Reset Positions</button></div></div>
    <div class="ac-hud-wrap"><div class="card" style="padding:12px;margin-bottom:0;"><div class="ac-stage" id="ac-hud-stage"><div class="ac-stage-grid"></div><div class="ac-xhair-preview" id="ac-xhair-preview"></div><div class="ac-hotbar-mock"><span>1</span><span>2</span><span>3</span><span>4</span><span>5</span><span>6</span><span>7</span><span>8</span><span>9</span></div></div></div><div class="card" style="padding:14px;margin-bottom:0;display:flex;flex-direction:column;gap:10px;" id="ac-hud-inspector"></div></div>
  </div>
  <div class="ac-sub" id="ac-sub-presets">
    <div class="card" style="margin-bottom:0;">
      <div class="flex-between" style="margin-bottom:10px;flex-wrap:wrap;"><div><strong style="font-size:0.9rem;">Preinstalled Multi-Version Mod JARs</strong><span id="ac-jar-status" style="display:block;font-size:0.73rem;color:var(--text-muted);">Checking...</span></div><div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap;"><button class="ac-pill" id="ac-autodeploy-btn">Auto-Deploy: ON</button><button class="btn-secondary" id="ac-deploy-jar">Deploy Matching JAR Now</button></div></div>
      <div id="ac-supported-list" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:6px;margin-bottom:10px;"></div>
      <div style="display:flex;gap:8px;flex-wrap:wrap;"><input type="text" id="ac-mod-url" class="input-field" placeholder="Optional: https://github.com/.../aetheria-client-mod-26.2.jar" style="flex:1;min-width:240px;" /><button class="btn" id="ac-dl-jar">Download Release JAR</button></div>
    </div>
    <div class="card" style="margin-bottom:0;"><strong style="font-size:0.9rem;display:block;margin-bottom:10px;">1-Click Mod Presets (Mod Config Only)</strong><div class="ac-pgrid" id="ac-presets-grid"></div></div>
    <div class="card" style="margin-bottom:0;"><div class="flex-between" style="margin-bottom:8px;"><span style="font-size:0.78rem;color:var(--text-muted);font-weight:600;">Active Profile /mods</span><button class="btn-secondary" id="ac-ref-mods" style="padding:3px 9px;font-size:0.7rem;">Refresh</button></div><div id="ac-local-mods" style="display:flex;flex-direction:column;gap:6px;"></div></div>
  </div>
  <div class="ac-sub" id="ac-sub-servers"><div class="card" style="margin-bottom:0;"><div class="flex-between" style="margin-bottom:10px;flex-wrap:wrap;"><strong style="font-size:0.9rem;">Server Browser &amp; Quick-Join</strong><button class="btn-secondary" id="ac-ping-all">Ping All</button></div><div style="display:flex;gap:8px;margin-bottom:12px;flex-wrap:wrap;"><input type="text" id="ac-srv-name" class="input-field" placeholder="Server Name" style="max-width:190px;" /><input type="text" id="ac-srv-ip" class="input-field" placeholder="IP (e.g. mc.hypixel.net)" style="flex:1;min-width:180px;" /><button class="btn" id="ac-add-srv">+ Add</button></div><div id="ac-srv-list" style="display:flex;flex-direction:column;gap:8px;"></div></div></div>
  <div class="ac-sub" id="ac-sub-social"><div class="card" style="margin-bottom:0;"><div class="flex-between" style="margin-bottom:10px;"><strong style="font-size:0.9rem;">Friends Roster</strong><span class="brand-badge" id="ac-fr-cnt">0</span></div><div style="display:flex;gap:8px;margin-bottom:10px;"><input type="text" id="ac-fr-in" class="input-field" placeholder="Minecraft username..." /><button class="btn" id="ac-fr-add">Add</button></div><div id="ac-fr-list" style="display:flex;flex-direction:column;gap:8px;"></div></div></div>
  <div class="ac-sub" id="ac-sub-playtime">
    <div class="grid-2" style="margin-bottom:0;"><div class="card" style="margin-bottom:0;"><span class="lbl">Total Playtime</span><strong id="ac-pt-tot" style="font-size:1.5rem;">0m</strong><span id="ac-pt-ses" style="display:block;font-size:0.74rem;color:var(--text-muted);margin-top:4px;">0 sessions</span></div><div class="card" style="margin-bottom:0;"><span class="lbl">Last Played</span><strong id="ac-pt-last" style="font-size:1.05rem;">Never</strong><span style="display:block;font-size:0.74rem;color:var(--text-muted);margin-top:4px;">Recorded via onGameExit</span></div></div>
    <div class="card" style="margin-bottom:0;"><strong style="font-size:0.9rem;display:block;margin-bottom:8px;">Version Playtime</strong><div id="ac-pt-list" style="display:flex;flex-direction:column;gap:6px;"></div></div>
    <div class="card" style="margin-bottom:0;"><strong style="font-size:0.9rem;display:block;margin-bottom:6px;">GitHub Extension Self-Updater</strong><div style="display:flex;gap:8px;flex-wrap:wrap;"><input type="text" id="ac-gh-url" class="input-field" placeholder="https://raw.githubusercontent.com/USER/REPO/main/extensions/aetheria-client/index.js" style="flex:1;min-width:240px;" /><button class="btn" id="ac-gh-update">Update from GitHub</button></div></div>
  </div>
</div>`;

const TAB_CSS = `.ac-root{display:flex;flex-direction:column;gap:12px;padding-bottom:24px;}.ac-hero{display:flex;justify-content:space-between;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:0;background:linear-gradient(135deg,rgba(var(--accent-rgb),0.08),rgba(var(--card-rgb),0.65));}.ac-hero-left{display:flex;align-items:center;gap:12px;}.ac-emblem{width:44px;height:44px;border-radius:11px;display:flex;justify-content:center;align-items:center;font-size:1.35rem;background:var(--accent);color:var(--accent-fg);font-weight:800;flex-shrink:0;}.ac-sub{display:none;flex-direction:column;gap:12px;}.ac-sub.active{display:flex;}.ac-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(290px,1fr));gap:10px;}.ac-ocard{background:var(--card);border:1px solid var(--border);border-radius:var(--radius);padding:11px 13px;display:flex;flex-direction:column;justify-content:space-between;gap:8px;backdrop-filter:blur(16px) saturate(145%);}.ac-ocard:hover{border-color:var(--border-hover);}.ac-pill{min-width:48px;padding:4px 10px;border-radius:99px;font-size:0.68rem;font-weight:700;border:1px solid var(--border);background:rgba(var(--card-rgb),0.6);color:var(--text-muted);cursor:pointer;}.ac-pill.on{background:var(--accent);color:var(--accent-fg);border-color:var(--accent);}.ac-cbtn{padding:3px 8px;border-radius:6px;font-size:0.68rem;border:1px solid var(--border);background:rgba(var(--card-rgb),0.5);color:var(--text-muted);cursor:pointer;}.ac-cbtn.active{background:var(--accent-bg);border-color:var(--accent);color:var(--text);font-weight:600;}.ac-pgrid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:8px;}.ac-pcard{padding:11px;border-radius:var(--radius);border:1px solid var(--border);background:rgba(var(--card-rgb),0.45);cursor:pointer;text-align:left;color:var(--text);}.ac-pcard.active{border-color:var(--accent);background:var(--accent-bg);}.ac-hud-wrap{display:grid;grid-template-columns:minmax(360px,1.7fr) minmax(250px,1fr);gap:12px;align-items:start;}@media(max-width:920px){.ac-hud-wrap{grid-template-columns:1fr;}}.ac-stage{position:relative;width:100%;aspect-ratio:16/9;border-radius:10px;overflow:hidden;border:1px solid var(--border);background:radial-gradient(circle at 50% 35%,rgba(80,120,170,0.32),rgba(16,20,28,0.92));user-select:none;}.ac-stage-grid{position:absolute;inset:0;background-image:linear-gradient(to right,rgba(255,255,255,0.04) 1px,transparent 1px),linear-gradient(to bottom,rgba(255,255,255,0.04) 1px,transparent 1px);background-size:10% 10%;pointer-events:none;}.ac-hud-box{position:absolute;padding:4px 7px;border-radius:5px;font-family:ui-monospace,monospace;font-size:11px;line-height:1.25;white-space:pre;cursor:grab;transform-origin:top left;}.ac-hud-box:active{cursor:grabbing;}.ac-hud-box.selected{outline:2px solid var(--accent);box-shadow:0 0 0 3px rgba(var(--accent-rgb),0.25);z-index:10;}.ac-hud-box.disabled{opacity:0.32 !important;border-style:dashed !important;}.ac-xhair-preview{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);pointer-events:none;}.ac-hotbar-mock{position:absolute;left:50%;bottom:8px;transform:translateX(-50%);display:flex;gap:2px;padding:3px;border-radius:4px;background:rgba(0,0,0,0.55);border:1px solid rgba(255,255,255,0.2);pointer-events:none;}.ac-hotbar-mock span{width:18px;height:18px;display:flex;align-items:center;justify-content:center;font-size:9px;color:rgba(255,255,255,0.55);border:1px solid rgba(255,255,255,0.12);}@keyframes ac-chroma{0%{color:#ff6b6b;}33%{color:#51cf66;}66%{color:#339af0;}100%{color:#ff6b6b;}}.ac-chroma{animation:ac-chroma 3s linear infinite;}`;

const TAB_SCRIPT = `(async function() {
  const esc = Aetheria.escapeHtml, q = s => panelEl.querySelector(s);
  let data = await Aetheria.ipcRenderer.invoke('ac:get-state');
  window.__AetheriaClientCache = data;
  let activeCat = 'All', saveTimer = null, hudTimer = null, selectedHudKey = 'fps';
  const pings = {};
  const fmtSec = sec => { const s = Math.max(0, Math.round(sec || 0)), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60); return h ? h + 'h ' + m + 'm' : m + 'm ' + (s % 60) + 's'; };
  const queueSave = () => { clearTimeout(saveTimer); saveTimer = setTimeout(() => Aetheria.ipcRenderer.invoke('ac:save-options', { options: data.modConfig.options, activePreset: data.meta.activePreset }), 180); };
  const queueHudSave = () => { clearTimeout(hudTimer); hudTimer = setTimeout(() => Aetheria.ipcRenderer.invoke('ac:save-hud', { hudLayout: data.modConfig.hudLayout, options: data.modConfig.options }), 180); };

  function renderHeader() {
    const p = data.profile;
    q('#ac-prof-badge').innerText = p.name || 'Active Profile';
    q('#ac-ver-badge').innerText = 'MC ' + p.mcVersion + ' • ' + String(p.loader || 'vanilla').toUpperCase();
    const jb = q('#ac-jar-badge');
    if (data.matchedJar) { jb.innerText = 'MOD JAR: READY (' + data.matchedJarKey + ')'; q('#ac-jar-status').innerText = 'Preinstalled JAR matched for MC ' + data.matchedJarKey + ': ' + data.matchedJar; }
    else { jb.innerText = data.matchedJarKey ? 'MOD JAR: MISSING (' + data.matchedJarKey + ')' : 'UNSUPPORTED MC VER'; q('#ac-jar-status').innerText = data.matchedJarKey ? 'No JAR found for MC ' + data.matchedJarKey + '.' : 'Profile MC ' + p.mcVersion + ' is outside supported targets.'; }
    const ad = q('#ac-autodeploy-btn'), on = data.meta.autoDeployMod !== false;
    ad.className = 'ac-pill ' + (on ? 'on' : ''); ad.innerText = 'Auto-Deploy: ' + (on ? 'ON' : 'OFF');
    q('#ac-mod-url').value = data.meta.modJarUrl || '';
  }

  function renderCats() {
    const bar = q('#ac-cats'); bar.innerHTML = '';
    ['All', ...data.catalog.categories].forEach(c => {
      const b = document.createElement('button'); b.className = 'chip-btn ' + (activeCat === c ? 'active' : '');
      b.innerText = c === 'All' ? 'All (' + data.catalog.options.length + ')' : c;
      b.onclick = () => { activeCat = c; renderCats(); renderOpts(); }; bar.appendChild(b);
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
      const val = data.modConfig.options[o.id], top = document.createElement('div');
      top.style.cssText = 'display:flex;justify-content:space-between;align-items:flex-start;gap:10px;';
      top.innerHTML = '<div><span style="font-size:0.6rem;color:var(--text-dim);text-transform:uppercase;">' + esc(o.category) + '</span><strong style="display:block;font-size:0.8rem;">' + esc(o.label) + '</strong><p style="font-size:0.69rem;color:var(--text-muted);margin-top:2px;">' + esc(o.desc) + '</p></div>';
      if (o.type === 'b') {
        const p = document.createElement('button'); p.className = 'ac-pill ' + (val ? 'on' : ''); p.innerText = val ? 'ON' : 'OFF';
        p.onclick = () => { data.modConfig.options[o.id] = !data.modConfig.options[o.id]; data.meta.activePreset = 'custom'; p.className = 'ac-pill ' + (data.modConfig.options[o.id] ? 'on' : ''); p.innerText = data.modConfig.options[o.id] ? 'ON' : 'OFF'; renderHudEditor(); queueSave(); };
        top.appendChild(p); card.appendChild(top);
      } else if (o.type === 'r') {
        const badge = document.createElement('span'); badge.className = 'brand-badge'; badge.innerText = String(val); top.appendChild(badge); card.appendChild(top);
        const sl = document.createElement('input'); sl.type = 'range'; sl.className = 'range-slider'; sl.min = o.extra[0]; sl.max = o.extra[1]; sl.step = o.extra[2]; sl.value = val;
        sl.oninput = () => { data.modConfig.options[o.id] = Number(sl.value); data.meta.activePreset = 'custom'; badge.innerText = String(sl.value); renderCrosshairPreview(); queueSave(); };
        card.appendChild(sl);
      } else {
        card.appendChild(top); const row = document.createElement('div'); row.style.cssText = 'display:flex;gap:4px;flex-wrap:wrap;';
        o.extra.forEach(ch => { const cb = document.createElement('button'); cb.className = 'ac-cbtn ' + (String(val) === String(ch) ? 'active' : ''); cb.innerText = ch; cb.onclick = () => { data.modConfig.options[o.id] = ch; data.meta.activePreset = 'custom'; row.querySelectorAll('button').forEach(x => x.classList.remove('active')); cb.classList.add('active'); renderCrosshairPreview(); queueSave(); }; row.appendChild(cb); });
        card.appendChild(row);
      }
      frag.appendChild(card);
    });
    grid.appendChild(frag);
  }

  function renderCrosshairPreview() {
    const xp = q('#ac-xhair-preview'); if (!xp) return;
    const st = data.modConfig.options, style = st.pvp_xhair_style || 'classic', sz = Number(st.pvp_xhair_size || 6), gap = Number(st.pvp_xhair_gap || 2);
    if (style === 'vanilla') { xp.innerHTML = ''; return; }
    if (style === 'dot') xp.innerHTML = '<div style="width:4px;height:4px;border-radius:50%;background:#fff;box-shadow:0 0 2px #000;"></div>';
    else if (style === 'circle') { const d = (sz + gap) * 2; xp.innerHTML = '<div style="width:' + d + 'px;height:' + d + 'px;border-radius:50%;border:1.5px solid #fff;box-shadow:0 0 2px #000;"></div>'; }
    else xp.innerHTML = '<div style="position:relative;width:0;height:0;">' + (style === 't_shape' ? '' : '<div style="position:absolute;left:-1px;bottom:' + gap + 'px;width:2px;height:' + sz + 'px;background:#fff;"></div>') + '<div style="position:absolute;left:-1px;top:' + gap + 'px;width:2px;height:' + sz + 'px;background:#fff;"></div><div style="position:absolute;top:-1px;right:' + gap + 'px;height:2px;width:' + sz + 'px;background:#fff;"></div><div style="position:absolute;top:-1px;left:' + gap + 'px;height:2px;width:' + sz + 'px;background:#fff;"></div></div>';
  }

  function renderHudInspector() {
    const box = q('#ac-hud-inspector'), layout = data.modConfig.hudLayout || {}, m = layout[selectedHudKey];
    if (!m) return;
    const isOn = m.optId ? !!data.modConfig.options[m.optId] : (m.enabled !== false);
    box.innerHTML = '<div class="flex-between"><div><span style="font-size:0.65rem;color:var(--text-muted);text-transform:uppercase;">Selected HUD Module</span><strong style="display:block;font-size:0.98rem;">' + esc(m.label) + '</strong></div><button class="ac-pill ' + (isOn ? 'on' : '') + '" id="ac-hi-tog">' + (isOn ? 'ON' : 'OFF') + '</button></div><div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:0.75rem;"><div><span style="color:var(--text-muted);">X: <strong id="ac-hi-xv">' + Math.round(m.x * 100) + '%</strong></span><input type="range" class="range-slider" id="ac-hi-x" min="0" max="0.92" step="0.005" value="' + m.x + '" /></div><div><span style="color:var(--text-muted);">Y: <strong id="ac-hi-yv">' + Math.round(m.y * 100) + '%</strong></span><input type="range" class="range-slider" id="ac-hi-y" min="0" max="0.92" step="0.005" value="' + m.y + '" /></div></div><div><span style="font-size:0.75rem;color:var(--text-muted);">Scale: <strong id="ac-hi-sv">' + Number(m.scale).toFixed(2) + 'x</strong></span><input type="range" class="range-slider" id="ac-hi-s" min="0.5" max="2.0" step="0.05" value="' + m.scale + '" /></div><div><span style="font-size:0.75rem;color:var(--text-muted);">Opacity: <strong id="ac-hi-ov">' + Math.round(m.opacity * 100) + '%</strong></span><input type="range" class="range-slider" id="ac-hi-o" min="0" max="1" step="0.05" value="' + m.opacity + '" /></div><div style="display:flex;gap:6px;flex-wrap:wrap;"><button class="ac-cbtn ' + (m.bg ? 'active' : '') + '" id="ac-hi-bg">Background</button><button class="ac-cbtn ' + (m.border ? 'active' : '') + '" id="ac-hi-bd">Border</button><button class="ac-cbtn ' + (m.chroma ? 'active' : '') + '" id="ac-hi-ch">Chroma RGB</button></div><div class="flex-between" style="padding-top:4px;"><span style="font-size:0.76rem;color:var(--text-muted);">Text Color</span><input type="color" id="ac-hi-col" value="' + esc(m.color || '#ffffff') + '" style="width:44px;height:28px;border:1px solid var(--border);border-radius:6px;background:transparent;cursor:pointer;" /></div><div style="border-top:1px solid var(--border);padding-top:8px;"><div id="ac-hi-list" style="display:flex;gap:4px;flex-wrap:wrap;"></div></div>';
    const list = box.querySelector('#ac-hi-list');
    Object.entries(layout).forEach(([k, item]) => { const b = document.createElement('button'); b.className = 'ac-cbtn ' + (k === selectedHudKey ? 'active' : ''); b.innerText = item.label; b.onclick = () => { selectedHudKey = k; renderHudEditor(); }; list.appendChild(b); });
    box.querySelector('#ac-hi-tog').onclick = () => { if (m.optId) data.modConfig.options[m.optId] = !data.modConfig.options[m.optId]; else m.enabled = !m.enabled; queueHudSave(); renderOpts(); renderHudEditor(); };
    const upd = () => { const el = q('.ac-hud-box[data-hud="' + selectedHudKey + '"]'); if (!el) return; el.style.left = (m.x * 100) + '%'; el.style.top = (m.y * 100) + '%'; el.style.transform = 'scale(' + m.scale + ')'; el.style.background = m.bg ? 'rgba(10,12,18,' + m.opacity + ')' : 'transparent'; el.style.border = m.border ? '1px solid rgba(255,255,255,0.22)' : '1px solid transparent'; el.style.color = m.chroma ? '' : (m.color || '#ffffff'); el.classList.toggle('ac-chroma', !!m.chroma); };
    box.querySelector('#ac-hi-x').oninput = e => { m.x = Number(e.target.value); box.querySelector('#ac-hi-xv').innerText = Math.round(m.x * 100) + '%'; upd(); queueHudSave(); };
    box.querySelector('#ac-hi-y').oninput = e => { m.y = Number(e.target.value); box.querySelector('#ac-hi-yv').innerText = Math.round(m.y * 100) + '%'; upd(); queueHudSave(); };
    box.querySelector('#ac-hi-s').oninput = e => { m.scale = Number(e.target.value); box.querySelector('#ac-hi-sv').innerText = m.scale.toFixed(2) + 'x'; upd(); queueHudSave(); };
    box.querySelector('#ac-hi-o').oninput = e => { m.opacity = Number(e.target.value); box.querySelector('#ac-hi-ov').innerText = Math.round(m.opacity * 100) + '%'; upd(); queueHudSave(); };
    box.querySelector('#ac-hi-bg').onclick = () => { m.bg = !m.bg; queueHudSave(); renderHudEditor(); };
    box.querySelector('#ac-hi-bd').onclick = () => { m.border = !m.border; queueHudSave(); renderHudEditor(); };
    box.querySelector('#ac-hi-ch').onclick = () => { m.chroma = !m.chroma; queueHudSave(); renderHudEditor(); };
    box.querySelector('#ac-hi-col').oninput = e => { m.color = e.target.value; m.chroma = false; upd(); queueHudSave(); };
  }

  function renderHudEditor() {
    const stage = q('#ac-hud-stage'); if (!stage) return;
    stage.querySelectorAll('.ac-hud-box').forEach(el => el.remove());
    Object.entries(data.modConfig.hudLayout || {}).forEach(([key, m]) => {
      const enabled = m.optId ? !!data.modConfig.options[m.optId] : (m.enabled !== false);
      const box = document.createElement('div');
      box.className = 'ac-hud-box' + (key === selectedHudKey ? ' selected' : '') + (enabled ? '' : ' disabled') + (m.chroma ? ' ac-chroma' : '');
      box.dataset.hud = key; box.innerText = m.sample || m.label;
      box.style.left = (m.x * 100) + '%'; box.style.top = (m.y * 100) + '%'; box.style.transform = 'scale(' + (m.scale || 1) + ')';
      box.style.background = m.bg ? 'rgba(10,12,18,' + (m.opacity ?? 0.85) + ')' : 'transparent';
      box.style.border = m.border ? '1px solid rgba(255,255,255,0.22)' : '1px solid transparent';
      if (!m.chroma) box.style.color = m.color || '#ffffff';
      box.onpointerdown = ev => {
        ev.preventDefault(); selectedHudKey = key; stage.querySelectorAll('.ac-hud-box').forEach(x => x.classList.remove('selected')); box.classList.add('selected'); renderHudInspector();
        const rect = stage.getBoundingClientRect(), sx = ev.clientX, sy = ev.clientY, ox = m.x, oy = m.y;
        const mv = e2 => {
          let nx = ox + (e2.clientX - sx) / rect.width, ny = oy + (e2.clientY - sy) / rect.height;
          if (data.modConfig.options.hud_snap_grid) { nx = Math.round(nx * 40) / 40; ny = Math.round(ny * 40) / 40; }
          m.x = Math.max(0, Math.min(0.92, nx)); m.y = Math.max(0, Math.min(0.92, ny));
          box.style.left = (m.x * 100) + '%'; box.style.top = (m.y * 100) + '%';
          if (q('#ac-hi-x')) { q('#ac-hi-x').value = m.x; q('#ac-hi-xv').innerText = Math.round(m.x * 100) + '%'; }
          if (q('#ac-hi-y')) { q('#ac-hi-y').value = m.y; q('#ac-hi-yv').innerText = Math.round(m.y * 100) + '%'; }
        };
        const up = () => { window.removeEventListener('pointermove', mv); window.removeEventListener('pointerup', up); queueHudSave(); };
        window.addEventListener('pointermove', mv); window.addEventListener('pointerup', up);
      };
      stage.appendChild(box);
    });
    renderCrosshairPreview(); renderHudInspector();
  }

  function renderPresetsAndMods() {
    const sl = q('#ac-supported-list'); sl.innerHTML = '';
    data.supportedTargets.forEach(t => { const d = document.createElement('div'); d.style.cssText = 'display:flex;justify-content:space-between;align-items:center;padding:7px 9px;border:1px solid var(--border);border-radius:7px;background:rgba(var(--card-rgb),0.4);font-size:0.74rem;'; d.innerHTML = '<span>' + esc(t.label) + '</span><span class="brand-badge">' + (data.availableJars[t.key] ? 'BUNDLED' : 'NOT BUILT') + '</span>'; sl.appendChild(d); });
    const pg = q('#ac-presets-grid'); pg.innerHTML = '';
    Object.values(data.catalog.presets).forEach(p => { const b = document.createElement('button'); b.className = 'ac-pcard ' + (data.meta.activePreset === p.id ? 'active' : ''); b.innerHTML = '<strong style="font-size:0.82rem;display:block;">' + esc(p.name) + '</strong><span style="font-size:0.7rem;color:var(--text-muted);display:block;margin-top:3px;">' + esc(p.desc) + '</span>'; b.onclick = async () => { const r = await Aetheria.ipcRenderer.invoke('ac:apply-preset', p.id); if (r && r.ok) { data = r.pack; window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Applied preset: ' + p.name); } }; pg.appendChild(b); });
    const lm = q('#ac-local-mods'); lm.innerHTML = '';
    if (!data.localMods.length) lm.innerHTML = '<span style="font-size:0.75rem;color:var(--text-muted);">No .jar mods in active profile.</span>';
    else data.localMods.forEach(m => { const r = document.createElement('div'); r.className = 'mod-item'; r.style.marginBottom = '0'; r.innerHTML = '<div><strong style="font-size:0.8rem;">' + esc(m.name) + '</strong><span style="font-size:0.68rem;color:var(--text-muted);margin-left:8px;">' + m.sizeMb + ' MB</span></div><button class="btn-secondary" style="font-size:0.7rem;padding:4px 10px;">' + (m.enabled ? 'Enabled' : 'Disabled') + '</button>'; r.querySelector('button').onclick = async () => { data.localMods = await Aetheria.ipcRenderer.invoke('ac:toggle-local-mod', m.fileName); renderPresetsAndMods(); }; lm.appendChild(r); });
  }

  function renderServers() {
    const list = q('#ac-srv-list'); list.innerHTML = '';
    if (!data.meta.servers.length) return (list.innerHTML = '<span style="font-size:0.78rem;color:var(--text-muted);">No servers saved.</span>');
    data.meta.servers.forEach((s, idx) => {
      const p = pings[s.ip], row = document.createElement('div'); row.className = 'mod-item'; row.style.marginBottom = '0';
      row.innerHTML = '<div><div style="display:flex;align-items:center;gap:8px;"><strong style="font-size:0.84rem;">' + esc(s.name || s.ip) + '</strong><span class="brand-badge">' + (p ? (p.online ? p.playersOnline + '/' + p.playersMax + ' • ' + p.latencyMs + 'ms' : 'OFFLINE') : 'UNPINGED') + '</span></div><span style="font-size:0.72rem;color:var(--text-muted);display:block;margin-top:2px;">' + esc(s.ip) + (p && p.motd ? ' - ' + esc(p.motd.slice(0, 64)) : '') + '</span></div><div style="display:flex;gap:6px;"><button class="btn-secondary" style="font-size:0.72rem;" data-a="p">Ping</button><button class="btn" style="font-size:0.72rem;" data-a="j">Quick Join</button><button class="btn-secondary" style="font-size:0.72rem;color:var(--danger);" data-a="d">X</button></div>';
      row.querySelector('[data-a="p"]').onclick = async () => { pings[s.ip] = await Aetheria.ipcRenderer.invoke('ac:ping-server', s.ip); renderServers(); };
      row.querySelector('[data-a="j"]').onclick = async () => { await Aetheria.ipcRenderer.invoke('ac:set-quick-join', s.ip); Aetheria.toast('Launching into ' + s.ip + '...'); Aetheria.ipcRenderer.send('launch-game', {}); };
      row.querySelector('[data-a="d"]').onclick = async () => { data.meta.servers.splice(idx, 1); await Aetheria.ipcRenderer.invoke('ac:save-servers', data.meta.servers); renderServers(); };
      list.appendChild(row);
    });
  }

  function renderSocial() {
    const fl = q('#ac-fr-list'); q('#ac-fr-cnt').innerText = String(data.meta.friends.length); fl.innerHTML = '';
    data.meta.friends.forEach((f, idx) => { const r = document.createElement('div'); r.className = 'mod-item'; r.style.marginBottom = '0'; r.innerHTML = '<div style="display:flex;align-items:center;gap:10px;"><img src="https://mc-heads.net/avatar/' + encodeURIComponent(f.name) + '/32" style="width:28px;height:28px;border-radius:6px;image-rendering:pixelated;" onerror="this.remove()" /><div><strong style="font-size:0.82rem;">' + esc(f.name) + '</strong><span style="display:block;font-size:0.68rem;color:var(--text-muted);">' + esc(f.note || 'Friend') + '</span></div></div><button class="btn-secondary" style="font-size:0.7rem;color:var(--danger);">Remove</button>'; r.querySelector('button').onclick = async () => { data.meta.friends.splice(idx, 1); await Aetheria.ipcRenderer.invoke('ac:save-friends', data.meta.friends); renderSocial(); }; fl.appendChild(r); });
  }

  function renderPlaytime() {
    const pt = data.meta.playtime || { totalSeconds: 0, sessions: 0, byVersion: {} };
    q('#ac-pt-tot').innerText = fmtSec(pt.totalSeconds); q('#ac-pt-ses').innerText = (pt.sessions || 0) + ' tracked sessions';
    q('#ac-pt-last').innerText = pt.lastPlayedAt ? new Date(pt.lastPlayedAt).toLocaleString() : 'Never';
    q('#ac-gh-url').value = data.meta.githubRawUrl || '';
    const list = q('#ac-pt-list'); list.innerHTML = '';
    const entries = Object.entries(pt.byVersion || {});
    if (!entries.length) list.innerHTML = '<span style="font-size:0.76rem;color:var(--text-muted);">No playtime logged yet.</span>';
    else entries.sort((a, b) => b[1] - a[1]).forEach(([v, sec]) => { const d = document.createElement('div'); d.className = 'flex-between'; d.style.cssText = 'padding:6px 10px;border:1px solid var(--border);border-radius:8px;'; d.innerHTML = '<strong style="font-size:0.8rem;">MC ' + esc(v) + '</strong><span class="brand-badge">' + fmtSec(sec) + '</span>'; list.appendChild(d); });
  }

  function renderAll() { renderHeader(); renderCats(); renderOpts(); renderHudEditor(); renderPresetsAndMods(); renderServers(); renderSocial(); renderPlaytime(); }
  q('#ac-subnav').querySelectorAll('button').forEach(btn => { btn.onclick = () => { q('#ac-subnav').querySelectorAll('button').forEach(b => b.classList.remove('active')); panelEl.querySelectorAll('.ac-sub').forEach(p => p.classList.remove('active')); btn.classList.add('active'); q('#ac-sub-' + btn.dataset.sub).classList.add('active'); }; });
  q('#ac-search').oninput = renderOpts;
  q('#ac-launch-btn').onclick = () => Aetheria.ipcRenderer.send('launch-game', {});
  q('#ac-refresh-prof').onclick = async () => { data = await Aetheria.ipcRenderer.invoke('ac:get-state'); window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Synced with active profile.'); };
  q('#ac-reload-cfg').onclick = async () => { data = await Aetheria.ipcRenderer.invoke('ac:get-state'); window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Reloaded from disk.'); };
  q('#ac-hud-pull').onclick = async () => { data = await Aetheria.ipcRenderer.invoke('ac:get-state'); window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Loaded latest in-game HUD layout!'); };
  q('#ac-hud-reset').onclick = async () => { if (await Aetheria.showConfirmModal('Reset HUD module positions for this profile?', 'Reset HUD Layout')) { data = await Aetheria.ipcRenderer.invoke('ac:reset-hud'); window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('HUD reset.'); } };
  q('#ac-reset-btn').onclick = async () => { if (await Aetheria.showConfirmModal('Reset mod settings for this profile?', 'Reset Mod Settings')) { data = await Aetheria.ipcRenderer.invoke('ac:reset-options'); window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Mod settings reset.'); } };
  q('#ac-autodeploy-btn').onclick = async () => { data = await Aetheria.ipcRenderer.invoke('ac:toggle-autodeploy'); window.__AetheriaClientCache = data; renderHeader(); };
  q('#ac-deploy-jar').onclick = async () => { const res = await Aetheria.ipcRenderer.invoke('ac:deploy-jar'); data = res.pack; window.__AetheriaClientCache = data; renderAll(); if (res.deployed) Aetheria.toast('Deployed ' + res.fileName + '!'); else Aetheria.showAlertModal(res.reason || 'Could not deploy JAR.', 'Mod JAR'); };
  q('#ac-dl-jar').onclick = async () => { const url = q('#ac-mod-url').value.trim(); if (!url) return Aetheria.showAlertModal('Paste an https:// URL first.', 'Download Mod JAR'); try { const res = await Aetheria.ipcRenderer.invoke('ac:download-mod-jar', url); data = res.pack; window.__AetheriaClientCache = data; renderAll(); Aetheria.toast('Downloaded ' + res.fileName + '!'); } catch (err) { Aetheria.showAlertModal(err.message, 'Download Failed'); } };
  q('#ac-ref-mods').onclick = async () => { data = await Aetheria.ipcRenderer.invoke('ac:get-state'); window.__AetheriaClientCache = data; renderPresetsAndMods(); };
  q('#ac-add-srv').onclick = async () => { const ip = q('#ac-srv-ip').value.trim(); if (!ip) return; data.meta.servers.push({ name: q('#ac-srv-name').value.trim() || ip, ip }); q('#ac-srv-name').value = q('#ac-srv-ip').value = ''; await Aetheria.ipcRenderer.invoke('ac:save-servers', data.meta.servers); renderServers(); };
  q('#ac-ping-all').onclick = async () => { Aetheria.toast('Pinging...'); await Promise.all(data.meta.servers.map(async s => { pings[s.ip] = await Aetheria.ipcRenderer.invoke('ac:ping-server', s.ip); })); renderServers(); };
  q('#ac-fr-add').onclick = async () => { const n = q('#ac-fr-in').value.trim(); if (!n) return; data.meta.friends.push({ name: n, note: 'Friend' }); q('#ac-fr-in').value = ''; await Aetheria.ipcRenderer.invoke('ac:save-friends', data.meta.friends); renderSocial(); };
  q('#ac-gh-update').onclick = async () => { const url = q('#ac-gh-url').value.trim(); if (!url) return Aetheria.showAlertModal('Enter raw index.js URL first.', 'GitHub Updater'); try { const res = await Aetheria.ipcRenderer.invoke('ac:update-from-github', url); Aetheria.toast('Updated (' + res.bytes + ' bytes)! Reloading...'); await Aetheria.ipcRenderer.invoke('reload-extensions'); } catch (err) { Aetheria.showAlertModal(err.message, 'Update Failed'); } };

  Aetheria.onTabChange(tab => { if (tab === 'ext-client') q('#ac-refresh-prof')?.click(); });
  Aetheria.ipcRenderer.on('ac:state-updated', (e, next) => { data = next; window.__AetheriaClientCache = next; renderAll(); });
  Aetheria.ipcRenderer.on('game-started', () => { q('#ac-launch-btn').disabled = true; q('#ac-launch-btn').innerText = 'RUNNING...'; });
  Aetheria.ipcRenderer.on('game-exited', () => { q('#ac-launch-btn').disabled = false; q('#ac-launch-btn').innerText = '▶ LAUNCH PROFILE'; });
  Aetheria.ipcRenderer.on('game-crashed', () => { q('#ac-launch-btn').disabled = false; q('#ac-launch-btn').innerText = '▶ LAUNCH PROFILE'; });
  renderAll();
})();`;

const WIDGET_JS = `(function(Aetheria) {
  return function(wConf, container) {
    function draw(d) {
      var prof = d && d.profile ? d.profile : { name: 'Active Profile', mcVersion: '-' };
      var pt = d && d.meta && d.meta.playtime ? d.meta.playtime.totalSeconds : 0;
      var tiny = wConf.colSpan <= 3 || wConf.rowSpan <= 1;
      container.innerHTML = '<div class="widget-card"><div class="widget-head"><span class="widget-title">✦ Aetheria Client</span><span class="brand-badge">' + Aetheria.escapeHtml(prof.mcVersion) + '</span></div>' + (tiny ? '' : '<div style="font-size:0.72rem;color:var(--text-muted);">Profile: <strong style="color:var(--text);">' + Aetheria.escapeHtml(prof.name) + '</strong> • ' + Math.floor(pt / 60) + 'm played</div>') + '<div style="display:flex;gap:5px;"><button class="btn" style="flex:1;padding:4px 8px;font-size:0.7rem;" data-act="play">▶ Play</button>' + (tiny ? '' : '<button class="btn-secondary" style="padding:4px 8px;font-size:0.7rem;" data-act="open">Mod Settings</button>') + '</div></div>';
      container.querySelector('[data-act="play"]').onclick = function() { Aetheria.ipcRenderer.send('launch-game', {}); };
      var ob = container.querySelector('[data-act="open"]'); if (ob) ob.onclick = function() { Aetheria.switchTab('ext-client'); };
    }
    draw(window.__AetheriaClientCache);
    if (!window.__AetheriaClientCache && !window.__AcWidgetLoading) { window.__AcWidgetLoading = true; Aetheria.ipcRenderer.invoke('ac:get-state').then(function(d) { window.__AetheriaClientCache = d; window.__AcWidgetLoading = false; draw(d); }).catch(function() { window.__AcWidgetLoading = false; }); }
  };
})`;

async function activate(api) {
  let sessionStart = null;
  const pack = () => {
    const prof = api.getActiveProfile() || { name: 'Default Profile', version: '1.20.4', loader: 'vanilla' };
    const profileDir = api.getActiveProfileDir(), meta = loadMeta(api), modConfig = loadModConfig(api, profileDir);
    const mcVersion = apiExtractVer(prof.version), jars = findAvailableBundledJars(api), targetKey = resolveTargetKeyForMcVersion(mcVersion, jars);
    const matchedPath = targetKey ? jars[targetKey] : null;
    const supportedTargets = [...SUPPORTED_MOD_TARGETS];
    for (const k of Object.keys(jars)) {
      if (k !== 'generic' && !supportedTargets.some(t => t.key === k)) supportedTargets.unshift({ key: k, label: `MC ${k} (Fabric)` });
    }
    return { profile: { id: prof.id, name: prof.name, mcVersion, loader: prof.loader || 'vanilla' }, meta, modConfig, matchedJarKey: targetKey, matchedJar: matchedPath ? path.basename(matchedPath) : null, availableJars: Object.fromEntries(Object.entries(jars).map(([k, v]) => [k, path.basename(v)])), supportedTargets, catalog: { presets: MOD_PRESETS, categories: MOD_OPTIONS_CATS.map(c => c[0]), options: OPTIONS_CATALOG }, localMods: listLocalMods(profileDir) };
  };

  api.registerIpc('ac:get-state', () => pack());
  api.registerIpc('ac:save-options', (e, { options, activePreset }) => { const dir = api.getActiveProfileDir(), modCfg = loadModConfig(api, dir), meta = loadMeta(api); if (options) modCfg.options = { ...modCfg.options, ...options }; saveModConfig(api, dir, modCfg); if (activePreset !== undefined) { meta.activePreset = activePreset; saveMeta(api, meta); } return { ok: true }; });
  api.registerIpc('ac:save-hud', (e, { hudLayout, options }) => { const dir = api.getActiveProfileDir(), modCfg = loadModConfig(api, dir); if (hudLayout) modCfg.hudLayout = { ...modCfg.hudLayout, ...hudLayout }; if (options) modCfg.options = { ...modCfg.options, ...options }; saveModConfig(api, dir, modCfg); return { ok: true }; });
  api.registerIpc('ac:reset-hud', () => { const dir = api.getActiveProfileDir(), modCfg = loadModConfig(api, dir); modCfg.hudLayout = cloneDefaultHud(); saveModConfig(api, dir, modCfg); return pack(); });
  api.registerIpc('ac:reset-options', () => { const dir = api.getActiveProfileDir(), modCfg = loadModConfig(api, dir), meta = loadMeta(api); modCfg.options = { ...DEFAULT_OPTIONS_MAP }; saveModConfig(api, dir, modCfg); meta.activePreset = 'custom'; saveMeta(api, meta); return pack(); });
  api.registerIpc('ac:apply-preset', (e, id) => { const p = MOD_PRESETS[id]; if (!p) return { ok: false }; const dir = api.getActiveProfileDir(), modCfg = loadModConfig(api, dir), meta = loadMeta(api); modCfg.options = { ...modCfg.options, ...p.ov }; saveModConfig(api, dir, modCfg); meta.activePreset = id; saveMeta(api, meta); return { ok: true, pack: pack() }; });
  api.registerIpc('ac:toggle-autodeploy', () => { const meta = loadMeta(api); meta.autoDeployMod = meta.autoDeployMod === false; saveMeta(api, meta); return pack(); });
  api.registerIpc('ac:deploy-jar', () => { const meta = loadMeta(api); const res = deployModJarForProfile(api, api.getActiveProfile(), api.getActiveProfileDir(), { ...meta, autoDeployMod: true }); return { ...res, pack: pack() }; });
  api.registerIpc('ac:download-mod-jar', async (e, url) => { const meta = loadMeta(api); meta.modJarUrl = String(url || '').trim(); saveMeta(api, meta); if (!/^https:\/\//i.test(meta.modJarUrl)) throw new Error('Must be an https:// URL.'); const modsDir = path.join(api.extensionDir, 'mods'); api.utils.safeMkdirSync(modsDir); const fileName = path.basename(new URL(meta.modJarUrl).pathname) || 'aetheria-client-mod.jar'; await api.utils.downloadFile(meta.modJarUrl, path.join(modsDir, fileName)); deployModJarForProfile(api, api.getActiveProfile(), api.getActiveProfileDir(), meta); return { ok: true, fileName, pack: pack() }; });
  api.registerIpc('ac:toggle-local-mod', (e, fn) => { const mDir = path.join(api.getActiveProfileDir(), 'mods'), src = path.join(mDir, fn); if (fs.existsSync(src)) fs.renameSync(src, path.join(mDir, fn.endsWith('.disabled') ? fn.replace(/\.disabled$/, '') : fn + '.disabled')); return listLocalMods(api.getActiveProfileDir()); });
  api.registerIpc('ac:set-quick-join', (e, ip) => { const m = loadMeta(api); m.pendingQuickJoin = String(ip || '').trim(); saveMeta(api, m); return { ok: true }; });
  api.registerIpc('ac:save-servers', (e, servers) => { const m = loadMeta(api); m.servers = Array.isArray(servers) ? servers : []; saveMeta(api, m); return { ok: true }; });
  api.registerIpc('ac:ping-server', (e, addr) => pingServer(addr));
  api.registerIpc('ac:save-friends', (e, friends) => { const m = loadMeta(api); m.friends = Array.isArray(friends) ? friends : []; saveMeta(api, m); return { ok: true }; });
  api.registerIpc('ac:update-from-github', async (e, url) => { const m = loadMeta(api); m.githubRawUrl = String(url || '').trim(); saveMeta(api, m); if (!/^https:\/\//i.test(m.githubRawUrl)) throw new Error('Must be an https:// URL.'); const code = await api.utils.httpGetText(m.githubRawUrl); if (!code || !code.includes('activate')) throw new Error('Invalid index.js.'); fs.writeFileSync(path.join(api.extensionDir, 'index.js'), code, 'utf8'); return { ok: true, bytes: Buffer.byteLength(code, 'utf8') }; });

  api.onBeforeLaunch(async (ctx) => {
    const meta = loadMeta(api); sessionStart = Date.now();
    const cfgPath = getProfileModConfigFile(ctx.activeGameDir);
    if (!fs.existsSync(cfgPath)) saveModConfig(api, ctx.activeGameDir, loadModConfig(api, ctx.activeGameDir));
    const jarRes = deployModJarForProfile(api, ctx.prof, ctx.activeGameDir, meta);
    if (jarRes.deployed) ctx.sendStatus(`Aetheria Client Mod ready (${jarRes.fileName})`);
    if (meta.pendingQuickJoin) {
      const target = meta.pendingQuickJoin; meta.pendingQuickJoin = ''; saveMeta(api, meta);
      const parts = String(ctx.baseGameVersion || '').split('.').map(n => parseInt(n, 10) || 0);
      if (parts[0] >= 25 || (parts[0] === 1 && parts[1] >= 20)) ctx.gameArgs.push('--quickPlayMultiplayer', target);
      else { const [h, p] = target.split(':'); ctx.gameArgs.push('--server', h); if (p) ctx.gameArgs.push('--port', p); }
      ctx.sendStatus(`Quick-joining ${target}...`);
    }
  });

  api.onGameExit(async ({ code, crashed, baseGameVersion }) => {
    if (!sessionStart) return;
    const sec = Math.max(1, Math.round((Date.now() - sessionStart) / 1000)); sessionStart = null;
    const meta = loadMeta(api), ver = baseGameVersion || 'unknown';
    meta.playtime.totalSeconds = (meta.playtime.totalSeconds || 0) + sec; meta.playtime.sessions = (meta.playtime.sessions || 0) + 1;
    meta.playtime.lastPlayedAt = new Date().toISOString(); meta.playtime.byVersion[ver] = (meta.playtime.byVersion[ver] || 0) + sec;
    meta.playtime.history = [{ at: meta.playtime.lastPlayedAt, version: ver, seconds: sec, crashed: !!crashed, code }, ...(meta.playtime.history || [])].slice(0, 20);
    saveMeta(api, meta); api.sendToRenderer('ac:state-updated', pack());
  });

  api.registerTab({ id: 'client', label: 'Client', order: 5, html: TAB_HTML, css: TAB_CSS, script: TAB_SCRIPT });
  api.registerWidget({ id: 'aetheria-client-widget', name: 'Aetheria Client', desc: 'Active profile quick launch & mod settings shortcut', defaultColSpan: 8, defaultRowSpan: 4, minColSpan: 2, maxColSpan: 21, minRowSpan: 1, maxRowSpan: 12, renderJs: WIDGET_JS });
}

module.exports = { activate };
