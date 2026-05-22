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
