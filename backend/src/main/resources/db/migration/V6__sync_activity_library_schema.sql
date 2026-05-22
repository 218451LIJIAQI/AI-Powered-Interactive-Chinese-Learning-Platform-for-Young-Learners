set @add_drag_drop_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'created_by_user_id') = 0,
    'alter table drag_drop_activities add column created_by_user_id bigint',
    'select 1'
);
prepare add_drag_drop_created_by_user_id_stmt from @add_drag_drop_created_by_user_id;
execute add_drag_drop_created_by_user_id_stmt;
deallocate prepare add_drag_drop_created_by_user_id_stmt;

set @add_drag_drop_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'estimated_seconds') = 0,
    'alter table drag_drop_activities add column estimated_seconds integer',
    'select 1'
);
prepare add_drag_drop_estimated_seconds_stmt from @add_drag_drop_estimated_seconds;
execute add_drag_drop_estimated_seconds_stmt;
deallocate prepare add_drag_drop_estimated_seconds_stmt;

set @add_drag_drop_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'hsk_level') = 0,
    'alter table drag_drop_activities add column hsk_level integer',
    'select 1'
);
prepare add_drag_drop_hsk_level_stmt from @add_drag_drop_hsk_level;
execute add_drag_drop_hsk_level_stmt;
deallocate prepare add_drag_drop_hsk_level_stmt;

set @add_drag_drop_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'xp_reward') = 0,
    'alter table drag_drop_activities add column xp_reward integer not null default 15',
    'select 1'
);
prepare add_drag_drop_xp_reward_stmt from @add_drag_drop_xp_reward;
execute add_drag_drop_xp_reward_stmt;
deallocate prepare add_drag_drop_xp_reward_stmt;

set @add_drag_drop_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'topic') = 0,
    'alter table drag_drop_activities add column topic varchar(100)',
    'select 1'
);
prepare add_drag_drop_topic_stmt from @add_drag_drop_topic;
execute add_drag_drop_topic_stmt;
deallocate prepare add_drag_drop_topic_stmt;

set @add_drag_drop_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'tags') = 0,
    'alter table drag_drop_activities add column tags varchar(500)',
    'select 1'
);
prepare add_drag_drop_tags_stmt from @add_drag_drop_tags;
execute add_drag_drop_tags_stmt;
deallocate prepare add_drag_drop_tags_stmt;

set @add_drag_drop_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'age_band') = 0,
    'alter table drag_drop_activities add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_drag_drop_age_band_stmt from @add_drag_drop_age_band;
execute add_drag_drop_age_band_stmt;
deallocate prepare add_drag_drop_age_band_stmt;

set @add_drag_drop_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'difficulty') = 0,
    'alter table drag_drop_activities add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_drag_drop_difficulty_stmt from @add_drag_drop_difficulty;
execute add_drag_drop_difficulty_stmt;
deallocate prepare add_drag_drop_difficulty_stmt;

set @add_flashcards_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'created_by_user_id') = 0,
    'alter table flashcards add column created_by_user_id bigint',
    'select 1'
);
prepare add_flashcards_created_by_user_id_stmt from @add_flashcards_created_by_user_id;
execute add_flashcards_created_by_user_id_stmt;
deallocate prepare add_flashcards_created_by_user_id_stmt;

set @add_flashcards_title = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'title') = 0,
    'alter table flashcards add column title varchar(200)',
    'select 1'
);
prepare add_flashcards_title_stmt from @add_flashcards_title;
execute add_flashcards_title_stmt;
deallocate prepare add_flashcards_title_stmt;

set @add_flashcards_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'estimated_seconds') = 0,
    'alter table flashcards add column estimated_seconds integer',
    'select 1'
);
prepare add_flashcards_estimated_seconds_stmt from @add_flashcards_estimated_seconds;
execute add_flashcards_estimated_seconds_stmt;
deallocate prepare add_flashcards_estimated_seconds_stmt;

