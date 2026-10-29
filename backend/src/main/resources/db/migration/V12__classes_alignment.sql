-- =====================================================================
-- V12 - Teacher-made classes (MySQL 8.4).
--
-- A class is a course_packages row; its units are learning_modules rows linked through package_items (MODULE);
-- a unit has ordered lessons. This migration adds what the prototype's class flows need:
--
--   1. Levels become "Beginner 1" (HSK 1) and "Beginner 2" (HSK 2); rows that used "Advanced" move to Beginner 2.
--   2. Categories become the class topics (Pinyin, Tones, Vocabulary, Characters, Listening, Speaking, Sentences,
--      Numbers, Culture); every class, unit and lesson keeps a topic through the mapping below.
--   3. Legacy courses that listed lessons directly get one unit holding those lessons; units are no longer
--      reviewed on their own, so every unit of a class is active (PUBLISHED) unless it was archived.
--   4. class_enrolments: a student joins a class (every class is free).
--   5. course_packages: a note to students, review timestamps and a pending revision of the class details
--      (students keep the approved details until an admin approves the revision).
--   6. lessons: position inside the unit, review timestamp and revision_of_lesson_id (a working copy of a
--      published lesson; students keep the published lesson until the revision is approved).
--   7. lesson_key_phrases: Chinese, pinyin and meaning of each key phrase of a lesson.
--   8. flashcards, tracing_exercises, matching_games: lesson_id (an activity of a class lesson, not a practice
--      set), lesson_position and revision_source_id. Standalone practice activities written by teachers have no
--      place in the product any more (the practice library is made by the MandarinSpark team): they are archived.
--   9. quizzes and recorded_lesson_videos: revision_source_id; quiz_attempts.expires_at (timed attempts that save
--      answers as the student goes).
--  10. class_activity_completions: a student finished an activity of a class lesson.
--  11. live_classes: recording link, the start it was moved from and when it was cancelled.
--  12. teacher_feedback: the class it belongs to and when it was last edited.
--  13. class_content_events: the history of a class and its lessons (created, submitted, approved, changes
--      requested with the reason, archived, restored).
-- =====================================================================

-- ===== 1. Levels =====

update learning_levels set name = 'Beginner 1', description = 'Beginner 1 (HSK 1)', sort_order = 1
where name = 'Beginner';

update learning_levels set name = 'Beginner 2', description = 'Beginner 2 (HSK 2)', sort_order = 2
where name = 'Intermediate';

update course_packages t
    join learning_levels a on a.id = t.level_id and a.name = 'Advanced'
    join learning_levels b on b.name = 'Beginner 2'
set t.level_id = b.id;

update learning_modules t
    join learning_levels a on a.id = t.level_id and a.name = 'Advanced'
    join learning_levels b on b.name = 'Beginner 2'
set t.level_id = b.id;

update lessons t
    join learning_levels a on a.id = t.level_id and a.name = 'Advanced'
    join learning_levels b on b.name = 'Beginner 2'
set t.level_id = b.id;

update student_profiles t
    join learning_levels a on a.id = t.current_level_id and a.name = 'Advanced'
    join learning_levels b on b.name = 'Beginner 2'
set t.current_level_id = b.id;

delete l from learning_levels l
where l.name = 'Advanced'
  and not exists (select 1 from course_packages t where t.level_id = l.id)
  and not exists (select 1 from learning_modules t where t.level_id = l.id)
  and not exists (select 1 from lessons t where t.level_id = l.id)
  and not exists (select 1 from student_profiles t where t.current_level_id = l.id);

-- ===== 2. Categories become class topics =====

create temporary table v12_topic_map (
    old_name varchar(120) not null,
    new_name varchar(120) not null
);

