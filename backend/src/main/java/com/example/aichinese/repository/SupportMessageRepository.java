package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.SupportMessage;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    @EntityGraph(attributePaths = { "author", "attachment" })
    List<SupportMessage> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);

    @EntityGraph(attributePaths = { "request", "request.requester", "attachment" })
    Optional<SupportMessage> findDetailedById(Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SupportMessage m where m.request.id in :requestIds")
    int deleteByRequestIdIn(@Param("requestIds") Collection<Long> requestIds);
}
