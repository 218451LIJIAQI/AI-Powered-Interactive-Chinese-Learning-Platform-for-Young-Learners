package com.example.aichinese.dto.admin.activity;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.example.aichinese.entity.enums.ContentStatus;

import jakarta.validation.constraints.NotNull;

public record AdminActivityStatusUpdateRequest(
        @NotNull(message = "Target status is required.")
        ContentStatus status) {

    public static final Set<ContentStatus> ALLOWED_TARGET_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(
                    ContentStatus.DRAFT,
                    ContentStatus.PUBLISHED,
                    ContentStatus.ARCHIVED));
}
