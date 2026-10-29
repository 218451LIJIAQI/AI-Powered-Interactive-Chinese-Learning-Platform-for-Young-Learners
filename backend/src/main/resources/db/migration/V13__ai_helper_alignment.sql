-- =====================================================================
-- V13 - AI Helper alignment with the low-fidelity prototype (MySQL 8.4).
--
--   1. ai_practice_checks: pronunciation / handwriting practice scores that
--      SparkBot gives a student (only the student sees them). Only the
--      result is kept: type, target text, optional lesson, score,
--      sub-scores and feedback text. The recording or photo is never stored.
--   2. The teacher/admin review of stored practice uploads is removed:
--      teacher_feedback loses practice_submission_id (its feedback rows stay
--      and read as lesson / quiz / general feedback), practice_submissions and
--      the ai_feedback_records log are dropped, and the uploaded_files rows of
--      those recordings and photos are deleted. (Their files under
--      <upload dir>/ai-feedback are removed by LegacyPracticeUploadPurger at
--      start-up, since SQL cannot reach the disk.)
--   3. feature_key becomes an enum of AiFeatureKey in ai_usage_logs,
--      ai_response_reports and ai_chat_sessions. Retired keys are mapped:
--      the vocabulary helper and sentence practice became Ask SparkBot quick
--      starts (CHAT_TUTOR), pronunciation and writing feedback became
--      PRACTICE_CHECK, and teachers' quiz/story requests are TEACHER_DRAFT.
--   4. ai_response_reports: the never-set ai_usage_log_id link is dropped;
--      an admin resolves or dismisses a report, so
--      IN_REVIEW rows become open again (PENDING_REVIEW, without the interim
--      reviewer fields); reasons gain UNCLEAR_EXPLANATION and
--      SCORE_SEEMS_WRONG, HALLUCINATION reads as INCORRECT_CONTENT and
--      UNSAFE_CONTENT as INAPPROPRIATE_CONTENT ("unsafe or inappropriate").
--   5. The read-only platform_name setting is removed: nothing shows it (the
--      MandarinSpark name lives in the interface).
--
-- V1-V12 are never edited (Flyway checksum stability).
-- =====================================================================

-- ===== 1. ai_practice_checks =====

