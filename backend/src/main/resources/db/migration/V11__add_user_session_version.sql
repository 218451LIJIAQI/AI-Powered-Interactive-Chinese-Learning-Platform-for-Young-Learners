alter table users
    add column session_version bigint not null default 0;
