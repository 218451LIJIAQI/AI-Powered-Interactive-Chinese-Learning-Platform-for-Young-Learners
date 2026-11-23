package com.example.aichinese.service.learn;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.support.ConfigFixtures;

class HintRunSignerTests {

    @Test
    void twoCopiesOfTheSameLessonOpenedOnTheSameClockTickHaveDistinctRunIds() {
        SecurityProperties settings = ConfigFixtures.securityProperties("unit-test-hint-signing-key", 60_000L, false);
        HintRunSigner signer = new HintRunSigner(settings);
        LocalDateTime start = LocalDateTime.of(2026, 11, 21, 9, 0);
        String scope = "LESSON#42#";
        String first = signer.sign(7L, scope, start);
        String second = signer.sign(7L, scope, start);

        String firstId = signer.requireId(7L, scope, start, first);
        String secondId = signer.requireId(7L, scope, start, second);
        assertThat(firstId).isNotNull().isNotEqualTo(secondId);
        assertThat(secondId).isNotNull();
    }
}
