-- V15: the learning plan (sign-up setup, level check placement, AI-assisted personal plan) and e-mail confirmation
-- for new accounts.
--
--   1. users.email_verified_at: when the owner opened a confirmation link. Existing accounts count as confirmed.
--   2. email_confirmation_tokens: the link e-mailed at student sign-up (hash only; once; newest link only).
--   3. student_profiles.plan_setup_state: new students are sent to the setup; existing students have "skipped" it,
--      so Home offers them the plan instead of interrupting them.
--   4. learning_plans (one row per version; one active per student) and learning_plan_steps (ordered Learn units and
--      classes from the real catalogue). Progress is not stored: it is read from Learn and class completions.

-- ===== 1. E-mail confirmation =====

alter table users
    add column email_verified_at datetime(6) null;

update users set email_verified_at = created_at;

-- ===== 2. Confirmation links =====

create table email_confirmation_tokens (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    user_id bigint not null,
    email varchar(150) not null,
    token_hash varchar(64) not null,
    expires_at datetime(6) not null,
    used_at datetime(6) null,
    primary key (id),
    constraint uk_email_confirmation_tokens_hash unique (token_hash),
    key idx_email_confirmation_tokens_user (user_id, created_at),
    constraint fk_email_confirmation_tokens_user foreign key (user_id) references users (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

-- ===== 3. Setup state =====

alter table student_profiles
    add column plan_setup_state enum ('COMPLETED','NOT_STARTED','SKIPPED') not null default 'NOT_STARTED';

update student_profiles set plan_setup_state = 'SKIPPED';

-- ===== 4. Learning plans =====

create table learning_plans (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    student_id bigint not null,
    version integer not null,
    active bit not null,
    plan_reason enum ('CULTURE','EVERYDAY','FRIENDS_FAMILY','JUST_FOR_ME','OTHER','STUDY','TRAVEL','WORK') not null,
    reason_text varchar(120) null,
    goal_picks varchar(400) not null,
    goal_text varchar(200) null,
    deadline date null,
    plan_pace enum ('INTENSE','LIGHT','REGULAR','SERIOUS') not null,
    focus_skills varchar(60) not null,
    self_level enum ('A_LITTLE','NEW_TO_MANDARIN','NOT_SURE','THE_BASICS') not null,
    placed_level enum ('BEGINNER_1','BEGINNER_2','NEW_TO_MANDARIN') not null,
    head_start bit not null,
    known_skills varchar(200) not null,
    new_skills varchar(200) not null,
    level_checked_at datetime(6) null,
    level_check_answered integer not null,
    target_level enum ('BEGINNER_1','BEGINNER_2','NEW_TO_MANDARIN') not null,
    ai_assisted bit not null,
    goal_title varchar(80) null,
    goal_summary varchar(500) null,
    work_on TEXT null,
    outcomes TEXT null,
    ambitious bit not null,
    milestone_week integer null,
    milestone_text varchar(400) null,
    starts_on date not null,
    total_weeks integer not null,
    content_weeks integer not null,
    primary key (id),
    constraint uk_learning_plans_student_version unique (student_id, version),
    key idx_learning_plans_student_active (student_id, active),
    constraint fk_learning_plans_student foreign key (student_id) references student_profiles (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create table learning_plan_steps (
    id bigint not null auto_increment,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    plan_id bigint not null,
    position integer not null,
    step_kind enum ('CLASS','LEARN_UNIT') not null,
    ref_id bigint not null,
    title varchar(200) not null,
    reason varchar(300) null,
    goal_picks varchar(400) not null,
    optional_step bit not null,
    quick_review bit not null,
    week_from integer not null,
    week_to integer not null,
    primary key (id),
    constraint uk_learning_plan_steps_plan_position unique (plan_id, position),
    constraint fk_learning_plan_steps_plan foreign key (plan_id) references learning_plans (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;