set @add_flashcards_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'hsk_level') = 0,
    'alter table flashcards add column hsk_level integer',
    'select 1'
);
prepare add_flashcards_hsk_level_stmt from @add_flashcards_hsk_level;
execute add_flashcards_hsk_level_stmt;
deallocate prepare add_flashcards_hsk_level_stmt;

set @add_flashcards_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'xp_reward') = 0,
    'alter table flashcards add column xp_reward integer not null default 10',
    'select 1'
);
prepare add_flashcards_xp_reward_stmt from @add_flashcards_xp_reward;
execute add_flashcards_xp_reward_stmt;
deallocate prepare add_flashcards_xp_reward_stmt;

set @add_flashcards_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'topic') = 0,
    'alter table flashcards add column topic varchar(100)',
    'select 1'
);
prepare add_flashcards_topic_stmt from @add_flashcards_topic;
execute add_flashcards_topic_stmt;
deallocate prepare add_flashcards_topic_stmt;

set @add_flashcards_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'tags') = 0,
    'alter table flashcards add column tags varchar(500)',
    'select 1'
);
prepare add_flashcards_tags_stmt from @add_flashcards_tags;
execute add_flashcards_tags_stmt;
deallocate prepare add_flashcards_tags_stmt;

set @add_flashcards_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'age_band') = 0,
    'alter table flashcards add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_flashcards_age_band_stmt from @add_flashcards_age_band;
execute add_flashcards_age_band_stmt;
deallocate prepare add_flashcards_age_band_stmt;

set @add_flashcards_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'difficulty') = 0,
    'alter table flashcards add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_flashcards_difficulty_stmt from @add_flashcards_difficulty;
execute add_flashcards_difficulty_stmt;
deallocate prepare add_flashcards_difficulty_stmt;

set @add_listening_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'created_by_user_id') = 0,
    'alter table listening_exercises add column created_by_user_id bigint',
    'select 1'
);
prepare add_listening_created_by_user_id_stmt from @add_listening_created_by_user_id;
execute add_listening_created_by_user_id_stmt;
deallocate prepare add_listening_created_by_user_id_stmt;

set @add_listening_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'estimated_seconds') = 0,
    'alter table listening_exercises add column estimated_seconds integer',
    'select 1'
);
prepare add_listening_estimated_seconds_stmt from @add_listening_estimated_seconds;
execute add_listening_estimated_seconds_stmt;
deallocate prepare add_listening_estimated_seconds_stmt;

set @add_listening_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'hsk_level') = 0,
    'alter table listening_exercises add column hsk_level integer',
    'select 1'
);
prepare add_listening_hsk_level_stmt from @add_listening_hsk_level;
execute add_listening_hsk_level_stmt;
deallocate prepare add_listening_hsk_level_stmt;

set @add_listening_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'xp_reward') = 0,
    'alter table listening_exercises add column xp_reward integer not null default 15',
    'select 1'
);
prepare add_listening_xp_reward_stmt from @add_listening_xp_reward;
execute add_listening_xp_reward_stmt;
deallocate prepare add_listening_xp_reward_stmt;

set @add_listening_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'topic') = 0,
    'alter table listening_exercises add column topic varchar(100)',
    'select 1'
);
prepare add_listening_topic_stmt from @add_listening_topic;
execute add_listening_topic_stmt;
deallocate prepare add_listening_topic_stmt;

set @add_listening_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'tags') = 0,
    'alter table listening_exercises add column tags varchar(500)',
    'select 1'
);
prepare add_listening_tags_stmt from @add_listening_tags;
execute add_listening_tags_stmt;
deallocate prepare add_listening_tags_stmt;

set @add_listening_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'age_band') = 0,
    'alter table listening_exercises add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_listening_age_band_stmt from @add_listening_age_band;
execute add_listening_age_band_stmt;
deallocate prepare add_listening_age_band_stmt;

set @add_listening_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'difficulty') = 0,
    'alter table listening_exercises add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_listening_difficulty_stmt from @add_listening_difficulty;
execute add_listening_difficulty_stmt;
deallocate prepare add_listening_difficulty_stmt;

