-- =====================================================================
-- V1 - Consolidated baseline schema.
--
-- This is the single source-of-truth migration that creates every
-- table, index, foreign-key constraint, and default-seed row used by
-- the platform. It was authored by consolidating multiple earlier
-- baselines into one file once the team agreed on the v1 contract.
--
-- Why one giant file (vs. per-entity migrations):
--   - Flyway checksums become stable: every developer / CI runs the
--     same migration in the same order, so we never see "your local
--     DB drifted from production" issues.
--   - The schema reads top-to-bottom like a contract: a reviewer can
--     see every table, its columns, and its constraints in a single
--     pass without jumping across files.
--   - During this pre-production assessment phase, every accepted
--     schema change is folded back into this one baseline file so a
--     fresh install has one database contract to review and apply.
--
-- Conventions used throughout:
--   - Primary keys are `bigint auto_increment`.
--   - Created/updated audit columns use `datetime(6)` for
--     microsecond precision, populated by JPA `@PrePersist` /
--     `@PreUpdate` callbacks.
--   - Enum columns use `varchar(N)` rather than MySQL ENUMs so we can
--     add / rename values without an `ALTER TABLE`.
--   - Soft-delete uses `is_active`/`deleted_at` columns; the
--     application never issues hard DELETEs on user-facing entities.
--   - Foreign keys use the `xx_id` naming convention and are indexed
--     for query performance on the FK column.
--
-- Forward-only:
--   This file is the only migration in this repository. If the project
--   later ships against a long-lived production database, freeze this
--   baseline at that release and create a new baseline only through a
--   deliberate migration-squash process.
-- =====================================================================

