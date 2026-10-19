package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.ContentSafetySettingsReader;

/**
 * Enforces the admin "AI safety check" platform setting.
 *
 * <p>When the setting is enabled (the default), learner text is screened with the platform's
 * sensitive-word list before it is sent to the AI provider, and the provider's reply is
 * screened before it is shown. A blocked request never reaches the provider and does not use
 * the learner's AI quota; a blocked reply is withheld and logged as a failed request.</p>
 */
@Component
public class AiSafetyGuard {

    public static final String BLOCKED_INPUT_MESSAGE =
            "This request was not sent to the AI helper because it may contain unsafe content. "
                    + "Please keep AI requests to Mandarin learning.";

    public static final String BLOCKED_OUTPUT_MESSAGE =
            "The AI reply was withheld by the safety check. Please rephrase your Mandarin-learning question.";

    public static final String BLOCKED_INPUT_LOG = "Blocked by the AI safety check before the provider call.";

    public static final String BLOCKED_OUTPUT_LOG = "AI reply withheld by the AI safety check.";

    private final ContentSafetySettingsReader settingsReader;
    private final ContentSafetyService contentSafetyService;

    public AiSafetyGuard(ContentSafetySettingsReader settingsReader, ContentSafetyService contentSafetyService) {
        this.settingsReader = Objects.requireNonNull(settingsReader, "settingsReader");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
    }

    public boolean blocksInput(String learnerText) {
        return settingsReader.aiSafetyCheckEnabled() && contentSafetyService.containsSensitiveContent(learnerText);
    }

    public boolean blocksOutput(String providerText) {
        return settingsReader.aiSafetyCheckEnabled() && contentSafetyService.containsSensitiveContent(providerText);
    }
}
