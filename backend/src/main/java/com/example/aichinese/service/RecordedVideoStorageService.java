package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;

/**
 * Persists teacher-uploaded lesson videos with the same defence-in-depth
 * upload pipeline as {@link AiFileStorageService}: extension whitelist,
 * Content-Type whitelist, and magic-byte verification.
 *
 * <p>Allowed types are MP4, WEBM, and QuickTime (MOV). Stored under
 * the configured {@code app.upload.recorded-video-directory} with a
 * randomly generated stored-file-name so the on-disk path is not
 * guessable from the user-supplied original name. Marked
 * {@code accessLevel=PRIVATE} — playback goes through
 * {@link LiveLearningAccessService} which enforces enrolment + paid
 * access before issuing the streaming response.</p>
 */
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

    public RecordedVideoStorageService(UploadedFileRepository uploadedFileRepository, UploadProperties uploadProperties) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.uploadProperties = uploadProperties;
    }

    @Transactional
    public UploadedFile storeRecordedVideo(MultipartFile multipartFile, User ownerUser) {
        validateMultipartFile(multipartFile);

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.");
        String storedFileName = UploadStorageSupport.buildStoredFileName(extension);

        Path targetPath = UploadStorageSupport.storeMultipartFile(
                multipartFile,
                resolveBaseDirectory(),
                storedFileName,
                "Invalid upload target path.",
                "Recorded video could not be stored.");

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
                uploadProperties.getRecordedVideoDirectory(),
                uploadedFile,
                "Requested video could not be found.");
    }

    private void validateMultipartFile(MultipartFile multipartFile) {
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
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
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
                extension.toLowerCase(Locale.ROOT),
                contentType,
                ALLOWED_SIGNATURES,
                "Uploaded video content does not match the declared MP4/WEBM/MOV type.");
    }

    private Path resolveBaseDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(uploadProperties.getRecordedVideoDirectory());
    }
}
