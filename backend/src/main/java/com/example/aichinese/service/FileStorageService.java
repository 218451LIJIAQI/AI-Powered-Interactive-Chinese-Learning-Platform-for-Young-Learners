package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.service.storage.StorageProvider;
import com.example.aichinese.service.storage.StorageReadRequest;
import com.example.aichinese.service.storage.StorageWriteRequest;
import com.example.aichinese.service.storage.StoredObject;

@Service
public class FileStorageService {

    private static final Set<UploadStorageSupport.FileSignature> ALLOWED_SIGNATURES = Set.of(
            UploadStorageSupport.FileSignature.PDF,
            UploadStorageSupport.FileSignature.JPEG,
            UploadStorageSupport.FileSignature.PNG);
    private static final Set<String> ALLOWED_CONTENT_TYPES = UploadStorageSupport.contentTypesOf(ALLOWED_SIGNATURES);
    private static final Set<String> ALLOWED_EXTENSIONS = UploadStorageSupport.extensionsOf(ALLOWED_SIGNATURES);

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadProperties uploadProperties;
    private final StorageProvider storageProvider;

    public FileStorageService(
            UploadedFileRepository uploadedFileRepository,
            UploadProperties uploadProperties,
            StorageProvider storageProvider) {
        this.uploadedFileRepository = Objects.requireNonNull(uploadedFileRepository, "uploadedFileRepository");
        this.uploadProperties = Objects.requireNonNull(uploadProperties, "uploadProperties");
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider");
    }

    @Transactional
    public UploadedFile storeVerificationDocument(MultipartFile multipartFile, User ownerUser) {
        String[] validated = validateMultipartFile(multipartFile);
        String originalFileName = validated[0];
        String extension = validated[1];
        String storedFileName = UploadStorageSupport.buildStoredFileName(extension);

        StoredObject storedObject = storageProvider.store(
                multipartFile,
                new StorageWriteRequest(
                        resolveBaseDirectory(),
                        storedFileName,
                        "Invalid upload target path.",
                        "Uploaded file could not be stored."));
        UploadStorageSupport.registerRollbackCleanup(storageProvider, storedObject);

        return UploadStorageSupport.saveUploadedFileMetadata(
                uploadedFileRepository::save,
                storageProvider,
                storedObject,
                ownerUser,
                originalFileName,
                storedFileName,
                multipartFile.getContentType(),
                multipartFile.getSize());
    }

    public StoredFileDownload loadAsDownload(UploadedFile uploadedFile) {
        return storageProvider.load(new StorageReadRequest(
                uploadProperties.getDirectory(),
                uploadedFile,
                "Requested file could not be found."));
    }

    private String[] validateMultipartFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Verification document file is required.");
        }

        if (multipartFile.getSize() > uploadProperties.getVerificationDocumentMaxFileSize().toBytes()) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed file size.");
        }

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded file must include a valid extension. Allowed types are PDF, JPG, JPEG, and PNG.");
        String normalizedExtension = extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
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
                normalizedExtension,
                contentType,
                ALLOWED_SIGNATURES,
                "Uploaded file content does not match the declared PDF/JPG/PNG type.");

        return new String[]{originalFileName, extension};
    }

    private Path resolveBaseDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(uploadProperties.getDirectory());
    }
}
