-- Cancelling an admin invite keeps it, marked as cancelled, so that the Undo in the toast can bring back the same link
-- (2026-11-17). A cancelled invite is not listed as pending and its link is refused; Undo or sending it again clears
-- the mark. Invites cancelled before were deleted, so nothing is filled in.
ALTER TABLE `admin_invites` ADD COLUMN `cancelled_at` datetime(6) DEFAULT NULL;
