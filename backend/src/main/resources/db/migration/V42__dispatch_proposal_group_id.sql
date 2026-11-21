ALTER TABLE dispatch_proposals ADD COLUMN group_id UUID NULL;
CREATE INDEX idx_dispatch_proposals_group ON dispatch_proposals (group_id, status);
