package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;

/**
 * Pins repository-level guard rails that protect callers from SQL edge
 * cases such as empty {@code IN} collections. These are intentionally
 * unit-level contracts around default methods; full Spring context
 * tests still validate that the backing JPQL/native queries parse.
 */
class RepositoryBoundaryContractTests {

    private static final long LESSON_ID = 10L;
    private static final long PACKAGE_ID = 20L;
    private static final long TEACHER_ID = 10L;
    private static final long DOCUMENT_COUNT = 2L;

    private static final List<Long> PACKAGE_IDS = List.of(PACKAGE_ID);
    private static final List<Long> TEACHER_IDS = List.of(TEACHER_ID);

    @Test
    void liveClassRelatedLookupFallsBackToDirectLessonQueryWhenPackageIdsAreMissing() {
        LiveClassRepository repository = liveClassRepository();
        LiveClass liveClass = new LiveClass();
        when(repository.findRelatedToLesson(LESSON_ID)).thenReturn(List.of(liveClass));

        assertThat(repository.findRelatedToLessonOrPackages(LESSON_ID, null)).containsExactly(liveClass);
        assertThat(repository.findRelatedToLessonOrPackages(LESSON_ID, List.of())).containsExactly(liveClass);

        verify(repository, never()).findRelatedToLessonOrPackagesInternal(eq(LESSON_ID), anyCollection());
    }

    @Test
    void liveClassRelatedLookupUsesPackageAwareQueryWhenPackageIdsExist() {
        LiveClassRepository repository = liveClassRepository();
        LiveClass liveClass = new LiveClass();
        when(repository.findRelatedToLessonOrPackagesInternal(LESSON_ID, PACKAGE_IDS))
                .thenReturn(List.of(liveClass));

        assertThat(repository.findRelatedToLessonOrPackages(LESSON_ID, PACKAGE_IDS)).containsExactly(liveClass);

        verify(repository, never()).findRelatedToLesson(LESSON_ID);
    }

    @Test
    void recordedVideoRelatedLookupFallsBackToDirectLessonQueryWhenPackageIdsAreMissing() {
        RecordedLessonVideoRepository repository = recordedLessonVideoRepository();
        RecordedLessonVideo video = new RecordedLessonVideo();
        when(repository.findActiveRelatedToLesson(LESSON_ID)).thenReturn(List.of(video));

        assertThat(repository.findActiveRelatedToLessonOrPackages(LESSON_ID, null)).containsExactly(video);
        assertThat(repository.findActiveRelatedToLessonOrPackages(LESSON_ID, List.of())).containsExactly(video);

        verify(repository, never()).findActiveRelatedToLessonOrPackagesInternal(eq(LESSON_ID), anyCollection());
    }

    @Test
    void recordedVideoRelatedLookupUsesPackageAwareQueryWhenPackageIdsExist() {
        RecordedLessonVideoRepository repository = recordedLessonVideoRepository();
        RecordedLessonVideo video = new RecordedLessonVideo();
        when(repository.findActiveRelatedToLessonOrPackagesInternal(LESSON_ID, PACKAGE_IDS))
                .thenReturn(List.of(video));

        assertThat(repository.findActiveRelatedToLessonOrPackages(LESSON_ID, PACKAGE_IDS)).containsExactly(video);

        verify(repository, never()).findActiveRelatedToLesson(LESSON_ID);
    }

    @Test
    void teacherDocumentCountReturnsEmptyRowsForMissingTeacherIds() {
        TeacherVerificationDocumentRepository repository = teacherVerificationDocumentRepository();

        assertThat(repository.countByTeacherIdIn(null)).isEmpty();
        assertThat(repository.countByTeacherIdIn(List.of())).isEmpty();

        verify(repository, never()).countByTeacherIdInInternal(anyCollection());
    }

    @Test
    void teacherDocumentCountDelegatesWhenTeacherIdsExist() {
        TeacherVerificationDocumentRepository repository = teacherVerificationDocumentRepository();
        Object[] row = new Object[] { TEACHER_ID, DOCUMENT_COUNT };
        when(repository.countByTeacherIdInInternal(TEACHER_IDS)).thenReturn(List.<Object[]>of(row));

        assertThat(repository.countByTeacherIdIn(TEACHER_IDS)).containsExactly(row);
    }

    private static LiveClassRepository liveClassRepository() {
        return mock(LiveClassRepository.class, CALLS_REAL_METHODS);
    }

    private static RecordedLessonVideoRepository recordedLessonVideoRepository() {
        return mock(RecordedLessonVideoRepository.class, CALLS_REAL_METHODS);
    }

    private static TeacherVerificationDocumentRepository teacherVerificationDocumentRepository() {
        return mock(TeacherVerificationDocumentRepository.class, CALLS_REAL_METHODS);
    }
}
