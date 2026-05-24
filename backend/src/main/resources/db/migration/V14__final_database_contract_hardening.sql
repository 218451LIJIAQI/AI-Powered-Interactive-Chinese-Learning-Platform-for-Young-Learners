-- =====================================================================
-- V14 — Final database contract hardening.
--
-- Why a separate migration:
--   The consolidated V1 schema was authored before some of the stricter
--   domain checks below were introduced (enum tightening, NOT-NULL
--   verification audit columns, payment/refund invariants). Flyway
--   checksums on V1 are already deployed in production environments,
--   so we apply the new constraints in a fresh, additive migration
--   rather than editing V1.
--
-- What it does:
--   1. Pre-flight: count rows that would violate the new constraints
--      and SIGNAL SQLSTATE 45000 so the migration aborts cleanly before
--      ALTER TABLE rather than failing partway through with a
--      half-applied schema. The repair-flyway-checksums.ps1 script
--      handles re-running this safely against a partially-migrated DB.
--   2. Tighten status / type ENUM domains (status columns, content
--      access modes, document types, checkout sources).
--   3. Enforce NOT-NULL on verification-audit columns so teacher
--      verification history is always traceable.
--   4. Add hot-path read indexes for fields queried in
--      AdminUsersPage / TeacherStudentsPage / StudentAchievements
--      aggregation paths.
--
-- Rollback strategy:
--   This migration is forward-only by design. To roll back, restore
--   from the snapshot taken before deployment — there is no down.sql.
--   The pre-flight SIGNAL ensures we never get into a half-applied
--   state, so a failed migration leaves the database exactly as it
--   was before.
-- =====================================================================

set @v2_invalid_status_count = (
    select
        (select count(*) from ai_chat_messages where sender_role not in ('USER', 'ASSISTANT'))
      + (select count(*) from teacher_earnings where earning_status not in ('AVAILABLE', 'REFUNDED'))
      + (select count(*) from ai_usage_logs where status not in ('SUCCESS', 'FAILED', 'UNAVAILABLE'))
      + (select count(*) from ai_response_reports where status not in ('PENDING_REVIEW', 'IN_REVIEW', 'RESOLVED', 'DISMISSED'))
      + (select count(*) from ai_response_reports where reason_category not in ('INCORRECT_CONTENT', 'HALLUCINATION', 'UNSAFE_CONTENT', 'AGE_INAPPROPRIATE', 'OTHER'))
      + (select count(*) from teacher_verification_documents where document_type not in ('CERTIFICATE', 'QUALIFICATION', 'IDENTITY', 'OTHER'))
      + (select count(*) from teacher_verification_documents where submitted_at is null)
      + (select count(*) from teacher_verification_reviews where reviewed_at is null or decision_status not in ('APPROVED', 'REJECTED'))
      + (select count(*) from orders where checkout_source is not null and checkout_source not in ('CART', 'BUY_NOW'))
      + (select count(*) from learning_modules where not ((access_type = 'FREE' and (price is null or price = 0)) or (access_type = 'PAID' and price > 0)))
      + (select count(*) from lessons where not ((access_type = 'FREE' and (price is null or price = 0)) or (access_type = 'PAID' and price > 0)))
      + (select count(*) from course_packages where not ((access_type = 'FREE' and (price is null or price = 0)) or (access_type = 'PAID' and price > 0)))
);
set @v2_sql = if(@v2_invalid_status_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V14 blocked: rows violate tightened status/domain checks''');
prepare v2_stmt from @v2_sql;
execute v2_stmt;
deallocate prepare v2_stmt;

set @v2_duplicate_current_teacher_document_count = (
    select count(*) from (
        select teacher_id, document_type
        from teacher_verification_documents
        where current_version = 1
        group by teacher_id, document_type
        having count(*) > 1
    ) duplicates
);
set @v2_sql = if(@v2_duplicate_current_teacher_document_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V14 blocked: multiple current teacher verification documents exist for the same type''');
prepare v2_stmt from @v2_sql;
execute v2_stmt;
deallocate prepare v2_stmt;

