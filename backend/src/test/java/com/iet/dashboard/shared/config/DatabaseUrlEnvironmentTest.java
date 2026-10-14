package com.iet.dashboard.shared.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentTest {

    @Test
    void readsRendersInternalUrlWithoutAPort() {
        assertThat(DatabaseUrlEnvironment.fromUrl("postgresql://chalkline_user:s3cr%40t@dpg-abc-a/chalkline"))
                .containsEntry("app.db.url", "jdbc:postgresql://dpg-abc-a:5432/chalkline")
                .containsEntry("app.db.username", "chalkline_user")
                .containsEntry("app.db.password", "s3cr@t");
    }

    @Test
    void keepsAnExplicitPortAndQuery() {
        assertThat(DatabaseUrlEnvironment.fromUrl(" postgres://u:p@host.example.com:6543/db?sslmode=require "))
                .containsEntry("app.db.url", "jdbc:postgresql://host.example.com:6543/db?sslmode=require");
    }

    @Test
    void ignoresAnythingElse() {
        assertThat(DatabaseUrlEnvironment.fromUrl(null)).isEmpty();
        assertThat(DatabaseUrlEnvironment.fromUrl("")).isEmpty();
        assertThat(DatabaseUrlEnvironment.fromUrl("jdbc:postgresql://x/db")).isEmpty();
    }
}
