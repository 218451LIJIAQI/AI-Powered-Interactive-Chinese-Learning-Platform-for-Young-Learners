package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for the authentication root {@link User}.
 *
 * <p>The {@code email} column is case-insensitively unique by
 * convention — every lookup, existence-check, and locking variant here
 * applies {@code lower(...)} to both sides so the same canonical
 * lower-cased value is compared regardless of how the caller cased the
 * input. This intentionally mirrors the way the authentication flow
 * stores email at registration (always lower-cased before persist), so
 * the only place case sensitivity matters is at the UI / API surface.</p>
 *
 * <p>The pessimistic-write {@link #findLockedByEmail(String)} variant
 * exists for the password-update and force-logout flows where the same
 * row must be re-read inside a transaction without a concurrent session
 * silently overwriting the {@code sessionVersion} counter.</p>
 *
 * <p>{@link #findAdminUserIds(String, String, String, Pageable)} returns
 * primary keys only (not entities) because the admin user-list flow
 * runs a second batched load on {@link #findByIdIn(Collection)} to
 * resolve a filtered, sorted page without paying the join-fan-out cost
 * inside the search query itself.</p>
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Case-insensitive registration-uniqueness probe used by the
     * sign-up flow before issuing the INSERT. The actual race-safe
     * boundary is the {@code uk_users_email_lower} unique index;
     * this probe just provides the user-facing 400 before the
     * constraint violation would surface.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Case-insensitive login lookup that eagerly joins the
     * many-to-many {@code roles} collection so the
     * {@code UserDetailsService} can build authorities in a single
     * round trip without a second {@code SELECT} on the role join
     * table.
     */
    @Query("select distinct u from User u left join fetch u.roles where lower(u.email) = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    /**
     * Pessimistic-write variant used by the password-update and
     * force-logout flows so the user row stays locked while the
     * service re-reads it, applies the new password hash + bumps
     * {@code sessionVersion}, and commits \u2014 protecting against a
     * concurrent session silently overwriting either field.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findLockedByEmail(@Param("email") String email);

    /**
     * Counts users holding the given role. Used by the admin
     * dashboard's role-distribution tile and by the
     * {@code TeacherStatus} reconciliation flow. The {@code distinct}
     * guard avoids the join-fan-out double-count when a user
     * accidentally has the same role row twice.
     */
    @Query("select count(distinct u) from User u join u.roles r where r.name = :roleName")
    long countByRoleName(@Param("roleName") RoleName roleName);

    /**
     * Five most recently registered users for the admin dashboard's
     * "recent registrations" tile. Backed by the implicit index on
     * {@code created_at}.
     */
    List<User> findTop5ByOrderByCreatedAtDesc();

    /**
     * Admin user-management page driven by a dynamic
     * {@link Specification}. The {@code roles} eager fetch keeps the
     * response mapper from triggering one lazy SELECT per row when
     * assembling the role-badge column.
     */
    @Override
    @EntityGraph(attributePaths = { "roles" })
    @NonNull
    Page<User> findAll(@Nullable Specification<User> specification, @NonNull Pageable pageable);

    /**
     * Bulk hydration of users by id, used by the admin user-list
     * flow after {@link #findAdminUserIds} returns the paginated id
     * slice. The {@code roles} eager fetch hydrates the role-badge
     * column in the same round trip.
     */
    @EntityGraph(attributePaths = { "roles" })
    List<User> findByIdIn(Collection<Long> ids);

    @Query(value = """
            select u.id
            from users u
            where (:roleName is null or exists (
                  select 1 from user_roles ur
                  join roles r on r.id = ur.role_id
                  where ur.user_id = u.id
                    and r.name = :roleName
              ))
              and (:keyword is null or lower(u.display_name) like :keyword or lower(u.email) like :keyword)
              and (:teacherStatus is null or exists (
                  select 1 from teacher_profiles tp
                  where tp.user_id = u.id
                    and tp.teacher_status = :teacherStatus
              ))
            order by u.created_at desc, u.id desc
            """,
            countQuery = """
            select count(*)
            from users u
            where (:roleName is null or exists (
                  select 1 from user_roles ur
                  join roles r on r.id = ur.role_id
                  where ur.user_id = u.id
                    and r.name = :roleName
              ))
              and (:keyword is null or lower(u.display_name) like :keyword or lower(u.email) like :keyword)
              and (:teacherStatus is null or exists (
                  select 1 from teacher_profiles tp
                  where tp.user_id = u.id
                    and tp.teacher_status = :teacherStatus
              ))
            """,
            nativeQuery = true)
    Page<Long> findAdminUserIds(
            @Param("roleName") String roleName,
            @Param("keyword") String keyword,
            @Param("teacherStatus") String teacherStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    @Query("select distinct u from User u join u.roles r where r.name = :roleName")
    List<User> findAllByRoleName(@Param("roleName") RoleName roleName);

    /**
     * Returns users created on or after the supplied threshold, ordered by
     * creation date ascending so the admin registration-trend chart can be
     * built without loading the entire user table. Pushing the time filter
     * and sort to the database lets the query touch only the new-user slice
     * instead of paying an O(users_total) JVM-side filter on every admin
     * analytics request.
     */
    List<User> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(LocalDateTime threshold);
}
