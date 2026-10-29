package com.example.aichinese.dto.teacher;

import jakarta.validation.constraints.AssertTrue;

/** Sends the teacher's application to the admins; the teacher must agree to the teacher expectations first. */
public record SubmitVerificationRequest(
        @AssertTrue(message = "Agree to follow what MandarinSpark expects from teachers before you submit.")
        boolean acceptedStandards) {
}