-- ===== Section: baseline core schema =====

    create table ai_chat_messages (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        session_id bigint not null,
        updated_at datetime(6) not null,
        sender_role varchar(30) not null,
        message_text LONGTEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table ai_chat_sessions (
        active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        related_lesson_id bigint,
        updated_at datetime(6) not null,
        user_id bigint not null,
        language_preference varchar(20),
        feature_key varchar(100) not null,
        session_title varchar(200),
        primary key (id)
    ) engine=InnoDB;

    create table ai_feedback_records (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        uploaded_file_id bigint,
        user_id bigint not null,
        language_preference varchar(20),
        status varchar(50),
        feature_key varchar(100) not null,
        input_file_url varchar(255),
        feedback_text LONGTEXT,
        input_text LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table ai_usage_logs (
        token_count integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        related_entity_id bigint,
        updated_at datetime(6) not null,
        user_id bigint,
        language_preference varchar(20),
        status varchar(50) not null,
        user_role varchar(50),
        feature_key varchar(100) not null,
        provider_name varchar(100),
        related_entity_type varchar(100),
        model_name varchar(120),
        input_summary varchar(500),
        output_summary varchar(500),
        error_summary varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table audit_logs (
        actor_user_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        target_id bigint,
        updated_at datetime(6) not null,
        ip_address varchar(64),
        action_type varchar(100) not null,
        target_type varchar(100),
        actor_email varchar(150),
        description LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table badges (
        active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        icon_key varchar(100),
        name varchar(120) not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table cart_items (
        quantity integer not null,
        unit_price decimal(10,2),
        cart_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        item_id bigint not null,
        updated_at datetime(6) not null,
        item_type varchar(50) not null,
        primary key (id)
    ) engine=InnoDB;

    create table carts (
        active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        student_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create table content_moderation_records (
        admin_user_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        related_teacher_id bigint,
        target_id bigint not null,
        updated_at datetime(6) not null,
        target_type varchar(50) not null,
        action_taken varchar(100) not null,
        target_title_snapshot varchar(255),
        note LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table course_packages (
        price decimal(10,2),
        category_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        level_id bigint,
        teacher_id bigint,
        updated_at datetime(6) not null,
        title varchar(200) not null,
        rejection_reason varchar(1000),
        description varchar(2000),
        cover_image_url varchar(255),
        access_type enum ('FREE','PAID') not null,
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        learning_type enum ('HYBRID','LIVE','RECORDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table daily_streaks (
        best_streak integer not null,
        current_streak integer not null,
        last_learning_date date,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        student_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create table drag_drop_activities (
        estimated_seconds integer,
        hsk_level integer,
        xp_reward integer not null,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        topic varchar(100),
        title varchar(200) not null,
        tags varchar(500),
        instructions varchar(1000),
        age_band enum ('ALL_AGES','KINDERGARTEN','LOWER_PRIMARY','UPPER_PRIMARY'),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        difficulty enum ('EASY','HARD','MEDIUM'),
        primary key (id)
    ) engine=InnoDB;

    create table drag_drop_items (
        sort_order integer not null,
        created_at datetime(6) not null,
        drag_drop_activity_id bigint not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        target_zone varchar(200) not null,
        drag_text varchar(500) not null,
        image_url varchar(255),
        pinyin varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table flashcards (
        estimated_seconds integer,
        hsk_level integer,
        sort_order integer not null,
        xp_reward integer not null,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        topic varchar(100),
        title varchar(200),
        english_meaning varchar(500),
        front_text varchar(500) not null,
        malay_meaning varchar(500),
        tags varchar(500),
        back_text varchar(1000) not null,
        audio_url varchar(255),
        image_url varchar(255),
        pinyin varchar(255),
        age_band enum ('ALL_AGES','KINDERGARTEN','LOWER_PRIMARY','UPPER_PRIMARY'),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        difficulty enum ('EASY','HARD','MEDIUM'),
        extra_payload LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table learning_categories (
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        name varchar(120) not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table learning_levels (
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        name varchar(80) not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table learning_modules (
        price decimal(10,2),
        category_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        level_id bigint,
        teacher_id bigint,
        updated_at datetime(6) not null,
        title varchar(200) not null,
        rejection_reason varchar(1000),
        description varchar(2000),
        cover_image_url varchar(255),
        access_type enum ('FREE','PAID') not null,
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table lesson_progress (
        completed bit not null,
        completion_percent integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_accessed_at datetime(6),
        lesson_id bigint not null,
        student_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create table lessons (
        estimated_duration_minutes integer,
        price decimal(10,2),
        category_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        level_id bigint,
        module_id bigint,
        teacher_id bigint,
        updated_at datetime(6) not null,
        title varchar(200) not null,
        rejection_reason varchar(1000),
        summary varchar(1000),
        cover_image_url varchar(255),
        access_type enum ('FREE','PAID') not null,
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        learning_type enum ('HYBRID','LIVE','RECORDED') not null,
        lesson_content LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table listening_exercises (
        estimated_seconds integer,
        hsk_level integer,
        xp_reward integer not null,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        topic varchar(100),
        title varchar(200) not null,
        tags varchar(500),
        audio_url varchar(255),
        age_band enum ('ALL_AGES','KINDERGARTEN','LOWER_PRIMARY','UPPER_PRIMARY'),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        difficulty enum ('EASY','HARD','MEDIUM'),
        question_payload LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table live_class_enrolments (
        attended_at datetime(6),
        cancelled_at datetime(6),
        created_at datetime(6) not null,
        enrolled_at datetime(6),
        id bigint not null auto_increment,
        live_class_id bigint not null,
        student_id bigint not null,
        updated_at datetime(6) not null,
        enrolment_status enum ('ATTENDED','CANCELLED','ENROLLED','MISSED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table live_classes (
        max_participants integer,
        course_package_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint,
        scheduled_end_at datetime(6),
        scheduled_start_at datetime(6),
        teacher_id bigint,
        updated_at datetime(6) not null,
        title varchar(200) not null,
        meeting_link varchar(500),
        description varchar(1000),
        live_class_status enum ('CANCELLED','COMPLETED','ONGOING','RESCHEDULED','SCHEDULED') not null,
        meeting_platform enum ('GOOGLE_MEET','MICROSOFT_TEAMS','OTHER','ZOOM') not null,
        primary key (id)
    ) engine=InnoDB;

    create table matching_game_items (
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        matching_game_id bigint not null,
        updated_at datetime(6) not null,
        left_text varchar(500) not null,
        right_text varchar(500) not null,
        left_image_url varchar(255),
        pinyin varchar(255),
        right_image_url varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table matching_games (
        estimated_seconds integer,
        hsk_level integer,
        xp_reward integer not null,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        topic varchar(100),
        title varchar(200) not null,
        tags varchar(500),
        age_band enum ('ALL_AGES','KINDERGARTEN','LOWER_PRIMARY','UPPER_PRIMARY'),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        difficulty enum ('EASY','HARD','MEDIUM'),
        primary key (id)
    ) engine=InnoDB;

    create table notifications (
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        read_at datetime(6),
        target_id bigint,
        updated_at datetime(6) not null,
        user_id bigint not null,
        target_type varchar(50),
        title varchar(200) not null,
        target_path varchar(255),
        message LONGTEXT not null,
        notification_status enum ('READ','UNREAD') not null,
        notification_type enum ('ADMIN_TASK','CONTENT_REVIEW','LIVE_CLASS_REMINDER','MODERATION_ACTION','PAYMENT_STATUS','PAYOUT_STATUS','PROGRESS_UPDATE','QUIZ_RESULT','REFUND_STATUS','REWARD_EARNED','SYSTEM','TEACHER_APPROVAL','TEACHER_FEEDBACK') not null,
        primary key (id)
    ) engine=InnoDB;

    create table order_items (
        line_total decimal(10,2),
        quantity integer not null,
        unit_price decimal(10,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        item_id bigint not null,
        order_id bigint not null,
        teacher_id bigint,
        updated_at datetime(6) not null,
        item_type varchar(50) not null,
        feature_key varchar(100),
        item_title_snapshot varchar(200),
        primary key (id)
    ) engine=InnoDB;

    create table orders (
        total_amount decimal(10,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        student_id bigint not null,
        updated_at datetime(6) not null,
        currency_code varchar(10) not null,
        checkout_source varchar(30),
        order_number varchar(50) not null,
        payment_status enum ('CANCELLED','FAILED','PAID','PENDING','REFUNDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table package_items (
        sort_order integer not null,
        course_package_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        item_id bigint not null,
        updated_at datetime(6) not null,
        title_snapshot varchar(200),
        item_type enum ('MODULE','LESSON') not null,
        primary key (id)
    ) engine=InnoDB;

    create table password_reset_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        used_at datetime(6),
        user_id bigint not null,
        token varchar(128) not null,
        primary key (id)
    ) engine=InnoDB;

    create table payment_entitlements (
        active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        item_id bigint not null,
        order_id bigint,
        payment_record_id bigint,
        student_id bigint not null,
        updated_at datetime(6) not null,
        item_type varchar(50) not null,
        source_type varchar(50) not null,
        feature_key varchar(100),
        primary key (id)
    ) engine=InnoDB;

    create table payment_records (
        amount decimal(10,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        order_id bigint,
        paid_at datetime(6),
        student_id bigint,
        updated_at datetime(6) not null,
        currency_code varchar(10) not null,
        payment_method varchar(100),
        provider_name varchar(100) not null,
        provider_status varchar(100),
        provider_reference varchar(150),
        payment_status enum ('CANCELLED','FAILED','PAID','PENDING','REFUNDED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table payout_requests (
        amount decimal(10,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        teacher_id bigint not null,
        updated_at datetime(6) not null,
        decision_reason varchar(1000),
        requested_note varchar(1000),
        payout_status enum ('APPROVED','PAID','PENDING','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table points_transactions (
        points_delta integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        source_id bigint,
        student_id bigint not null,
        updated_at datetime(6) not null,
        source_type varchar(100),
        reason varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table quiz_attempt_answers (
        correct bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        question_id bigint not null,
        quiz_attempt_id bigint not null,
        updated_at datetime(6) not null,
        answer_text LONGTEXT,
        primary key (id)
    ) engine=InnoDB;

    create table quiz_attempts (
        max_score integer,
        score integer,
        submitted bit not null,
        completed_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        quiz_id bigint not null,
        started_at datetime(6),
        student_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create table quiz_options (
        correct_option bit not null,
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        question_id bigint not null,
        updated_at datetime(6) not null,
        option_text LONGTEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table quiz_questions (
        points integer not null,
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        quiz_id bigint not null,
        updated_at datetime(6) not null,
        question_type varchar(50) not null,
        correct_answer_text varchar(1000),
        explanation varchar(1000),
        question_text LONGTEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table quizzes (
        time_limit_minutes integer,
        total_points integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint,
        teacher_id bigint,
        updated_at datetime(6) not null,
        title varchar(200) not null,
        description varchar(1000),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table recorded_lesson_videos (
        active bit not null,
        duration_seconds integer,
        sort_order integer not null,
        course_package_id bigint,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint not null,
        updated_at datetime(6) not null,
        uploaded_file_id bigint,
        title varchar(200) not null,
        external_video_url varchar(500),
        description varchar(1000),
        source_type enum ('EXTERNAL_URL','UPLOADED_FILE') not null,
        primary key (id)
    ) engine=InnoDB;

    create table refund_requests (
        amount decimal(10,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        order_id bigint,
        student_id bigint not null,
        updated_at datetime(6) not null,
        decision_reason varchar(1000),
        reason varchar(1000),
        refund_status enum ('APPROVED','PENDING','REFUNDED','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table roles (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        description varchar(255),
        name enum ('ADMIN','STUDENT','TEACHER') not null,
        primary key (id)
    ) engine=InnoDB;

    create table story_activities (
        estimated_seconds integer,
        hsk_level integer,
        xp_reward integer not null,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        topic varchar(100),
        title varchar(200) not null,
        tags varchar(500),
        instructions varchar(1000),
        image_url varchar(255),
        age_band enum ('ALL_AGES','KINDERGARTEN','LOWER_PRIMARY','UPPER_PRIMARY'),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        difficulty enum ('EASY','HARD','MEDIUM'),
        pinyin TEXT,
        story_text LONGTEXT not null,
        translation TEXT,
        primary key (id)
    ) engine=InnoDB;

    create table story_questions (
        sort_order integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        story_activity_id bigint not null,
        updated_at datetime(6) not null,
        question_text varchar(500) not null,
        explanation varchar(1000),
        options_payload TEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table student_quiz_progress (
        attempt_count integer not null,
        completed bit not null,
        completed_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_interacted_at datetime(6),
        lesson_id bigint not null,
        quiz_id bigint not null,
        started_at datetime(6),
        student_id bigint not null,
        updated_at datetime(6) not null,
        completion_summary varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table student_badges (
        awarded_at datetime(6) not null,
        badge_id bigint not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        student_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create table student_profiles (
        total_points integer not null,
        created_at datetime(6) not null,
        current_level_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        country varchar(100),
        display_name varchar(120),
        school_name varchar(150),
        avatar_url varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table student_progress (
        completion_percent integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        level_id bigint,
        student_id bigint not null,
        updated_at datetime(6) not null,
        last_activity_summary varchar(500),
        weak_areas varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table system_settings (
        editable bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        setting_key varchar(100) not null,
        setting_value varchar(2000) not null,
        description varchar(255),
        primary key (id)
    ) engine=InnoDB;

    create table teacher_earnings (
        commission_amount decimal(10,2),
        gross_amount decimal(10,2),
        net_amount decimal(10,2),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        order_item_id bigint,
        teacher_id bigint not null,
        updated_at datetime(6) not null,
        earning_status varchar(30) not null,
        primary key (id)
    ) engine=InnoDB;

    create table teacher_feedback (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint,
        quiz_attempt_id bigint,
        student_id bigint not null,
        teacher_id bigint not null,
        updated_at datetime(6) not null,
        feedback_type varchar(50),
        feedback_message LONGTEXT not null,
        primary key (id)
    ) engine=InnoDB;

    create table teacher_profiles (
        public_visible bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        user_id bigint not null,
        verified_at datetime(6),
        teaching_categories varchar(500),
        qualification_summary varchar(1000),
        rejection_reason varchar(1000),
        bio varchar(2000),
        teacher_status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table teacher_verification_documents (
        current_version bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        submitted_at datetime(6),
        teacher_id bigint not null,
        updated_at datetime(6) not null,
        uploaded_file_id bigint not null,
        document_type varchar(100) not null,
        document_note varchar(500),
        primary key (id)
    ) engine=InnoDB;

    create table teacher_verification_reviews (
        created_at datetime(6) not null,
        document_id bigint,
        id bigint not null auto_increment,
        reviewed_at datetime(6),
        reviewer_user_id bigint,
        teacher_id bigint not null,
        updated_at datetime(6) not null,
        review_note varchar(1000),
        decision_status enum ('APPROVED','PENDING','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB;

    create table tracing_exercises (
        estimated_seconds integer,
        hsk_level integer,
        xp_reward integer not null,
        created_at datetime(6) not null,
        created_by_user_id bigint,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        target_character varchar(100),
        topic varchar(100),
        title varchar(200) not null,
        meaning varchar(500),
        tags varchar(500),
        instructions varchar(1000),
        pinyin varchar(255),
        worksheet_url varchar(255),
        age_band enum ('ALL_AGES','KINDERGARTEN','LOWER_PRIMARY','UPPER_PRIMARY'),
        content_status enum ('APPROVED','ARCHIVED','DRAFT','PUBLISHED','REJECTED','SUBMITTED') not null,
        difficulty enum ('EASY','HARD','MEDIUM'),
        primary key (id)
    ) engine=InnoDB;

    create table uploaded_files (
        active bit not null,
        created_at datetime(6) not null,
        file_size bigint not null,
        id bigint not null auto_increment,
        owner_user_id bigint,
        updated_at datetime(6) not null,
        access_level varchar(30) not null,
        content_type varchar(120) not null,
        file_path varchar(500) not null,
        file_name varchar(255) not null,
        stored_file_name varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table user_roles (
        role_id bigint not null,
        user_id bigint not null,
        constraint uk_user_roles_user_role primary key (user_id, role_id)
    ) engine=InnoDB;

    create table users (
        active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        preferred_language varchar(10),
        display_name varchar(120) not null,
        email varchar(150) not null,
        password_hash varchar(255) not null,
        primary key (id)
    ) engine=InnoDB;

    create table video_progress (
        completed bit not null,
        progress_percentage integer,
        total_seconds integer,
        watched_seconds integer,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_watched_at datetime(6),
        lesson_id bigint not null,
        recorded_video_id bigint,
        student_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB;

    create index idx_ai_chat_messages_session_id 
       on ai_chat_messages (session_id);

    create index idx_ai_chat_sessions_user_id 
       on ai_chat_sessions (user_id);

    create index idx_ai_chat_sessions_feature_key 
       on ai_chat_sessions (feature_key);

    create index idx_ai_feedback_records_user_id 
       on ai_feedback_records (user_id);

    create index idx_ai_feedback_records_feature_key 
       on ai_feedback_records (feature_key);

    create index idx_ai_usage_logs_user_id 
       on ai_usage_logs (user_id);

    create index idx_ai_usage_logs_feature_key 
       on ai_usage_logs (feature_key);

    create index idx_ai_usage_logs_created_at 
       on ai_usage_logs (created_at);

    create index idx_audit_logs_actor_user_id 
       on audit_logs (actor_user_id);

    create index idx_audit_logs_action_type 
       on audit_logs (action_type);

    create index idx_audit_logs_created_at 
       on audit_logs (created_at);

    alter table badges 
       add constraint idx_badges_name unique (name);

    create index idx_cart_items_cart_id 
       on cart_items (cart_id);

    alter table carts 
       add constraint idx_carts_student_id unique (student_id);

    create index idx_content_moderation_records_admin_user_id 
       on content_moderation_records (admin_user_id);

    create index idx_content_moderation_records_target_type_target_id 
       on content_moderation_records (target_type, target_id);

    create index idx_course_packages_teacher_id 
       on course_packages (teacher_id);

    create index idx_course_packages_status 
       on course_packages (content_status);

    create index idx_daily_streaks_student_id 
       on daily_streaks (student_id);

    create index idx_drag_drop_activities_status 
       on drag_drop_activities (content_status);

    create index idx_drag_drop_activities_hsk_level 
       on drag_drop_activities (hsk_level);

    create index idx_drag_drop_activities_age_band 
       on drag_drop_activities (age_band);

    create index idx_drag_drop_activities_difficulty 
       on drag_drop_activities (difficulty);

    create index idx_drag_drop_activities_created_by_user_id 
       on drag_drop_activities (created_by_user_id);

    create index idx_drag_drop_items_activity_id 
       on drag_drop_items (drag_drop_activity_id);

    create index idx_flashcards_status 
       on flashcards (content_status);

    create index idx_flashcards_hsk_level 
       on flashcards (hsk_level);

    create index idx_flashcards_age_band 
       on flashcards (age_band);

    create index idx_flashcards_difficulty 
       on flashcards (difficulty);

    create index idx_flashcards_created_by_user_id 
       on flashcards (created_by_user_id);

    alter table learning_categories 
       add constraint idx_learning_categories_name unique (name);

    alter table learning_levels 
       add constraint idx_learning_levels_name unique (name);

    create index idx_learning_modules_teacher_id 
       on learning_modules (teacher_id);

    create index idx_learning_modules_level_id 
       on learning_modules (level_id);

    create index idx_learning_modules_category_id 
       on learning_modules (category_id);

    create index idx_learning_modules_status 
       on learning_modules (content_status);

    create index idx_lesson_progress_student_id 
       on lesson_progress (student_id);

    create index idx_lesson_progress_lesson_id 
       on lesson_progress (lesson_id);

    create index idx_lessons_module_id 
       on lessons (module_id);

    create index idx_lessons_teacher_id 
       on lessons (teacher_id);

    create index idx_lessons_status 
       on lessons (content_status);

    create index idx_listening_exercises_status 
       on listening_exercises (content_status);

    create index idx_listening_exercises_hsk_level 
       on listening_exercises (hsk_level);

    create index idx_listening_exercises_age_band 
       on listening_exercises (age_band);

    create index idx_listening_exercises_difficulty 
       on listening_exercises (difficulty);

    create index idx_listening_exercises_created_by_user_id 
       on listening_exercises (created_by_user_id);

    create index idx_live_class_enrolments_live_class_id 
       on live_class_enrolments (live_class_id);

    create index idx_live_class_enrolments_student_id 
       on live_class_enrolments (student_id);

    alter table live_class_enrolments 
       add constraint uk_live_class_enrolments_live_class_student unique (live_class_id, student_id);

    create index idx_live_classes_teacher_id 
       on live_classes (teacher_id);

    create index idx_live_classes_lesson_id 
       on live_classes (lesson_id);

    create index idx_live_classes_status 
       on live_classes (live_class_status);

    create index idx_live_classes_scheduled_start_at 
       on live_classes (scheduled_start_at);

    create index idx_matching_game_items_game_id 
       on matching_game_items (matching_game_id);

    create index idx_matching_games_status 
       on matching_games (content_status);

    create index idx_matching_games_hsk_level 
       on matching_games (hsk_level);

    create index idx_matching_games_age_band 
       on matching_games (age_band);

    create index idx_matching_games_difficulty 
       on matching_games (difficulty);

    create index idx_matching_games_created_by_user_id 
       on matching_games (created_by_user_id);

    create index idx_notifications_user_id 
       on notifications (user_id);

    create index idx_notifications_status 
       on notifications (notification_status);

    create index idx_notifications_type 
       on notifications (notification_type);

    create index idx_order_items_order_id 
       on order_items (order_id);

    create index idx_orders_student_id 
       on orders (student_id);

    create index idx_orders_payment_status 
       on orders (payment_status);

    alter table orders 
       add constraint UKnthkiu7pgmnqnu86i2jyoe2v7 unique (order_number);

    create index idx_package_items_package_id 
       on package_items (course_package_id);

    create index idx_password_reset_tokens_user_id 
       on password_reset_tokens (user_id);

    alter table password_reset_tokens 
       add constraint idx_password_reset_tokens_token unique (token);

    create index idx_payment_entitlements_student_id 
       on payment_entitlements (student_id);

    create index idx_payment_entitlements_order_id 
       on payment_entitlements (order_id);

    create index idx_payment_entitlements_payment_id 
       on payment_entitlements (payment_record_id);

    create index idx_payment_entitlements_item 
       on payment_entitlements (item_type, item_id);

    create index idx_payment_records_order_id 
       on payment_records (order_id);

    create index idx_payment_records_status 
       on payment_records (payment_status);

    create index idx_payout_requests_teacher_id 
       on payout_requests (teacher_id);

    create index idx_payout_requests_status 
       on payout_requests (payout_status);

    create index idx_points_transactions_student_id 
       on points_transactions (student_id);

    create index idx_points_transactions_created_at 
       on points_transactions (created_at);

    create index idx_quiz_attempt_answers_attempt_id 
       on quiz_attempt_answers (quiz_attempt_id);

    create index idx_quiz_attempt_answers_question_id 
       on quiz_attempt_answers (question_id);

    create index idx_quiz_attempts_quiz_id 
       on quiz_attempts (quiz_id);

    create index idx_quiz_attempts_student_id 
       on quiz_attempts (student_id);

    create index idx_quiz_attempts_completed_at 
       on quiz_attempts (completed_at);

    create index idx_quiz_options_question_id 
       on quiz_options (question_id);

    create index idx_quiz_questions_quiz_id 
       on quiz_questions (quiz_id);

    create index idx_quizzes_lesson_id 
       on quizzes (lesson_id);

    create index idx_quizzes_status 
       on quizzes (content_status);

    create index idx_recorded_videos_lesson_id 
       on recorded_lesson_videos (lesson_id);

    create index idx_recorded_videos_package_id 
       on recorded_lesson_videos (course_package_id);

    create index idx_recorded_videos_uploaded_file_id 
       on recorded_lesson_videos (uploaded_file_id);

    create index idx_recorded_videos_active 
       on recorded_lesson_videos (active);

    create index idx_refund_requests_student_id 
       on refund_requests (student_id);

    create index idx_refund_requests_order_id 
       on refund_requests (order_id);

    create index idx_refund_requests_status 
       on refund_requests (refund_status);

    alter table roles 
       add constraint idx_roles_name unique (name);

    create index idx_story_activities_status 
       on story_activities (content_status);

    create index idx_story_activities_hsk_level 
       on story_activities (hsk_level);

    create index idx_story_activities_age_band 
       on story_activities (age_band);

    create index idx_story_activities_difficulty 
       on story_activities (difficulty);

    create index idx_story_activities_created_by_user_id 
       on story_activities (created_by_user_id);

    create index idx_story_questions_activity_id 
       on story_questions (story_activity_id);

    create index idx_student_quiz_progress_student_id 
       on student_quiz_progress (student_id);

    create index idx_student_quiz_progress_lesson_id 
       on student_quiz_progress (lesson_id);

    create index idx_student_quiz_progress_quiz_id 
       on student_quiz_progress (quiz_id);

    alter table student_quiz_progress 
       add constraint uk_student_quiz_progress_student_quiz unique (student_id, quiz_id);

    create index idx_student_badges_student_id 
       on student_badges (student_id);

    create index idx_student_badges_badge_id 
       on student_badges (badge_id);

    alter table student_badges 
       add constraint uk_student_badges_student_badge unique (student_id, badge_id);

    create index idx_student_profiles_current_level_id 
       on student_profiles (current_level_id);

    alter table student_profiles 
       add constraint idx_student_profiles_user_id unique (user_id);

    create index idx_student_progress_student_id 
       on student_progress (student_id);

    create index idx_student_progress_level_id 
       on student_progress (level_id);

    alter table system_settings 
       add constraint idx_system_settings_setting_key unique (setting_key);

    create index idx_teacher_earnings_teacher_id 
       on teacher_earnings (teacher_id);

    create index idx_teacher_earnings_order_item_id 
       on teacher_earnings (order_item_id);

    create index idx_teacher_feedback_teacher_id 
       on teacher_feedback (teacher_id);

    create index idx_teacher_feedback_student_id 
       on teacher_feedback (student_id);

    create index idx_teacher_feedback_lesson_id 
       on teacher_feedback (lesson_id);

    create index idx_teacher_feedback_quiz_attempt_id 
       on teacher_feedback (quiz_attempt_id);

    create index idx_teacher_profiles_status 
       on teacher_profiles (teacher_status);

    alter table teacher_profiles 
       add constraint idx_teacher_profiles_user_id unique (user_id);

    create index idx_teacher_verification_documents_teacher_id 
       on teacher_verification_documents (teacher_id);

    create index idx_teacher_verification_documents_uploaded_file_id 
       on teacher_verification_documents (uploaded_file_id);

    create index idx_teacher_verification_reviews_teacher_id 
       on teacher_verification_reviews (teacher_id);

    create index idx_teacher_verification_reviews_reviewer_user_id 
       on teacher_verification_reviews (reviewer_user_id);

    create index idx_tracing_exercises_status 
       on tracing_exercises (content_status);

    create index idx_tracing_exercises_hsk_level 
       on tracing_exercises (hsk_level);

    create index idx_tracing_exercises_age_band 
       on tracing_exercises (age_band);

    create index idx_tracing_exercises_difficulty 
       on tracing_exercises (difficulty);

    create index idx_tracing_exercises_created_by_user_id 
       on tracing_exercises (created_by_user_id);

    create index idx_uploaded_files_owner_user_id 
       on uploaded_files (owner_user_id);

    create index idx_uploaded_files_created_at 
       on uploaded_files (created_at);

    create index idx_users_created_at 
       on users (created_at);

    alter table users 
       add constraint idx_users_email unique (email);

    create index idx_video_progress_student_id 
       on video_progress (student_id);

    create index idx_video_progress_lesson_id 
       on video_progress (lesson_id);

    create index idx_video_progress_recorded_video_id 
       on video_progress (recorded_video_id);

    alter table video_progress 
       add constraint uk_video_progress_student_video unique (student_id, recorded_video_id);

    alter table ai_chat_messages 
       add constraint FKncfaarj6gwd37j1fi98bmpmcl 
       foreign key (session_id) 
       references ai_chat_sessions (id);

    alter table ai_chat_sessions 
       add constraint FK6mxk23rx514hrkne46l8bblp4 
       foreign key (user_id) 
       references users (id);

    alter table ai_feedback_records 
       add constraint FKhaejvy681n9rcvn3hh1ed9kp1 
       foreign key (uploaded_file_id) 
       references uploaded_files (id);

    alter table ai_feedback_records 
       add constraint FK1iv926cruqituwqaufp5bm8gx 
       foreign key (user_id) 
       references users (id);

    alter table ai_usage_logs 
       add constraint FKs6ew5u0i9jag515tvn9ms1t2w 
       foreign key (user_id) 
       references users (id);

    alter table audit_logs 
       add constraint FK17vn8rhj6qver0naebk935vkk 
       foreign key (actor_user_id) 
       references users (id);

    alter table cart_items 
       add constraint FKpcttvuq4mxppo8sxggjtn5i2c 
       foreign key (cart_id) 
       references carts (id);

    alter table carts 
       add constraint FK94dli8br6rdc9yvd20p6b9g6e 
       foreign key (student_id) 
       references student_profiles (id);

    alter table content_moderation_records 
       add constraint FKtq2i8kbttgioi6tbif9eod36k 
       foreign key (admin_user_id) 
       references users (id);

    alter table content_moderation_records 
       add constraint FKr9jc8to94rwwntccete1jdsd9 
       foreign key (related_teacher_id) 
       references teacher_profiles (id);

    alter table course_packages 
       add constraint FKrpijdtkt0sen2sf1anb4phqym 
       foreign key (category_id) 
       references learning_categories (id);

    alter table course_packages 
       add constraint FKg4b3swtw73y9rgsxsrfh1rfrj 
       foreign key (level_id) 
       references learning_levels (id);

    alter table course_packages 
       add constraint FKro7vyjl7p0lepwedme1h5uqxe 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table daily_streaks 
       add constraint FKs76gc3on5gbgotqkw7si0s2ha 
       foreign key (student_id) 
       references student_profiles (id);

    alter table drag_drop_activities 
       add constraint FKei5kgr46vhjn8qfk9a6d7a5yl 
       foreign key (created_by_user_id) 
       references users (id);

    alter table drag_drop_items 
       add constraint FKa1elfd3nl4s21kyps00ur44sn 
       foreign key (drag_drop_activity_id) 
       references drag_drop_activities (id);

    alter table flashcards 
       add constraint FKmss52266jmxyysm5uijhf63ie 
       foreign key (created_by_user_id) 
       references users (id);

    alter table learning_modules 
       add constraint FKcy0imyujgs9isekjhu4piwo04 
       foreign key (category_id) 
       references learning_categories (id);

    alter table learning_modules 
       add constraint FKgsekhoyy8f6thh4djiw73o8gw 
       foreign key (level_id) 
       references learning_levels (id);

    alter table learning_modules 
       add constraint FKngka0ebpyqfu9deb7mflm7kms 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table lesson_progress 
       add constraint FKqwr70bkn0j6gok1y4op9jns8y 
       foreign key (lesson_id) 
       references lessons (id);

    alter table lesson_progress 
       add constraint FKq75k0u8fs6niodnxid01mpy57 
       foreign key (student_id) 
       references student_profiles (id);

    alter table lessons 
       add constraint FKmbyaqkogx5f50xe9xkrgf1yri 
       foreign key (category_id) 
       references learning_categories (id);

    alter table lessons 
       add constraint FK1lgvcryna8gxh8bv22ll97qjr 
       foreign key (level_id) 
       references learning_levels (id);

    alter table lessons 
       add constraint FKbioqstqvvgu46entuqgajuklb 
       foreign key (module_id) 
       references learning_modules (id);

    alter table lessons 
       add constraint FK85pfpipvgxct5yalal134dq1w 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table listening_exercises 
       add constraint FKlo1t6skrjponktm3nck9suoww 
       foreign key (created_by_user_id) 
       references users (id);

    alter table live_class_enrolments 
       add constraint FK78q00l39eytntaeagtbkav5xg 
       foreign key (live_class_id) 
       references live_classes (id);

    alter table live_class_enrolments 
       add constraint FK5hntartx4wfp4e24ji9c17j9m 
       foreign key (student_id) 
       references student_profiles (id);

    alter table live_classes 
       add constraint FKqg3u5ofl9ucg96mshrjh1jdas 
       foreign key (course_package_id) 
       references course_packages (id);

    alter table live_classes 
       add constraint FKhlxheioyp7bshqkiixh1v8qtx 
       foreign key (lesson_id) 
       references lessons (id);

    alter table live_classes 
       add constraint FKt3xuqfrdocxy59x5l4nrsvmb8 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table matching_game_items 
       add constraint FK65586qwsgs79q383doebg3p87 
       foreign key (matching_game_id) 
       references matching_games (id);

    alter table matching_games 
       add constraint FK9qopobo92p8ga0io07cq6xbcr 
       foreign key (created_by_user_id) 
       references users (id);

    alter table notifications 
       add constraint FKciucoi9mtan2s6xmcfrt08cln 
       foreign key (created_by_user_id) 
       references users (id);

    alter table notifications 
       add constraint FK9y21adhxn0ayjhfocscqox7bh 
       foreign key (user_id) 
       references users (id);

    alter table order_items 
       add constraint FKbioxgbv59vetrxe0ejfubep1w 
       foreign key (order_id) 
       references orders (id);

    alter table order_items 
       add constraint FK2advp773jjyxinc5kgxnohx4l 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table orders 
       add constraint FKpovxv0wskgfcvrnwhssdf2jo6 
       foreign key (student_id) 
       references student_profiles (id);

    alter table package_items 
       add constraint FK3y1vt7pu35xvx6ip4hsopcc6d 
       foreign key (course_package_id) 
       references course_packages (id);

    alter table password_reset_tokens 
       add constraint FKk3ndxg5xp6v7wd4gjyusp15gq 
       foreign key (user_id) 
       references users (id);

    alter table payment_entitlements 
       add constraint FK7d9y052u9mtl9qawornj5nwy7 
       foreign key (order_id) 
       references orders (id);

    alter table payment_entitlements 
       add constraint FK7lm90rg92vjyw87e14w2jkwyx 
       foreign key (payment_record_id) 
       references payment_records (id);

    alter table payment_entitlements 
       add constraint FK9en9ek35tj74sy7rnsk8v1miy 
       foreign key (student_id) 
       references student_profiles (id);

    alter table payment_records 
       add constraint FKcqu2d790mp868g1ogc678ygaj 
       foreign key (order_id) 
       references orders (id);

    alter table payment_records 
       add constraint FKqx0d20cubo6ak3qp79vdhpmnt 
       foreign key (student_id) 
       references student_profiles (id);

    alter table payout_requests 
       add constraint FK1odx87vhbl5ucq1m0iaqkwktj 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table points_transactions 
       add constraint FK1m9uhs8ghtik7hl37hllyp3bn 
       foreign key (student_id) 
       references student_profiles (id);

    alter table quiz_attempt_answers 
       add constraint FK59s9075rs7i5520p8awj6ri1 
       foreign key (question_id) 
       references quiz_questions (id);

    alter table quiz_attempt_answers 
       add constraint FKm8571omdvyu3q35r8ansqqqi2 
       foreign key (quiz_attempt_id) 
       references quiz_attempts (id);

    alter table quiz_attempts 
       add constraint FKfwipvfipnnwsoacoyv5k7fbxc 
       foreign key (quiz_id) 
       references quizzes (id);

    alter table quiz_attempts 
       add constraint FKo1dvlihe37kkv2ugmj8l4oica 
       foreign key (student_id) 
       references student_profiles (id);

    alter table quiz_options 
       add constraint FKhkuvmd7qk1lmtq3hy9htpinkr 
       foreign key (question_id) 
       references quiz_questions (id);

    alter table quiz_questions 
       add constraint FKanfmgf6ksbdnv7ojb0pfve54q 
       foreign key (quiz_id) 
       references quizzes (id);

    alter table quizzes 
       add constraint FKbdv8uggpsin6pnkx0d80ryqey 
       foreign key (lesson_id) 
       references lessons (id);

    alter table quizzes 
       add constraint FKh8m8lpcfawschgw6url6b13yy 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table recorded_lesson_videos 
       add constraint FKnts1h6dxmcj5d8616pnkyh4vw 
       foreign key (course_package_id) 
       references course_packages (id);

    alter table recorded_lesson_videos 
       add constraint FKcqo8auork0arjxayvfqxhbsx0 
       foreign key (lesson_id) 
       references lessons (id);

    alter table recorded_lesson_videos 
       add constraint FKkt22yac011o76sonbnlcoounr 
       foreign key (uploaded_file_id) 
       references uploaded_files (id);

    alter table refund_requests 
       add constraint FK452xm7hwgngbanwkdgs3601b1 
       foreign key (order_id) 
       references orders (id);

    alter table refund_requests 
       add constraint FK6o8wign29iswkr3qm1587jv0m 
       foreign key (student_id) 
       references student_profiles (id);

    alter table story_activities 
       add constraint FKh522tgfw3txujcfbdl4k5bod7 
       foreign key (created_by_user_id) 
       references users (id);

    alter table story_questions 
       add constraint FK5nweaviksxxxqeh6vyrfi9l83 
       foreign key (story_activity_id) 
       references story_activities (id);

    alter table student_quiz_progress 
       add constraint FK_student_quiz_progress_lesson 
       foreign key (lesson_id) 
       references lessons (id);

    alter table student_quiz_progress 
       add constraint FK_student_quiz_progress_student 
       foreign key (student_id) 
       references student_profiles (id);

    alter table student_badges 
       add constraint FKdogjuytddp97f0p6ivt3r8ufp 
       foreign key (badge_id) 
       references badges (id);

    alter table student_badges 
       add constraint FKfgx9txqmkltpu2d9y9xgx0pr4 
       foreign key (student_id) 
       references student_profiles (id);

    alter table student_profiles 
       add constraint FK8vj7yk20fpq5pwu7jxetmakkw 
       foreign key (current_level_id) 
       references learning_levels (id);

    alter table student_profiles 
       add constraint FK32koy3tgqtaujxhfsn0b9pel2 
       foreign key (user_id) 
       references users (id);

    alter table student_progress 
       add constraint FK69wlpjqe62x50s7c0a9xoekcg 
       foreign key (level_id) 
       references learning_levels (id);

    alter table student_progress 
       add constraint FK2xterun7tk7mkv475kk97foju 
       foreign key (student_id) 
       references student_profiles (id);

    alter table teacher_earnings 
       add constraint FKh0ecr76qq1quwldf7lapak879 
       foreign key (order_item_id) 
       references order_items (id);

    alter table teacher_earnings 
       add constraint FK783nfvb4pi53sy3fly7rsso9r 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table teacher_feedback 
       add constraint FK20dqiw55c8g861irt3ss17gei 
       foreign key (lesson_id) 
       references lessons (id);

    alter table teacher_feedback 
       add constraint FK9hwlmq73lsoic18x7vl3hlinj 
       foreign key (quiz_attempt_id) 
       references quiz_attempts (id);

    alter table teacher_feedback 
       add constraint FKfncou1297ai4q4x7a3w7pk4m1 
       foreign key (student_id) 
       references student_profiles (id);

    alter table teacher_feedback 
       add constraint FKo45jtk4pyheie3uoeig54rnar 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table teacher_profiles 
       add constraint FK38hlnhfrimybow0wdfwnsfhew 
       foreign key (user_id) 
       references users (id);

    alter table teacher_verification_documents 
       add constraint FKcv0uukr5souwo3d23ml3xwtpl 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table teacher_verification_documents 
       add constraint FKs9i8og0090d99nx3qat7k2p75 
       foreign key (uploaded_file_id) 
       references uploaded_files (id);

    alter table teacher_verification_reviews 
       add constraint FKh4ivqeg71ua59mo29wbjl4bju 
       foreign key (document_id) 
       references teacher_verification_documents (id);

    alter table teacher_verification_reviews 
       add constraint FKbop7rx4hlfrk7rc9qhs1xwd0r 
       foreign key (reviewer_user_id) 
       references users (id);

    alter table teacher_verification_reviews 
       add constraint FKecrytd9lvfoe64ksxxgvrgk0n 
       foreign key (teacher_id) 
       references teacher_profiles (id);

    alter table tracing_exercises 
       add constraint FK87jluuqqc6mam1kxbthcy4qvm 
       foreign key (created_by_user_id) 
       references users (id);

    alter table uploaded_files 
       add constraint FKfg64d98iapftr6cur53s2kbuv 
       foreign key (owner_user_id) 
       references users (id);

    alter table user_roles 
       add constraint FKh8ciramu9cc9q3qcqiv4ue8a6 
       foreign key (role_id) 
       references roles (id);

    alter table user_roles 
       add constraint FKhfh9dx7w3ubf1co1vdev94g3f 
       foreign key (user_id) 
       references users (id);

    alter table video_progress 
       add constraint FK5uinmc3mesxfs52gcr3a076bf 
       foreign key (lesson_id) 
       references lessons (id);

    alter table video_progress 
       add constraint FKdjbxt2h00nn3l4ckqkc0f92mo 
       foreign key (recorded_video_id) 
       references recorded_lesson_videos (id);

    alter table video_progress 
       add constraint FKfhbi17gox8advjbaqxqyf0b5w 
       foreign key (student_id) 
       references student_profiles (id);


-- ===== Section: ai response reports =====
create table ai_response_reports (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    student_user_id bigint not null,
    ai_usage_log_id bigint,
    chat_session_id bigint,
    chat_message_id bigint,
    reviewer_user_id bigint,
    reviewed_at datetime(6),
    status varchar(30) not null,
    reason_category varchar(40) not null,
    feature_key varchar(100) not null,
    description varchar(1000),
    reviewer_note varchar(1000),
    ai_response_snapshot LONGTEXT not null,
    prompt_snapshot LONGTEXT,
    primary key (id)
) engine=InnoDB;

create index idx_ai_response_reports_student_user_id
    on ai_response_reports (student_user_id);

create index idx_ai_response_reports_status
    on ai_response_reports (status);

create index idx_ai_response_reports_feature_key
    on ai_response_reports (feature_key);

create index idx_ai_response_reports_usage_log_id
    on ai_response_reports (ai_usage_log_id);

create index idx_ai_response_reports_chat_session_id
    on ai_response_reports (chat_session_id);

alter table ai_response_reports
    add constraint fk_ai_response_reports_student_user_id
    foreign key (student_user_id)
    references users (id);

alter table ai_response_reports
    add constraint fk_ai_response_reports_usage_log_id
    foreign key (ai_usage_log_id)
    references ai_usage_logs (id);

alter table ai_response_reports
    add constraint fk_ai_response_reports_chat_session_id
    foreign key (chat_session_id)
    references ai_chat_sessions (id);

alter table ai_response_reports
    add constraint fk_ai_response_reports_chat_message_id
    foreign key (chat_message_id)
    references ai_chat_messages (id);

alter table ai_response_reports
    add constraint fk_ai_response_reports_reviewer_user_id
    foreign key (reviewer_user_id)
    references users (id);


-- ===== Section: legacy lesson activity cleanup =====
drop table if exists lesson_activities;


-- ===== Section: package item activity cleanup =====
set @delete_legacy_activity_package_items = if(
    (
        select count(*)
        from information_schema.columns
        where table_schema = database()
          and table_name = 'package_items'
          and column_name = 'item_type'
          and column_type like '%''ACTIVITY''%'
    ) > 0,
    'delete from package_items where item_type = ''ACTIVITY''',
    'select 1'
);

prepare delete_legacy_activity_package_items_stmt from @delete_legacy_activity_package_items;
execute delete_legacy_activity_package_items_stmt;
deallocate prepare delete_legacy_activity_package_items_stmt;

set @delete_legacy_student_activity_progress = if(
    (
        select count(*)
        from information_schema.tables
        where table_schema = database()
          and table_name = 'student_activity_progress'
    ) > 0,
    'delete from student_activity_progress where activity_type <> ''QUIZ''',
    'select 1'
);

prepare delete_legacy_student_activity_progress_stmt from @delete_legacy_student_activity_progress;
execute delete_legacy_student_activity_progress_stmt;
deallocate prepare delete_legacy_student_activity_progress_stmt;

alter table package_items
    modify column item_type enum ('MODULE','LESSON') not null;


-- ===== Section: student quiz progress consolidation =====
create table if not exists student_quiz_progress (
    attempt_count integer not null,
    completed bit not null,
    completed_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    last_interacted_at datetime(6),
    lesson_id bigint not null,
    quiz_id bigint not null,
    started_at datetime(6),
    student_id bigint not null,
    updated_at datetime(6) not null,
    completion_summary varchar(500),
    primary key (id)
) engine=InnoDB;

set @copy_legacy_quiz_progress = if(
    (
        select count(*)
        from information_schema.tables
        where table_schema = database()
          and table_name = 'student_activity_progress'
    ) > 0,
    'insert into student_quiz_progress (
        attempt_count,
        completed,
        completed_at,
        created_at,
        last_interacted_at,
        lesson_id,
        quiz_id,
        started_at,
        student_id,
        updated_at,
        completion_summary
    )
    select
        attempt_count,
        completed,
        completed_at,
        created_at,
        last_interacted_at,
        lesson_id,
        activity_source_id,
        started_at,
        student_id,
        updated_at,
        completion_summary
    from student_activity_progress
    where activity_type = ''QUIZ''
      and not exists (
          select 1
          from student_quiz_progress sqp
          where sqp.student_id = student_activity_progress.student_id
            and sqp.quiz_id = student_activity_progress.activity_source_id
      )',
    'select 1'
);

prepare copy_legacy_quiz_progress_stmt from @copy_legacy_quiz_progress;
execute copy_legacy_quiz_progress_stmt;
deallocate prepare copy_legacy_quiz_progress_stmt;

set @create_student_quiz_progress_student_index = if(
    (
        select count(*)
        from information_schema.statistics
        where table_schema = database()
          and table_name = 'student_quiz_progress'
          and index_name = 'idx_student_quiz_progress_student_id'
    ) = 0,
    'create index idx_student_quiz_progress_student_id on student_quiz_progress (student_id)',
    'select 1'
);

prepare create_student_quiz_progress_student_index_stmt from @create_student_quiz_progress_student_index;
execute create_student_quiz_progress_student_index_stmt;
deallocate prepare create_student_quiz_progress_student_index_stmt;

set @create_student_quiz_progress_lesson_index = if(
    (
        select count(*)
        from information_schema.statistics
        where table_schema = database()
          and table_name = 'student_quiz_progress'
          and index_name = 'idx_student_quiz_progress_lesson_id'
    ) = 0,
    'create index idx_student_quiz_progress_lesson_id on student_quiz_progress (lesson_id)',
    'select 1'
);

prepare create_student_quiz_progress_lesson_index_stmt from @create_student_quiz_progress_lesson_index;
execute create_student_quiz_progress_lesson_index_stmt;
deallocate prepare create_student_quiz_progress_lesson_index_stmt;

set @create_student_quiz_progress_quiz_index = if(
    (
        select count(*)
        from information_schema.statistics
        where table_schema = database()
          and table_name = 'student_quiz_progress'
          and index_name = 'idx_student_quiz_progress_quiz_id'
    ) = 0,
    'create index idx_student_quiz_progress_quiz_id on student_quiz_progress (quiz_id)',
    'select 1'
);

prepare create_student_quiz_progress_quiz_index_stmt from @create_student_quiz_progress_quiz_index;
execute create_student_quiz_progress_quiz_index_stmt;
deallocate prepare create_student_quiz_progress_quiz_index_stmt;

set @create_student_quiz_progress_unique = if(
    (
        select count(*)
        from information_schema.table_constraints
        where table_schema = database()
          and table_name = 'student_quiz_progress'
          and constraint_name = 'uk_student_quiz_progress_student_quiz'
    ) = 0,
    'alter table student_quiz_progress add constraint uk_student_quiz_progress_student_quiz unique (student_id, quiz_id)',
    'select 1'
);

prepare create_student_quiz_progress_unique_stmt from @create_student_quiz_progress_unique;
execute create_student_quiz_progress_unique_stmt;
deallocate prepare create_student_quiz_progress_unique_stmt;

set @create_student_quiz_progress_lesson_fk = if(
    (
        select count(*)
        from information_schema.table_constraints
        where table_schema = database()
          and table_name = 'student_quiz_progress'
          and constraint_name = 'FK_student_quiz_progress_lesson'
    ) = 0,
    'alter table student_quiz_progress add constraint FK_student_quiz_progress_lesson foreign key (lesson_id) references lessons (id)',
    'select 1'
);

prepare create_student_quiz_progress_lesson_fk_stmt from @create_student_quiz_progress_lesson_fk;
execute create_student_quiz_progress_lesson_fk_stmt;
deallocate prepare create_student_quiz_progress_lesson_fk_stmt;

set @create_student_quiz_progress_student_fk = if(
    (
        select count(*)
        from information_schema.table_constraints
        where table_schema = database()
          and table_name = 'student_quiz_progress'
          and constraint_name = 'FK_student_quiz_progress_student'
    ) = 0,
    'alter table student_quiz_progress add constraint FK_student_quiz_progress_student foreign key (student_id) references student_profiles (id)',
    'select 1'
);

prepare create_student_quiz_progress_student_fk_stmt from @create_student_quiz_progress_student_fk;
execute create_student_quiz_progress_student_fk_stmt;
deallocate prepare create_student_quiz_progress_student_fk_stmt;

drop table if exists student_activity_progress;


-- ===== Section: activity library schema sync =====
set @add_drag_drop_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'created_by_user_id') = 0,
    'alter table drag_drop_activities add column created_by_user_id bigint',
    'select 1'
);
prepare add_drag_drop_created_by_user_id_stmt from @add_drag_drop_created_by_user_id;
execute add_drag_drop_created_by_user_id_stmt;
deallocate prepare add_drag_drop_created_by_user_id_stmt;

set @add_drag_drop_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'estimated_seconds') = 0,
    'alter table drag_drop_activities add column estimated_seconds integer',
    'select 1'
);
prepare add_drag_drop_estimated_seconds_stmt from @add_drag_drop_estimated_seconds;
execute add_drag_drop_estimated_seconds_stmt;
deallocate prepare add_drag_drop_estimated_seconds_stmt;

set @add_drag_drop_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'hsk_level') = 0,
    'alter table drag_drop_activities add column hsk_level integer',
    'select 1'
);
prepare add_drag_drop_hsk_level_stmt from @add_drag_drop_hsk_level;
execute add_drag_drop_hsk_level_stmt;
deallocate prepare add_drag_drop_hsk_level_stmt;

set @add_drag_drop_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'xp_reward') = 0,
    'alter table drag_drop_activities add column xp_reward integer not null default 15',
    'select 1'
);
prepare add_drag_drop_xp_reward_stmt from @add_drag_drop_xp_reward;
execute add_drag_drop_xp_reward_stmt;
deallocate prepare add_drag_drop_xp_reward_stmt;

set @add_drag_drop_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'topic') = 0,
    'alter table drag_drop_activities add column topic varchar(100)',
    'select 1'
);
prepare add_drag_drop_topic_stmt from @add_drag_drop_topic;
execute add_drag_drop_topic_stmt;
deallocate prepare add_drag_drop_topic_stmt;

set @add_drag_drop_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'tags') = 0,
    'alter table drag_drop_activities add column tags varchar(500)',
    'select 1'
);
prepare add_drag_drop_tags_stmt from @add_drag_drop_tags;
execute add_drag_drop_tags_stmt;
deallocate prepare add_drag_drop_tags_stmt;

set @add_drag_drop_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'age_band') = 0,
    'alter table drag_drop_activities add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_drag_drop_age_band_stmt from @add_drag_drop_age_band;
execute add_drag_drop_age_band_stmt;
deallocate prepare add_drag_drop_age_band_stmt;

set @add_drag_drop_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'difficulty') = 0,
    'alter table drag_drop_activities add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_drag_drop_difficulty_stmt from @add_drag_drop_difficulty;
execute add_drag_drop_difficulty_stmt;
deallocate prepare add_drag_drop_difficulty_stmt;

set @add_flashcards_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'created_by_user_id') = 0,
    'alter table flashcards add column created_by_user_id bigint',
    'select 1'
);
prepare add_flashcards_created_by_user_id_stmt from @add_flashcards_created_by_user_id;
execute add_flashcards_created_by_user_id_stmt;
deallocate prepare add_flashcards_created_by_user_id_stmt;

set @add_flashcards_title = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'title') = 0,
    'alter table flashcards add column title varchar(200)',
    'select 1'
);
prepare add_flashcards_title_stmt from @add_flashcards_title;
execute add_flashcards_title_stmt;
deallocate prepare add_flashcards_title_stmt;

set @add_flashcards_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'estimated_seconds') = 0,
    'alter table flashcards add column estimated_seconds integer',
    'select 1'
);
prepare add_flashcards_estimated_seconds_stmt from @add_flashcards_estimated_seconds;
execute add_flashcards_estimated_seconds_stmt;
deallocate prepare add_flashcards_estimated_seconds_stmt;

set @add_flashcards_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'hsk_level') = 0,
    'alter table flashcards add column hsk_level integer',
    'select 1'
);
prepare add_flashcards_hsk_level_stmt from @add_flashcards_hsk_level;
execute add_flashcards_hsk_level_stmt;
deallocate prepare add_flashcards_hsk_level_stmt;

set @add_flashcards_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'xp_reward') = 0,
    'alter table flashcards add column xp_reward integer not null default 10',
    'select 1'
);
prepare add_flashcards_xp_reward_stmt from @add_flashcards_xp_reward;
execute add_flashcards_xp_reward_stmt;
deallocate prepare add_flashcards_xp_reward_stmt;

set @add_flashcards_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'topic') = 0,
    'alter table flashcards add column topic varchar(100)',
    'select 1'
);
prepare add_flashcards_topic_stmt from @add_flashcards_topic;
execute add_flashcards_topic_stmt;
deallocate prepare add_flashcards_topic_stmt;

set @add_flashcards_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'tags') = 0,
    'alter table flashcards add column tags varchar(500)',
    'select 1'
);
prepare add_flashcards_tags_stmt from @add_flashcards_tags;
execute add_flashcards_tags_stmt;
deallocate prepare add_flashcards_tags_stmt;

set @add_flashcards_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'age_band') = 0,
    'alter table flashcards add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_flashcards_age_band_stmt from @add_flashcards_age_band;
execute add_flashcards_age_band_stmt;
deallocate prepare add_flashcards_age_band_stmt;

set @add_flashcards_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'difficulty') = 0,
    'alter table flashcards add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_flashcards_difficulty_stmt from @add_flashcards_difficulty;
execute add_flashcards_difficulty_stmt;
deallocate prepare add_flashcards_difficulty_stmt;

set @add_listening_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'created_by_user_id') = 0,
    'alter table listening_exercises add column created_by_user_id bigint',
    'select 1'
);
prepare add_listening_created_by_user_id_stmt from @add_listening_created_by_user_id;
execute add_listening_created_by_user_id_stmt;
deallocate prepare add_listening_created_by_user_id_stmt;

set @add_listening_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'estimated_seconds') = 0,
    'alter table listening_exercises add column estimated_seconds integer',
    'select 1'
);
prepare add_listening_estimated_seconds_stmt from @add_listening_estimated_seconds;
execute add_listening_estimated_seconds_stmt;
deallocate prepare add_listening_estimated_seconds_stmt;

set @add_listening_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'hsk_level') = 0,
    'alter table listening_exercises add column hsk_level integer',
    'select 1'
);
prepare add_listening_hsk_level_stmt from @add_listening_hsk_level;
execute add_listening_hsk_level_stmt;
deallocate prepare add_listening_hsk_level_stmt;

set @add_listening_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'xp_reward') = 0,
    'alter table listening_exercises add column xp_reward integer not null default 15',
    'select 1'
);
prepare add_listening_xp_reward_stmt from @add_listening_xp_reward;
execute add_listening_xp_reward_stmt;
deallocate prepare add_listening_xp_reward_stmt;

set @add_listening_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'topic') = 0,
    'alter table listening_exercises add column topic varchar(100)',
    'select 1'
);
prepare add_listening_topic_stmt from @add_listening_topic;
execute add_listening_topic_stmt;
deallocate prepare add_listening_topic_stmt;

set @add_listening_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'tags') = 0,
    'alter table listening_exercises add column tags varchar(500)',
    'select 1'
);
prepare add_listening_tags_stmt from @add_listening_tags;
execute add_listening_tags_stmt;
deallocate prepare add_listening_tags_stmt;

