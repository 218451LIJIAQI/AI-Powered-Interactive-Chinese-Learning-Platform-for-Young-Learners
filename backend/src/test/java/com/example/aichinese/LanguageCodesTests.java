package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.util.LanguageCodes;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Interface languages (FRLANG01): exactly en, ms and zh-CN.
 */
class LanguageCodesTests {

    @Test
    void keepsSupportedLanguagesAndRefusesAnythingElse() {
        assertThat(LanguageCodes.normalize("en")).isEqualTo("en");
        assertThat(LanguageCodes.normalize(" ms ")).isEqualTo("ms");
        assertThat(LanguageCodes.normalize("zh-CN")).isEqualTo("zh-CN");
        assertThat(LanguageCodes.normalize("zh")).isNull();
        assertThat(LanguageCodes.normalize("zh_cn")).isNull();
        assertThat(LanguageCodes.normalize("MS")).isNull();
        assertThat(LanguageCodes.normalize("fr")).isNull();
        assertThat(LanguageCodes.normalize("  ")).isNull();
        assertThat(LanguageCodes.normalizeOrDefault("fr")).isEqualTo("en");
        assertThat(LanguageCodes.normalizeOrDefault(null)).isEqualTo("en");
    }

    @Test
    void studentRegistrationAcceptsTheSupportedLanguagesOnly() {
        RegisterStudentRequest zhCn = request("zh-CN");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertThat(validator.validate(zhCn)).isEmpty();
            assertThat(zhCn.preferredLanguage()).isEqualTo("zh-CN");
            for (String unsupported : new String[] { "zh", "fr" }) {
                assertThat(validator.validate(request(unsupported)))
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .containsExactly("preferredLanguage");
            }
        }
    }

    private static RegisterStudentRequest request(String language) {
        return new RegisterStudentRequest("Nur Aina", "aina@example.com", "Password123!", "Password123!", language);
    }
}
