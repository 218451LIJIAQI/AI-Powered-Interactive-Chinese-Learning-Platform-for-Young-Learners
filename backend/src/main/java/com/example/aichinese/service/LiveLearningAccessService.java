package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.enums.MeetingPlatform;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;

/**
 * Shared rules of live sessions and lesson videos: the session time window in its own time zone, meeting links
 * that match their platform and video sources.
 */
@Service
public class LiveLearningAccessService {

    /** A booked student can join this many minutes before the start. */
    public static final int JOIN_WINDOW_LEAD_MINUTES = 15;

    /**
     * Current wall-clock time in the session's own time zone: start and end are stored as wall-clock values
     * there, so the join window and the end do not depend on the server zone.
     */
    public static LocalDateTime nowAt(LiveClass liveClass) {
        String timeZone = liveClass.getTimeZone();
        if (timeZone == null || timeZone.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.now(ZoneId.of(timeZone));
        } catch (DateTimeException exception) {
            return LocalDateTime.now();
        }
    }

    /** A session past its planned end is over. */
    public static boolean hasEnded(LiveClass liveClass) {
        return liveClass.getScheduledEndAt() != null && !nowAt(liveClass).isBefore(liveClass.getScheduledEndAt());
    }

    /** Joining opens 15 minutes before the start and closes at the end. */
    public boolean isJoinWindowOpen(LiveClass liveClass) {
        if (liveClass.getScheduledStartAt() == null || liveClass.getScheduledEndAt() == null) {
            return false;
        }
        LocalDateTime now = nowAt(liveClass);
        return !now.isBefore(liveClass.getScheduledStartAt().minusMinutes(JOIN_WINDOW_LEAD_MINUTES))
                && now.isBefore(liveClass.getScheduledEndAt());
    }

    public MeetingPlatform parseMeetingPlatform(String value) {
        try {
            return MeetingPlatform.fromValue(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage());
        }
    }

    public RecordedVideoSourceType parseRecordedVideoSourceType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Recorded video source type is required.");
        }
        return RecordedVideoSourceType.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Recorded video source type is invalid."));
    }

    public String validateMeetingLink(String meetingLink, MeetingPlatform platform) {
        if (platform == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting platform is required.");
        }

        URI uri = parseSupportedWebUri(meetingLink, "Meeting link must be a valid URL.");
        if (!platform.allowsHost(uri.getHost())) {
            throw new ResponseStatusException(BAD_REQUEST, "Meeting link host does not match the selected meeting platform.");
        }
        return uri.normalize().toASCIIString();
    }

    public void validateLiveClassTiming(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ResponseStatusException(BAD_REQUEST, "End time must be after start time.");
        }
    }

    public String validateRecordedVideoSource(
            RecordedVideoSourceType sourceType,
            String externalVideoUrl,
            UploadedFile uploadedFile) {
        if (sourceType == RecordedVideoSourceType.EXTERNAL_URL) {
            if (externalVideoUrl == null || externalVideoUrl.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "External video URL is required.");
            }
            return parseSupportedWebUri(externalVideoUrl, "External video URL must be valid.").normalize().toASCIIString();
        }

        if (uploadedFile == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded recorded video file is required.");
        }
        return null;
    }

    private static URI parseSupportedWebUri(String value, String invalidMessage) {
        if (value == null || value.isBlank() || containsUnsafeUrlCodePoint(value) || value.indexOf('\\') >= 0) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }

        try {
            URI uri = new URI(value.strip());
            if (!isSupportedWebUri(uri)
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(BAD_REQUEST, invalidMessage);
        }
    }

    private static boolean isSupportedWebUri(URI uri) {
        if (uri == null || uri.isOpaque() || uri.getScheme() == null || uri.getHost() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private static boolean containsUnsafeUrlCodePoint(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
                return true;
            }
        }
        return false;
    }
}