set @add_listening_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'age_band') = 0,
    'alter table listening_exercises add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_listening_age_band_stmt from @add_listening_age_band;
execute add_listening_age_band_stmt;
deallocate prepare add_listening_age_band_stmt;

set @add_listening_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'difficulty') = 0,
    'alter table listening_exercises add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_listening_difficulty_stmt from @add_listening_difficulty;
execute add_listening_difficulty_stmt;
deallocate prepare add_listening_difficulty_stmt;

set @add_matching_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'created_by_user_id') = 0,
    'alter table matching_games add column created_by_user_id bigint',
    'select 1'
);
prepare add_matching_created_by_user_id_stmt from @add_matching_created_by_user_id;
execute add_matching_created_by_user_id_stmt;
deallocate prepare add_matching_created_by_user_id_stmt;

set @add_matching_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'estimated_seconds') = 0,
    'alter table matching_games add column estimated_seconds integer',
    'select 1'
);
prepare add_matching_estimated_seconds_stmt from @add_matching_estimated_seconds;
execute add_matching_estimated_seconds_stmt;
deallocate prepare add_matching_estimated_seconds_stmt;

set @add_matching_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'hsk_level') = 0,
    'alter table matching_games add column hsk_level integer',
    'select 1'
);
prepare add_matching_hsk_level_stmt from @add_matching_hsk_level;
execute add_matching_hsk_level_stmt;
deallocate prepare add_matching_hsk_level_stmt;

