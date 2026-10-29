package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.util.LanguageCodes;

/**
 * Interface languages (FRLANG01): en, ms and zh-CN; the legacy "zh" code is mapped to zh-CN.
 */
class LanguageCodesTests {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void normalisesSupportedLanguagesAndLegacyChinese() {
        assertThat(LanguageCodes.normalize("en")).isEqualTo("en");
        assertThat(LanguageCodes.normalize(" MS ")).isEqualTo("ms");
        assertThat(LanguageCodes.normalize("zh-CN")).isEqualTo("zh-CN");
        assertThat(LanguageCodes.normalize("zh")).isEqualTo("zh-CN");
        assertThat(LanguageCodes.normalize("zh_cn")).isEqualTo("zh-CN");
        assertThat(LanguageCodes.normalize("fr")).isNull();
        assertThat(LanguageCodes.normalize("  ")).isNull();
        assertThat(LanguageCodes.normalizeOrDefault("fr")).isEqualTo("en");
        assertThat(LanguageCodes.normalizeOrDefault(null)).isEqualTo("en");
    }

    @Test
    void normalizeRequestedMapsSupportedCodesAndKeepsOtherValuesForValidation() {
        assertThat(LanguageCodes.normalizeRequested(" zh ")).isEqualTo("zh-CN");
        assertThat(LanguageCodes.normalizeRequested("MS")).isEqualTo("ms");
        assertThat(LanguageCodes.normalizeRequested(" FR ")).isEqualTo("fr");
        assertThat(LanguageCodes.normalizeRequested("  ")).isEmpty();
        assertThat(LanguageCodes.normalizeRequested(null)).isNull();
    }

    @Test
    void studentRegistrationAcceptsZhCnAndMapsLegacyZh() {
        RegisterStudentRequest zhCn = request("zh-CN");
        RegisterStudentRequest legacy = request("zh");
        RegisterStudentRequest unsupported = request("fr");

        assertThat(VALIDATOR.validate(zhCn)).isEmpty();
        assertThat(zhCn.preferredLanguage()).isEqualTo("zh-CN");
        assertThat(VALIDATOR.validate(legacy)).isEmpty();
        assertThat(legacy.preferredLanguage()).isEqualTo("zh-CN");
        assertThat(VALIDATOR.validate(unsupported))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("preferredLanguage");
    }

    private static RegisterStudentRequest request(String language) {
        return new RegisterStudentRequest("Nur Aina", "aina@example.com", "Password123!", "Password123!", language);
    }
}
