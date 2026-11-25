package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.util.AfterCommit;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.FileNames;

/** Validates upload formats and coordinates managed files with their metadata transactions. */
final class UploadStorageSupport {

    private static final Logger log = LoggerFactory.getLogger(UploadStorageSupport.class);

    private static final int SIGNATURE_PREFIX_BYTES = 64;
    /** Length of {@code uploaded_files.file_name}, which stores the sanitised original name. */
    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 255;
    /** Returned when a stored-file name would leave the managed upload directory. */
    static final String INVALID_UPLOAD_TARGET_MESSAGE = "Invalid upload target path.";
    static final String INVALID_FILE_NAME_MESSAGE = "Uploaded file name is invalid.";
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom",
            "iso2",
            "iso5",
            "iso6",
            "mp41",
            "mp42",
            "mp71",
            "avc1",
            "hev1",
            "hvc1",
            "av01",
            "dash",
            "m4v ",
            "3gp4",
            "3gp5",
            "3gp6",
            "3g2a");

    private UploadStorageSupport() {
    }

    /**
     * Runs every upload check in the same order for each kind of upload: present, within the size limit, a
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

        validateFileSignature(multipartFile, extension, contentType, policy);

        return new ValidatedUpload(originalFileName, extension, contentType);
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

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        // Browsers can include codec parameters. Only the media type is stored and passed to the decoder.
        int parameters = contentType.indexOf(';');
        String mediaType = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return mediaType.strip().toLowerCase(Locale.ROOT);
    }

    /** The content must match a signature that accepts both the extension and the content type. */
    private static void validateFileSignature(
            MultipartFile multipartFile,
            String extension,
            String contentType,
            UploadPolicy policy) {
        List<FileSignature> candidates = policy.allowedSignatures().stream()
                .filter(signature -> signature.supportsExtension(extension))
                .filter(signature -> signature.supportsContentType(contentType))
                .toList();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, policy.signatureMismatchMessage());
        }
        byte[] prefix = readPrefix(multipartFile);
        if (candidates.stream().noneMatch(signature -> signature.matches(prefix))) {
            throw new ResponseStatusException(BAD_REQUEST, policy.signatureMismatchMessage());
        }
    }

    static Path resolveBaseDirectory(String configuredDirectory) {
        return Path.of(configuredDirectory).toAbsolutePath().normalize();
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
            // CREATE_NEW establishes ownership before cleanup can run; an existing target is never removed.
            OutputStream output = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try (output; InputStream inputStream = multipartFile.getInputStream()) {
                inputStream.transferTo(output);
            } catch (IOException | RuntimeException exception) {
                deleteQuietly(targetPath);
                throw exception;
            }
            return targetPath;
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, storageFailureMessage, exception);
        }
    }

    /** Captures upload identity and stored filename before bulk deletes detach potentially lazy proxies. */
    record ManagedFileRef(Long id, String storedFileName) {
        static ManagedFileRef of(UploadedFile file) {
            return new ManagedFileRef(file.getId(), file.getStoredFileName());
        }
    }

    /** Resolves the managed directory once and schedules one after-commit deletion callback for the whole batch. */
    static void deleteManagedFileRefs(String configuredDirectory, Collection<ManagedFileRef> uploadedFiles) {
        if (uploadedFiles == null || uploadedFiles.isEmpty()) {
            return;
        }
        Path baseDirectory;
        try {
            baseDirectory = resolveBaseDirectory(configuredDirectory).toRealPath();
        } catch (NoSuchFileException missingDirectory) {
            log.debug("Stored upload directory was already gone ({} upload(s)).", uploadedFiles.size());
            return;
        } catch (IOException exception) {
            log.warn("Could not resolve upload directory while deleting {} upload(s): {}",
                    uploadedFiles.size(), exception.getClass().getSimpleName());
            ExceptionLogUtils.debugOrigin(log, exception);
            return;
        }
        List<Path> paths = new ArrayList<>(uploadedFiles.size());
        for (ManagedFileRef uploadedFile : uploadedFiles) {
            if (uploadedFile == null || !StringUtils.hasText(uploadedFile.storedFileName())) {
                continue;
            }
            Optional<Path> target = tryResolveManagedPath(baseDirectory, uploadedFile.storedFileName());
            if (target.isEmpty()) {
                log.warn("Rejected invalid stored file name while deleting upload {}.", uploadedFile.id());
            } else {
                paths.add(target.get());
            }
        }
        if (!paths.isEmpty()) {
            AfterCommit.run(() -> paths.forEach(UploadStorageSupport::deleteQuietly));
        }
    }

    /**
     * Removes a stored file (an interrupted copy, a rolled-back upload, a committed deletion); a failure is only logged,
     * by file name and exception type, never the server path.
     */
    static void deleteQuietly(Path location) {
        try {
            Files.deleteIfExists(location);
        } catch (IOException exception) {
            log.warn("Failed to delete stored upload {}: {}",
                    location.getFileName(),
                    exception.getClass().getSimpleName());
            ExceptionLogUtils.debugOrigin(log, exception);
        }
    }

    static StoredFileDownload loadManagedFile(
            String configuredDirectory,
            UploadedFile uploadedFile,
            String notFoundMessage) {
        Path filePath = managedFilePath(configuredDirectory, uploadedFile, notFoundMessage);
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        return new StoredFileDownload(
                new FileSystemResource(filePath),
                uploadedFile.getFileName(),
                uploadedFile.getContentType());
    }

    /** Where a metadata row's stored file must be: inside the real managed directory, or 404. */
    private static Path managedFilePath(
            String configuredDirectory,
            UploadedFile uploadedFile,
            String notFoundMessage) {
        if (uploadedFile == null || !StringUtils.hasText(uploadedFile.getStoredFileName())) {
            throw new ResponseStatusException(NOT_FOUND, notFoundMessage);
        }

        Path baseDirectory = realDirectory(resolveBaseDirectory(configuredDirectory), NOT_FOUND, notFoundMessage);
        return resolveManagedPath(baseDirectory, uploadedFile.getStoredFileName(), NOT_FOUND, notFoundMessage);
    }

    private static Path resolveManagedPath(
            Path baseDirectory,
            String storedFileName,
            HttpStatus invalidStatus,
            String invalidMessage) {
        return tryResolveManagedPath(baseDirectory, storedFileName)
                .orElseThrow(() -> new ResponseStatusException(invalidStatus, invalidMessage));
    }

    private static Optional<Path> tryResolveManagedPath(Path baseDirectory, String storedFileName) {
        try {
            // Managed uploads are stored directly in this directory under a generated filename.
            String name = storedFileName.strip();
            Path target = baseDirectory.resolve(name).normalize();
            if (baseDirectory.equals(target.getParent()) && target.getFileName().toString().equals(name)) {
                return Optional.of(target);
            }
        } catch (InvalidPathException invalidName) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Path realDirectory(Path directory, HttpStatus missingStatus, String missingMessage) {
        try {
            return directory.toRealPath();
        } catch (NoSuchFileException exception) {
            throw new ResponseStatusException(missingStatus, missingMessage, exception);
        } catch (IOException exception) {
            log.warn("Could not access upload directory: {}", exception.getClass().getSimpleName());
            ExceptionLogUtils.debugOrigin(log, exception);
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    missingStatus == INTERNAL_SERVER_ERROR ? missingMessage : "Stored upload could not be accessed.",
                    exception);
        }
    }

    private static String extractLastPathSegment(String originalFileName) {
        String stripped = originalFileName.strip();
        int lastSeparator = Math.max(stripped.lastIndexOf('/'), stripped.lastIndexOf('\\'));
        return stripped.substring(lastSeparator + 1);
    }

    private static byte[] readPrefix(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return inputStream.readNBytes(SIGNATURE_PREFIX_BYTES);
        } catch (IOException exception) {
            log.warn("Could not read uploaded file prefix: {}", exception.getClass().getSimpleName());
            ExceptionLogUtils.debugOrigin(log, exception);
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file could not be read.", exception);
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
        if (bytes.length < offset + expected.length()) {
            return false;
        }

        String actual = new String(bytes, offset, expected.length(), StandardCharsets.US_ASCII);
        return expected.equals(actual);
    }

    /**
     * Parses an ISO 14496-12 {@code ftyp} box (4-byte big-endian size, {@code "ftyp"}, 4-byte major brand, 4-byte
     * minor version, and zero or more 4-byte compatible brands) and tests whether its major brand or any compatible
     * brand inside the prefix matches {@code predicate}.
     */
    private static boolean hasFtypBrand(byte[] bytes, Predicate<String> brandPredicate) {
        if (bytes.length < 16 || !hasAsciiAt(bytes, 4, "ftyp")) {
            return false;
        }
        long boxSize = ((bytes[0] & 0xFFL) << 24)
                | ((bytes[1] & 0xFFL) << 16)
                | ((bytes[2] & 0xFFL) << 8)
                | (bytes[3] & 0xFFL);
        if (boxSize < 16 || (boxSize - 16) % 4 != 0) {
            return false;
        }
        String majorBrand = readFourCc(bytes, 8);
        if (brandPredicate.test(majorBrand)) {
            return true;
        }
        int end = (int) Math.min(boxSize, bytes.length);
        for (int offset = 16; offset + 4 <= end; offset += 4) {
            if (brandPredicate.test(readFourCc(bytes, offset))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWebm(byte[] prefix) {
        return startsWith(prefix, 0x1A, 0x45, 0xDF, 0xA3) && new String(prefix, StandardCharsets.US_ASCII).contains("webm");
    }

    private static String readFourCc(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
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

    /** The sanitised original file name, lower-case extension and normalised content type of an accepted upload. */
    record ValidatedUpload(String originalFileName, String extension, String contentType) {
    }

    enum FileSignature {
        JPEG(Set.of(".jpg", ".jpeg"), Set.of("image/jpeg"),
                prefix -> startsWith(prefix, 0xFF, 0xD8, 0xFF)),
        PNG(Set.of(".png"), Set.of("image/png"),
                prefix -> startsWith(prefix, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        MP4(Set.of(".mp4"), Set.of("video/mp4"),
                prefix -> hasFtypBrand(prefix, MP4_BRANDS::contains)),
        WEBM_VIDEO(Set.of(".webm"), Set.of("video/webm"), UploadStorageSupport::isWebm),
        WEBM_AUDIO(Set.of(".webm"), Set.of("audio/webm"), UploadStorageSupport::isWebm),
        QUICKTIME(Set.of(".mov"), Set.of("video/quicktime"),
                prefix -> hasFtypBrand(prefix, "qt  "::equals)),
        WAV(Set.of(".wav"), Set.of("audio/wav", "audio/x-wav"),
                prefix -> hasAsciiAt(prefix, 0, "RIFF") && hasAsciiAt(prefix, 8, "WAVE")),
        MP3(Set.of(".mp3", ".mpeg"), Set.of("audio/mpeg", "audio/mp3"),
                prefix -> hasAsciiAt(prefix, 0, "ID3")
                        || (prefix.length >= 2 && (prefix[0] & 0xFF) == 0xFF && (prefix[1] & 0xE0) == 0xE0));

        private final Set<String> extensions;
        private final Set<String> contentTypes;
        private final Predicate<byte[]> signature;

        FileSignature(Set<String> extensions, Set<String> contentTypes, Predicate<byte[]> signature) {
            this.extensions = extensions;
            this.contentTypes = contentTypes;
            this.signature = signature;
        }

        boolean supportsExtension(String extension) {
            return extensions.contains(extension);
        }

        boolean supportsContentType(String contentType) {
            return contentTypes.contains(contentType);
        }

        boolean matches(byte[] prefix) {
            return signature.test(prefix);
        }
    }
}
