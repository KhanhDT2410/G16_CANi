# Database Migration Log

| Version | File | Description | Date | Author |
|---------|------|-------------|------|--------|
| V42 | `V42__dispatch_proposal_group_id.sql` | Add `group_id` column to `dispatch_proposals` table to support Group-Aware Matching (FC-MATCH-06). Update `source` CHECK constraint to include `'GROUP'` value. Add index on `(group_id, status)`. | 2026-11-21 | QuyTH |
| V43 | `V43__worker_group_last_no_show_at.sql` | Add `last_no_show_at` column to `worker_groups` table to track 20-minute no-show cooldown for Group Dispatch channel. | 2026-11-21 | QuyTH |

---

## V42 Details — `dispatch_proposals.group_id` + Source CHECK

**File:** `backend/src/main/resources/db/migration/V42__dispatch_proposal_group_id.sql`

```sql
ALTER TABLE dispatch_proposals ADD COLUMN group_id UUID NULL;
CREATE INDEX idx_dispatch_proposals_group ON dispatch_proposals (group_id, status);

-- V1 declared CHECK (source IN ('MATCHING','DIRECT','BULK')) inline without a name,
-- so PostgreSQL auto-named it dispatch_proposals_source_check.
ALTER TABLE dispatch_proposals DROP CONSTRAINT dispatch_proposals_source_check;
ALTER TABLE dispatch_proposals ADD CONSTRAINT dispatch_proposals_source_check
    CHECK (source IN ('MATCHING', 'DIRECT', 'BULK', 'GROUP'));
```

**Purpose:** Enable `DispatchProposal` to carry group context when sourced from Group Dispatch channel (`AssignmentSource.GROUP`). The `group_id` links proposal to `worker_groups.id`. The CHECK constraint update allows `source = 'GROUP'` for proposals created by `MatchingDispatchService.dispatchGroupsForShift()`.

**Related entities:**
- `DispatchProposal` entity: added `groupId` field
- `MatchingDispatchService.dispatchGroupsForShift()`: sets `source=GROUP`, populates `groupId`
- `WorkerGroupService.acceptGroupProposal()`: reads `proposal.getGroupId()` to attach to `ShiftAssignment`

---

## V43 Details — `worker_groups.last_no_show_at`

**File:** `backend/src/main/resources/db/migration/V43__worker_group_last_no_show_at.sql`

```sql
ALTER TABLE worker_groups ADD COLUMN last_no_show_at TIMESTAMPTZ NULL;
```

**Purpose:** Track the timestamp of the most recent no-show by any member of a worker group. Used by Group Dispatch channel to enforce a **20-minute cooldown**: if a group has `last_no_show_at` within the last 20 minutes, `dispatchGroupsForShift()` skips that group.

**Flow:**
1. `NoShowService.markNoShow(assignmentId, ...)` detects assignment has `group_id` → loads `WorkerGroup` → sets `group.setLastNoShowAt(Instant.now())` → saves.
2. `MatchingDispatchService.dispatchGroupsForShift()` checks `group.getLastNoShowAt().isAfter(now.minus(20 min))` → if true, skip group.
3. Cooldown auto-expires after 20 minutes; no manual cleanup needed.

**Related code:**
- `NoShowService.markNoShow()` (lines ~172-190 in test): sets `lastNoShowAt` on group
- `MatchingDispatchService.dispatchGroupsForShift()` (lines ~656-659): reads cooldown
- `WorkerGroup` entity: added `lastNoShowAt` field

---

## Verification Notes

- Both migrations are **additive only** (no data loss, no column drops).
- V42 is backward-compatible: existing rows have `group_id = NULL`, `source` values unchanged.
- V43 is backward-compatible: existing rows have `last_no_show_at = NULL` (no cooldown applied).
- Run order: V42 → V43 (no cross-dependency, but logical sequence).