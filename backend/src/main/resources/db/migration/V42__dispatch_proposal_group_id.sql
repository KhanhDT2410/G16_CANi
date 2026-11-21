ALTER TABLE dispatch_proposals ADD COLUMN group_id UUID NULL;
CREATE INDEX idx_dispatch_proposals_group ON dispatch_proposals (group_id, status);

-- V1 declared CHECK (source IN ('MATCHING','DIRECT','BULK')) inline without a name,
-- so PostgreSQL auto-named it dispatch_proposals_source_check.
ALTER TABLE dispatch_proposals DROP CONSTRAINT dispatch_proposals_source_check;
ALTER TABLE dispatch_proposals ADD CONSTRAINT dispatch_proposals_source_check
    CHECK (source IN ('MATCHING', 'DIRECT', 'BULK', 'GROUP'));
