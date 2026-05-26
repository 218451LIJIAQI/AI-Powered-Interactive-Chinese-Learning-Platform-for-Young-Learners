package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.enums.PayoutStatus;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.PayoutRequestRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;

/**
 * Pins repository-level guard rails that protect callers from SQL edge
 * cases such as empty {@code IN} collections. These are intentionally
 * unit-level contracts around default methods; full Spring context
 * tests still validate that the backing JPQL/native queries parse.
 */
class RepositoryBoundaryContractTests {

    @Test
    void liveClassRelatedLookupFallsBackToDirectLessonQueryWhenPackageIdsAreMissing() {
        LiveClassRepository repository = mock(LiveClassRepository.class, CALLS_REAL_METHODS);
        LiveClass liveClass = new LiveClass();
        when(repository.findRelatedToLesson(10L)).thenReturn(List.of(liveClass));

        assertThat(repository.findRelatedToLessonOrPackages(10L, null)).containsExactly(liveClass);
        assertThat(repository.findRelatedToLessonOrPackages(10L, List.of())).containsExactly(liveClass);

        verify(repository, never()).findRelatedToLessonOrPackagesInternal(eq(10L), anyCollection());
    }

    @Test
    void liveClassRelatedLookupUsesPackageAwareQueryWhenPackageIdsExist() {
        LiveClassRepository repository = mock(LiveClassRepository.class, CALLS_REAL_METHODS);
        LiveClass liveClass = new LiveClass();
        when(repository.findRelatedToLessonOrPackagesInternal(10L, List.of(20L)))
                .thenReturn(List.of(liveClass));

        assertThat(repository.findRelatedToLessonOrPackages(10L, List.of(20L))).containsExactly(liveClass);

        verify(repository, never()).findRelatedToLesson(10L);
    }

    @Test
    void recordedVideoRelatedLookupFallsBackToDirectLessonQueryWhenPackageIdsAreMissing() {
        RecordedLessonVideoRepository repository = mock(RecordedLessonVideoRepository.class, CALLS_REAL_METHODS);
        RecordedLessonVideo video = new RecordedLessonVideo();
        when(repository.findActiveRelatedToLesson(10L)).thenReturn(List.of(video));

        assertThat(repository.findActiveRelatedToLessonOrPackages(10L, null)).containsExactly(video);
        assertThat(repository.findActiveRelatedToLessonOrPackages(10L, List.of())).containsExactly(video);

        verify(repository, never()).findActiveRelatedToLessonOrPackagesInternal(eq(10L), anyCollection());
    }

    @Test
    void recordedVideoRelatedLookupUsesPackageAwareQueryWhenPackageIdsExist() {
        RecordedLessonVideoRepository repository = mock(RecordedLessonVideoRepository.class, CALLS_REAL_METHODS);
        RecordedLessonVideo video = new RecordedLessonVideo();
        when(repository.findActiveRelatedToLessonOrPackagesInternal(10L, List.of(20L)))
                .thenReturn(List.of(video));

        assertThat(repository.findActiveRelatedToLessonOrPackages(10L, List.of(20L))).containsExactly(video);

        verify(repository, never()).findActiveRelatedToLesson(10L);
    }

    @Test
    void payoutSumReturnsZeroForMissingStatusSet() {
        PayoutRequestRepository repository = mock(PayoutRequestRepository.class, CALLS_REAL_METHODS);

        assertThat(repository.sumAmountByTeacherIdAndPayoutStatusIn(10L, null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repository.sumAmountByTeacherIdAndPayoutStatusIn(10L, Set.of())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(repository.sumAmountByTeacherIdAndPayoutStatusIn(null, PayoutStatus.COMMITTED)).isEqualByComparingTo(BigDecimal.ZERO);

        verify(repository, never()).sumAmountByTeacherIdAndPayoutStatusInInternal(eq(10L), anySet());
    }

    @Test
    void payoutSumDelegatesWhenStatusesExist() {
        PayoutRequestRepository repository = mock(PayoutRequestRepository.class, CALLS_REAL_METHODS);
        when(repository.sumAmountByTeacherIdAndPayoutStatusInInternal(10L, PayoutStatus.COMMITTED))
                .thenReturn(new BigDecimal("12.34"));

        assertThat(repository.sumAmountByTeacherIdAndPayoutStatusIn(10L, PayoutStatus.COMMITTED))
                .isEqualByComparingTo("12.34");
    }

    @Test
    void teacherDocumentCountReturnsEmptyRowsForMissingTeacherIds() {
        TeacherVerificationDocumentRepository repository = mock(TeacherVerificationDocumentRepository.class, CALLS_REAL_METHODS);

        assertThat(repository.countByTeacherIdIn(null)).isEmpty();
        assertThat(repository.countByTeacherIdIn(List.of())).isEmpty();

        verify(repository, never()).countByTeacherIdInInternal(anyCollection());
    }

    @Test
    void teacherDocumentCountDelegatesWhenTeacherIdsExist() {
        TeacherVerificationDocumentRepository repository = mock(TeacherVerificationDocumentRepository.class, CALLS_REAL_METHODS);
        Object[] row = new Object[] { 10L, 2L };
        when(repository.countByTeacherIdInInternal(List.of(10L))).thenReturn(List.<Object[]>of(row));

        assertThat(repository.countByTeacherIdIn(List.of(10L))).containsExactly(row);
    }
}
