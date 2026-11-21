# UC-70 / UC-71 Extension — Worker Group Matching Implementation

**Reference:** Worker group matching implemented per design doc 2026-11-21; UC-70/71 extended with group-aware dispatch, mixed fill, atomic slots.

---

## Context

Original SRS (SEP490-G16-Report-3) defines:
- **UC-70: Create Worker Group** — Worker (Leader) creates a new worker group
- **UC-71: Manage Worker Group** — Leader updates group info, invites/removes members, transfers leadership

This extension adds **Group-Aware Matching & Dispatch** capabilities (FC-MATCH-06) that integrate with the existing Matching & Dispatch Engine (FC-MATCH-01..05, UC-59).

---

## New Functional Scope (Extended from UC-70/71)

### UC-70 Extended: Group Creation → Dispatch Readiness
When a group is created (UC-70) and has ≥2 ACTIVE members, it becomes eligible for **Group Dispatch Channel**. No additional API call needed; the background scheduler (`MatchingDispatchService.dispatchForShift()`) automatically includes active groups.

### UC-71 Extended: Group Management → Dispatch Control
| UC-71 Original | Extended Capability |
|----------------|---------------------|
| Leader views group shifts (`GET /{id}/shifts`) | Leader views **group proposals** (`GET /{id}/group-proposals`) — PENDING + EXPIRED 24h |
| Leader manages members | Leader **accepts/rejects group proposals** (all-or-nothing, atomic slots) |
| Leader transfers leadership (`POST /{id}/transfer-leader`) | Transfer **auto-expires orphaned PENDING group proposals** (atomic role swap + proposal expiry) |

---

## Key Business Rules Implemented (BR-GM-01..05)

| Rule ID | Description | Enforcement Point |
|---------|-------------|-------------------|
| BR-GM-01 | **Two dispatch channels run in parallel**: Individual (existing) + Group (new). `dispatchForShift()` = individual count + group count. | `MatchingDispatchService.dispatchForShift()` line 225 |
| BR-GM-02 | **Group proposal targets Leader**: `DispatchProposal.worker = leaderWorkerId`, `source = GROUP`, `group_id` set. FCM sent to Leader. | `MatchingDispatchService.dispatchGroupsForShift()` lines 743-772 |
| BR-GM-03 | **Member eligibility** = hard filter minus `isOnline` check. No-show cooldown 20 min per group (`worker_groups.last_no_show_at`). | `MatchingDispatchService.groupMemberEligibilityReason()` + cooldown check line 656-659 |
| BR-GM-04 | **Atomic slot claim**: Leader selects k members → `shiftRepository.incrementConfirmedWorkersAtomicBy(k)` → if 0 returned (race), full rollback, 409 Conflict. | `WorkerGroupService.acceptGroupProposal()` lines 604-625 (test T3-3) |
| BR-GM-05 | **All-or-nothing**: Any member conflict/ineligible → entire accept rejected, 0 assignments persisted. | `WorkerGroupService.acceptGroupProposal()` lines 628-639 (test T3-4) |
| BR-GM-06 | **Leadership transfer** (UC-71) swaps 3 sources of truth atomically: membership roles, `worker_groups.leader_worker_id`, and expires all `PENDING` group proposals. | `WorkerGroupService.transferLeader()` + `expireStalePendingProposals()` cron |

---

## Data Model Changes (Migrations V42, V43)

| Migration | Table | Change |
|-----------|-------|--------|
| V42 | `dispatch_proposals` | `ADD COLUMN group_id UUID NULL`, `source CHECK` now includes `'GROUP'`, index on `(group_id, status)` |
| V43 | `worker_groups` | `ADD COLUMN last_no_show_at TIMESTAMPTZ NULL` (20-min cooldown tracker) |

See `docs/database/migrations.md` for full SQL.

---

## API Endpoints Added (5 new)