set @add_matching_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'created_by_user_id') = 0,
    'alter table matching_games add column created_by_user_id bigint',
    'select 1'
);
prepare add_matching_created_by_user_id_stmt from @add_matching_created_by_user_id;
execute add_matching_created_by_user_id_stmt;
deallocate prepare add_matching_created_by_user_id_stmt;

set @add_matching_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'estimated_seconds') = 0,
    'alter table matching_games add column estimated_seconds integer',
    'select 1'
);
prepare add_matching_estimated_seconds_stmt from @add_matching_estimated_seconds;
execute add_matching_estimated_seconds_stmt;
deallocate prepare add_matching_estimated_seconds_stmt;

set @add_matching_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'hsk_level') = 0,
    'alter table matching_games add column hsk_level integer',
    'select 1'
);
prepare add_matching_hsk_level_stmt from @add_matching_hsk_level;
execute add_matching_hsk_level_stmt;
deallocate prepare add_matching_hsk_level_stmt;

set @add_matching_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'xp_reward') = 0,
    'alter table matching_games add column xp_reward integer not null default 15',
    'select 1'
);
prepare add_matching_xp_reward_stmt from @add_matching_xp_reward;
execute add_matching_xp_reward_stmt;
deallocate prepare add_matching_xp_reward_stmt;

set @add_matching_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'topic') = 0,
    'alter table matching_games add column topic varchar(100)',
    'select 1'
);
prepare add_matching_topic_stmt from @add_matching_topic;
execute add_matching_topic_stmt;
deallocate prepare add_matching_topic_stmt;

set @add_matching_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'tags') = 0,
    'alter table matching_games add column tags varchar(500)',
    'select 1'
);
prepare add_matching_tags_stmt from @add_matching_tags;
execute add_matching_tags_stmt;
deallocate prepare add_matching_tags_stmt;

set @add_matching_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'age_band') = 0,
    'alter table matching_games add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_matching_age_band_stmt from @add_matching_age_band;
execute add_matching_age_band_stmt;
deallocate prepare add_matching_age_band_stmt;

set @add_matching_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'difficulty') = 0,
    'alter table matching_games add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_matching_difficulty_stmt from @add_matching_difficulty;
execute add_matching_difficulty_stmt;
deallocate prepare add_matching_difficulty_stmt;

set @add_story_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'created_by_user_id') = 0,
    'alter table story_activities add column created_by_user_id bigint',
    'select 1'
);
prepare add_story_created_by_user_id_stmt from @add_story_created_by_user_id;
execute add_story_created_by_user_id_stmt;
deallocate prepare add_story_created_by_user_id_stmt;

set @add_story_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'estimated_seconds') = 0,
    'alter table story_activities add column estimated_seconds integer',
    'select 1'
);
prepare add_story_estimated_seconds_stmt from @add_story_estimated_seconds;
execute add_story_estimated_seconds_stmt;
deallocate prepare add_story_estimated_seconds_stmt;

set @add_story_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'hsk_level') = 0,
    'alter table story_activities add column hsk_level integer',
    'select 1'
);
prepare add_story_hsk_level_stmt from @add_story_hsk_level;
execute add_story_hsk_level_stmt;
deallocate prepare add_story_hsk_level_stmt;

set @add_story_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'xp_reward') = 0,
    'alter table story_activities add column xp_reward integer not null default 20',
    'select 1'
);
prepare add_story_xp_reward_stmt from @add_story_xp_reward;
execute add_story_xp_reward_stmt;
deallocate prepare add_story_xp_reward_stmt;

set @add_story_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'topic') = 0,
    'alter table story_activities add column topic varchar(100)',
    'select 1'
);
prepare add_story_topic_stmt from @add_story_topic;
execute add_story_topic_stmt;
deallocate prepare add_story_topic_stmt;

set @add_story_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'tags') = 0,
    'alter table story_activities add column tags varchar(500)',
    'select 1'
);
prepare add_story_tags_stmt from @add_story_tags;
execute add_story_tags_stmt;
deallocate prepare add_story_tags_stmt;

set @add_story_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'age_band') = 0,
    'alter table story_activities add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_story_age_band_stmt from @add_story_age_band;
