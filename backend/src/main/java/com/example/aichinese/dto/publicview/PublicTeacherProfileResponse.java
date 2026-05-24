package com.example.aichinese.dto.publicview;

import java.util.List;

/**
 * Public-facing teacher profile returned by
 * {@code GET /api/v1/public/teachers/{id}} — accessible to
 * unauthenticated visitors browsing the teacher directory.
 *
 * <p>Drives the public "Teacher detail" page reachable from the
 * course catalogue (see
 * {@link com.example.aichinese.dto.content.PublicTeacherSummaryResponse#teacherId()}).
 * Carries enough information to help a prospective student
 * decide whether to enrol in this teacher's content, without
 * leaking any internal moderation state.</p>
 *
 * <h2>Filtered visibility</h2>
 * <p>Only
 * {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 * teachers can be looked up via this endpoint — see
 * {@code PublicTeacherProfileService.getApprovedTeacherProfile}
 * which returns 404 for any other status. Hence
 * {@link #verified} is always {@code true} on the wire (it is
 * preserved as a boolean for symmetry with
 * {@link com.example.aichinese.dto.content.PublicTeacherSummaryResponse#verified()}).</p>
 *
 * <h2>Compared to private teacher DTOs</h2>
 * <p>Strictly leaner than
 * {@link com.example.aichinese.dto.teacher.TeacherProfileResponse}
 * (the teacher's own self-view) and
 * {@link com.example.aichinese.dto.admin.AdminTeacherDetailResponse}
 * (the admin's view). Drops:</p>
 * <ul>
 *   <li>Email / user-id / teacher-profile-id (PII / internal
 *       linkage).</li>
 *   <li>Full status enum (only the boolean badge survives).</li>
 *   <li>Verification documents and rejection reasons (admin
 *       moderation surface, not visitor surface).</li>
 *   <li>Audit timestamps.</li>
 * </ul>
 *
 * <p>{@link #displayName} is {@code users.display_name} (the
 * historical naming as documented on
 * {@code TeacherStudentSummaryResponse#fullName}).
 * {@link #bio} is the long-form description editable via
 * {@code UpdateTeacherProfileRequest#bio}.
 * {@link #courseCount} is the count of
 * {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}
 * lessons + packages owned by this teacher (modules are
 * not counted because they are not directly purchasable).</p>
 */
public record PublicTeacherProfileResponse(
        Long teacherId,
        String displayName,
        String bio,
        boolean verified,
        List<String> teachingCategories,
        int courseCount) {
}