create table ai_practice_checks (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_user_id bigint not null,
    lesson_id bigint null,
    check_type enum ('HANDWRITING','RECORDING') not null,
    target_text varchar(100) not null,
    score integer not null,
    tones_score integer null,
    clarity_score integer null,
    pace_score integer null,
    headline varchar(200) not null,
    feedback varchar(1000) null,
    tips varchar(2000) null,
    primary key (id),
    key idx_ai_practice_checks_student_created (student_user_id, created_at),
    key idx_ai_practice_checks_lesson_id (lesson_id),
    constraint fk_ai_practice_checks_student_user foreign key (student_user_id) references users (id),
    constraint fk_ai_practice_checks_lesson foreign key (lesson_id) references lessons (id),
    constraint chk_ai_practice_checks_scores check (
        score between 0 and 100
        and (tones_score is null or tones_score between 0 and 100)
        and (clarity_score is null or clarity_score between 0 and 100)
        and (pace_score is null or pace_score between 0 and 100))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- ===== 2. practice submissions and the AI feedback log =====

alter table teacher_feedback drop foreign key fk_teacher_feedback_practice_submission;
alter table teacher_feedback drop index idx_teacher_feedback_practice_submission_id;
alter table teacher_feedback drop column practice_submission_id;

create temporary table v13_practice_upload_ids (id bigint not null primary key);
insert ignore into v13_practice_upload_ids (id)
    select uploaded_file_id from practice_submissions;
insert ignore into v13_practice_upload_ids (id)
    select uploaded_file_id from ai_feedback_records where uploaded_file_id is not null;

drop table practice_submissions;
drop table ai_feedback_records;

delete from uploaded_files where id in (select id from v13_practice_upload_ids);
drop temporary table v13_practice_upload_ids;

-- ===== 3. feature_key as an AiFeatureKey enum =====

update ai_usage_logs set feature_key = 'CHAT_TUTOR'
where feature_key in ('VOCABULARY_HELPER', 'SENTENCE_PRACTICE');
update ai_usage_logs set feature_key = 'PRACTICE_CHECK'
where feature_key in ('PRONUNCIATION_FEEDBACK', 'WRITING_FEEDBACK');
update ai_usage_logs set feature_key = 'TEACHER_DRAFT'
where user_role = 'TEACHER' and feature_key in ('QUIZ_GENERATOR', 'STORY_GENERATOR');
update ai_usage_logs set feature_key = 'CHAT_TUTOR'
where feature_key not in ('CHAT_TUTOR', 'XIAOMEI_CONVERSATION', 'QUIZ_GENERATOR', 'STORY_GENERATOR',
                          'PRACTICE_CHECK', 'TEACHER_DRAFT', 'LEARNING_PLAN');

alter table ai_usage_logs
    modify column feature_key enum ('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') not null;

update ai_response_reports set feature_key = 'CHAT_TUTOR'
where feature_key in ('VOCABULARY_HELPER', 'SENTENCE_PRACTICE');
update ai_response_reports set feature_key = 'PRACTICE_CHECK'
where feature_key in ('PRONUNCIATION_FEEDBACK', 'WRITING_FEEDBACK');
update ai_response_reports set feature_key = 'CHAT_TUTOR'
where feature_key not in ('CHAT_TUTOR', 'XIAOMEI_CONVERSATION', 'QUIZ_GENERATOR', 'STORY_GENERATOR',
                          'PRACTICE_CHECK', 'TEACHER_DRAFT', 'LEARNING_PLAN');

alter table ai_response_reports
    modify column feature_key enum ('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') not null;

update ai_chat_sessions set feature_key = 'CHAT_TUTOR'
where feature_key not in ('CHAT_TUTOR', 'XIAOMEI_CONVERSATION', 'QUIZ_GENERATOR', 'STORY_GENERATOR',
                          'PRACTICE_CHECK', 'TEACHER_DRAFT', 'LEARNING_PLAN');

alter table ai_chat_sessions
    modify column feature_key enum ('CHAT_TUTOR','LEARNING_PLAN','PRACTICE_CHECK','QUIZ_GENERATOR','STORY_GENERATOR','TEACHER_DRAFT','XIAOMEI_CONVERSATION') not null;

-- ===== 4. ai_response_reports: link, statuses and reasons =====

-- Nothing ever set ai_usage_log_id (the usage log keeps no text to attach); reports carry their own snapshots.
alter table ai_response_reports drop foreign key fk_ai_response_reports_usage_log_id;
alter table ai_response_reports drop index idx_ai_response_reports_usage_log_id;
alter table ai_response_reports drop column ai_usage_log_id;

alter table ai_response_reports drop check chk_ai_response_reports_status;
alter table ai_response_reports drop check chk_ai_response_reports_review_consistency;
alter table ai_response_reports drop check chk_ai_response_reports_reason_category;

update ai_response_reports
set status = 'PENDING_REVIEW',
    reviewer_user_id = null,
    reviewer_note = null,
    reviewed_at = null
where status = 'IN_REVIEW';

update ai_response_reports set reason_category = 'INCORRECT_CONTENT' where reason_category = 'HALLUCINATION';
update ai_response_reports set reason_category = 'INAPPROPRIATE_CONTENT' where reason_category = 'UNSAFE_CONTENT';

alter table ai_response_reports
    add constraint chk_ai_response_reports_status check (status in ('PENDING_REVIEW', 'RESOLVED', 'DISMISSED')),
    add constraint chk_ai_response_reports_reason_category check (reason_category in (
        'INCORRECT_CONTENT', 'UNCLEAR_EXPLANATION', 'INAPPROPRIATE_CONTENT', 'SCORE_SEEMS_WRONG', 'OTHER')),
    add constraint chk_ai_response_reports_review_consistency check (
        (status = 'PENDING_REVIEW' and reviewed_at is null)
        or (status in ('RESOLVED', 'DISMISSED') and reviewed_at is not null));

-- ===== 5. platform settings =====

delete from system_settings where setting_key = 'platform_name';