execute add_story_age_band_stmt;
deallocate prepare add_story_age_band_stmt;

set @add_story_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'difficulty') = 0,
    'alter table story_activities add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_story_difficulty_stmt from @add_story_difficulty;
execute add_story_difficulty_stmt;
deallocate prepare add_story_difficulty_stmt;

set @add_tracing_created_by_user_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'created_by_user_id') = 0,
    'alter table tracing_exercises add column created_by_user_id bigint',
    'select 1'
);
prepare add_tracing_created_by_user_id_stmt from @add_tracing_created_by_user_id;
execute add_tracing_created_by_user_id_stmt;
deallocate prepare add_tracing_created_by_user_id_stmt;

set @add_tracing_estimated_seconds = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'estimated_seconds') = 0,
    'alter table tracing_exercises add column estimated_seconds integer',
    'select 1'
);
prepare add_tracing_estimated_seconds_stmt from @add_tracing_estimated_seconds;
execute add_tracing_estimated_seconds_stmt;
deallocate prepare add_tracing_estimated_seconds_stmt;

set @add_tracing_hsk_level = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'hsk_level') = 0,
    'alter table tracing_exercises add column hsk_level integer',
    'select 1'
);
prepare add_tracing_hsk_level_stmt from @add_tracing_hsk_level;
execute add_tracing_hsk_level_stmt;
deallocate prepare add_tracing_hsk_level_stmt;

set @add_tracing_xp_reward = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'xp_reward') = 0,
    'alter table tracing_exercises add column xp_reward integer not null default 12',
    'select 1'
);
prepare add_tracing_xp_reward_stmt from @add_tracing_xp_reward;
execute add_tracing_xp_reward_stmt;
deallocate prepare add_tracing_xp_reward_stmt;

set @add_tracing_topic = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'topic') = 0,
    'alter table tracing_exercises add column topic varchar(100)',
    'select 1'
);
prepare add_tracing_topic_stmt from @add_tracing_topic;
execute add_tracing_topic_stmt;
deallocate prepare add_tracing_topic_stmt;

set @add_tracing_tags = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'tags') = 0,
    'alter table tracing_exercises add column tags varchar(500)',
    'select 1'
);
prepare add_tracing_tags_stmt from @add_tracing_tags;
execute add_tracing_tags_stmt;
deallocate prepare add_tracing_tags_stmt;

set @add_tracing_age_band = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'age_band') = 0,
    'alter table tracing_exercises add column age_band enum (''ALL_AGES'',''KINDERGARTEN'',''LOWER_PRIMARY'',''UPPER_PRIMARY'')',
    'select 1'
);
prepare add_tracing_age_band_stmt from @add_tracing_age_band;
execute add_tracing_age_band_stmt;
deallocate prepare add_tracing_age_band_stmt;

set @add_tracing_difficulty = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'difficulty') = 0,
    'alter table tracing_exercises add column difficulty enum (''EASY'',''HARD'',''MEDIUM'')',
    'select 1'
);
prepare add_tracing_difficulty_stmt from @add_tracing_difficulty;
execute add_tracing_difficulty_stmt;
deallocate prepare add_tracing_difficulty_stmt;

set @drag_drop_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_drag_drop_lesson_fk = if(@drag_drop_lesson_fk is not null, concat('alter table drag_drop_activities drop foreign key `', @drag_drop_lesson_fk, '`'), 'select 1');
prepare drop_drag_drop_lesson_fk_stmt from @drop_drag_drop_lesson_fk;
execute drop_drag_drop_lesson_fk_stmt;
deallocate prepare drop_drag_drop_lesson_fk_stmt;

set @drop_drag_drop_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'drag_drop_activities' and column_name = 'lesson_id') > 0,
    'alter table drag_drop_activities drop column lesson_id',
    'select 1'
);
prepare drop_drag_drop_lesson_id_stmt from @drop_drag_drop_lesson_id;
execute drop_drag_drop_lesson_id_stmt;
deallocate prepare drop_drag_drop_lesson_id_stmt;

