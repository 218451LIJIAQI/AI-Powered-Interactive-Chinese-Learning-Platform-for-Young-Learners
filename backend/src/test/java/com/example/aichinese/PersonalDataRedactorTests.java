package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.util.PersonalDataRedactor;

/**
 * Unit coverage for the AI data-minimisation step (NFRSEC06): direct identifiers typed by a
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
        assertThat(PersonalDataRedactor.redact("alice@gmail.com0123456789"))
                .isEqualTo("[EMAIL][PHONE]");
        assertThat(PersonalDataRedactor.redact("alice@example.com_work"))
                .isEqualTo("[EMAIL]_work");
    }

    @Test
    void redactsMalaysianNricLikeNumbers() {
        assertThat(PersonalDataRedactor.redact("My IC is 990101-14-5678."))
                .isEqualTo("My IC is [ID_NUMBER].");
        assertThat(PersonalDataRedactor.redact("IC 990101145678"))
                .isEqualTo("IC [ID_NUMBER]");
        assertThat(PersonalDataRedactor.redact("证件号９９０１０１－１４－５６７８已登记"))
                .isEqualTo("证件号[ID_NUMBER]已登记");
        assertThat(PersonalDataRedactor.redact("My IC is 990101 14 5678."))
                .isEqualTo("My IC is [ID_NUMBER].");
        assertThat(PersonalDataRedactor.redact("My IC is 990101\u201314\u20135678."))
                .isEqualTo("My IC is [ID_NUMBER].");
    }

    @Test
    void redactsPhoneNumbersInCommonMalaysianFormats() {
        assertThat(PersonalDataRedactor.redact("Call 012-345 6789")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("Call 0123456789")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("WhatsApp +60 12-345 6789 now")).isEqualTo("WhatsApp [PHONE] now");
        assertThat(PersonalDataRedactor.redact("Call +60 3-8946 6000")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("Call +60 (3) 8946 6000")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("Call +601112345678")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("Call +65 9123 4567")).isEqualTo("Call [PHONE]");
        assertThat(PersonalDataRedactor.redact("Office (03) 8946 6000")).isEqualTo("Office [PHONE]");
        assertThat(PersonalDataRedactor.redact("Office 012\u00A0345\u20116789")).isEqualTo("Office [PHONE]");
        assertThat(PersonalDataRedactor.redact("我的电话是0123456789")).isEqualTo("我的电话是[PHONE]");
        assertThat(PersonalDataRedactor.redact("请拨＋６０　（０３）８９４６－６０００转接"))
                .isEqualTo("请拨[PHONE]转接");
    }

    @Test
    void keepsOrdinaryLearningNumbersAndText() {
        String learningText = "我有3个苹果, it costs RM12.50, the class is at 10:30 on 2026-10-18, room 1203, "
                + "count 1 2 3 4 5 6 7 8 9 10. 你好 (nǐ hǎo)! ２０２６年";

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

    @Test
    void handlesLongRepeatedDotLabelsLinearlyWithoutStackOverflow() {
        String longLabels = "a@" + "a.".repeat(3_000) + "!";
        long startNanos = System.nanoTime();
        assertThat(PersonalDataRedactor.redact(longLabels)).isEqualTo(longLabels);
        assertThat(PersonalDataRedactor.redact("Reach me at a@b.com.I am here"))
                .isEqualTo("Reach me at [EMAIL].I am here");
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        assertThat(elapsedMs).isLessThan(1_000L);
    }
}
