package com.example.aichinese.service.navigation;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.service.admin.TeacherApplicationService;
import com.example.aichinese.service.support.SupportService;

/**
 * Sidebar badges of the ops module: admins see the applications waiting for a decision and the open support requests;
 * students and teachers see their support requests that support answered (their turn).
 */
@Configuration(proxyBeanMethods = false)
public class OpsNavCounts {

    @Bean
    NavCountContributor adminOpsNavCounts(TeacherApplicationService applications, SupportService support) {
        Objects.requireNonNull(applications, "applications");
        Objects.requireNonNull(support, "support");
        return new Contributor(RoleName.ADMIN, userId -> Map.of(
                "teacherApplications", applications.countWaiting(),
                "supportOpen", support.countOpen()));
    }

    @Bean
    NavCountContributor studentSupportNavCounts(SupportService support) {
        return supportReplies(RoleName.STUDENT, support);
    }

    @Bean
    NavCountContributor teacherSupportNavCounts(SupportService support) {
        return supportReplies(RoleName.TEACHER, support);
    }

    private static NavCountContributor supportReplies(RoleName role, SupportService support) {
        Objects.requireNonNull(support, "support");
        return new Contributor(role, userId -> Map.of("supportReplies", support.countAwaitingRequester(userId)));
    }

    private record Contributor(RoleName role, Function<Long, Map<String, Long>> source)
            implements NavCountContributor {

        @Override
        public Map<String, Long> counts(Long userId) {
            return source.apply(userId);
        }
    }
}
