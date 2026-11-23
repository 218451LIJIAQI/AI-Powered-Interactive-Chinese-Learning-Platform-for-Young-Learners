package com.example.aichinese.service.ai;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.dto.ai.AiHelperStatusResponse;
import com.example.aichinese.service.PlatformSettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiHelperStatusService {

    private final AiAvailabilityService availabilityService;
    private final AiUsageLimiter usage;
    private final PlatformSettingsService settings;
    private final UploadProperties uploads;

    public AiHelperStatusResponse forStudent(Long userId) {
        var features = availabilityService.getAvailability();
        return new AiHelperStatusResponse(
                features.available(), features.speechAnalysisAvailable(), features.imageAnalysisAvailable(),
                uploads.getAiAudioMaxFileSize().toBytes(), uploads.getAiImageMaxFileSize().toBytes(),
                usage.usedToday(userId), usage.requestsPerDay(), settings.aiDisclaimerText());
    }

    /** Quiz authors see whether generation is available. Configuration diagnostics belong in the usage log. */
    public AiAvailabilityResponse forQuizAuthor() {
        return new AiAvailabilityResponse(availabilityService.getAvailability().available());
    }
}
