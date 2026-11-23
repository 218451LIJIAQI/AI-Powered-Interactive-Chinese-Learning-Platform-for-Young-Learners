package com.example.aichinese.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import com.example.aichinese.util.LanguageCodes;

/**
 * The fixed words of every e-mail in English, Bahasa Melayu and Simplified Chinese: {@code i18n/emails.properties}
 * (English, also the fallback), {@code emails_ms.properties} and {@code emails_zh_CN.properties}. A text names its
 * values in braces ({@code {link}}); each value goes in exactly as given, never read as a text itself. A line break of
 * a text is written as the platform's line separator, like the rest of the e-mail. The language is a code of
 * {@link LanguageCodes}; a blank or unknown one reads English.
 */
@Component
public class EmailTexts {

    static final String BASENAME = "i18n/emails";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)\\}");

    private final ResourceBundleMessageSource bundles = new ResourceBundleMessageSource();

    public EmailTexts() {
        bundles.setBasename(BASENAME);
        bundles.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // A language without a file of its own reads the English file, never the one of the server's locale.
        bundles.setFallbackToSystemLocale(false);
    }

    /** The text {@code key} in {@code language}, without values. */
    public String text(String language, String key) {
        return text(language, key, Map.of());
    }

    /** The text {@code key} in {@code language} with its values; a value the text does not name is left out. */
    public String text(String language, String key, Map<String, String> values) {
        String template = bundles.getMessage(key, null, locale(language)).replace("\n", System.lineSeparator());
        return PLACEHOLDER.matcher(template).replaceAll(placeholder -> {
            String value = values.get(placeholder.group(1));
            if (value == null) {
                throw new IllegalStateException("The e-mail text " + key + " needs a value for " + placeholder.group());
            }
            return Matcher.quoteReplacement(value);
        });
    }

    private static Locale locale(String language) {
        return Locale.forLanguageTag(LanguageCodes.normalizeOrDefault(language));
    }
}
