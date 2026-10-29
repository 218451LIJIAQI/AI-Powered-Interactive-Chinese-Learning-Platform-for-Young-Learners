package com.example.aichinese.service.admin;

import com.example.aichinese.entity.User;

/**
 * One consequence of deactivating an account (cancel the teacher's upcoming live sessions, send their items in the
 * review queue back to Draft, ...). Each module that owns such data registers a step bean; the admin Users page lists
 * every step's preview in the Deactivate dialog and {@link AdminUserService} applies them all in one transaction.
 */
public interface AccountDeactivationStep {

    /** Adds what deactivating {@code user} now would do to {@code impact}; must not change anything. */
    void preview(User user, DeactivationTally impact);

    /** Applies the consequence for {@code user}, deactivated by {@code admin}, and adds what it did to {@code done}. */
    void apply(User user, User admin, DeactivationTally done);
}