insert into v12_topic_map (old_name, new_name) values
    ('Chinese Characters', 'Characters'),
    ('Writing / Tracing', 'Characters'),
    ('Reading', 'Sentences'),
    ('Greetings', 'Sentences'),
    ('Daily Conversation', 'Speaking'),
    ('Self-introduction', 'Speaking'),
    ('Travel', 'Speaking'),
    ('Work & Daily Life', 'Speaking'),
    ('University Life', 'Vocabulary'),
    ('Food', 'Vocabulary');

-- Only when the database already has categories: an empty table is filled by the application seeder.
insert into learning_categories (name, description, sort_order, created_at, updated_at)
select topic.name, concat(topic.name, ' learning category.'), topic.sort_order, now(6), now(6)
from (
    select 'Pinyin' as name, 1 as sort_order
    union all select 'Tones', 2
    union all select 'Vocabulary', 3
    union all select 'Characters', 4
    union all select 'Listening', 5
    union all select 'Speaking', 6
    union all select 'Sentences', 7
    union all select 'Numbers', 8
    union all select 'Culture', 9
) topic
where exists (select 1 from learning_categories existing)
  and not exists (select 1 from learning_categories c where c.name = topic.name);

update course_packages t
    join learning_categories old_topic on old_topic.id = t.category_id
    join v12_topic_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = m.new_name
set t.category_id = new_topic.id;

update learning_modules t
    join learning_categories old_topic on old_topic.id = t.category_id
    join v12_topic_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = m.new_name
set t.category_id = new_topic.id;

update lessons t
    join learning_categories old_topic on old_topic.id = t.category_id
    join v12_topic_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = m.new_name
set t.category_id = new_topic.id;

delete c from learning_categories c
    join v12_topic_map m on m.old_name = c.name;

update learning_categories set sort_order = 1 where name = 'Pinyin';
update learning_categories set sort_order = 2 where name = 'Tones';
update learning_categories set sort_order = 3 where name = 'Vocabulary';
update learning_categories set sort_order = 4 where name = 'Characters';
update learning_categories set sort_order = 5 where name = 'Listening';
update learning_categories set sort_order = 6 where name = 'Speaking';
update learning_categories set sort_order = 7 where name = 'Sentences';
update learning_categories set sort_order = 8 where name = 'Numbers';
update learning_categories set sort_order = 9 where name = 'Culture';

drop temporary table v12_topic_map;

-- ===== 3. Units =====

alter table lessons
    add column unit_position integer not null default 0;

alter table learning_modules
    add column v12_class_id bigint null;

insert into learning_modules (
    title, description, teacher_id, level_id, category_id, content_status, created_at, updated_at, v12_class_id)
select concat('Unit 1: ', cp.title), null, cp.teacher_id, cp.level_id, cp.category_id, 'PUBLISHED', now(6), now(6), cp.id
from course_packages cp
where exists (
    select 1 from package_items pi where pi.course_package_id = cp.id and pi.item_type = 'LESSON');

update lessons l
    join package_items pi on pi.item_type = 'LESSON' and pi.item_id = l.id
    join learning_modules m on m.v12_class_id = pi.course_package_id
set l.module_id = m.id,
    l.unit_position = pi.sort_order;

delete from package_items where item_type = 'LESSON';

insert into package_items (course_package_id, item_type, item_id, sort_order, title_snapshot, created_at, updated_at)
select m.v12_class_id, 'MODULE', m.id, 0, m.title, now(6), now(6)
from learning_modules m
where m.v12_class_id is not null;

alter table learning_modules
    drop column v12_class_id;

update learning_modules m
set m.content_status = 'PUBLISHED',
    m.rejection_reason = null
where m.content_status in ('APPROVED', 'DRAFT', 'REJECTED', 'SUBMITTED');

-- ===== 4. Class enrolments =====