set @add_matching_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'xp_reward') = 0,
    'alter table matching_games add column xp_reward integer not null default 15',
    'select 1'
);
prepare add_matching_xp_reward_stmt from @add_matching_xp_reward;
execute add_matching_xp_reward_stmt;
deallocate prepare add_matching_xp_reward_stmt;

set @add_matching_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'topic') = 0,
    'alter table matching_games add column topic varchar(100)',
    'select 1'
);
prepare add_matching_topic_stmt from @add_matching_topic;
execute add_matching_topic_stmt;
deallocate prepare add_matching_topic_stmt;

set @add_matching_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'tags') = 0,
    'alter table matching_games add column tags varchar(500)',
    'select 1'
);
prepare add_matching_tags_stmt from @add_matching_tags;
execute add_matching_tags_stmt;
deallocate prepare add_matching_tags_stmt;

set @add_matching_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'age_band') = 0,
    'alter table matching_games add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_matching_age_band_stmt from @add_matching_age_band;
execute add_matching_age_band_stmt;
deallocate prepare add_matching_age_band_stmt;

set @add_matching_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'difficulty') = 0,
    'alter table matching_games add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_matching_difficulty_stmt from @add_matching_difficulty;
execute add_matching_difficulty_stmt;
deallocate prepare add_matching_difficulty_stmt;

set @add_story_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'created_by_user_id') = 0,
    'alter table story_activities add column created_by_user_id bigint',
    'select 1'
);
prepare add_story_created_by_user_id_stmt from @add_story_created_by_user_id;
execute add_story_created_by_user_id_stmt;
deallocate prepare add_story_created_by_user_id_stmt;

