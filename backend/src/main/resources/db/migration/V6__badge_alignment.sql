-- =====================================================================
-- V6 - Badge catalogue alignment (MySQL 8.4).
--
-- The reward service (StudentLearningProgressService / RewardBadge) awards
-- exactly five badges: First Steps, Quiz Star, Lesson Finisher,
-- Streak Explorer (3-day streak) and Point Starter (50 points). Earlier
-- seeds created "Quiz Explorer" and "Daily Spark", which are never
-- awarded, and the awarded badges were only created lazily.
--
--   1. insert every awarded badge that is missing (guarded by name);
--   2. align the description (the earning rule) and icon key of the five;
--   3. remove the two never-awarded badges when no student holds them,
--      otherwise only deactivate them (student history is kept).
--
-- Pure DML guarded by NOT EXISTS / WHERE clauses, so it is idempotent and
-- safe on a partially seeded database. V1-V5 are never edited.
-- =====================================================================

-- ===== Step 1: insert missing awarded badges =====

insert into badges (active, created_at, updated_at, icon_key, name, description)
select 1, current_timestamp(6), current_timestamp(6), 'first-steps', 'First Steps',
       'Earned the first time you submit a quiz or complete a practice activity.'
from dual
where not exists (select 1 from badges b where b.name = 'First Steps');

insert into badges (active, created_at, updated_at, icon_key, name, description)
select 1, current_timestamp(6), current_timestamp(6), 'quiz-star', 'Quiz Star',
       'Earned the first time you score at least 80% on a quiz.'
from dual
where not exists (select 1 from badges b where b.name = 'Quiz Star');

insert into badges (active, created_at, updated_at, icon_key, name, description)
select 1, current_timestamp(6), current_timestamp(6), 'lesson-finisher', 'Lesson Finisher',
       'Earned the first time you complete a lesson.'
from dual
where not exists (select 1 from badges b where b.name = 'Lesson Finisher');

insert into badges (active, created_at, updated_at, icon_key, name, description)
select 1, current_timestamp(6), current_timestamp(6), 'streak-explorer', 'Streak Explorer',
       'Earned when you learn on 3 days in a row (submit a quiz or complete a practice activity each day).'
from dual
where not exists (select 1 from badges b where b.name = 'Streak Explorer');

insert into badges (active, created_at, updated_at, icon_key, name, description)
select 1, current_timestamp(6), current_timestamp(6), 'point-starter', 'Point Starter',
       'Earned when you collect at least 50 learning points.'
from dual
where not exists (select 1 from badges b where b.name = 'Point Starter');

-- ===== Step 2: align rule descriptions and icon keys =====

update badges
set description = 'Earned the first time you submit a quiz or complete a practice activity.',
    icon_key = 'first-steps', active = 1, updated_at = current_timestamp(6)
where name = 'First Steps';

update badges
set description = 'Earned the first time you score at least 80% on a quiz.',
    icon_key = 'quiz-star', active = 1, updated_at = current_timestamp(6)
where name = 'Quiz Star';

update badges
set description = 'Earned the first time you complete a lesson.',
    icon_key = 'lesson-finisher', active = 1, updated_at = current_timestamp(6)
where name = 'Lesson Finisher';

update badges
set description = 'Earned when you learn on 3 days in a row (submit a quiz or complete a practice activity each day).',
    icon_key = 'streak-explorer', active = 1, updated_at = current_timestamp(6)
where name = 'Streak Explorer';

update badges
set description = 'Earned when you collect at least 50 learning points.',
    icon_key = 'point-starter', active = 1, updated_at = current_timestamp(6)
where name = 'Point Starter';

-- ===== Step 3: retire the never-awarded badges =====

delete from badges
where name in ('Quiz Explorer', 'Daily Spark')
  and not exists (select 1 from student_badges sb where sb.badge_id = badges.id);

update badges
set active = 0, updated_at = current_timestamp(6)
where name in ('Quiz Explorer', 'Daily Spark');
