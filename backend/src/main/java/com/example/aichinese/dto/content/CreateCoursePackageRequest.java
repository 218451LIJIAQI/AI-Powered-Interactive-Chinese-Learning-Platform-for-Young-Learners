package com.example.aichinese.dto.content;

import java.math.BigDecimal;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/packages} — an approved
 * teacher creates a new
 * {@link com.example.aichinese.entity.CoursePackage} (a curated
 * bundle of modules and/or lessons sold as a single unit, e.g.
 * "HSK 1 Foundation Pack").
 *
 * <p>Packages are created empty: items
 * ({@link com.example.aichinese.entity.PackageItem}) are added in
 * follow-up calls to {@code POST /packages/{id}/items} via
 * {@link PackageItemRequest}. This two-step authoring keeps the
 * create payload simple and lets the teacher save the package
 * shell, browse their library, and add items one-by-one without
 * losing partial work.</p>
 *
 * <h2>{@link #learningType}</h2>
 * <p>Unlike modules, packages <em>do</em> carry a
 * {@link LearningType}. This is the package-level "headline"
 * learning type shown on the catalogue card; individual lessons
 * inside the package may have their own type but the package's
 * top-level type tells the student "this bundle is primarily X".
 * The two-axis taxonomy
 * (level + category) plus the {@link #learningType} headline gives
 * the front-end three independent filters for the catalogue.</p>
 *
 * <p>See {@link CreateModuleRequest} for the shared metadata
 * vocabulary contract this DTO conforms to (title, description,
 * level, category, accessType, price, coverImageUrl).</p>
 */
public record CreateCoursePackageRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = 2000, message = "Description must not exceed 2000 characters.")
        String description,
        @NotNull(message = "Level is required.")
        @Positive(message = "Level id must be positive.")
        Long levelId,
        @NotNull(message = "Category is required.")
        @Positive(message = "Category id must be positive.")
        Long categoryId,
        @NotNull(message = "Learning type is required.")
        LearningType learningType,
        @NotNull(message = "Access type is required.")
        AccessType accessType,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0.")
        @Digits(integer = 8, fraction = 2, message = "Price must fit within 8 integer digits and 2 decimal places.")
        BigDecimal price,
        @Size(max = 255, message = "Cover image reference must not exceed 255 characters.")
        String coverImageUrl) {
}
