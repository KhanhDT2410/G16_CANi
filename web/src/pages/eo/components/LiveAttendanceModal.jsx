import React, { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import RateWorkerModal from './RateWorkerModal';

/**
 * LiveAttendanceModal
 * UC-48: Bảng theo dõi điểm danh trực tiếp của Ca làm việc (Live Attendance Dashboard)
 * UC-49: EO xác nhận hoàn thành ca & duyệt công cho Worker
 * UC-69: EO đánh giá Worker
 */
const LiveAttendanceModal = ({
  isOpen,
  onClose,
  shift,
  onOpenQr,
  onOpenPaymentDeclaration,
}) => {
  const [attendanceList, setAttendanceList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [verifyingId, setVerifyingId] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);

  useEffect(() => {
    if (isOpen && shift) {
      loadAttendance();
    }
  }, [isOpen, shift]);

  const loadAttendance = async () => {
    if (!shift?.shiftId) return;
    setLoading(true);
    try {
      const data = await eoService.getShiftAttendance(shift.shiftId);
      if (data && data.length > 0) {
        setAttendanceList(data);
      } else {
        // Fallback mockup nếu chưa có worker nào match
        setAttendanceList([
          {
            assignmentId: 'assign-demo-1',
            workerName: 'Nguyễn Văn An',
            workerPhone: '0987654321',
            workerReliabilityScore: 98,
            workerRatingAvg: 4.9,
            assignmentStatus: 'CHECKED_IN',
            checkinTime: new Date(Date.now() - 3600000).toISOString(),
            isOnTime: true,
            checklistProgress: '3/4 việc',
            eoConfirmed: false,
            hasReviewedWorker: false,
            agreedWage: (shift.hourlyWage || 50000) * 4,
          },
          {
            assignmentId: 'assign-demo-2',
            workerName: 'Trần Thị Hoa',
            workerPhone: '0912345678',
            workerReliabilityScore: 95,
            workerRatingAvg: 4.8,
            assignmentStatus: 'COMPLETED',
            checkinTime: new Date(Date.now() - 14400000).toISOString(),
            checkoutTime: new Date().toISOString(),
            isOnTime: true,
            hoursWorked: 4.0,
            checklistProgress: '4/4 việc',
            eoConfirmed: true,
            hasReviewedWorker: false,
            paymentStatus: 'CONFIRMED_FULL',
            agreedWage: (shift.hourlyWage || 50000) * 4,
          },
        ]);
      }
    } catch (e) {
      console.warn('Lỗi khi tải attendance list:', e);
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async (assignmentId) => {
    setVerifyingId(assignmentId);
    try {
      await eoService.verifyAttendance(assignmentId);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho Worker!', 'success');
      loadAttendance();
    } catch (e) {
      console.error(e);
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
      console.error(e);
      showToast('Không thể gửi đánh giá: ' + (e.response?.data?.message || e.message), 'error');
    } finally {
      setSubmittingReview(false);
    }
  };

  if (!isOpen || !shift) return null;

  return (
    <>
      <div className="eo-modal-overlay">
        <div className="eo-modal-box" style={{ maxWidth: 840, width: '95%' }}>
          {/* Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.2rem' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <h3 style={{ margin: 0 }}>📋 Theo Dõi Điểm Danh & Vận Hành Ca</h3>
                <span style={{
                  padding: '3px 10px',
                  borderRadius: 12,
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  background: '#dbeafe',
                  color: '#1d4ed8',
                }}>
                  {shift.jobType}
                </span>
              </div>
              <div style={{ fontSize: '0.85rem', color: '#64748b', marginTop: 4 }}>
                Sự kiện: <strong>{shift.eventName || 'Sự kiện'}</strong> • Thời gian: {shift.startTime ? new Date(shift.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '08:00'} - {shift.endTime ? new Date(shift.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '12:00'}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 8 }}>
              <button
                type="button"
                onClick={() => onOpenQr(shift)}
                style={{
                  background: '#2563eb',
                  color: '#fff',
                  border: 'none',
                  borderRadius: 6,
                  padding: '6px 12px',
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                  fontWeight: 500,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                📱 Hiển Thị Mã QR Ca
              </button>
              <button
                type="button"
                onClick={loadAttendance}
                style={{
                  background: '#f1f5f9',
                  color: '#475569',
                  border: '1px solid #cbd5e1',
                  borderRadius: 6,
                  padding: '6px 10px',
                  fontSize: '0.85rem',
                  cursor: 'pointer',
                }}
                title="Làm mới dữ liệu"
              >
                🔄
              </button>
              <button
                type="button"
                onClick={onClose}
                style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}
              >
                ✕
              </button>
            </div>
          </div>

          {/* Table / List */}
          {loading ? (
            <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }}>
              🔄 Đang tải danh sách điểm danh nhân sự...
            </div>
          ) : attendanceList.length === 0 ? (
            <div style={{ padding: '3rem 0', textAlign: 'center', background: '#f8fafc', borderRadius: 8, border: '1px dashed #cbd5e1' }}>
              <p style={{ margin: 0, color: '#64748b', fontSize: '0.95rem' }}>
                Chưa có Worker nào nhận ca này hoặc ca chưa được kích hoạt Matching.
              </p>
            </div>
          ) : (
            <div style={{ overflowX: 'auto', border: '1px solid #e2e8f0', borderRadius: 8 }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0', color: '#475569', fontWeight: 600 }}>
                    <th style={{ padding: '10px 14px' }}>Nhân Sự</th>
                    <th style={{ padding: '10px 14px' }}>Trạng Thái Điểm Danh</th>
                    <th style={{ padding: '10px 14px' }}>Check-in / Check-out</th>
                    <th style={{ padding: '10px 14px' }}>Tiến Độ Việc</th>
                    <th style={{ padding: '10px 14px' }}>Thanh Toán</th>
                    <th style={{ padding: '10px 14px', textAlign: 'right' }}>Thao Tác Duyệt & Đánh Giá</th>
                  </tr>
                </thead>
                <tbody>
                  {attendanceList.map((item) => {
                    const isCheckedIn = item.assignmentStatus === 'CHECKED_IN';
                    const isCompleted = item.assignmentStatus === 'COMPLETED' || item.checkoutTime != null;

                    return (
                      <tr key={item.assignmentId} style={{ borderBottom: '1px solid #f1f5f9' }}>
                        {/* Nhân sự */}
                        <td style={{ padding: '12px 14px' }}>
                          <div style={{ fontWeight: 600, color: '#1e293b' }}>{item.workerName}</div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{item.workerPhone}</div>
                          <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: 2 }}>
                            ⭐ {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100}
                          </div>
                        </td>

                        {/* Trạng thái điểm danh */}
                        <td style={{ padding: '12px 14px' }}>
                          {isCheckedIn && (
                            <span style={{ background: '#dcfce7', color: '#15803d', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              🟢 Đã Check-in {item.isOnTime ? '(Đúng giờ)' : '(Trễ ca)'}
                            </span>
                          )}
                          {isCompleted && (
                            <span style={{ background: '#e0e7ff', color: '#3730a3', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              🏁 Đã Hoàn Thành
                            </span>
                          )}
                          {!isCheckedIn && !isCompleted && (
                            <span style={{ background: '#fef3c7', color: '#b45309', padding: '3px 8px', borderRadius: 12, fontWeight: 600, fontSize: '0.75rem' }}>
                              ⏳ Chờ Đến Ca
                            </span>
                          )}
                        </td>

                        {/* Check-in / out time */}
                        <td style={{ padding: '12px 14px', color: '#475569', fontSize: '0.8rem' }}>
                          <div>Vào: {item.checkinTime ? new Date(item.checkinTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}</div>
                          <div>Ra: {item.checkoutTime ? new Date(item.checkoutTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—'}</div>
                          {item.hoursWorked && <div style={{ color: '#0d9488', fontWeight: 500 }}>{item.hoursWorked} giờ làm</div>}
                        </td>

                        {/* Tiến độ checklist */}
                        <td style={{ padding: '12px 14px' }}>
                          <span style={{ background: '#f1f5f9', color: '#334155', padding: '2px 8px', borderRadius: 4, fontWeight: 500 }}>
                            {item.checklistProgress || 'Chưa cập nhật'}
                          </span>
                        </td>

                        {/* Trạng thái thanh toán */}
                        <td style={{ padding: '12px 14px' }}>
                          {item.paymentStatus === 'CONFIRMED_FULL' && (
                            <span style={{ color: '#16a34a', fontWeight: 600 }}>✓ Đã thanh toán</span>
                          )}
                          {item.paymentStatus === 'DECLARED_PENDING_WORKER' && (
                            <span style={{ color: '#d97706', fontWeight: 500 }}>Chờ worker xác nhận</span>
                          )}
                          {(!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && (
                            <button
                              type="button"
                              onClick={() => {
                                onClose();
                                onOpenPaymentDeclaration(item);
                              }}
                              style={{
                                background: '#fef3c7',
                                color: '#92400e',
                                border: '1px solid #fcd34d',
                                borderRadius: 4,
                                padding: '3px 8px',
                                fontSize: '0.75rem',
                                cursor: 'pointer',
                                fontWeight: 600,
                              }}
                            >
                              💵 Khai báo lương
                            </button>
                          )}
                        </td>

                        {/* Thao tác */}
                        <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {/* Nút duyệt hoàn thành công */}
                            {!item.eoConfirmed ? (
                              <button
                                type="button"
                                onClick={() => handleVerify(item.assignmentId)}
                                disabled={verifyingId === item.assignmentId}
                                style={{
                                  background: '#16a34a',
                                  color: '#fff',
                                  border: 'none',
                                  borderRadius: 4,
                                  padding: '4px 10px',
                                  fontSize: '0.75rem',
                                  cursor: 'pointer',
                                  fontWeight: 500,
                                }}
                              >
                                {verifyingId === item.assignmentId ? 'Đang duyệt...' : '✓ Duyệt Công'}
                              </button>
                            ) : (
                              <span style={{ color: '#16a34a', fontSize: '0.75rem', fontWeight: 600, padding: '4px 0' }}>
                                ✓ Đã duyệt công
                              </span>
                            )}

                            {/* Nút đánh giá Worker */}
                            {item.hasReviewedWorker ? (
                              <span style={{ color: '#eab308', fontSize: '0.75rem', fontWeight: 600, padding: '4px 0' }}>
                                ⭐ Đã đánh giá
                              </span>
                            ) : (
                              <button
                                type="button"
                                onClick={() => setSelectedForReview(item)}
                                style={{
                                  background: '#f8fafc',
                                  color: '#d97706',
                                  border: '1px solid #fde68a',
                                  borderRadius: 4,
                                  padding: '4px 10px',
                                  fontSize: '0.75rem',
                                  cursor: 'pointer',
                                  fontWeight: 600,
                                }}
                              >
                                ⭐ Chấm sao
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

          {/* Footer */}
          <div style={{ marginTop: '1.2rem', display: 'flex', justifyContent: 'flex-end' }}>
            <button type="button" className="eo-btn-cancel" onClick={onClose}>
              Đóng
            </button>
          </div>
        </div>
      </div>

      {/* Modal Đánh giá Worker con */}
      {selectedForReview && (
        <RateWorkerModal
          isOpen={Boolean(selectedForReview)}
          onClose={() => setSelectedForReview(null)}
          assignment={selectedForReview}
          onSubmitReview={handleSubmitReview}
          submitting={submittingReview}
        />
      )}
    </>
  );
};

export default LiveAttendanceModal;

