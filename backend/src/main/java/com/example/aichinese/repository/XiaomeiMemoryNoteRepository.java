package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.XiaomeiMemoryNote;

public interface XiaomeiMemoryNoteRepository extends JpaRepository<XiaomeiMemoryNote, Long> {

    /** Every note of the learner, remembered or forgotten, oldest first. */
    List<XiaomeiMemoryNote> findByStudentUserIdOrderByCreatedAtAscIdAsc(Long studentUserId);

    /**
     * This student's chat notes forgotten before {@code cutoff}: their Undo is over, so they are deleted for good.
     * Scoped to the acting learner under their account lock so concurrent learners never contend on one another's rows.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from XiaomeiMemoryNote n
            where n.studentUser.id = :studentUserId and n.planReason is null and n.forgottenAt < :cutoff
            """)
    int deleteChatNotesForgottenBefore(@Param("studentUserId") Long studentUserId, @Param("cutoff") LocalDateTime cutoff);

    /** Account deletion: everything Xiaomei remembers about the learner goes. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from XiaomeiMemoryNote n where n.studentUser.id = :userId")
    int deleteByStudentUserId(@Param("userId") Long userId);
}
