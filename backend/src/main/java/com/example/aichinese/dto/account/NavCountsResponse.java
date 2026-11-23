package com.example.aichinese.dto.account;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Sidebar badge counts for the signed-in user: omitted keys are not applicable to the user's roles, keeping the JSON
 * shape per role identical while exposing a typed OpenAPI schema.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavCountsResponse(Long mistakes, Long aiReports, Long supportOpen) {
}
