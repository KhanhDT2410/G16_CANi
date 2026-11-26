import React, { useState, useEffect, useCallback, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import RateWorkerModal from './RateWorkerModal';
import ConfirmVerifySkillModal, { resolvePreassignedPerformedSkillId } from './ConfirmVerifySkillModal';
import ReportNoShowModal from './ReportNoShowModal';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';
import { resolveAssetUrl, buildWorkerVietQrUrl } from '../utils/resolveAssetUrl';
import { formatVndWhole } from '../utils/eventHelpers';

/**
 * ShiftQrAndAttendancePanel
 * Không gian làm việc hợp nhất: Mã QR Điểm Danh Động + Bảng Theo Dõi Điểm Danh Trực Tiếp
 * Hiển thị dạng trượt mở rộng toàn chiều rộng (Full-width Slide-down Panel / Workspace) thay vì popup nhỏ.
 */
export default function ShiftQrAndAttendancePanel({
  shift,
  event,
  skillTaxonomy = [],
  onSelectShift,
  onClose,
  onReloadEvents,
  onOpenPaymentDeclaration,
}) {
  const panelRef = useRef(null);
  const [qrMode, setQrMode] = useState('CHECKIN'); // 'CHECKIN' | 'CHECKOUT'
  const ROTATE_INTERVAL = 20;
  const [countdown, setCountdown] = useState(ROTATE_INTERVAL);
  const [refreshNonce, setRefreshNonce] = useState(Date.now());
  const workerProfile = useWorkerProfile();

  const [attendanceList, setAttendanceList] = useState([]);
  const [loadingAttendance, setLoadingAttendance] = useState(false);
  const [verifyingId, setVerifyingId] = useState(null);
  const [selectedForVerify, setSelectedForVerify] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [selectedForNoShow, setSelectedForNoShow] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [serverQrData, setServerQrData] = useState(null);
  const [previewImage, setPreviewImage] = useState(null);

  // Phân tích danh sách đầu việc mẫu của ca
  const shiftChecklistTasks = React.useMemo(() => {
    if (!shift?.checklistTemplate) return [];
    try {
      const parsed = typeof shift.checklistTemplate === 'string'
        ? JSON.parse(shift.checklistTemplate)
        : shift.checklistTemplate;
      if (Array.isArray(parsed)) {
        return parsed.map((item, idx) => {
          if (typeof item === 'string') {
            return { id: `task_${idx + 1}`, label: item, isRequiredEvidence: false };
          }
          return {
            id: String(item.id || item.taskId || `task_${idx + 1}`),
            label: item.title || item.taskName || item.name || item.label || `Đầu việc ${idx + 1}`,
            isRequiredEvidence: Boolean(item.isRequiredEvidence || item.requireProof || item.requireEvidence),
            instruction: item.instruction || '',
          };
        });
      }
    } catch {
      // ignore
    }
    return [];
  }, [shift?.checklistTemplate]);

  const parseWorkerChecklistProgressMap = (rawProgress) => {
    if (!rawProgress) return {};
    try {
      const parsed = typeof rawProgress === 'string' ? JSON.parse(rawProgress) : rawProgress;
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        return parsed;
      }
      if (Array.isArray(parsed)) {
        const map = {};
        parsed.forEach((item, idx) => {
          if (typeof item === 'string') {
            map[item] = { isCompleted: true };
          } else if (item && typeof item === 'object') {
            const key = String(item.id ?? item.taskId ?? item.title ?? `chk_${idx + 1}`);
            map[key] = {
              isCompleted: Boolean(item.isCompleted ?? item.completed ?? item.done ?? item.checked ?? false),
              evidenceImageUrl: item.evidenceImageUrl || item.proofUrl || null,
              evidenceSubmittedAt: item.evidenceSubmittedAt || null,
              completedAt: item.completedAt || null,
            };
          }
        });
        return map;
      }
    } catch {
      // ignore
    }
    return {};
  };

  const canEoConfirmWorker = (workerItem) => {
    const isCheckedIn = workerItem.assignmentStatus === 'CHECKED_IN' || Boolean(workerItem.checkinTime);
    if (!isCheckedIn) return false;
    if (workerItem.eoConfirmed || workerItem.eoVerified) return false;

    if (!shiftChecklistTasks || shiftChecklistTasks.length === 0) {
      return true;
    }

    const progressMap = parseWorkerChecklistProgressMap(workerItem.checklistRawProgress);
    for (const task of shiftChecklistTasks) {
      const prog = progressMap[task.id] || progressMap[task.label];
      const isDone = Boolean(prog?.isCompleted || workerItem.isChecklistCompleted);
      if (!isDone) return false;
      if (task.isRequiredEvidence) {
        const hasProof = Boolean(prog?.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);
        if (!hasProof) return false;
      }
    }
    return true;
  };

  // Lấy mã QR động ký chữ ký HMAC-SHA256 chính thức từ backend
  const fetchLiveQr = useCallback(async () => {
    if (!shift?.shiftId) return;
    try {
      const data = await eoService.getShiftQr(shift.shiftId);
      if (data) {
        setServerQrData(data);
      }
    } catch (err) {
      console.warn('Không thể lấy mã QR trực tiếp từ server, sử dụng fallback client:', err);
    }
  }, [shift?.shiftId]);

  useEffect(() => {
    if (shift?.shiftId) {
      fetchLiveQr();
    }
  }, [shift?.shiftId, refreshNonce, fetchLiveQr]);

  // Tự động cuộn mượt xuống khu vực Điểm danh & Mã QR khi mở
  useEffect(() => {
    if (shift?.shiftId && panelRef.current) {
      setTimeout(() => {
        panelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 100);
    }
  }, [shift?.shiftId]);

  // Xoay vòng mã QR động mỗi 20 giây
  useEffect(() => {
    if (!shift?.shiftId) return;
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          setRefreshNonce(Date.now());
          return ROTATE_INTERVAL;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [shift?.shiftId]);

  const loadAttendance = useCallback(async () => {
    if (!shift?.shiftId) return;
    setLoadingAttendance(true);
    try {
      const data = await eoService.getShiftAttendance(shift.shiftId);
      setAttendanceList(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Lỗi khi tải danh sách điểm danh:', e);
      setAttendanceList([]);
    } finally {
      setLoadingAttendance(false);
    }
  }, [shift?.shiftId]);

  useEffect(() => {
    if (shift?.shiftId) {
      loadAttendance();
    }
  }, [shift?.shiftId, loadAttendance]);

  useEffect(() => {
    const handlePaymentUpdated = () => {
      loadAttendance();
      if (onReloadEvents) {
        Promise.resolve(onReloadEvents()).catch(() => { });
      }
    };
    window.addEventListener('cani_payment_updated', handlePaymentUpdated);
    return () => window.removeEventListener('cani_payment_updated', handlePaymentUpdated);
  }, [loadAttendance, onReloadEvents]);

  const handleVerify = async (assignmentId, verifyPayload = null) => {
    setVerifyingId(assignmentId);
    try {
      await eoService.verifyAttendance(assignmentId, verifyPayload);
      setSelectedForVerify(null);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho nhân sự!', 'success');
      await Promise.allSettled([
        loadAttendance(),
        onReloadEvents ? Promise.resolve(onReloadEvents()) : Promise.resolve(),
      ]);
    } catch (e) {
      showToast('Không thể duyệt công: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setVerifyingId(null);
    }
  };

  const handleSubmitReview = async (reviewData) => {
    setSubmittingReview(true);
    try {
      await eoService.rateWorker(reviewData.assignmentId, reviewData);
      showToast('Đã gửi đánh giá Worker thành công! Điểm uy tín đã được cập nhật.', 'success');
      setSelectedForReview(null);
      loadAttendance();
    } catch (e) {
      showToast('Không thể gửi đánh giá: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setSubmittingReview(false);
    }
  };

  if (!shift) return null;

  const isCheckin = qrMode === 'CHECKIN';
  const accentColor = isCheckin ? '#2563eb' : '#0d9488';
  const epochSeconds = Math.floor(refreshNonce / 1000);
  const fallbackPayload = isCheckin
    ? `CANI_CHECKIN:${shift.shiftId || ''}:${epochSeconds}:${refreshNonce.toString(36)}`
    : `CANI_CHECKOUT:${shift.shiftId || ''}:${epochSeconds}:${refreshNonce.toString(36)}`;
  const currentPayload = isCheckin
    ? (serverQrData?.checkinQrContent || fallbackPayload)
    : (serverQrData?.checkoutQrContent || fallbackPayload);
  const progressPercent = Math.round((countdown / ROTATE_INTERVAL) * 100);

  const handleManualRefreshQr = () => {
    setRefreshNonce(Date.now());
    setCountdown(ROTATE_INTERVAL);
    fetchLiveQr();
  };

  // Danh sách các ca của sự kiện hiện tại để chuyển nhanh
  const availableShifts = event?.shifts || [];

  return (
    <div
      ref={panelRef}
      className="eo-widget-card animate-fade-in-up"
      style={{
        marginTop: '1.5rem',
        padding: '1.5rem',
        border: '2px solid #2563eb',
        borderRadius: '1rem',
        background: '#ffffff',
        boxShadow: '0 12px 32px -4px rgba(37, 99, 235, 0.12)',
      }}
    >
      {/* HEADER BAR */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
          paddingBottom: '1rem',
          marginBottom: '1.5rem',
          borderBottom: '1px solid #e2e8f0',
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap' }}>
            <span
              style={{
                background: '#eff6ff',
                color: '#2563eb',
                padding: '4px 10px',
                borderRadius: 8,
                fontSize: '0.75rem',
                fontWeight: 700,
                textTransform: 'uppercase',
              }}
            >
              Trạm Điểm Danh & QR Trực Tiếp
            </span>
            <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
              {shift.jobType} — {event?.name || shift.eventName || 'Sự kiện'}
            </h3>
          </div>
          <p style={{ margin: '0.35rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
            Khung giờ:{' '}
            <strong>
              {shift.startTime
                ? new Date(shift.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                : '08:00'}{' '}
              -{' '}
              {shift.endTime
                ? new Date(shift.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                : '12:00'}
            </strong>{' '}
            • Nhân sự đã chốt: <strong>{shift.confirmedWorkers || 0}/{shift.requiredWorkers || 0}</strong>
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap' }}>
          {availableShifts.length > 1 && onSelectShift && (
            <select
              className="table-filter-select"
              value={shift.shiftId}
              onChange={(e) => {
                const found = availableShifts.find((s) => s.shiftId === e.target.value);
                if (found) onSelectShift(found, event);
              }}
              style={{ fontWeight: 600 }}
            >
              {availableShifts
                .filter((s) => s.status !== 'CANCELLED')
                .map((s) => (
                  <option key={s.shiftId} value={s.shiftId}>
                    Chuyển ca: {s.jobType} (
                    {s.startTime
                      ? new Date(s.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      : '08:00'}
                    )
                  </option>
                ))}
            </select>
          )}

          <button
            type="button"
            onClick={loadAttendance}
            className="eo-btn-secondary"
            style={{ padding: '0.5rem 0.9rem', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: 6 }}
          >
            Làm mới điểm danh
          </button>

          {onClose && (
            <button
              type="button"
              onClick={onClose}
              style={{
                background: '#f1f5f9',
                color: '#475569',
                border: '1px solid #cbd5e1',
                borderRadius: 8,
                padding: '0.5rem 1rem',
                fontSize: '0.85rem',
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              ✕ Thu gọn bảng
            </button>
          )}
        </div>
      </div>

      {/* BODY: 2 CỘT RỘNG RÃI (TRÁI: MÃ QR ĐỘNG | PHẢI: DANH SÁCH ĐIỂM DANH) */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 320px), 1fr))',
          gap: '1.5rem',
          alignItems: 'start',
        }}
      >
        {/* CỘT 1: MÃ QR ĐIỂM DANH ĐỘNG */}
        <div
          style={{
            background: '#f8fafc',
            border: '1px solid #e2e8f0',
            borderRadius: '0.85rem',
            padding: '1.25rem',
            textAlign: 'center',
            maxWidth: 420,
            width: '100%',
            justifySelf: 'center',
          }}
        >
          {/* Chuyển đổi Check-in / Check-out */}
          <div style={{ display: 'flex', background: '#e2e8f0', borderRadius: 8, padding: 4, marginBottom: '1rem' }}>
            <button
              type="button"
              onClick={() => setQrMode('CHECKIN')}
              style={{
                flex: 1,
                padding: '8px 12px',
                border: 'none',
                borderRadius: 6,
                background: isCheckin ? '#2563eb' : 'transparent',
                color: isCheckin ? '#ffffff' : '#475569',
                fontWeight: 700,
                fontSize: '0.85rem',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
            >
              Check-in (Vào ca)
            </button>
            <button
              type="button"
              onClick={() => setQrMode('CHECKOUT')}
              style={{
                flex: 1,
                padding: '8px 12px',
                border: 'none',
                borderRadius: 6,
                background: !isCheckin ? '#0d9488' : 'transparent',
                color: !isCheckin ? '#ffffff' : '#475569',
                fontWeight: 700,
                fontSize: '0.85rem',
                cursor: 'pointer',
                transition: 'all 0.2s',
              }}
            >
              Check-out (Tan ca)
            </button>
          </div>

          {/* Thanh đếm ngược 20s */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#eff6ff',
              border: '1px solid #bfdbfe',
              padding: '0.45rem 0.85rem',
              borderRadius: '0.5rem',
              marginBottom: '0.65rem',
              fontSize: '0.8rem',
            }}
          >
            <span style={{ color: '#1e40af', fontWeight: 600 }}>
              Mã tự động xoay sau: <strong>{countdown}s</strong>
            </span>
            <button
              type="button"
              onClick={handleManualRefreshQr}
              style={{
                background: 'none',
                border: 'none',
                color: '#2563eb',
                fontWeight: 700,
                fontSize: '0.78rem',
                cursor: 'pointer',
              }}
            >
              Đổi mã ngay
            </button>
          </div>

          <div
            style={{
              width: '100%',
              height: '4px',
              backgroundColor: '#e2e8f0',
              borderRadius: '2px',
              marginBottom: '1rem',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                height: '100%',
                width: `${progressPercent}%`,
                backgroundColor: countdown <= 5 ? '#ef4444' : accentColor,
                transition: 'width 1s linear, background-color 0.3s',
              }}
            />
          </div>

          {/* Khung QR */}
          <div
            style={{
              background: '#ffffff',
              padding: 16,
              borderRadius: 16,
              display: 'inline-block',
              border: `2px solid ${accentColor}`,
              boxShadow: '0 8px 20px rgba(0,0,0,0.06)',
            }}
          >
            <QRCodeSVG value={currentPayload} size={210} level="H" includeMargin fgColor="#0f172a" />
            <div
              style={{
                marginTop: 6,
                fontSize: '0.7rem',
                color: '#64748b',
                fontFamily: 'monospace',
                background: '#f8fafc',
                padding: '4px 8px',
                borderRadius: 4,
                wordBreak: 'break-all',
              }}
            >
              {currentPayload}
            </div>
          </div>

          <div
            style={{
              backgroundColor: '#ecfdf5',
              border: '1px solid #a7f3d0',
              borderRadius: '0.5rem',
              padding: '0.65rem 0.85rem',
              marginTop: '1rem',
              textAlign: 'left',
              fontSize: '0.78rem',
              color: '#065f46',
              lineHeight: 1.45,
            }}
          >
            <strong>Bảo mật chống giả mạo:</strong> Mã QR xoay vòng 20 giây và được ký số bảo mật <strong>HMAC-SHA256</strong> từ máy chủ. Ngăn chặn tuyệt đối việc Worker tự sinh mã QR hoặc gian lận điểm danh từ xa.
          </div>
        </div>

        {/* CỘT 2: BẢNG ĐIỂM DANH & DUYỆT CÔNG TRỰC TIẾP */}
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem' }}>
            <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#0f172a' }}>
              Danh Sách Nhân Sự Điểm Danh Trong Ca ({attendanceList.length})
            </h4>
          </div>

          {loadingAttendance ? (
            <div style={{ padding: '3rem 1rem', textAlign: 'center', color: '#64748b', background: '#f8fafc', borderRadius: 10 }}>
              Đang tải dữ liệu điểm danh thời gian thực...
            </div>
          ) : attendanceList.length === 0 ? (
            <div
              style={{
                padding: '3rem 1.5rem',
                textAlign: 'center',
                background: '#f8fafc',
                borderRadius: 12,
                border: '1px dashed #cbd5e1',
              }}
            >
              <p style={{ margin: 0, color: '#475569', fontSize: '0.95rem', fontWeight: 600 }}>
                Chưa có nhân sự nào được ghi nhận trong ca làm việc này.
              </p>
              <p style={{ margin: '0.35rem 0 0', color: '#64748b', fontSize: '0.825rem' }}>
                Khi nhân sự nhận ca và quét mã QR bên trái bằng ứng dụng CANi Mobile, thông tin giờ vào/ra sẽ hiển thị tức thì tại đây.
              </p>
            </div>
          ) : (
            <div style={{ overflowX: 'auto', border: '1px solid #e2e8f0', borderRadius: 10 }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569', fontWeight: 700 }}>
                    <th style={{ padding: '10px 14px' }}>Nhân Sự</th>
                    <th style={{ padding: '10px 14px' }}>Trạng Thái</th>
                    <th style={{ padding: '10px 14px' }}>Giờ Vào / Ra</th>
                    <th style={{ padding: '10px 14px' }}>Checklist</th>
                    <th style={{ padding: '10px 14px' }}>Thanh Toán</th>
                    <th style={{ padding: '10px 14px', textAlign: 'right' }}>Thao Tác</th>
                  </tr>
                </thead>
                <tbody>
                  {attendanceList.map((item) => {
                    const isNoShow = item.assignmentStatus === 'NO_SHOW';
                    const isCheckedIn = item.assignmentStatus === 'CHECKED_IN' || Boolean(item.checkinTime);
                    const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;
                    const workerProgressMap = parseWorkerChecklistProgressMap(item.checklistRawProgress);
                    const canVerify = canEoConfirmWorker(item);
                    const isEoConfirmed = Boolean(item.eoConfirmed || item.eoVerified);

                    return (
                      <tr key={item.assignmentId} style={{ borderBottom: '1px solid #f1f5f9', verticalAlign: 'top' }}>
                        <td style={{ padding: '12px 14px' }}>
                          <div
                            onClick={() => workerProfile.openProfile(item.workerId || item.userId)}
                            style={{
                              fontWeight: 700,
                              color: '#0066FF',
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 4,
                            }}
                            title="Bấm để xem hồ sơ năng lực chi tiết của nhân sự"
                          >
                            <span>{item.workerName}</span>
                          </div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{item.workerPhone}</div>
                          <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: 2 }}>
                            Đánh giá: {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100}
                          </div>
                        </td>

                        <td style={{ padding: '12px 14px' }}>
                          {isNoShow && (
                            <span style={{ background: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca', padding: '2px 8px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                              Vắng mặt
                            </span>
                          )}
                          {!isNoShow && isCheckedIn && (
                            <span style={{ background: '#dcfce7', color: '#15803d', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              Đã Check-in {item.isOnTime ? '(Đúng giờ)' : '(Trễ ca)'}
                            </span>
                          )}
                          {!isNoShow && isCompleted && (
                            <span style={{ background: '#ecfdf5', color: '#065f46', border: '1px solid #a7f3d0', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              Đã Hoàn Thành
                            </span>
                          )}
                          {!isNoShow && !isCheckedIn && !isCompleted && (
                            <span style={{ background: '#fef3c7', color: '#b45309', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              Chờ Đến Ca
                            </span>
                          )}
                        </td>

                        <td style={{ padding: '12px 14px', color: '#475569', fontSize: '0.8rem' }}>
                          <div>
                            Vào:{' '}
                            {item.checkinTime
                              ? new Date(item.checkinTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                              : '—'}
                          </div>
                          <div>
                            Ra:{' '}
                            {item.checkoutTime
                              ? new Date(item.checkoutTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                              : '—'}
                          </div>
                          {item.hoursWorked && (
                            <div style={{ color: '#0d9488', fontWeight: 600 }}>{item.hoursWorked} giờ làm</div>
                          )}
                        </td>

                        {/* Checklist & Minh chứng */}
                        <td style={{ padding: '12px 14px' }}>
                          <span style={{ background: item.isChecklistCompleted ? '#dcfce7' : '#f1f5f9', color: item.isChecklistCompleted ? '#15803d' : '#334155', padding: '2px 8px', borderRadius: 4, fontWeight: 700, fontSize: '0.75rem' }}>
                            {item.isChecklistCompleted ? 'Đã xong 100%' : (item.checklistProgress || 'Chưa cập nhật')}
                          </span>

                          {shiftChecklistTasks.length > 0 && (
                            <div style={{ marginTop: 6, display: 'flex', flexDirection: 'column', gap: 4 }}>
                              {shiftChecklistTasks.map((t) => {
                                const prog = workerProgressMap[t.id] || workerProgressMap[t.label] || {};
                                const isTaskDone = Boolean(prog.isCompleted || item.isChecklistCompleted);
                                const hasEvidence = Boolean(prog.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);

                                return (
                                  <div key={t.id} style={{ fontSize: '0.73rem', padding: '2px 4px', background: isTaskDone ? '#f0fdf4' : '#f8fafc', borderRadius: 4, border: `1px solid ${isTaskDone ? '#bbf7d0' : '#e2e8f0'}` }}>
                                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 4 }}>
                                      <span style={{ fontWeight: 600, color: isTaskDone ? '#15803d' : '#64748b' }}>
                                        {isTaskDone ? '✓ ' : '○ '} {t.label}
                                      </span>
                                      <span style={{ fontSize: '0.68rem', color: isTaskDone ? '#15803d' : '#94a3b8' }}>
                                        {isTaskDone ? 'Xong' : 'Chưa'}
                                      </span>
                                    </div>
                                    {t.isRequiredEvidence ? (
                                      hasEvidence ? (
                                        <button
                                          type="button"
                                          onClick={() => setPreviewImage({ url: prog.evidenceImageUrl, title: `Minh chứng việc "${t.label}" - ${item.workerName}` })}
                                          style={{ background: '#eff6ff', color: '#1d4ed8', border: '1px solid #bfdbfe', borderRadius: 4, padding: '1px 5px', fontSize: '0.68rem', fontWeight: 600, cursor: 'pointer', marginTop: 2 }}
                                        >
                                          Xem ảnh
                                        </button>
                                      ) : (
                                        <span style={{ color: '#dc2626', fontSize: '0.68rem', fontWeight: 700, display: 'block', marginTop: 1 }}>
                                          Chưa nộp minh chứng
                                        </span>
                                      )
                                    ) : (
                                      <span style={{ color: '#94a3b8', fontSize: '0.68rem', fontStyle: 'italic' }}>
                                        Không yêu cầu ảnh
                                      </span>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          )}
                        </td>

                        {/* Thanh toán & Bằng chứng */}
                        <td style={{ padding: '12px 14px' }}>
                          {(() => {
                            const workerQrUrl = buildWorkerVietQrUrl(item);
                            const isTransfer = item.paymentMethod === 'BANK_TRANSFER';
                            const isCash = item.paymentMethod === 'CASH';

                            return (
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start' }}>
                                {item.declaredAmount != null && (
                                  <div style={{ fontSize: '0.75rem', color: '#0369a1' }}>
                                    Đã khai: <strong>{formatVndWhole(item.declaredAmount)}</strong>
                                    {(isTransfer || isCash) && (
                                      <span style={{ marginLeft: 4, fontSize: '0.68rem', color: '#475569' }}>
                                        ({isTransfer ? 'CK' : 'Tiền mặt'})
                                      </span>
                                    )}
                                  </div>
                                )}

                                {item.externalRef && (
                                  <div style={{ fontSize: '0.7rem', color: '#475569', fontFamily: 'monospace' }}>
                                    Mã GD: {item.externalRef}
                                  </div>
                                )}

                                {item.paymentProofUrl && (
                                  <button
                                    type="button"
                                    onClick={() => setPreviewImage({
                                      url: resolveAssetUrl(item.paymentProofUrl),
                                      title: `Chứng từ thanh toán cho ${item.workerName}${item.externalRef ? ' (' + item.externalRef + ')' : ''}`,
                                    })}
                                    style={{
                                      display: 'inline-flex',
                                      alignItems: 'center',
                                      gap: 4,
                                      background: '#eff6ff',
                                      color: '#1d4ed8',
                                      border: '1px solid #bfdbfe',
                                      borderRadius: 5,
                                      padding: '2px 6px',
                                      fontSize: '0.7rem',
                                      fontWeight: 700,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    <img
                                      src={resolveAssetUrl(item.paymentProofUrl)}
                                      alt="Biên lai"
                                      style={{ width: 18, height: 18, objectFit: 'cover', borderRadius: 3 }}
                                    />
                                    <span>Xem chứng từ</span>
                                  </button>
                                )}

                                {item.workerConfirmStatus === 'RECEIVED_FULL' && (
                                  <span style={{ background: '#dcfce7', color: '#15803d', padding: '2px 6px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                                    Worker nhận đủ
                                  </span>
                                )}
                                {item.workerConfirmStatus === 'RECEIVED_PARTIAL' && (
                                  <span style={{ background: '#fef3c7', color: '#b45309', padding: '2px 6px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                                    Nhận thiếu: {item.workerComment || ''}
                                  </span>
                                )}
                                {item.workerConfirmStatus === 'NOT_RECEIVED' && (
                                  <span style={{ background: '#fee2e2', color: '#b91c1c', padding: '2px 6px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                                    Chưa nhận: {item.workerComment || ''}
                                  </span>
                                )}

                                {item.paymentStatus === 'CONFIRMED_FULL' && !item.workerConfirmStatus && (
                                  <span style={{ color: '#16a34a', fontWeight: 700, fontSize: '0.75rem' }}>Đã thanh toán</span>
                                )}
                                {item.paymentStatus === 'DECLARED_PENDING_WORKER' && !item.workerConfirmStatus && (
                                  <span style={{ color: '#d97706', fontWeight: 600, fontSize: '0.75rem' }}>Chờ xác nhận</span>
                                )}

                                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                                  {workerQrUrl && (
                                    <button
                                      type="button"
                                      onClick={() => setPreviewImage({
                                        url: workerQrUrl,
                                        title: `Mã VietQR nhận lương của ${item.workerName}`,
                                      })}
                                      style={{
                                        background: '#f8fafc',
                                        color: '#334155',
                                        border: '1px solid #cbd5e1',
                                        borderRadius: 5,
                                        padding: '2px 6px',
                                        fontSize: '0.68rem',
                                        fontWeight: 700,
                                        cursor: 'pointer',
                                      }}
                                    >
                                      QR
                                    </button>
                                  )}

                                  {(!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') ? (
                                    isEoConfirmed ? (
                                      <button
                                        type="button"
                                        onClick={() => onOpenPaymentDeclaration?.(item)}
                                        style={{
                                          background: '#2563eb',
                                          color: '#ffffff',
                                          border: 'none',
                                          borderRadius: 6,
                                          padding: '5px 10px',
                                          fontSize: '0.75rem',
                                          fontWeight: 700,
                                          cursor: 'pointer',
                                        }}
                                      >
                                        Trả lương & QR
                                      </button>
                                    ) : (
                                      <span
                                        title="Chỉ mở khai báo trả lương sau khi đã duyệt công"
                                        style={{
                                          background: '#fef3c7',
                                          color: '#92400e',
                                          border: '1px solid #fde68a',
                                          borderRadius: 6,
                                          padding: '3px 8px',
                                          fontSize: '0.72rem',
                                          fontWeight: 600,
                                          display: 'inline-block',
                                        }}
                                      >
                                        Chờ duyệt công
                                      </span>
                                    )
                                  ) : (
                                    isEoConfirmed && item.paymentStatus !== 'CONFIRMED_FULL' && (
                                      <button
                                        type="button"
                                        onClick={() => onOpenPaymentDeclaration?.(item)}
                                        style={{
                                          background: '#f8fafc',
                                          color: '#0f172a',
                                          border: '1px solid #cbd5e1',
                                          borderRadius: 5,
                                          padding: '2px 6px',
                                          fontSize: '0.68rem',
                                          fontWeight: 700,
                                          cursor: 'pointer',
                                        }}
                                      >
                                        Cập nhật chứng từ
                                      </button>
                                    )
                                  )}
                                </div>
                              </div>
                            );
                          })()}
                        </td>

                        <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center', flexWrap: 'wrap' }}>
                            <button
                              type="button"
                              onClick={() => {
                                const targetUserId = item.userId || item.workerId;
                                window.dispatchEvent(
                                  new CustomEvent('cani_open_direct_chat', {
                                    detail: { targetUserId, shiftId: shift.shiftId },
                                  })
                                );
                              }}
                              style={{
                                background: '#eff6ff',
                                color: '#2563eb',
                                border: '1px solid #bfdbfe',
                                borderRadius: 6,
                                padding: '5px 8px',
                                fontSize: '0.78rem',
                                fontWeight: 600,
                                cursor: 'pointer',
                              }}
                              title="Nhắn tin với nhân sự này"
                            >
                              Nhắn tin
                            </button>

                            {!isEoConfirmed && (isCheckedIn || isCompleted) && (
                              <button
                                type="button"
                                disabled={!canVerify || verifyingId === item.assignmentId}
                                onClick={() => {
                                  const preassignedSkillId = resolvePreassignedPerformedSkillId(item, shift);
                                  if (preassignedSkillId) {
                                    handleVerify(item.assignmentId, {
                                      confirmedPerformedSkillId: preassignedSkillId,
                                      confirmPerformedSkill: true,
                                    });
                                  } else {
                                    setSelectedForVerify(item);
                                  }
                                }}
                                title={
                                  !isCheckedIn
                                    ? 'Nhân sự chưa check-in vào ca'
                                    : !canVerify
                                      ? 'Chưa hoàn tất các đầu việc bắt buộc hoặc thiếu ảnh minh chứng'
                                      : 'Xác nhận hoàn thành công việc và duyệt công'
                                }
                                style={{
                                  background: !canVerify ? '#94a3b8' : '#059669',
                                  color: '#ffffff',
                                  border: 'none',
                                  borderRadius: 6,
                                  padding: '5px 10px',
                                  fontSize: '0.78rem',
                                  fontWeight: 600,
                                  cursor: !canVerify || verifyingId === item.assignmentId ? 'not-allowed' : 'pointer',
                                }}
                              >
                                {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt Công'}
                              </button>
                            )}

                            {!isCheckedIn && !isCompleted && !isNoShow && item.assignmentStatus !== 'CANCELLED' && (
                              <button
                                type="button"
                                onClick={() => setSelectedForNoShow(item)}
                                style={{
                                  background: '#fff1f2',
                                  color: '#e11d48',
                                  border: '1px solid #fecdd3',
                                  borderRadius: 6,
                                  padding: '5px 9px',
                                  fontSize: '0.75rem',
                                  fontWeight: 700,
                                  cursor: 'pointer',
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: 3,
                                  transition: 'all 0.15s ease',
                                }}
                                title="Báo cáo nhân sự không đến & Kích hoạt tìm người thay thế khẩn cấp"
                              >
                                Báo Vắng Mặt
                              </button>
                            )}

                            {isNoShow && (
                              <span style={{ color: '#e11d48', fontSize: '0.75rem', fontWeight: 700, alignSelf: 'center' }}>
                                Đã phạt No-Show
                              </span>
                            )}

                            {isCompleted && !item.hasReviewedWorker && (
                              <button
                                type="button"
                                onClick={() => setSelectedForReview(item)}
                                style={{
                                  background: '#eff6ff',
                                  color: '#1d4ed8',
                                  border: '1px solid #bfdbfe',
                                  borderRadius: 6,
                                  padding: '5px 10px',
                                  fontSize: '0.78rem',
                                  fontWeight: 600,
                                  cursor: 'pointer',
                                }}
                              >
                                Đánh Giá
                              </button>
                            )}

                            {isCompleted && item.hasReviewedWorker && (
                              <span style={{ color: '#059669', fontSize: '0.75rem', fontWeight: 600, padding: '4px' }}>
                                Đã đánh giá
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <ConfirmVerifySkillModal
        isOpen={Boolean(selectedForVerify)}
        onClose={() => setSelectedForVerify(null)}
        assignment={selectedForVerify}
        shift={shift}
        skillTaxonomy={skillTaxonomy}
        submitting={Boolean(selectedForVerify && verifyingId === selectedForVerify.assignmentId)}
        onConfirm={({ assignmentId, confirmedPerformedSkillId, confirmPerformedSkill }) =>
          handleVerify(assignmentId, { confirmedPerformedSkillId, confirmPerformedSkill })
        }
      />

      <RateWorkerModal
        isOpen={Boolean(selectedForReview)}
        onClose={() => setSelectedForReview(null)}
        assignment={selectedForReview}
        submitting={submittingReview}
        onSubmitReview={handleSubmitReview}
      />

      <ReportNoShowModal
        isOpen={Boolean(selectedForNoShow)}
        onClose={() => setSelectedForNoShow(null)}
        assignment={selectedForNoShow}
        shift={shift}
        onSuccess={async () => {
          await Promise.allSettled([
            loadAttendance(),
            onReloadEvents ? Promise.resolve(onReloadEvents()) : Promise.resolve(),
          ]);
        }}
      />

      <WorkerProfileModal
        isOpen={workerProfile.isOpen}
        loading={workerProfile.loading}
        error={workerProfile.error}
        profile={workerProfile.profile}
        onClose={workerProfile.closeProfile}
      />

      {/* HỘP THOẠI XEM ẢNH MINH CHỨNG PHÓNG TO */}
      {previewImage && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(15, 23, 42, 0.85)',
            zIndex: 10000,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
          }}
          onClick={() => setPreviewImage(null)}
        >
          <div style={{ color: '#ffffff', fontWeight: 700, marginBottom: '1rem', fontSize: '1.05rem' }}>
            {previewImage.title}
          </div>
          <img
            src={resolveAssetUrl(previewImage.url)}
            alt={previewImage.title}
            style={{ maxWidth: '90vw', maxHeight: '80vh', borderRadius: '12px', boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)', backgroundColor: '#ffffff' }}
            onClick={(e) => e.stopPropagation()}
          />
          <button
            type="button"
            onClick={() => setPreviewImage(null)}
            style={{ marginTop: '1rem', background: '#ffffff', color: '#0f172a', border: 'none', borderRadius: '8px', padding: '0.5rem 1.25rem', fontWeight: 700, cursor: 'pointer' }}
          >
            Đóng ảnh
          </button>
        </div>
      )}
    </div>
  );
}

