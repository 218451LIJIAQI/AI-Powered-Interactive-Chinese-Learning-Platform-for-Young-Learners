package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;

/**
 * Stores and streams the uploads that are kept (teacher verification documents, recorded lesson videos), and checks
 * the pronunciation recordings and handwriting photos of practice checks, which are only read into memory to be scored
 * and never stored.
 */
@Service
public class FileStorageService {

    private static final UploadStorageSupport.UploadPolicy VERIFICATION_DOCUMENT_POLICY = new UploadStorageSupport.UploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.PDF,
                    UploadStorageSupport.FileSignature.JPEG,
                    UploadStorageSupport.FileSignature.PNG),
            "Verification document file is required.",
            "Uploaded file must include a valid extension. Allowed types are PDF, JPG, JPEG, and PNG.",
            "Unsupported file type. Allowed types are PDF, JPG, JPEG, and PNG.",
            "Unsupported file content type. Allowed types are application/pdf, image/jpeg, and image/png.",
            "Uploaded file content does not match the declared PDF/JPG/PNG type.");

    private static final UploadStorageSupport.UploadPolicy RECORDED_VIDEO_POLICY = new UploadStorageSupport.UploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.MP4,
                    UploadStorageSupport.FileSignature.WEBM,
                    UploadStorageSupport.FileSignature.QUICKTIME),
            "Recorded video file is required.",
            "Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.",
            "Unsupported video type. Allowed types are MP4, WEBM, and MOV.",
            "Unsupported video content type. Allowed types are video/mp4, video/webm, and video/quicktime.",
            "Uploaded video content does not match the declared MP4/WEBM/MOV type.");

    private static final UploadStorageSupport.UploadPolicy PRACTICE_AUDIO_POLICY = practiceUploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.WEBM,
                    UploadStorageSupport.FileSignature.WAV,
                    UploadStorageSupport.FileSignature.MP3),
            "Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.",
            "Uploaded audio content does not match the declared WEBM/WAV/MP3 type.");

    private static final UploadStorageSupport.UploadPolicy PRACTICE_IMAGE_POLICY = practiceUploadPolicy(
            Set.of(
                    UploadStorageSupport.FileSignature.PNG,
                    UploadStorageSupport.FileSignature.JPEG),
            "Unsupported image file type. Allowed types are PNG, JPG, and JPEG.",
            "Uploaded image content does not match the declared PNG/JPG/JPEG type.");

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadProperties uploadProperties;

    public FileStorageService(UploadedFileRepository uploadedFileRepository, UploadProperties uploadProperties) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.uploadProperties = uploadProperties;
    }

    @Transactional
    public UploadedFile storeVerificationDocument(MultipartFile multipartFile, User ownerUser) {
        return store(multipartFile, ownerUser, uploadProperties.getVerificationDocumentMaxFileSize(),
                VERIFICATION_DOCUMENT_POLICY, uploadProperties.getDirectory(), "Uploaded file could not be stored.");
    }

    @Transactional
    public UploadedFile storeRecordedVideo(MultipartFile multipartFile, User ownerUser) {
        return store(multipartFile, ownerUser, uploadProperties.getMaxVideoFileSize(),
                RECORDED_VIDEO_POLICY, uploadProperties.getRecordedVideoDirectory(), "Recorded video could not be stored.");
    }

    /** A pronunciation recording (WEBM, WAV or MP3 within the AI audio size limit), read into memory. */
    public PracticeUpload readPracticeAudio(MultipartFile multipartFile) {
        return readPracticeUpload(multipartFile, uploadProperties.getAiAudioMaxFileSize(), PRACTICE_AUDIO_POLICY);
    }

    /** A handwriting photo (PNG or JPEG within the AI image size limit), read into memory. */
    public PracticeUpload readPracticeImage(MultipartFile multipartFile) {
        return readPracticeUpload(multipartFile, uploadProperties.getAiImageMaxFileSize(), PRACTICE_IMAGE_POLICY);
    }

    /** A checked practice upload: its bytes, the sanitised original name and the normalised content type. */
    public record PracticeUpload(byte[] data, String fileName, String contentType) {
    }

    public StoredFileDownload loadVerificationDocument(UploadedFile uploadedFile) {
        return UploadStorageSupport.loadManagedFile(
                uploadProperties.getDirectory(),
                uploadedFile,
                "Requested file could not be found.");
    }

    public StoredFileDownload loadRecordedVideo(UploadedFile uploadedFile) {
        return UploadStorageSupport.loadManagedFile(
                uploadProperties.getRecordedVideoDirectory(),
                uploadedFile,
                "Requested video could not be found.");
    }

    private UploadedFile store(
            MultipartFile multipartFile,
            User ownerUser,
            DataSize maxFileSize,
            UploadStorageSupport.UploadPolicy policy,
            String directory,
            String storageFailureMessage) {
        UploadStorageSupport.ValidatedUpload validated =
                UploadStorageSupport.validateUpload(multipartFile, maxFileSize, policy);
        return UploadStorageSupport.storeUpload(
                multipartFile,
                validated,
                UploadStorageSupport.resolveBaseDirectory(directory),
                storageFailureMessage,
                uploadedFileRepository,
                ownerUser);
    }

    private static PracticeUpload readPracticeUpload(
            MultipartFile multipartFile, DataSize maxFileSize, UploadStorageSupport.UploadPolicy policy) {
        UploadStorageSupport.ValidatedUpload validated =
                UploadStorageSupport.validateUpload(multipartFile, maxFileSize, policy);
        try {
            return new PracticeUpload(
                    multipartFile.getBytes(),
                    validated.originalFileName(),
                    UploadStorageSupport.normalizeContentType(multipartFile.getContentType()));
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file could not be read.");
        }
    }

    /** A screenshot added to a Help & support request: PNG or JPG, up to 5 MB (the verification document limit). */
    @Transactional
    public UploadedFile storeSupportScreenshot(MultipartFile multipartFile, User ownerUser) {
        return store(multipartFile, ownerUser, uploadProperties.getVerificationDocumentMaxFileSize(),
                PRACTICE_IMAGE_POLICY, supportDirectory(), "Uploaded file could not be stored.");
    }

    public StoredFileDownload loadSupportScreenshot(UploadedFile uploadedFile) {
        return UploadStorageSupport.loadManagedFile(supportDirectory(), uploadedFile, "Requested file could not be found.");
    }

    /** Removes a support screenshot: the stored file and its record. */
    @Transactional
    public void deleteSupportScreenshot(UploadedFile uploadedFile) {
        UploadStorageSupport.deleteManagedFile(supportDirectory(), uploadedFile);
        uploadedFileRepository.delete(uploadedFile);
    }

    /** Removes a verification document upload: the stored file and its record. */
    @Transactional
    public void deleteVerificationDocument(UploadedFile uploadedFile) {
        UploadStorageSupport.deleteManagedFile(uploadProperties.getDirectory(), uploadedFile);
        uploadedFileRepository.delete(uploadedFile);
    }

    private String supportDirectory() {
        return Path.of(uploadProperties.getDirectory(), "support").toString();
    }

    /** Audio and image uploads share their messages; an unsupported extension or content type reads the same. */
    private static UploadStorageSupport.UploadPolicy practiceUploadPolicy(
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
