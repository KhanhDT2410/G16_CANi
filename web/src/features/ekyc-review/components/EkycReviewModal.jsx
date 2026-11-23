import React, { useState, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { ekycReviewApi } from '../api/ekycReviewApi';
import { showToast } from '../../../components/common/Toast';

const IMAGE_SLOTS = [
  { key: 'front', label: 'Mặt trước CCCD' },
  { key: 'back', label: 'Mặt sau CCCD' },
  { key: 'selfie', label: 'Ảnh chân dung Selfie' },
];

const QUICK_REJECT_REASONS = [
  'Ảnh CCCD bị mờ, chói sáng hoặc mất góc, không đọc rõ thông tin.',
  'Ảnh Selfie không khớp với chân dung trên CCCD.',
  'Thông tin trên giấy tờ có dấu hiệu chỉnh sửa hoặc không hợp lệ.',
];

const formatScorePercent = (val) => {
  if (val == null || Number.isNaN(Number(val))) return '—';
  const num = Number(val);
  const pct = num <= 1 ? num * 100 : num;
  return `${pct.toFixed(1)}%`;
};

export default function EkycReviewModal({
  targetType,
  item,
  canManageLegalHold = false,
  onClose,
  onReviewed,
}) {
  const [detail, setDetail] = useState(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [signedImages, setSignedImages] = useState({});
  const [loadingImages, setLoadingImages] = useState(false);
  const [decision, setDecision] = useState('APPROVED');
  const [reviewNotes, setReviewNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [togglingHold, setTogglingHold] = useState(false);

  const verificationId = item?.verificationId;

  const fetchSignedImages = useCallback(async () => {
    if (!verificationId) return;
    setLoadingImages(true);
    try {
      const results = await Promise.allSettled(
        IMAGE_SLOTS.map(async ({ key }) => {
          const url = await ekycReviewApi.getSignedImageUrl(targetType, verificationId, key);
          return [key, url];
        })
      );
      const nextMap = {};
      results.forEach((res) => {
        if (res.status === 'fulfilled' && res.value?.[1]) {
          nextMap[res.value[0]] = res.value[1];
        }
      });
      setSignedImages(nextMap);
    } finally {
      setLoadingImages(false);
    }
  }, [targetType, verificationId]);

  useEffect(() => {
    if (!verificationId) return;
    let active = true;

    const loadDossier = async () => {
      setLoadingDetail(true);
      setDetail(null);
      setSignedImages({});
      setDecision('APPROVED');
      setReviewNotes('');
      try {
        const data = await ekycReviewApi.getDetail(targetType, verificationId);
        if (!active) return;
        setDetail(data);
        if (data?.imagesAvailable !== false) {
          fetchSignedImages();
        }
      } catch (err) {
        if (active) {
          showToast(
            err.response?.data?.message || 'Không thể tải chi tiết hồ sơ eKYC.',
            'error'
          );
        }
      } finally {
        if (active) setLoadingDetail(false);
      }
    };

    loadDossier();

    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKeyDown);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      active = false;
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = prevOverflow;
    };
  }, [verificationId, targetType, fetchSignedImages, onClose]);

  if (!item || typeof document === 'undefined') return null;

  const record = detail || item;
  const displayName =
    targetType === 'eo'
      ? record.representativeName || record.companyName || 'Nhà tổ chức sự kiện'
      : record.fullName || record.workerName || 'Nhân sự CANi';

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (decision === 'REJECTED' && !reviewNotes.trim()) {
      showToast('Vui lòng nhập lý do từ chối hồ sơ để người dùng biết cách chụp lại.', 'error');
      return;
    }

    setSubmitting(true);
    try {
      await ekycReviewApi.reviewDossier(targetType, verificationId, {
        decision,
        reviewNotes: reviewNotes.trim() || (decision === 'APPROVED' ? 'Đã đối chiếu thủ công hợp lệ.' : ''),
      });
      showToast(
        decision === 'APPROVED'
          ? 'Đã phê duyệt hồ sơ eKYC thành công!'
          : 'Đã từ chối hồ sơ eKYC.',
        'success'
      );
      if (onReviewed) onReviewed();
      onClose();
    } catch (err) {
      showToast(
        err.response?.data?.message || 'Không thể lưu quyết định thẩm định eKYC.',
        'error'
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleLegalHold = async () => {
    const nextHold = !record.legalHold;
    setTogglingHold(true);
    try {
      const updated = await ekycReviewApi.updateLegalHold(targetType, verificationId, {
        legalHold: nextHold,
        reason: nextHold ? 'Admin bật bảo lưu phục vụ đối soát.' : 'Admin gỡ trạng thái bảo lưu.',
      });
      setDetail((prev) => ({ ...(prev || item), legalHold: updated?.legalHold ?? nextHold }));
      showToast(
        nextHold ? 'Đã bật bảo lưu pháp lý cho hồ sơ này.' : 'Đã gỡ bảo lưu pháp lý.',
        'success'
      );
      if (onReviewed) onReviewed();
    } catch (err) {
      showToast(
        err.response?.data?.message || 'Không thể cập nhật trạng thái bảo lưu.',
        'error'
      );
    } finally {
      setTogglingHold(false);
    }
  };

  const modalContent = (
    <div className="ekyc-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="ekyc-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="ekyc-modal-header">
          <div>
            <h3>
              Thẩm Định Hồ Sơ eKYC ({targetType === 'eo' ? 'Nhà Tổ Chức' : 'Nhân Sự Worker'})
            </h3>
            <p className="ekyc-modal-subtitle">
              Mã hồ sơ: {verificationId} {record.legalHold ? '• Đang bảo lưu pháp lý' : ''}
            </p>
          </div>
          <button type="button" className="ekyc-modal-close" onClick={onClose} aria-label="Đóng">
            &times;
          </button>
        </div>

        <div className="ekyc-modal-body">
          {loadingDetail ? (
            <div className="ekyc-loading-box">Đang tải chi tiết hồ sơ và liên kết ảnh bảo mật...</div>
          ) : (
            <>
              <div className="ekyc-info-grid">
                <div>
                  <span className="ekyc-info-label">
                    {targetType === 'eo' ? 'Người đại diện:' : 'Họ và tên OCR:'}
                  </span>
                  <strong className="ekyc-info-value">{displayName}</strong>
                </div>
                {targetType === 'eo' && (
                  <div>
                    <span className="ekyc-info-label">Đơn vị / Công ty:</span>
                    <span className="ekyc-info-value">{record.companyName || '—'}</span>
                  </div>
                )}
                <div>
                  <span className="ekyc-info-label">Số CCCD (đã che):</span>
                  <strong className="ekyc-info-value">{record.cccdNumberMasked || 'Chưa trích xuất'}</strong>
                </div>
                <div>
                  <span className="ekyc-info-label">Ngày sinh / Giới tính:</span>
                  <span className="ekyc-info-value">
                    {[record.dob, record.gender].filter(Boolean).join(' • ') || '—'}
                  </span>
                </div>
                <div>
                  <span className="ekyc-info-label">Khớp khuôn mặt (VNPT):</span>
                  <span className="ekyc-info-value">{formatScorePercent(record.faceMatchScore)}</span>
                </div>
                <div>
                  <span className="ekyc-info-label">Độ tin cậy OCR:</span>
                  <span className="ekyc-info-value">{formatScorePercent(record.ocrConfidence)}</span>
                </div>
                <div>
                  <span className="ekyc-info-label">Quê quán:</span>
                  <span className="ekyc-info-value">{record.originAddress || '—'}</span>
                </div>
                <div>
                  <span className="ekyc-info-label">Nơi thường trú:</span>
                  <span className="ekyc-info-value">{record.residenceAddress || '—'}</span>
                </div>
                <div>
                  <span className="ekyc-info-label">Ngày cấp / Hết hạn:</span>
                  <span className="ekyc-info-value">
                    {[record.issueDate, record.expiryDate].filter(Boolean).join(' → ') || '—'}
                  </span>
                </div>
              </div>

              {record.routingReason && (
                <div className="ekyc-routing-alert">
                  <strong>Lý do chuyển duyệt thủ công:</strong> {record.routingReason}
                </div>
              )}

              <div className="ekyc-images-section">
                <div className="ekyc-images-header">
                  <h4>Ảnh Đối Chiếu Bảo Mật (Signed URL 180 giây)</h4>
                  <button
                    type="button"
                    className="btn-ekyc-secondary"
                    onClick={fetchSignedImages}
                    disabled={loadingImages || record.imagesAvailable === false}
                  >
                    {loadingImages ? 'Đang lấy liên kết...' : 'Làm mới ảnh'}
                  </button>
                </div>

                {record.imagesAvailable === false ? (
                  <div className="ekyc-images-empty">
                    Ảnh xác minh không khả dụng hoặc đã hết hạn lưu trữ.
                  </div>
                ) : (
                  <div className="ekyc-images-grid">
                    {IMAGE_SLOTS.map(({ key, label }) => (
                      <div key={key} className="ekyc-image-slot">
                        <div className="ekyc-image-slot-title">{label}</div>
                        {signedImages[key] ? (
                          <a href={signedImages[key]} target="_blank" rel="noreferrer">
                            <img src={signedImages[key]} alt={label} className="ekyc-image-preview" />
                          </a>
                        ) : (
                          <div className="ekyc-image-placeholder">
                            {loadingImages ? 'Đang tải...' : 'Không có ảnh'}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <form onSubmit={handleSubmitReview} className="ekyc-review-form">
                <div className="ekyc-decision-row">
                  <label className={`ekyc-decision-option ${decision === 'APPROVED' ? 'selected-approve' : ''}`}>
                    <input
                      type="radio"
                      name="ekycDecision"
                      value="APPROVED"
                      checked={decision === 'APPROVED'}
                      onChange={() => setDecision('APPROVED')}
                    />
                    <span>Chấp thuận định danh (APPROVED)</span>
                  </label>
                  <label className={`ekyc-decision-option ${decision === 'REJECTED' ? 'selected-reject' : ''}`}>
                    <input
                      type="radio"
                      name="ekycDecision"
                      value="REJECTED"
                      checked={decision === 'REJECTED'}
                      onChange={() => setDecision('REJECTED')}
                    />
                    <span>Từ chối hồ sơ (REJECTED)</span>
                  </label>
                </div>

                {decision === 'REJECTED' && (
                  <div className="ekyc-quick-reasons">
                    {QUICK_REJECT_REASONS.map((reason) => (
                      <button
                        key={reason}
                        type="button"
                        className="ekyc-quick-chip"
                        onClick={() => setReviewNotes(reason)}
                      >
                        {reason}
                      </button>
                    ))}
                  </div>
                )}

                <div className="ekyc-notes-field">
                  <label htmlFor="ekycReviewNotes">
                    Ghi chú thẩm định {decision === 'REJECTED' ? '(Bắt buộc khi từ chối)' : '(Tùy chọn)'}:
                  </label>
                  <textarea
                    id="ekycReviewNotes"
                    rows={3}
                    placeholder="Nhập kết luận kiểm tra CCCD và ảnh chân dung..."
                    value={reviewNotes}
                    onChange={(e) => setReviewNotes(e.target.value)}
                  />
                </div>

                <div className="ekyc-modal-footer">
                  {canManageLegalHold && (
                    <button
                      type="button"
                      className="btn-ekyc-hold"
                      onClick={handleToggleLegalHold}
                      disabled={togglingHold || submitting}
                    >
                      {record.legalHold ? 'Gỡ Bảo Lưu Pháp Lý' : 'Bật Bảo Lưu Pháp Lý'}
                    </button>
                  )}
                  <div className="ekyc-modal-footer-right">
                    <button type="button" className="btn-ekyc-secondary" onClick={onClose}>
                      Đóng
                    </button>
                    <button
                      type="submit"
                      className={decision === 'APPROVED' ? 'btn-ekyc-approve' : 'btn-ekyc-reject'}
                      disabled={submitting}
                    >
                      {submitting
                        ? 'Đang lưu...'
                        : decision === 'APPROVED'
                          ? 'Xác Nhận Phê Duyệt'
                          : 'Xác Nhận Từ Chối'}
                    </button>
                  </div>
                </div>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );

  return createPortal(modalContent, document.body);
}
