package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.nio.file.Path;
import java.util.List;
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

@Service
public class AiFileStorageService {

    private static final Set<String> ALLOWED_AUDIO_CONTENT_TYPES = Set.of(
            "audio/webm",
            "audio/wav",
            "audio/x-wav",
            "audio/mpeg",
            "audio/mp3");

    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = Set.of(".webm", ".wav", ".mp3", ".mpeg");

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg");

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg");

    private final UploadProperties uploadProperties;
    private final UploadedFileRepository uploadedFileRepository;

    public AiFileStorageService(UploadProperties uploadProperties, UploadedFileRepository uploadedFileRepository) {
        this.uploadProperties = uploadProperties;
        this.uploadedFileRepository = uploadedFileRepository;
    }

    @Transactional
    public UploadedFile storePronunciationAudio(MultipartFile multipartFile, User ownerUser) {
        validateMultipartFile(multipartFile, ALLOWED_AUDIO_CONTENT_TYPES, ALLOWED_AUDIO_EXTENSIONS,
                "Unsupported audio file type. Allowed types are WEBM, WAV, MPEG, and MP3.");
        return storeFile(multipartFile, ownerUser, "PRIVATE");
    }

    @Transactional
    public UploadedFile storeWritingImage(MultipartFile multipartFile, User ownerUser) {
        validateMultipartFile(multipartFile, ALLOWED_IMAGE_CONTENT_TYPES, ALLOWED_IMAGE_EXTENSIONS,
                "Unsupported image file type. Allowed types are PNG, JPG, and JPEG.");
        return storeFile(multipartFile, ownerUser, "PRIVATE");
    }

    public long getMaxFileSizeBytes() {
        return uploadProperties.getMaxFileSize().toBytes();
    }

    public List<String> getAllowedAudioContentTypes() {
        return ALLOWED_AUDIO_CONTENT_TYPES.stream().sorted().toList();
    }

    public List<String> getAllowedImageContentTypes() {
        return ALLOWED_IMAGE_CONTENT_TYPES.stream().sorted().toList();
    }

    private UploadedFile storeFile(MultipartFile multipartFile, User ownerUser, String accessLevel) {
        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded file must include a valid extension.");
        String storedFileName = UploadStorageSupport.buildStoredFileName(extension);

        Path targetPath = UploadStorageSupport.storeMultipartFile(
                multipartFile,
                resolveAiDirectory(),
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
        uploadedFile.setAccessLevel(accessLevel);
        uploadedFile.setActive(true);
        return uploadedFileRepository.save(uploadedFile);
    }

    private void validateMultipartFile(
            MultipartFile multipartFile,
            Set<String> allowedContentTypes,
            Set<String> allowedExtensions,
            String invalidTypeMessage) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file is required.");
        }

        if (multipartFile.getSize() > uploadProperties.getMaxFileSize().toBytes()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file exceeds the maximum allowed file size.");
        }

        String originalFileName = UploadStorageSupport.extractSafeOriginalFileName(
                multipartFile.getOriginalFilename(),
                "Uploaded file name is invalid.");
        String extension = UploadStorageSupport.getRequiredExtension(
                originalFileName,
                "Uploaded file must include a valid extension.");
        if (!allowedExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }

        String contentType = UploadStorageSupport.normalizeContentType(multipartFile.getContentType());
        if (!allowedContentTypes.contains(contentType)) {
            throw new ResponseStatusException(BAD_REQUEST, invalidTypeMessage);
        }
    }

    private Path resolveAiDirectory() {
        return UploadStorageSupport.resolveBaseDirectory(Path.of(uploadProperties.getDirectory(), "ai-feedback").toString());
    }
}
