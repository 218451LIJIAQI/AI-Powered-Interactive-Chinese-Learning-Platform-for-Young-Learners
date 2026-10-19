-- =====================================================================
-- V5 - Learner experience, practice submissions and integrity
--      (MySQL 8.4).
--
-- Decisions D7/D7a, D8, D12, D25, D26 and audit A4 §5:
--
--   1. practice_submissions (pronunciation audio / handwriting image kept
--      for teacher review, with the AI feedback status/text) and
--      teacher_feedback.practice_submission_id;
--   2. quiz hints (quiz_questions.hint), hint usage per answer
--      (quiz_attempt_answers.hint_used), the chosen option for option-based
--      scoring (quiz_attempt_answers.selected_option_id) and the wider
--      explanation column (1000 -> 2000, matching the DTO limit);
--   3. story_questions.correct_option_index (answer key kept server side)
--      and lesson_progress.content_viewed_at (lessons without quizzes
--      complete from content + recorded videos);
--   4. live class re-enrolment: the over-strict unique (live_class_id,
--      student_id) is dropped; uk_live_class_enrolments_active_student on
--      the generated active_enrolment_key still blocks duplicate ACTIVE
--      enrolments;
--   5. integrity: FK student_quiz_progress.quiz_id -> quizzes,
--      FK ai_chat_sessions.related_lesson_id -> lessons,
--      UNIQUE (quiz_attempt_id, question_id) on quiz_attempt_answers,
--      the orphan student_progress table is dropped, core ownership FKs
--      become NOT NULL where no NULL rows exist;
--   6. charset: the database default and every application table are
--      declared utf8mb4 / utf8mb4_0900_ai_ci explicitly (no-op on a
--      database that already uses them).
--
-- Every DDL step is guarded through information_schema so a partially
-- applied database does not block the migration. V1-V4 are never edited
-- (Flyway checksum stability).
-- =====================================================================

-- ===== Step 1: database default character set =====

-- Idempotent: a no-op when the schema already defaults to utf8mb4_0900_ai_ci.
alter database character set utf8mb4 collate utf8mb4_0900_ai_ci;

-- ===== Step 2: practice submissions (D7a) =====

