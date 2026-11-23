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
import com.example.aichinese.entity.UploadedFile;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    @EntityGraph(attributePaths = { "author", "attachment" })
    List<SupportMessage> findByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);

    @Query("select attachment from SupportMessage m join m.attachment attachment where m.id = :messageId")
    Optional<UploadedFile> findAttachmentByMessageId(@Param("messageId") Long messageId);

    @Query("select attachment from SupportMessage m join m.attachment attachment where m.request.id in :requestIds")
    List<UploadedFile> findAttachmentsByRequestIdIn(@Param("requestIds") Collection<Long> requestIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SupportMessage m where m.request.id in :requestIds")
    int deleteByRequestIdIn(@Param("requestIds") Collection<Long> requestIds);
}
