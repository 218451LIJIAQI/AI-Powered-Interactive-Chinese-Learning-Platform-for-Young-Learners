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

/**
 * Persists learner-uploaded audio (pronunciation feedback) and image
 * (writing feedback) artefacts under the AI feedback subdirectory.
 *
 * <h2>Defence in depth</h2>
 * <p>Every upload goes through three independent checks before the
 * file ever lands on disk:</p>
 * <ol>
 *   <li>File-extension whitelist (lower-cased) — rejects obvious
 *       impostors.</li>
 *   <li>Content-Type header whitelist — rejects re-labelled types.</li>
 *   <li>Magic-byte / file-signature check via {@link UploadStorageSupport} —
 *       rejects payloads that disagree with their declared type, the
 *       classic upload-bypass vector.</li>
 * </ol>
 *
 * <p>All AI-uploaded blobs are tagged {@code accessLevel=PRIVATE} so
 * the file-download surface only serves them back to the originating
 * learner; cross-user reads are blocked at the access-control layer
 * regardless of file-name guessing.</p>
 */
@Service
public class AiFileStorageService {

    private static final Set<UploadStorageSupport.FileSignature> ALLOWED_AUDIO_SIGNATURES = Set.of(
            UploadStorageSupport.FileSignature.WEBM,
            UploadStorageSupport.FileSignature.WAV,
            UploadStorageSupport.FileSignature.MP3);

    private static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES = UploadStorageSupport.contentTypesOf(ALLOWED_AUDIO_SIGNATURES);

    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = UploadStorageSupport.extensionsOf(ALLOWED_AUDIO_SIGNATURES);

    private static final Set<UploadStorageSupport.FileSignature> ALLOWED_IMAGE_SIGNATURES = Set.of(
            UploadStorageSupport.FileSignature.PNG,
            UploadStorageSupport.FileSignature.JPEG);

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = UploadStorageSupport.contentTypesOf(ALLOWED_IMAGE_SIGNATURES);

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = UploadStorageSupport.extensionsOf(ALLOWED_IMAGE_SIGNATURES);

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
        String[] validated = validateMultipartFile(multipartFile,
                ALLOWED_AUDIO_CONTENT_TYPES, ALLOWED_AUDIO_EXTENSIONS,
                "Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.",
                ALLOWED_AUDIO_SIGNATURES,
                "Uploaded audio content does not match the declared WEBM/WAV/MP3 type.",
                uploadProperties.getAiAudioMaxFileSize());
        return storeFile(multipartFile, ownerUser, "PRIVATE", validated[0], validated[1]);
    }

    public UploadedFile storeWritingImage(MultipartFile multipartFile, User ownerUser) {
        String[] validated = validateMultipartFile(multipartFile,
                ALLOWED_IMAGE_CONTENT_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                "Unsupported image file type. Allowed types are PNG, JPG, and JPEG.",
                ALLOWED_IMAGE_SIGNATURES,
                "Uploaded image content does not match the declared PNG/JPG/JPEG type.",
                uploadProperties.getAiImageMaxFileSize());
        return storeFile(multipartFile, ownerUser, "PRIVATE", validated[0], validated[1]);
    }

    /**
     * Persists the validated multipart file to disk and records it in the
     * database.
     *
     * @param originalFileName safe file name already validated
     * @param extension        lower-cased extension already validated
     */
    private UploadedFile storeFile(
            MultipartFile multipartFile,
            User ownerUser,
            String accessLevel,
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

        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setOwnerUser(ownerUser);
        uploadedFile.setFileName(originalFileName);
        uploadedFile.setStoredFileName(storedFileName);
        uploadedFile.setFilePath(storedObject.location().toString());
        uploadedFile.setContentType(UploadStorageSupport.normalizeContentType(multipartFile.getContentType()));
        uploadedFile.setFileSize(multipartFile.getSize());
        uploadedFile.setAccessLevel(accessLevel);
        uploadedFile.setActive(true);
        return Objects.requireNonNull(
                metadataWriteTransaction.execute(status -> uploadedFileRepository.save(uploadedFile)),
                "uploaded file metadata");
    }

    /**
     * Validates size, extension, content-type, and magic-byte signature.
     *
     * @return {@code [originalFileName, extension]} — both already
     *         validated so {@link #storeFile} avoids re-extraction
     */
    private String[] validateMultipartFile(
            MultipartFile multipartFile,
            Set<String> allowedContentTypes,
            Set<String> allowedExtensions,
            String invalidTypeMessage,
            Set<UploadStorageSupport.FileSignature> allowedSignatures,
            String invalidSignatureMessage,
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
        if (!allowedExtensions.contains(extension)) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }

        String contentType = UploadStorageSupport.normalizeContentType(multipartFile.getContentType());
        if (!allowedContentTypes.contains(contentType)) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }

        UploadStorageSupport.validateFileSignature(
                multipartFile,
                extension,
                contentType,
                allowedSignatures,
                invalidSignatureMessage);

        return new String[]{originalFileName, extension};
    }

    private Path resolveAiDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(Path.of(uploadProperties.getDirectory(), "ai-feedback").toString());
    }
}
