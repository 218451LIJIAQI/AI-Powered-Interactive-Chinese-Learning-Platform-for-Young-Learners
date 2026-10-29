-- V11: the Learn path, Practice library, Mistakes and the XP / daily goal / streak / badge rules.
--
--   1. student_profiles.daily_goal_xp: the daily goal (10, 20 or 30 XP, default 20) that keeps the streak.
--   2. The platform's own Learn path: units, guidebook phrases, stops (lesson / bonus / review / checkpoint),
--      questions and per-student completions. The content itself is platform content filled by
--      PlatformContentSeeder when the tables are empty (every profile), not by this migration.
--   3. learning_mistakes: wrong answers from Learn and Practice waiting for the Mistakes review.
--   4. The level check question bank (admin-owned) used to place new students.
--   5. Practice library: the seventh activity type (quizzes), flashcard sets with several cards, sentence-building
--      distractor tiles, stroke counts for tracing, and practice_completions (one row per finished run).
--   6. Badges: "Point Starter" becomes "XP Starter" so earned badges carry over; DataSeeder aligns the
--      descriptions and adds the new badges (10-day Streak, Unit Champion, 300 XP).

-- ===== 1. Daily goal =====

alter table student_profiles
    add column daily_goal_xp integer not null default 20;

alter table student_profiles
    add constraint chk_student_profiles_daily_goal_xp check (daily_goal_xp in (10, 20, 30));

-- ===== 2. Learn path =====

