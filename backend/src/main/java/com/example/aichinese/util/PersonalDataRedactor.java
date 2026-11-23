package com.example.aichinese.util;

import java.util.regex.Pattern;

/**
 * Removes obvious direct identifiers from learner-written text before it is sent to the
 * external AI provider (data minimisation, NFRSEC06).
 *
 * <p>Redacted, in this order:</p>
 * <ol>
 *   <li>e-mail addresses &rarr; {@value #EMAIL_PLACEHOLDER}</li>
 *   <li>Malaysian NRIC-like numbers ({@code ######-##-####} or 12 consecutive digits)
 *       &rarr; {@value #ID_NUMBER_PLACEHOLDER}</li>
 *   <li>phone numbers (optional {@code +} country code, then 8&ndash;12 digits written in
 *       2&ndash;4 / 3&ndash;4 / 3&ndash;4 digit groups, optionally separated by spaces,
 *       dashes or a bracketed area code) &rarr; {@value #PHONE_PLACEHOLDER}</li>
 * </ol>
 *
 * <p>The patterns are deliberately conservative: short numbers used in learning content
 * (counting, prices, years, times, dates, lesson ids) are left untouched. This is a
 * best-effort safeguard, not a guarantee; the platform also never adds the learner's name,
 * e-mail address or account id to prompts.</p>
 */
public final class PersonalDataRedactor {

    public static final String EMAIL_PLACEHOLDER = "[EMAIL]";

    public static final String PHONE_PLACEHOLDER = "[PHONE]";

    public static final String ID_NUMBER_PLACEHOLDER = "[ID_NUMBER]";

    // Keep adjacent Chinese characters and directly-following phone numbers/suffixes intact: possessive local part,
    // label repetition capped to RFC limits, and possessive TLD avoid both backtracking/recursion overflow and \b
    // mismatches when an email is glued directly to digits or underscores.
    static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]++@[A-Z0-9-]++(?:\\.[A-Z0-9-]++){0,63}\\.[A-Z]{2,}+");

    private static final String SEP = "[ \\u00A0\\u202F\\u3000\\u2011\\u2013－-]";

    private static final Pattern NRIC = Pattern.compile(
            "(?<![\\d+＋\\u2011\\u2013－-])(?:\\d{6}" + SEP + "\\d{2}" + SEP + "\\d{4}|\\d{12})(?![\\d\\u2011\\u2013－-])",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern PHONE = Pattern.compile(
            "(?<![A-Za-z\\d+＋])(?:"
                    + "[+＋]\\d{1,3}" + SEP + "?(?:(?:[(（]\\d{1,4}[)）]|\\d{1,4})(?:" + SEP + "?\\d{3,4}){2}|\\d{4}" + SEP + "?\\d{4})"
                    + "|(?:[(（]\\d{2,4}[)）]|\\d{2,4})(?:" + SEP + "?\\d{3,4}){2}"
                    + ")(?![A-Za-z\\d])",
            Pattern.UNICODE_CHARACTER_CLASS);

    private PersonalDataRedactor() {
    }

    /**
     * Returns the text with direct identifiers replaced by placeholders.
     *
     * @param text learner-supplied text; may be {@code null}
     * @return the redacted text, or {@code null} when the input is {@code null}
     */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String withoutEmails = EMAIL_PATTERN.matcher(text).replaceAll(EMAIL_PLACEHOLDER);
        String withoutIdNumbers = NRIC.matcher(withoutEmails).replaceAll(ID_NUMBER_PLACEHOLDER);
        return PHONE.matcher(withoutIdNumbers).replaceAll(PHONE_PLACEHOLDER);
    }
}
