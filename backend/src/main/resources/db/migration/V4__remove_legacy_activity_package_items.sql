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
