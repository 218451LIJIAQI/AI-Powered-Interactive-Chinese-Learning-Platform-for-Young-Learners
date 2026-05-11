package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;
import com.example.aichinese.entity.enums.SupportTicketPriority;
import com.example.aichinese.entity.enums.SupportTicketStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "support_tickets", indexes = {
        @Index(name = "idx_support_tickets_created_by_user_id", columnList = "created_by_user_id"),
        @Index(name = "idx_support_tickets_status", columnList = "ticket_status")
})
public class SupportTicket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "LONGTEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", nullable = false, length = 30)
    private SupportTicketStatus ticketStatus = SupportTicketStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_priority", nullable = false, length = 20)
    private SupportTicketPriority ticketPriority = SupportTicketPriority.MEDIUM;
}
