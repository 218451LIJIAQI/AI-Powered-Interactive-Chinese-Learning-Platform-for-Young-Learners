package com.example.aichinese.dto.content;

/**
 * Centralised size / precision limits shared by the request DTOs in
 * this package.
 *
 * <p>The limits are mirrored from the persistence layer
 * ({@link com.example.aichinese.entity.LearningModule},
 * {@link com.example.aichinese.entity.Lesson},
 * {@link com.example.aichinese.entity.CoursePackage}) and the
 * {@code DECIMAL(10,2)} price schema column. Centralising them here
 * keeps the four request DTOs that share the same vocabulary
 * ({@link CreateModuleRequest}, {@link UpdateModuleRequest},
 * {@link CreateLessonRequest}, {@link UpdateLessonRequest},
 * {@link CreateCoursePackageRequest}, {@link UpdateCoursePackageRequest})
 * in lock-step with the schema and with each other, so a schema
 * widening only needs one source-code edit.</p>
 *
 * <h2>Why a class with private constructor and not an {@code enum}
 * or {@code interface}</h2>
 * <p>Bean Validation annotations
 * ({@link jakarta.validation.constraints.Size},
 * {@link jakarta.validation.constraints.Digits}) require their
 * member expressions to be compile-time constants of the exact
 * primitive type expected by the annotation. {@code public static
 * final int} fields on a final class with a private constructor
 * give us exactly that contract without the boxing tax of a wrapper
 * type and without the "constant interface" anti-pattern.</p>
 */
public final class ContentDtoConstants {

    /**
     * Maximum length for the editorial title of any owned-content
     * artefact (lesson / module / package). Mirrors the
     * {@code title VARCHAR(200) NOT NULL} schema columns on
     * {@code lessons}, {@code learning_modules}, and
     * {@code course_packages}.
     */
    public static final int TITLE_MAX_LENGTH = 200;

    /**
     * Maximum length for the short description field carried by
     * lessons via the {@code summary VARCHAR(1000)} schema column.
     * Lessons authoring favours brevity here; longer authored body
     * goes in {@link #LESSON_CONTENT_MAX_LENGTH}.
     */
    public static final int SHORT_DESCRIPTION_MAX_LENGTH = 1000;

    /**
     * Maximum length for the long description field carried by
     * modules and packages via the {@code description VARCHAR(2000)}
     * schema column. Lessons use the leaner
     * {@link #SHORT_DESCRIPTION_MAX_LENGTH} instead.
     */
    public static final int LONG_DESCRIPTION_MAX_LENGTH = 2000;

    /**
     * Maximum length for the rich-text authored body of a lesson.
     * Matches the {@code lesson_content LONGTEXT} schema column —
     * we cap at 10 000 characters at the API boundary so the wire
     * format stays predictable even though the column itself can
     * hold more. Rich media (videos, quizzes, etc.) is attached
     * separately and not gated by this cap.
     */
    public static final int LESSON_CONTENT_MAX_LENGTH = 10_000;

    /**
     * Maximum length for the cover-image reference (URL or relative
     * asset-store path). Mirrors the
     * {@code cover_image_url VARCHAR(255)} schema column.
     */
    public static final int COVER_IMAGE_URL_MAX_LENGTH = 255;

    /**
     * Maximum number of digits accepted to the left of the decimal
     * separator in a price input. Combined with
     * {@link #PRICE_FRACTION_DIGITS}, the pair encodes a
     * {@code DECIMAL(10,2)} schema column: 10 total digits split
     * into 8 integer digits and 2 fraction digits.
     */
    public static final int PRICE_INTEGER_DIGITS = 8;

    /**
     * Maximum number of digits accepted to the right of the
     * decimal separator in a price input.
     * See {@link #PRICE_INTEGER_DIGITS} for the paired contract.
     */
    public static final int PRICE_FRACTION_DIGITS = 2;

    /**
     * Maximum length for an admin moderation note. Mirrors the
     * {@code content_moderation_records.note} schema column and the
     * sanitizer's {@code MAX_MODERATION_NOTE_LENGTH} contract in
     * {@link com.example.aichinese.service.AdminContentReviewService}.
     */
    public static final int MODERATION_NOTE_MAX_LENGTH = 1000;

    private ContentDtoConstants() {
        // Utility class — no instances.
        throw new UnsupportedOperationException("ContentDtoConstants is a utility class.");
    }
}
