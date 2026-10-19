-- =====================================================================
-- V2 - Remove commerce (MySQL 8.4).
--
-- The Proposal has no payments or monetisation: every PUBLISHED lesson,
-- module, course, quiz, recorded video and live class is free for every
-- signed-in student. This migration removes the commerce subsystem that
-- V1 created, in dependency order, so it can run on an existing V1
-- database as well as on a fresh install:
--
--   1. drop the foreign keys between commerce tables, then drop the
--      commerce tables child-first (their remaining FKs to
--      student_profiles / teacher_profiles go with them);
--   2. drop the FREE/PAID price check constraints, then the price and
--      access_type columns of learning_modules, lessons, course_packages
--      (course_packages stays as the free "Course" grouping; the
--      learning_type delivery-mode column is kept);
--   3. delete the default_currency / platform_commission_rate settings;
--   4. remap PAYMENT_STATUS / REFUND_STATUS / PAYOUT_STATUS notifications
--      to SYSTEM and shrink the notification_type ENUM.
--
-- Every DDL step is guarded through information_schema so a partially
-- applied or hand-repaired database does not block the migration.
-- V1 is never edited (Flyway checksum stability).
-- =====================================================================

-- ===== Step 1a: foreign keys between commerce tables =====

set @fk_sql = (
    select if(count(*) > 0,
              'alter table teacher_earnings drop foreign key FKh0ecr76qq1quwldf7lapak879',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'teacher_earnings'
      and constraint_name = 'FKh0ecr76qq1quwldf7lapak879'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @fk_sql = (
    select if(count(*) > 0,
              'alter table refund_requests drop foreign key FK452xm7hwgngbanwkdgs3601b1',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'refund_requests'
      and constraint_name = 'FK452xm7hwgngbanwkdgs3601b1'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @fk_sql = (
    select if(count(*) > 0,
              'alter table payment_entitlements drop foreign key FK7d9y052u9mtl9qawornj5nwy7',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'payment_entitlements'
      and constraint_name = 'FK7d9y052u9mtl9qawornj5nwy7'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @fk_sql = (
    select if(count(*) > 0,
              'alter table payment_entitlements drop foreign key FK7lm90rg92vjyw87e14w2jkwyx',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'payment_entitlements'
      and constraint_name = 'FK7lm90rg92vjyw87e14w2jkwyx'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @fk_sql = (
    select if(count(*) > 0,
              'alter table payment_records drop foreign key FKcqu2d790mp868g1ogc678ygaj',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'payment_records'
      and constraint_name = 'FKcqu2d790mp868g1ogc678ygaj'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @fk_sql = (
    select if(count(*) > 0,
              'alter table order_items drop foreign key FKbioxgbv59vetrxe0ejfubep1w',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'order_items'
      and constraint_name = 'FKbioxgbv59vetrxe0ejfubep1w'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

set @fk_sql = (
    select if(count(*) > 0,
              'alter table cart_items drop foreign key FKpcttvuq4mxppo8sxggjtn5i2c',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'cart_items'
      and constraint_name = 'FKpcttvuq4mxppo8sxggjtn5i2c'
      and constraint_type = 'FOREIGN KEY'
);
prepare fk_stmt from @fk_sql;
execute fk_stmt;
deallocate prepare fk_stmt;

-- ===== Step 1b: commerce tables, child-first =====

drop table if exists teacher_earnings;
drop table if exists refund_requests;
drop table if exists payment_entitlements;
drop table if exists payment_records;
drop table if exists order_items;
drop table if exists orders;
drop table if exists cart_items;
drop table if exists carts;
drop table if exists payout_requests;

-- ===== Step 2a: FREE/PAID price check constraints =====

set @chk_sql = (
    select if(count(*) > 0,
              'alter table learning_modules drop check chk_learning_modules_access_price',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'learning_modules'
      and constraint_name = 'chk_learning_modules_access_price'
      and constraint_type = 'CHECK'
);
prepare chk_stmt from @chk_sql;
execute chk_stmt;
deallocate prepare chk_stmt;

set @chk_sql = (
    select if(count(*) > 0,
              'alter table lessons drop check chk_lessons_access_price',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'lessons'
      and constraint_name = 'chk_lessons_access_price'
      and constraint_type = 'CHECK'
);
prepare chk_stmt from @chk_sql;
execute chk_stmt;
deallocate prepare chk_stmt;

set @chk_sql = (
    select if(count(*) > 0,
              'alter table course_packages drop check chk_course_packages_access_price',
              'select 1')
    from information_schema.table_constraints
    where constraint_schema = database()
      and table_name = 'course_packages'
      and constraint_name = 'chk_course_packages_access_price'
      and constraint_type = 'CHECK'
);
prepare chk_stmt from @chk_sql;
execute chk_stmt;
deallocate prepare chk_stmt;

-- ===== Step 2b: price / access_type columns =====

set @col_sql = (
    select if(count(*) > 0, 'alter table learning_modules drop column price', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'learning_modules' and column_name = 'price'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table learning_modules drop column access_type', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'learning_modules' and column_name = 'access_type'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table lessons drop column price', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'lessons' and column_name = 'price'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table lessons drop column access_type', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'lessons' and column_name = 'access_type'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table course_packages drop column price', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'course_packages' and column_name = 'price'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

set @col_sql = (
    select if(count(*) > 0, 'alter table course_packages drop column access_type', 'select 1')
    from information_schema.columns
    where table_schema = database() and table_name = 'course_packages' and column_name = 'access_type'
);
prepare col_stmt from @col_sql;
execute col_stmt;
deallocate prepare col_stmt;

-- ===== Step 3: commerce platform settings =====

delete from system_settings
where setting_key in ('default_currency', 'platform_commission_rate');

-- ===== Step 4: notification types =====

update notifications
set notification_type = 'SYSTEM'
where notification_type in ('PAYMENT_STATUS', 'PAYOUT_STATUS', 'REFUND_STATUS');

alter table notifications
    modify column notification_type enum (
        'ADMIN_TASK',
        'AI_REPORT_REVIEW',
        'CONTENT_REVIEW',
        'LIVE_CLASS_REMINDER',
        'MODERATION_ACTION',
        'PROGRESS_UPDATE',
        'QUIZ_RESULT',
        'REWARD_EARNED',
        'SYSTEM',
        'TEACHER_APPROVAL',
        'TEACHER_FEEDBACK'
    ) not null;