create table if not exists practice_submissions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    lesson_id bigint null,
    submission_type enum ('HANDWRITING_IMAGE','PRONUNCIATION_AUDIO') not null,
    uploaded_file_id bigint not null,
    target_text varchar(200) null,
    ai_feedback_status varchar(30) not null,
    ai_feedback_text LONGTEXT null,
    review_status enum ('REVIEWED','SUBMITTED') not null,
    submitted_at datetime(6) not null,
    reviewed_at datetime(6) null,
    primary key (id),
    key idx_practice_submissions_student_submitted (student_id, submitted_at),
    key idx_practice_submissions_lesson_id (lesson_id),
    key idx_practice_submissions_uploaded_file_id (uploaded_file_id),
    key idx_practice_submissions_review_status (review_status, submitted_at),
    constraint fk_practice_submissions_student foreign key (student_id) references student_profiles (id),
    constraint fk_practice_submissions_lesson foreign key (lesson_id) references lessons (id),
    constraint fk_practice_submissions_uploaded_file foreign key (uploaded_file_id) references uploaded_files (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

set @chk_sql = (
    select if(count(*) > 0,
              'alter table practice_submissions drop check chk_practice_submissions_ai_feedback_status',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'practice_submissions'
      and constraint_name = 'chk_practice_submissions_ai_feedback_status'
      and constraint_type = 'CHECK'
);
prepare chk_stmt from @chk_sql;
execute chk_stmt;
deallocate prepare chk_stmt;

alter table practice_submissions
    add constraint chk_practice_submissions_ai_feedback_status check (
        ai_feedback_status in ('AVAILABLE', 'UNAVAILABLE', 'FAILED'));

set @chk_sql = (
    select if(count(*) = 0,
              'alter table practice_submissions add constraint chk_practice_submissions_review_state check ((review_status = ''SUBMITTED'' and reviewed_at is null) or (review_status = ''REVIEWED'' and reviewed_at is not null))',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'practice_submissions'
      and constraint_name = 'chk_practice_submissions_review_state'
);
prepare chk_stmt from @chk_sql;
execute chk_stmt;
deallocate prepare chk_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table teacher_feedback add column practice_submission_id bigint null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'teacher_feedback' and column_name = 'practice_submission_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_teacher_feedback_practice_submission_id on teacher_feedback (practice_submission_id)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'teacher_feedback'
      and index_name = 'idx_teacher_feedback_practice_submission_id'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @fk_sql = (
    select if(count(*) = 0,
              'alter table teacher_feedback add constraint fk_teacher_feedback_practice_submission foreign key (practice_submission_id) references practice_submissions (id)',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'teacher_feedback'
      and constraint_name = 'fk_teacher_feedback_practice_submission'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

-- ===== Step 3: quiz hints, hint usage, option-id scoring, explanation length =====

set @col_sql = (
    select if(count(*) = 0, 'alter table quiz_questions add column hint varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quiz_questions' and column_name = 'hint'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 1 and max(character_maximum_length) < 2000,
              'alter table quiz_questions modify column explanation varchar(2000) null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quiz_questions' and column_name = 'explanation'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table quiz_attempt_answers add column hint_used bit not null default 0', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quiz_attempt_answers' and column_name = 'hint_used'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table quiz_attempt_answers add column selected_option_id bigint null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quiz_attempt_answers' and column_name = 'selected_option_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_quiz_attempt_answers_selected_option_id on quiz_attempt_answers (selected_option_id)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'quiz_attempt_answers'
      and index_name = 'idx_quiz_attempt_answers_selected_option_id'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @fk_sql = (
    select if(count(*) = 0,
              'alter table quiz_attempt_answers add constraint fk_quiz_attempt_answers_selected_option foreign key (selected_option_id) references quiz_options (id)',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'quiz_attempt_answers'
      and constraint_name = 'fk_quiz_attempt_answers_selected_option'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

-- ===== Step 4: activity answer key and lesson content views =====

set @col_sql = (
    select if(count(*) = 0, 'alter table story_questions add column correct_option_index integer null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'story_questions' and column_name = 'correct_option_index'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table lesson_progress add column content_viewed_at datetime(6) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'lesson_progress' and column_name = 'content_viewed_at'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== Step 5: live class re-enrolment (D26) =====
-- The generated active_enrolment_key (ENROLLED/ATTENDED only) keeps its unique key
-- uk_live_class_enrolments_active_student; idx_live_class_enrolments_live_class_id keeps
-- backing the live_class_id foreign key.

set @idx_sql = (
    select if(count(*) > 0,
              'alter table live_class_enrolments drop index uk_live_class_enrolments_live_class_student',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'live_class_enrolments'
      and index_name = 'uk_live_class_enrolments_live_class_student'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

-- ===== Step 6: integrity (audit A4 §5) =====

-- 6a. One answer per question in an attempt (SDD Table 4.5).
-- Existing duplicates (impossible through the service) make the statement below fail loudly.
set @uk_sql = (
    select if(count(*) = 0,
              'alter table quiz_attempt_answers add constraint uk_quiz_attempt_answers_attempt_question unique (quiz_attempt_id, question_id)',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'quiz_attempt_answers'
      and constraint_name = 'uk_quiz_attempt_answers_attempt_question'
);
prepare uk_stmt from @uk_sql;
execute uk_stmt;
deallocate prepare uk_stmt;

-- 6b. student_quiz_progress.quiz_id -> quizzes (quizzes are archived, never hard-deleted).
-- Orphan rows (impossible through the service) make the foreign key below fail loudly.
set @fk_sql = (
    select if(count(*) = 0,
              'alter table student_quiz_progress add constraint fk_student_quiz_progress_quiz foreign key (quiz_id) references quizzes (id)',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'student_quiz_progress'
      and constraint_name = 'fk_student_quiz_progress_quiz'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

-- 6c. ai_chat_sessions.related_lesson_id -> lessons (unknown ids were free text context; cleared).
update ai_chat_sessions s
set s.related_lesson_id = null
where s.related_lesson_id is not null
  and not exists (select 1 from lessons l where l.id = s.related_lesson_id);

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_ai_chat_sessions_related_lesson_id on ai_chat_sessions (related_lesson_id)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'ai_chat_sessions'
      and index_name = 'idx_ai_chat_sessions_related_lesson_id'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @fk_sql = (
    select if(count(*) = 0,
              'alter table ai_chat_sessions add constraint fk_ai_chat_sessions_related_lesson foreign key (related_lesson_id) references lessons (id)',
              'select 1')
    from information_schema.table_constraints
    where table_schema = database() and table_name = 'ai_chat_sessions'
      and constraint_name = 'fk_ai_chat_sessions_related_lesson'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

-- 6d. Orphan table: student_progress has no entity and no writer (dropping it also drops its FKs).
drop table if exists student_progress;

-- 6e. Core ownership foreign keys become NOT NULL where the data allows it. A column that still
--     holds NULL rows is left nullable (the application always sets it; see the SDD notes).
--     MySQL rejects MODIFY on a column used by a foreign key while foreign_key_checks is on
--     (error 1832), even when only the nullability changes. Every statement below first proves
--     that no NULL rows exist, so the checks are suspended for this block only and restored after.
set @saved_foreign_key_checks = @@foreign_key_checks;
set foreign_key_checks = 0;

set @col_sql = (
    select if((select count(*) from lessons where teacher_id is null) = 0 and max(is_nullable) = 'YES',
              'alter table lessons modify column teacher_id bigint not null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'lessons' and column_name = 'teacher_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if((select count(*) from learning_modules where teacher_id is null) = 0 and max(is_nullable) = 'YES',
              'alter table learning_modules modify column teacher_id bigint not null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'learning_modules' and column_name = 'teacher_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if((select count(*) from course_packages where teacher_id is null) = 0 and max(is_nullable) = 'YES',
              'alter table course_packages modify column teacher_id bigint not null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'course_packages' and column_name = 'teacher_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if((select count(*) from quizzes where teacher_id is null) = 0 and max(is_nullable) = 'YES',
              'alter table quizzes modify column teacher_id bigint not null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quizzes' and column_name = 'teacher_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if((select count(*) from quizzes where lesson_id is null) = 0 and max(is_nullable) = 'YES',
              'alter table quizzes modify column lesson_id bigint not null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quizzes' and column_name = 'lesson_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if((select count(*) from live_classes where teacher_id is null) = 0 and max(is_nullable) = 'YES',
              'alter table live_classes modify column teacher_id bigint not null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'live_classes' and column_name = 'teacher_id'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set foreign_key_checks = @saved_foreign_key_checks;

-- ===== Step 7: explicit utf8mb4 / utf8mb4_0900_ai_ci on every application table =====
-- Each table is converted only when its collation differs, so a database created by the
-- project docker-compose file (utf8mb4_0900_ai_ci server default) is left untouched.

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table ai_chat_messages convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'ai_chat_messages'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table ai_chat_sessions convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'ai_chat_sessions'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table ai_feedback_records convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'ai_feedback_records'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table ai_response_reports convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'ai_response_reports'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table ai_usage_logs convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'ai_usage_logs'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table ai_usage_quota_counters convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'ai_usage_quota_counters'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table audit_logs convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'audit_logs'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table badges convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'badges'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table content_moderation_records convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'content_moderation_records'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table course_packages convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'course_packages'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table daily_streaks convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'daily_streaks'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table drag_drop_activities convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'drag_drop_activities'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table drag_drop_items convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'drag_drop_items'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table flashcards convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'flashcards'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table learning_categories convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'learning_categories'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table learning_levels convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'learning_levels'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table learning_modules convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'learning_modules'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table lesson_progress convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'lesson_progress'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table lessons convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'lessons'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table listening_exercises convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'listening_exercises'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table live_class_enrolments convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'live_class_enrolments'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table live_classes convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'live_classes'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table matching_game_items convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'matching_game_items'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table matching_games convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'matching_games'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table notifications convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'notifications'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table package_items convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'package_items'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table password_reset_tokens convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'password_reset_tokens'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table points_transactions convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'points_transactions'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table practice_submissions convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'practice_submissions'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table quiz_attempt_answers convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'quiz_attempt_answers'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table quiz_attempts convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'quiz_attempts'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table quiz_options convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'quiz_options'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table quiz_questions convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'quiz_questions'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table quizzes convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'quizzes'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table recorded_lesson_videos convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'recorded_lesson_videos'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table refresh_tokens convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'refresh_tokens'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table roles convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'roles'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table story_activities convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'story_activities'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table story_questions convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'story_questions'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table student_badges convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'student_badges'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table student_profiles convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'student_profiles'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table student_quiz_progress convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'student_quiz_progress'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table system_settings convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'system_settings'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table teacher_feedback convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'teacher_feedback'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table teacher_profiles convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'teacher_profiles'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table teacher_verification_documents convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'teacher_verification_documents'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table teacher_verification_reviews convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'teacher_verification_reviews'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table tracing_exercises convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'tracing_exercises'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table uploaded_files convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'uploaded_files'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table user_roles convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'user_roles'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table users convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'users'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;

set @charset_sql = (
    select if(count(*) = 1 and max(table_collation) <> 'utf8mb4_0900_ai_ci',
              'alter table video_progress convert to character set utf8mb4 collate utf8mb4_0900_ai_ci',
              'select 1')
    from information_schema.tables
    where table_schema = database() and table_name = 'video_progress'
);
prepare charset_stmt from @charset_sql;
execute charset_stmt;
deallocate prepare charset_stmt;