| # | Method | Path | Description |
|---|--------|------|-------------|
| 1 | GET | `/api/v1/worker-groups/{id}/group-proposals` | List PENDING + EXPIRED(24h) group proposals (Leader only) |
| 2 | GET | `/api/v1/worker-groups/{id}/group-proposals/eligible?proposalId=` | Re-check member eligibility for a proposal (Leader only) |
| 3 | POST | `/api/v1/worker-groups/{id}/group-proposals/{proposalId}/accept` | Accept group proposal: atomic k-slot claim, all-or-nothing (Leader only) |
| 4 | POST | `/api/v1/worker-groups/{id}/group-proposals/{proposalId}/reject` | Reject group proposal → triggers re-dispatch (Leader only) |
| 5 | POST | `/api/v1/worker-groups/{id}/transfer-leader` | Transfer leadership: atomic role swap + expire orphaned proposals (Leader/Admin) |

Full API spec: `docs/api/worker-group-matching-api.md`

---

## Test Coverage (Static Verification)

All new/modified test classes verified for:
- **Strict stubbing safety**: Uses `@ExtendWith(MockitoExtension.class)`, no `UnnecessaryStubbingException` risk
- **Assertions match spec values**: RS penalties, cooldown minutes, slot counts, status transitions
- **Failure paths assert NO side effects**: `verify(..., never()).save(...)` on 400/409 paths (T3-2, T3-3, T3-4)
- **No pre-existing test deleted/changed**: Test count preserved; only new `@Test` methods added

**Test classes touched by Tasks 1-8:**
1. `MatchingDispatchServiceTest` — Group dispatch channel (GROUP001..007), individual matching regression (MATCH001..018)
2. `WorkerGroupServiceTest` — UC-70/71 original (UTC080..094) + Task 3 group proposals (T3-1..T3-7) + Task 5 transfer leader (T5-1..T5-4)
3. `WorkerJobServiceTest` — Individual worker flow regression (UTC001..UTC101) + P10 group info attachment
4. `NoShowServiceTest` — No-show marking with group cooldown (V43 `last_no_show_at`)

---

## Acceptance Criteria Met (per Task 9 Brief)

- [x] **AC8 spec**: Individual matching behavior unchanged (verified via `MatchingDispatchServiceTest` MATCH001..018)
- [x] **Group dispatch channel**: `dispatchGroupsForShift()` runs alongside individual channel
- [x] **Mixed fill**: Individual + Group proposals coexist for same shift
- [x] **Atomic slots**: `incrementConfirmedWorkersAtomicBy(k)` with 409 on race
- [x] **All-or-nothing**: Single member conflict → full rollback
- [x] **20-min cooldown**: `last_no_show_at` on group, enforced in dispatch
- [x] **Orphaned proposal expiry**: Leadership transfer → PENDING group proposals → EXPIRED
- [x] **API docs**: `docs/api/worker-group-matching-api.md` (5 endpoints, error codes, examples)
- [x] **Migration notes**: `docs/database/migrations.md` (V42, V43)
- [x] **SRS extension**: This document (`UC70-UC71-group-matching-extension.md`)

---

## Risks / Known Limits

| Area | Limit | Mitigation |
|------|-------|------------|
| Group dispatch scale | `findAll()` limited to 50 groups (line 615-618) | `shortcut: 50 groups cap; upgrade to ranked DB query when groups > 1000` |
| Member eligibility re-check | Runs in-memory per proposal accept | Acceptable for max 20 members/group; upgrade to batch query if needed |
| Orphaned proposal detection | Cron every 5 min, scans PENDING + recent group proposals | Bounded query by `createdAt > now-24h`; no full table scan |

---

## Merge Instruction for User

This file (`docs/requirements/UC70-UC71-group-matching-extension.md`) is a **companion reference** to be merged into the main SRS/SDS docx (`SEP490-G16-Report-3_Software Requirement Specification.docx`, `SEP490-G16-Report-4_Software Design Specification.docx`) at the end of the UC-70/UC-71 sections. Suggested insertion point: after "7.10 UC-71: Manage Worker Group" in SRS Report-3.