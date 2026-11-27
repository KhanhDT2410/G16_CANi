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

  // Lắng nghe sự kiện người dùng bấm vào thông báo chuông khẩn cấp (Ticket EO-011) hoặc bấm Mở rộng / Quét lại
  useEffect(() => {
    const handleOpenPrompt = (e) => {
      const shiftId = e.detail?.shiftId != null ? String(e.detail.shiftId).trim() : '';
      const mode = e.detail?.mode === 'redispatch' ? 'redispatch' : 'trigger';
      if (!shiftId) return;
      if (typeof window !== 'undefined') {
        sessionStorage.removeItem(DISMISSED_STORAGE_KEY_PREFIX + shiftId);
      }
      let foundPair = null;
      if (Array.isArray(events)) {
        for (const evt of events) {
          if (!evt || !Array.isArray(evt.shifts)) continue;
          const s = evt.shifts.find((item) => String(item.shiftId) === shiftId);
          if (s) {
            foundPair = { shift: s, event: evt, mode };
            break;
          }
        }
      }

      if (foundPair) {
        const summary = getShiftStaffingSummary(foundPair.shift, foundPair.event, new Date());
        if (mode === 'redispatch') {
          if (!summary.isEmergency || !summary.canRedispatchEmergency) {
            showToast(
              summary.emergencyCooldownRemainingSeconds > 0
                ? `Vui lòng chờ ${summary.emergencyCooldownRemainingSeconds} giây trước khi quét lại.`
                : 'Ca làm việc hiện không ở trạng thái có thể quét lại khẩn cấp.',
              'info'
            );
            return;
          }
          activePromptIdRef.current = foundPair.shift.shiftId;
          setPromptTarget(foundPair);
          pendingPromptShiftIdRef.current = null;
          return;
        }

        const allowManualNow = summary.canTriggerEmergency && (summary.withinManualWindow || summary.isInProgress);
        if (!allowManualNow || summary.isEmergency || summary.isInviteMode) {
          showToast(
            summary.isEmergency
              ? 'Ca làm việc đang được điều phối khẩn cấp.'
              : !summary.withinManualWindow && summary.canTriggerEmergency
              ? 'Chỉ có thể kích hoạt điều phối khẩn cấp trong vòng 24 giờ trước giờ bắt đầu ca.'
              : summary.emergencyBlockReason || 'Ca làm việc hiện không đủ điều kiện kích hoạt khẩn cấp thủ công.',
            'info'
          );
          pendingPromptShiftIdRef.current = null;
          return;
        }
        activePromptIdRef.current = foundPair.shift.shiftId;
        setPromptTarget(foundPair);
        pendingPromptShiftIdRef.current = null;
      } else {
        pendingPromptShiftIdRef.current = shiftId;
      }
    };

    const handleReloadSignal = () => {
      onReloadEvents?.();
    };

    window.addEventListener('cani_open_emergency_prompt', handleOpenPrompt);
    window.addEventListener('cani_eo_reload_events', handleReloadSignal);
    return () => {
      window.removeEventListener('cani_open_emergency_prompt', handleOpenPrompt);
      window.removeEventListener('cani_eo_reload_events', handleReloadSignal);
    };
  }, [events, onReloadEvents]);

  useEffect(() => {
    if (!Array.isArray(events) || events.length === 0) return;
    if (promptTarget) return; // Đang hiển thị popup cho một ca, không ghi đè

    // Nếu có ca đang chờ kích hoạt từ Notification Bell
    if (pendingPromptShiftIdRef.current) {
      const pendingId = String(pendingPromptShiftIdRef.current);
      for (const evt of events) {
        if (!evt || !Array.isArray(evt.shifts)) continue;
        const shift = evt.shifts.find((s) => String(s.shiftId) === pendingId);
        if (shift) {
          pendingPromptShiftIdRef.current = null;
          const summary = getShiftStaffingSummary(shift, evt, new Date());
          if (!summary.canTriggerEmergency || !summary.nearStartUnderstaffed || summary.isEmergency || summary.isInviteMode) {
            showToast(
              summary.isEmergency
                ? 'Ca làm việc đang được điều phối khẩn cấp.'
                : !summary.nearStartUnderstaffed && summary.canTriggerEmergency
                ? 'Chỉ có thể kích hoạt điều phối khẩn cấp khi gần đến giờ bắt đầu ca (trong vòng 4 giờ trước giờ bắt đầu).'
                : summary.emergencyBlockReason || 'Ca làm việc hiện không đủ điều kiện kích hoạt khẩn cấp thủ công.',
              'info'
            );
            return;
          }
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

  const handleAccept = async (shiftId, optionsOrRadius) => {
    const isRedispatch = promptTarget?.mode === 'redispatch';
    const radiusKm = typeof optionsOrRadius === 'object' ? optionsOrRadius?.radiusKm : optionsOrRadius;
    setIsSubmitting(true);
    try {
      const result = isRedispatch
        ? await eoService.redispatchEmergencyDispatch(shiftId, optionsOrRadius)
        : await eoService.enableEmergencyDispatch(shiftId, optionsOrRadius);
      const dispatched = Number(result?.dispatchedCount || 0);
      const appliedRadius = Number(result?.effectiveRadiusKm || radiusKm || 25);

      if (dispatched > 0) {
        showToast(
          `Đã ${isRedispatch ? 'quét lại' : 'kích hoạt'} điều phối khẩn cấp (${appliedRadius} km): Gửi ${dispatched} đề xuất đến Worker phù hợp.`,
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
