package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.core.io.Resource;

import com.example.aichinese.util.TextUtils;

public record StoredFileDownload(
        Resource resource,
        String originalFileName,
        String contentType) {

    public StoredFileDownload {
        resource = Objects.requireNonNull(resource, "resource");
        originalFileName = TextUtils.trimToNull(originalFileName);
        contentType = TextUtils.trimToNull(contentType);
    }
}
