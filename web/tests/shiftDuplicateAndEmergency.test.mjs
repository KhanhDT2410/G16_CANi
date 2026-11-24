import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { validateDuplicateShift } from '../src/pages/eo/utils/shiftValidation.js';
import { getShiftStaffingSummary, formatVndWhole, roundVndAmount, getShiftEditRestrictions } from '../src/pages/eo/utils/eventHelpers.js';

describe('Duplicate shift validation (same role + same hours)', () => {
  const existing = [
    {
      shiftId: 's1',
      jobType: 'Lễ tân / Check-in',
      status: 'ACTIVE',
      startTime: '2026-11-26T08:00:00',
      endTime: '2026-11-26T12:00:00',
    },
    {
      shiftId: 's2',
      jobType: 'Phục vụ bàn',
      status: 'CANCELLED',
      startTime: '2026-11-26T08:00:00',
      endTime: '2026-11-26T12:00:00',
    },
  ];

  it('Chặn tạo ca trùng tên vị trí (không phân biệt hoa/thường, khoảng trắng) và cùng khung giờ', () => {
    const err = validateDuplicateShift(
      {
        jobType: '  lễ tân /   check-in ',
        startTime: '2026-11-26T08:00',
        endTime: '2026-11-26T12:00',
      },
      existing
    );
    assert.ok(err && err.includes('Đã tồn tại ca'));
  });

  it('Cho phép cùng vị trí ở khung giờ khác (ca sáng vs ca chiều)', () => {
    const err = validateDuplicateShift(
      {
        jobType: 'Lễ tân / Check-in',
        startTime: '2026-11-26T13:00',
        endTime: '2026-11-26T17:00',
      },
      existing
    );
    assert.equal(err, null);
  });

  it('Cho phép khác vị trí ở cùng khung giờ hoặc trùng với ca đã CANCELLED', () => {
    const errDiffRole = validateDuplicateShift(
      {
        jobType: 'Bảo vệ / An ninh',
        startTime: '2026-11-26T08:00',
        endTime: '2026-11-26T12:00',
      },
      existing
    );
    const errCancelled = validateDuplicateShift(
      {
        jobType: 'Phục vụ bàn',
        startTime: '2026-11-26T08:00',
        endTime: '2026-11-26T12:00',
      },
      existing
    );
    assert.equal(errDiffRole, null);
    assert.equal(errCancelled, null);
  });

  it('Bỏ qua chính ca đang sửa khi truyền excludeShiftId hoặc excludeIndex', () => {
    assert.equal(
      validateDuplicateShift(
        {
          jobType: 'Lễ tân / Check-in',
          startTime: '2026-11-26T08:00',
          endTime: '2026-11-26T12:00',
        },
        existing,
        { excludeShiftId: 's1' }
      ),
      null
    );
    assert.equal(
      validateDuplicateShift(
        {
          jobType: 'Lễ tân / Check-in',
          startTime: '2026-11-26T08:00',
          endTime: '2026-11-26T12:00',
        },
        existing,
        { excludeIndex: 0 }
      ),
      null
    );
  });
});

describe('Emergency button near-start window gating', () => {
  const now = new Date('2026-11-24T10:00:00Z');
  const event = { eventId: 'e1', mode: 'MATCHING', status: 'ACTIVE' };

  it('Còn > 4h trước giờ bắt đầu -> khóa nút kích hoạt khẩn cấp (nearStartUnderstaffed = false)', () => {
    const shift = {
      shiftId: 's-far',
      jobType: 'Lễ tân',
      status: 'ACTIVE',
      requiredWorkers: 4,
      confirmedWorkers: 1,
      startTime: new Date(now.getTime() + 6 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 10 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const summary = getShiftStaffingSummary(shift, event, now, 4);
    assert.equal(summary.nearStartUnderstaffed, false);
    assert.equal(summary.canTriggerEmergency, true);
  });

  it('Trong vòng <= 4h trước giờ bắt đầu -> mở khóa nút kích hoạt khẩn cấp', () => {
    const shift = {
      shiftId: 's-near',
      jobType: 'Lễ tân',
      status: 'ACTIVE',
      requiredWorkers: 4,
      confirmedWorkers: 1,
      startTime: new Date(now.getTime() + 2 * 3600 * 1000).toISOString(),
      endTime: new Date(now.getTime() + 6 * 3600 * 1000).toISOString(),
      emergency: false,
    };
    const summary = getShiftStaffingSummary(shift, event, now, 4);
    assert.equal(summary.nearStartUnderstaffed, true);
    assert.equal(summary.canTriggerEmergency, true);
  });
});

describe('Currency whole-VNĐ formatting and rounding helpers', () => {
  it('Làm tròn số tiền lẻ (106166.667 / 106166.67) về số nguyên VNĐ chuẩn không thập phân', () => {
    assert.equal(roundVndAmount(106166.667), 106167);
    assert.equal(roundVndAmount('106166.67'), 106167);
    assert.equal(roundVndAmount(null), 0);

    const formatted = formatVndWhole(106166.667);
    assert.equal(formatted.includes(','), false);
    assert.ok(formatted.endsWith(' đ'));
    assert.equal(formatVndWhole(null), '—');
  });
});

describe('Shift edit restrictions when shift has already started', () => {
  const now = new Date('2026-11-26T10:00:00Z');

  it('Trước giờ bắt đầu ca hoặc bản nháp -> mở khóa toàn bộ giờ, tiền và số lượng', () => {
    const futureShift = {
      status: 'ACTIVE',
      startTime: '2026-11-26T12:00:00Z',
      endTime: '2026-11-26T16:00:00Z',
      confirmedWorkers: 2,
    };
    const r1 = getShiftEditRestrictions(futureShift, { status: 'ACTIVE' }, now);
    assert.equal(r1.lockTimeAndMoney, false);
    assert.equal(r1.canEditWorkers, true);
    assert.equal(r1.minRequiredWorkers, 1);

    const draftShift = {
      status: 'DRAFT',
      startTime: '2026-11-26T09:00:00Z',
      endTime: '2026-11-26T13:00:00Z',
      confirmedWorkers: 0,
    };
    const r2 = getShiftEditRestrictions(draftShift, { status: 'DRAFT' }, now);
    assert.equal(r2.lockTimeAndMoney, false);
  });

  it('Khi đã vào ca -> khóa thời gian và số tiền, vẫn cho sửa số lượng người >= số đã nhận', () => {
    const startedShift = {
      status: 'FULLY_STAFFED',
      startTime: '2026-11-26T09:00:00Z',
      endTime: '2026-11-26T13:00:00Z',
      confirmedWorkers: 3,
    };
    const r = getShiftEditRestrictions(startedShift, { status: 'ACTIVE' }, now);
    assert.equal(r.hasStarted, true);
    assert.equal(r.lockTimeAndMoney, true);
    assert.equal(r.canEditWorkers, true);
    assert.equal(r.minRequiredWorkers, 3);
  });
});
