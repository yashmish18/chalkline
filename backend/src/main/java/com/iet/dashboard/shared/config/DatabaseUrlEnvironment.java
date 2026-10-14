package com.iet.dashboard.shared.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Database connection settings from a host's environment, made robust:
 * <ul>
 *   <li>Accepts a single connection string, e.g. Render's Internal Database URL
 *       {@code postgresql://user:pass@host[:port]/db}, as {@code DATABASE_URL}.</li>
 *   <li>Trims {@code SPRING_DATASOURCE_*} values: a space or newline pasted into a dashboard
 *       would otherwise make the password wrong.</li>
 *   <li>Logs where the connection settings came from, with a short fingerprint of the password
 *       (never the password), so a failed login can be diagnosed from the host's logs.</li>
 * </ul>
 * Explicit {@code SPRING_DATASOURCE_*} values win over {@code DATABASE_URL}.
 */
public class DatabaseUrlEnvironment implements EnvironmentPostProcessor {

    private final Log log;

    public DatabaseUrlEnvironment(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DatabaseUrlEnvironment.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> fromUrl = fromUrl(environment.getProperty("DATABASE_URL"));
        if (!fromUrl.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("databaseUrl", fromUrl));
        }

        Map<String, Object> trimmed = new HashMap<>();
        for (String key : new String[]{"SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"}) {
            String raw = environment.getProperty(key);
            if (raw != null && !raw.equals(raw.strip())) {
                trimmed.put(key, raw.strip());
                trimmed.put("spring.datasource." + key.substring("SPRING_DATASOURCE_".length()).toLowerCase(), raw.strip());
                log.warn(key + " had spaces or a line break around it; they were removed.");
            }
        }
        if (!trimmed.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("trimmedDatasource", trimmed));
        }

        if (environment.getProperty("DATABASE_URL") != null && fromUrl.isEmpty()) {
            log.warn("DATABASE_URL is set but is not a postgresql:// URL; it is ignored.");
        }
        String passwordEnv = environment.getProperty("SPRING_DATASOURCE_PASSWORD");
        String source = passwordEnv != null ? "SPRING_DATASOURCE_PASSWORD"
                : !fromUrl.isEmpty() ? "DATABASE_URL" : "none";
        String password = passwordEnv != null ? passwordEnv.strip() : (String) fromUrl.get("app.db.password");
        if (passwordEnv != null && fromUrl.containsKey("app.db.password")
                && !passwordEnv.strip().equals(fromUrl.get("app.db.password"))) {
            log.warn("SPRING_DATASOURCE_PASSWORD differs from the password in DATABASE_URL and wins. "
                    + "Delete SPRING_DATASOURCE_PASSWORD to use DATABASE_URL.");
        }
        if (passwordEnv != null || !fromUrl.isEmpty()) {
            log.info("Database password from " + source + ": " + describe(password)
                    + "; user " + firstNonNull(environment.getProperty("SPRING_DATASOURCE_USERNAME"), fromUrl.get("app.db.username"))
                    + "; url " + firstNonNull(environment.getProperty("SPRING_DATASOURCE_URL"), fromUrl.get("app.db.url")));
        }
    }

    /** "32 chars, fingerprint 1a2b3c4d": enough to compare with the dashboard, useless to an attacker. */
    static String describe(String password) {
        if (password == null || password.isEmpty()) {
            return "EMPTY";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return password.length() + " chars, fingerprint " + HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (Exception e) {
            return password.length() + " chars";
        }
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : (b != null ? b : "(not set)");
    }

    /** {@code app.db.*} (read by the prod datasource settings) for a postgres:// URL; empty otherwise. */
    static Map<String, Object> fromUrl(String raw) {
        Map<String, Object> props = new HashMap<>();
        if (raw == null || !raw.trim().matches("(?i)postgres(ql)?://.+")) {
            return props;
        }
        URI uri = URI.create(raw.trim());
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        props.put("app.db.url", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            props.put("app.db.username", decode(colon < 0 ? userInfo : userInfo.substring(0, colon)));
            if (colon >= 0) {
                props.put("app.db.password", decode(userInfo.substring(colon + 1)));
            }
        }
        return props;
    }

    private static String decode(String part) {
        return URLDecoder.decode(part, StandardCharsets.UTF_8);
    }
}
