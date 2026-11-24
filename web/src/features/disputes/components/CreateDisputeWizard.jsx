import { useState, useEffect, useMemo } from 'react';
import { showToast } from '../../../components/common/Toast';
import { eoService } from '../../../services/eoService';
import { disputeApi } from '../api/disputeApi';
import { DISPUTE_TYPES, DISPUTE_TYPE_LABELS } from '../types/dispute.constants';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const ASSIGNMENT_STATUS_MAP = {
  COMPLETED: 'Đã hoàn thành',
  CHECKED_IN: 'Đã điểm danh',
  CONFIRMED: 'Đã xác nhận',
  APPLIED: 'Đã ứng tuyển',
  ASSIGNED: 'Đã phân công',
};

const TYPE_DESCRIPTIONS = {
  [DISPUTE_TYPES.PAYMENT]: 'Thiếu tiền công, chậm thanh toán hoặc sai lệch số tiền thỏa thuận.',
  [DISPUTE_TYPES.WORK_QUALITY]: 'Chất lượng thực hiện nhiệm vụ không đúng thỏa thuận hoặc bàn giao thiếu.',
  [DISPUTE_TYPES.ATTENDANCE]: 'Tranh chấp giờ điểm danh vào/ra ca, đi trễ hoặc vắng mặt.',
  [DISPUTE_TYPES.CONDUCT]: 'Thái độ làm việc, ứng xử thiếu chuyên nghiệp hoặc vi phạm nội quy.',
  [DISPUTE_TYPES.OTHER]: 'Các vấn đề phát sinh khác cần CSKH hỗ trợ làm rõ.',
};

const STEPS = [
  { id: 1, title: 'Chọn ca & Danh mục', short: 'Bước 1' },
  { id: 2, title: 'Nội dung & Minh chứng', short: 'Bước 2' },
  { id: 3, title: 'Xem lại & Nộp đơn', short: 'Bước 3' },
];

/**
 * Form tạo đơn tranh chấp & khiếu nại dạng Step-by-Step nhúng trực tiếp trong trang.
 * Giao diện tối giản, sạch sẽ, không dùng icon emoji.
 */
