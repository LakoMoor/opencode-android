package ai.opencode.android;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.util.Base64;

final class Api {
    private volatile HttpURLConnection active;
    private volatile boolean cancelled;
    interface Delta { void accept(String text); }
    void reset() { cancelled = false; }
    void cancel() { cancelled = true; HttpURLConnection c = active; if (c != null) c.disconnect(); }
    boolean cancelled() { return cancelled; }
    private HttpURLConnection open(Config config, String path, String method, JSONObject body) throws Exception {
        if (cancelled) throw new IOException(L.t("Request cancelled","Запрос остановлен"));
        String url = config.normalizedBase() + path;
        if (config.agent() && !config.directory.trim().isEmpty()) url += (path.contains("?") ? "&" : "?") + "directory=" + URLEncoder.encode(config.directory, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); active = c;
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(180000); c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        if (config.agent() && !config.password.isEmpty()) c.setRequestProperty("Authorization", "Basic " + Base64.encodeToString((config.username + ":" + config.password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        if (!config.agent() && !config.key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + config.key);
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            c.disconnect();
            throw new IOException("HTTP " + code + switch(code) { case 401,403 -> L.t(": check the key or password",": проверьте ключ или пароль"); case 404 -> L.t(": check the base URL and mode",": проверьте базовый URL и режим"); case 429 -> L.t(": rate limit, try again later",": лимит запросов, повторите позже"); default -> L.t(": the server rejected the request",": сервер отклонил запрос"); });
        }
        return c;
    }
    String request(Config config, String path, String method, JSONObject body) throws Exception {
        HttpURLConnection c = open(config, path, method, body);
        try (InputStream in = c.getInputStream()) { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); return out.toString("UTF-8"); } finally { c.disconnect(); active = null; }
    }
    void chat(Config config, JSONArray messages, Delta delta) throws Exception {
        JSONObject body = new JSONObject().put("model", config.model).put("messages", messages).put("stream", true);
        HttpURLConnection c = open(config, "/chat/completions", "POST", body);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            if (c.getContentType() != null && c.getContentType().contains("application/json")) {
                StringBuilder json = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) json.append(line);
                delta.accept(new JSONObject(json.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content"));
                return;
            }
            String line; StringBuilder event = new StringBuilder();
            while (!cancelled && (line = reader.readLine()) != null) {
                if (line.isEmpty()) { if (event.length() > 0) { if (consume(event.toString(), delta)) return; event.setLength(0); } }
                else if (line.startsWith("data:")) { if (event.length() > 0) event.append('\n'); event.append(line.substring(5).replaceFirst("^ +", "")); }
            }
            if (event.length() > 0 && !cancelled) consume(event.toString(), delta);
        } finally { c.disconnect(); active = null; }
    }
    private boolean consume(String data, Delta delta) throws Exception {
        if (data.trim().equals("[DONE]")) return true;
        JSONObject event = new JSONObject(data);
        if (event.has("error")) throw new IOException(L.t("The server returned a generation error","Сервер вернул ошибку генерации"));
        JSONArray choices = event.optJSONArray("choices");
        if (choices != null && choices.length() > 0) {
            JSONObject d = choices.getJSONObject(0).optJSONObject("delta");
            if (d != null && !d.isNull("content")) delta.accept(d.optString("content", ""));
        }
        return false;
    }
}
