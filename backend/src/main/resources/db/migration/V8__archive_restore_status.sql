-- =====================================================================
-- V8 - Status before archive (MySQL 8.4).
--
-- Teachers can archive their own courses, modules, lessons and quizzes
-- while they are DRAFT, PUBLISHED or REJECTED, but an admin restore always
-- published the item again. A draft that was never reviewed could
-- therefore reach students. Each of the four tables gets a nullable
-- status_before_archive column, so a restore returns the item to the
-- status it had: published content is published again, and a draft or
-- rejected item goes back to its teacher.
--
-- Rows archived before this migration keep a NULL value and are restored
-- to PUBLISHED, as before. Every step is guarded through
-- information_schema so the script can be run again on a partially
-- migrated database. V1-V7 are never edited.
-- =====================================================================

set @col_sql = (
    select if(count(*) = 0,
              'alter table course_packages add column status_before_archive enum (''APPROVED'',''ARCHIVED'',''DRAFT'',''PUBLISHED'',''REJECTED'',''SUBMITTED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'course_packages' and column_name = 'status_before_archive'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table learning_modules add column status_before_archive enum (''APPROVED'',''ARCHIVED'',''DRAFT'',''PUBLISHED'',''REJECTED'',''SUBMITTED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'learning_modules' and column_name = 'status_before_archive'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table lessons add column status_before_archive enum (''APPROVED'',''ARCHIVED'',''DRAFT'',''PUBLISHED'',''REJECTED'',''SUBMITTED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'lessons' and column_name = 'status_before_archive'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0,
              'alter table quizzes add column status_before_archive enum (''APPROVED'',''ARCHIVED'',''DRAFT'',''PUBLISHED'',''REJECTED'',''SUBMITTED'') null',
              'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quizzes' and column_name = 'status_before_archive'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;