set @add_story_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'estimated_seconds') = 0,
    'alter table story_activities add column estimated_seconds integer',
    'select 1'
);
prepare add_story_estimated_seconds_stmt from @add_story_estimated_seconds;
execute add_story_estimated_seconds_stmt;
deallocate prepare add_story_estimated_seconds_stmt;

set @add_story_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'hsk_level') = 0,
    'alter table story_activities add column hsk_level integer',
    'select 1'
);
prepare add_story_hsk_level_stmt from @add_story_hsk_level;
execute add_story_hsk_level_stmt;
deallocate prepare add_story_hsk_level_stmt;

set @add_story_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'xp_reward') = 0,
    'alter table story_activities add column xp_reward integer not null default 20',
    'select 1'
);
prepare add_story_xp_reward_stmt from @add_story_xp_reward;
execute add_story_xp_reward_stmt;
deallocate prepare add_story_xp_reward_stmt;

set @add_story_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'topic') = 0,
    'alter table story_activities add column topic varchar(100)',
    'select 1'
);
prepare add_story_topic_stmt from @add_story_topic;
execute add_story_topic_stmt;
deallocate prepare add_story_topic_stmt;

set @add_story_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'tags') = 0,
    'alter table story_activities add column tags varchar(500)',
    'select 1'
);
prepare add_story_tags_stmt from @add_story_tags;
execute add_story_tags_stmt;
deallocate prepare add_story_tags_stmt;

set @add_story_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'age_band') = 0,
    'alter table story_activities add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_story_age_band_stmt from @add_story_age_band;
execute add_story_age_band_stmt;
deallocate prepare add_story_age_band_stmt;

set @add_story_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'difficulty') = 0,
    'alter table story_activities add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_story_difficulty_stmt from @add_story_difficulty;
execute add_story_difficulty_stmt;
deallocate prepare add_story_difficulty_stmt;

set @add_tracing_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'created_by_user_id') = 0,
    'alter table tracing_exercises add column created_by_user_id bigint',
    'select 1'
);
prepare add_tracing_created_by_user_id_stmt from @add_tracing_created_by_user_id;
execute add_tracing_created_by_user_id_stmt;
deallocate prepare add_tracing_created_by_user_id_stmt;

set @add_tracing_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'estimated_seconds') = 0,
    'alter table tracing_exercises add column estimated_seconds integer',
    'select 1'
);
prepare add_tracing_estimated_seconds_stmt from @add_tracing_estimated_seconds;
execute add_tracing_estimated_seconds_stmt;
deallocate prepare add_tracing_estimated_seconds_stmt;

set @add_tracing_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'hsk_level') = 0,
    'alter table tracing_exercises add column hsk_level integer',
    'select 1'
);
prepare add_tracing_hsk_level_stmt from @add_tracing_hsk_level;
execute add_tracing_hsk_level_stmt;
deallocate prepare add_tracing_hsk_level_stmt;

set @add_tracing_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'xp_reward') = 0,
    'alter table tracing_exercises add column xp_reward integer not null default 12',
    'select 1'
);
prepare add_tracing_xp_reward_stmt from @add_tracing_xp_reward;
execute add_tracing_xp_reward_stmt;
deallocate prepare add_tracing_xp_reward_stmt;

set @add_tracing_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'topic') = 0,
    'alter table tracing_exercises add column topic varchar(100)',
    'select 1'
);
prepare add_tracing_topic_stmt from @add_tracing_topic;
execute add_tracing_topic_stmt;
deallocate prepare add_tracing_topic_stmt;

set @add_tracing_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'tags') = 0,
    'alter table tracing_exercises add column tags varchar(500)',
    'select 1'
);
prepare add_tracing_tags_stmt from @add_tracing_tags;
execute add_tracing_tags_stmt;
deallocate prepare add_tracing_tags_stmt;

set @add_tracing_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'age_band') = 0,
    'alter table tracing_exercises add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_tracing_age_band_stmt from @add_tracing_age_band;
execute add_tracing_age_band_stmt;
deallocate prepare add_tracing_age_band_stmt;

set @add_tracing_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'difficulty') = 0,
    'alter table tracing_exercises add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_tracing_difficulty_stmt from @add_tracing_difficulty;
execute add_tracing_difficulty_stmt;
deallocate prepare add_tracing_difficulty_stmt;

set @drag_drop_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_drag_drop_lesson_fk = if(@drag_drop_lesson_fk is not null, concat('alter table drag_drop_activities drop foreign key `', @drag_drop_lesson_fk, '`'), 'select 1');
prepare drop_drag_drop_lesson_fk_stmt from @drop_drag_drop_lesson_fk;
execute drop_drag_drop_lesson_fk_stmt;
deallocate prepare drop_drag_drop_lesson_fk_stmt;

set @drop_drag_drop_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'lesson_id') > 0,
    'alter table drag_drop_activities drop column lesson_id',
    'select 1'
);
prepare drop_drag_drop_lesson_id_stmt from @drop_drag_drop_lesson_id;
execute drop_drag_drop_lesson_id_stmt;
deallocate prepare drop_drag_drop_lesson_id_stmt;

set @flashcards_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'flashcards' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_flashcards_lesson_fk = if(@flashcards_lesson_fk is not null, concat('alter table flashcards drop foreign key `', @flashcards_lesson_fk, '`'), 'select 1');
prepare drop_flashcards_lesson_fk_stmt from @drop_flashcards_lesson_fk;
execute drop_flashcards_lesson_fk_stmt;
deallocate prepare drop_flashcards_lesson_fk_stmt;

set @drop_flashcards_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'lesson_id') > 0,
    'alter table flashcards drop column lesson_id',
    'select 1'
);
prepare drop_flashcards_lesson_id_stmt from @drop_flashcards_lesson_id;
execute drop_flashcards_lesson_id_stmt;
deallocate prepare drop_flashcards_lesson_id_stmt;

set @listening_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'listening_exercises' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_listening_lesson_fk = if(@listening_lesson_fk is not null, concat('alter table listening_exercises drop foreign key `', @listening_lesson_fk, '`'), 'select 1');
prepare drop_listening_lesson_fk_stmt from @drop_listening_lesson_fk;
execute drop_listening_lesson_fk_stmt;
deallocate prepare drop_listening_lesson_fk_stmt;

set @drop_listening_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'lesson_id') > 0,
    'alter table listening_exercises drop column lesson_id',
    'select 1'
);
prepare drop_listening_lesson_id_stmt from @drop_listening_lesson_id;
execute drop_listening_lesson_id_stmt;
deallocate prepare drop_listening_lesson_id_stmt;

set @matching_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'matching_games' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_matching_lesson_fk = if(@matching_lesson_fk is not null, concat('alter table matching_games drop foreign key `', @matching_lesson_fk, '`'), 'select 1');
prepare drop_matching_lesson_fk_stmt from @drop_matching_lesson_fk;
execute drop_matching_lesson_fk_stmt;
deallocate prepare drop_matching_lesson_fk_stmt;

set @drop_matching_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'lesson_id') > 0,
    'alter table matching_games drop column lesson_id',
    'select 1'
);
prepare drop_matching_lesson_id_stmt from @drop_matching_lesson_id;
execute drop_matching_lesson_id_stmt;
deallocate prepare drop_matching_lesson_id_stmt;

set @story_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'story_activities' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_story_lesson_fk = if(@story_lesson_fk is not null, concat('alter table story_activities drop foreign key `', @story_lesson_fk, '`'), 'select 1');
prepare drop_story_lesson_fk_stmt from @drop_story_lesson_fk;
execute drop_story_lesson_fk_stmt;
deallocate prepare drop_story_lesson_fk_stmt;

set @drop_story_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'lesson_id') > 0,
    'alter table story_activities drop column lesson_id',
    'select 1'
);
prepare drop_story_lesson_id_stmt from @drop_story_lesson_id;
execute drop_story_lesson_id_stmt;
deallocate prepare drop_story_lesson_id_stmt;

set @tracing_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_tracing_lesson_fk = if(@tracing_lesson_fk is not null, concat('alter table tracing_exercises drop foreign key `', @tracing_lesson_fk, '`'), 'select 1');
prepare drop_tracing_lesson_fk_stmt from @drop_tracing_lesson_fk;
execute drop_tracing_lesson_fk_stmt;
deallocate prepare drop_tracing_lesson_fk_stmt;

set @drop_tracing_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'lesson_id') > 0,
    'alter table tracing_exercises drop column lesson_id',
    'select 1'
);
prepare drop_tracing_lesson_id_stmt from @drop_tracing_lesson_id;
execute drop_tracing_lesson_id_stmt;
deallocate prepare drop_tracing_lesson_id_stmt;

set @add_drag_drop_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_created_by_user_id') = 0,
    'create index idx_drag_drop_activities_created_by_user_id on drag_drop_activities (created_by_user_id)',
    'select 1'
);
prepare add_drag_drop_created_by_user_id_index_stmt from @add_drag_drop_created_by_user_id_index;
execute add_drag_drop_created_by_user_id_index_stmt;
deallocate prepare add_drag_drop_created_by_user_id_index_stmt;

set @add_drag_drop_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_hsk_level') = 0,
    'create index idx_drag_drop_activities_hsk_level on drag_drop_activities (hsk_level)',
    'select 1'
);
prepare add_drag_drop_hsk_level_index_stmt from @add_drag_drop_hsk_level_index;
execute add_drag_drop_hsk_level_index_stmt;
deallocate prepare add_drag_drop_hsk_level_index_stmt;

set @add_drag_drop_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_age_band') = 0,
    'create index idx_drag_drop_activities_age_band on drag_drop_activities (age_band)',
    'select 1'
);
prepare add_drag_drop_age_band_index_stmt from @add_drag_drop_age_band_index;
execute add_drag_drop_age_band_index_stmt;
deallocate prepare add_drag_drop_age_band_index_stmt;

set @add_drag_drop_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_difficulty') = 0,
    'create index idx_drag_drop_activities_difficulty on drag_drop_activities (difficulty)',
    'select 1'
);
prepare add_drag_drop_difficulty_index_stmt from @add_drag_drop_difficulty_index;
execute add_drag_drop_difficulty_index_stmt;
deallocate prepare add_drag_drop_difficulty_index_stmt;

set @add_flashcards_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_created_by_user_id') = 0,
    'create index idx_flashcards_created_by_user_id on flashcards (created_by_user_id)',
    'select 1'
);
prepare add_flashcards_created_by_user_id_index_stmt from @add_flashcards_created_by_user_id_index;
execute add_flashcards_created_by_user_id_index_stmt;
deallocate prepare add_flashcards_created_by_user_id_index_stmt;

set @add_flashcards_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_hsk_level') = 0,
    'create index idx_flashcards_hsk_level on flashcards (hsk_level)',
    'select 1'
);
prepare add_flashcards_hsk_level_index_stmt from @add_flashcards_hsk_level_index;
execute add_flashcards_hsk_level_index_stmt;
deallocate prepare add_flashcards_hsk_level_index_stmt;

set @add_flashcards_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_age_band') = 0,
    'create index idx_flashcards_age_band on flashcards (age_band)',
    'select 1'
);
prepare add_flashcards_age_band_index_stmt from @add_flashcards_age_band_index;
execute add_flashcards_age_band_index_stmt;
deallocate prepare add_flashcards_age_band_index_stmt;

set @add_flashcards_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_difficulty') = 0,
    'create index idx_flashcards_difficulty on flashcards (difficulty)',
    'select 1'
);
prepare add_flashcards_difficulty_index_stmt from @add_flashcards_difficulty_index;
execute add_flashcards_difficulty_index_stmt;
deallocate prepare add_flashcards_difficulty_index_stmt;

set @add_listening_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_created_by_user_id') = 0,
    'create index idx_listening_exercises_created_by_user_id on listening_exercises (created_by_user_id)',
    'select 1'
);
prepare add_listening_created_by_user_id_index_stmt from @add_listening_created_by_user_id_index;
execute add_listening_created_by_user_id_index_stmt;
deallocate prepare add_listening_created_by_user_id_index_stmt;

set @add_listening_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_hsk_level') = 0,
    'create index idx_listening_exercises_hsk_level on listening_exercises (hsk_level)',
    'select 1'
);
prepare add_listening_hsk_level_index_stmt from @add_listening_hsk_level_index;
execute add_listening_hsk_level_index_stmt;
deallocate prepare add_listening_hsk_level_index_stmt;

set @add_listening_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_age_band') = 0,
    'create index idx_listening_exercises_age_band on listening_exercises (age_band)',
    'select 1'
);
prepare add_listening_age_band_index_stmt from @add_listening_age_band_index;
execute add_listening_age_band_index_stmt;
deallocate prepare add_listening_age_band_index_stmt;

set @add_listening_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_difficulty') = 0,
    'create index idx_listening_exercises_difficulty on listening_exercises (difficulty)',
    'select 1'
);
prepare add_listening_difficulty_index_stmt from @add_listening_difficulty_index;
execute add_listening_difficulty_index_stmt;
deallocate prepare add_listening_difficulty_index_stmt;

set @add_matching_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_created_by_user_id') = 0,
    'create index idx_matching_games_created_by_user_id on matching_games (created_by_user_id)',
    'select 1'
);
prepare add_matching_created_by_user_id_index_stmt from @add_matching_created_by_user_id_index;
execute add_matching_created_by_user_id_index_stmt;
deallocate prepare add_matching_created_by_user_id_index_stmt;

set @add_matching_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_hsk_level') = 0,
    'create index idx_matching_games_hsk_level on matching_games (hsk_level)',
    'select 1'
);
prepare add_matching_hsk_level_index_stmt from @add_matching_hsk_level_index;
execute add_matching_hsk_level_index_stmt;
deallocate prepare add_matching_hsk_level_index_stmt;

set @add_matching_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_age_band') = 0,
    'create index idx_matching_games_age_band on matching_games (age_band)',
    'select 1'
);
prepare add_matching_age_band_index_stmt from @add_matching_age_band_index;
execute add_matching_age_band_index_stmt;
deallocate prepare add_matching_age_band_index_stmt;

set @add_matching_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_difficulty') = 0,
    'create index idx_matching_games_difficulty on matching_games (difficulty)',
    'select 1'
);
prepare add_matching_difficulty_index_stmt from @add_matching_difficulty_index;
execute add_matching_difficulty_index_stmt;
deallocate prepare add_matching_difficulty_index_stmt;

set @add_story_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_created_by_user_id') = 0,
    'create index idx_story_activities_created_by_user_id on story_activities (created_by_user_id)',
    'select 1'
);
prepare add_story_created_by_user_id_index_stmt from @add_story_created_by_user_id_index;
execute add_story_created_by_user_id_index_stmt;
deallocate prepare add_story_created_by_user_id_index_stmt;

set @add_story_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_hsk_level') = 0,
    'create index idx_story_activities_hsk_level on story_activities (hsk_level)',
    'select 1'
);
prepare add_story_hsk_level_index_stmt from @add_story_hsk_level_index;
execute add_story_hsk_level_index_stmt;
deallocate prepare add_story_hsk_level_index_stmt;

set @add_story_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_age_band') = 0,
    'create index idx_story_activities_age_band on story_activities (age_band)',
    'select 1'
);
prepare add_story_age_band_index_stmt from @add_story_age_band_index;
execute add_story_age_band_index_stmt;
deallocate prepare add_story_age_band_index_stmt;

