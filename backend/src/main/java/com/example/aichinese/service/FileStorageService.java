package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;
import com.example.aichinese.util.AfterCommit;

import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Stores and streams the uploads that are kept (recorded lesson videos, support screenshots) and removes the screenshots
 * of a deleted account; checks the pronunciation recordings and handwriting photos of practice checks, which are only
 * read into memory to be scored and never stored.
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final UploadStorageSupport.UploadPolicy RECORDED_VIDEO_POLICY = new UploadStorageSupport.UploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.MP4,
                    UploadStorageSupport.FileSignature.WEBM_VIDEO,
                    UploadStorageSupport.FileSignature.QUICKTIME),
            "Recorded video file is required.",
            "Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.",
            "Unsupported video type. Allowed types are MP4, WEBM, and MOV.",
            "Unsupported video content type. Allowed types are video/mp4, video/webm, and video/quicktime.",
            "Uploaded video content does not match the declared MP4/WEBM/MOV type.");

    private static final UploadStorageSupport.UploadPolicy PRACTICE_AUDIO_POLICY = mediaUploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.WEBM_AUDIO,
                    UploadStorageSupport.FileSignature.WEBM_VIDEO,
                    UploadStorageSupport.FileSignature.WAV,
                    UploadStorageSupport.FileSignature.MP3),
            "Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.",
            "Uploaded audio content does not match the declared WEBM/WAV/MP3 type.");

    /** Handwriting photos of practice checks and support screenshots. */
    private static final UploadStorageSupport.UploadPolicy IMAGE_POLICY = mediaUploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.PNG,
                    UploadStorageSupport.FileSignature.JPEG),
            "Unsupported image file type. Allowed types are PNG, JPG, and JPEG.",
            "Uploaded image content does not match the declared PNG/JPG/JPEG type.");

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadProperties uploadProperties;
    private final EntityManager entityManager;

    /** Validates and streams a recorded lesson video to disk outside any database lock. */
    public StagedUpload stageRecordedVideo(MultipartFile multipartFile) {
        return stage(multipartFile, uploadProperties.getMaxVideoFileSize(), RECORDED_VIDEO_POLICY,
                uploadProperties.getRecordedVideoDirectory(),
                "Recorded video could not be stored.");
    }

    public StoredFileDownload loadRecordedVideo(UploadedFile uploadedFile) {
        return UploadStorageSupport.loadManagedFile(
                uploadProperties.getRecordedVideoDirectory(), uploadedFile, "Requested video could not be found.");
    }

    /** Removes the bytes after commit only when the last reference to this upload went with the draft. */
    @Transactional
    public void deleteUnusedRecordedVideo(UploadedFile file) {
        if (uploadedFileRepository.deleteIfUnreferenced(file.getId()) == 1) {
            entityManager.detach(file);
            UploadStorageSupport.deleteManagedFile(uploadProperties.getRecordedVideoDirectory(), file);
        }
    }

    /** Validates and streams a support screenshot to disk outside any database lock. */
    public StagedUpload stageSupportScreenshot(MultipartFile multipartFile) {
        return stage(multipartFile, uploadProperties.getSupportScreenshotMaxFileSize(), IMAGE_POLICY,
                supportDirectory(), "Uploaded file could not be stored.");
    }

    private static StagedUpload stage(MultipartFile file, DataSize maxSize, UploadStorageSupport.UploadPolicy policy,
            String directory, String failureMessage) {
        UploadStorageSupport.ValidatedUpload validated = UploadStorageSupport.validateUpload(
                file, maxSize, policy);
        String storedName = UUID.randomUUID() + validated.extension();
        Path path = UploadStorageSupport.storeMultipartFile(file, UploadStorageSupport.resolveBaseDirectory(directory),
                storedName, failureMessage);
        return new StagedUpload(path, validated, file.getSize());
    }

    /** Once metadata is saved, the database transaction owns the file and removes it on rollback. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UploadedFile saveStagedUpload(StagedUpload staged, User owner) {
        AfterCommit.onRollback(() -> UploadStorageSupport.deleteQuietly(staged.path));
        UploadedFile metadata = new UploadedFile();
        metadata.setOwnerUser(owner);
        metadata.setFileName(staged.validated.originalFileName());
        metadata.setStoredFileName(staged.path.getFileName().toString());
        metadata.setContentType(staged.validated.contentType());
        metadata.setFileSize(staged.size);
        UploadedFile saved = uploadedFileRepository.save(metadata);
        staged.saved = true;
        return saved;
    }

    /** Close with try-with-resources to remove an upload that never reached its metadata transaction. */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class StagedUpload implements AutoCloseable {
        private final Path path;
        private final UploadStorageSupport.ValidatedUpload validated;
        private final long size;
        private boolean saved;

        @Override
        public void close() {
            if (!saved) {
                UploadStorageSupport.deleteQuietly(path);
            }
        }
    }

    public StoredFileDownload loadSupportScreenshot(UploadedFile uploadedFile) {
        return UploadStorageSupport.loadManagedFile(supportDirectory(), uploadedFile, "Requested file could not be found.");
    }

    /** Removes a batch of support screenshots in one DB batch delete while scheduling file deletion after commit. */
    @Transactional
    public void deleteSupportScreenshots(Collection<UploadedFile> uploadedFiles) {
        if (uploadedFiles.isEmpty()) {
            return;
        }
        UploadStorageSupport.deleteManagedFiles(supportDirectory(), uploadedFiles);
        List<Long> ids = uploadedFiles.stream().map(UploadedFile::getId).toList();
        uploadedFileRepository.deleteAllByIdInBatch(ids);
    }

    /** A pronunciation recording (WEBM, WAV or MP3 within the AI audio size limit), read into memory. */
    public PracticeUpload readPracticeAudio(MultipartFile multipartFile) {
        return readPracticeUpload(multipartFile, uploadProperties.getAiAudioMaxFileSize(), PRACTICE_AUDIO_POLICY);
    }

    /** A handwriting photo (PNG or JPEG within the AI image size limit), read into memory. */
    public PracticeUpload readPracticeImage(MultipartFile multipartFile) {
        return readPracticeUpload(multipartFile, uploadProperties.getAiImageMaxFileSize(), IMAGE_POLICY);
    }

    /** Only the bytes and media type are needed to score a practice upload. */
    public record PracticeUpload(byte[] data, String contentType) {
    }

    private static PracticeUpload readPracticeUpload(
            MultipartFile multipartFile, DataSize maxFileSize, UploadStorageSupport.UploadPolicy policy) {
        UploadStorageSupport.ValidatedUpload validated =
                UploadStorageSupport.validateUpload(multipartFile, maxFileSize, policy);
        try {
            return new PracticeUpload(multipartFile.getBytes(), validated.contentType());
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file could not be read.");
        }
    }

    private String supportDirectory() {
        return Path.of(uploadProperties.getDirectory(), "support").toString();
    }

    /** Audio and image uploads share their messages; an unsupported extension or content type reads the same. */
    private static UploadStorageSupport.UploadPolicy mediaUploadPolicy(
            Set<UploadStorageSupport.FileSignature> allowedSignatures,
            String invalidTypeMessage,
            String invalidSignatureMessage) {
        return new UploadStorageSupport.UploadPolicy(
                allowedSignatures,
                "Uploaded file is required.",
                "Uploaded file must include a valid extension.",
                invalidTypeMessage,
                invalidTypeMessage,
                invalidSignatureMessage);
    }
}