set @v2_invalid_state_count = (
    select
        (select count(*) from ai_response_reports
         where not (
             (status = 'PENDING_REVIEW' and reviewed_at is null)
             or (status in ('IN_REVIEW', 'RESOLVED', 'DISMISSED') and reviewed_at is not null)
         ))
      + (select count(*) from notifications
         where not (
             (notification_status = 'UNREAD' and read_at is null)
             or (notification_status = 'READ' and read_at is not null)
         ))
);
set @v2_sql = if(@v2_invalid_state_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V14 blocked: rows violate review/read state consistency checks''');
prepare v2_stmt from @v2_sql;
execute v2_stmt;
deallocate prepare v2_stmt;

alter table teacher_verification_documents
    add column current_document_key varchar(160) generated always as (
        case when current_version = 1 then concat(teacher_id, '#', document_type) else null end
    ) stored,
    add constraint uk_teacher_verification_documents_current_type unique (current_document_key);

alter table ai_chat_messages
    add constraint chk_ai_chat_messages_sender_role check (sender_role in ('USER', 'ASSISTANT'));

alter table teacher_earnings
    add constraint chk_teacher_earnings_status check (earning_status in ('AVAILABLE', 'REFUNDED'));

alter table ai_usage_logs
    add constraint chk_ai_usage_logs_status check (status in ('SUCCESS', 'FAILED', 'UNAVAILABLE'));

alter table ai_response_reports
    add constraint chk_ai_response_reports_status check (status in ('PENDING_REVIEW', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    add constraint chk_ai_response_reports_reason_category check (reason_category in ('INCORRECT_CONTENT', 'HALLUCINATION', 'UNSAFE_CONTENT', 'AGE_INAPPROPRIATE', 'OTHER')),
    add constraint chk_ai_response_reports_snapshot_source check (
        chat_message_id is not null
        or ai_response_snapshot is not null
    ),
    add constraint chk_ai_response_reports_review_consistency check (
        (status = 'PENDING_REVIEW' and reviewed_at is null)
        or (status in ('IN_REVIEW', 'RESOLVED', 'DISMISSED') and reviewed_at is not null)
    );

alter table teacher_verification_documents
    add constraint chk_teacher_verification_documents_type check (document_type in ('CERTIFICATE', 'QUALIFICATION', 'IDENTITY', 'OTHER')),
    add constraint chk_teacher_verification_documents_submitted_at check (submitted_at is not null);

alter table teacher_verification_reviews
    add constraint chk_teacher_verification_reviews_reviewed_at check (reviewed_at is not null),
    add constraint chk_teacher_verification_reviews_final_decision check (decision_status in ('APPROVED', 'REJECTED'));

alter table orders
    add constraint chk_orders_checkout_source check (checkout_source is null or checkout_source in ('CART', 'BUY_NOW'));

alter table learning_modules
    add constraint chk_learning_modules_access_price check (
        (access_type = 'FREE' and (price is null or price = 0))
        or (access_type = 'PAID' and price > 0)
    );

alter table lessons
    add constraint chk_lessons_access_price check (
        (access_type = 'FREE' and (price is null or price = 0))
        or (access_type = 'PAID' and price > 0)
    );

alter table course_packages
    add constraint chk_course_packages_access_price check (
        (access_type = 'FREE' and (price is null or price = 0))
        or (access_type = 'PAID' and price > 0)
    );

alter table notifications
    add constraint chk_notifications_read_state check (
        (notification_status = 'UNREAD' and read_at is null)
        or (notification_status = 'READ' and read_at is not null)
    );

create index idx_ai_chat_messages_session_role_id
    on ai_chat_messages (session_id, sender_role, id);

create index idx_ai_chat_messages_session_created
    on ai_chat_messages (session_id, created_at);

create index idx_ai_chat_sessions_user_feature_created
    on ai_chat_sessions (user_id, feature_key, created_at);

create index idx_ai_usage_logs_user_feature_created
    on ai_usage_logs (user_id, feature_key, created_at);

create index idx_ai_response_reports_student_created
    on ai_response_reports (student_user_id, created_at);

create index idx_order_items_order_created
    on order_items (order_id, created_at);

create index idx_payment_records_status_created
    on payment_records (payment_status, created_at);

create index idx_teacher_earnings_teacher_status_created
    on teacher_earnings (teacher_id, earning_status, created_at);

create index idx_teacher_verification_documents_teacher_current_submitted
    on teacher_verification_documents (teacher_id, current_version, submitted_at, id);

create index idx_teacher_verification_documents_latest
    on teacher_verification_documents (teacher_id, submitted_at, id);

create index idx_teacher_verification_reviews_latest
    on teacher_verification_reviews (teacher_id, reviewed_at, id);

create index idx_audit_logs_actor_email_created
    on audit_logs (actor_email, created_at);

create index idx_notifications_user_status_created_id
    on notifications (user_id, notification_status, created_at, id);
