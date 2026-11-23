package com.example.aichinese.entity.base;

import jakarta.persistence.MappedSuperclass;

/** A one-time link that verifies an address at sign-up or when an account changes its e-mail. */
@MappedSuperclass
public abstract class EmailVerificationToken extends OneTimeUserToken {
}
