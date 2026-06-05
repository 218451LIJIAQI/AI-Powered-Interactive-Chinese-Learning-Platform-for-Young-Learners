package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.service.storage.StorageProvider;
import com.example.aichinese.service.storage.StoredObject;

/**
 * Package-private upload pipeline helpers shared by every storage
 * service ({@link AiFileStorageService}, {@link RecordedVideoStorageService},
 * {@link FileStorageService}).
 *
 * <h2>Defence in depth</h2>
 * <p>Provides the three independent checks every upload runs:</p>
 * <ol>
 *   <li>Safe original file-name extraction — strips path components,
 *       CR/LF, and other shell-tricky characters; rejects empty or
 *       traversal-attempt names.</li>
 *   <li>Extension + Content-Type whitelist matching.</li>
 *   <li>Magic-byte / file-signature verification reading the first
 *       {@link #SIGNATURE_PREFIX_BYTES} bytes — defeats the classic
 *       "rename .php to .png" upload bypass.</li>
 * </ol>
 *
 * <h2>Storage</h2>
 * <p>Stored file names are {@link UUID}-derived so the on-disk path
 * is not guessable from the user-supplied name. The target directory
 * is canonicalised before write to defeat symlink-based escape
 * attempts. Reads stream through {@link FileSystemResource} so the
 * controller does not buffer entire blobs in memory.</p>
 *
 * <h2>Why a class instead of utility methods</h2>
 * <p>Package-private static methods keep the pipeline narrow — only
 * the three storage services in this package can call them, so a
 * future change cannot accidentally widen the surface to a less
 * defended path.</p>
 */
final class UploadStorageSupport {

    private static final int SIGNATURE_PREFIX_BYTES = 64;

    private UploadStorageSupport() {
    }

    static String extractSafeOriginalFileName(String originalFileName, String invalidMessage) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }

        String cleaned = sanitizeOriginalFileName(extractLastPathSegment(originalFileName));
        if (cleaned.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }
        return cleaned;
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

            Path targetPath = resolveManagedPath(
                    realDirectory(baseDirectory, INTERNAL_SERVER_ERROR, storageFailureMessage),
                    storedFileName,
                    invalidTargetMessage);
            try (InputStream inputStream = multipartFile.getInputStream()) {
                Files.copy(inputStream, targetPath);
            }
            return targetPath;
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, storageFailureMessage);
        }
    }

    static void registerRollbackCleanup(StorageProvider storageProvider, StoredObject storedObject) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageProvider.deleteQuietly(storedObject);
                }
            }
        });
    }

    static UploadedFile saveUploadedFileMetadata(
            Function<UploadedFile, UploadedFile> metadataSaver,
            StorageProvider storageProvider,
            StoredObject storedObject,
            User ownerUser,
            String originalFileName,
            String storedFileName,
            String contentType,
            long fileSize) {
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setOwnerUser(ownerUser);
        uploadedFile.setFileName(originalFileName);
        uploadedFile.setStoredFileName(storedFileName);
        uploadedFile.setFilePath(storedObject.location().toString());
        uploadedFile.setContentType(normalizeContentType(contentType));
        uploadedFile.setFileSize(fileSize);
        uploadedFile.setAccessLevel("PRIVATE");
        uploadedFile.setActive(true);
        try {
            return metadataSaver.apply(uploadedFile);
        } catch (RuntimeException exception) {
            storageProvider.deleteQuietly(storedObject);
            throw exception;
        }
    }

    static StoredFileDownload loadManagedFile(
            String configuredDirectory,
            UploadedFile uploadedFile,
            String notFoundMessage) {
        if (uploadedFile == null
                || uploadedFile.getStoredFileName() == null
                || uploadedFile.getStoredFileName().isBlank()) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        Path baseDirectory = realDirectory(resolveBaseDirectory(configuredDirectory), NOT_FOUND, notFoundMessage);
        Path filePath = resolveManagedPath(baseDirectory, uploadedFile.getStoredFileName(), NOT_FOUND, notFoundMessage);
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        Resource resource = new FileSystemResource(filePath);
        return new StoredFileDownload(
                resource,
                uploadedFile.getFileName(),
                normalizeContentType(uploadedFile.getContentType()));
    }

    private static Path resolveManagedPath(Path baseDirectory, String storedFileName, String invalidMessage) {
        return resolveManagedPath(baseDirectory, storedFileName, BAD_REQUEST, invalidMessage);
    }

    private static Path resolveManagedPath(
            Path baseDirectory,
            String storedFileName,
            HttpStatus invalidStatus,
            String invalidMessage) {
        Path targetPath = baseDirectory.resolve(storedFileName).normalize();
        if (!targetPath.startsWith(baseDirectory)) {
            throw new ResponseStatusException(invalidStatus, invalidMessage);
        }
        return targetPath;
    }

    private static Path realDirectory(Path directory, HttpStatus failureStatus, String failureMessage) {
        try {
            return directory.toRealPath();
        } catch (IOException exception) {
            throw new ResponseStatusException(failureStatus, failureMessage);
        }
    }

    private static String extractLastPathSegment(String originalFileName) {
        String stripped = originalFileName.strip();
        int lastUnixSeparator = stripped.lastIndexOf('/');
        int lastWindowsSeparator = stripped.lastIndexOf('\\');
        int lastSeparator = Math.max(lastUnixSeparator, lastWindowsSeparator);
        return stripped.substring(lastSeparator + 1);
    }

    private static String sanitizeOriginalFileName(String fileName) {
        StringBuilder sanitized = new StringBuilder(fileName.length());
        boolean previousWasWhitespace = false;

        for (int offset = 0; offset < fileName.length();) {
            int codePoint = fileName.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isUnsafeOriginalFileNameCodePoint(codePoint)) {
                appendReplacement(sanitized);
                previousWasWhitespace = false;
                continue;
            }

            if (Character.isWhitespace(codePoint)) {
                if (!previousWasWhitespace && !sanitized.isEmpty()) {
                    sanitized.append(' ');
                    previousWasWhitespace = true;
                }
                continue;
            }

            sanitized.appendCodePoint(codePoint);
            previousWasWhitespace = false;
        }

        String cleaned = sanitized.toString().trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }

    private static boolean isUnsafeOriginalFileNameCodePoint(int codePoint) {
        return Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || switch (codePoint) {
                    case '"', '*', '/', ':', '<', '>', '?', '\\', '|' -> true;
                    default -> false;
                };
    }

    private static void appendReplacement(StringBuilder sanitized) {
        if (sanitized.isEmpty() || sanitized.charAt(sanitized.length() - 1) != '_') {
            sanitized.append('_');
        }
    }

    private static byte[] readPrefix(MultipartFile multipartFile, String invalidTypeMessage) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return inputStream.readNBytes(SIGNATURE_PREFIX_BYTES);
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
