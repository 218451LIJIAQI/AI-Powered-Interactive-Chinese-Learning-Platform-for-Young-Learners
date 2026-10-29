package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.seed.DemoOpsSeeder;
import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.dto.teacher.TeacherVerificationState;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.UserRepository;

/** The ops demo data: the prototype's support requests, two applications in review, a deactivated student. */
@SpringBootTest
@ActiveProfiles("demo")
class DemoOpsSeederTests {

    @Autowired
    private DemoOpsSeeder demoOpsSeeder;

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private TeacherVerificationDocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @Transactional
    void seedsTheSupportQueueApplicationsAndADeactivatedStudentOnce() {
        Map<Integer, SupportRequestStatus> requests = supportRequestRepository.findAll().stream()
                .collect(Collectors.toMap(SupportRequest::getNumber, SupportRequest::getStatus));
        assertThat(requests).containsEntry(1038, SupportRequestStatus.ANSWERED)
                .containsEntry(1021, SupportRequestStatus.SOLVED)
                .containsEntry(1040, SupportRequestStatus.ANSWERED)
                .containsEntry(1033, SupportRequestStatus.SOLVED)
                .containsEntry(1041, SupportRequestStatus.OPEN);

        TeacherProfile meiLin = applicant(DemoPersonas.APPLICANT.email());
        assertThat(TeacherVerificationState.of(meiLin)).isEqualTo(TeacherVerificationState.IN_REVIEW);
        assertThat(documentRepository.findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc(meiLin.getId()))
                .hasSize(3);
        TeacherProfile farah = applicant("farah.aziz@fyp.local");
        assertThat(TeacherVerificationState.of(farah)).isEqualTo(TeacherVerificationState.IN_REVIEW);
        assertThat(userRepository.findByEmailWithRoles("zhang.yuxi@fyp.local").orElseThrow().isActive()).isFalse();
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(10);

        long before = supportRequestRepository.count();
        demoOpsSeeder.run();
        assertThat(supportRequestRepository.count()).isEqualTo(before);
    }

    private TeacherProfile applicant(String email) {
        Long userId = userRepository.findByEmailWithRoles(email).orElseThrow().getId();
        return teacherProfileRepository.findDetailedByUserId(userId).orElseThrow();
    }
}
