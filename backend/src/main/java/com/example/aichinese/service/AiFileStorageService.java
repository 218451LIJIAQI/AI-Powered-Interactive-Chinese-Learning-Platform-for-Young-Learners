package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.service.storage.StorageProvider;
import com.example.aichinese.service.storage.StorageWriteRequest;
import com.example.aichinese.service.storage.StoredObject;

@Service
public class AiFileStorageService {

    private static final FileValidationPolicy AUDIO_POLICY = new FileValidationPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.WEBM,
                    UploadStorageSupport.FileSignature.WAV,
                    UploadStorageSupport.FileSignature.MP3),
            "Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.",
            "Uploaded audio content does not match the declared WEBM/WAV/MP3 type.");

    private static final FileValidationPolicy IMAGE_POLICY = new FileValidationPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.PNG,
                    UploadStorageSupport.FileSignature.JPEG),
            "Unsupported image file type. Allowed types are PNG, JPG, and JPEG.",
            "Uploaded image content does not match the declared PNG/JPG/JPEG type.");

    private final UploadProperties uploadProperties;
    private final UploadedFileRepository uploadedFileRepository;
    private final TransactionTemplate metadataWriteTransaction;
    private final StorageProvider storageProvider;

    public AiFileStorageService(
            UploadProperties uploadProperties,
            UploadedFileRepository uploadedFileRepository,
            PlatformTransactionManager transactionManager,
            StorageProvider storageProvider) {
        this.uploadProperties = Objects.requireNonNull(uploadProperties, "uploadProperties");
        this.uploadedFileRepository = Objects.requireNonNull(uploadedFileRepository, "uploadedFileRepository");
        this.metadataWriteTransaction = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider");
    }

    public UploadedFile storePronunciationAudio(MultipartFile multipartFile, User ownerUser) {
        ValidatedUpload validated = validateMultipartFile(
                multipartFile,
                AUDIO_POLICY,
                uploadProperties.getAiAudioMaxFileSize());
        return storeFile(multipartFile, ownerUser, validated.originalFileName(), validated.extension());
    }

    public UploadedFile storeWritingImage(MultipartFile multipartFile, User ownerUser) {
        ValidatedUpload validated = validateMultipartFile(
                multipartFile,
                IMAGE_POLICY,
                uploadProperties.getAiImageMaxFileSize());
        return storeFile(multipartFile, ownerUser, validated.originalFileName(), validated.extension());
    }

    private UploadedFile storeFile(
            MultipartFile multipartFile,
            User ownerUser,
            String originalFileName,
            String extension) {
        String storedFileName = UploadStorageSupport.buildStoredFileName(extension);

        StoredObject storedObject = storageProvider.store(
                multipartFile,
                new StorageWriteRequest(
                        resolveAiDirectory(),
                        storedFileName,
                        "Invalid upload target path.",
                        "Uploaded file could not be stored."));
        UploadStorageSupport.registerRollbackCleanup(storageProvider, storedObject);

        return UploadStorageSupport.saveUploadedFileMetadata(
                uploadedFile -> Objects.requireNonNull(
                        metadataWriteTransaction.execute(status -> uploadedFileRepository.save(uploadedFile)),
                        "uploaded file metadata"),
                storageProvider,
                storedObject,
                ownerUser,
                originalFileName,
                storedFileName,
                multipartFile.getContentType(),
                multipartFile.getSize());
    }

    private ValidatedUpload validateMultipartFile(
            MultipartFile multipartFile,
            FileValidationPolicy policy,
            DataSize maxFileSize) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file is required.");
        }

        if (multipartFile.getSize() > maxFileSize.toBytes()) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE,
                    "Uploaded file exceeds the maximum allowed file size.");
        }

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded file must include a valid extension.");
        if (!policy.allowedExtensions().contains(extension)) {
            throw new ResponseStatusException(BAD_REQUEST, policy.invalidTypeMessage());
        }

        String contentType = UploadStorageSupport.normalizeContentType(multipartFile.getContentType());
        if (!policy.allowedContentTypes().contains(contentType)) {
            throw new ResponseStatusException(BAD_REQUEST, policy.invalidTypeMessage());
        }

        UploadStorageSupport.validateFileSignature(
                multipartFile,
                extension,
                contentType,
                policy.allowedSignatures(),
                policy.invalidSignatureMessage());

        return new ValidatedUpload(originalFileName, extension);
    }

    private Path resolveAiDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(Path.of(uploadProperties.getDirectory(), "ai-feedback").toString());
    }

    private record FileValidationPolicy(
            Set<UploadStorageSupport.FileSignature> allowedSignatures,
            String invalidTypeMessage,
            String invalidSignatureMessage) {

        private Set<String> allowedContentTypes() {
            return UploadStorageSupport.contentTypesOf(allowedSignatures);
        }

        private Set<String> allowedExtensions() {
            return UploadStorageSupport.extensionsOf(allowedSignatures);
        }
    }

    private record ValidatedUpload(String originalFileName, String extension) {
    }
}
