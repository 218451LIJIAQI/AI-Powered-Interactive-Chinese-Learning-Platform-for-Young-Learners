update cart_items set unit_price = 0.00 where unit_price is null;
update ai_feedback_records
set input_text = concat('legacy_content_redacted_length=', char_length(input_text))
where input_text is not null and input_text not regexp '^(content_length|legacy_content_redacted_length)=';
update ai_feedback_records
set feedback_text = concat('legacy_content_redacted_length=', char_length(feedback_text))
where feedback_text is not null and feedback_text not regexp '^(content_length|legacy_content_redacted_length)=';
update ai_feedback_records set input_file_url = null where input_file_url is not null;

set @v12_cart_duplicate_count = (
    select count(*) from (
        select cart_id, item_type, item_id
        from cart_items
        group by cart_id, item_type, item_id
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_cart_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: duplicate cart item tuples exist''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_lesson_progress_duplicate_count = (
    select count(*) from (
        select student_id, lesson_id
        from lesson_progress
        group by student_id, lesson_id
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_lesson_progress_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: duplicate lesson progress tuples exist''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_student_progress_duplicate_count = (
    select count(*) from (
        select student_id
        from student_progress
        group by student_id
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_student_progress_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: duplicate student progress summaries exist''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_daily_streak_duplicate_count = (
    select count(*) from (
        select student_id
        from daily_streaks
        group by student_id
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_daily_streak_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: duplicate daily streak rows exist''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_points_duplicate_count = (
    select count(*) from (
        select student_id, source_type, source_id
        from points_transactions
        where source_type is not null and source_id is not null
        group by student_id, source_type, source_id
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_points_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: duplicate point-award source tuples exist''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_uploaded_duplicate_count = (
    select count(*) from (
        select stored_file_name
        from uploaded_files
        group by stored_file_name
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_uploaded_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: duplicate stored upload filenames exist''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_active_reset_token_duplicate_count = (
    select count(*) from (
        select user_id
        from password_reset_tokens
        where used_at is null
        group by user_id
        having count(*) > 1
    ) duplicates
);
set @v12_sql = if(@v12_active_reset_token_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: multiple active password reset tokens exist for a user''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_invalid_count = (
    select
        (select count(*) from cart_items where quantity <= 0 or unit_price < 0)
      + (select count(*) from lesson_progress where completion_percent is not null and (completion_percent < 0 or completion_percent > 100))
      + (select count(*) from student_progress where completion_percent is not null and (completion_percent < 0 or completion_percent > 100))
      + (select count(*) from daily_streaks where current_streak < 0 or best_streak < 0 or best_streak < current_streak)
      + (select count(*) from points_transactions where points_delta < 0 or (source_type is null and source_id is not null) or (source_type is not null and source_id is null))
      + (select count(*) from video_progress where recorded_video_id is null or watched_seconds < 0 or total_seconds < 0 or progress_percentage < 0 or progress_percentage > 100 or watched_seconds > total_seconds)
      + (select count(*) from live_classes where max_participants is not null and max_participants <= 0)
      + (select count(*) from uploaded_files where file_size < 0 or access_level not in ('PRIVATE'))
      + (select count(*) from users where session_version < 0)
      + (select count(*) from password_reset_tokens where used_at is not null and used_at < created_at)
);
set @v12_sql = if(@v12_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''V12 blocked: rows violate strengthened integrity checks''');
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select is_nullable from information_schema.columns where table_schema = database() and table_name = 'cart_items' and column_name = 'unit_price') = 'YES',
    'alter table cart_items modify unit_price decimal(10,2) not null',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'cart_items' and constraint_name = 'uk_cart_items_cart_item') = 0,
    'alter table cart_items add constraint uk_cart_items_cart_item unique (cart_id, item_type, item_id)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'lesson_progress' and column_name = 'version') = 0,
    'alter table lesson_progress add column version bigint not null default 0',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'lesson_progress' and constraint_name = 'uk_lesson_progress_student_lesson') = 0,
    'alter table lesson_progress add constraint uk_lesson_progress_student_lesson unique (student_id, lesson_id)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'lesson_progress' and constraint_name = 'chk_lesson_progress_percent_range') = 0,
    'alter table lesson_progress add constraint chk_lesson_progress_percent_range check (completion_percent is null or completion_percent between 0 and 100)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'student_progress' and column_name = 'version') = 0,
    'alter table student_progress add column version bigint not null default 0',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'student_progress' and constraint_name = 'uk_student_progress_student') = 0,
    'alter table student_progress add constraint uk_student_progress_student unique (student_id)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'student_progress' and constraint_name = 'chk_student_progress_percent_range') = 0,
    'alter table student_progress add constraint chk_student_progress_percent_range check (completion_percent is null or completion_percent between 0 and 100)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'daily_streaks' and column_name = 'version') = 0,
    'alter table daily_streaks add column version bigint not null default 0',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'daily_streaks' and constraint_name = 'uk_daily_streaks_student') = 0,
    'alter table daily_streaks add constraint uk_daily_streaks_student unique (student_id)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'daily_streaks' and constraint_name = 'chk_daily_streaks_non_negative') = 0,
    'alter table daily_streaks add constraint chk_daily_streaks_non_negative check (current_streak >= 0 and best_streak >= 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'daily_streaks' and constraint_name = 'chk_daily_streaks_best_not_less_current') = 0,
    'alter table daily_streaks add constraint chk_daily_streaks_best_not_less_current check (best_streak >= current_streak)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'points_transactions' and column_name = 'source_key') = 0,
    'alter table points_transactions add column source_key varchar(220) generated always as (case when source_type is not null and source_id is not null then concat(source_type, ''#'', source_id) else null end) stored',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'uk_points_transactions_student_source') = 0,
    'alter table points_transactions add constraint uk_points_transactions_student_source unique (student_id, source_key)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'chk_points_transactions_delta_non_negative') = 0,
    'alter table points_transactions add constraint chk_points_transactions_delta_non_negative check (points_delta >= 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'chk_points_transactions_source_pair') = 0,
    'alter table points_transactions add constraint chk_points_transactions_source_pair check ((source_type is null and source_id is null) or (source_type is not null and source_id is not null))',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'video_progress' and column_name = 'version') = 0,
    'alter table video_progress add column version bigint not null default 0',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_recorded_video_fk_name = (
    select constraint_name
    from information_schema.key_column_usage
    where table_schema = database()
      and table_name = 'video_progress'
      and column_name = 'recorded_video_id'
      and referenced_table_name = 'recorded_lesson_videos'
    limit 1
);
set @v12_sql = if(
    @v12_recorded_video_fk_name is null,
    'select 1',
    concat('alter table video_progress drop foreign key `', replace(@v12_recorded_video_fk_name, '`', '``'), '`')
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select is_nullable from information_schema.columns where table_schema = database() and table_name = 'video_progress' and column_name = 'recorded_video_id') = 'YES',
    'alter table video_progress modify recorded_video_id bigint not null',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*)
     from information_schema.key_column_usage
     where table_schema = database()
       and table_name = 'video_progress'
       and column_name = 'recorded_video_id'
       and referenced_table_name = 'recorded_lesson_videos') = 0,
    'alter table video_progress add constraint FKdjbxt2h00nn3l4ckqkc0f92mo foreign key (recorded_video_id) references recorded_lesson_videos (id)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_seconds_non_negative') = 0,
    'alter table video_progress add constraint chk_video_progress_seconds_non_negative check (watched_seconds is null or watched_seconds >= 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_total_seconds_non_negative') = 0,
    'alter table video_progress add constraint chk_video_progress_total_seconds_non_negative check (total_seconds is null or total_seconds >= 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_percentage_range') = 0,
    'alter table video_progress add constraint chk_video_progress_percentage_range check (progress_percentage is null or progress_percentage between 0 and 100)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_watched_within_total') = 0,
    'alter table video_progress add constraint chk_video_progress_watched_within_total check (watched_seconds is null or total_seconds is null or watched_seconds <= total_seconds)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'live_classes' and column_name = 'version') = 0,
    'alter table live_classes add column version bigint not null default 0',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_classes' and constraint_name = 'chk_live_classes_max_participants_positive') = 0,
    'alter table live_classes add constraint chk_live_classes_max_participants_positive check (max_participants is null or max_participants > 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_classes' and constraint_name = 'chk_live_classes_time_order') = 0,
    'alter table live_classes add constraint chk_live_classes_time_order check (scheduled_start_at is null or scheduled_end_at is null or scheduled_end_at > scheduled_start_at)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_classes' and constraint_name = 'chk_live_classes_linked_content') = 0,
    'alter table live_classes add constraint chk_live_classes_linked_content check (lesson_id is not null or course_package_id is not null)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'live_class_enrolments' and column_name = 'version') = 0,
    'alter table live_class_enrolments add column version bigint not null default 0',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'live_class_enrolments' and column_name = 'active_enrolment_key') = 0,
    'alter table live_class_enrolments add column active_enrolment_key varchar(220) generated always as (case when enrolment_status in (''ENROLLED'',''ATTENDED'') then concat(live_class_id, ''#'', student_id) else null end) stored',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_class_enrolments' and constraint_name = 'uk_live_class_enrolments_active_student') = 0,
    'alter table live_class_enrolments add constraint uk_live_class_enrolments_active_student unique (active_enrolment_key)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'uploaded_files' and constraint_name = 'uk_uploaded_files_stored_file_name') = 0,
    'alter table uploaded_files add constraint uk_uploaded_files_stored_file_name unique (stored_file_name)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'uploaded_files' and constraint_name = 'chk_uploaded_files_file_size_non_negative') = 0,
    'alter table uploaded_files add constraint chk_uploaded_files_file_size_non_negative check (file_size >= 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'uploaded_files' and constraint_name = 'chk_uploaded_files_private_access') = 0,
    'alter table uploaded_files add constraint chk_uploaded_files_private_access check (access_level in (''PRIVATE''))',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'users' and constraint_name = 'chk_users_session_version_non_negative') = 0,
    'alter table users add constraint chk_users_session_version_non_negative check (session_version >= 0)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'password_reset_tokens' and column_name = 'active_token_user_id') = 0,
    'alter table password_reset_tokens add column active_token_user_id bigint generated always as (case when used_at is null then user_id else null end) stored',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'password_reset_tokens' and constraint_name = 'uk_password_reset_tokens_active_user') = 0,
    'alter table password_reset_tokens add constraint uk_password_reset_tokens_active_user unique (active_token_user_id)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @v12_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'password_reset_tokens' and constraint_name = 'chk_password_reset_tokens_used_after_created') = 0,
    'alter table password_reset_tokens add constraint chk_password_reset_tokens_used_after_created check (used_at is null or used_at >= created_at)',
    'select 1'
);
prepare v12_stmt from @v12_sql;
execute v12_stmt;
deallocate prepare v12_stmt;

set @add_cart_items_cart_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'cart_items' and index_name = 'idx_cart_items_cart_created_at') = 0,
    'create index idx_cart_items_cart_created_at on cart_items (cart_id, created_at)',
    'select 1'
);
prepare add_cart_items_cart_created_at_index_stmt from @add_cart_items_cart_created_at_index;
execute add_cart_items_cart_created_at_index_stmt;
deallocate prepare add_cart_items_cart_created_at_index_stmt;

set @add_lesson_progress_student_updated_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'lesson_progress' and index_name = 'idx_lesson_progress_student_updated') = 0,
    'create index idx_lesson_progress_student_updated on lesson_progress (student_id, updated_at)',
    'select 1'
);
prepare add_lesson_progress_student_updated_index_stmt from @add_lesson_progress_student_updated_index;
execute add_lesson_progress_student_updated_index_stmt;
deallocate prepare add_lesson_progress_student_updated_index_stmt;

set @add_student_badges_student_awarded_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'student_badges' and index_name = 'idx_student_badges_student_awarded') = 0,
    'create index idx_student_badges_student_awarded on student_badges (student_id, awarded_at)',
    'select 1'
);
prepare add_student_badges_student_awarded_index_stmt from @add_student_badges_student_awarded_index;
execute add_student_badges_student_awarded_index_stmt;
deallocate prepare add_student_badges_student_awarded_index_stmt;

set @add_points_transactions_student_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'points_transactions' and index_name = 'idx_points_transactions_student_created') = 0,
    'create index idx_points_transactions_student_created on points_transactions (student_id, created_at)',
    'select 1'
);
prepare add_points_transactions_student_created_index_stmt from @add_points_transactions_student_created_index;
execute add_points_transactions_student_created_index_stmt;
deallocate prepare add_points_transactions_student_created_index_stmt;

set @add_live_class_enrolments_class_status_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'live_class_enrolments' and index_name = 'idx_live_class_enrolments_class_status') = 0,
    'create index idx_live_class_enrolments_class_status on live_class_enrolments (live_class_id, enrolment_status)',
    'select 1'
);
prepare add_live_class_enrolments_class_status_index_stmt from @add_live_class_enrolments_class_status_index;
execute add_live_class_enrolments_class_status_index_stmt;
deallocate prepare add_live_class_enrolments_class_status_index_stmt;

set @add_live_class_enrolments_student_status_start_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'live_class_enrolments' and index_name = 'idx_live_class_enrolments_student_status_start') = 0,
    'create index idx_live_class_enrolments_student_status_start on live_class_enrolments (student_id, enrolment_status, live_class_id)',
    'select 1'
);
prepare add_live_class_enrolments_student_status_start_index_stmt from @add_live_class_enrolments_student_status_start_index;
execute add_live_class_enrolments_student_status_start_index_stmt;
deallocate prepare add_live_class_enrolments_student_status_start_index_stmt;

set @add_video_progress_teacher_lesson_watch_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'video_progress' and index_name = 'idx_video_progress_lesson_last_watched') = 0,
    'create index idx_video_progress_lesson_last_watched on video_progress (lesson_id, last_watched_at)',
    'select 1'
);
prepare add_video_progress_teacher_lesson_watch_index_stmt from @add_video_progress_teacher_lesson_watch_index;
execute add_video_progress_teacher_lesson_watch_index_stmt;
deallocate prepare add_video_progress_teacher_lesson_watch_index_stmt;

set @add_teacher_profiles_status_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'teacher_profiles' and index_name = 'idx_teacher_profiles_status_created') = 0,
    'create index idx_teacher_profiles_status_created on teacher_profiles (teacher_status, created_at)',
    'select 1'
);
prepare add_teacher_profiles_status_created_index_stmt from @add_teacher_profiles_status_created_index;
execute add_teacher_profiles_status_created_index_stmt;
deallocate prepare add_teacher_profiles_status_created_index_stmt;

set @add_audit_logs_target_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_target_created') = 0,
    'create index idx_audit_logs_target_created on audit_logs (target_type, target_id, created_at)',
    'select 1'
);
prepare add_audit_logs_target_created_index_stmt from @add_audit_logs_target_created_index;
execute add_audit_logs_target_created_index_stmt;
deallocate prepare add_audit_logs_target_created_index_stmt;

set @add_audit_logs_actor_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_actor_created') = 0,
    'create index idx_audit_logs_actor_created on audit_logs (actor_user_id, created_at)',
    'select 1'
);
prepare add_audit_logs_actor_created_index_stmt from @add_audit_logs_actor_created_index;
execute add_audit_logs_actor_created_index_stmt;
deallocate prepare add_audit_logs_actor_created_index_stmt;
