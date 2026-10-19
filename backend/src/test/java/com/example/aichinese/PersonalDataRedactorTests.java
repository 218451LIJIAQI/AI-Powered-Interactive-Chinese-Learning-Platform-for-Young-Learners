package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.PersonalDataRedactor;

/**
 * Unit coverage for the AI data-minimisation step (decision D27): direct identifiers typed by a
 * learner are replaced before any text is sent to the external AI provider, while ordinary
 * learning content (numbers, prices, years, times, Chinese text) is left untouched.
 */
class PersonalDataRedactorTests {

    @Test
    void redactsEmailAddresses() {
        assertThat(PersonalDataRedactor.redact("Email me at amy.tan+fyp@student.upm.edu.my please"))
                .isEqualTo("Email me at [EMAIL] please");
        assertThat(PersonalDataRedactor.redact("我的邮箱是abc_1@example.com。"))
                .isEqualTo("我的邮箱是[EMAIL]。");
    }

    @Test
    void redactsMalaysianNricLikeNumbers() {
        assertThat(PersonalDataRedactor.redact("My IC is 990101-14-5678."))
                .isEqualTo("My IC is [ID_NUMBER].");
        assertThat(PersonalDataRedactor.redact("IC 990101145678"))
                .isEqualTo("IC [ID_NUMBER]");
    }

    @Test
    void redactsPhoneNumbersInCommonMalaysianFormats() {
        assertThat(PersonalDataRedactor.redact("Call 012-345 6789")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("Call 0123456789")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("WhatsApp +60 12-345 6789 now")).isEqualTo("WhatsApp [PHONE] now");
        assertThat(PersonalDataRedactor.redact("Office (03) 8946 6000")).isEqualTo("Office [PHONE]");
        assertThat(PersonalDataRedactor.redact("我的电话是0123456789")).isEqualTo("我的电话是[PHONE]");
    }

    @Test
    void keepsOrdinaryLearningNumbersAndText() {
        String learningText = "我有3个苹果, it costs RM12.50, the class is at 10:30 on 2026-10-18, room 1203, "
                + "count 1 2 3 4 5 6 7 8 9 10. 你好 (nǐ hǎo)!";

        assertThat(PersonalDataRedactor.redact(learningText)).isEqualTo(learningText);
    }

    @Test
    void handlesNullAndEmptyInput() {
        assertThat(PersonalDataRedactor.redact(null)).isNull();
        assertThat(PersonalDataRedactor.redact("")).isEmpty();
    }

    @Test
    void redactsSeveralIdentifiersInOneMessage() {
        assertThat(PersonalDataRedactor.redact(
                "Name: Ali, email ali@example.com, phone 019-876 5432, IC 010203-10-1234"))
                .isEqualTo("Name: Ali, email [EMAIL], phone [PHONE], IC [ID_NUMBER]");
    }
}
