package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.enums.PayoutStatus;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    long countByPayoutStatus(PayoutStatus payoutStatus);

    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    List<PayoutRequest> findByTeacherUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "teacher", "teacher.user" })
    Optional<PayoutRequest> findByIdAndTeacherUserId(Long id, Long userId);

    @Query("select coalesce(sum(pr.amount), 0) from PayoutRequest pr where pr.teacher.id = :teacherId and pr.payoutStatus in :statuses")
    java.math.BigDecimal sumAmountByTeacherIdAndPayoutStatusIn(
            @Param("teacherId") Long teacherId,
            @Param("statuses") Set<PayoutStatus> statuses);
}
