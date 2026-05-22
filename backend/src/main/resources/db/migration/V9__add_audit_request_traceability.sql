alter table audit_logs
    add column request_id varchar(120) null;

create index idx_audit_logs_request_id
    on audit_logs (request_id);
