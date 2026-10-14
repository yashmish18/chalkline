package com.iet.dashboard.shared.ops;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class KeepAwakePingerTest {

    @Test
    void pingsTheHealthEndpointOfThePublicUrlOnlyWhenOneIsSet() {
        assertThat(KeepAwakePinger.healthUrl("https://chalkline.onrender.com/"))
                .isEqualTo("https://chalkline.onrender.com/actuator/health");
        assertThat(KeepAwakePinger.healthUrl("")).isNull();
        assertThat(KeepAwakePinger.healthUrl(null)).isNull();
        assertThat(KeepAwakePinger.healthUrl("not-a-url")).isNull();
    }

    @Test
    void doesNothingWhenOff() {
        assertThatCode(() -> new KeepAwakePinger("").ping()).doesNotThrowAnyException();
    }
}