set @add_story_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_difficulty') = 0,
    'create index idx_story_activities_difficulty on story_activities (difficulty)',
    'select 1'
);
prepare add_story_difficulty_index_stmt from @add_story_difficulty_index;
execute add_story_difficulty_index_stmt;
deallocate prepare add_story_difficulty_index_stmt;

set @add_tracing_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_created_by_user_id') = 0,
    'create index idx_tracing_exercises_created_by_user_id on tracing_exercises (created_by_user_id)',
    'select 1'
);
prepare add_tracing_created_by_user_id_index_stmt from @add_tracing_created_by_user_id_index;
execute add_tracing_created_by_user_id_index_stmt;
deallocate prepare add_tracing_created_by_user_id_index_stmt;

set @add_tracing_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_hsk_level') = 0,
    'create index idx_tracing_exercises_hsk_level on tracing_exercises (hsk_level)',
    'select 1'
);
prepare add_tracing_hsk_level_index_stmt from @add_tracing_hsk_level_index;
execute add_tracing_hsk_level_index_stmt;
deallocate prepare add_tracing_hsk_level_index_stmt;

set @add_tracing_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_age_band') = 0,
    'create index idx_tracing_exercises_age_band on tracing_exercises (age_band)',
    'select 1'
);
prepare add_tracing_age_band_index_stmt from @add_tracing_age_band_index;
execute add_tracing_age_band_index_stmt;
deallocate prepare add_tracing_age_band_index_stmt;

set @add_tracing_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_difficulty') = 0,
    'create index idx_tracing_exercises_difficulty on tracing_exercises (difficulty)',
    'select 1'
);
prepare add_tracing_difficulty_index_stmt from @add_tracing_difficulty_index;
execute add_tracing_difficulty_index_stmt;
deallocate prepare add_tracing_difficulty_index_stmt;

set @add_drag_drop_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'drag_drop_activities' and constraint_name = 'FKei5kgr46vhjn8qfk9a6d7a5yl') = 0,
    'alter table drag_drop_activities add constraint FKei5kgr46vhjn8qfk9a6d7a5yl foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_drag_drop_created_by_user_id_fk_stmt from @add_drag_drop_created_by_user_id_fk;
execute add_drag_drop_created_by_user_id_fk_stmt;
deallocate prepare add_drag_drop_created_by_user_id_fk_stmt;

set @add_flashcards_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'flashcards' and constraint_name = 'FKmss52266jmxyysm5uijhf63ie') = 0,
    'alter table flashcards add constraint FKmss52266jmxyysm5uijhf63ie foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_flashcards_created_by_user_id_fk_stmt from @add_flashcards_created_by_user_id_fk;
execute add_flashcards_created_by_user_id_fk_stmt;
deallocate prepare add_flashcards_created_by_user_id_fk_stmt;

set @add_listening_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'listening_exercises' and constraint_name = 'FKlo1t6skrjponktm3nck9suoww') = 0,
    'alter table listening_exercises add constraint FKlo1t6skrjponktm3nck9suoww foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_listening_created_by_user_id_fk_stmt from @add_listening_created_by_user_id_fk;
execute add_listening_created_by_user_id_fk_stmt;
deallocate prepare add_listening_created_by_user_id_fk_stmt;

set @add_matching_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'matching_games' and constraint_name = 'FK9qopobo92p8ga0io07cq6xbcr') = 0,
    'alter table matching_games add constraint FK9qopobo92p8ga0io07cq6xbcr foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_matching_created_by_user_id_fk_stmt from @add_matching_created_by_user_id_fk;
execute add_matching_created_by_user_id_fk_stmt;
deallocate prepare add_matching_created_by_user_id_fk_stmt;

set @add_story_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'story_activities' and constraint_name = 'FKh522tgfw3txujcfbdl4k5bod7') = 0,
    'alter table story_activities add constraint FKh522tgfw3txujcfbdl4k5bod7 foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_story_created_by_user_id_fk_stmt from @add_story_created_by_user_id_fk;
execute add_story_created_by_user_id_fk_stmt;
deallocate prepare add_story_created_by_user_id_fk_stmt;

set @add_tracing_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'tracing_exercises' and constraint_name = 'FK87jluuqqc6mam1kxbthcy4qvm') = 0,
    'alter table tracing_exercises add constraint FK87jluuqqc6mam1kxbthcy4qvm foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_tracing_created_by_user_id_fk_stmt from @add_tracing_created_by_user_id_fk;
execute add_tracing_created_by_user_id_fk_stmt;
deallocate prepare add_tracing_created_by_user_id_fk_stmt;


-- ===== Section: hot path indexes =====
set @add_payment_records_student_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'payment_records' and index_name = 'idx_payment_records_student_created_at') = 0,
    'create index idx_payment_records_student_created_at on payment_records (student_id, created_at)',
    'select 1'
);
prepare add_payment_records_student_created_at_index_stmt from @add_payment_records_student_created_at_index;
execute add_payment_records_student_created_at_index_stmt;
deallocate prepare add_payment_records_student_created_at_index_stmt;

set @add_refund_requests_student_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'refund_requests' and index_name = 'idx_refund_requests_student_created_at') = 0,
    'create index idx_refund_requests_student_created_at on refund_requests (student_id, created_at)',
    'select 1'
);
prepare add_refund_requests_student_created_at_index_stmt from @add_refund_requests_student_created_at_index;
execute add_refund_requests_student_created_at_index_stmt;
deallocate prepare add_refund_requests_student_created_at_index_stmt;

set @add_refund_requests_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'refund_requests' and index_name = 'idx_refund_requests_status_created_at') = 0,
    'create index idx_refund_requests_status_created_at on refund_requests (refund_status, created_at)',
    'select 1'
);
prepare add_refund_requests_status_created_at_index_stmt from @add_refund_requests_status_created_at_index;
execute add_refund_requests_status_created_at_index_stmt;
deallocate prepare add_refund_requests_status_created_at_index_stmt;

set @add_payout_requests_teacher_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'payout_requests' and index_name = 'idx_payout_requests_teacher_created_at') = 0,
    'create index idx_payout_requests_teacher_created_at on payout_requests (teacher_id, created_at)',
    'select 1'
);
prepare add_payout_requests_teacher_created_at_index_stmt from @add_payout_requests_teacher_created_at_index;
execute add_payout_requests_teacher_created_at_index_stmt;
deallocate prepare add_payout_requests_teacher_created_at_index_stmt;

set @add_payout_requests_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'payout_requests' and index_name = 'idx_payout_requests_status_created_at') = 0,
    'create index idx_payout_requests_status_created_at on payout_requests (payout_status, created_at)',
    'select 1'
);
prepare add_payout_requests_status_created_at_index_stmt from @add_payout_requests_status_created_at_index;
execute add_payout_requests_status_created_at_index_stmt;
deallocate prepare add_payout_requests_status_created_at_index_stmt;

set @add_teacher_earnings_teacher_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'teacher_earnings' and index_name = 'idx_teacher_earnings_teacher_created_at') = 0,
    'create index idx_teacher_earnings_teacher_created_at on teacher_earnings (teacher_id, created_at)',
    'select 1'
);
prepare add_teacher_earnings_teacher_created_at_index_stmt from @add_teacher_earnings_teacher_created_at_index;
execute add_teacher_earnings_teacher_created_at_index_stmt;
deallocate prepare add_teacher_earnings_teacher_created_at_index_stmt;

set @add_notifications_user_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'notifications' and index_name = 'idx_notifications_user_status_created_at') = 0,
    'create index idx_notifications_user_status_created_at on notifications (user_id, notification_status, created_at)',
    'select 1'
);
prepare add_notifications_user_status_created_at_index_stmt from @add_notifications_user_status_created_at_index;
execute add_notifications_user_status_created_at_index_stmt;
deallocate prepare add_notifications_user_status_created_at_index_stmt;

set @add_ai_response_reports_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'ai_response_reports' and index_name = 'idx_ai_response_reports_status_created_at') = 0,
    'create index idx_ai_response_reports_status_created_at on ai_response_reports (status, created_at)',
    'select 1'
);
prepare add_ai_response_reports_status_created_at_index_stmt from @add_ai_response_reports_status_created_at_index;
execute add_ai_response_reports_status_created_at_index_stmt;
deallocate prepare add_ai_response_reports_status_created_at_index_stmt;

set @add_ai_usage_logs_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'ai_usage_logs' and index_name = 'idx_ai_usage_logs_status_created_at') = 0,
    'create index idx_ai_usage_logs_status_created_at on ai_usage_logs (status, created_at)',
    'select 1'
);
prepare add_ai_usage_logs_status_created_at_index_stmt from @add_ai_usage_logs_status_created_at_index;
execute add_ai_usage_logs_status_created_at_index_stmt;
deallocate prepare add_ai_usage_logs_status_created_at_index_stmt;

set @add_audit_logs_action_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_action_created_at') = 0,
    'create index idx_audit_logs_action_created_at on audit_logs (action_type, created_at)',
    'select 1'
);
prepare add_audit_logs_action_created_at_index_stmt from @add_audit_logs_action_created_at_index;
execute add_audit_logs_action_created_at_index_stmt;
deallocate prepare add_audit_logs_action_created_at_index_stmt;

set @add_live_classes_teacher_status_start_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'live_classes' and index_name = 'idx_live_classes_teacher_status_start') = 0,
    'create index idx_live_classes_teacher_status_start on live_classes (teacher_id, live_class_status, scheduled_start_at)',
    'select 1'
);
prepare add_live_classes_teacher_status_start_index_stmt from @add_live_classes_teacher_status_start_index;
execute add_live_classes_teacher_status_start_index_stmt;
deallocate prepare add_live_classes_teacher_status_start_index_stmt;

set @add_recorded_videos_lesson_active_sort_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'recorded_lesson_videos' and index_name = 'idx_recorded_videos_lesson_active_sort') = 0,
    'create index idx_recorded_videos_lesson_active_sort on recorded_lesson_videos (lesson_id, active, sort_order)',
    'select 1'
);
prepare add_recorded_videos_lesson_active_sort_index_stmt from @add_recorded_videos_lesson_active_sort_index;
execute add_recorded_videos_lesson_active_sort_index_stmt;
deallocate prepare add_recorded_videos_lesson_active_sort_index_stmt;

set @add_learning_modules_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'learning_modules' and index_name = 'idx_learning_modules_status_created_at') = 0,
    'create index idx_learning_modules_status_created_at on learning_modules (content_status, created_at)',
    'select 1'
);
prepare add_learning_modules_status_created_at_index_stmt from @add_learning_modules_status_created_at_index;
execute add_learning_modules_status_created_at_index_stmt;
deallocate prepare add_learning_modules_status_created_at_index_stmt;

set @add_lessons_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'lessons' and index_name = 'idx_lessons_status_created_at') = 0,
    'create index idx_lessons_status_created_at on lessons (content_status, created_at)',
    'select 1'
);
prepare add_lessons_status_created_at_index_stmt from @add_lessons_status_created_at_index;
execute add_lessons_status_created_at_index_stmt;
deallocate prepare add_lessons_status_created_at_index_stmt;

set @add_course_packages_status_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'course_packages' and index_name = 'idx_course_packages_status_created_at') = 0,
    'create index idx_course_packages_status_created_at on course_packages (content_status, created_at)',
    'select 1'
);
prepare add_course_packages_status_created_at_index_stmt from @add_course_packages_status_created_at_index;
execute add_course_packages_status_created_at_index_stmt;
deallocate prepare add_course_packages_status_created_at_index_stmt;


-- ===== Section: financial integrity hardening =====
update orders set total_amount = 0.00 where total_amount is null;
update orders set currency_code = upper(trim(currency_code)) where currency_code is not null;
update order_items set unit_price = 0.00 where unit_price is null;
update order_items set line_total = 0.00 where line_total is null;
update payment_records set amount = 0.00 where amount is null;
update payment_records set currency_code = upper(trim(currency_code)) where currency_code is not null;
update refund_requests set amount = 0.00 where amount is null;
update teacher_earnings set gross_amount = 0.00 where gross_amount is null;
update teacher_earnings set commission_amount = 0.00 where commission_amount is null;
update teacher_earnings set net_amount = 0.00 where net_amount is null;

