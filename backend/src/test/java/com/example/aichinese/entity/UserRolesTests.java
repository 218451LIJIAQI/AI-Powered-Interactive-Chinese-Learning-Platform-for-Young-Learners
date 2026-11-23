package com.example.aichinese.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.enums.RoleName;

/** An account's role, and a clear failure for an account that has none (a database left from an older version). */
class UserRolesTests {

    @Test
    void anAccountWithARoleHasIt() {
        User user = new User();
        Role learner = new Role();
        learner.setName(RoleName.STUDENT);
        user.getRoles().add(learner);

        assertThat(user.requirePrimaryRole()).isEqualTo(RoleName.STUDENT);
    }

    @Test
    void anAccountWithBothRolesUsesTheSamePrimaryRoleAsTheSignInResponse() {
        Role learner = new Role();
        learner.setName(RoleName.STUDENT);
        Role admin = new Role();
        admin.setName(RoleName.ADMIN);
        User user = new User();
        user.setRoles(new LinkedHashSet<>(List.of(learner, admin)));

        assertThat(user.orderedRoleNames()).containsExactly(RoleName.ADMIN, RoleName.STUDENT);
        assertThat(user.requirePrimaryRole()).isEqualTo(RoleName.ADMIN);
    }

    @Test
    void anAccountWithoutARoleFailsWithAClearMessage() {
        User user = new User();
        user.setId(7L);

        assertThatThrownBy(user::requirePrimaryRole)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account 7 has no role.");
    }
}
