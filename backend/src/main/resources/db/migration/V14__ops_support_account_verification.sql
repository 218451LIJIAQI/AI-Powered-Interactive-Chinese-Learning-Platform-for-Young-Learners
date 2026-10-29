-- =====================================================================
-- V14 - Ops module: Help & support, account settings, teacher verification, admin invites (MySQL 8.4).
--
--   1. users gains deleted_at (accounts deleted by their owner are anonymised, never removed) and the four
--      e-mail notification preferences of the Account page.
--   2. teacher_profiles gains verification_submitted_at (an application is "in review" once the teacher sends it)
--      and standards_accepted_at (the "I'll follow what MandarinSpark expects from teachers" consent).
--      Pending teachers who already uploaded documents count as submitted, so they stay in the admin queue.
--   3. audit_logs gains target_label (what the entry is about, as shown in the audit log) and reason (the reason an
--      admin gave for a decision).
--   4. refresh_tokens gains persistent ("Keep me signed in": a persistent cookie, otherwise a browser-session one).
--   5. New tables: email_change_tokens, admin_invites, support_requests, support_messages.
-- =====================================================================

-- ===== 1. users =====

alter table users
    add column deleted_at datetime(6) null,
    add column notify_live_session_reminders bit not null default 1,
    add column notify_feedback bit not null default 1,
    add column notify_content_reviews bit not null default 1,
    add column notify_review_queues bit not null default 1;

-- ===== 2. teacher_profiles =====

alter table teacher_profiles
    add column verification_submitted_at datetime(6) null,
    add column standards_accepted_at datetime(6) null;

update teacher_profiles tp
set tp.verification_submitted_at = (
    select max(d.submitted_at)
    from teacher_verification_documents d
    where d.teacher_id = tp.id)
where tp.teacher_status = 'PENDING'
  and exists (select 1 from teacher_verification_documents d where d.teacher_id = tp.id);

create index idx_teacher_profiles_status_submitted
    on teacher_profiles (teacher_status, verification_submitted_at);

-- ===== 3. audit_logs =====

alter table audit_logs
    add column target_label varchar(255) null,
    add column reason varchar(1000) null;

-- ===== 4. refresh_tokens =====

alter table refresh_tokens
    add column persistent bit not null default 1;

-- ===== 5. New tables =====

create table email_change_tokens (
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    updated_at datetime(6) not null,
    used_at datetime(6),
    user_id bigint not null,
    new_email varchar(150) not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

alter table email_change_tokens
    add constraint uk_email_change_tokens_token_hash unique (token_hash);

create index idx_email_change_tokens_user_id
    on email_change_tokens (user_id);

alter table email_change_tokens
    add constraint fk_email_change_tokens_user
    foreign key (user_id) references users (id);

create table admin_invites (
    accepted_at datetime(6),
    accepted_user_id bigint,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null auto_increment,
    invited_by_user_id bigint,
    updated_at datetime(6) not null,
    email varchar(150) not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

alter table admin_invites
    add constraint uk_admin_invites_token_hash unique (token_hash);

create index idx_admin_invites_email
    on admin_invites (email);

create index idx_admin_invites_pending
    on admin_invites (accepted_at, expires_at);

alter table admin_invites
    add constraint fk_admin_invites_invited_by
    foreign key (invited_by_user_id) references users (id);

alter table admin_invites
    add constraint fk_admin_invites_accepted_user
    foreign key (accepted_user_id) references users (id);

create table support_requests (
    closes_at datetime(6),
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    last_support_reply_at datetime(6),
    request_number integer not null,
    requester_user_id bigint,
    solved_at datetime(6),
    updated_at datetime(6) not null,
    requester_email varchar(150) not null,
    subject varchar(150) not null,
    requester_role enum ('STUDENT','TEACHER','VISITOR') not null,
    request_topic enum ('ACCOUNT','AI_HELPER','CLASSES','LEARNING','OTHER','SAFETY','VERIFICATION') not null,
    request_status enum ('ANSWERED','OPEN','SOLVED') not null,
    closed_by enum ('AUTO','REQUESTER','SUPPORT'),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

alter table support_requests
    add constraint uk_support_requests_number unique (request_number);

create index idx_support_requests_requester_created
    on support_requests (requester_user_id, created_at);

create index idx_support_requests_status_created
    on support_requests (request_status, created_at);

create index idx_support_requests_status_closes
    on support_requests (request_status, closes_at);

alter table support_requests
    add constraint fk_support_requests_requester
    foreign key (requester_user_id) references users (id);

create table support_messages (
    attachment_file_id bigint,
    author_user_id bigint,
    created_at datetime(6) not null,
    from_support bit not null,
    id bigint not null auto_increment,
    request_id bigint not null,
    updated_at datetime(6) not null,
    body varchar(4000) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

create index idx_support_messages_request_created
    on support_messages (request_id, created_at);

alter table support_messages
    add constraint fk_support_messages_request
    foreign key (request_id) references support_requests (id);

alter table support_messages
    add constraint fk_support_messages_author
    foreign key (author_user_id) references users (id);

alter table support_messages
    add constraint fk_support_messages_attachment
    foreign key (attachment_file_id) references uploaded_files (id);