set @baseline_financial_orders_invalid_count = (
    select count(*) from orders
    where total_amount < 0 or currency_code is null or currency_code not regexp '^[A-Z]{3}$'
);
set @baseline_financial_sql = if(@baseline_financial_orders_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: orders contain negative totals or invalid currency codes''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_order_items_invalid_count = (
    select count(*) from order_items
    where quantity <= 0 or unit_price < 0 or line_total < 0
);
set @baseline_financial_sql = if(@baseline_financial_order_items_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: order_items contain non-positive quantities or negative amounts''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_payment_records_invalid_count = (
    select count(*) from payment_records
    where order_id is null or student_id is null or amount < 0 or currency_code is null or currency_code not regexp '^[A-Z]{3}$'
);
set @baseline_financial_sql = if(@baseline_financial_payment_records_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: payment_records contain missing owner/order, negative amounts, or invalid currency codes''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_payment_provider_duplicate_count = (
    select count(*) from (
        select provider_name, provider_reference
        from payment_records
        where provider_reference is not null
        group by provider_name, provider_reference
        having count(*) > 1
    ) duplicates
);
set @baseline_financial_sql = if(@baseline_financial_payment_provider_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate payment provider references exist''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_entitlements_invalid_count = (
    select count(*) from payment_entitlements
    where order_id is null or payment_record_id is null
);
set @baseline_financial_sql = if(@baseline_financial_entitlements_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: payment_entitlements contain missing order/payment links''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_entitlements_duplicate_count = (
    select count(*) from (
        select order_id, item_type, item_id, coalesce(feature_key, '') as feature_key_normalized
        from payment_entitlements
        group by order_id, item_type, item_id, coalesce(feature_key, '')
        having count(*) > 1
    ) duplicates
);
set @baseline_financial_sql = if(@baseline_financial_entitlements_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate payment entitlement tuples exist''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_refunds_invalid_count = (
    select count(*) from refund_requests
    where order_id is null or amount < 0
);
set @baseline_financial_sql = if(@baseline_financial_refunds_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: refund_requests contain missing orders or negative amounts''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_active_refund_duplicate_count = (
    select count(*) from (
        select order_id
        from refund_requests
        where refund_status in ('PENDING', 'APPROVED')
        group by order_id
        having count(*) > 1
    ) duplicates
);
set @baseline_financial_sql = if(@baseline_financial_active_refund_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: multiple active refund requests exist for an order''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_payouts_invalid_count = (
    select count(*) from payout_requests
    where amount is null or amount <= 0
);
set @baseline_financial_sql = if(@baseline_financial_payouts_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: payout_requests contain missing or non-positive amounts''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_teacher_earnings_invalid_count = (
    select count(*) from teacher_earnings
    where order_item_id is null
       or gross_amount < 0
       or commission_amount < 0
       or net_amount < 0
       or commission_amount > gross_amount
       or net_amount <> gross_amount - commission_amount
);
set @baseline_financial_sql = if(@baseline_financial_teacher_earnings_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: teacher_earnings contain missing order items or inconsistent amounts''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

set @baseline_financial_teacher_earnings_duplicate_count = (
    select count(*) from (
        select order_item_id
        from teacher_earnings
        group by order_item_id
        having count(*) > 1
    ) duplicates
);
set @baseline_financial_sql = if(@baseline_financial_teacher_earnings_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate teacher earning rows exist for an order item''');
prepare baseline_financial_stmt from @baseline_financial_sql;
execute baseline_financial_stmt;
deallocate prepare baseline_financial_stmt;

alter table payment_entitlements
    drop foreign key FK7d9y052u9mtl9qawornj5nwy7,
    drop foreign key FK7lm90rg92vjyw87e14w2jkwyx;

alter table payment_records
    drop foreign key FKcqu2d790mp868g1ogc678ygaj,
    drop foreign key FKqx0d20cubo6ak3qp79vdhpmnt;

alter table refund_requests
    drop foreign key FK452xm7hwgngbanwkdgs3601b1;

alter table teacher_earnings
    drop foreign key FKh0ecr76qq1quwldf7lapak879;

alter table orders
    add column version bigint not null default 0,
    modify total_amount decimal(10,2) not null,
    modify currency_code varchar(3) not null,
    add constraint chk_orders_total_amount_non_negative check (total_amount >= 0),
    add constraint chk_orders_currency_code_iso check (currency_code regexp '^[A-Z]{3}$');

alter table order_items
    modify unit_price decimal(10,2) not null,
    modify line_total decimal(10,2) not null,
    add constraint chk_order_items_quantity_positive check (quantity > 0),
    add constraint chk_order_items_unit_price_non_negative check (unit_price >= 0),
    add constraint chk_order_items_line_total_non_negative check (line_total >= 0);

alter table payment_records
    add column version bigint not null default 0,
    modify order_id bigint not null,
    modify student_id bigint not null,
    modify amount decimal(10,2) not null,
    modify currency_code varchar(3) not null,
    add constraint chk_payment_records_amount_non_negative check (amount >= 0),
    add constraint chk_payment_records_currency_code_iso check (currency_code regexp '^[A-Z]{3}$');

alter table payment_records
    add constraint uk_payment_records_provider_reference unique (provider_name, provider_reference);

alter table payment_entitlements
    add column version bigint not null default 0,
    modify order_id bigint not null,
    modify payment_record_id bigint not null,
    add column feature_key_normalized varchar(100) generated always as (coalesce(feature_key, '')) stored,
    add constraint uk_payment_entitlements_order_item_feature unique (order_id, item_type, item_id, feature_key_normalized);

alter table refund_requests
    add column version bigint not null default 0,
    modify order_id bigint not null,
    modify amount decimal(10,2) not null,
    add column active_refund_order_id bigint generated always as (case when refund_status in ('PENDING','APPROVED') then order_id else null end) stored,
    add constraint chk_refund_requests_amount_non_negative check (amount >= 0),
    add constraint uk_refund_requests_active_order unique (active_refund_order_id);

alter table payout_requests
    add column version bigint not null default 0,
    modify amount decimal(10,2) not null,
    add constraint chk_payout_requests_amount_positive check (amount > 0);

alter table teacher_earnings
    add column version bigint not null default 0,
    modify order_item_id bigint not null,
    modify gross_amount decimal(10,2) not null,
    modify commission_amount decimal(10,2) not null,
    modify net_amount decimal(10,2) not null,
    add constraint uk_teacher_earnings_order_item unique (order_item_id),
    add constraint chk_teacher_earnings_amounts_non_negative check (gross_amount >= 0 and commission_amount >= 0 and net_amount >= 0),
    add constraint chk_teacher_earnings_commission_not_above_gross check (commission_amount <= gross_amount),
    add constraint chk_teacher_earnings_net_matches_components check (net_amount = gross_amount - commission_amount);

alter table payment_records
    add constraint FKcqu2d790mp868g1ogc678ygaj foreign key (order_id) references orders (id),
    add constraint FKqx0d20cubo6ak3qp79vdhpmnt foreign key (student_id) references student_profiles (id);

alter table payment_entitlements
    add constraint FK7d9y052u9mtl9qawornj5nwy7 foreign key (order_id) references orders (id),
    add constraint FK7lm90rg92vjyw87e14w2jkwyx foreign key (payment_record_id) references payment_records (id);

alter table refund_requests
    add constraint FK452xm7hwgngbanwkdgs3601b1 foreign key (order_id) references orders (id);

alter table teacher_earnings
    add constraint FKh0ecr76qq1quwldf7lapak879 foreign key (order_item_id) references order_items (id);


-- ===== Section: audit request traceability =====
alter table audit_logs
    add column request_id varchar(120) null;

create index idx_audit_logs_request_id
    on audit_logs (request_id);


-- ===== Section: ai report snapshot retention minimization =====
alter table ai_response_reports
    modify column ai_response_snapshot LONGTEXT null;

update ai_response_reports
set prompt_snapshot = null,
    ai_response_snapshot = null
where chat_message_id is not null;


-- ===== Section: user session version =====
alter table users
    add column session_version bigint not null default 0;


-- ===== Section: database contracts and privacy bounds =====
update cart_items set unit_price = 0.00 where unit_price is null;
update ai_feedback_records
set input_text = concat('legacy_content_redacted_length=', char_length(input_text))
where input_text is not null and input_text not regexp '^(content_length|legacy_content_redacted_length)=';
update ai_feedback_records
set feedback_text = concat('legacy_content_redacted_length=', char_length(feedback_text))
where feedback_text is not null and feedback_text not regexp '^(content_length|legacy_content_redacted_length)=';
update ai_feedback_records set input_file_url = null where input_file_url is not null;

set @baseline_contract_cart_duplicate_count = (
    select count(*) from (
        select cart_id, item_type, item_id
        from cart_items
        group by cart_id, item_type, item_id
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_cart_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate cart item tuples exist''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_lesson_progress_duplicate_count = (
    select count(*) from (
        select student_id, lesson_id
        from lesson_progress
        group by student_id, lesson_id
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_lesson_progress_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate lesson progress tuples exist''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_student_progress_duplicate_count = (
    select count(*) from (
        select student_id
        from student_progress
        group by student_id
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_student_progress_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate student progress summaries exist''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_daily_streak_duplicate_count = (
    select count(*) from (
        select student_id
        from daily_streaks
        group by student_id
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_daily_streak_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate daily streak rows exist''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_points_duplicate_count = (
    select count(*) from (
        select student_id, source_type, source_id
        from points_transactions
        where source_type is not null and source_id is not null
        group by student_id, source_type, source_id
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_points_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate point-award source tuples exist''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_uploaded_duplicate_count = (
    select count(*) from (
        select stored_file_name
        from uploaded_files
        group by stored_file_name
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_uploaded_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: duplicate stored upload filenames exist''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_active_reset_token_duplicate_count = (
    select count(*) from (
        select user_id
        from password_reset_tokens
        where used_at is null
        group by user_id
        having count(*) > 1
    ) duplicates
);
set @baseline_contract_sql = if(@baseline_contract_active_reset_token_duplicate_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: multiple active password reset tokens exist for a user''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_invalid_count = (
    select
        (select count(*) from cart_items where quantity <= 0 or unit_price < 0)
      + (select count(*) from lesson_progress where completion_percent is not null and (completion_percent < 0 or completion_percent > 100))
      + (select count(*) from student_progress where completion_percent is not null and (completion_percent < 0 or completion_percent > 100))
      + (select count(*) from daily_streaks where current_streak < 0 or best_streak < 0 or best_streak < current_streak)
      + (select count(*) from points_transactions where points_delta < 0 or (source_type is null and source_id is not null) or (source_type is not null and source_id is null))
      + (select count(*) from video_progress where recorded_video_id is null or watched_seconds < 0 or total_seconds < 0 or progress_percentage < 0 or progress_percentage > 100 or watched_seconds > total_seconds)
      + (select count(*) from live_classes where max_participants is not null and max_participants <= 0)
      + (select count(*) from uploaded_files where file_size < 0 or access_level not in ('PRIVATE'))
      + (select count(*) from users where session_version < 0)
      + (select count(*) from password_reset_tokens where used_at is not null and used_at < created_at)
);
set @baseline_contract_sql = if(@baseline_contract_invalid_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: rows violate strengthened integrity checks''');
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select is_nullable from information_schema.columns where table_schema = database() and table_name = 'cart_items' and column_name = 'unit_price') = 'YES',
    'alter table cart_items modify unit_price decimal(10,2) not null',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'cart_items' and constraint_name = 'uk_cart_items_cart_item') = 0,
    'alter table cart_items add constraint uk_cart_items_cart_item unique (cart_id, item_type, item_id)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'lesson_progress' and column_name = 'version') = 0,
    'alter table lesson_progress add column version bigint not null default 0',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'lesson_progress' and constraint_name = 'uk_lesson_progress_student_lesson') = 0,
    'alter table lesson_progress add constraint uk_lesson_progress_student_lesson unique (student_id, lesson_id)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'lesson_progress' and constraint_name = 'chk_lesson_progress_percent_range') = 0,
    'alter table lesson_progress add constraint chk_lesson_progress_percent_range check (completion_percent is null or completion_percent between 0 and 100)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'student_progress' and column_name = 'version') = 0,
    'alter table student_progress add column version bigint not null default 0',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'student_progress' and constraint_name = 'uk_student_progress_student') = 0,
    'alter table student_progress add constraint uk_student_progress_student unique (student_id)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'student_progress' and constraint_name = 'chk_student_progress_percent_range') = 0,
    'alter table student_progress add constraint chk_student_progress_percent_range check (completion_percent is null or completion_percent between 0 and 100)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'daily_streaks' and column_name = 'version') = 0,
    'alter table daily_streaks add column version bigint not null default 0',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'daily_streaks' and constraint_name = 'uk_daily_streaks_student') = 0,
    'alter table daily_streaks add constraint uk_daily_streaks_student unique (student_id)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'daily_streaks' and constraint_name = 'chk_daily_streaks_non_negative') = 0,
    'alter table daily_streaks add constraint chk_daily_streaks_non_negative check (current_streak >= 0 and best_streak >= 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'daily_streaks' and constraint_name = 'chk_daily_streaks_best_not_less_current') = 0,
    'alter table daily_streaks add constraint chk_daily_streaks_best_not_less_current check (best_streak >= current_streak)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'points_transactions' and column_name = 'source_key') = 0,
    'alter table points_transactions add column source_key varchar(220) generated always as (case when source_type is not null and source_id is not null then concat(source_type, ''#'', source_id) else null end) stored',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'uk_points_transactions_student_source') = 0,
    'alter table points_transactions add constraint uk_points_transactions_student_source unique (student_id, source_key)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'chk_points_transactions_delta_non_negative') = 0,
    'alter table points_transactions add constraint chk_points_transactions_delta_non_negative check (points_delta >= 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'points_transactions' and constraint_name = 'chk_points_transactions_source_pair') = 0,
    'alter table points_transactions add constraint chk_points_transactions_source_pair check ((source_type is null and source_id is null) or (source_type is not null and source_id is not null))',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'video_progress' and column_name = 'version') = 0,
    'alter table video_progress add column version bigint not null default 0',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_recorded_video_fk_name = (
    select constraint_name
    from information_schema.key_column_usage
    where table_schema = database()
      and table_name = 'video_progress'
      and column_name = 'recorded_video_id'
      and referenced_table_name = 'recorded_lesson_videos'
    limit 1
);
set @baseline_contract_sql = if(
    @baseline_contract_recorded_video_fk_name is null,
    'select 1',
    concat('alter table video_progress drop foreign key `', replace(@baseline_contract_recorded_video_fk_name, '`', '``'), '`')
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select is_nullable from information_schema.columns where table_schema = database() and table_name = 'video_progress' and column_name = 'recorded_video_id') = 'YES',
    'alter table video_progress modify recorded_video_id bigint not null',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*)
     from information_schema.key_column_usage
     where table_schema = database()
       and table_name = 'video_progress'
       and column_name = 'recorded_video_id'
       and referenced_table_name = 'recorded_lesson_videos') = 0,
    'alter table video_progress add constraint FKdjbxt2h00nn3l4ckqkc0f92mo foreign key (recorded_video_id) references recorded_lesson_videos (id)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_seconds_non_negative') = 0,
    'alter table video_progress add constraint chk_video_progress_seconds_non_negative check (watched_seconds is null or watched_seconds >= 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_total_seconds_non_negative') = 0,
    'alter table video_progress add constraint chk_video_progress_total_seconds_non_negative check (total_seconds is null or total_seconds >= 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_percentage_range') = 0,
    'alter table video_progress add constraint chk_video_progress_percentage_range check (progress_percentage is null or progress_percentage between 0 and 100)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'video_progress' and constraint_name = 'chk_video_progress_watched_within_total') = 0,
    'alter table video_progress add constraint chk_video_progress_watched_within_total check (watched_seconds is null or total_seconds is null or watched_seconds <= total_seconds)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'live_classes' and column_name = 'version') = 0,
    'alter table live_classes add column version bigint not null default 0',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_classes' and constraint_name = 'chk_live_classes_max_participants_positive') = 0,
    'alter table live_classes add constraint chk_live_classes_max_participants_positive check (max_participants is null or max_participants > 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_classes' and constraint_name = 'chk_live_classes_time_order') = 0,
    'alter table live_classes add constraint chk_live_classes_time_order check (scheduled_start_at is null or scheduled_end_at is null or scheduled_end_at > scheduled_start_at)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_classes' and constraint_name = 'chk_live_classes_linked_content') = 0,
    'alter table live_classes add constraint chk_live_classes_linked_content check (lesson_id is not null or course_package_id is not null)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'live_class_enrolments' and column_name = 'version') = 0,
    'alter table live_class_enrolments add column version bigint not null default 0',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'live_class_enrolments' and column_name = 'active_enrolment_key') = 0,
    'alter table live_class_enrolments add column active_enrolment_key varchar(220) generated always as (case when enrolment_status in (''ENROLLED'',''ATTENDED'') then concat(live_class_id, ''#'', student_id) else null end) stored',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'live_class_enrolments' and constraint_name = 'uk_live_class_enrolments_active_student') = 0,
    'alter table live_class_enrolments add constraint uk_live_class_enrolments_active_student unique (active_enrolment_key)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'uploaded_files' and constraint_name = 'uk_uploaded_files_stored_file_name') = 0,
    'alter table uploaded_files add constraint uk_uploaded_files_stored_file_name unique (stored_file_name)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'uploaded_files' and constraint_name = 'chk_uploaded_files_file_size_non_negative') = 0,
    'alter table uploaded_files add constraint chk_uploaded_files_file_size_non_negative check (file_size >= 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'uploaded_files' and constraint_name = 'chk_uploaded_files_private_access') = 0,
    'alter table uploaded_files add constraint chk_uploaded_files_private_access check (access_level in (''PRIVATE''))',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'users' and constraint_name = 'chk_users_session_version_non_negative') = 0,
    'alter table users add constraint chk_users_session_version_non_negative check (session_version >= 0)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'password_reset_tokens' and column_name = 'active_token_user_id') = 0,
    'alter table password_reset_tokens add column active_token_user_id bigint generated always as (case when used_at is null then user_id else null end) stored',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'password_reset_tokens' and constraint_name = 'uk_password_reset_tokens_active_user') = 0,
    'alter table password_reset_tokens add constraint uk_password_reset_tokens_active_user unique (active_token_user_id)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @baseline_contract_sql = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'password_reset_tokens' and constraint_name = 'chk_password_reset_tokens_used_after_created') = 0,
    'alter table password_reset_tokens add constraint chk_password_reset_tokens_used_after_created check (used_at is null or used_at >= created_at)',
    'select 1'
);
prepare baseline_contract_stmt from @baseline_contract_sql;
execute baseline_contract_stmt;
deallocate prepare baseline_contract_stmt;

