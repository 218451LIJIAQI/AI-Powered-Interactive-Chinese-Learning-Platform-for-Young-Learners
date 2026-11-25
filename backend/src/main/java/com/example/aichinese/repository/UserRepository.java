package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.dto.admin.AdminAnalyticsResponse.UserCounts;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.RoleName;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("select exists (select 1 from User u where u.email = lower(:email))")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("select u from User u where u.email = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select distinct u from User u left join fetch u.roles where u.email = lower(:email)")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<User> findWithReadLockById(Long id);

    /** Locks a batch of user rows in deterministic ascending id order so concurrent account deletions cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select u from User u where u.id in :ids order by u.id asc")
    List<User> findWithReadLockByIdInOrderByIdAsc(@Param("ids") Collection<Long> ids);

    /**
     * Increments the stored value directly under the DB update lock, including when the caller read an older account
     * before another revocation. Omits {@code clearAutomatically} so callers holding a managed {@code User} must call
     * {@code entityManager.refresh(user)} before reading {@code sessionVersion}.
     */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.sessionVersion = u.sessionVersion + 1, u.updatedAt = :now where u.id = :id")
    void incrementSessionVersion(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Each account counts once in the total and once in each of its current roles. */
    @Query("""
            select new com.example.aichinese.dto.admin.AdminAnalyticsResponse$UserCounts(
                count(distinct u.id), count(case when r.name = STUDENT then 1 end), count(case when r.name = ADMIN then 1 end))
            from User u join u.roles r where u.deletedAt is null
            """)
    UserCounts countCurrentAccounts();

    @EntityGraph(attributePaths = { "roles" })
    List<User> findByIdIn(Collection<Long> ids);

    @Query("select distinct u from User u left join fetch u.roles where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") Long id);

    /**
     * The admin Users list: name or e-mail keyword, role and account state filters. Accounts deleted by their owners,
     * and accounts left without a role (teacher accounts when teachers were removed), are left out. Callers pass the
     * desired sort order via {@link Pageable} (e.g. {@link com.example.aichinese.util.PaginationUtils#newestFirst}).
     */
    @Query("""
            select u.id from User u
            where u.deletedAt is null
              and exists (
                  select 1 from User u2 join u2.roles r
                  where u2 = u and (:roleName is null or r.name = :roleName)
              )
              and (:keyword is null
                   or locate(lower(:keyword), lower(u.displayName)) > 0
                   or locate(lower(:keyword), lower(u.email)) > 0)
              and (:active is null or u.active = :active)
            """)
    Page<Long> findAdminUserIds(
            @Param("roleName") RoleName roleName,
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable);

    @EntityGraph(attributePaths = { "roles" })
    @Query("select distinct u from User u join u.roles r where r.name = :roleName and u.deletedAt is null")
    List<User> findAllByRoleName(@Param("roleName") RoleName roleName);

    @Query("select u.id from User u join u.roles r where r.name = :roleName and u.active = true and u.deletedAt is null")
    List<Long> findActiveIdsByRoleName(@Param("roleName") RoleName roleName);

    @Query("""
            select distinct u from User u join u.roles r
            where r.name = com.example.aichinese.entity.enums.RoleName.ADMIN
              and u.active = true and u.deletedAt is null and u.notifyReviewQueues = true
            """)
    List<User> findActiveDigestAdmins();

    /** Bounded 15-minute bucket count for platform-day registration aggregation across arbitrary timezones and DST. */
    interface QuarterHourCount {
        String getBucket();
        long getCount();
    }

    /**
     * Counts accounts created in [start, end) grouped into 15-minute server-local buckets (at most 96 rows/day,
     * covering every timezone offset including half/quarter-hour offsets and DST without loading all rows into heap).
     */
    @Query(value = """
            select concat(date_format(u.created_at, '%Y-%m-%d %H:'),
                          lpad(floor(minute(u.created_at) / 15) * 15, 2, '0'),
                          ':00') as bucket,
                   count(*) as count
            from users u
            where u.created_at >= :start and u.created_at < :end
              and u.deleted_at is null
              and exists (select 1 from user_roles ur where ur.user_id = u.id)
            group by bucket
            """, nativeQuery = true)
    List<QuarterHourCount> countCreatedByQuarterHourBetween(
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
