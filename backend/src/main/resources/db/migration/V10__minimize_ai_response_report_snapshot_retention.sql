alter table ai_response_reports
    modify column ai_response_snapshot LONGTEXT null;

update ai_response_reports
set prompt_snapshot = null,
    ai_response_snapshot = null
where chat_message_id is not null;
