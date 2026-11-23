import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { skillReviewApi, resolveSkillDocumentUrl } from '../api/skillReviewApi';
import { showToast } from '../../../components/common/Toast';

export default function SkillReviewModal({ item, onClose, onUpdated }) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(false);
  const [decision, setDecision] = useState('APPROVE');
  const [reviewNotes, setReviewNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const verificationId = item?.id;

  useEffect(() => {
    if (!verificationId) return;
    let active = true;
    const loadDetail = async () => {
      setLoading(true);
      setDecision('APPROVE');
      setReviewNotes('');
      try {
        const data = await skillReviewApi.getDetail(verificationId);
        if (active) setDetail(data);
      } catch (err) {
        if (active) showToast(err.response?.data?.message || 'Không thể tải chi tiết chứng chỉ.', 'error');
      } finally {
        if (active) setLoading(false);
      }
    };
    loadDetail();
    return () => { active = false; };
  }, [verificationId]);

  if (!item || typeof document === 'undefined') return null;
  const record = detail || item;
  const docUrl = resolveSkillDocumentUrl(record.documentUrl);
  const isPdf = (docUrl || '').toLowerCase().includes('.pdf');
  const isPendingReview = record.status === 'PENDING_REVIEW';
  const isFailed = record.status === 'VERIFICATION_FAILED';
  const isApproved = record.status === 'APPROVED';

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    if (decision !== 'APPROVE' && !reviewNotes.trim()) {
      showToast('Vui lòng nhập ghi chú để Worker biết lý do.', 'error');
      return;
    }
    setSubmitting(true);
    try {
      await skillReviewApi.reviewVerification(verificationId, {
        decision,
        reviewNotes: reviewNotes.trim() || undefined,
      });
      showToast(
        decision === 'APPROVE' ? 'Đã phê duyệt chứng chỉ và cộng +25 SP!' : 'Đã cập nhật kết quả thẩm định.',
        'success'
      );
      if (onUpdated) onUpdated();
      onClose();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể lưu kết quả thẩm định.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleMoveToManualReview = async () => {
    setSubmitting(true);
    try {
      await skillReviewApi.moveFailedToManualReview(verificationId);
      showToast('Đã chuyển hồ sơ sang hàng đợi duyệt thủ công.', 'success');
      if (onUpdated) onUpdated();
      onClose();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể chuyển sang duyệt thủ công.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRevoke = async () => {
    const reason = reviewNotes.trim() || 'Thu hồi sau hậu kiểm chứng chỉ không hợp lệ.';
    setSubmitting(true);
    try {
      await skillReviewApi.revokeVerification(verificationId, reason);
      showToast('Đã thu hồi chứng nhận và khấu trừ điểm SP.', 'success');
      if (onUpdated) onUpdated();
      onClose();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể thu hồi chứng nhận.', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return createPortal(
    <div className="skill-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="skill-modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="skill-modal-header">
          <div>
            <h3>Thẩm Định Chứng Chỉ Nghề Nghiệp</h3>
            <p className="skill-modal-sub">
              {record.workerName || 'Worker'} • {record.skillName || record.jobType || 'Kỹ năng'}
            </p>
          </div>
          <button type="button" className="skill-modal-close" onClick={onClose}>&times;</button>
        </div>

        <div className="skill-modal-body">
          {loading ? (
            <div className="skill-loading-box">Đang tải chi tiết hồ sơ...</div>
          ) : (
            <>
              <div className="skill-info-grid">
                <div><span>Trạng thái:</span><strong>{record.status}</strong></div>
                <div><span>Kỹ năng:</span><strong>{record.skillName || record.jobType || '—'}</strong></div>
                <div><span>Người nhận trên bằng:</span><span>{record.recipientName || '—'}</span></div>
                <div><span>Đơn vị cấp:</span><span>{record.institution || '—'}</span></div>
                <div><span>Số hiệu / Ngày cấp:</span><span>{[record.certificateNumber, record.issueDate].filter(Boolean).join(' • ') || '—'}</span></div>
                <div><span>Nguồn thẩm định:</span><span>{record.verificationSource || 'MANUAL_REVIEW'}</span></div>
              </div>

              {record.routingReason && (
                <div className="skill-alert-box">
                  <strong>Ghi chú tiếp nhận:</strong> {record.routingReason}
                </div>
              )}

              <div className="skill-doc-preview">
                <div className="skill-doc-header">
                  <strong>Tệp chứng chỉ đã nộp</strong>
                  {docUrl && <a href={docUrl} target="_blank" rel="noreferrer">Mở tab mới</a>}
                </div>
                {!docUrl ? (
                  <div className="skill-doc-empty">Không có tệp đính kèm khả dụng.</div>
                ) : isPdf ? (
                  <iframe title="Chứng chỉ PDF" src={docUrl} className="skill-doc-frame" />
                ) : (
                  <img src={docUrl} alt="Chứng chỉ kỹ năng" className="skill-doc-img" />
                )}
              </div>

              {isFailed && (
                <div className="skill-failed-actions">
                  <p>Hồ sơ cũ này đang ở trạng thái lỗi xử lý tự động trước đây. Bạn có thể chuyển sang hàng đợi duyệt thủ công:</p>
                  <button type="button" className="btn-skill-primary" disabled={submitting} onClick={handleMoveToManualReview}>
                    {submitting ? 'Đang xử lý...' : 'Chuyển sang duyệt thủ công'}
                  </button>
                </div>
              )}

              {isPendingReview && (
                <form onSubmit={handleSubmitReview} className="skill-review-form">
                  <div className="skill-decision-row">
                    {[
                      { value: 'APPROVE', label: 'Chấp thuận (+25 SP)' },
                      { value: 'NEEDS_MORE_INFO', label: 'Yêu cầu bổ sung' },
                      { value: 'REJECT', label: 'Từ chối' },
                    ].map((opt) => (
                      <label key={opt.value} className={`skill-decision-opt ${decision === opt.value ? 'selected' : ''}`}>
                        <input
                          type="radio"
                          name="skillDecision"
                          value={opt.value}
                          checked={decision === opt.value}
                          onChange={() => setDecision(opt.value)}
                        />
                        <span>{opt.label}</span>
                      </label>
                    ))}
                  </div>
                  <textarea
                    rows={3}
                    placeholder="Nhập nhận xét thẩm định (bắt buộc nếu từ chối hoặc yêu cầu bổ sung)..."
                    value={reviewNotes}
                    onChange={(e) => setReviewNotes(e.target.value)}
                  />
                  <div className="skill-modal-footer">
                    <button type="button" className="btn-skill-secondary" onClick={onClose}>Đóng</button>
                    <button type="submit" className="btn-skill-primary" disabled={submitting}>
                      {submitting ? 'Đang lưu...' : 'Xác Nhận Thẩm Định'}
                    </button>
                  </div>
                </form>
              )}

              {isApproved && (
                <div className="skill-review-form">
                  <textarea
                    rows={2}
                    placeholder="Lý do thu hồi chứng chỉ (nếu phát hiện không hợp lệ)..."
                    value={reviewNotes}
                    onChange={(e) => setReviewNotes(e.target.value)}
                  />
                  <div className="skill-modal-footer">
                    <button type="button" className="btn-skill-secondary" onClick={onClose}>Đóng</button>
                    <button type="button" className="btn-skill-danger" disabled={submitting} onClick={handleRevoke}>
                      {submitting ? 'Đang thu hồi...' : 'Thu hồi chứng nhận & điểm SP'}
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
