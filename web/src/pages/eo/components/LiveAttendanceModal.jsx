import React, { useState, useEffect, useCallback } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import RateWorkerModal from './RateWorkerModal';
import ConfirmVerifySkillModal, { resolvePreassignedPerformedSkillId } from './ConfirmVerifySkillModal';
import { WorkerProfileModal } from './worker-profile';
import { useWorkerProfile } from '../hooks/useWorkerProfile';
import { resolveAssetUrl, buildWorkerVietQrUrl } from '../utils/resolveAssetUrl';
import { formatVndWhole } from '../utils/eventHelpers';

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
  skillTaxonomy = [],
  onOpenQr,
  onOpenPaymentDeclaration,
}) => {
  const [attendanceList, setAttendanceList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [verifyingId, setVerifyingId] = useState(null);
  const [selectedForVerify, setSelectedForVerify] = useState(null);
  const [selectedForReview, setSelectedForReview] = useState(null);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [previewImage, setPreviewImage] = useState(null);
  const workerProfile = useWorkerProfile();

  const loadAttendance = useCallback(async () => {
    if (!shift?.shiftId) return;
    setLoading(true);
    try {
      const data = await eoService.getShiftAttendance(shift.shiftId);
      setAttendanceList(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Lỗi khi tải attendance list:', e);
      setAttendanceList([]);
    } finally {
      setLoading(false);
    }
  }, [shift?.shiftId]);

  useEffect(() => {
    if (isOpen && shift) {
      loadAttendance();
    }
  }, [isOpen, shift, loadAttendance]);

  useEffect(() => {
    if (!isOpen) return;
    const handlePaymentUpdated = () => loadAttendance();
    window.addEventListener('cani_payment_updated', handlePaymentUpdated);
    return () => window.removeEventListener('cani_payment_updated', handlePaymentUpdated);
  }, [isOpen, loadAttendance]);

  const handleVerify = async (assignmentId, verifyPayload = null) => {
    setVerifyingId(assignmentId);
    try {
      await eoService.verifyAttendance(assignmentId, verifyPayload);
      setSelectedForVerify(null);
      showToast('Đã xác nhận hoàn thành ca và duyệt công cho nhân sự!', 'success');
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
                <h3 style={{ margin: 0 }}>Theo Dõi Điểm Danh & Vận Hành Ca</h3>
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

            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <button
                type="button"
                onClick={() => onOpenQr(shift)}
                className="eo-btn-primary"
                style={{
                  padding: '0.45rem 0.95rem',
                  fontSize: '0.85rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                }}
              >
                Mã QR Ca
              </button>
              <button
                type="button"
                onClick={loadAttendance}
                className="eo-btn-secondary"
                style={{
                  padding: '0.45rem 0.85rem',
                  fontSize: '0.85rem',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
                title="Làm mới dữ liệu"
              >
                Làm mới
              </button>
              <button
                type="button"
                onClick={onClose}
                className="eo-btn-secondary"
                style={{
                  padding: '0.45rem 0.85rem',
                  fontSize: '0.85rem',
                  color: '#64748b',
                }}
              >
                Đóng
              </button>
            </div>
          </div>

          {/* Table / List */}
          {loading ? (
            <div style={{ padding: '3rem 0', textAlign: 'center', color: '#64748b' }}>
              Đang tải danh sách điểm danh nhân sự...
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
                          <div
                            onClick={() => workerProfile.openProfile(item.workerId || item.userId)}
                            style={{
                              fontWeight: 600,
                              color: '#0066FF',
                              cursor: 'pointer',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: 4,
                            }}
                            title="Bấm để xem hồ sơ năng lực chi tiết"
                          >
                            <span>{item.workerName}</span>
                          </div>
                          <div style={{ fontSize: '0.75rem', color: '#64748b' }}>{item.workerPhone}</div>
                          <div style={{ fontSize: '0.75rem', color: '#2563eb', marginTop: 2 }}>
                            Đánh giá: {item.workerRatingAvg || 5.0} • Uy tín: {item.workerReliabilityScore || 100}
                          </div>
                        </td>

                        {/* Trạng thái điểm danh */}
                        <td style={{ padding: '12px 14px' }}>
                          {isCheckedIn && (
                            <span style={{ background: '#dcfce7', color: '#15803d', padding: '2px 8px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                              Đã Check-in {item.isOnTime ? '(Đúng giờ)' : '(Trễ ca)'}
                            </span>
                          )}
                          {isCompleted && (
                            <span style={{ background: '#e0e7ff', color: '#3730a3', padding: '2px 8px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                              Đã Hoàn Thành
                            </span>
                          )}
                          {!isCheckedIn && !isCompleted && (
                            <span style={{ background: '#fef3c7', color: '#b45309', padding: '2px 8px', borderRadius: 4, fontWeight: 500, fontSize: '0.78rem' }}>
                              Chờ Đến Ca
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

                        {/* Trạng thái thanh toán & bằng chứng */}
                        <td style={{ padding: '12px 14px' }}>
                          {(() => {
                            const workerQrUrl = buildWorkerVietQrUrl(item);
                            return (
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start' }}>
                                {item.declaredAmount != null && (
                                  <div style={{ fontSize: '0.75rem', color: '#0369a1' }}>
                                    Đã khai: <strong>{formatVndWhole(item.declaredAmount)}</strong>
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
                                      title: `Chứng từ thanh toán cho ${item.workerName}`,
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
                                      fontSize: '0.72rem',
                                      fontWeight: 700,
                                      cursor: 'pointer',
                                    }}
                                  >
                                    Xem chứng từ
                                  </button>
                                )}
                                {item.workerConfirmStatus === 'RECEIVED_FULL' && (
                                  <span style={{ color: '#16a34a', fontWeight: 600, fontSize: '0.75rem' }}>Worker đã nhận đủ</span>
                                )}
                                {item.workerConfirmStatus === 'RECEIVED_PARTIAL' && (
                                  <span style={{ color: '#b45309', fontWeight: 600, fontSize: '0.72rem' }}>Nhận thiếu: {item.workerComment || ''}</span>
                                )}
                                {item.workerConfirmStatus === 'NOT_RECEIVED' && (
                                  <span style={{ color: '#dc2626', fontWeight: 600, fontSize: '0.72rem' }}>Chưa nhận: {item.workerComment || ''}</span>
                                )}
                                {item.paymentStatus === 'CONFIRMED_FULL' && !item.workerConfirmStatus && (
                                  <span style={{ color: '#16a34a', fontWeight: 600 }}>Đã thanh toán</span>
                                )}
                                {item.paymentStatus === 'DECLARED_PENDING_WORKER' && !item.workerConfirmStatus && (
                                  <span style={{ color: '#d97706', fontWeight: 500 }}>Chờ worker xác nhận</span>
                                )}
                                <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                                  {workerQrUrl && (
                                    <button
                                      type="button"
                                      onClick={() => setPreviewImage({ url: workerQrUrl, title: `Mã VietQR của ${item.workerName}` })}
                                      style={{
                                        background: '#f5f3ff',
                                        color: '#6d28d9',
                                        border: '1px solid #ddd6fe',
                                        borderRadius: 4,
                                        padding: '2px 6px',
                                        fontSize: '0.7rem',
                                        fontWeight: 600,
                                        cursor: 'pointer',
                                      }}
                                    >
                                      QR
                                    </button>
                                  )}
                                  {(!item.paymentStatus || item.paymentStatus === 'NOT_DECLARED') && item.eoConfirmed && (
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
                                      Khai báo lương
                                    </button>
                                  )}
                                </div>
                              </div>
                            );
                          })()}
                        </td>

                        {/* Thao tác */}
                        <td style={{ padding: '12px 14px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                            {/* Nút duyệt hoàn thành công */}
                            {!item.eoConfirmed ? (
                              <button
                                type="button"
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
                                {verifyingId === item.assignmentId ? 'Đang duyệt...' : 'Duyệt Công'}
                              </button>
                            ) : (
                              <span style={{ color: '#16a34a', fontSize: '0.75rem', fontWeight: 600, padding: '4px 0' }}>
                                Đã duyệt công
                              </span>
                            )}

                            {/* Nút đánh giá Worker: Chỉ mở khi Worker đã Check-out hoặc ca đã hoàn thành */}
                            {item.hasReviewedWorker ? (
                              <span style={{ color: '#eab308', fontSize: '0.75rem', fontWeight: 600, padding: '4px 0' }}>
                                Đã đánh giá
                              </span>
                            ) : isCompleted ? (
                              <button
                                type="button"
                                onClick={() => setSelectedForReview(item)}
                                style={{
                                  background: '#fef3c7',
                                  color: '#b45309',
                                  border: '1px solid #fde68a',
                                  borderRadius: 4,
                                  padding: '4px 10px',
                                  fontSize: '0.75rem',
                                  cursor: 'pointer',
                                  fontWeight: 600,
                                }}
                                title="Đánh giá năng lực và thái độ của Worker sau ca làm việc"
                              >
                                Đánh giá
                              </button>
                            ) : (
                              <span
                                style={{
                                  color: '#94a3b8',
                                  fontSize: '0.75rem',
                                  fontStyle: 'italic',
                                  padding: '4px 6px',
                                }}
                                title="Chỉ mở đánh giá sau khi Worker đã Check-out hoàn thành ca làm việc"
                              >
                                Chờ Check-out
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                  {attendanceList.length === 0 && (
                    <tr>
                      <td colSpan={6} style={{ textAlign: 'center', padding: '2rem', color: '#94a3b8' }}>
                        Chưa có nhân sự nào được phân công hoặc điểm danh trong ca này.
                      </td>
                    </tr>
                  )}
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

      {/* Modal Xem Hồ sơ Năng lực Worker */}
      <WorkerProfileModal
        isOpen={workerProfile.isOpen}
        loading={workerProfile.loading}
        error={workerProfile.error}
        profile={workerProfile.profile}
        onClose={workerProfile.closeProfile}
      />

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
            src={previewImage.url}
            alt={previewImage.title}
            style={{ maxWidth: '90vw', maxHeight: '80vh', borderRadius: '12px', backgroundColor: '#ffffff' }}
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
    </>
  );
};

export default LiveAttendanceModal;