create table class_enrolments (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    course_package_id bigint not null,
    joined_at datetime(6) not null,
    primary key (id),
    constraint uk_class_enrolments_student_class unique (student_id, course_package_id),
    constraint fk_class_enrolments_student foreign key (student_id) references student_profiles (id),
    constraint fk_class_enrolments_class foreign key (course_package_id) references course_packages (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_class_enrolments_class_id on class_enrolments (course_package_id);

-- ===== 5. Class details =====

alter table course_packages
    add column teacher_note varchar(500) null,
    add column submitted_at datetime(6) null,
    add column pending_title varchar(200) null,
    add column pending_description varchar(2000) null,
    add column pending_level_id bigint null,
    add column pending_category_id bigint null,
    add column pending_learning_type enum ('HYBRID','LIVE','RECORDED') null,
    add column revision_status enum ('DRAFT','REJECTED','SUBMITTED') null,
    add column revision_rejection_reason varchar(1000) null,
    add column revision_submitted_at datetime(6) null,
    add constraint fk_course_packages_pending_level foreign key (pending_level_id) references learning_levels (id),
    add constraint fk_course_packages_pending_category foreign key (pending_category_id) references learning_categories (id);

create index idx_course_packages_revision_status on course_packages (revision_status);

update course_packages set submitted_at = updated_at where content_status = 'SUBMITTED';

-- ===== 6. Lessons =====

alter table lessons
    add column submitted_at datetime(6) null,
    add column revision_of_lesson_id bigint null,
    add constraint fk_lessons_revision_of foreign key (revision_of_lesson_id) references lessons (id);

create index idx_lessons_revision_of on lessons (revision_of_lesson_id);
create index idx_lessons_module_position on lessons (module_id, unit_position);

update lessons set submitted_at = updated_at where content_status = 'SUBMITTED';

-- ===== 7. Key phrases =====

create table lesson_key_phrases (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    lesson_id bigint not null,
    chinese varchar(100) not null,
    pinyin varchar(200) not null,
    meaning varchar(300) not null,
    sort_order integer not null,
    primary key (id),
    constraint fk_lesson_key_phrases_lesson foreign key (lesson_id) references lessons (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_lesson_key_phrases_lesson on lesson_key_phrases (lesson_id, sort_order);

-- ===== 8. Lesson activities =====

alter table flashcards
    add column lesson_id bigint null,
    add column lesson_position integer null,
    add column revision_source_id bigint null,
    add constraint fk_flashcards_lesson foreign key (lesson_id) references lessons (id),
    add constraint fk_flashcards_revision_source foreign key (revision_source_id) references flashcards (id);

create index idx_flashcards_lesson on flashcards (lesson_id);

alter table tracing_exercises
    add column lesson_id bigint null,
    add column lesson_position integer null,
    add column revision_source_id bigint null,
    add constraint fk_tracing_exercises_lesson foreign key (lesson_id) references lessons (id),
    add constraint fk_tracing_exercises_revision_source foreign key (revision_source_id) references tracing_exercises (id);

create index idx_tracing_exercises_lesson on tracing_exercises (lesson_id);

alter table matching_games
    add column lesson_id bigint null,
    add column lesson_position integer null,
    add column revision_source_id bigint null,
    add constraint fk_matching_games_lesson foreign key (lesson_id) references lessons (id),
    add constraint fk_matching_games_revision_source foreign key (revision_source_id) references matching_games (id);

create index idx_matching_games_lesson on matching_games (lesson_id);

update flashcards a join teacher_profiles tp on tp.user_id = a.created_by_user_id
set a.content_status = 'ARCHIVED' where a.lesson_id is null and a.content_status <> 'ARCHIVED';
update tracing_exercises a join teacher_profiles tp on tp.user_id = a.created_by_user_id
set a.content_status = 'ARCHIVED' where a.lesson_id is null and a.content_status <> 'ARCHIVED';
update matching_games a join teacher_profiles tp on tp.user_id = a.created_by_user_id
set a.content_status = 'ARCHIVED' where a.lesson_id is null and a.content_status <> 'ARCHIVED';
update listening_exercises a join teacher_profiles tp on tp.user_id = a.created_by_user_id
set a.content_status = 'ARCHIVED' where a.content_status <> 'ARCHIVED';
update drag_drop_activities a join teacher_profiles tp on tp.user_id = a.created_by_user_id
set a.content_status = 'ARCHIVED' where a.content_status <> 'ARCHIVED';
update story_activities a join teacher_profiles tp on tp.user_id = a.created_by_user_id
set a.content_status = 'ARCHIVED' where a.content_status <> 'ARCHIVED';

-- ===== 9. Quizzes, videos and attempts =====

alter table quizzes
    add column revision_source_id bigint null,
    add constraint fk_quizzes_revision_source foreign key (revision_source_id) references quizzes (id);

alter table recorded_lesson_videos
    add column revision_source_id bigint null,
    add constraint fk_recorded_videos_revision_source foreign key (revision_source_id) references recorded_lesson_videos (id);

alter table quiz_attempts
    add column expires_at datetime(6) null;

-- ===== 10. Activity completions =====

create table class_activity_completions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    lesson_id bigint not null,
    activity_type enum ('FLASHCARDS','MATCHING','TRACING') not null,
    activity_id bigint not null,
    completed_at datetime(6) not null,
    times_completed integer not null,
    primary key (id),
    constraint uk_class_activity_completions_student_activity unique (student_id, activity_type, activity_id),
    constraint fk_class_activity_completions_student foreign key (student_id) references student_profiles (id),
    constraint fk_class_activity_completions_lesson foreign key (lesson_id) references lessons (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_class_activity_completions_lesson on class_activity_completions (lesson_id, student_id);

-- ===== 11. Live sessions =====

alter table live_classes
    add column recording_url varchar(500) null,
    add column rescheduled_from_start_at datetime(6) null,
    add column cancelled_at datetime(6) null;

update live_classes set cancelled_at = updated_at where live_class_status = 'CANCELLED';

-- ===== 12. Teacher feedback =====

alter table teacher_feedback
    add column course_package_id bigint null,
    add column edited_at datetime(6) null,
    add constraint fk_teacher_feedback_class foreign key (course_package_id) references course_packages (id);

create index idx_teacher_feedback_class on teacher_feedback (course_package_id);

update teacher_feedback f
    join quiz_attempts qa on qa.id = f.quiz_attempt_id
    join quizzes q on q.id = qa.quiz_id
set f.lesson_id = q.lesson_id
where f.lesson_id is null;

update teacher_feedback f
    join lessons l on l.id = f.lesson_id
    join package_items pi on pi.item_type = 'MODULE' and pi.item_id = l.module_id
set f.course_package_id = pi.course_package_id
where f.course_package_id is null;

-- ===== 13. Class history =====

create table class_content_events (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    course_package_id bigint not null,
    lesson_id bigint null,
    actor_user_id bigint null,
    event_type enum (
        'CLASS_APPROVED',
        'CLASS_ARCHIVED',
        'CLASS_CHANGES_REQUESTED',
        'CLASS_CREATED',
        'CLASS_DETAILS_APPROVED',
        'CLASS_DETAILS_CHANGES_REQUESTED',
        'CLASS_DETAILS_SUBMITTED',
        'CLASS_RESTORED',
        'CLASS_SUBMITTED',
        'LESSON_APPROVED',
        'LESSON_ARCHIVED',
        'LESSON_CHANGES_REQUESTED',
        'LESSON_CREATED',
        'LESSON_RESTORED',
        'LESSON_SUBMITTED',
        'REVISION_APPROVED',
        'REVISION_CHANGES_REQUESTED',
        'REVISION_SUBMITTED') not null,
    subject_title varchar(200) not null,
    note varchar(1000) null,
    changes LONGTEXT null,
    primary key (id),
    constraint fk_class_content_events_class foreign key (course_package_id) references course_packages (id),
    constraint fk_class_content_events_lesson foreign key (lesson_id) references lessons (id),
    constraint fk_class_content_events_actor foreign key (actor_user_id) references users (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_class_content_events_class on class_content_events (course_package_id, created_at);
create index idx_class_content_events_lesson on class_content_events (lesson_id, created_at);