set @add_cart_items_cart_created_at_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'cart_items' and index_name = 'idx_cart_items_cart_created_at') = 0,
    'create index idx_cart_items_cart_created_at on cart_items (cart_id, created_at)',
    'select 1'
);
prepare add_cart_items_cart_created_at_index_stmt from @add_cart_items_cart_created_at_index;
execute add_cart_items_cart_created_at_index_stmt;
deallocate prepare add_cart_items_cart_created_at_index_stmt;

set @add_lesson_progress_student_updated_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'lesson_progress' and index_name = 'idx_lesson_progress_student_updated') = 0,
    'create index idx_lesson_progress_student_updated on lesson_progress (student_id, updated_at)',
    'select 1'
);
prepare add_lesson_progress_student_updated_index_stmt from @add_lesson_progress_student_updated_index;
execute add_lesson_progress_student_updated_index_stmt;
deallocate prepare add_lesson_progress_student_updated_index_stmt;

set @add_student_badges_student_awarded_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'student_badges' and index_name = 'idx_student_badges_student_awarded') = 0,
    'create index idx_student_badges_student_awarded on student_badges (student_id, awarded_at)',
    'select 1'
);
prepare add_student_badges_student_awarded_index_stmt from @add_student_badges_student_awarded_index;
execute add_student_badges_student_awarded_index_stmt;
deallocate prepare add_student_badges_student_awarded_index_stmt;

set @add_points_transactions_student_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'points_transactions' and index_name = 'idx_points_transactions_student_created') = 0,
    'create index idx_points_transactions_student_created on points_transactions (student_id, created_at)',
    'select 1'
);
prepare add_points_transactions_student_created_index_stmt from @add_points_transactions_student_created_index;
execute add_points_transactions_student_created_index_stmt;
deallocate prepare add_points_transactions_student_created_index_stmt;

set @add_live_class_enrolments_class_status_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'live_class_enrolments' and index_name = 'idx_live_class_enrolments_class_status') = 0,
    'create index idx_live_class_enrolments_class_status on live_class_enrolments (live_class_id, enrolment_status)',
    'select 1'
);
prepare add_live_class_enrolments_class_status_index_stmt from @add_live_class_enrolments_class_status_index;
execute add_live_class_enrolments_class_status_index_stmt;
deallocate prepare add_live_class_enrolments_class_status_index_stmt;

set @add_live_class_enrolments_student_status_start_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'live_class_enrolments' and index_name = 'idx_live_class_enrolments_student_status_start') = 0,
    'create index idx_live_class_enrolments_student_status_start on live_class_enrolments (student_id, enrolment_status, live_class_id)',
    'select 1'
);
prepare add_live_class_enrolments_student_status_start_index_stmt from @add_live_class_enrolments_student_status_start_index;
execute add_live_class_enrolments_student_status_start_index_stmt;
deallocate prepare add_live_class_enrolments_student_status_start_index_stmt;

set @add_video_progress_teacher_lesson_watch_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'video_progress' and index_name = 'idx_video_progress_lesson_last_watched') = 0,
    'create index idx_video_progress_lesson_last_watched on video_progress (lesson_id, last_watched_at)',
    'select 1'
);
prepare add_video_progress_teacher_lesson_watch_index_stmt from @add_video_progress_teacher_lesson_watch_index;
execute add_video_progress_teacher_lesson_watch_index_stmt;
deallocate prepare add_video_progress_teacher_lesson_watch_index_stmt;

set @add_teacher_profiles_status_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'teacher_profiles' and index_name = 'idx_teacher_profiles_status_created') = 0,
    'create index idx_teacher_profiles_status_created on teacher_profiles (teacher_status, created_at)',
    'select 1'
);
prepare add_teacher_profiles_status_created_index_stmt from @add_teacher_profiles_status_created_index;
execute add_teacher_profiles_status_created_index_stmt;
deallocate prepare add_teacher_profiles_status_created_index_stmt;

set @add_audit_logs_target_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_target_created') = 0,
    'create index idx_audit_logs_target_created on audit_logs (target_type, target_id, created_at)',
    'select 1'
);
prepare add_audit_logs_target_created_index_stmt from @add_audit_logs_target_created_index;
execute add_audit_logs_target_created_index_stmt;
deallocate prepare add_audit_logs_target_created_index_stmt;

set @add_audit_logs_actor_created_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'audit_logs' and index_name = 'idx_audit_logs_actor_created') = 0,
    'create index idx_audit_logs_actor_created on audit_logs (actor_user_id, created_at)',
    'select 1'
);
prepare add_audit_logs_actor_created_index_stmt from @add_audit_logs_actor_created_index;
execute add_audit_logs_actor_created_index_stmt;
deallocate prepare add_audit_logs_actor_created_index_stmt;


-- ===== Section: ai report review notification type =====
alter table notifications
    modify column notification_type enum (
        'ADMIN_TASK',
        'AI_REPORT_REVIEW',
        'CONTENT_REVIEW',
        'LIVE_CLASS_REMINDER',
        'MODERATION_ACTION',
        'PAYMENT_STATUS',
        'PAYOUT_STATUS',
        'PROGRESS_UPDATE',
        'QUIZ_RESULT',
        'REFUND_STATUS',
        'REWARD_EARNED',
        'SYSTEM',
        'TEACHER_APPROVAL',
        'TEACHER_FEEDBACK'
    ) not null;

-- ===== Consolidated production hardening / sessions / quota / search =====

set @baseline_invalid_status_count = (
    select
        (select count(*) from ai_chat_messages where sender_role not in ('USER', 'ASSISTANT'))
      + (select count(*) from teacher_earnings where earning_status not in ('AVAILABLE', 'REFUNDED'))
      + (select count(*) from ai_usage_logs where status not in ('SUCCESS', 'FAILED', 'UNAVAILABLE'))
      + (select count(*) from ai_response_reports where status not in ('PENDING_REVIEW', 'IN_REVIEW', 'RESOLVED', 'DISMISSED'))
      + (select count(*) from ai_response_reports where reason_category not in ('INCORRECT_CONTENT', 'HALLUCINATION', 'UNSAFE_CONTENT', 'AGE_INAPPROPRIATE', 'OTHER'))
      + (select count(*) from teacher_verification_documents where document_type not in ('CERTIFICATE', 'QUALIFICATION', 'IDENTITY', 'OTHER'))
      + (select count(*) from teacher_verification_documents where submitted_at is null)
      + (select count(*) from teacher_verification_reviews where reviewed_at is null or decision_status not in ('APPROVED', 'REJECTED'))
      + (select count(*) from orders where checkout_source is not null and checkout_source not in ('CART', 'BUY_NOW'))
      + (select count(*) from learning_modules where not ((access_type = 'FREE' and (price is null or price = 0)) or (access_type = 'PAID' and price > 0)))
      + (select count(*) from lessons where not ((access_type = 'FREE' and (price is null or price = 0)) or (access_type = 'PAID' and price > 0)))
      + (select count(*) from course_packages where not ((access_type = 'FREE' and (price is null or price = 0)) or (access_type = 'PAID' and price > 0)))
);
set @baseline_sql = if(@baseline_invalid_status_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: rows violate tightened status/domain checks''');
prepare baseline_stmt from @baseline_sql;
execute baseline_stmt;
deallocate prepare baseline_stmt;

set @baseline_duplicate_current_teacher_document_count = (
    select count(*) from (
        select teacher_id, document_type
        from teacher_verification_documents
        where current_version = 1
        group by teacher_id, document_type
        having count(*) > 1
    ) duplicates
);
set @baseline_sql = if(@baseline_duplicate_current_teacher_document_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: multiple current teacher verification documents exist for the same type''');
prepare baseline_stmt from @baseline_sql;
execute baseline_stmt;
deallocate prepare baseline_stmt;

set @baseline_invalid_state_count = (
    select
        (select count(*) from ai_response_reports
         where not (
             (status = 'PENDING_REVIEW' and reviewed_at is null)
             or (status in ('IN_REVIEW', 'RESOLVED', 'DISMISSED') and reviewed_at is not null)
         ))
      + (select count(*) from notifications
         where not (
             (notification_status = 'UNREAD' and read_at is null)
             or (notification_status = 'READ' and read_at is not null)
         ))
);
set @baseline_sql = if(@baseline_invalid_state_count = 0, 'select 1', 'signal sqlstate ''45000'' set message_text = ''Baseline blocked: rows violate review/read state consistency checks''');
prepare baseline_stmt from @baseline_sql;
execute baseline_stmt;
deallocate prepare baseline_stmt;

alter table teacher_verification_documents
    add column current_document_key varchar(160) generated always as (
        case when current_version = 1 then concat(teacher_id, '#', document_type) else null end
    ) stored,
    add constraint uk_teacher_verification_documents_current_type unique (current_document_key);

alter table ai_chat_messages
    add constraint chk_ai_chat_messages_sender_role check (sender_role in ('USER', 'ASSISTANT'));

alter table teacher_earnings
    add constraint chk_teacher_earnings_status check (earning_status in ('AVAILABLE', 'REFUNDED'));

alter table ai_usage_logs
    add constraint chk_ai_usage_logs_status check (status in ('SUCCESS', 'FAILED', 'UNAVAILABLE'));

alter table ai_response_reports
    add constraint chk_ai_response_reports_status check (status in ('PENDING_REVIEW', 'IN_REVIEW', 'RESOLVED', 'DISMISSED')),
    add constraint chk_ai_response_reports_reason_category check (reason_category in ('INCORRECT_CONTENT', 'HALLUCINATION', 'UNSAFE_CONTENT', 'AGE_INAPPROPRIATE', 'OTHER')),
    add constraint chk_ai_response_reports_snapshot_source check (
        chat_message_id is not null
        or ai_response_snapshot is not null
    ),
    add constraint chk_ai_response_reports_review_consistency check (
        (status = 'PENDING_REVIEW' and reviewed_at is null)
        or (status in ('IN_REVIEW', 'RESOLVED', 'DISMISSED') and reviewed_at is not null)
    );

alter table teacher_verification_documents
    add constraint chk_teacher_verification_documents_type check (document_type in ('CERTIFICATE', 'QUALIFICATION', 'IDENTITY', 'OTHER')),
    add constraint chk_teacher_verification_documents_submitted_at check (submitted_at is not null);

alter table teacher_verification_reviews
    add constraint chk_teacher_verification_reviews_reviewed_at check (reviewed_at is not null),
    add constraint chk_teacher_verification_reviews_final_decision check (decision_status in ('APPROVED', 'REJECTED'));

alter table orders
    add constraint chk_orders_checkout_source check (checkout_source is null or checkout_source in ('CART', 'BUY_NOW'));

alter table learning_modules
    add constraint chk_learning_modules_access_price check (
        (access_type = 'FREE' and (price is null or price = 0))
        or (access_type = 'PAID' and price > 0)
    );

alter table lessons
    add constraint chk_lessons_access_price check (
        (access_type = 'FREE' and (price is null or price = 0))
        or (access_type = 'PAID' and price > 0)
    );

alter table course_packages
    add constraint chk_course_packages_access_price check (
        (access_type = 'FREE' and (price is null or price = 0))
        or (access_type = 'PAID' and price > 0)
    );

alter table notifications
    add constraint chk_notifications_read_state check (
        (notification_status = 'UNREAD' and read_at is null)
        or (notification_status = 'READ' and read_at is not null)
    );

create index idx_ai_chat_messages_session_role_id
    on ai_chat_messages (session_id, sender_role, id);

create index idx_ai_chat_messages_session_created
    on ai_chat_messages (session_id, created_at);

create index idx_ai_chat_sessions_user_feature_created
    on ai_chat_sessions (user_id, feature_key, created_at);

create index idx_ai_usage_logs_user_feature_created
    on ai_usage_logs (user_id, feature_key, created_at);

create index idx_ai_response_reports_student_created
    on ai_response_reports (student_user_id, created_at);

create index idx_order_items_order_created
    on order_items (order_id, created_at);

create index idx_payment_records_status_created
    on payment_records (payment_status, created_at);

create index idx_teacher_earnings_teacher_status_created
    on teacher_earnings (teacher_id, earning_status, created_at);

create index idx_teacher_verification_documents_teacher_current_submitted
    on teacher_verification_documents (teacher_id, current_version, submitted_at, id);

create index idx_teacher_verification_documents_latest
    on teacher_verification_documents (teacher_id, submitted_at, id);

create index idx_teacher_verification_reviews_latest
    on teacher_verification_reviews (teacher_id, reviewed_at, id);

create index idx_audit_logs_actor_email_created
    on audit_logs (actor_email, created_at);

create index idx_notifications_user_status_created_id
    on notifications (user_id, notification_status, created_at, id);

create table refresh_tokens (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    revoked_at datetime(6),
    rotated_at datetime(6),
    updated_at datetime(6) not null,
    user_id bigint not null,
    created_by_ip varchar(64),
    family_id varchar(36) not null,
    replaced_by_token_hash varchar(64),
    token_hash varchar(64) not null,
    user_agent varchar(255),
    primary key (id)
) engine=InnoDB;

alter table refresh_tokens
    add constraint uk_refresh_tokens_token_hash unique (token_hash);

alter table refresh_tokens
    add constraint fk_refresh_tokens_user
    foreign key (user_id) references users (id);

alter table refresh_tokens
    add constraint chk_refresh_tokens_expiry_after_created
    check (expires_at > created_at);

alter table refresh_tokens
    add constraint chk_refresh_tokens_revoked_after_created
    check (revoked_at is null or revoked_at >= created_at);

alter table refresh_tokens
    add constraint chk_refresh_tokens_rotated_after_created
    check (rotated_at is null or rotated_at >= created_at);

alter table refresh_tokens
    add constraint chk_refresh_tokens_replacement_on_rotation
    check (
        (rotated_at is null and replaced_by_token_hash is null)
        or (rotated_at is not null and replaced_by_token_hash is not null)
    );

create index idx_refresh_tokens_user_id
    on refresh_tokens (user_id);

create index idx_refresh_tokens_family_id
    on refresh_tokens (family_id);

create index idx_refresh_tokens_expires_at
    on refresh_tokens (expires_at);

create table ai_usage_quota_counters (
    created_at datetime(6) not null,
    day_count integer not null,
    id bigint not null auto_increment,
    minute_count integer not null,
    minute_started_at datetime(6) not null,
    updated_at datetime(6) not null,
    usage_day date not null,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;

alter table ai_usage_quota_counters
    add constraint uk_ai_usage_quota_counters_user unique (user_id);

alter table ai_usage_quota_counters
    add constraint fk_ai_usage_quota_counters_user
    foreign key (user_id) references users (id);

alter table ai_usage_quota_counters
    add constraint chk_ai_usage_quota_counters_minute_count
    check (minute_count >= 0);

alter table ai_usage_quota_counters
    add constraint chk_ai_usage_quota_counters_day_count
    check (day_count >= 0);

create index idx_ai_usage_quota_counters_user_id
    on ai_usage_quota_counters (user_id);

alter table lessons
    add fulltext index ft_lessons_title_summary_content (title, summary, lesson_content);

alter table course_packages
    add fulltext index ft_course_packages_title_description (title, description);

alter table flashcards
    add fulltext index ft_flashcards_learning_text (title, front_text, back_text, pinyin, english_meaning, malay_meaning, tags);
