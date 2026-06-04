package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.util.AiInputSanitizer;

@Service
public class ContentSafetyService {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyService.class);

    private static final String UNSAFE_CONTENT_MESSAGE = "Content contains language that is not suitable for young learners.";
    private static final String SEPARATORS = "[\\p{Z}\\p{Punct}\\p{S}_]*";
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            sensitivePattern("fuck"),
            sensitivePattern("shit"),
            sensitivePattern("bitch"),
            sensitivePattern("asshole"),
            sensitivePattern("porn"),
            sensitivePattern("porno"),
            sensitivePattern("nude"),
            sensitivePattern("naked"),
            sensitivePattern("rape"),
            sensitivePattern("rapist"),
            sensitivePattern("suicide"),
            sensitivePattern("self harm"),
            sensitivePattern("self-harm"),
            sensitivePattern("kill yourself"),
            sensitivePattern("cocaine"),
            sensitivePattern("heroin"),
            sensitivePattern("meth"),
            sensitivePattern("lucah"),
            sensitivePattern("bogel"),
            sensitivePattern("dadah"),
            sensitivePattern("bunuh diri"));
    private static final List<String> SENSITIVE_SUBSTRINGS = List.of(
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
            "去死");

    private final ContentSafetySettingsReader contentSafetySettingsReader;

    public ContentSafetyService(ContentSafetySettingsReader contentSafetySettingsReader) {
        this.contentSafetySettingsReader = Objects.requireNonNull(contentSafetySettingsReader, "contentSafetySettingsReader");
    }

    public String requireSafeText(String value, int maxLength, String requiredMessage) {
        String sanitized = sanitize(value, maxLength);
        if (sanitized == null) {
            throw new ResponseStatusException(BAD_REQUEST, requiredMessage);
        }
        requireSafeContent(sanitized);
        return sanitized;
    }

    public String optionalSafeText(String value, int maxLength) {
        String sanitized = sanitize(value, maxLength);
        if (sanitized != null) {
            requireSafeContent(sanitized);
        }
        return sanitized;
    }

    private void requireSafeContent(String value) {
        if (value == null || !contentSafetySettingsReader.sensitiveWordFilterEnabled()) {
            return;
        }
        String normalized = normalize(value);
        for (String sensitiveSubstring : SENSITIVE_SUBSTRINGS) {
            if (normalized.contains(sensitiveSubstring)) {
                log.warn("Content safety: substring match rejected (length={})", value.length());
                throw new ResponseStatusException(BAD_REQUEST, UNSAFE_CONTENT_MESSAGE);
            }
        }
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                log.warn("Content safety: pattern match rejected (length={})", value.length());
                throw new ResponseStatusException(BAD_REQUEST, UNSAFE_CONTENT_MESSAGE);
            }
        }
    }

    private String sanitize(String value, int maxLength) {
        return AiInputSanitizer.sanitize(value, maxLength);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static Pattern sensitivePattern(String term) {
        String normalizedTerm = normalize(term);
        StringBuilder pattern = new StringBuilder("(?iu)(?<![\\p{L}\\p{N}])");
        int[] codePoints = normalizedTerm.codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            int codePoint = codePoints[index];
            String character = new String(Character.toChars(codePoint));
            if (Character.isLetterOrDigit(codePoint)) {
                pattern.append(Pattern.quote(character));
                if (index < codePoints.length - 1) {
                    pattern.append(SEPARATORS);
                }
            } else {
                pattern.append("[\\p{Z}\\p{Punct}\\p{S}_]+");
            }
        }
        pattern.append("(?![\\p{L}\\p{N}])");
        return Pattern.compile(pattern.toString());
    }
}
