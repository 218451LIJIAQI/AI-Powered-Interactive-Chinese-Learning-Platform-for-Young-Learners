package com.example.aichinese.service.navigation;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.security.SecurityUserDetails;

/** Sidebar badge counts for the signed-in user: every contributor registered for the user's roles. */
@Service
public class NavCountService {

    private final List<NavCountContributor> contributors;

    public NavCountService(List<NavCountContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countsFor(SecurityUserDetails currentUser) {
        Map<String, Long> counts = new TreeMap<>();
        for (NavCountContributor contributor : contributors) {
            if (hasRole(currentUser, contributor.role())) {
                contributor.counts(currentUser.getUserId()).forEach((key, value) ->
                        counts.merge(key, Math.max(0L, value == null ? 0L : value), Long::sum));
            }
        }
        return counts;
    }

    private static boolean hasRole(SecurityUserDetails user, RoleName role) {
        return user.getRoleNames().contains(role.name());
    }
}
