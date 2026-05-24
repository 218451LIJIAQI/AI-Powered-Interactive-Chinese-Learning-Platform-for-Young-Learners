package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.entity.enums.AiFeatureKey;

/**
 * Builds {@link AiProviderPrompt} objects with the platform's standard
 * child-safety + Mandarin-learning system instructions.
 *
 * <p>Centralising the system instruction here ensures every AI feature
 * inherits the same age-appropriate tone, language preference rules
 * ({@code en} / {@code ms} / {@code zh}), refusal of professional
 * pronunciation/handwriting claims, and human-review requirement for
 * any official content the LLM generates. Adding a new feature does
 * not require duplicating these guardrails — only a fresh
 * {@code userPrompt} body.</p>
 *
 * <p>The {@code expectJson} flag toggles a single instruction
 * ("Return valid JSON only ...") plus the OpenAI-compatible
 * {@code response_format} hint downstream, so generators that need a
 * structured shape get strict JSON without per-call repetition.</p>
 *
 * <h2>Implementation notes</h2>
 * <ul>
 *   <li>The base preamble is pre-joined once at class load
 *       ({@link #BASE_INSTRUCTIONS}) so building a prompt is a few
 *       string concatenations rather than an {@code ArrayList} fill.</li>
 *   <li>The {@code history} parameter is defensively copied into an
 *       unmodifiable {@link List}, so a caller that later mutates the
 *       original list cannot retroactively rewrite the prompt that
 *       has already been dispatched.</li>
 *   <li>Language normalisation uses exact-or-tagged-prefix matching
 *       ({@code ms}, {@code ms-…}, {@code ms_…}) so an unrelated
 *       locale tag like {@code mscool} no longer collapses to Bahasa
 *       Melayu.</li>
 * </ul>
 */
@Component
public class AiPromptBuilder {

    /** Canonical normalised tag for English (also the fallback when input is unrecognised). */
    static final String LANG_EN = "en";

    /** Canonical normalised tag for Bahasa Melayu. */
    static final String LANG_MS = "ms";

    /** Canonical normalised tag for Mandarin Chinese. */
    static final String LANG_ZH = "zh";

    /**
     * Pre-joined child-safety + Mandarin-learning preamble shared by
     * every AI feature. Stored as a single string (rather than a
     * {@code List<String>}) so the hot {@link #buildPrompt} path is a
     * few concatenations instead of a per-call list build + join.
     */
    private static final String BASE_INSTRUCTIONS = String.join(" ", List.of(
            "You are a friendly Mandarin Chinese learning assistant for children aged 7 to 12.",
            "Keep responses short, simple, encouraging, and age-appropriate.",
            "Use positive wording and avoid harsh correction.",
            "Support beginner Mandarin learners and include Chinese, pinyin, and translation when useful.",
            "Avoid adult, unsafe, political, or unrelated content.",
            "Do not claim professional pronunciation scoring, handwriting recognition, or stroke-order analysis.",
            "Any official learning content generated must still require human review before publication."));

    private static final String INSTRUCTION_ENGLISH =
            "Explain mainly in simple English.";

    private static final String INSTRUCTION_MALAY =
            "Explain mainly in simple Bahasa Melayu, while keeping Chinese learning content clear.";

    private static final String INSTRUCTION_CHINESE =
            "Explain mainly in simple Chinese suitable for beginners.";

    private static final String INSTRUCTION_JSON =
            "Return valid JSON only with no markdown fences or extra commentary.";

    private final AiProperties aiProperties;

    public AiPromptBuilder(AiProperties aiProperties) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
    }

    /**
     * Builds a fully-formed prompt for the supplied feature.
     *
     * @param featureKey         non-null feature identifier; its
     *                           {@link AiFeatureKey#name() enum name}
     *                           is what gets logged and surfaced as
     *                           {@link AiProviderPrompt#featureKey()}
     * @param languagePreference any locale tag (e.g. {@code "en"},
     *                           {@code "ms-MY"}, {@code "zh_Hant"});
     *                           normalised to one of {@link #LANG_EN}
     *                           / {@link #LANG_MS} / {@link #LANG_ZH}
     *                           and surfaced as
     *                           {@link AiProviderPrompt#languagePreference()}
     * @param userPrompt         non-null user-supplied body (the caller
     *                           is responsible for upstream sanitisation
     *                           via {@code AiInputSanitizer})
     * @param expectJson         when {@code true}, the prompt is
     *                           augmented with a "return JSON only"
     *                           instruction and the OpenAI-compatible
     *                           {@code response_format} hint
     * @param history            prior conversation turns; {@code null}
     *                           is treated as empty, and the list is
     *                           defensively copied into an unmodifiable
     *                           form so post-construction mutation by
     *                           the caller cannot mutate the prompt
     */
    public AiProviderPrompt buildPrompt(
            AiFeatureKey featureKey,
            String languagePreference,
            String userPrompt,
            boolean expectJson,
            List<AiProviderPrompt.AiMessage> history) {
        Objects.requireNonNull(featureKey, "featureKey");
        Objects.requireNonNull(userPrompt, "userPrompt");
        String language = normalizeLanguage(languagePreference);
        return new AiProviderPrompt(
                featureKey.name(),
                buildSystemInstruction(language, expectJson),
                userPrompt,
                language,
                aiProperties.getMaxOutputTokens(),
                expectJson,
                history == null ? List.of() : List.copyOf(history));
    }

    private String buildSystemInstruction(String normalizedLanguage, boolean expectJson) {
        String languageInstruction = switch (normalizedLanguage) {
            case LANG_MS -> INSTRUCTION_MALAY;
            case LANG_ZH -> INSTRUCTION_CHINESE;
            default -> INSTRUCTION_ENGLISH;
        };
        return expectJson
                ? BASE_INSTRUCTIONS + ' ' + languageInstruction + ' ' + INSTRUCTION_JSON
                : BASE_INSTRUCTIONS + ' ' + languageInstruction;
    }

    /**
     * Normalises a raw language preference (e.g. {@code "ms-MY"},
     * {@code "zh_Hant"}, {@code "EN"}) to one of {@link #LANG_EN} /
     * {@link #LANG_MS} / {@link #LANG_ZH}. Uses exact-or-tagged-prefix
     * matching so values like {@code "mscool"} or {@code "zhuangzi"}
     * cannot accidentally collapse to a Mandarin/Malay preference.
     */
    private static String normalizeLanguage(String languagePreference) {
        if (languagePreference == null) {
            return LANG_EN;
        }
        String value = languagePreference.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return LANG_EN;
        }
        if (value.equals(LANG_MS) || value.startsWith("ms-") || value.startsWith("ms_")) {
            return LANG_MS;
        }
        if (value.equals(LANG_ZH) || value.startsWith("zh-") || value.startsWith("zh_")) {
            return LANG_ZH;
        }
        return LANG_EN;
    }
}
