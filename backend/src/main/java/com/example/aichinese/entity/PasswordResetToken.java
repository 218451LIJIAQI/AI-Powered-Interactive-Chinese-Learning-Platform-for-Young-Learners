package com.example.aichinese.entity;

import com.example.aichinese.entity.base.OneTimeUserToken;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@AttributeOverride(name = "tokenHash", column = @Column(name = "token", nullable = false, updatable = false, length = 128))
@Table(name = "password_reset_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "idx_password_reset_tokens_token", columnNames = "token"),
        @UniqueConstraint(name = "uk_password_reset_tokens_active_user", columnNames = { "active_token_user_id" })
})
public class PasswordResetToken extends OneTimeUserToken {

    // Database-generated, as in the V1 baseline, so the unique key above also works on the schemas Hibernate
    // creates for the demo and test profiles. Flyway owns the column of the main database.
    @Column(name = "active_token_user_id", insertable = false, updatable = false,
            columnDefinition = "bigint generated always as (case when used_at is null then user_id else null end) stored")
    private Long activeTokenUserId;
}