set @flashcards_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'flashcards' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_flashcards_lesson_fk = if(@flashcards_lesson_fk is not null, concat('alter table flashcards drop foreign key `', @flashcards_lesson_fk, '`'), 'select 1');
prepare drop_flashcards_lesson_fk_stmt from @drop_flashcards_lesson_fk;
execute drop_flashcards_lesson_fk_stmt;
deallocate prepare drop_flashcards_lesson_fk_stmt;

set @drop_flashcards_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'flashcards' and column_name = 'lesson_id') > 0,
    'alter table flashcards drop column lesson_id',
    'select 1'
);
prepare drop_flashcards_lesson_id_stmt from @drop_flashcards_lesson_id;
execute drop_flashcards_lesson_id_stmt;
deallocate prepare drop_flashcards_lesson_id_stmt;

set @listening_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'listening_exercises' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_listening_lesson_fk = if(@listening_lesson_fk is not null, concat('alter table listening_exercises drop foreign key `', @listening_lesson_fk, '`'), 'select 1');
prepare drop_listening_lesson_fk_stmt from @drop_listening_lesson_fk;
execute drop_listening_lesson_fk_stmt;
deallocate prepare drop_listening_lesson_fk_stmt;

set @drop_listening_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'listening_exercises' and column_name = 'lesson_id') > 0,
    'alter table listening_exercises drop column lesson_id',
    'select 1'
);
prepare drop_listening_lesson_id_stmt from @drop_listening_lesson_id;
execute drop_listening_lesson_id_stmt;
deallocate prepare drop_listening_lesson_id_stmt;

set @matching_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'matching_games' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_matching_lesson_fk = if(@matching_lesson_fk is not null, concat('alter table matching_games drop foreign key `', @matching_lesson_fk, '`'), 'select 1');
prepare drop_matching_lesson_fk_stmt from @drop_matching_lesson_fk;
execute drop_matching_lesson_fk_stmt;
deallocate prepare drop_matching_lesson_fk_stmt;

set @drop_matching_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'matching_games' and column_name = 'lesson_id') > 0,
    'alter table matching_games drop column lesson_id',
    'select 1'
);
prepare drop_matching_lesson_id_stmt from @drop_matching_lesson_id;
execute drop_matching_lesson_id_stmt;
deallocate prepare drop_matching_lesson_id_stmt;

set @story_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'story_activities' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_story_lesson_fk = if(@story_lesson_fk is not null, concat('alter table story_activities drop foreign key `', @story_lesson_fk, '`'), 'select 1');
prepare drop_story_lesson_fk_stmt from @drop_story_lesson_fk;
execute drop_story_lesson_fk_stmt;
deallocate prepare drop_story_lesson_fk_stmt;

set @drop_story_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'story_activities' and column_name = 'lesson_id') > 0,
    'alter table story_activities drop column lesson_id',
    'select 1'
);
prepare drop_story_lesson_id_stmt from @drop_story_lesson_id;
execute drop_story_lesson_id_stmt;
deallocate prepare drop_story_lesson_id_stmt;

set @tracing_lesson_fk = (select constraint_name from information_schema.key_column_usage where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'lesson_id' and referenced_table_name = 'lessons' limit 1);
set @drop_tracing_lesson_fk = if(@tracing_lesson_fk is not null, concat('alter table tracing_exercises drop foreign key `', @tracing_lesson_fk, '`'), 'select 1');
prepare drop_tracing_lesson_fk_stmt from @drop_tracing_lesson_fk;
execute drop_tracing_lesson_fk_stmt;
deallocate prepare drop_tracing_lesson_fk_stmt;

set @drop_tracing_lesson_id = if(
    (select count(*) from information_schema.columns where table_schema = database() and table_name = 'tracing_exercises' and column_name = 'lesson_id') > 0,
    'alter table tracing_exercises drop column lesson_id',
    'select 1'
);
prepare drop_tracing_lesson_id_stmt from @drop_tracing_lesson_id;
execute drop_tracing_lesson_id_stmt;
deallocate prepare drop_tracing_lesson_id_stmt;

