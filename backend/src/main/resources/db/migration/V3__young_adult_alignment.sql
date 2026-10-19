-- =====================================================================
-- V3 - Young-adult alignment (MySQL 8.4).
--
-- The Proposal targets young adult Mandarin beginners (18-25) in
-- Malaysia, so the child-targeting artefacts created by V1 are removed:
--
--   1. drop the age_band indexes and columns of the six practice-activity
--      tables (flashcards, matching_games, listening_exercises,
--      tracing_exercises, drag_drop_activities, story_activities);
--   2. rename the AI report reason AGE_INAPPROPRIATE to
--      INAPPROPRIATE_CONTENT and replace the reason CHECK constraint;
--   3. platform settings: rename the "Young Learners" platform name,
--      replace the old default AI disclaimer and delete the four admin
--      toggles that were never enforced (only the AI disclaimer, the
--      sensitive-word filter and the AI safety check remain);
--   4. map the legacy Chinese language code "zh" to "zh-CN".
--
-- student_profiles.school_name is kept; it is exposed as the optional
-- "institution" field. Every DDL step is guarded through
-- information_schema so a partially applied database does not block the
-- migration. V1 and V2 are never edited (Flyway checksum stability).
-- =====================================================================

-- ===== Step 1a: age_band indexes =====

set @idx_sql = (
    select if(count(*) > 0, 'alter table flashcards drop index idx_flashcards_age_band', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_age_band'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table matching_games drop index idx_matching_games_age_band', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_age_band'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table listening_exercises drop index idx_listening_exercises_age_band', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_age_band'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table tracing_exercises drop index idx_tracing_exercises_age_band', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_age_band'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table drag_drop_activities drop index idx_drag_drop_activities_age_band', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_age_band'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table story_activities drop index idx_story_activities_age_band', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_age_band'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

-- ===== Step 1b: age_band columns =====

set @col_sql = (
    select if(count(*) > 0, 'alter table flashcards drop column age_band', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'flashcards' and column_name = 'age_band'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table matching_games drop column age_band', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'matching_games' and column_name = 'age_band'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table listening_exercises drop column age_band', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'listening_exercises' and column_name = 'age_band'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table tracing_exercises drop column age_band', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'age_band'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table drag_drop_activities drop column age_band', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'age_band'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table story_activities drop column age_band', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'story_activities' and column_name = 'age_band'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== Step 2: AI report reason AGE_INAPPROPRIATE -> INAPPROPRIATE_CONTENT =====

set @chk_sql = (
    select if(count(*) > 0,
              'alter table ai_response_reports drop check chk_ai_response_reports_reason_category',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'ai_response_reports'
      and constraint_name = 'chk_ai_response_reports_reason_category'
      and constraint_type = 'CHECK'
);
prepare chk_stmt from @chk_sql;
execute chk_stmt;
deallocate prepare chk_stmt;

update ai_response_reports
set reason_category = 'INAPPROPRIATE_CONTENT'
where reason_category = 'AGE_INAPPROPRIATE';

alter table ai_response_reports
    add constraint chk_ai_response_reports_reason_category check (
        reason_category in ('INCORRECT_CONTENT', 'HALLUCINATION', 'UNSAFE_CONTENT', 'INAPPROPRIATE_CONTENT', 'OTHER'));

-- ===== Step 3: platform settings =====

update system_settings
set setting_value = 'AI-Powered Interactive Chinese Learning Platform for Young Adults in Malaysia'
where setting_key = 'platform_name'
  and setting_value like '%Young Learners%';

update system_settings
set setting_value = 'AI responses may contain errors and do not replace your teacher.',
    description = 'Shown next to every AI-powered learning feature.'
where setting_key = 'ai_disclaimer_text'
  and setting_value = 'AI-generated content may not always be accurate. Please review before use.';

update system_settings
set description = 'Block unsafe words in teacher-authored content.'
where setting_key = 'sensitive_word_filter_enabled';

update system_settings
set description = 'Screen learner AI requests and AI replies for unsafe content.'
where setting_key = 'ai_safety_check_enabled';

delete from system_settings
where setting_key in (
    'admin_email_notifications_enabled',
    'admin_in_app_notifications_enabled',
    'admin_daily_summary_enabled',
    'strict_content_filtering_enabled');

-- ===== Step 4: interface language code zh -> zh-CN =====

update users
set preferred_language = 'zh-CN'
where lower(preferred_language) in ('zh', 'zh-cn', 'zh_cn');
