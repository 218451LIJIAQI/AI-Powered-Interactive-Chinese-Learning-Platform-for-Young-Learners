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
     * Unicode punctuation (\p{P}, covering Chinese/CJK punctuation like 、 。 — · as well as ASCII punctuation),
     * symbols (\p{S}) and underscores.
     */
    private static final String SEPARATOR_CLASS = "[\\p{Z}\\s\\x{85}\\p{P}\\p{S}_]";
    private static final String REQUIRED_SEPARATOR = SEPARATOR_CLASS + "+";
    /** Non-space obfuscation separators (punctuation, symbols, underscores, line breaks, tabs) within a Latin word. */
    private static final String LATIN_OBFUSCATOR_CLASS = "[\\p{P}\\p{S}_\\r\\n\\t\\x{85}\\u2028\\u2029]";
    /** Chinese clause punctuation (after NFKC folds ，；： to ,;:) that separates benign words like "红色，情况". */
    private static final String CHINESE_CLAUSE_PUNCT = "[,;:]";
    /** Non-clause separators that always flag obfuscated Chinese terms even between surrounding Han characters. */
    private static final String CHINESE_NON_CLAUSE_SEP = "[\\p{Z}\\s\\x{85}\\p{P}\\p{S}_&&[^,;:]]";
    /**
     * Invisible Unicode format characters (zero-width spaces, BOM, bidi controls), combining grapheme joiner (U+034F),
     * variation selectors (U+FE00–U+FE0F), Hangul filler characters, and non-whitespace control characters cannot split
     * blocked words.
     */
    private static final Pattern INVISIBLE_CHARACTERS = Pattern.compile(
            "[\\p{Cf}\\u034F\\uFE00-\\uFE0F\\u115F\\u1160\\u3164\\uFFA0[\\p{Cc}&&[^\\r\\n\\t\\x{85}]]]+");
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
        return INVISIBLE_CHARACTERS.matcher(nfkc).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static Pattern sensitivePattern(String term, boolean latinWordBoundaries) {
        String normalizedTerm = normalize(term);
        if (!latinWordBoundaries) {
            return chineseSensitivePattern(normalizedTerm);
        }
        String[] words = normalizedTerm.split("\\s+");
        StringBuilder pattern = new StringBuilder("(?iu)(?<![\\p{IsLatin}\\p{N}])");
        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            if (wordIndex > 0) {
                pattern.append(REQUIRED_SEPARATOR);
            }
            pattern.append(latinWordAlternation(words[wordIndex]));
        }
        pattern.append("(?![\\p{IsLatin}\\p{N}])");
        return Pattern.compile(pattern.toString());
    }

    /**
     * Matches a single Latin word either intact, obfuscated with non-space separators (e.g. "p.o.r.n", "f—u—c—k",
     * "p\no\nr\nn"), or letter-by-letter separated with any separator including spaces (e.g. "s h i t").
     * Avoids splitting two legitimate words across a single plain space (such as English "Let's hit" -> "shit" or
     * toneless Pinyin "nu de" -> "nude") while still rejecting apostrophe-prefixed bypasses like "a'porn" or "a'p.o.r.n".
     */
    private static String latinWordAlternation(String word) {
        int[] codePoints = word.codePoints().toArray();
        if (codePoints.length <= 1) {
            return Pattern.quote(word);
        }
        StringBuilder nonSpaceObfuscated = new StringBuilder();
        StringBuilder fullySpaced = new StringBuilder();
        for (int i = 0; i < codePoints.length; i++) {
            String ch = Pattern.quote(new String(Character.toChars(codePoints[i])));
            nonSpaceObfuscated.append(ch);
            fullySpaced.append(ch);
            if (i + 1 < codePoints.length) {
                nonSpaceObfuscated.append(LATIN_OBFUSCATOR_CLASS).append('*');
                fullySpaced.append(REQUIRED_SEPARATOR);
            }
        }
        return "(?:" + nonSpaceObfuscated + "|" + fullySpaced + ")";
    }

    /**
     * Matches a Chinese sensitive term either contiguous, split by non-clause obfuscation separators (including
     * spaces, newlines, dots, dashes, middle-dot, enumeration comma 、, full stop 。, etc.), or split by clause
     * punctuation (， ； ：) when NOT flanked on both sides by Han characters (preventing benign clause-boundary false
     * positives like "红色，情况" or "角色，情绪" while still blocking standalone "色，情").
     */
    private static Pattern chineseSensitivePattern(String normalizedTerm) {
        int[] codePoints = normalizedTerm.codePoints().toArray();
        StringBuilder nonClause = new StringBuilder();
        StringBuilder withClause = new StringBuilder();
        for (int i = 0; i < codePoints.length; i++) {
            String ch = Pattern.quote(new String(Character.toChars(codePoints[i])));
            nonClause.append(ch);
            withClause.append(ch);
            if (i + 1 < codePoints.length) {
                nonClause.append(CHINESE_NON_CLAUSE_SEP).append('*');
                withClause.append("(?:").append(CHINESE_NON_CLAUSE_SEP).append("|").append(CHINESE_CLAUSE_PUNCT).append(")*");
            }
        }
        // Clause-punctuation match is allowed unless flanked on both sides by Han characters (two benign multi-char words).
        String clauseGuard = "(?:(?<!\\p{IsHan})" + withClause + "|" + withClause + "(?!\\p{IsHan}))";
        return Pattern.compile("(?iu)(?:" + nonClause + "|" + clauseGuard + ")");
    }
}
