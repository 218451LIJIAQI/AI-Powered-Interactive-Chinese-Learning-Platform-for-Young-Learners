package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.FileNames;

/**
 * Package-private upload pipeline helpers used by {@link FileStorageService}.
 *
 * <h2>Defence in depth</h2>
 * <p>Provides the three independent checks every upload runs:</p>
 * <ol>
 *   <li>Safe original file-name extraction — strips path components
 *       (so {@code ../x.pdf} becomes {@code x.pdf}), replaces CR/LF and
 *       other shell-tricky characters with {@code _}; rejects empty or
 *       over-long names.</li>
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
 */
final class UploadStorageSupport {

    private static final Logger log = LoggerFactory.getLogger(UploadStorageSupport.class);

    private static final int SIGNATURE_PREFIX_BYTES = 64;
    /** Length of {@code uploaded_files.file_name}, which stores the sanitised original name. */
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;
    /** Returned when a stored-file name would leave the managed upload directory. */
    static final String INVALID_UPLOAD_TARGET_MESSAGE = "Invalid upload target path.";
    static final String INVALID_FILE_NAME_MESSAGE = "Uploaded file name is invalid.";

    private UploadStorageSupport() {
    }

    /**
     * Runs every upload check in the same order for each storage service: present, within the size limit, a
     * safe original name with an allowed extension and content type, and a matching file signature.
     */
    static ValidatedUpload validateUpload(MultipartFile multipartFile, DataSize maxFileSize, UploadPolicy policy) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, policy.requiredMessage());
        }

        if (multipartFile.getSize() > maxFileSize.toBytes()) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, FriendlyErrorMessageResolver.uploadTooLargeMessage());
        }

        String originalFileName = extractSafeOriginalFileName(multipartFile.getOriginalFilename());
        String extension = getRequiredExtension(originalFileName, policy.missingExtensionMessage());
        if (policy.allowedSignatures().stream().noneMatch(signature -> signature.supportsExtension(extension))) {
            throw new ResponseStatusException(BAD_REQUEST, policy.unsupportedExtensionMessage());
        }

        String contentType = normalizeContentType(multipartFile.getContentType());
        if (policy.allowedSignatures().stream().noneMatch(signature -> signature.supportsContentType(contentType))) {
            throw new ResponseStatusException(BAD_REQUEST, policy.unsupportedContentTypeMessage());
        }

        validateFileSignature(
                multipartFile,
                extension,
                contentType,
                policy.allowedSignatures(),
                policy.signatureMismatchMessage());

        return new ValidatedUpload(originalFileName, extension);
    }

    /**
     * Stores a validated upload under a new random name and saves its metadata row.
     * The stored file is deleted again when the metadata cannot be saved or the surrounding transaction rolls back.
     */
    static UploadedFile storeUpload(
            MultipartFile multipartFile,
            ValidatedUpload validated,
            Path baseDirectory,
            String storageFailureMessage,
            UploadedFileRepository uploadedFileRepository,
            User ownerUser) {
        String storedFileName = UUID.randomUUID() + validated.extension();

        Path storedPath = storeMultipartFile(multipartFile, baseDirectory, storedFileName, storageFailureMessage);
        registerRollbackCleanup(storedPath);

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setOwnerUser(ownerUser);
        uploadedFile.setFileName(validated.originalFileName());
        uploadedFile.setStoredFileName(storedFileName);
        uploadedFile.setFilePath(storedPath.toString());
        uploadedFile.setContentType(normalizeContentType(multipartFile.getContentType()));
        uploadedFile.setFileSize(multipartFile.getSize());
        uploadedFile.setAccessLevel("PRIVATE");
        uploadedFile.setActive(true);
        try {
            return uploadedFileRepository.save(uploadedFile);
        } catch (RuntimeException exception) {
            deleteQuietly(storedPath);
            throw exception;
        }
    }

    static String extractSafeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_FILE_NAME_MESSAGE);
        }

        String cleaned = FileNames.sanitize(extractLastPathSegment(originalFileName));
        if (cleaned.isBlank() || cleaned.length() > MAX_ORIGINAL_FILE_NAME_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, INVALID_FILE_NAME_MESSAGE);
        }
        return cleaned;
    }

    private static String getRequiredExtension(String fileName, String missingExtensionMessage) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            throw new ResponseStatusException(BAD_REQUEST, missingExtensionMessage);
        }

        return fileName.substring(lastDot).toLowerCase(Locale.ROOT);
    }

    static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
    }

    private static void validateFileSignature(
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
            String storageFailureMessage) {
        try {
            Files.createDirectories(baseDirectory);

            Path targetPath = resolveManagedPath(
                    realDirectory(baseDirectory, INTERNAL_SERVER_ERROR, storageFailureMessage),
                    storedFileName,
                    BAD_REQUEST,
                    INVALID_UPLOAD_TARGET_MESSAGE);
            try (InputStream inputStream = multipartFile.getInputStream()) {
                Files.copy(inputStream, targetPath);
            }
            return targetPath;
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, storageFailureMessage, exception);
        }
    }

    private static void registerRollbackCleanup(Path storedPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(storedPath);
                }
            }
        });
    }

    /** Deletes a stored upload of the managed directory; a file that is already gone is ignored. */
    static void deleteManagedFile(String configuredDirectory, UploadedFile uploadedFile) {
        if (uploadedFile == null || uploadedFile.getStoredFileName() == null || uploadedFile.getStoredFileName().isBlank()) {
            return;
        }
        String missing = "Requested file could not be found.";
        try {
            Path baseDirectory = realDirectory(resolveBaseDirectory(configuredDirectory), NOT_FOUND, missing);
            deleteQuietly(resolveManagedPath(baseDirectory, uploadedFile.getStoredFileName(), NOT_FOUND, missing));
        } catch (ResponseStatusException alreadyGone) {
            log.debug("Stored upload {} was already gone.", uploadedFile.getId());
        }
    }

    static void deleteQuietly(Path location) {
        try {
            Files.deleteIfExists(location);
        } catch (IOException exception) {
            String fileName = location.getFileName() == null ? "-" : location.getFileName().toString();
            log.warn("Failed to delete stored object during upload rollback for name {}: {}",
                    fileName,
                    ExceptionLogUtils.exceptionType(exception));
            log.debug("Stored object cleanup stack trace for name {}.", fileName, exception);
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

    private static Path resolveManagedPath(
            Path baseDirectory,
            String storedFileName,
            HttpStatus invalidStatus,
            String invalidMessage) {
        Path targetPath = baseDirectory.resolve(storedFileName.strip()).normalize();
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

    private static boolean hasFtypBox(byte[] bytes) {
        return hasAsciiAt(bytes, 4, "ftyp");
    }

    private static String readBrand(byte[] bytes) {
        if (bytes.length < 12) {
            return "";
        }
        return new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
    }

    /** What one kind of upload accepts, with its message for each failed check (all share the "too large" one). */
    record UploadPolicy(
            Set<FileSignature> allowedSignatures,
            String requiredMessage,
            String missingExtensionMessage,
            String unsupportedExtensionMessage,
            String unsupportedContentTypeMessage,
            String signatureMismatchMessage) {
    }

    /** The sanitised original file name and lower-case extension of an upload that passed every check. */
    record ValidatedUpload(String originalFileName, String extension) {
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
                return startsWith(prefix, 0x1A, 0x45, 0xDF, 0xA3)
                        && new String(prefix, StandardCharsets.US_ASCII).contains("webm");
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

        boolean supportsExtension(String extension) {
            return extensions.contains(extension);
        }

        boolean supportsContentType(String contentType) {
            return contentTypes.contains(contentType);
        }

        abstract boolean matches(byte[] prefix);
    }
}