set @add_drag_drop_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_created_by_user_id') = 0,
    'create index idx_drag_drop_activities_created_by_user_id on drag_drop_activities (created_by_user_id)',
    'select 1'
);
prepare add_drag_drop_created_by_user_id_index_stmt from @add_drag_drop_created_by_user_id_index;
execute add_drag_drop_created_by_user_id_index_stmt;
deallocate prepare add_drag_drop_created_by_user_id_index_stmt;

set @add_drag_drop_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_hsk_level') = 0,
    'create index idx_drag_drop_activities_hsk_level on drag_drop_activities (hsk_level)',
    'select 1'
);
prepare add_drag_drop_hsk_level_index_stmt from @add_drag_drop_hsk_level_index;
execute add_drag_drop_hsk_level_index_stmt;
deallocate prepare add_drag_drop_hsk_level_index_stmt;

set @add_drag_drop_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_age_band') = 0,
    'create index idx_drag_drop_activities_age_band on drag_drop_activities (age_band)',
    'select 1'
);
prepare add_drag_drop_age_band_index_stmt from @add_drag_drop_age_band_index;
execute add_drag_drop_age_band_index_stmt;
deallocate prepare add_drag_drop_age_band_index_stmt;

set @add_drag_drop_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'drag_drop_activities' and index_name = 'idx_drag_drop_activities_difficulty') = 0,
    'create index idx_drag_drop_activities_difficulty on drag_drop_activities (difficulty)',
    'select 1'
);
prepare add_drag_drop_difficulty_index_stmt from @add_drag_drop_difficulty_index;
execute add_drag_drop_difficulty_index_stmt;
deallocate prepare add_drag_drop_difficulty_index_stmt;

set @add_flashcards_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_created_by_user_id') = 0,
    'create index idx_flashcards_created_by_user_id on flashcards (created_by_user_id)',
    'select 1'
);
prepare add_flashcards_created_by_user_id_index_stmt from @add_flashcards_created_by_user_id_index;
execute add_flashcards_created_by_user_id_index_stmt;
deallocate prepare add_flashcards_created_by_user_id_index_stmt;

set @add_flashcards_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_hsk_level') = 0,
    'create index idx_flashcards_hsk_level on flashcards (hsk_level)',
    'select 1'
);
prepare add_flashcards_hsk_level_index_stmt from @add_flashcards_hsk_level_index;
execute add_flashcards_hsk_level_index_stmt;
deallocate prepare add_flashcards_hsk_level_index_stmt;

set @add_flashcards_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_age_band') = 0,
    'create index idx_flashcards_age_band on flashcards (age_band)',
    'select 1'
);
prepare add_flashcards_age_band_index_stmt from @add_flashcards_age_band_index;
execute add_flashcards_age_band_index_stmt;
deallocate prepare add_flashcards_age_band_index_stmt;

set @add_flashcards_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'flashcards' and index_name = 'idx_flashcards_difficulty') = 0,
    'create index idx_flashcards_difficulty on flashcards (difficulty)',
    'select 1'
);
prepare add_flashcards_difficulty_index_stmt from @add_flashcards_difficulty_index;
execute add_flashcards_difficulty_index_stmt;
deallocate prepare add_flashcards_difficulty_index_stmt;

set @add_listening_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_created_by_user_id') = 0,
    'create index idx_listening_exercises_created_by_user_id on listening_exercises (created_by_user_id)',
    'select 1'
);
prepare add_listening_created_by_user_id_index_stmt from @add_listening_created_by_user_id_index;
execute add_listening_created_by_user_id_index_stmt;
deallocate prepare add_listening_created_by_user_id_index_stmt;

set @add_listening_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_hsk_level') = 0,
    'create index idx_listening_exercises_hsk_level on listening_exercises (hsk_level)',
    'select 1'
);
prepare add_listening_hsk_level_index_stmt from @add_listening_hsk_level_index;
execute add_listening_hsk_level_index_stmt;
deallocate prepare add_listening_hsk_level_index_stmt;

