import React, { useState, useEffect, useCallback, useRef } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import RateWorkerModal from './RateWorkerModal';

/**
 * ShiftQrAndAttendancePanel
 * Không gian làm việc hợp nhất: Mã QR Điểm Danh Động + Bảng Theo Dõi Điểm Danh Trực Tiếp
 * Hiển thị dạng trượt mở rộng toàn chiều rộng (Full-width Slide-down Panel / Workspace) thay vì popup nhỏ.
 */
export default function ShiftQrAndAttendancePanel({
  shift,
  event,
  allEvents = [],
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

  const [attendanceList, setAttendanceList] = useState([]);
  const [loadingAttendance, setLoadingAttendance] = useState(false);
  const [verifyingId, setVerifyingId] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);

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

  const handleVerify = async (assignmentId) => {
    setVerifyingId(assignmentId);
    try {
      await eoService.verifyAttendance(assignmentId);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho nhân sự!', 'success');
      loadAttendance();
      onReloadEvents?.();
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
  const currentPayload = isCheckin
    ? `CANI_CHECKIN:${shift.shiftId || ''}:${epochSeconds}:${refreshNonce.toString(36)}`
    : `CANI_CHECKOUT:${shift.shiftId || ''}:${epochSeconds}:${refreshNonce.toString(36)}`;
  const progressPercent = Math.round((countdown / ROTATE_INTERVAL) * 100);

  const handleManualRefreshQr = () => {
    setRefreshNonce(Date.now());
    setCountdown(ROTATE_INTERVAL);
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
            🔄 Làm mới điểm danh
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
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
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
              backgroundColor: '#fffbeb',
              border: '1px solid #fde68a',
              borderRadius: '0.5rem',
              padding: '0.65rem 0.85rem',
              marginTop: '1rem',
              textAlign: 'left',
              fontSize: '0.78rem',
              color: '#92400e',
              lineHeight: 1.45,
            }}
          >
            <strong>🚫 Chống gian lận điểm danh hộ:</strong> Mã QR tự làm mới mỗi 20 giây và yêu cầu nhân sự quét trực tiếp tại địa điểm sự kiện (Geofence GPS 200m).
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
                    const isCheckedIn = item.assignmentStatus === 'CHECKED_IN';
                    const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;

                    return (
                      <tr key={item.assignmentId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        <td style={{ padding: '12px 14px' }}>
                          <div style={{ fontWeight: 700, color: '#0f172a' }}>{item.workerName}</div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{item.workerPhone}</div>
                          <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: 2 }}>
                            ⭐ {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100}
                          </div>
                        </td>

                        <td style={{ padding: '12px 14px' }}>
                          {isCheckedIn && (
                            <span style={{ background: '#dcfce7', color: '#15803d', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              Đã Check-in {item.isOnTime ? '(Đúng giờ)' : '(Trễ ca)'}
                            </span>
                          )}
                          {isCompleted && (
                            <span style={{ background: '#e0e7ff', color: '#3730a3', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              Đã Hoàn Thành
                            </span>
                          )}
                          {!isCheckedIn && !isCompleted && (
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

                        <td style={{ padding: '12px 14px' }}>
                          <span style={{ background: '#f1f5f9', color: '#334155', padding: '2px 8px', borderRadius: 4, fontWeight: 600 }}>
                            {item.checklistProgress || 'Chưa cập nhật'}
                          </span>
                        </td>

                        <td style={{ padding: '12px 14px' }}>
                          {item.paymentStatus === 'CONFIRMED_FULL' && (
                            <span style={{ color: '#16a34a', fontWeight: 700 }}>Đã thanh toán</span>
                          )}
                          {item.paymentStatus === 'DECLARED_PENDING_WORKER' && (
                            <span style={{ color: '#d97706', fontWeight: 600 }}>Chờ xác nhận</span>
                          )}
                          {(!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && (
                            <button
                              type="button"
                              onClick={() => onOpenPaymentDeclaration?.(item)}
                              style={{
                                background: '#fef3c7',
                                color: '#92400e',
                                border: '1px solid #fcd34d',
                                borderRadius: 6,
                                padding: '4px 8px',
                                fontSize: '0.75rem',
                                fontWeight: 600,
                                cursor: 'pointer',
                              }}
                            >
                              Khai báo trả lương
                            </button>
                          )}
                        </td>

                        <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                            {!item.eoVerified && (isCheckedIn || isCompleted) && (
                              <button
                                type="button"
                                disabled={verifyingId === item.assignmentId}
                                onClick={() => handleVerify(item.assignmentId)}
                                style={{
                                  background: '#059669',
                                  color: '#ffffff',
                                  border: 'none',
                                  borderRadius: 6,
                                  padding: '5px 10px',
                                  fontSize: '0.78rem',
                                  fontWeight: 600,
                                  cursor: 'pointer',
                                }}
                              >
                                {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt Công'}
                              </button>
                            )}

                            {item.eoVerified && (
                              <span style={{ color: '#059669', fontSize: '0.78rem', fontWeight: 700, alignSelf: 'center' }}>
                                ✓ Đã duyệt
                              </span>
                            )}

                            {(isCompleted || item.eoVerified) && (
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
                                ⭐ Đánh Giá
                              </button>
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

      <RateWorkerModal
        isOpen={Boolean(selectedForReview)}
        onClose={() => setSelectedForReview(null)}
        assignment={selectedForReview}
        submitting={submittingReview}
        onSubmit={handleSubmitReview}
      />
    </div>
  );
}