const CreateDisputeWizard = ({ onCancel, onSubmit }) => {
  const [step, setStep] = useState(1);

  // Step 1 state
  const [assignments, setAssignments] = useState([]);
  const [loadingAssignments, setLoadingAssignments] = useState(true);
  const [shiftSearch, setShiftSearch] = useState('');
  const [manualIdMode, setManualIdMode] = useState(false);
  const [assignmentId, setAssignmentId] = useState('');
  const [type, setType] = useState(DISPUTE_TYPES.PAYMENT);

  // Step 2 state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [evidenceUrl, setEvidenceUrl] = useState('');
  const [uploading, setUploading] = useState(false);

  // Step 3 state
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
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
    };
  }, []);

  const filteredAssignments = useMemo(() => {
    const q = shiftSearch.trim().toLowerCase();
    if (!q) return assignments;
    return assignments.filter((a) => {
      return (
        a.eventName?.toLowerCase().includes(q) ||
        a.jobType?.toLowerCase().includes(q) ||
        a.counterpartyName?.toLowerCase().includes(q) ||
        String(a.assignmentId || '').toLowerCase().includes(q)
      );
    });
  }, [assignments, shiftSearch]);

  const selectedAssignment = useMemo(
    () => assignments.find((a) => String(a.assignmentId) === String(assignmentId).trim()) || null,
    [assignments, assignmentId]
  );

  const descLength = description.trim().length;
  const isDescValid = descLength >= 50 && descLength <= 3000;
  const isStep1Valid = UUID_REGEX.test(assignmentId.trim()) && Boolean(type);
  const isStep2Valid = title.trim().length > 0 && isDescValid;

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

  const handleNext = () => {
    if (step === 1) {
      const trimmedId = assignmentId.trim();
      if (!trimmedId) {
        showToast('Vui lòng chọn ca làm việc hoặc nhập Mã phân công ca làm việc', 'warning');
        return;
      }
      if (!UUID_REGEX.test(trimmedId)) {
        showToast('Mã Ca Làm Việc phải là chuỗi UUID hợp lệ hoặc chọn từ danh sách', 'warning');
        return;
      }
      setStep(2);
      return;
    }

    if (step === 2) {
      if (!title.trim()) {
        showToast('Vui lòng nhập tiêu đề tranh chấp', 'warning');
        return;
      }
      if (!isDescValid) {
        showToast('Mô tả chi tiết tình huống phải từ 50 đến 3000 ký tự', 'warning');
        return;
      }
      setStep(3);
    }
  };

  const handleConfirmSubmit = async () => {
    if (!isStep1Valid || !isStep2Valid) return;
    setSubmitting(true);
    try {
      await onSubmit({
        assignmentId: assignmentId.trim(),
        type,
        title: title.trim(),
        description: description.trim(),
        evidence: evidenceUrl.trim() || undefined,
      });
      onCancel();
    } catch {
      // Toast lỗi đã được hook xử lý
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="dispute-wizard-card animate-fade-in">
      {/* Top Bar */}
      <div className="dispute-wizard-header">
        <div>
          <button type="button" className="dispute-wizard-back" onClick={onCancel}>
            Quay lại danh sách đơn
          </button>
          <h3 className="dispute-wizard-title">Tạo Đơn Tranh Chấp & Khiếu Nại</h3>
          <p className="dispute-wizard-subtitle">
            Thực hiện lần lượt 3 bước để hồ sơ có đủ căn cứ đối soát và được CSKH tiếp nhận nhanh nhất trong vòng 14 ngày sau khi kết thúc ca.
          </p>
        </div>
      </div>

      {/* Stepper (Dùng số thứ tự thuần túy, không dùng icon) */}
      <div className="dispute-stepper">
        {STEPS.map((s, idx) => {
          const isDone = step > s.id;
          const isActive = step === s.id;
          const canJump = s.id < step || (s.id === 2 && isStep1Valid) || (s.id === 3 && isStep1Valid && isStep2Valid);
          return (
            <button
              key={s.id}
              type="button"
              disabled={!canJump}
              onClick={() => canJump && setStep(s.id)}
              className={`dispute-step-item ${isActive ? 'active' : ''} ${isDone ? 'done' : ''}`}
            >
              <span className="dispute-step-circle">{idx + 1}</span>
              <span className="dispute-step-text">
                <span className="dispute-step-short">{s.short}</span>
                <span className="dispute-step-name">{s.title}</span>
              </span>
            </button>
          );
        })}
      </div>

      {/* Step Body */}
      <div className="dispute-wizard-body">
        {step === 1 && (
          <div className="animate-fade-in">
            {/* Mục 1: Ca làm việc */}
            <div style={{ marginBottom: '1.75rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '0.65rem' }}>
                <label className="dispute-field-label" style={{ margin: 0 }}>
                  1. Chọn ca làm việc cần khiếu nại *
                </label>
                {assignments.length > 0 && (
                  <button
                    type="button"
                    onClick={() => setManualIdMode((v) => !v)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#2563eb',
                      fontSize: '0.82rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                      padding: 0,
                      textDecoration: 'underline',
                    }}
                  >
                    {manualIdMode ? 'Chọn từ danh sách ca làm việc' : 'Nhập mã phân công thủ công'}
                  </button>
                )}
              </div>

              {loadingAssignments ? (
                <div className="dispute-inline-info">
                  <span className="cani-spinner" style={{ display: 'inline-block', width: 14, height: 14, verticalAlign: 'middle', marginRight: 8 }} />
                  Đang tải danh sách ca làm việc của bạn từ hệ thống...
                </div>
              ) : !manualIdMode && assignments.length > 0 ? (
                <>
                  {assignments.length > 3 && (
                    <div style={{ position: 'relative', marginBottom: '0.75rem' }}>
                      <input
                        type="text"
                        className="dispute-wizard-input"
                        placeholder="Tìm nhanh theo tên sự kiện, vị trí hoặc tên đối tác..."
                        value={shiftSearch}
                        onChange={(e) => setShiftSearch(e.target.value)}
                      />
                    </div>
                  )}
                  <div className="dispute-assignment-grid">
                    {filteredAssignments.map((a) => {
                      const isSelected = String(a.assignmentId) === String(assignmentId);
                      const timeStr = a.startTime
                        ? new Date(a.startTime).toLocaleString('vi-VN', {
                          day: '2-digit',
                          month: '2-digit',
                          year: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })
                        : '';
                      const isComp = a.status === 'COMPLETED';
                      const isCheck = a.status === 'CHECKED_IN';
                      return (
                        <button
                          key={a.assignmentId}
                          type="button"
                          onClick={() => setAssignmentId(String(a.assignmentId))}
                          className={`dispute-assignment-card ${isSelected ? 'selected' : ''}`}
                        >
                          <div className="dispute-assignment-top">
                            <span className="dispute-assignment-event" title={a.eventName}>
                              {a.eventName || 'Sự kiện không tên'}
                            </span>
                            <span className={`dispute-assignment-status ${isComp ? 'completed' : isCheck ? 'checked-in' : ''}`}>
                              {ASSIGNMENT_STATUS_MAP[a.status] || a.status}
                            </span>
                          </div>
                          <div className="dispute-assignment-meta">
                            <span>Vị trí: <strong>{a.jobType || 'Nhân sự'}</strong></span>
                          </div>
                          <div style={{ marginTop: '0.45rem', padding: '0.35rem 0.6rem', background: isSelected ? '#eff6ff' : '#f8fafc', borderRadius: '6px', border: '1px solid ' + (isSelected ? '#bfdbfe' : '#e2e8f0'), display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <span style={{ fontSize: '0.8rem', fontWeight: 600, color: '#475569' }}>Nhân sự bị khiếu nại:</span>
                            <span style={{ fontSize: '0.82rem', fontWeight: 800, color: '#0f172a' }}>{a.counterpartyName || 'Chưa cập nhật'} {a.counterpartyPhone ? `(${a.counterpartyPhone})` : ''}</span>
                          </div>
                          {timeStr && <div className="dispute-assignment-time">Thời gian ca: {timeStr}</div>}
                        </button>
                      );
                    })}
                    {filteredAssignments.length === 0 && (
                      <div className="dispute-inline-info" style={{ gridColumn: '1 / -1' }}>
                        Không có ca làm việc nào khớp với từ khóa "{shiftSearch}".
                      </div>
                    )}
                  </div>
                  {selectedAssignment && (
                    <div style={{ marginTop: '0.85rem', padding: '0.75rem 1rem', background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '8px', fontSize: '0.85rem', color: '#166534' }}>
                      <strong>Xác nhận đối tượng khiếu nại:</strong> Bạn đang chọn mở hồ sơ tranh chấp liên quan đến nhân sự <strong>{selectedAssignment.counterpartyName}</strong> ({selectedAssignment.jobType || 'Nhân sự'}) tại sự kiện <em>{selectedAssignment.eventName}</em>.
                    </div>
                  )}
                </>
              ) : (
                <div>
                  <input
                    type="text"
                    className="dispute-wizard-input"
                    placeholder="Dán mã phân công ca làm việc, ví dụ: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d"
                    value={assignmentId}
                    onChange={(e) => setAssignmentId(e.target.value)}
                  />
                  <div className="dispute-inline-info" style={{ marginTop: '0.65rem' }}>
                    <strong>Lưu ý:</strong> Bạn có thể sao chép mã phân công từ trang Quản lý Ca làm việc hoặc Danh sách thanh toán để dán vào đây.
                  </div>
                </div>
              )}
            </div>

            {/* Mục 2: Danh mục tranh chấp (Không dùng icon) */}
            <div>
              <label className="dispute-field-label">
                2. Chọn phân loại vấn đề tranh chấp *
              </label>
              <div className="dispute-type-grid">
                {Object.values(DISPUTE_TYPES).map((val) => {
                  const isSelected = type === val;
                  return (
                    <button
                      key={val}
                      type="button"
                      onClick={() => setType(val)}
                      className={`dispute-type-card ${isSelected ? 'selected' : ''}`}
                    >
                      <div className="dispute-type-card-title">
                        {DISPUTE_TYPE_LABELS[val] || val}
                      </div>
                      <div className="dispute-type-card-desc">{TYPE_DESCRIPTIONS[val] || ''}</div>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {step === 2 && (
          <div className="animate-fade-in">
            {/* Tiêu đề */}
            <div style={{ marginBottom: '1.25rem' }}>
              <label className="dispute-field-label">Tiêu đề tranh chấp *</label>
              <input
                type="text"
                className="dispute-wizard-input"
                maxLength={200}
                placeholder="Ví dụ: Sai lệch tiền công ca tối 20/11 hoặc Bàn giao thiếu thiết bị âm thanh..."
                value={title}
                onChange={(e) => setTitle(e.target.value)}
              />
            </div>

            {/* Mô tả */}
            <div style={{ marginBottom: '1.25rem' }}>
              <label className="dispute-field-label">
                Mô tả chi tiết tình huống (Tối thiểu 50 ký tự) *
              </label>
              <textarea
                className="dispute-wizard-textarea"
                rows={5}
                placeholder="Trình bày rõ diễn biến sự việc, thời gian xảy ra, số tiền chênh lệch hoặc cam kết cụ thể bị vi phạm để trọng tài viên CSKH có đầy đủ căn cứ xác minh..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
              <div className={`dispute-char-counter ${isDescValid ? 'valid' : 'invalid'}`}>
                {descLength} / 50 ký tự tối thiểu {isDescValid ? '— Đạt yêu cầu' : '— Cần thêm ký tự'}
              </div>
            </div>

            {/* Minh chứng */}
            <div>
              <label className="dispute-field-label">
                Ảnh / Biên lai / Minh chứng ban đầu (Tùy chọn)
              </label>
              <div style={{ display: 'flex', gap: '0.85rem', alignItems: 'center', flexWrap: 'wrap', marginBottom: '0.75rem' }}>
                <label className="dispute-wizard-btn-upload">
                  <span>{uploading ? 'Đang tải ảnh...' : 'Chọn ảnh từ máy tính'}</span>
                  <input
                    type="file"
                    accept="image/*"
                    style={{ display: 'none' }}
                    onChange={handleFileUpload}
                    disabled={uploading}
                  />
                </label>
                <span style={{ fontSize: '0.82rem', color: '#64748b' }}>hoặc dán đường link ảnh biên lai ngân hàng, ảnh chấm công bên dưới</span>
              </div>
              <input
                type="text"
                className="dispute-wizard-input"
                placeholder="https://... hoặc /api/v1/..."
                value={evidenceUrl}
                onChange={(e) => setEvidenceUrl(e.target.value)}
              />
              {evidenceUrl && (
                <div style={{ marginTop: '0.85rem', display: 'flex', alignItems: 'flex-start', gap: '1rem', padding: '0.75rem', background: '#f8fafc', borderRadius: 10, border: '1px solid #e2e8f0' }}>
                  <img
                    src={evidenceUrl}
                    alt="Xem trước minh chứng"
                    style={{ maxHeight: 150, maxWidth: 260, borderRadius: 8, border: '1px solid #cbd5e1', objectFit: 'cover' }}
                  />
                  <div>
                    <div style={{ fontSize: '0.82rem', fontWeight: 700, color: '#0f172a', marginBottom: 4 }}>
                      Ảnh minh chứng đã chọn
                    </div>
                    <button
                      type="button"
                      style={{
                        background: '#fef2f2',
                        border: '1px solid #fecaca',
                        color: '#dc2626',
                        fontSize: '0.8rem',
                        fontWeight: 700,
                        padding: '0.35rem 0.75rem',
                        borderRadius: 6,
                        cursor: 'pointer',
                      }}
                      onClick={() => setEvidenceUrl('')}
                    >
                      Xóa ảnh này
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {step === 3 && (
          <div className="animate-fade-in">
            <div className="dispute-review-card">
              <h4 className="dispute-review-heading">
                Xác nhận hồ sơ khiếu nại trước khi nộp
              </h4>
              <div className="dispute-review-grid">
                <div className="dispute-review-item">
                  <span className="dispute-review-label">Ca làm việc áp dụng</span>
                  <span className="dispute-review-val">
                    {selectedAssignment
                      ? `${selectedAssignment.eventName} · ${selectedAssignment.jobType}`
                      : assignmentId}
                  </span>
                </div>
                {selectedAssignment?.counterpartyName && (
                  <div className="dispute-review-item">
                    <span className="dispute-review-label">Đối tác bị khiếu nại</span>
                    <span className="dispute-review-val" style={{ fontWeight: 800, color: '#b91c1c' }}>
                      {selectedAssignment.counterpartyName} {selectedAssignment.counterpartyPhone ? `(${selectedAssignment.counterpartyPhone})` : ''}
                    </span>
                  </div>
                )}
                <div className="dispute-review-item">
                  <span className="dispute-review-label">Phân loại tranh chấp</span>
                  <span className="dispute-review-val">
                    {DISPUTE_TYPE_LABELS[type] || type}
                  </span>
                </div>
                <div className="dispute-review-item">
                  <span className="dispute-review-label">Tiêu đề hồ sơ</span>
                  <span className="dispute-review-val">{title.trim()}</span>
                </div>
                <div className="dispute-review-item">
                  <span className="dispute-review-label">Mô tả chi tiết nội dung</span>
                  <span className="dispute-review-val" style={{ whiteSpace: 'pre-wrap' }}>
                    {description.trim()}
                  </span>
                </div>
                <div className="dispute-review-item">
                  <span className="dispute-review-label">Minh chứng đính kèm</span>
                  <span className="dispute-review-val">
                    {evidenceUrl ? (
                      <img
                        src={evidenceUrl}
                        alt="Minh chứng"
                        style={{ maxHeight: 150, borderRadius: 8, border: '1px solid #cbd5e1', marginTop: 6 }}
                      />
                    ) : (
                      <span style={{ color: '#64748b', fontStyle: 'italic', fontWeight: 500 }}>
                        Không có ảnh minh chứng ban đầu (bạn có thể bổ sung sau khi đơn được mở).
                      </span>
                    )}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Footer Actions */}
      <div className="dispute-wizard-footer">
        <button
          type="button"
          className="dispute-wizard-btn-cancel"
          onClick={step === 1 ? onCancel : () => setStep((s) => s - 1)}
          disabled={submitting}
        >
          {step === 1 ? 'Hủy bỏ' : 'Quay lại bước trước'}
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          {step === 1 && !isStep1Valid && (
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              Vui lòng chọn 1 ca làm việc để tiếp tục
            </span>
          )}
          {step === 2 && !isStep2Valid && (
            <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
              {!title.trim() ? 'Cần nhập tiêu đề' : 'Mô tả cần tối thiểu 50 ký tự'}
            </span>
          )}

          {step < 3 ? (
            <button
              type="button"
              className="dispute-wizard-btn-submit"
              onClick={handleNext}
              disabled={(step === 1 && !isStep1Valid) || (step === 2 && !isStep2Valid)}
            >
              Tiếp tục
            </button>
          ) : (
            <button
              type="button"
              className="dispute-wizard-btn-submit"
              onClick={handleConfirmSubmit}
              disabled={submitting}
            >
              {submitting ? 'Đang nộp hồ sơ...' : 'Xác nhận nộp đơn khiếu nại'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default CreateDisputeWizard;
