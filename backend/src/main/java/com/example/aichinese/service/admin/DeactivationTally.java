package com.example.aichinese.service.admin;

import java.util.ArrayList;
import java.util.List;

import com.example.aichinese.dto.admin.AdminUserDetailResponse;

/** What a deactivation would do (preview) or did (apply), filled in by the {@link AccountDeactivationStep}s. */
public final class DeactivationTally {

    private final List<String> publishedClasses = new ArrayList<>();
    private long upcomingSessions;
    private long bookings;
    private long reviewItems;

    public void addPublishedClass(String title) {
        publishedClasses.add(title);
    }

    public void addUpcomingSessions(long count) {
        upcomingSessions += count;
    }

    public void addBookings(long count) {
        bookings += count;
    }

    public void addReviewItems(long count) {
        reviewItems += count;
    }

    public long upcomingSessions() {
        return upcomingSessions;
    }

    public long bookings() {
        return bookings;
    }

    public long reviewItems() {
        return reviewItems;
    }

    AdminUserDetailResponse.DeactivationImpact toImpact() {
        return new AdminUserDetailResponse.DeactivationImpact(
                List.copyOf(publishedClasses), upcomingSessions, bookings, reviewItems);
    }
}
