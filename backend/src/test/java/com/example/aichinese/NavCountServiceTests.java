package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.navigation.NavCountContributor;
import com.example.aichinese.service.navigation.NavCountService;

class NavCountServiceTests {

    @Test
    void mergesTheCountsOfEveryContributorForTheUsersRoleOnly() {
        NavCountService service = new NavCountService(List.of(
                contributor(RoleName.ADMIN, Map.of("reviewQueue", 2L)),
                contributor(RoleName.ADMIN, Map.of("aiReports", 1L, "reviewQueue", 1L)),
                contributor(RoleName.STUDENT, Map.of("mistakes", 6L))));

        assertThat(service.countsFor(user(RoleName.ADMIN))).containsExactly(
                Map.entry("aiReports", 1L), Map.entry("reviewQueue", 3L));
        assertThat(service.countsFor(user(RoleName.STUDENT))).containsExactly(Map.entry("mistakes", 6L));
        assertThat(service.countsFor(user(RoleName.TEACHER))).isEmpty();
    }

    @Test
    void neverReportsANegativeCount() {
        NavCountService service = new NavCountService(List.of(contributor(RoleName.STUDENT, Map.of("mistakes", -3L))));
        assertThat(service.countsFor(user(RoleName.STUDENT))).containsEntry("mistakes", 0L);
    }

    private static NavCountContributor contributor(RoleName role, Map<String, Long> counts) {
        return new NavCountContributor() {
            @Override
            public RoleName role() {
                return role;
            }

            @Override
            public Map<String, Long> counts(Long userId) {
                return counts;
            }
        };
    }

    private static SecurityUserDetails user(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(5L);
        user.setEmail("someone@example.com");
        user.setPasswordHash("hash");
        user.setDisplayName("Someone");
        user.setRoles(Set.of(role));
        return new SecurityUserDetails(user);
    }
}
