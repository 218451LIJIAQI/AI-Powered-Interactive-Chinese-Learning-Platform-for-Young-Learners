package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.security.SecurityUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentLearningAccessService {

    private static final String PROFILE_NOT_FOUND = "Student profile could not be found.";

    private final StudentProfileRepository studentProfileRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public StudentProfile requireStudent(SecurityUserDetails currentUser) {
        return findByUser(currentUser);
    }

    /**
     * The caller's write transaction holds this learner's lock before reading membership, attempts or completions.
     * Uses an unlocked lookup followed by a locking refresh so a single {@code SELECT ... FOR UPDATE} also replaces
     * any stale L1-cached instance.
     */
    public StudentProfile requireLockedStudent(SecurityUserDetails currentUser) {
        requireReadWriteTransaction("requireLockedStudent");
        return refreshWithLock(findByUser(currentUser));
    }

    /** Reattaches a known profile and reads its latest committed fields before awarding XP or serving hints. */
    public StudentProfile lockProfile(StudentProfile student) {
        requireReadWriteTransaction("lockProfile");
        StudentProfile managed = studentProfileRepository.findById(student.getId()).orElseThrow();
        return refreshWithLock(managed);
    }

    private StudentProfile findByUser(SecurityUserDetails currentUser) {
        return studentProfileRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, PROFILE_NOT_FOUND));
    }

    private static void requireReadWriteTransaction(String caller) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException(caller + " requires an active read-write transaction.");
        }
    }

    private StudentProfile refreshWithLock(StudentProfile profile) {
        entityManager.refresh(profile, LockModeType.PESSIMISTIC_WRITE);
        return profile;
    }
}
