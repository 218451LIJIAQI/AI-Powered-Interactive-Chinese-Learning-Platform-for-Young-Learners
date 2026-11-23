package com.example.aichinese.entity.base;

import java.time.LocalDateTime;

import org.hibernate.proxy.HibernateProxy;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Keeps a creation or change time set before the first save; the ones not set become now. */
    @PrePersist
    protected void onCreate() {
        final LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /**
     * Stamps each flushed change of a row with now, replacing an updatedAt set by hand. Bulk JPQL updates skip this
     * callback, so those queries set updatedAt themselves where it matters.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that) || entityType() != that.entityType()) {
            return false;
        }
        Long thisId = entityId();
        return thisId != null && thisId.equals(that.entityId());
    }

    @Override
    public final int hashCode() {
        return entityType().hashCode();
    }

    @Override
    public final String toString() {
        return entityType().getSimpleName() + "(id=" + entityId() + ")";
    }

    // All concrete entities have their own table. The proxy already knows its type and key, even after detachment.
    private Class<?> entityType() {
        return this instanceof HibernateProxy proxy ? proxy.getHibernateLazyInitializer().getPersistentClass() : getClass();
    }

    protected final Long entityId() {
        return this instanceof HibernateProxy proxy ? (Long) proxy.getHibernateLazyInitializer().getInternalIdentifier() : id;
    }
}
