package dev.aetheria.client.common;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class MicrosoftAuth {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    public static volatile boolean busy = false;
    public static volatile String statusMsg = "";

    private MicrosoftAuth() {}

    public static String getAuthorizeUrl() {
        try {
            return "https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID
                + "&response_type=code&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                + "&prompt=select_account";
        } catch (Exception e) {
            return "https://login.live.com/oauth20_authorize.srf?client_id=" + CLIENT_ID + "&response_type=code";
        }
    }

    public static String formatUuid(String raw) {
        if (raw == null) return "";
        String clean = raw.trim().replace("-", "");
        if (clean.length() != 32) return raw.trim();
        return clean.substring(0, 8) + "-" + clean.substring(8, 12) + "-" + clean.substring(12, 16) + "-"
             + clean.substring(16, 20) + "-" + clean.substring(20, 32);
    }

    public static void syncFromLauncherAsync(final Runnable onDone) {
        if (busy) return;
        busy = true;
        statusMsg = "Syncing Microsoft accounts from Aetheria Launcher...";
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    int n = syncFromLauncher();
                    statusMsg = n > 0 ? ("Synced " + n + " Microsoft account(s) from launcher.") : "No MSA accounts found in launcher config.";
                } catch (Exception e) {
                    statusMsg = "Launcher sync error: " + e.getMessage();
                } finally {
                    busy = false;
                    if (onDone != null) onDone.run();
                }
            }
        }, "aetheria-msa-sync");
        t.setDaemon(true);
        t.start();
    }

    public static int syncFromLauncher() {
        List<Path> candidates = new ArrayList<Path>();
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isEmpty()) candidates.add(Paths.get(appData, ".aetheria-launcher", "config.json"));
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty()) {
            candidates.add(Paths.get(home, ".aetheria-launcher", "config.json"));
            candidates.add(Paths.get(home, ".config", "aetheria-launcher", "config.json"));
        }
        int imported = 0;
        for (int c = 0; c < candidates.size(); c++) {
            Path p = candidates.get(c);
            if (!Files.exists(p)) continue;
            try {
                String raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                JsonObject root = new JsonParser().parse(raw).getAsJsonObject();
                List<JsonObject> objs = new ArrayList<JsonObject>();
                if (root.has("accounts") && root.get("accounts").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("accounts")) {
                        if (el.isJsonObject()) objs.add(el.getAsJsonObject());
                    }
                }
                if (root.has("activeAccount") && root.get("activeAccount").isJsonObject()) {
                    objs.add(root.getAsJsonObject("activeAccount"));
                }
                for (int i = 0; i < objs.size(); i++) {
                    JsonObject o = objs.get(i);
                    String name = o.has("name") ? o.get("name").getAsString() : "";
                    String uuid = o.has("id") ? formatUuid(o.get("id").getAsString()) : "";
                    String token = o.has("accessToken") && !o.get("accessToken").isJsonNull() ? o.get("accessToken").getAsString() : "";
                    String rTok = o.has("refreshToken") && !o.get("refreshToken").isJsonNull() ? o.get("refreshToken").getAsString() : "";
                    long exp = o.has("expiresAt") && o.get("expiresAt").isJsonPrimitive() ? o.get("expiresAt").getAsLong() : 0L;
                    if (!name.isEmpty() && !token.isEmpty()) {
                        ClientConfig.get().addOrUpdateAccount(name, "msa", "Main", uuid, token, rTok, exp);
                        imported++;
                    }
                }
                if (imported > 0) break;
            } catch (Exception ignored) {}
        }
        return imported;
    }

    public static void loginAsync(final String codeOrUrlOrToken, final UiBridge.UiDraw bridge) {
        if (busy) return;
        final String trimmed = codeOrUrlOrToken == null ? "" : codeOrUrlOrToken.trim();
        if (trimmed.isEmpty()) { statusMsg = "Paste the browser redirect URL, code, or MC token first."; return; }
        busy = true;
        statusMsg = "Authenticating with Microsoft / Minecraft...";
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ClientConfig.AccountEntry entry;
                    if (trimmed.startsWith("eyJ") && trimmed.indexOf('.') > 0) {
                        entry = loginWithMcToken(trimmed, "");
                    } else {
                        String code = extractCode(trimmed);
                        entry = exchangeCodeForAccount(code);
                    }
                    if (entry != null && bridge != null) {
                        bridge.switchAccount(entry.name, entry.uuid, entry.accessToken, entry.type);
                        statusMsg = "Signed in as " + entry.name + " (MSA)!";
                    }
                } catch (Exception e) {
                    statusMsg = "Login failed: " + e.getMessage();
                } finally {
                    busy = false;
                }
            }
        }, "aetheria-msa-login");
        t.setDaemon(true);
        t.start();
    }

    public static void refreshOrVerifyAsync(final ClientConfig.AccountEntry acc, final UiBridge.UiDraw bridge) {
        if (busy || acc == null) return;
        busy = true;
        statusMsg = "Refreshing/verifying " + acc.name + "...";
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (acc.refreshToken != null && !acc.refreshToken.isEmpty()) {
                        ClientConfig.AccountEntry updated = refreshFromMsToken(acc.refreshToken);
                        if (updated != null && bridge != null) {
                            bridge.switchAccount(updated.name, updated.uuid, updated.accessToken, updated.type);
                        }
                        statusMsg = "Refreshed MSA token for " + acc.name + "!";
                    } else if (acc.accessToken != null && !acc.accessToken.isEmpty()) {
                        ClientConfig.AccountEntry verified = loginWithMcToken(acc.accessToken, "");
                        if (verified != null && bridge != null) {
                            bridge.switchAccount(verified.name, verified.uuid, verified.accessToken, verified.type);
                        }
                        statusMsg = "Verified active MSA session for " + acc.name + "!";
                    } else {
                        statusMsg = "Offline account (no Microsoft token to refresh).";
                    }
                } catch (Exception e) {
                    statusMsg = "Refresh failed: " + e.getMessage();
                } finally {
                    busy = false;
                }
            }
        }, "aetheria-msa-refresh");
        t.setDaemon(true);
        t.start();
    }

    private static String extractCode(String input) {
        int idx = input.indexOf("code=");
        if (idx >= 0) {
            String rest = input.substring(idx + 5);
            int amp = rest.indexOf('&');
            if (amp >= 0) rest = rest.substring(0, amp);
            try { return URLDecoder.decode(rest, "UTF-8"); } catch (Exception ignored) { return rest; }
        }
        return input;
    }

    private static ClientConfig.AccountEntry exchangeCodeForAccount(String code) throws Exception {
        String body = "client_id=" + CLIENT_ID
            + "&code=" + URLEncoder.encode(code, "UTF-8")
            + "&grant_type=authorization_code"
            + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8");
        JsonObject ms = postForm("https://login.live.com/oauth20_token.srf", body);
        String msTok = ms.get("access_token").getAsString();
        String rTok = ms.has("refresh_token") ? ms.get("refresh_token").getAsString() : "";
        return authenticateWithMsAccessToken(msTok, rTok);
    }

    private static ClientConfig.AccountEntry refreshFromMsToken(String refreshToken) throws Exception {
        String body = "client_id=" + CLIENT_ID
            + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
            + "&grant_type=refresh_token"
            + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8");
        JsonObject ms = postForm("https://login.live.com/oauth20_token.srf", body);
        String msTok = ms.get("access_token").getAsString();
        String newRTok = ms.has("refresh_token") ? ms.get("refresh_token").getAsString() : refreshToken;
        return authenticateWithMsAccessToken(msTok, newRTok);
    }

    private static ClientConfig.AccountEntry authenticateWithMsAccessToken(String msToken, String refreshToken) throws Exception {
        JsonObject xblReq = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msToken);
        xblReq.add("Properties", props);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq);
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        JsonObject xstsReq = new JsonObject();
        JsonObject xProps = new JsonObject();
        xProps.addProperty("SandboxId", "RETAIL");
        JsonArray toks = new JsonArray(); toks.add(new JsonPrimitive(xblToken));
        xProps.add("UserTokens", toks);
        xstsReq.add("Properties", xProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq);
        if (xsts.has("XErr")) {
            long err = xsts.get("XErr").getAsLong();
            if (err == 2148916233L) throw new RuntimeException("This Microsoft account has no Xbox profile.");
            if (err == 2148916238L) throw new RuntimeException("Child account requires family approval.");
            throw new RuntimeException("XSTS error " + err);
        }
        String xstsToken = xsts.get("Token").getAsString();

        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mcAuth = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq);
        String mcToken = mcAuth.get("access_token").getAsString();
        long expiresAt = mcAuth.has("expires_in") ? (System.currentTimeMillis() + mcAuth.get("expires_in").getAsLong() * 1000L) : 0L;

        ClientConfig.AccountEntry entry = loginWithMcToken(mcToken, refreshToken);
        if (entry != null && expiresAt > 0L) { entry.expiresAt = expiresAt; ClientConfig.get().save(); }
        return entry;
    }

    private static ClientConfig.AccountEntry loginWithMcToken(String mcToken, String refreshToken) throws Exception {
        JsonObject prof = getJsonWithBearer("https://api.minecraftservices.com/minecraft/profile", mcToken);
        if (!prof.has("id") || !prof.has("name")) throw new RuntimeException("Account does not own Minecraft Java.");
        String uuid = formatUuid(prof.get("id").getAsString());
        String name = prof.get("name").getAsString();
        return ClientConfig.get().addOrUpdateAccount(name, "msa", "Main", uuid, mcToken, refreshToken, 0L);
    }

    private static JsonObject postForm(String urlStr, String formBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(12000); conn.setReadTimeout(12000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] bytes = formBody.getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream(); os.write(bytes); os.close();
        return readJsonResponse(conn);
    }

    private static JsonObject postJson(String urlStr, JsonObject body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(12000); conn.setReadTimeout(12000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream(); os.write(bytes); os.close();
        return readJsonResponse(conn);
    }

    private static JsonObject getJsonWithBearer(String urlStr, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(12000); conn.setReadTimeout(12000);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        return readJsonResponse(conn);
    }

    private static JsonObject readJsonResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String raw = readStream(is);
        if (code < 200 || code >= 300) {
            try {
                JsonObject err = new JsonParser().parse(raw).getAsJsonObject();
                if (err.has("XErr")) return err;
                if (err.has("error_description")) throw new RuntimeException(err.get("error_description").getAsString());
                if (err.has("errorMessage")) throw new RuntimeException(err.get("errorMessage").getAsString());
            } catch (RuntimeException re) { throw re; } catch (Exception ignored) {}
            throw new RuntimeException("HTTP " + code);
        }
        return new JsonParser().parse(raw).getAsJsonObject();
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "{}";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int r;
        while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
        is.close();
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}