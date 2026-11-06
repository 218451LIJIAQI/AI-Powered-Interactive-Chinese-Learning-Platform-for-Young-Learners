-- =====================================================================
-- V17 - Class themes, "Best after Unit N" and the eight level check skills (MySQL 8.4).
--
--   1. Categories become the class themes (Travel, Work and career, Study or exchange, Daily life, Personal
--      interest); every class, pending class revision, unit and lesson keeps a category through the mapping below.
--      An empty table stays empty: the application seeder fills it on a fresh database.
--   2. course_packages: best_after_unit, a soft prerequisite label (Learn unit 1 to 8, never a lock), with its
--      pending copy for the revision of a published class's details.
--   3. level_check_questions.skill: Numbers, Grammar and Idioms join the five existing skills.
--
-- Learn path content is not touched: existing databases keep the path they were seeded with.
-- =====================================================================

-- ===== 1. Categories become class themes =====

create temporary table v17_theme_map (
    old_name varchar(120) not null,
    new_name varchar(120) not null
) default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

insert into v17_theme_map (old_name, new_name) values
    ('Culture', 'Personal interest'),
    ('Speaking', 'Daily life'),
    ('Sentences', 'Daily life'),
    ('Vocabulary', 'Daily life'),
    ('Numbers', 'Daily life'),
    ('Pinyin', 'Daily life'),
    ('Tones', 'Daily life'),
    ('Characters', 'Daily life'),
    ('Listening', 'Daily life');

-- Only when the database already has categories: an empty table is filled by the application seeder.
insert into learning_categories (name, description, sort_order, created_at, updated_at)
select theme.name, concat(theme.name, ' learning category.'), theme.sort_order, now(6), now(6)
from (
    select 'Travel' as name, 1 as sort_order
    union all select 'Work and career', 2
    union all select 'Study or exchange', 3
    union all select 'Daily life', 4
    union all select 'Personal interest', 5
) theme
where exists (select 1 from learning_categories existing)
  and not exists (select 1 from learning_categories c where c.name = theme.name);

-- A category outside the map (none is seeded, but an old database may hold one) also moves to Daily life, so that
-- nothing keeps the old rows from being deleted.
update course_packages t
    join learning_categories old_topic on old_topic.id = t.category_id
    left join v17_theme_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = coalesce(m.new_name, 'Daily life')
set t.category_id = new_topic.id
where old_topic.name not in ('Travel', 'Work and career', 'Study or exchange', 'Daily life', 'Personal interest');

update course_packages t
    join learning_categories old_topic on old_topic.id = t.pending_category_id
    left join v17_theme_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = coalesce(m.new_name, 'Daily life')
set t.pending_category_id = new_topic.id
where old_topic.name not in ('Travel', 'Work and career', 'Study or exchange', 'Daily life', 'Personal interest');

update learning_modules t
    join learning_categories old_topic on old_topic.id = t.category_id
    left join v17_theme_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = coalesce(m.new_name, 'Daily life')
set t.category_id = new_topic.id
where old_topic.name not in ('Travel', 'Work and career', 'Study or exchange', 'Daily life', 'Personal interest');

update lessons t
    join learning_categories old_topic on old_topic.id = t.category_id
    left join v17_theme_map m on m.old_name = old_topic.name
    join learning_categories new_topic on new_topic.name = coalesce(m.new_name, 'Daily life')
set t.category_id = new_topic.id
where old_topic.name not in ('Travel', 'Work and career', 'Study or exchange', 'Daily life', 'Personal interest');

delete from learning_categories
where name not in ('Travel', 'Work and career', 'Study or exchange', 'Daily life', 'Personal interest');

update learning_categories set sort_order = 1 where name = 'Travel';
update learning_categories set sort_order = 2 where name = 'Work and career';
update learning_categories set sort_order = 3 where name = 'Study or exchange';
update learning_categories set sort_order = 4 where name = 'Daily life';
update learning_categories set sort_order = 5 where name = 'Personal interest';

drop temporary table v17_theme_map;

-- ===== 2. Best after Unit N =====

alter table course_packages
    add column best_after_unit integer null,
    add column pending_best_after_unit integer null;

-- ===== 3. Level check skills =====

alter table level_check_questions
    modify column skill enum ('CHARACTERS','GRAMMAR','IDIOMS','LISTENING','NUMBERS','PINYIN','SENTENCES','VOCABULARY') not null;
