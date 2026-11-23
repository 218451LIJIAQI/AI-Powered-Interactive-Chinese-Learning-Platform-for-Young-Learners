package com.example.aichinese.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** E-mail translations share keys and placeholders. Supplied values are inserted literally. */
class EmailTextsTests {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)\\}");
    /**
     * Texts without words to translate: the "Re:" subject of a reply, which every mail program reads, and a queue's
     * "name: count" line (Malay; the Chinese one has a full-width colon).
     */
    private static final Set<String> SAME_IN_EVERY_LANGUAGE = Set.of("supportReply.subject", "reviewQueueDigest.queue");
    private final EmailTexts texts = new EmailTexts();

    @Test
    void malayAndChineseTranslateEachKeyAndPreserveItsPlaceholders() throws IOException {
        Properties english = bundle("");
        assertThat(english).hasSizeGreaterThan(15);
        for (String suffix : new String[] { "_ms", "_zh_CN" }) {
            Properties translated = bundle(suffix);
            assertThat(translated.stringPropertyNames()).as(suffix).isEqualTo(english.stringPropertyNames());
            for (String key : english.stringPropertyNames()) {
                assertThat(placeholders(translated.getProperty(key))).as(suffix + " " + key)
                        .isEqualTo(placeholders(english.getProperty(key)));
                if (!SAME_IN_EVERY_LANGUAGE.contains(key)) {
                    assertThat(translated.getProperty(key)).as(suffix + " " + key).isNotEqualTo(english.getProperty(key));
                }
            }
        }
    }

    @Test
    void anUnknownOrMissingLanguageReadsEnglish() {
        assertThat(texts.text(null, "passwordReset.subject")).isEqualTo("Password reset request");
        assertThat(texts.text("fr", "passwordReset.subject")).isEqualTo("Password reset request");
        assertThat(texts.text("zh", "passwordReset.subject")).isEqualTo("Password reset request");
        assertThat(texts.text("ms", "passwordReset.subject")).isEqualTo("Permintaan tetapan semula kata laluan");
        assertThat(texts.text("zh-CN", "passwordReset.subject")).isEqualTo("密码重置请求");
    }

    @Test
    void valuesGoInExactlyAsGivenAndLineBreaksAreThePlatformsOwn() {
        String reply = "See {link} and $1 \\ here";

        assertThat(texts.text("en", "supportReply.body", Map.of("reply", reply)))
                .isEqualTo(reply + System.lineSeparator() + System.lineSeparator()
                        + "MandarinSpark support. Reply to this email or send us a message on the Help page.");
        assertThatThrownBy(() -> texts.text("en", "supportReply.body", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{reply}");
    }

    private static Properties bundle(String suffix) throws IOException {
        Properties properties = new Properties();
        ClassPathResource file = new ClassPathResource(EmailTexts.BASENAME + suffix + ".properties");
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
