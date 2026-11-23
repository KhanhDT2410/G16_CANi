import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  getShiftStaffingSummary,
  getShiftStaffingAlerts,
  getEventUnderstaffedSummary,
} from '../src/pages/eo/utils/eventHelpers.js';

describe('Shift Understaffed Alerts & Emergency Eligibility Helpers', () => {
  const now = new Date('2026-11-23T10:00:00Z');

  it('Còn hơn 4 giờ -> chưa có cảnh báo sát giờ', () => {
    const shift = {
      shiftId: 's1',
      jobType: 'Lễ tân',
      status: 'ACTIVE',
      requiredWorkers: 6,
      confirmedWorkers: 2,
      startTime: new Date(now.getTime() + 5 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 9 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const event = { eventId: 'e1', mode: 'MATCHING', status: 'ACTIVE', shifts: [shift] };

    const summary = getShiftStaffingSummary(shift, event, now, 4);
    assert.equal(summary.nearStartUnderstaffed, false);
    assert.equal(getShiftStaffingAlerts([shift], event, now, 4).length, 0);
    assert.equal(getEventUnderstaffedSummary(event, now, 4).count, 0);
  });

  it('Vào khung 4 giờ và thiếu người -> hiện cảnh báo đúng định dạng và số ca trên sự kiện', () => {
    const start1 = new Date(now.getTime() + 2 * 3600 * 1000).toISOString();
    const end1 = new Date(now.getTime() + 6 * 3600 * 1000).toISOString();
    const s1 = {
      shiftId: 's1',
      jobType: 'Lễ tân',
      status: 'ACTIVE',
      requiredWorkers: 6,
      confirmedWorkers: 4,
      startTime: start1,
      endTime: end1,
      emergency: false,
    };
    const s2 = {
      shiftId: 's2',
      jobType: 'Âm thanh',
      status: 'ACTIVE',
      requiredWorkers: 3,
      confirmedWorkers: 1,
      startTime: start1,
      endTime: end1,
      emergency: false,
    };
    const s3Full = {
      shiftId: 's3',
      jobType: 'Phục vụ',
      status: 'FULLY_STAFFED',
      requiredWorkers: 5,
      confirmedWorkers: 5,
      startTime: start1,
      endTime: end1,
      emergency: false,
    };

    const event = { eventId: 'e1', mode: 'MATCHING', status: 'ACTIVE', shifts: [s1, s2, s3Full] };
    const eventSummary = getEventUnderstaffedSummary(event, now, 4);

    assert.equal(eventSummary.count, 2);
    assert.equal(eventSummary.alerts[0].missing, 2);
    assert.ok(eventSummary.alerts[0].summaryLine.includes('Ca Lễ tân bắt đầu lúc'));
    assert.ok(eventSummary.alerts[0].summaryLine.includes('Đã chốt 4/6 · Còn thiếu 2 người'));
    assert.equal(eventSummary.alerts[0].canTriggerEmergency, true);
    assert.equal(eventSummary.alerts[1].missing, 2);
    assert.equal(eventSummary.alerts[1].canTriggerEmergency, true);
  });

  it('Sự kiện INVITE: vẫn báo thiếu người nhưng không cho phép Kích hoạt khẩn cấp', () => {
    const sInvite = {
      shiftId: 's-inv',
      jobType: 'Check-in',
      status: 'ACTIVE',
      requiredWorkers: 4,
      confirmedWorkers: 2,
      startTime: new Date(now.getTime() + 2 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 6 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const event = { eventId: 'e-inv', mode: 'INVITE', status: 'ACTIVE', shifts: [sInvite] };
    const summary = getShiftStaffingSummary(sInvite, event, now, 4);

    assert.equal(summary.nearStartUnderstaffed, true);
    assert.equal(summary.isInviteMode, true);
    assert.equal(summary.canTriggerEmergency, false);
  });

  it('Ca IN_PROGRESS: vẫn cảnh báo thiếu người nhưng ẩn nút kích hoạt khẩn cấp', () => {
    const sRunning = {
      shiftId: 's-run',
      jobType: 'Bảo vệ',
      status: 'IN_PROGRESS',
      requiredWorkers: 4,
      confirmedWorkers: 3,
      startTime: new Date(now.getTime() - 30 * 60 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 4 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const event = { eventId: 'e1', mode: 'MATCHING', status: 'ONGOING', shifts: [sRunning] };
    const summary = getShiftStaffingSummary(sRunning, event, now, 4);

    assert.equal(summary.isInProgress, true);
    assert.equal(summary.nearStartUnderstaffed, true);
    assert.equal(summary.canTriggerEmergency, false);
  });

  it('Ca đã bật Emergency với 0 ứng viên vs có ứng viên', () => {
    const base = {
      shiftId: 's-em',
      jobType: 'Pha chế',
      status: 'ACTIVE',
      requiredWorkers: 4,
      confirmedWorkers: 1,
      startTime: new Date(now.getTime() + 1 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 5 * 3600 * 1000).toISOString(),
      emergency: true,
    };
    const event = { eventId: 'e1', mode: 'MATCHING', status: 'ACTIVE' };

    const zeroCandidates = getShiftStaffingSummary({ ...base, pendingProposalCount: 0 }, event, now, 4);
    assert.equal(zeroCandidates.isEmergency, true);
    assert.equal(zeroCandidates.canTriggerEmergency, false);
    assert.equal(zeroCandidates.hasCandidates, false);

    const withCandidates = getShiftStaffingSummary({ ...base, pendingProposalCount: 3 }, event, now, 4);
    assert.equal(withCandidates.hasCandidates, true);
    assert.equal(withCandidates.canTriggerEmergency, false);
  });

  it('Ca đã đủ người -> gỡ cảnh báo đang hoạt động và nút kích hoạt', () => {
    const fullShift = {
      shiftId: 's-full',
      jobType: 'Lễ tân',
      status: 'FULLY_STAFFED',
      requiredWorkers: 6,
      confirmedWorkers: 6,
      startTime: new Date(now.getTime() + 1 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 5 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const event = { eventId: 'e1', mode: 'MATCHING', status: 'ACTIVE', shifts: [fullShift] };
    const summary = getShiftStaffingSummary(fullShift, event, now, 4);

    assert.equal(summary.nearStartUnderstaffed, false);
    assert.equal(summary.canTriggerEmergency, false);
    assert.equal(getShiftStaffingAlerts([fullShift], event, now, 4).length, 0);
  });
});