create table learn_units (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    position integer not null,
    path_level integer not null,
    title varchar(120) not null,
    title_zh varchar(40) not null,
    hsk_level integer not null,
    icon_key varchar(40) not null,
    publish_status enum ('DRAFT','PUBLISHED') not null,
    guidebook_tips TEXT null,
    primary key (id),
    constraint uk_learn_units_position unique (position),
    key idx_learn_units_status (publish_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table learn_unit_phrases (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unit_id bigint not null,
    position integer not null,
    hanzi varchar(100) not null,
    pinyin varchar(200) not null,
    meaning varchar(200) not null,
    primary key (id),
    key idx_learn_unit_phrases_unit (unit_id, position),
    constraint fk_learn_unit_phrases_unit foreign key (unit_id) references learn_units (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table learn_lessons (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unit_id bigint not null,
    position integer not null,
    title varchar(120) not null,
    lesson_kind enum ('BONUS','CHECKPOINT','LESSON','REVIEW') not null,
    minutes integer not null,
    xp_reward integer not null,
    primary key (id),
    constraint uk_learn_lessons_unit_position unique (unit_id, position),
    constraint fk_learn_lessons_unit foreign key (unit_id) references learn_units (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table learn_questions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    lesson_id bigint not null,
    position integer not null,
    question_kind enum ('HANZI','LISTEN_BUILD','LISTEN_CHOOSE','MEANING','PINYIN','TRANSLATE_BUILD') not null,
    hanzi varchar(200) null,
    pinyin varchar(300) null,
    meaning varchar(300) null,
    audio_text varchar(200) null,
    choices TEXT not null,
    answer varchar(300) not null,
    explanation varchar(500) null,
    primary key (id),
    key idx_learn_questions_lesson (lesson_id, position),
    constraint fk_learn_questions_lesson foreign key (lesson_id) references learn_lessons (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table learn_lesson_completions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    lesson_id bigint not null,
    best_accuracy integer not null,
    attempts integer not null,
    xp_awarded integer not null,
    first_completed_at datetime(6) not null,
    last_completed_at datetime(6) not null,
    primary key (id),
    constraint uk_learn_lesson_completions_student_lesson unique (student_id, lesson_id),
    key idx_learn_lesson_completions_lesson (lesson_id),
    constraint fk_learn_lesson_completions_student foreign key (student_id) references student_profiles (id),
    constraint fk_learn_lesson_completions_lesson foreign key (lesson_id) references learn_lessons (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- ===== 3. Mistakes =====

create table learning_mistakes (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    mistake_source enum ('LEARN','PRACTICE') not null,
    item_ref varchar(120) not null,
    source_title varchar(200) not null,
    answer_kind enum ('BUILD','CHOICE') not null,
    question_kind enum ('HANZI','LISTEN_BUILD','LISTEN_CHOOSE','MEANING','PINYIN','TRANSLATE_BUILD') null,
    prompt varchar(500) null,
    hanzi varchar(200) null,
    pinyin varchar(300) null,
    meaning varchar(300) null,
    audio_text varchar(200) null,
    choices TEXT not null,
    answer varchar(300) not null,
    explanation varchar(500) null,
    your_answer varchar(300) null,
    wrong_count integer not null,
    last_wrong_at datetime(6) not null,
    cleared_at datetime(6) null,
    primary key (id),
    constraint uk_learning_mistakes_student_item unique (student_id, item_ref),
    key idx_learning_mistakes_student_waiting (student_id, cleared_at, last_wrong_at),
    constraint fk_learning_mistakes_student foreign key (student_id) references student_profiles (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- ===== 4. Level check question bank =====

create table level_check_questions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    prompt varchar(300) not null,
    hanzi varchar(100) null,
    pinyin varchar(200) null,
    audio_text varchar(200) null,
    skill enum ('CHARACTERS','LISTENING','PINYIN','SENTENCES','VOCABULARY') not null,
    difficulty enum ('EASY','HARD','MEDIUM') not null,
    mandarin_level enum ('BEGINNER_1','BEGINNER_2') not null,
    publish_status enum ('DRAFT','PUBLISHED') not null,
    answered_count integer not null,
    correct_count integer not null,
    primary key (id),
    key idx_level_check_questions_pick (publish_status, mandarin_level, difficulty)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table level_check_options (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    question_id bigint not null,
    position integer not null,
    option_text varchar(200) not null,
    correct bit not null,
    explanation varchar(300) null,
    primary key (id),
    key idx_level_check_options_question (question_id, position),
    constraint fk_level_check_options_question foreign key (question_id) references level_check_questions (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- ===== 5. Practice library =====

create table practice_quizzes (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    title varchar(200) not null,
    content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
    rejection_reason varchar(1000) null,
    created_by_user_id bigint null,
    learning_stage enum ('GUIDED','INDEPENDENT','SCAFFOLDED') null,
    hsk_level integer null,
    difficulty enum ('EASY','HARD','MEDIUM') null,
    topic varchar(100) null,
    tags varchar(500) null,
    estimated_seconds integer null,
    xp_reward integer not null,
    primary key (id),
    key idx_practice_quizzes_status_updated (content_status, updated_at),
    key idx_practice_quizzes_created_by_user_id (created_by_user_id),
    constraint fk_practice_quizzes_created_by foreign key (created_by_user_id) references users (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table practice_quiz_questions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    practice_quiz_id bigint not null,
    sort_order integer not null,
    prompt varchar(300) not null,
    hanzi varchar(100) null,
    pinyin varchar(200) null,
    options TEXT not null,
    correct_option_index integer not null,
    explanation varchar(500) null,
    primary key (id),
    key idx_practice_quiz_questions_quiz (practice_quiz_id, sort_order),
    constraint fk_practice_quiz_questions_quiz foreign key (practice_quiz_id) references practice_quizzes (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table flashcard_items (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    flashcard_id bigint not null,
    sort_order integer not null,
    hanzi varchar(100) not null,
    pinyin varchar(200) null,
    meaning varchar(200) not null,
    example varchar(200) null,
    example_meaning varchar(300) null,
    primary key (id),
    key idx_flashcard_items_flashcard (flashcard_id, sort_order),
    constraint fk_flashcard_items_flashcard foreign key (flashcard_id) references flashcards (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

alter table drag_drop_items
    add column distractors varchar(255) null;

alter table tracing_exercises
    add column stroke_count integer null;

create table practice_completions (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    practice_type enum ('DRAG_DROP','FLASHCARDS','LISTENING','MATCHING','QUIZZES','STORY','TRACING') not null,
    activity_id bigint not null,
    correct_count integer not null,
    item_count integer not null,
    xp_awarded integer not null,
    primary key (id),
    key idx_practice_completions_student_created (student_id, created_at),
    key idx_practice_completions_set (practice_type, activity_id),
    constraint fk_practice_completions_student foreign key (student_id) references student_profiles (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- ===== 6. Badges =====

update badges
set name = 'XP Starter', icon_key = 'xp-starter', updated_at = current_timestamp(6)
where name = 'Point Starter'
  and not exists (select 1 from (select id from badges where name = 'XP Starter') existing);
