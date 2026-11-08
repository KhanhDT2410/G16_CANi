import { formatDateTime, REASON_CODE_LABELS } from '../types/rsAppeal.constants';
import { RsAppealStatusBadge } from './RsAppealStatusBadge';

export const RsAppealDetailModal = ({ appeal, isOpen, onClose, onReview }) => {
  if (!isOpen || !appeal) return null;

  const isPending = appeal.status === 'SUBMITTED' || appeal.status === 'UNDER_REVIEW';

  return (
    <div className="appeal-modal-overlay" onClick={onClose}>
      <div className="appeal-modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="appeal-modal-header">
          <div>
            <h3 className="appeal-modal-title">Chi Tiết Đơn Kháng Cáo Điểm RS</h3>
            <p className="appeal-modal-sub">Mã đơn: {appeal.id}</p>
          </div>
          <button className="btn-modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="appeal-modal-body">
          <div className="detail-section">
            <div className="detail-row">
              <span className="detail-label">Nhân sự:</span>
              <span className="detail-val font-semibold">{appeal.workerName} ({appeal.workerEmail})</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Điểm RS hiện tại:</span>
              <span className="detail-val highlight">{appeal.currentReliabilityScore ?? '—'} / 100</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Mức điểm bị trừ:</span>
              <span className="detail-val penalty">-{appeal.penaltyAmount} RS</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Thời điểm bị phạt:</span>
              <span className="detail-val">{formatDateTime(appeal.deductedAt)}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Lý do hệ thống trừ:</span>
              <span className="detail-val">{appeal.deductionReason || '—'}</span>
            </div>
            <div className="detail-row">
              <span className="detail-label">Trạng thái đơn:</span>
              <span className="detail-val">
                <RsAppealStatusBadge status={appeal.status} />
              </span>
            </div>
          </div>

          <div className="detail-box">
            <h4 className="detail-box-title">Giải Trình Của Worker:</h4>
            <p className="detail-box-reason">
              <strong>Lý do bất khả kháng:</strong> {REASON_CODE_LABELS[appeal.reasonCode] || appeal.reasonCode}
            </p>
            <p className="detail-box-text">{appeal.reasonDetail}</p>
          </div>

          {appeal.evidence && (
            <div className="detail-evidence-box">
              <h4 className="detail-box-title">Minh Chứng / Bằng Chứng Đính Kèm:</h4>
              <a
                href={appeal.evidence}
                target="_blank"
                rel="noreferrer"
                className="btn-evidence-link"
              >
                📎 Mở liên kết tài liệu minh chứng
              </a>
            </div>
          )}

          {appeal.decidedAt && (
            <div className="detail-decision-box">
              <h4 className="detail-box-title">Kết Luận Thẩm Định:</h4>
              <p><strong>Người duyệt:</strong> {appeal.reviewerName || 'Hệ thống'}</p>
              <p><strong>Thời gian:</strong> {formatDateTime(appeal.decidedAt)}</p>
              <p><strong>Ghi chú giải trình:</strong> {appeal.decisionNote}</p>
            </div>
          )}
        </div>

        <div className="appeal-modal-footer">
          <button className="btn-secondary" onClick={onClose}>Đóng</button>
          {isPending && (
            <button
              className="btn-primary"
              onClick={() => {
                onClose();
                onReview(appeal);
              }}
            >
              Ra Quyết Định Xử Lý
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

