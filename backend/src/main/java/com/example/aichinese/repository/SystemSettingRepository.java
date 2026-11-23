package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.SystemSetting;

import jakarta.persistence.LockModeType;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    /** Creates an empty setting or locks the existing row until commit, preserving its value. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into system_settings (setting_key, setting_value, created_at, updated_at)
            values (:key, '', current_timestamp(6), current_timestamp(6))
            on duplicate key update setting_key = setting_key
            """, nativeQuery = true)
    void lockOrCreateEmpty(@Param("key") String key);

    /** Both startup seeders acquire this row before reading their inputs. */
    default void lockForSeeding() {
        lockOrCreateEmpty("startup_seed_lock");
    }

    Optional<SystemSetting> findBySettingKey(String settingKey);

    List<SystemSetting> findBySettingKeyIn(Collection<String> settingKeys);

    /** Locks settings rows in ascending key order so concurrent admin saves never interleave or deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSetting s where s.settingKey in :keys order by s.settingKey asc")
    List<SystemSetting> findLockedBySettingKeyInOrderBySettingKeyAsc(@Param("keys") Collection<String> keys);

    boolean existsBySettingKey(String settingKey);

    @Query("select s.settingValue from SystemSetting s where s.settingKey = :key")
    Optional<String> findValueByKey(@Param("key") String key);

    /**
     * Sets the setting to {@code value} only while its stored value sorts before it (an ISO date sorts by day): 1 when
     * this caller moved it on. The row lock makes a concurrent caller wait and then find the new value.
     * Omits {@code clearAutomatically}; any previously managed {@code SystemSetting} in the same transaction must be
     * refreshed before reading {@code settingValue}.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update SystemSetting s set s.settingValue = :value, s.updatedAt = :now
            where s.settingKey = :key and s.settingValue < :value
            """)
    int advanceValue(@Param("key") String key, @Param("value") String value, @Param("now") LocalDateTime now);
}
