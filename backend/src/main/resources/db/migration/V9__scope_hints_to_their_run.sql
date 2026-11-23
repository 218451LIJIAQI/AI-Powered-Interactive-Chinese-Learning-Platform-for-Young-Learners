-- Older hints retain their delivery time for answer history and for runs opened before this upgrade.
ALTER TABLE served_hints
  ADD COLUMN run_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  ADD INDEX idx_served_hints_student_run (student_id, run_id, item_ref);
