-- =====================================================================
-- V9 - Schema cleanup (MySQL 8.4).
--
-- Removes columns, indexes and enum values that no code reads or writes:
--
--   1. notifications.notification_type loses QUIZ_RESULT and REWARD_EARNED
--      (nothing sends them); any such row becomes SYSTEM first.
--   2. live_class_enrolments.enrolment_status loses MISSED (nothing sets it).
--      A MISSED row becomes CANCELLED: like MISSED it holds no seat and
--      gives no join access, and its cancelled_at is filled from its last
--      update so the row stays consistent with other cancelled enrolments.
--      The teacher roster and the practice-submission access check count
--      only non-CANCELLED enrolments, so a remapped student no longer
--      reaches a teacher's roster through that enrolment. Nothing ever set
--      MISSED, so no existing row is expected to change.
--   3. student_profiles drops display_name (the name is users.display_name),
--      country and avatar_url. school_name stays: SDD Table 4.2 lists it.
--   4. ai_feedback_records drops input_file_url (the file is referenced
--      through uploaded_file_id; V1 already cleared the column).
--   5. ai_chat_sessions drops related_lesson_id with its foreign key and
--      index (V5): the chat request no longer carries a lesson.
--   6. The FULLTEXT indexes ft_lessons_title_summary_content,
--      ft_course_packages_title_description and ft_flashcards_learning_text
--      are dropped: no query uses MATCH ... AGAINST, and every write had to
--      maintain them.
--
-- Data updates only touch rows that still hold a removed value, and every
-- DDL step is guarded through information_schema, so the script can be run
-- again on a partially migrated database. V1-V8 are never edited.
-- =====================================================================

-- ===== 1. notifications.notification_type =====

update notifications
set notification_type = 'SYSTEM'
where notification_type in ('QUIZ_RESULT', 'REWARD_EARNED');

set @enum_sql = (
    select if(count(*) > 0,
              'alter table notifications modify column notification_type enum (''ADMIN_TASK'',''AI_REPORT_REVIEW'',''CONTENT_REVIEW'',''LIVE_CLASS_REMINDER'',''MODERATION_ACTION'',''PROGRESS_UPDATE'',''SYSTEM'',''TEACHER_APPROVAL'',''TEACHER_FEEDBACK'') not null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'notifications' and column_name = 'notification_type'
      and (column_type like '%''QUIZ_RESULT''%' or column_type like '%''REWARD_EARNED''%')
);
prepare enum_stmt from @enum_sql;
execute enum_stmt;
deallocate prepare enum_stmt;

-- ===== 2. live_class_enrolments.enrolment_status =====

update live_class_enrolments
set enrolment_status = 'CANCELLED',
    cancelled_at = coalesce(cancelled_at, updated_at)
where enrolment_status = 'MISSED';

set @enum_sql = (
    select if(count(*) > 0,
              'alter table live_class_enrolments modify column enrolment_status enum (''ATTENDED'',''CANCELLED'',''ENROLLED'') not null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'live_class_enrolments' and column_name = 'enrolment_status'
      and column_type like '%''MISSED''%'
);
prepare enum_stmt from @enum_sql;
execute enum_stmt;
deallocate prepare enum_stmt;

-- ===== 3. student_profiles =====

set @col_sql = (
    select if(count(*) > 0, 'alter table student_profiles drop column display_name', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'student_profiles' and column_name = 'display_name'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table student_profiles drop column country', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'student_profiles' and column_name = 'country'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table student_profiles drop column avatar_url', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'student_profiles' and column_name = 'avatar_url'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== 4. ai_feedback_records =====

set @col_sql = (
    select if(count(*) > 0, 'alter table ai_feedback_records drop column input_file_url', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'ai_feedback_records' and column_name = 'input_file_url'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== 5. ai_chat_sessions.related_lesson_id (foreign key, then index, then column) =====

set @fk_sql = (
    select if(count(*) > 0,
              'alter table ai_chat_sessions drop foreign key fk_ai_chat_sessions_related_lesson',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'ai_chat_sessions'
      and constraint_name = 'fk_ai_chat_sessions_related_lesson' and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @idx_sql = (
    select if(count(*) > 0,
              'alter table ai_chat_sessions drop index idx_ai_chat_sessions_related_lesson_id',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'ai_chat_sessions'
      and index_name = 'idx_ai_chat_sessions_related_lesson_id'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table ai_chat_sessions drop column related_lesson_id', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'ai_chat_sessions' and column_name = 'related_lesson_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== 6. unused FULLTEXT indexes =====

set @idx_sql = (
    select if(count(*) > 0, 'alter table lessons drop index ft_lessons_title_summary_content', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'lessons'
      and index_name = 'ft_lessons_title_summary_content'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table course_packages drop index ft_course_packages_title_description', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'course_packages'
      and index_name = 'ft_course_packages_title_description'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) > 0, 'alter table flashcards drop index ft_flashcards_learning_text', 'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'flashcards'
      and index_name = 'ft_flashcards_learning_text'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;
