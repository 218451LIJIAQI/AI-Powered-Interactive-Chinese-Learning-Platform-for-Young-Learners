package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import java.nio.file.Path;
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
public class RecordedVideoStorageService {

    private static final Set<UploadStorageSupport.FileSignature> ALLOWED_SIGNATURES = Set.of(
            UploadStorageSupport.FileSignature.MP4,
            UploadStorageSupport.FileSignature.WEBM,
            UploadStorageSupport.FileSignature.QUICKTIME);
    private static final Set<String> ALLOWED_CONTENT_TYPES = UploadStorageSupport.contentTypesOf(ALLOWED_SIGNATURES);
    private static final Set<String> ALLOWED_EXTENSIONS = UploadStorageSupport.extensionsOf(ALLOWED_SIGNATURES);

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadProperties uploadProperties;
    private final StorageProvider storageProvider;

    public RecordedVideoStorageService(
            UploadedFileRepository uploadedFileRepository,
            UploadProperties uploadProperties,
            StorageProvider storageProvider) {
        this.uploadedFileRepository = Objects.requireNonNull(uploadedFileRepository, "uploadedFileRepository");
        this.uploadProperties = Objects.requireNonNull(uploadProperties, "uploadProperties");
        this.storageProvider = Objects.requireNonNull(storageProvider, "storageProvider");
    }

    @Transactional
    public UploadedFile storeRecordedVideo(MultipartFile multipartFile, User ownerUser) {
        ValidatedUpload validated = validateMultipartFile(multipartFile);
        String storedFileName = UploadStorageSupport.buildStoredFileName(validated.extension());

        StoredObject storedObject = storageProvider.store(
                multipartFile,
                new StorageWriteRequest(
                        resolveBaseDirectory(),
                        storedFileName,
                        "Invalid upload target path.",
                        "Recorded video could not be stored."));
        UploadStorageSupport.registerRollbackCleanup(storageProvider, storedObject);

        return UploadStorageSupport.saveUploadedFileMetadata(
                uploadedFileRepository::save,
                storageProvider,
                storedObject,
                ownerUser,
                validated.originalFileName(),
                storedFileName,
                multipartFile.getContentType(),
                multipartFile.getSize());
    }

    public StoredFileDownload loadAsDownload(UploadedFile uploadedFile) {
        return storageProvider.load(new StorageReadRequest(
                uploadProperties.getRecordedVideoDirectory(),
                uploadedFile,
                "Requested video could not be found."));
    }

    private ValidatedUpload validateMultipartFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video file is required.");
        }

        if (multipartFile.getSize() > uploadProperties.getMaxVideoFileSize().toBytes()) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Uploaded video exceeds the maximum allowed file size.");
        }

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.");
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported video type. Allowed types are MP4, WEBM, and MOV.");
        }

        String contentType = UploadStorageSupport.normalizeContentType(multipartFile.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported video content type. Allowed types are video/mp4, video/webm, and video/quicktime.");
        }

        UploadStorageSupport.validateFileSignature(
                multipartFile,
                extension,
                contentType,
                ALLOWED_SIGNATURES,
                "Uploaded video content does not match the declared MP4/WEBM/MOV type.");

        return new ValidatedUpload(originalFileName, extension);
    }

    private Path resolveBaseDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(uploadProperties.getRecordedVideoDirectory());
    }

    private record ValidatedUpload(String originalFileName, String extension) {
    }
}