set @add_listening_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_age_band') = 0,
    'create index idx_listening_exercises_age_band on listening_exercises (age_band)',
    'select 1'
);
prepare add_listening_age_band_index_stmt from @add_listening_age_band_index;
execute add_listening_age_band_index_stmt;
deallocate prepare add_listening_age_band_index_stmt;

set @add_listening_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'listening_exercises' and index_name = 'idx_listening_exercises_difficulty') = 0,
    'create index idx_listening_exercises_difficulty on listening_exercises (difficulty)',
    'select 1'
);
prepare add_listening_difficulty_index_stmt from @add_listening_difficulty_index;
execute add_listening_difficulty_index_stmt;
deallocate prepare add_listening_difficulty_index_stmt;

set @add_matching_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_created_by_user_id') = 0,
    'create index idx_matching_games_created_by_user_id on matching_games (created_by_user_id)',
    'select 1'
);
prepare add_matching_created_by_user_id_index_stmt from @add_matching_created_by_user_id_index;
execute add_matching_created_by_user_id_index_stmt;
deallocate prepare add_matching_created_by_user_id_index_stmt;

set @add_matching_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_hsk_level') = 0,
    'create index idx_matching_games_hsk_level on matching_games (hsk_level)',
    'select 1'
);
prepare add_matching_hsk_level_index_stmt from @add_matching_hsk_level_index;
execute add_matching_hsk_level_index_stmt;
deallocate prepare add_matching_hsk_level_index_stmt;

set @add_matching_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_age_band') = 0,
    'create index idx_matching_games_age_band on matching_games (age_band)',
    'select 1'
);
prepare add_matching_age_band_index_stmt from @add_matching_age_band_index;
execute add_matching_age_band_index_stmt;
deallocate prepare add_matching_age_band_index_stmt;

set @add_matching_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'matching_games' and index_name = 'idx_matching_games_difficulty') = 0,
    'create index idx_matching_games_difficulty on matching_games (difficulty)',
    'select 1'
);
prepare add_matching_difficulty_index_stmt from @add_matching_difficulty_index;
execute add_matching_difficulty_index_stmt;
deallocate prepare add_matching_difficulty_index_stmt;

set @add_story_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_created_by_user_id') = 0,
    'create index idx_story_activities_created_by_user_id on story_activities (created_by_user_id)',
    'select 1'
);
prepare add_story_created_by_user_id_index_stmt from @add_story_created_by_user_id_index;
execute add_story_created_by_user_id_index_stmt;
deallocate prepare add_story_created_by_user_id_index_stmt;

set @add_story_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_hsk_level') = 0,
    'create index idx_story_activities_hsk_level on story_activities (hsk_level)',
    'select 1'
);
prepare add_story_hsk_level_index_stmt from @add_story_hsk_level_index;
execute add_story_hsk_level_index_stmt;
deallocate prepare add_story_hsk_level_index_stmt;

set @add_story_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_age_band') = 0,
    'create index idx_story_activities_age_band on story_activities (age_band)',
    'select 1'
);
prepare add_story_age_band_index_stmt from @add_story_age_band_index;
execute add_story_age_band_index_stmt;
deallocate prepare add_story_age_band_index_stmt;

set @add_story_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'story_activities' and index_name = 'idx_story_activities_difficulty') = 0,
    'create index idx_story_activities_difficulty on story_activities (difficulty)',
    'select 1'
);
prepare add_story_difficulty_index_stmt from @add_story_difficulty_index;
execute add_story_difficulty_index_stmt;
deallocate prepare add_story_difficulty_index_stmt;

set @add_tracing_created_by_user_id_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_created_by_user_id') = 0,
    'create index idx_tracing_exercises_created_by_user_id on tracing_exercises (created_by_user_id)',
    'select 1'
);
prepare add_tracing_created_by_user_id_index_stmt from @add_tracing_created_by_user_id_index;
execute add_tracing_created_by_user_id_index_stmt;
deallocate prepare add_tracing_created_by_user_id_index_stmt;

