package com.example.aichinese.service;

import org.springframework.core.io.Resource;

public record StoredFileDownload(
        Resource resource,
        String originalFileName,
        String contentType) {
}
