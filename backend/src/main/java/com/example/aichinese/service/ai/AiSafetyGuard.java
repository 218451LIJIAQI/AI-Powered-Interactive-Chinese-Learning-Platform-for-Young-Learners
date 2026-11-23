package com.example.aichinese.service.ai;

import org.springframework.stereotype.Component;

import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.service.PlatformSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Enforces the two admin safety toggles on AI requests, as the AI Settings page and the Responsible AI page describe
 * them: the "Sensitive-word filter" blocks unsafe words in class content and AI requests, and the "AI safety check"
 * screens AI requests and replies before they are shown. Both use the platform's sensitive-word list.
 *
 * <p>Either toggle enables input screening. The gateway screens text and conversation history, and a recording's
 * target, before reserving quota. The AI safety check also screens provider replies, logging a withheld reply as a
 * failure.</p>
 */
@Component
@RequiredArgsConstructor
public class AiSafetyGuard {

    public static final String BLOCKED_INPUT_MESSAGE =
            "This request was not sent to the AI helper because it may contain unsafe content. "
                    + "Please keep AI requests to Mandarin learning.";

    public static final String BLOCKED_OUTPUT_MESSAGE =
            "The AI reply was withheld by the safety check. Please rephrase your Mandarin-learning question.";

    public static final String BLOCKED_INPUT_LOG = "Blocked by the AI safety check before the provider call.";

    public static final String BLOCKED_OUTPUT_LOG = "AI reply withheld by the AI safety check.";

    private final PlatformSettingsService platformSettingsService;
    private final ContentSafetyService contentSafetyService;

    /** Text that would be sent to the provider: screened while the word filter or the AI safety check is on. */
    public boolean blocksRequest(String text) {
        return contentSafetyService.containsSensitiveContent(text)
                && (platformSettingsService.aiSafetyCheckEnabled() || platformSettingsService.sensitiveWordFilterEnabled());
    }

    /** A provider reply: screened while the AI safety check is on. */
    public boolean blocksReply(String text) {
        return contentSafetyService.containsSensitiveContent(text) && platformSettingsService.aiSafetyCheckEnabled();
    }
}
