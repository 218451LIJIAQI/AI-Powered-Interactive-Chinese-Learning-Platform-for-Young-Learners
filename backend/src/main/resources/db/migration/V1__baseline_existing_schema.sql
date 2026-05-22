
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
