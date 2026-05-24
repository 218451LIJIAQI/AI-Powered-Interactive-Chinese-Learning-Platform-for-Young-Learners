package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;

/**
 * Generic upload pipeline for the teacher verification document
 * surface: PDF / JPEG / PNG with the same defence-in-depth checks
 * (extension whitelist, content-type whitelist, magic-byte sniff)
 * used by every multipart endpoint.
 *
 * <p>Distinct from {@link AiFileStorageService} (audio / image for AI
 * feedback) and {@link RecordedVideoStorageService} (lesson video)
 * by accepted file types and destination directory; sharing the same
 * {@link UploadStorageSupport} ensures all three surfaces apply
 * identical security checks.</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<UploadStorageSupport.FileSignature> ALLOWED_SIGNATURES = Set.of(
            UploadStorageSupport.FileSignature.PDF,
            UploadStorageSupport.FileSignature.JPEG,
            UploadStorageSupport.FileSignature.PNG);
    private static final Set<String> ALLOWED_CONTENT_TYPES = UploadStorageSupport.contentTypesOf(ALLOWED_SIGNATURES);
    private static final Set<String> ALLOWED_EXTENSIONS = UploadStorageSupport.extensionsOf(ALLOWED_SIGNATURES);

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadProperties uploadProperties;

    public FileStorageService(UploadedFileRepository uploadedFileRepository, UploadProperties uploadProperties) {
        this.uploadedFileRepository = Objects.requireNonNull(uploadedFileRepository, "uploadedFileRepository");
        this.uploadProperties = Objects.requireNonNull(uploadProperties, "uploadProperties");
    }

    @Transactional
    public UploadedFile storeVerificationDocument(MultipartFile multipartFile, User ownerUser) {
        validateMultipartFile(multipartFile);

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded file must include a valid extension. Allowed types are PDF, JPG, JPEG, and PNG.");
        String storedFileName = UploadStorageSupport.buildStoredFileName(extension);

        Path targetPath = UploadStorageSupport.storeMultipartFile(
                multipartFile,
                resolveBaseDirectory(),
                storedFileName,
                "Invalid upload target path.",
                "Uploaded file could not be stored.");

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setOwnerUser(ownerUser);
        uploadedFile.setFileName(originalFileName);
        uploadedFile.setStoredFileName(storedFileName);
        uploadedFile.setFilePath(targetPath.toString());
        uploadedFile.setContentType(UploadStorageSupport.normalizeContentType(multipartFile.getContentType()));
        uploadedFile.setFileSize(multipartFile.getSize());
        uploadedFile.setAccessLevel("PRIVATE");
        uploadedFile.setActive(true);
        return uploadedFileRepository.save(uploadedFile);
    }

    public StoredFileDownload loadAsDownload(UploadedFile uploadedFile) {
        return UploadStorageSupport.loadManagedFile(
                uploadProperties.getDirectory(),
                uploadedFile,
                "Requested file could not be found.");
    }

    private void validateMultipartFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification document file is required.");
        }

        if (multipartFile.getSize() > uploadProperties.getMaxFileSize().toBytes()) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed file size.");
        }

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded file must include a valid extension. Allowed types are PDF, JPG, JPEG, and PNG.");
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported file type. Allowed types are PDF, JPG, JPEG, and PNG.");
        }

        String contentType = UploadStorageSupport.normalizeContentType(multipartFile.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported file content type. Allowed types are application/pdf, image/jpeg, and image/png.");
        }

        UploadStorageSupport.validateFileSignature(
                multipartFile,
                extension.toLowerCase(Locale.ROOT),
                contentType,
                ALLOWED_SIGNATURES,
                "Uploaded file content does not match the declared PDF/JPG/PNG type.");
    }

    private Path resolveBaseDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(uploadProperties.getDirectory());
    }
}
