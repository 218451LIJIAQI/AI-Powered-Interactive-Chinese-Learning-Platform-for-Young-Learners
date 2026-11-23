-- "Pick this first" on the plan's road map is kept with the plan (2026-11-17), so Home shows the class the learner
-- picked (Figma S3.19/S3.20) on any device. It was kept in the browser before, so nothing is filled in; a rebuilt plan
-- carries the pick over while the class is still in it.
ALTER TABLE `learning_plans` ADD COLUMN `picked_class_id` bigint DEFAULT NULL;
