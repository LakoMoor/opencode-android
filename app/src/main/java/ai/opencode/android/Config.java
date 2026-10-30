package ai.opencode.android;

import java.net.URI;

final class Config {
    String mode = "direct", base = "", key = "", model = "", username = "opencode", password = "", directory = "";
    boolean agent() { return mode.equals("agent"); }
    String normalizedBase() {
        String value = base.trim().replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(value); } catch (Exception e) { throw new IllegalArgumentException(L.t("Invalid server URL","Некорректный URL сервера")); }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException(L.t("Enter an http(s) URL without a password, query or fragment","Укажите http(s) URL без пароля, query и fragment"));
        if (!agent() && (value.endsWith("/chat/completions") || value.endsWith("/models")))
            throw new IllegalArgumentException(L.t("Enter a base URL, e.g. http://192.168.1.10:1234/v1","Укажите базовый URL, например http://192.168.1.10:1234/v1"));
        return value;
    }
}
