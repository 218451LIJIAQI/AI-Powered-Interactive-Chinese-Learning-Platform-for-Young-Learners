package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.base.EmailVerificationToken;

/** Each kind of verification link uses its own table and inherits the shared single-use token rules. */
@NoRepositoryBean
public interface EmailVerificationTokenRepository<T extends EmailVerificationToken>
        extends OneTimeUserTokenRepository<T> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from #{#entityName} t where t.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
