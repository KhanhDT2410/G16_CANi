import { useState, useEffect, useRef } from 'react';
import { getShiftStaffingSummary } from '../utils/eventHelpers';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

const DISMISSED_STORAGE_KEY_PREFIX = 'cani_eo_dismissed_emergency_';

/**
 * Custom hook tự động kiểm tra ca làm việc sắp diễn ra mà chưa đủ người
 * và đề xuất Popup mở rộng phạm vi tìm kiếm khẩn cấp cho Event Organizer (Ticket EO-009).
 */
export function useEmergencyAutoPrompt({ events = [], onReloadEvents }) {
  const [promptTarget, setPromptTarget] = useState(null); // { shift, event }
  const [isSubmitting, setIsSubmitting] = useState(false);
  const activePromptIdRef = useRef(null);
  const pendingPromptShiftIdRef = useRef(null);

  // Lắng nghe sự kiện người dùng bấm vào thông báo chuông khẩn cấp (Ticket EO-011)
  useEffect(() => {
    const handleOpenPrompt = (e) => {
      const shiftId = Number(e.detail?.shiftId);
      if (!shiftId) return;
      if (typeof window !== 'undefined') {
        sessionStorage.removeItem(DISMISSED_STORAGE_KEY_PREFIX + shiftId);
      }
      let foundPair = null;
      if (Array.isArray(events)) {
        for (const evt of events) {
          if (!evt || !Array.isArray(evt.shifts)) continue;
          const s = evt.shifts.find((item) => item.shiftId === shiftId);
          if (s) {
            foundPair = { shift: s, event: evt };
            break;
          }
        }
      }

      if (foundPair) {
        activePromptIdRef.current = shiftId;
        setPromptTarget(foundPair);
        pendingPromptShiftIdRef.current = null;
      } else {
        pendingPromptShiftIdRef.current = shiftId;
      }
    };

    window.addEventListener('cani_open_emergency_prompt', handleOpenPrompt);
    return () => window.removeEventListener('cani_open_emergency_prompt', handleOpenPrompt);
  }, [events]);

  useEffect(() => {
    if (!Array.isArray(events) || events.length === 0) return;
    if (promptTarget) return; // Đang hiển thị popup cho một ca, không ghi đè

    // Nếu có ca đang chờ kích hoạt từ Notification Bell
    if (pendingPromptShiftIdRef.current) {
      const pendingId = pendingPromptShiftIdRef.current;
      for (const evt of events) {
        if (!evt || !Array.isArray(evt.shifts)) continue;
        const shift = evt.shifts.find((s) => s.shiftId === pendingId);
        if (shift) {
          pendingPromptShiftIdRef.current = null;
          activePromptIdRef.current = shift.shiftId;
          setPromptTarget({ shift, event: evt });
          return;
        }
      }
    }

    const now = new Date();
    const candidatePairs = [];

    for (const evt of events) {
      if (!evt || !Array.isArray(evt.shifts)) continue;
      for (const shift of evt.shifts) {
        if (!shift || !shift.shiftId) continue;
        const summary = getShiftStaffingSummary(shift, evt, now);

        // Điều kiện tự động đề xuất: Ca sắp bắt đầu + thiếu người + chưa bật khẩn cấp + đủ điều kiện bật
        if (summary.nearStartUnderstaffed && summary.canTriggerEmergency && !summary.isEmergency) {
          const isDismissed =
            typeof window !== 'undefined' &&
            Boolean(sessionStorage.getItem(DISMISSED_STORAGE_KEY_PREFIX + shift.shiftId));

          if (!isDismissed) {
            candidatePairs.push({
              shift,
              event: evt,
              startTime: new Date(shift.startTime).getTime(),
            });
          }
        }
      }
    }

    if (candidatePairs.length > 0) {
      // Ưu tiên ca sắp diễn ra sớm nhất
      candidatePairs.sort((a, b) => a.startTime - b.startTime);
      const chosen = candidatePairs[0];

      if (activePromptIdRef.current !== chosen.shift.shiftId) {
        activePromptIdRef.current = chosen.shift.shiftId;
        setPromptTarget({ shift: chosen.shift, event: chosen.event });
      }
    }
  }, [events, promptTarget]);

  const handleAccept = async (shiftId, radiusKm) => {
    setIsSubmitting(true);
    try {
      const result = await eoService.enableEmergencyDispatch(shiftId, radiusKm);
      const dispatched = Number(result?.dispatchedCount || 0);
      const appliedRadius = Number(result?.effectiveRadiusKm || radiusKm);

      if (dispatched > 0) {
        showToast(
          `Đã kích hoạt điều phối khẩn cấp (${appliedRadius} km): Gửi ${dispatched} đề xuất đến Worker phù hợp theo Preference.`,
          'success'
        );
      } else {
        showToast(
          `Đã mở rộng phạm vi điều phối (${appliedRadius} km) · Chưa tìm được Worker phù hợp ngay lúc này.`,
          'info'
        );
      }

      setPromptTarget(null);
      activePromptIdRef.current = null;
      await onReloadEvents?.();
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Không thể kích hoạt điều phối khẩn cấp.';
      showToast(msg, 'error');
      setPromptTarget(null);
      activePromptIdRef.current = null;
      await onReloadEvents?.();
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDecline = (shiftId) => {
    if (shiftId && typeof window !== 'undefined') {
      sessionStorage.setItem(DISMISSED_STORAGE_KEY_PREFIX + shiftId, Date.now().toString());
    }
    setPromptTarget(null);
    activePromptIdRef.current = null;
    showToast('Đã giữ nguyên trạng thái tìm kiếm ban đầu của ca làm việc.', 'info');
  };

  return {
    promptTarget,
    isSubmitting,
    handleAccept,
    handleDecline,
  };
}
