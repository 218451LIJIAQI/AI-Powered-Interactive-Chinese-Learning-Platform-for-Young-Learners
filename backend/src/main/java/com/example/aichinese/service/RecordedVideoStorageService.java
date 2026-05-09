package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UploadedFileRepository;

@Service
public class RecordedVideoStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".mp4", ".webm", ".mov");

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadProperties uploadProperties;

    public RecordedVideoStorageService(UploadedFileRepository uploadedFileRepository, UploadProperties uploadProperties) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.uploadProperties = uploadProperties;
    }

    @Transactional
    public UploadedFile storeRecordedVideo(MultipartFile multipartFile, User ownerUser) {
        validateMultipartFile(multipartFile);

        String originalFileName = extractSafeOriginalFileName(multipartFile.getOriginalFilename());
        String extension = getExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;

        try {
            Path baseDirectory = resolveBaseDirectory();
            Files.createDirectories(baseDirectory);

            Path targetPath = baseDirectory.resolve(storedFileName).normalize();
            if (!targetPath.startsWith(baseDirectory)) {
                throw new ResponseStatusException(BAD_REQUEST, "Invalid upload target path.");
            }

            Files.copy(multipartFile.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            UploadedFile uploadedFile = new UploadedFile();
            uploadedFile.setOwnerUser(ownerUser);
            uploadedFile.setFileName(originalFileName);
            uploadedFile.setStoredFileName(storedFileName);
            uploadedFile.setFilePath(targetPath.toAbsolutePath().toString());
            uploadedFile.setContentType(normalizeContentType(multipartFile.getContentType()));
            uploadedFile.setFileSize(multipartFile.getSize());
            uploadedFile.setAccessLevel("PRIVATE");
            uploadedFile.setActive(true);
            return uploadedFileRepository.save(uploadedFile);
        } catch (IOException exception) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Recorded video could not be stored.");
        }
    }

    public Resource loadAsResource(UploadedFile uploadedFile) {
        Path filePath = Path.of(uploadedFile.getFilePath()).normalize();
        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(NOT_FOUND, "Requested video could not be found.");
        }

        return new FileSystemResource(filePath);
    }

    public long getMaxVideoFileSizeBytes() {
        return uploadProperties.getMaxVideoFileSize().toBytes();
    }

    public List<String> getAllowedContentTypes() {
        return ALLOWED_CONTENT_TYPES.stream().sorted().toList();
    }

    private void validateMultipartFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video file is required.");
        }

        if (multipartFile.getSize() > uploadProperties.getMaxVideoFileSize().toBytes()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded video exceeds the maximum allowed file size.");
        }

        String originalFileName = extractSafeOriginalFileName(multipartFile.getOriginalFilename());
        String extension = getExtension(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported video type. Allowed types are MP4, WEBM, and MOV.");
        }

        String contentType = normalizeContentType(multipartFile.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported video content type. Allowed types are video/mp4, video/webm, and video/quicktime.");
        }
    }

    private Path resolveBaseDirectory() {
        return Path.of(uploadProperties.getRecordedVideoDirectory()).toAbsolutePath().normalize();
    }

    private String extractSafeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file name is invalid.");
        }

        String cleaned = Path.of(originalFileName).getFileName().toString().trim();
        if (cleaned.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file name is invalid.");
        }

        return cleaned;
    }

    private String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Uploaded video must include a valid extension. Allowed types are MP4, WEBM, and MOV.");
        }

        return fileName.substring(lastDot);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }
}
