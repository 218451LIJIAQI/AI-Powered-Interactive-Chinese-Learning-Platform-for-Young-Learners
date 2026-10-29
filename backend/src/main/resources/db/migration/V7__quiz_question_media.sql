-- =====================================================================
-- V7 - Quiz question media (MySQL 8.4).
--
-- SDD 5.4 lists "word-image matching" and "listen-and-choose" among the
-- quiz question types, but a question could only carry text, so the
-- learner had nothing to look at or listen to. Two optional links are
-- added to quiz_questions:
--
--   1. audio_url - the recording a LISTEN_CHOOSE question plays;
--   2. image_url - the picture a MATCH_WORD_IMAGE question shows.
--
-- Both columns are nullable, so existing questions are unchanged. Every
-- step is guarded through information_schema so the script can be run
-- again on a partially migrated database. V1-V6 are never edited.
-- =====================================================================

set @col_sql = (
    select if(count(*) = 0, 'alter table quiz_questions add column audio_url varchar(500) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quiz_questions' and column_name = 'audio_url'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) = 0, 'alter table quiz_questions add column image_url varchar(500) null', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'quiz_questions' and column_name = 'image_url'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;
