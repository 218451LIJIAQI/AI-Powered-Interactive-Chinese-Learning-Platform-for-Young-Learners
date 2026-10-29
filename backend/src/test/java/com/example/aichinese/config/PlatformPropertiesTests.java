package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlatformPropertiesTests {

    @Test
    void trimsTheTimeZoneAndDefaultsToSingapore() {
        assertThat(new PlatformProperties("  Asia/Kuala_Lumpur  ").defaultTimeZone()).isEqualTo("Asia/Kuala_Lumpur");
        assertThat(new PlatformProperties(" ").defaultTimeZone()).isEqualTo("Asia/Singapore");
    }

    @Test
    void rejectsAnInvalidTimeZone() {
        assertThatThrownBy(() -> new PlatformProperties("Mars/Base"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_DEFAULT_TIME_ZONE");
    }
}
