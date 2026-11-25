package com.example.aichinese.entity;

import com.example.aichinese.entity.base.PracticeActivityContent;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "drag_drop_activities", indexes = {
        @Index(name = "idx_drag_drop_activities_status_updated", columnList = "content_status,updated_at")
})
public class DragDropActivity extends PracticeActivityContent {
}
