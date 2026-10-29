-- V10: shared groundwork for the product alignment with the low-fidelity prototype.
--
--   1. notifications.notification_type gains SUPPORT_REQUEST (Help & support requests and replies) and
--      CLASS_ENROLMENT (a student joined a teacher's class). The enum is redefined in full so the
--      latest definition lists every NotificationType value (SchemaEnumContractTests).
--
-- Later migrations of this alignment (V11 onwards) add each module's own tables.

-- ===== 1. notifications.notification_type =====

alter table notifications
    modify column notification_type enum (
        'ADMIN_TASK',
        'AI_REPORT_REVIEW',
        'CLASS_ENROLMENT',
        'CONTENT_REVIEW',
        'LIVE_CLASS_REMINDER',
        'MODERATION_ACTION',
        'PROGRESS_UPDATE',
        'SUPPORT_REQUEST',
        'SYSTEM',
        'TEACHER_APPROVAL',
        'TEACHER_FEEDBACK') not null;
