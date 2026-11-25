package com.example.aichinese.entity;

import com.example.aichinese.entity.base.OneTimeUserToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A pending e-mail change: the new address takes effect when the link sent to it is opened (once, within 60 min). */
@Getter
@Setter
@Entity
@Table(name = "email_change_tokens", indexes = {
        @Index(name = "idx_email_change_tokens_user_open", columnList = "user_id,used_at,expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_change_tokens_token_hash", columnNames = "token_hash")
})
public class EmailChangeToken extends OneTimeUserToken {

    @Column(name = "new_email", nullable = false, updatable = false, length = 150)
    private String newEmail;
}
