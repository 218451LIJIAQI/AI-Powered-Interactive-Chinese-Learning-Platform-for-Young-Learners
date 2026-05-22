set @add_payment_records_student_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'payment_records' and index_name = 'idx_payment_records_student_created_at') = 0,
    'create index idx_payment_records_student_created_at on payment_records (student_id, created_at)',
    'select 1'
);
prepare add_payment_records_student_created_at_index_stmt from @add_payment_records_student_created_at_index;
execute add_payment_records_student_created_at_index_stmt;
deallocate prepare add_payment_records_student_created_at_index_stmt;

set @add_refund_requests_student_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'refund_requests' and index_name = 'idx_refund_requests_student_created_at') = 0,
    'create index idx_refund_requests_student_created_at on refund_requests (student_id, created_at)',
    'select 1'
);
prepare add_refund_requests_student_created_at_index_stmt from @add_refund_requests_student_created_at_index;
execute add_refund_requests_student_created_at_index_stmt;
deallocate prepare add_refund_requests_student_created_at_index_stmt;

set @add_refund_requests_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'refund_requests' and index_name = 'idx_refund_requests_status_created_at') = 0,
    'create index idx_refund_requests_status_created_at on refund_requests (refund_status, created_at)',
    'select 1'
);
prepare add_refund_requests_status_created_at_index_stmt from @add_refund_requests_status_created_at_index;
execute add_refund_requests_status_created_at_index_stmt;
deallocate prepare add_refund_requests_status_created_at_index_stmt;

set @add_payout_requests_teacher_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'payout_requests' and index_name = 'idx_payout_requests_teacher_created_at') = 0,
    'create index idx_payout_requests_teacher_created_at on payout_requests (teacher_id, created_at)',
    'select 1'
);
prepare add_payout_requests_teacher_created_at_index_stmt from @add_payout_requests_teacher_created_at_index;
execute add_payout_requests_teacher_created_at_index_stmt;
deallocate prepare add_payout_requests_teacher_created_at_index_stmt;

set @add_payout_requests_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'payout_requests' and index_name = 'idx_payout_requests_status_created_at') = 0,
    'create index idx_payout_requests_status_created_at on payout_requests (payout_status, created_at)',
    'select 1'
);
prepare add_payout_requests_status_created_at_index_stmt from @add_payout_requests_status_created_at_index;
execute add_payout_requests_status_created_at_index_stmt;
deallocate prepare add_payout_requests_status_created_at_index_stmt;

set @add_teacher_earnings_teacher_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'teacher_earnings' and index_name = 'idx_teacher_earnings_teacher_created_at') = 0,
    'create index idx_teacher_earnings_teacher_created_at on teacher_earnings (teacher_id, created_at)',
    'select 1'
);
prepare add_teacher_earnings_teacher_created_at_index_stmt from @add_teacher_earnings_teacher_created_at_index;
execute add_teacher_earnings_teacher_created_at_index_stmt;
deallocate prepare add_teacher_earnings_teacher_created_at_index_stmt;

set @add_notifications_user_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'notifications' and index_name = 'idx_notifications_user_status_created_at') = 0,
    'create index idx_notifications_user_status_created_at on notifications (user_id, notification_status, created_at)',
    'select 1'
);
prepare add_notifications_user_status_created_at_index_stmt from @add_notifications_user_status_created_at_index;
execute add_notifications_user_status_created_at_index_stmt;
deallocate prepare add_notifications_user_status_created_at_index_stmt;

set @add_ai_response_reports_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'ai_response_reports' and index_name = 'idx_ai_response_reports_status_created_at') = 0,
    'create index idx_ai_response_reports_status_created_at on ai_response_reports (status, created_at)',
    'select 1'
);
prepare add_ai_response_reports_status_created_at_index_stmt from @add_ai_response_reports_status_created_at_index;
execute add_ai_response_reports_status_created_at_index_stmt;
deallocate prepare add_ai_response_reports_status_created_at_index_stmt;

set @add_ai_usage_logs_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'ai_usage_logs' and index_name = 'idx_ai_usage_logs_status_created_at') = 0,
    'create index idx_ai_usage_logs_status_created_at on ai_usage_logs (status, created_at)',
    'select 1'
);
prepare add_ai_usage_logs_status_created_at_index_stmt from @add_ai_usage_logs_status_created_at_index;
execute add_ai_usage_logs_status_created_at_index_stmt;
deallocate prepare add_ai_usage_logs_status_created_at_index_stmt;

set @add_audit_logs_action_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_action_created_at') = 0,
    'create index idx_audit_logs_action_created_at on audit_logs (action_type, created_at)',
    'select 1'
);
prepare add_audit_logs_action_created_at_index_stmt from @add_audit_logs_action_created_at_index;
execute add_audit_logs_action_created_at_index_stmt;
deallocate prepare add_audit_logs_action_created_at_index_stmt;

set @add_live_classes_teacher_status_start_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'live_classes' and index_name = 'idx_live_classes_teacher_status_start') = 0,
    'create index idx_live_classes_teacher_status_start on live_classes (teacher_id, live_class_status, scheduled_start_at)',
    'select 1'
);
prepare add_live_classes_teacher_status_start_index_stmt from @add_live_classes_teacher_status_start_index;
execute add_live_classes_teacher_status_start_index_stmt;
deallocate prepare add_live_classes_teacher_status_start_index_stmt;

set @add_recorded_videos_lesson_active_sort_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'recorded_lesson_videos' and index_name = 'idx_recorded_videos_lesson_active_sort') = 0,
    'create index idx_recorded_videos_lesson_active_sort on recorded_lesson_videos (lesson_id, active, sort_order)',
    'select 1'
);
prepare add_recorded_videos_lesson_active_sort_index_stmt from @add_recorded_videos_lesson_active_sort_index;
execute add_recorded_videos_lesson_active_sort_index_stmt;
deallocate prepare add_recorded_videos_lesson_active_sort_index_stmt;

set @add_learning_modules_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'learning_modules' and index_name = 'idx_learning_modules_status_created_at') = 0,
    'create index idx_learning_modules_status_created_at on learning_modules (content_status, created_at)',
    'select 1'
);
prepare add_learning_modules_status_created_at_index_stmt from @add_learning_modules_status_created_at_index;
execute add_learning_modules_status_created_at_index_stmt;
deallocate prepare add_learning_modules_status_created_at_index_stmt;

set @add_lessons_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'lessons' and index_name = 'idx_lessons_status_created_at') = 0,
    'create index idx_lessons_status_created_at on lessons (content_status, created_at)',
    'select 1'
);
prepare add_lessons_status_created_at_index_stmt from @add_lessons_status_created_at_index;
execute add_lessons_status_created_at_index_stmt;
deallocate prepare add_lessons_status_created_at_index_stmt;

set @add_course_packages_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'course_packages' and index_name = 'idx_course_packages_status_created_at') = 0,
    'create index idx_course_packages_status_created_at on course_packages (content_status, created_at)',
    'select 1'
);
prepare add_course_packages_status_created_at_index_stmt from @add_course_packages_status_created_at_index;
execute add_course_packages_status_created_at_index_stmt;
deallocate prepare add_course_packages_status_created_at_index_stmt;
