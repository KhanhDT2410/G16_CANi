import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { getShiftStaffingSummary } from '../src/pages/eo/utils/eventHelpers.js';

describe('Emergency Dispatch Auto Prompt Eligibility', () => {
  const now = new Date('2026-11-23T10:00:00Z');

  it('Ca cận giờ và thiếu nhân sự sẽ đủ điều kiện tự động đề xuất', () => {
    const shift = {
      shiftId: 's-auto',
      jobType: 'Phục vụ bàn',
      status: 'ACTIVE',
      requiredWorkers: 5,
      confirmedWorkers: 2,
      startTime: new Date(now.getTime() + 2 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 6 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const event = { eventId: 'e-auto', mode: 'MATCHING', status: 'ACTIVE', shifts: [shift] };

    const summary = getShiftStaffingSummary(shift, event, now, 4);
    assert.equal(summary.nearStartUnderstaffed, true);
    assert.equal(summary.canTriggerEmergency, true);
    assert.equal(summary.isEmergency, false);
    assert.equal(summary.missing, 3);
  });

  it('Ca đã bật emergency hoặc đủ người thì không tự động đề xuất', () => {
    const shiftActiveEmergency = {
      shiftId: 's-em',
      jobType: 'Lễ tân',
      status: 'ACTIVE',
      requiredWorkers: 4,
      confirmedWorkers: 2,
      startTime: new Date(now.getTime() + 1 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 5 * 3600 * 1000).toISOString(),
      emergency: true,
    };
    const event = { eventId: 'e-em', mode: 'MATCHING', status: 'ACTIVE', shifts: [shiftActiveEmergency] };

    const summary = getShiftStaffingSummary(shiftActiveEmergency, event, now, 4);
    assert.equal(summary.isEmergency, true);
    assert.equal(summary.canTriggerEmergency, false);
  });

  it('Ca ở chế độ INVITE không tự động đề xuất Emergency Dispatch', () => {
    const shiftInvite = {
      shiftId: 's-invite',
      jobType: 'Check-in',
      status: 'ACTIVE',
      requiredWorkers: 4,
      confirmedWorkers: 1,
      startTime: new Date(now.getTime() + 1 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 5 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const event = { eventId: 'e-invite', mode: 'INVITE', status: 'ACTIVE', shifts: [shiftInvite] };

    const summary = getShiftStaffingSummary(shiftInvite, event, now, 4);
    assert.equal(summary.isInviteMode, true);
    assert.equal(summary.canTriggerEmergency, false);
  });
});
