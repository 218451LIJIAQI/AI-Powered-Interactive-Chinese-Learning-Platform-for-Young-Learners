package com.example.aichinese.service.account;

import java.util.Comparator;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

/** The role an account acts as: accounts have one role, and should an old one hold several, ADMIN beats TEACHER. */
public final class AccountRoles {

    private AccountRoles() {
    }

    public static RoleName primary(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName())
                .min(Comparator.comparingInt(AccountRoles::rank))
                .orElse(RoleName.STUDENT);
    }

    public static boolean has(User user, RoleName role) {
        return user.getRoles().stream().anyMatch(candidate -> candidate.getName() == role);
    }

    private static int rank(RoleName role) {
        return switch (role) {
            case ADMIN -> 0;
            case TEACHER -> 1;
            default -> 2;
        };
    }
}
