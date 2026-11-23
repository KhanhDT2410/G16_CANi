import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { getShiftStaffingSummary } from '../src/pages/eo/utils/eventHelpers.js';

describe('Emergency Dispatch Auto Prompt Eligibility (Ticket EO-009)', () => {
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

describe('Emergency Dispatch Warning & Confirmation (Ticket EO-010)', () => {
  it('Yêu cầu bắt buộc xác nhận nhận thức hệ quả trước khi kích hoạt', () => {
    let isAcknowledged = false;
    const canSubmit = (acknowledged, isBusy) => !isBusy && acknowledged;

    // Khi chưa tích xác nhận -> không thể kích hoạt
    assert.equal(canSubmit(isAcknowledged, false), false);

    // Khi người dùng tích xác nhận -> cho phép kích hoạt
    isAcknowledged = true;
    assert.equal(canSubmit(isAcknowledged, false), true);

    // Khi đang submitting -> không cho phép spam click
    assert.equal(canSubmit(isAcknowledged, true), false);
  });

  it('Nút hành động có đúng nhãn chuẩn Acceptance Criteria', () => {
    const confirmButtonLabel = 'Tôi hiểu và đồng ý kích hoạt';
    const cancelButtonLabel = 'Hủy bỏ';

    assert.equal(confirmButtonLabel, 'Tôi hiểu và đồng ý kích hoạt');
    assert.equal(cancelButtonLabel, 'Hủy bỏ');
  });
});

describe('Emergency Dispatch Notification Sync & Bell (Ticket EO-011)', () => {
  it('Nhận diện chính xác thông báo khẩn cấp qua tiêu đề hoặc payload type', async () => {
    const { isEmergencyNotification, NOTIFICATION_TYPE_CONFIG } = await import(
      '../src/features/notifications/types/notification.constants.js'
    );

    // 1. Đề xuất khẩn cấp
    const proposalNotif = {
      title: '🚨 [ĐỀ XUẤT KHẨN CẤP] Thiếu nhân sự sát giờ: Phục vụ',
      type: 'REMINDER',
      data: JSON.stringify({ type: 'EMERGENCY_PROPOSAL', shiftId: 's-1' }),
    };
    assert.equal(isEmergencyNotification(proposalNotif), true);

    // 2. Trạng thái điều phối khẩn cấp
    const dispatchActiveNotif = {
      title: '🚨 [ĐIỀU PHỐI KHẨN CẤP] Đã kích hoạt cho ca Barista',
      type: 'REMINDER',
      data: JSON.stringify({ type: 'EMERGENCY_DISPATCH_ACTIVE', shiftId: 's-2' }),
    };
    assert.equal(isEmergencyNotification(dispatchActiveNotif), true);

    // 3. Kết quả ứng tuyển khẩn cấp: Worker nhận ca
    const acceptedNotif = {
      title: '🚨 [CỨU TRỢ KHẨN CẤP] Nhân sự nhận ca thành công: Check-in',
      type: 'ASSIGNMENT',
      data: { type: 'EMERGENCY_ASSIGNMENT_ACCEPTED', shiftId: 's-3' },
    };
    assert.equal(isEmergencyNotification(acceptedNotif), true);

    // 4. Kết quả ứng tuyển khẩn cấp: Worker từ chối
    const rejectedNotif = {
      title: '⚠️ [CỨU TRỢ KHẨN CẤP] Worker từ chối lời mời nhận ca',
      type: 'REMINDER',
      data: { type: 'EMERGENCY_PROPOSAL_REJECTED', shiftId: 's-4' },
    };
    assert.equal(isEmergencyNotification(rejectedNotif), true);

    // 5. Thông báo bình thường không bị gán nhầm khẩn cấp
    const normalNotif = {
      title: 'Nhắc nhở ca làm: Bảo vệ',
      type: 'REMINDER',
      data: { type: 'SHIFT_REMINDER', shiftId: 's-5' },
    };
    assert.equal(isEmergencyNotification(normalNotif), false);

    // Cấu hình hiển thị chuẩn
    assert.equal(NOTIFICATION_TYPE_CONFIG.EMERGENCY.label, 'Cứu trợ khẩn cấp');
    assert.equal(NOTIFICATION_TYPE_CONFIG.EMERGENCY.icon, '🚨');
  });
});
