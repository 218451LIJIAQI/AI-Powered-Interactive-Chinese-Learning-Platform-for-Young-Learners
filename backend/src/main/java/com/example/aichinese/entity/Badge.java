package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "badges",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_badges_name", columnNames = "name")
        }
)
public class Badge extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "icon_key", length = 100)
    private String iconKey;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
