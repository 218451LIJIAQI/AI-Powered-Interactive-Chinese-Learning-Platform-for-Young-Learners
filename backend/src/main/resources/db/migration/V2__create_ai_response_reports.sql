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
