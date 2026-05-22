package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;

final class UploadStorageSupport {

    private static final int SIGNATURE_PREFIX_BYTES = 64;

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

    static Set<String> extensionsOf(Set<FileSignature> signatures) {
        return signatures.stream()
                .flatMap(signature -> signature.extensions().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    static Set<String> contentTypesOf(Set<FileSignature> signatures) {
        return signatures.stream()
                .flatMap(signature -> signature.contentTypes().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    static void validateFileSignature(
            MultipartFile multipartFile,
            String extension,
            String contentType,
            Set<FileSignature> allowedSignatures,
            String invalidTypeMessage) {
        Set<FileSignature> compatibleSignatures = allowedSignatures.stream()
                .filter(signature -> signature.supportsExtension(extension))
                .filter(signature -> signature.supportsContentType(contentType))
                .collect(Collectors.toUnmodifiableSet());

        if (compatibleSignatures.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }

        byte[] prefix = readPrefix(multipartFile, invalidTypeMessage);
        boolean matches = compatibleSignatures.stream().anyMatch(signature -> signature.matches(prefix));
        if (!matches) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }
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
            Files.copy(multipartFile.getInputStream(), targetPath);
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

    private static byte[] readPrefix(MultipartFile multipartFile, String invalidTypeMessage) {
        try {
            return multipartFile.getInputStream().readNBytes(SIGNATURE_PREFIX_BYTES);
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }
    }

    private static boolean startsWith(byte[] bytes, int... values) {
        if (bytes.length < values.length) {
            return false;
        }

        for (int index = 0; index < values.length; index++) {
            if ((bytes[index] & 0xFF) != values[index]) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasAsciiAt(byte[] bytes, int offset, String expected) {
        if (offset < 0 || bytes.length < offset + expected.length()) {
            return false;
        }

        String actual = new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII);
        return expected.equals(actual);
    }

    private static boolean containsAscii(byte[] bytes, String expected) {
        if (bytes.length < expected.length()) {
            return false;
        }

        for (int index = 0; index <= bytes.length - expected.length(); index++) {
            if (hasAsciiAt(bytes, index, expected)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasFtypBox(byte[] bytes) {
        return hasAsciiAt(bytes, 4, "ftyp");
    }

    private static String readBrand(byte[] bytes) {
        if (bytes.length < 12) {
            return "";
        }
        return new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
    }

    enum FileSignature {
        PDF(Set.of(".pdf"), Set.of("application/pdf")) {
            @Override
            boolean matches(byte[] prefix) {
                return startsWith(prefix, 0x25, 0x50, 0x44, 0x46, 0x2D);
            }
        },
        JPEG(Set.of(".jpg", ".jpeg"), Set.of("image/jpeg")) {
            @Override
            boolean matches(byte[] prefix) {
                return startsWith(prefix, 0xFF, 0xD8, 0xFF);
            }
        },
        PNG(Set.of(".png"), Set.of("image/png")) {
            @Override
            boolean matches(byte[] prefix) {
                return startsWith(prefix, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            }
        },
        MP4(Set.of(".mp4"), Set.of("video/mp4")) {
            private final Set<String> compatibleBrands = Set.of(
                    "isom",
                    "iso2",
                    "mp41",
                    "mp42",
                    "avc1",
                    "dash",
                    "m4v ",
                    "m4a ",
                    "3gp4",
                    "3gp5",
                    "3gp6",
                    "3g2a");

            @Override
            boolean matches(byte[] prefix) {
                return hasFtypBox(prefix) && compatibleBrands.contains(readBrand(prefix));
            }
        },
        WEBM(Set.of(".webm"), Set.of("video/webm", "audio/webm")) {
            @Override
            boolean matches(byte[] prefix) {
                return startsWith(prefix, 0x1A, 0x45, 0xDF, 0xA3) && containsAscii(prefix, "webm");
            }
        },
        QUICKTIME(Set.of(".mov"), Set.of("video/quicktime")) {
            @Override
            boolean matches(byte[] prefix) {
                return hasFtypBox(prefix) && "qt  ".equals(readBrand(prefix));
            }
        },
        WAV(Set.of(".wav"), Set.of("audio/wav", "audio/x-wav")) {
            @Override
            boolean matches(byte[] prefix) {
                return hasAsciiAt(prefix, 0, "RIFF") && hasAsciiAt(prefix, 8, "WAVE");
            }
        },
        MP3(Set.of(".mp3", ".mpeg"), Set.of("audio/mpeg", "audio/mp3")) {
            @Override
            boolean matches(byte[] prefix) {
                return hasAsciiAt(prefix, 0, "ID3")
                        || (prefix.length >= 2 && (prefix[0] & 0xFF) == 0xFF && (prefix[1] & 0xE0) == 0xE0);
            }
        };

        private final Set<String> extensions;
        private final Set<String> contentTypes;

        FileSignature(Set<String> extensions, Set<String> contentTypes) {
            this.extensions = extensions;
            this.contentTypes = contentTypes;
        }

        Set<String> extensions() {
            return extensions;
        }

        Set<String> contentTypes() {
            return contentTypes;
        }

        boolean supportsExtension(String extension) {
            return extensions.contains(extension);
        }

        boolean supportsContentType(String contentType) {
            return contentTypes.contains(contentType);
        }

        abstract boolean matches(byte[] prefix);
    }
}
