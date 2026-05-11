package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;

final class UploadStorageSupport {

    private UploadStorageSupport() {
    }

    static String extractSafeOriginalFileName(String originalFileName, String invalidMessage) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }

        try {
            String cleaned = Path.of(originalFileName)
                    .getFileName()
                    .toString()
                    .replace('\r', '_')
                    .replace('\n', '_')
                    .trim();
            if (cleaned.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
            }
            return cleaned;
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }
    }

    static String getRequiredExtension(String fileName, String missingExtensionMessage) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            throw new ResponseStatusException(BAD_REQUEST, missingExtensionMessage);
        }

        return fileName.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    static String buildStoredFileName(String extension) {
        return UUID.randomUUID() + extension;
    }

    static Path resolveBaseDirectory(String configuredDirectory) {
        try {
            return Path.of(configuredDirectory).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Upload storage directory is misconfigured.");
        }
    }

    static Path storeMultipartFile(
            MultipartFile multipartFile,
            Path baseDirectory,
            String storedFileName,
            String invalidTargetMessage,
            String storageFailureMessage) {
        try {
            Files.createDirectories(baseDirectory);

            Path targetPath = resolveManagedPath(baseDirectory, storedFileName, invalidTargetMessage);
            Files.copy(multipartFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, storageFailureMessage);
        }
    }

    static StoredFileDownload loadManagedFile(
            String configuredDirectory,
            UploadedFile uploadedFile,
            String notFoundMessage) {
        Path baseDirectory = resolveBaseDirectory(configuredDirectory);
        Path filePath = resolveManagedPath(baseDirectory, uploadedFile.getStoredFileName(), notFoundMessage);
        if (!Files.isRegularFile(filePath)) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        Resource resource = new FileSystemResource(filePath);
        return new StoredFileDownload(
                resource,
                uploadedFile.getFileName(),
                normalizeContentType(uploadedFile.getContentType()));
    }

    private static Path resolveManagedPath(Path baseDirectory, String storedFileName, String invalidMessage) {
        Path targetPath = baseDirectory.resolve(storedFileName).normalize();
        if (!targetPath.startsWith(baseDirectory)) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }
        return targetPath;
    }
}
