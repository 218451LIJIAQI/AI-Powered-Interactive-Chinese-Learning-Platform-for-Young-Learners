package com.example.aichinese.dto.admin;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.support.SupportRequestSummaryResponse;
import com.example.aichinese.entity.enums.AiResponseReportReason;

/** The admin dashboard: what needs attention, each queue with its first items. */
public record AdminDashboardResponse(
        Queue<ClassDraft> classDrafts,
        Queue<AiReportItem> aiReports,
        Queue<SupportRequestSummaryResponse> support) {

    /** How many items wait in one queue, and the first of them. */
    public record Queue<T>(long count, List<T> items) {
    }

    /**
     * A draft class (newest first) with the number of its lessons that are not archived; {@code updatedAt} is the last
     * change to the class or any of its lessons ("edited 18 May").
     */
    public record ClassDraft(Long id, String title, int lessonCount, LocalDateTime updatedAt) {
    }

    public record AiReportItem(Long id, AiResponseReportReason reason, String studentName, LocalDateTime reportedAt) {

        @Override
        public String toString() {
            return "AiReportItem[id=" + id
                    + ", reason=" + reason
                    + ", studentName=" + redactValue(studentName)
                    + ", reportedAt=" + reportedAt + "]";
        }
    }
}
