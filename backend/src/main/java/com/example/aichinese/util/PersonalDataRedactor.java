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

    // ASCII-only character classes on purpose: Chinese text written directly next to an
    // identifier (for example "我的电话是0123456789") must stay intact and must not stop the match.
    private static final Pattern EMAIL = Pattern.compile(
            "(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");

    private static final Pattern NRIC = Pattern.compile(
            "(?<![0-9-])(?:[0-9]{6}-[0-9]{2}-[0-9]{4}|[0-9]{12})(?![0-9-])");

    private static final Pattern PHONE = Pattern.compile(
            "(?<![A-Za-z0-9+])(?:\\+[0-9]{1,3}[ -]?)?(?:\\([0-9]{2,4}\\)|[0-9]{2,4})(?:[ -]?[0-9]{3,4}){2}(?![A-Za-z0-9])");

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
        String withoutEmails = EMAIL.matcher(text).replaceAll(EMAIL_PLACEHOLDER);
        String withoutIdNumbers = NRIC.matcher(withoutEmails).replaceAll(ID_NUMBER_PLACEHOLDER);
        return PHONE.matcher(withoutIdNumbers).replaceAll(PHONE_PLACEHOLDER);
    }
}
