package com.example.aichinese.dto.ai;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.util.List;

import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.PlanReason;

/**
 * The answers of Talk with Xiaomei. A reply and a summary carry {@code status} (SUCCESS, FAILED or UNAVAILABLE) and,
 * when it did not succeed, the fixed {@code message} that says why; the AI content is then empty.
 */
public final class XiaomeiResponses {

    private XiaomeiResponses() {
    }

    /**
     * Xiaomei's next line, and a tip when the student's last line could sound more natural: {@code naturalVersion}
     * (the better sentence) and {@code tip} (why), both null when there is nothing to correct.
     */
    public record Reply(AiRequestStatus status, String message, AiStoryLineResponse reply, String naturalVersion, String tip) {
        @Override
        public String toString() {
            return "Reply[status=" + status
                    + ", message=" + message
                    + ", reply=" + (reply == null ? "null" : "<present>")
                    + ", naturalVersion=" + redactLength(naturalVersion)
                    + ", tip=" + redactLength(tip)
                    + "]";
        }
    }

    /**
     * The end-of-chat summary: Xiaomei's goodbye, short feedback, the useful expressions she used and the words the
     * student used (translation = meaning), and what she will remember ({@code memory}, also when SparkBot could not
     * write the rest: the plan's goal is not written by the AI).
     */
    public record Summary(
            AiRequestStatus status,
            String message,
            AiStoryLineResponse goodbye,
            String feedback,
            List<AiStoryLineResponse> newExpressions,
            List<AiStoryLineResponse> wordsUsed,
            List<MemoryNote> memory) {
        @Override
        public String toString() {
            return "Summary[status=" + status
                    + ", message=" + message
                    + ", goodbye=" + (goodbye == null ? "null" : "<present>")
                    + ", feedback=" + redactLength(feedback)
                    + ", newExpressions=" + (newExpressions == null ? 0 : newExpressions.size())
                    + ", wordsUsed=" + (wordsUsed == null ? 0 : wordsUsed.size())
                    + ", memory=" + (memory == null ? 0 : memory.size())
                    + "]";
        }
    }

    /**
     * One note Xiaomei remembers ("Xiaomei will remember"), oldest first. The goal of the learner's plan has
     * {@code planReason}; its {@code text} is the plan's goal title, null for a plan built from rules (the page names the
     * reason then, as the plan pages do). Any other note is a short fact from a chat, in {@code text}.
     */
    public record MemoryNote(Long id, String text, PlanReason planReason) {
        @Override
        public String toString() {
            return "MemoryNote[id=" + id
                    + ", text=" + redactLength(text)
                    + ", planReason=" + planReason
                    + "]";
        }
    }
}
