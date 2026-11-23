package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.util.AiInputSanitizer;

import lombok.RequiredArgsConstructor;

/**
 * The platform's sensitive-word list (English, Bahasa Melayu and Chinese; full-width letters and separators such as
 * {@code p.o.r.n} do not hide a word). While the admin "Sensitive-word filter" is on, the text methods refuse matching
 * class content with 400; {@code AiSafetyGuard} screens AI requests and replies with
 * {@link #containsSensitiveContent}.
 */
@Service
@RequiredArgsConstructor
public class ContentSafetyService {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyService.class);

    /*
     * Separators that cannot hide a word: Unicode spaces and line/paragraph separators (\p{Z}, which NFKC keeps for
     * U+1680, U+2028 and U+2029), ASCII whitespace including line breaks and tabs (\s), the next-line control U+0085,
     * punctuation, symbols and underscores.
     */
    private static final String SEPARATOR_CLASS = "[\\p{Z}\\s\\x{85}\\p{Punct}\\p{S}_]";
    private static final String SEPARATORS = SEPARATOR_CLASS + "*";
    private static final String REQUIRED_SEPARATOR = SEPARATOR_CLASS + "+";
    /** Invisible Unicode format characters (zero-width spaces, BOM, bidi controls) cannot split blocked words. */
    private static final Pattern FORMAT_CHARACTERS = Pattern.compile("\\p{Cf}+");
    private static final List<Pattern> SENSITIVE_PATTERNS = Stream.of(
                    "fuck", "shit", "bitch", "asshole", "porn", "porno", "nude", "naked", "rape", "rapist", "suicide",
                    "self harm", "kill yourself", "cocaine", "heroin", "meth", "lucah", "bogel", "dadah", "bunuh diri")
            .map(term -> sensitivePattern(term, true))
            .toList();
    private static final List<Pattern> CHINESE_SENSITIVE_PATTERNS = Stream.of(
                    "色情",
                    "裸聊",
                    "自杀",
                    "自殺",
                    "自残",
                    "自殘",
                    "毒品",
                    "强奸",
                    "強姦",
                    "杀了你",
                    "殺了你",
                    "去死")
            .map(term -> sensitivePattern(term, false))
            .toList();

    private final PlatformSettingsService platformSettingsService;

    /** The sanitised, screened text; 400 with {@code requiredMessage} when nothing is left of it. */
    public String requireSafeText(String value, int maxLength, String requiredMessage) {
        String sanitized = AiInputSanitizer.sanitize(value, maxLength);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, requiredMessage);
        }
        requireSafeContent(sanitized);
        return sanitized;
    }

    /** The sanitised, screened text, or null when nothing is left of it. */
    public String optionalSafeText(String value, int maxLength) {
        String sanitized = AiInputSanitizer.sanitize(value, maxLength);
        requireSafeContent(sanitized);
        return sanitized;
    }

    /**
     * Screens free text that is stored as entered (for example a lesson's flashcard phrases) with the
     * same sensitive-word rule used for lessons and quizzes; blank values are ignored.
     */
    public void screenText(String... values) {
        for (String value : values) {
            requireSafeContent(value);
        }
    }

    /** 400 for text with a sensitive word while the "Sensitive-word filter" is on; null and blank text pass. */
    private void requireSafeContent(String value) {
        if (containsSensitiveContent(value) && platformSettingsService.sensitiveWordFilterEnabled()) {
            log.warn("Content safety: sensitive content rejected (length={})", value.length());
            throw new ResponseStatusException(BAD_REQUEST,
                    "Content contains language that is not suitable for this learning platform.");
        }
    }

    /**
     * Checks the text against the sensitive-word list (English, Bahasa Melayu and Chinese)
     * regardless of the admin toggles. Callers decide which toggle applies.
     */
    public boolean containsSensitiveContent(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalize(value);
        return CHINESE_SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find())
                || SENSITIVE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    private static String normalize(String value) {
        String nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return FORMAT_CHARACTERS.matcher(nfkc).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static Pattern sensitivePattern(String term, boolean latinWordBoundaries) {
        String normalizedTerm = normalize(term);
        // Word boundaries only against Latin letters, digits, and English contractions/possessives ending in "'s"
        // (so "Let's hit the road" does not match "shit", while "我想看porn" still matches and "class" skips "ass").
        StringBuilder pattern = new StringBuilder(
                latinWordBoundaries ? "(?iu)(?<![\\p{IsLatin}\\p{N}])(?<![\\p{IsLatin}]['’])" : "(?iu)");
        int[] codePoints = normalizedTerm.codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            int codePoint = codePoints[index];
            String character = new String(Character.toChars(codePoint));
            if (Character.isLetterOrDigit(codePoint)) {
                pattern.append(Pattern.quote(character));
                if (index + 1 < codePoints.length && Character.isLetterOrDigit(codePoints[index + 1])) {
                    pattern.append(SEPARATORS);
                }
            } else {
                pattern.append(REQUIRED_SEPARATOR);
            }
        }
        if (latinWordBoundaries) {
            pattern.append("(?![\\p{IsLatin}\\p{N}])");
        }
        return Pattern.compile(pattern.toString());
    }
}
