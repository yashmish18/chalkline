package com.iet.dashboard.shared.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Accepts a host's single connection string, e.g. Render's Internal Database URL
 * {@code postgresql://user:pass@host[:port]/db}, as {@code DATABASE_URL}, and turns it into the JDBC
 * URL, username and password Spring expects. Explicit {@code SPRING_DATASOURCE_*} values still win.
 */
public class DatabaseUrlEnvironment implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = fromUrl(environment.getProperty("DATABASE_URL"));
        if (!props.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("databaseUrl", props));
        }
    }

    /** {@code app.db.*} (read by the prod datasource settings) for a postgres:// URL; empty when there is none or it isn't one. */
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
