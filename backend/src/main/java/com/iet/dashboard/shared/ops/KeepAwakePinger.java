package com.iet.dashboard.shared.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Keeps a free Render instance from spinning down: Render sleeps a web service after 15 minutes
 * without inbound requests, so the app requests its own public health URL every 10 minutes.
 * Off unless {@code app.keep-awake.url} is set (prod sets it from Render's RENDER_EXTERNAL_URL).
 */
@Component
public class KeepAwakePinger {

    private static final Logger log = LoggerFactory.getLogger(KeepAwakePinger.class);

    private final String healthUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public KeepAwakePinger(@Value("${app.keep-awake.url:}") String baseUrl) {
        this.healthUrl = healthUrl(baseUrl);
        if (healthUrl != null) {
            log.info("Keep-awake: requesting {} every 10 minutes", healthUrl);
        }
    }

    /** {@code <base>/actuator/health}, or null when keep-awake is off. */
    static String healthUrl(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return base.startsWith("http") ? base + "/actuator/health" : null;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    public void ping() {
        if (healthUrl == null) {
            return;
        }
        try {
            HttpResponse<Void> response = http.send(
                    HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-awake ping: {}", response.statusCode());
        } catch (Exception e) {
            log.warn("Keep-awake ping to {} failed: {}", healthUrl, e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