set @add_tracing_hsk_level_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_hsk_level') = 0,
    'create index idx_tracing_exercises_hsk_level on tracing_exercises (hsk_level)',
    'select 1'
);
prepare add_tracing_hsk_level_index_stmt from @add_tracing_hsk_level_index;
execute add_tracing_hsk_level_index_stmt;
deallocate prepare add_tracing_hsk_level_index_stmt;

set @add_tracing_age_band_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_age_band') = 0,
    'create index idx_tracing_exercises_age_band on tracing_exercises (age_band)',
    'select 1'
);
prepare add_tracing_age_band_index_stmt from @add_tracing_age_band_index;
execute add_tracing_age_band_index_stmt;
deallocate prepare add_tracing_age_band_index_stmt;

set @add_tracing_difficulty_index = if(
    (select count(*) from information_schema.statistics where table_schema = database() and table_name = 'tracing_exercises' and index_name = 'idx_tracing_exercises_difficulty') = 0,
    'create index idx_tracing_exercises_difficulty on tracing_exercises (difficulty)',
    'select 1'
);
prepare add_tracing_difficulty_index_stmt from @add_tracing_difficulty_index;
execute add_tracing_difficulty_index_stmt;
deallocate prepare add_tracing_difficulty_index_stmt;

set @add_drag_drop_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'drag_drop_activities' and constraint_name = 'FKei5kgr46vhjn8qfk9a6d7a5yl') = 0,
    'alter table drag_drop_activities add constraint FKei5kgr46vhjn8qfk9a6d7a5yl foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_drag_drop_created_by_user_id_fk_stmt from @add_drag_drop_created_by_user_id_fk;
execute add_drag_drop_created_by_user_id_fk_stmt;
deallocate prepare add_drag_drop_created_by_user_id_fk_stmt;

set @add_flashcards_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'flashcards' and constraint_name = 'FKmss52266jmxyysm5uijhf63ie') = 0,
    'alter table flashcards add constraint FKmss52266jmxyysm5uijhf63ie foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_flashcards_created_by_user_id_fk_stmt from @add_flashcards_created_by_user_id_fk;
execute add_flashcards_created_by_user_id_fk_stmt;
deallocate prepare add_flashcards_created_by_user_id_fk_stmt;

set @add_listening_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'listening_exercises' and constraint_name = 'FKlo1t6skrjponktm3nck9suoww') = 0,
    'alter table listening_exercises add constraint FKlo1t6skrjponktm3nck9suoww foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_listening_created_by_user_id_fk_stmt from @add_listening_created_by_user_id_fk;
execute add_listening_created_by_user_id_fk_stmt;
deallocate prepare add_listening_created_by_user_id_fk_stmt;

set @add_matching_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'matching_games' and constraint_name = 'FK9qopobo92p8ga0io07cq6xbcr') = 0,
    'alter table matching_games add constraint FK9qopobo92p8ga0io07cq6xbcr foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_matching_created_by_user_id_fk_stmt from @add_matching_created_by_user_id_fk;
execute add_matching_created_by_user_id_fk_stmt;
deallocate prepare add_matching_created_by_user_id_fk_stmt;

set @add_story_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'story_activities' and constraint_name = 'FKh522tgfw3txujcfbdl4k5bod7') = 0,
    'alter table story_activities add constraint FKh522tgfw3txujcfbdl4k5bod7 foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_story_created_by_user_id_fk_stmt from @add_story_created_by_user_id_fk;
execute add_story_created_by_user_id_fk_stmt;
deallocate prepare add_story_created_by_user_id_fk_stmt;

set @add_tracing_created_by_user_id_fk = if(
    (select count(*) from information_schema.table_constraints where table_schema = database() and table_name = 'tracing_exercises' and constraint_name = 'FK87jluuqqc6mam1kxbthcy4qvm') = 0,
    'alter table tracing_exercises add constraint FK87jluuqqc6mam1kxbthcy4qvm foreign key (created_by_user_id) references users (id)',
    'select 1'
);
prepare add_tracing_created_by_user_id_fk_stmt from @add_tracing_created_by_user_id_fk;
execute add_tracing_created_by_user_id_fk_stmt;
deallocate prepare add_tracing_created_by_user_id_fk_stmt;
