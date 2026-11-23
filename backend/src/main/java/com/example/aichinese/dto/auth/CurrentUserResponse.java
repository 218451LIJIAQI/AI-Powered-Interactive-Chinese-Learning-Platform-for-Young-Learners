package com.example.aichinese.dto.auth;

import static com.example.aichinese.dto.DtoNormalization.redactValue;

import java.util.List;

public record CurrentUserResponse(
        Long id,
        String fullName,
        String email,
        String preferredLanguage,
        List<String> roles,
        StudentProfileSummary studentProfile) {

    public record StudentProfileSummary(Integer totalPoints) {
    }

    @Override
    public String toString() {
        return "CurrentUserResponse[id=" + id
                + ", fullName=" + redactValue(fullName)
                + ", email=" + redactValue(email)
                + ", preferredLanguage=" + preferredLanguage
                + ", roles=" + roles
                + ", studentProfile=" + studentProfile + "]";
    }
}
