package com.example.aichinese.dto.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Shared request body for the admin content-moderation endpoints
 * exposed by {@link com.example.aichinese.controller.AdminCommunicationController}:
 * <ul>
 *   <li>{@code POST /api/v1/admin/moderation/hide}</li>
 *   <li>{@code POST /api/v1/admin/moderation/unpublish}</li>
 *   <li>{@code POST /api/v1/admin/moderation/archive}</li>
 *   <li>{@code POST /api/v1/admin/moderation/request-revision}</li>
 *   <li>{@code POST /api/v1/admin/moderation/restore}</li>
 * </ul>
 *
 * <p>Each endpoint maps the request to a corresponding
 * {@link com.example.aichinese.entity.enums.ContentModerationAction}
 * and persists a
 * {@link com.example.aichinese.entity.ContentModerationRecord}
 * (echoed back through {@link ModerationRecordResponse}). The
 * endpoint chosen — not the request body — determines the action
 * type, so the DTO intentionally has no {@code action} field.
 * This contrasts with the structured approval workflow
 * ({@link com.example.aichinese.dto.content.ContentApprovalRequest}
 * and friends) which is for the teacher-content lifecycle rather
 * than after-publish moderation.</p>
 *
 * <h2>{@link #targetType} / {@link #targetId}</h2>
 * <p>{@code (targetType, targetId)} polymorphically identifies the
 * affected resource. {@link #targetType} is the short discriminator
 * understood by
 * {@link com.example.aichinese.service.CommunicationTargetResolver};
 * the supported moderation values are {@code "LESSON"},
 * {@code "PACKAGE"} and {@code "TEACHER"} (case-insensitive),
 * resolved respectively to {@link com.example.aichinese.entity.Lesson},
 * {@link com.example.aichinese.entity.CoursePackage} and
 * {@link com.example.aichinese.entity.TeacherProfile}.
 * {@link #targetId} is the row id within that table. Unsupported
 * target types are rejected by the resolver with {@code 400} —
 * the {@link Size} cap of 40 only enforces a sane upper bound on
 * payload size before reaching the resolver.</p>
 *
 * <h2>{@link #reason}</h2>
 * <p>The admin's mandatory justification, capped at
 * {@value #MAX_REASON_LENGTH} characters to match the underlying
 * {@code content_moderation_records.note} column and the
 * {@code MAX_MODERATION_NOTE_LENGTH} constant inside
 * {@link com.example.aichinese.service.ContentModerationService}.
 * The service runs the value through
 * {@link com.example.aichinese.util.AiInputSanitizer} before
 * persisting, which strips {@code <|im_start|>}-style chat-template
 * sentinels, ASCII control characters, and re-trims whitespace.
 * Inputs that sanitise to an empty string are rejected with
 * {@code 400 Bad Request}.</p>
 *
 * <h2>Invariants enforced by Bean Validation</h2>
 * <ul>
 *   <li>{@link #targetType} is non-blank and {@code <= 40} chars.</li>
 *   <li>{@link #targetId} is non-{@code null} and strictly positive
 *       (rejecting {@code 0} / negative ids that could never match a
 *       row).</li>
 *   <li>{@link #reason} is non-blank and {@code <=}
 *       {@value #MAX_REASON_LENGTH} chars.</li>
 * </ul>
 *
 * @param targetType discriminator identifying the kind of resource
 *                   being moderated (e.g. {@code "LESSON"});
 *                   non-blank, {@code <= 40} chars
 * @param targetId   primary key of the affected resource within the
 *                   table implied by {@link #targetType}; must be
 *                   strictly positive
 * @param reason     admin-supplied human-readable justification;
 *                   non-blank, {@code <=} {@value #MAX_REASON_LENGTH}
 *                   chars; sanitised server-side before persistence
 */
public record CreateModerationActionRequest(
        @NotBlank(message = "Target type is required.")
        @Size(max = MAX_TARGET_TYPE_LENGTH, message = "Target type must be 40 characters or fewer.")
        String targetType,

        @NotNull(message = "Target id is required.")
        @Positive(message = "Target id must be positive.")
        Long targetId,

        @NotBlank(message = "Reason is required.")
        @Size(max = MAX_REASON_LENGTH, message = "Reason must be 1000 characters or fewer.")
        String reason) {

    /**
     * Maximum number of characters accepted in {@link #targetType}.
     * Aligned with the realistic upper bound on the discriminator
     * tokens understood by
     * {@link com.example.aichinese.service.CommunicationTargetResolver}.
     */
    public static final int MAX_TARGET_TYPE_LENGTH = 40;

    /**
     * Maximum number of characters accepted in {@link #reason}.
     * Mirrors the {@code content_moderation_records.note} column and
     * the {@code MAX_MODERATION_NOTE_LENGTH} cap enforced by
     * {@link com.example.aichinese.service.ContentModerationService}
     * via {@link com.example.aichinese.util.AiInputSanitizer}.
     */
    public static final int MAX_REASON_LENGTH = 1000;
}
