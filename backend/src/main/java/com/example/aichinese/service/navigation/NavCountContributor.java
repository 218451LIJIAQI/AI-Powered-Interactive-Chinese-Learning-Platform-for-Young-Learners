package com.example.aichinese.service.navigation;

import java.util.Map;

import com.example.aichinese.entity.enums.RoleName;

/**
 * One module's sidebar badge counts (the frontend's {@code RoleNavItem.countKey}). Each module that shows a badge
 * registers a contributor bean; {@link NavCountService} merges them for the signed-in user's role.
 */
public interface NavCountContributor {

    /** The role whose sidebar shows these counts. */
    RoleName role();

    /** Count per badge key for this user, e.g. {@code mistakes -> 6}; zero counts may be left out. */
    Map<String, Long> counts(Long userId);
}
