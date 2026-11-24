import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { disputeApi } from '../api/disputeApi';
import { DISPUTE_TYPES, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const CreateDisputeModal = ({ onClose, onSubmit }) => {
  const [assignmentId, setAssignmentId] = useState('');
  const [assignments, setAssignments] = useState([]);
  const [loadingAssignments, setLoadingAssignments] = useState(true);
  const [manualIdMode, setManualIdMode] = useState(false);
  const [type, setType] = useState(DISPUTE_TYPES.PAYMENT);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [evidenceUrl, setEvidenceUrl] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    let mounted = true;
    disputeApi
      .getMyAssignments()
      .then((list) => {
        if (!mounted) return;
        const safeList = Array.isArray(list) ? list : [];
        setAssignments(safeList);
        if (safeList.length === 1 && safeList[0].assignmentId) {
          setAssignmentId(String(safeList[0].assignmentId));
        } else if (safeList.length === 0) {
          setManualIdMode(true);
        }
      })
      .catch(() => {
        if (mounted) {
          setAssignments([]);
          setManualIdMode(true);
        }
      })
      .finally(() => {
        if (mounted) setLoadingAssignments(false);
      });

    return () => {
      mounted = false;
      document.body.style.overflow = prev;
    };
  }, []);

  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const url = await eoService.uploadImage(file);
      if (url) {
        setEvidenceUrl(url);
        showToast('Tải ảnh minh chứng lên thành công', 'success');
      }
    } catch (err) {
      showToast('Lỗi tải ảnh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setUploading(false);
    }
  };

  const descLength = description.trim().length;
  const isDescValid = descLength >= 50 && descLength <= 3000;

  const handleSubmit = async (e) => {
    e.preventDefault();
    const trimmedId = assignmentId.trim();
    if (!trimmedId) {
      showToast('Vui lòng chọn ca làm việc hoặc nhập Mã phân công ca làm việc', 'warning');
      return;
    }
    if (!UUID_REGEX.test(trimmedId)) {
      showToast('Mã Ca Làm Việc phải là chuỗi UUID hợp lệ hoặc chọn trực tiếp từ danh sách bên trên', 'warning');
      return;
    }
    if (!title.trim()) {
      showToast('Vui lòng nhập tiêu đề tranh chấp', 'warning');
      return;
    }
    if (!isDescValid) {
      showToast('Mô tả tranh chấp phải từ 50 đến 3000 ký tự', 'warning');
      return;
    }

    setSubmitting(true);
    try {
      await onSubmit({
        assignmentId: trimmedId,
        type,
        title: title.trim(),
        description: description.trim(),
        evidence: evidenceUrl.trim() || undefined,
      });
      onClose();
    } catch {
      // Toast lỗi đã được hook xử lý
    } finally {
      setSubmitting(false);
    }
  };

  return createPortal(
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <h3>Nộp Đơn Tranh Chấp & Khiếu Nại</h3>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body">
            <div className="dispute-modal-notice">
              Lưu ý: Đơn chỉ được nộp trong vòng 14 ngày kể từ khi ca kết thúc, mô tả tối thiểu 50 ký tự.
            </div>

            <div className="dispute-field-group">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                <label style={{ margin: 0 }}>Ca Làm Việc Liên Quan *</label>
                {assignments.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setManualIdMode((v) => !v)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#2563eb',
                      fontSize: '0.78rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      padding: 0,
                    }}
                  >
                    {manualIdMode ? 'Chọn từ danh sách ca' : 'Nhập mã UUID thủ công'}
                  </button>
                )}
              </div>

              {loadingAssignments ? (
                <div style={{ fontSize: '0.84rem', color: '#64748b', padding: '0.5rem 0' }}>
                  Đang tải danh sách ca làm việc của bạn...
                </div>
              ) : !manualIdMode && assignments.length > 0 ? (
                <select
                  className="dispute-select"
                  value={assignmentId}
                  onChange={(e) => setAssignmentId(e.target.value)}
                  required
                >
                  <option value="">-- Chọn ca làm việc muốn khiếu nại --</option>
                  {assignments.map((a) => {
                    const timeStr = a.startTime
                      ? new Date(a.startTime).toLocaleDateString('vi-VN', {
                          day: '2-digit',
                          month: '2-digit',
                          year: 'numeric',
                        })
                      : '';
                    return (
                      <option key={a.assignmentId} value={a.assignmentId}>
                        {a.eventName} · {a.jobType} · {a.counterpartyName}
                        {timeStr ? ` (${timeStr})` : ''}
                      </option>
                    );
                  })}
                </select>
              ) : (
                <>
                  <input
                    type="text"
                    className="dispute-input"
                    placeholder="Ví dụ: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
                    value={assignmentId}
                    onChange={(e) => setAssignmentId(e.target.value)}
                    required
                  />
                  {assignments.length === 0 && (
                    <div style={{ fontSize: '0.76rem', color: '#64748b', marginTop: 4 }}>
                      Chưa ghi nhận ca phân công nào trong tài khoản của bạn. Bạn có thể dán mã phân công ca làm việc nếu có.
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="dispute-field-group">
              <label>Danh mục tranh chấp *</label>
              <select
                className="dispute-select"
                value={type}
                onChange={(e) => setType(e.target.value)}
              >
                {Object.entries(DISPUTE_TYPES).map(([key, val]) => (
                  <option key={key} value={val}>
                    {DISPUTE_TYPE_LABELS[val] || val}
                  </option>
                ))}
              </select>
            </div>

            <div className="dispute-field-group">
              <label>Tiêu đề tranh chấp *</label>
              <input
                type="text"
                className="dispute-input"
                maxLength={200}
                placeholder="Tóm tắt nội dung khiếu nại (tối đa 200 ký tự)"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                required
              />
            </div>

            <div className="dispute-field-group">
              <label>Mô tả chi tiết tình huống (Tối thiểu 50 ký tự) *</label>
              <textarea
                className="dispute-textarea"
                rows={4}
                placeholder="Mô tả cụ thể diễn biến, thời gian, số tiền tranh chấp hoặc thỏa thuận bị vi phạm..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                required
              />
              <div className={`char-counter ${isDescValid ? 'valid' : 'invalid'}`}>
                {descLength} / 50 ký tự tối thiểu {isDescValid ? '(Đủ điều kiện)' : '(Chưa đủ)'}
              </div>
            </div>

            <div className="dispute-field-group">
              <label>Ảnh / Minh chứng đính kèm ban đầu (Tùy chọn)</label>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem' }}>
                <label className="btn-quick-fill" style={{ margin: 0, cursor: 'pointer', display: 'inline-flex', alignItems: 'center' }}>
                  {uploading ? 'Đang tải ảnh...' : 'Chọn ảnh từ thiết bị'}
                  <input
                    type="file"
                    accept="image/*"
                    style={{ display: 'none' }}
                    onChange={handleFileUpload}
                    disabled={uploading}
                  />
                </label>
                <span style={{ fontSize: '0.78rem', color: '#64748b' }}>hoặc dán đường dẫn ảnh</span>
              </div>
              <input
                type="text"
                className="dispute-input"
                placeholder="https://... hoặc đường dẫn hình ảnh/biên lai"
                value={evidenceUrl}
                onChange={(e) => setEvidenceUrl(e.target.value)}
              />
              {evidenceUrl && (
                <div style={{ marginTop: '0.5rem' }}>
                  <img
                    src={evidenceUrl}
                    alt="Xem trước ảnh"
                    style={{ maxHeight: 100, maxWidth: '100%', borderRadius: 6, border: '1px solid #cbd5e1', objectFit: 'cover' }}
                  />
                </div>
              )}
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button
              type="button"
              className="btn-cancel"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="btn-submit"
              disabled={submitting || !isDescValid}
            >
              {submitting ? 'Đang gửi...' : 'Nộp đơn khiếu nại'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};

export default CreateDisputeModal;
