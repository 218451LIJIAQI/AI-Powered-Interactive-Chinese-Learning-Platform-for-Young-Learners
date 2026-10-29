-- =====================================================================
-- V4 - Content review workflow, teacher-authored activities and
--      learning stages (MySQL 8.4).
--
-- Decisions D4, D5, D14, D23 and D24:
--
--   1. add the optional learning_stage (GUIDED, SCAFFOLDED, INDEPENDENT)
--      to quizzes and the six practice-activity tables;
--   2. add rejection_reason to quizzes and the six practice-activity tables
--      so an admin rejection reason is visible to the authoring teacher
--      (courses, modules and lessons already have the column);
--   3. status data: APPROVED is no longer a resting state. The old code
--      used it as "hidden by moderation", so every APPROVED row becomes
--      ARCHIVED (an admin can restore it to PUBLISHED). The enum value is
--      kept in the column definitions for compatibility but is never
--      assigned by the application. Published quizzes without a single
--      question go back to DRAFT so they are not shown to students;
--   4. composite (content_status, updated_at) indexes for the admin
--      review queue on all ten reviewable tables.
--
-- Every DDL step is guarded through information_schema so a partially
-- applied database does not block the migration. V1-V3 are never edited
-- (Flyway checksum stability).
-- =====================================================================

-- ===== Step 1: learning_stage columns =====

set @col_sql = (
    select if(count(*) = 0,
              'alter table quizzes add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quizzes' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table flashcards add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'flashcards' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table matching_games add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'matching_games' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table listening_exercises add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'listening_exercises' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table tracing_exercises add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table drag_drop_activities add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table story_activities add column learning_stage enum (''GUIDED'',''INDEPENDENT'',''SCAFFOLDED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'story_activities' and column_name = 'learning_stage'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== Step 2: rejection_reason columns =====

set @col_sql = (
    select if(count(*) = 0, 'alter table quizzes add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quizzes' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table flashcards add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'flashcards' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table matching_games add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'matching_games' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table listening_exercises add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'listening_exercises' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table tracing_exercises add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table drag_drop_activities add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table story_activities add column rejection_reason varchar(1000) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'story_activities' and column_name = 'rejection_reason'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== Step 3: status data =====

update course_packages set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update learning_modules set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update lessons set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update quizzes set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update flashcards set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update matching_games set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update listening_exercises set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update tracing_exercises set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update drag_drop_activities set content_status = 'ARCHIVED' where content_status = 'APPROVED';
update story_activities set content_status = 'ARCHIVED' where content_status = 'APPROVED';

update quizzes
set content_status = 'DRAFT'
where content_status = 'PUBLISHED'
  and not exists (select 1 from quiz_questions qq where qq.quiz_id = quizzes.id);

-- ===== Step 4: review-queue indexes (content_status, updated_at) =====

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_course_packages_status_updated on course_packages (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'course_packages' and index_name = 'idx_course_packages_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_learning_modules_status_updated on learning_modules (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'learning_modules' and index_name = 'idx_learning_modules_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_lessons_status_updated on lessons (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'lessons' and index_name = 'idx_lessons_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_quizzes_status_updated on quizzes (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'quizzes' and index_name = 'idx_quizzes_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_flashcards_status_updated on flashcards (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_matching_games_status_updated on matching_games (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_listening_exercises_status_updated on listening_exercises (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_tracing_exercises_status_updated on tracing_exercises (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_drag_drop_activities_status_updated on drag_drop_activities (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;

set @idx_sql = (
    select if(count(*) = 0,
              'create index idx_story_activities_status_updated on story_activities (content_status, updated_at)',
              'select 1')
    from information_schema.statistics
    where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_status_updated'
);
prepare idx_stmt from @idx_sql;
execute idx_stmt;
deallocate prepare idx_stmt;
