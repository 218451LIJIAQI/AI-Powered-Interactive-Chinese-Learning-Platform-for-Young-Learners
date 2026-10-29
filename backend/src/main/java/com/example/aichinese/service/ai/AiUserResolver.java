package com.example.aichinese.service.ai;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.User;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.LanguageCodes;

/** The signed-in user of an AI request and the language SparkBot explains in. */
@Component
public class AiUserResolver {

    private final UserRepository userRepository;

    public AiUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The user with their roles: AI requests run outside a transaction and the usage log reads the role. */
    public User require(SecurityUserDetails currentUser) {
        return userRepository.findWithRolesById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Authenticated user could not be found."));
    }

    /**
     * The student's current interface language (en, ms or zh-CN) wins; the account preference is the fallback. A
     * legacy "zh" value is mapped to "zh-CN".
     */
    public static String language(User user, String requestedLanguage) {
        String requested = LanguageCodes.normalize(requestedLanguage);
        if (requested != null) {
            return requested;
        }
        String preferred = LanguageCodes.normalize(user.getPreferredLanguage());
        return preferred != null ? preferred : LanguageCodes.ENGLISH;
    }

    /** The language translations are written in: Bahasa Melayu for Malay, otherwise English. */
    public static String translationLanguage(String language) {
        return LanguageCodes.MALAY.equals(language) ? "Bahasa Melayu" : "English";
    }
}
