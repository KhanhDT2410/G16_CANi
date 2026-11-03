import React, { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

const DISPUTE_TYPES = [
  { value: 'PAYMENT', label: 'Tranh chấp tiền lương / đối soát thanh toán' },
  { value: 'WORK_QUALITY', label: 'Chất lượng công việc không đạt yêu cầu' },
  { value: 'NO_SHOW', label: 'Nhân sự vắng mặt không lý do (No-show)' },
  { value: 'HARASSMENT', label: 'Thái độ / Vi phạm quy tắc ứng xử' },
  { value: 'OTHER', label: 'Lý do khác' },
];

export default function DisputeModal({ isOpen, onClose, assignment, onSuccess }) {
  const [activeTab, setActiveTab] = useState('CREATE'); // 'CREATE' | 'HISTORY'
  const [disputes, setDisputes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    type: 'PAYMENT',
    title: '',
    description: '',
    evidence: '',
  });

  useEffect(() => {
    if (isOpen) {
      if (assignment) {
        setForm({
          type: 'PAYMENT',
          title: `Khiếu nại ca làm việc: ${assignment.jobType || 'Nhân sự'} - ${assignment.workerName || ''}`,
          description: '',
          evidence: assignment.externalRef || '',
        });
      }
      loadMyDisputes();
    }
  }, [isOpen, assignment]);

  const loadMyDisputes = async () => {
    setLoading(true);
    try {
      const data = await eoService.getMyDisputes();
      setDisputes(data || []);
    } catch {
      setDisputes([]);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!assignment?.assignmentId) {
      showToast('Không xác định được mã ca phân công để khiếu nại', 'error');
      return;
    }

    setSubmitting(true);
    try {
      await eoService.submitDispute({
        assignmentId: assignment.assignmentId,
        type: form.type,
        title: form.title,
        description: form.description,
        evidence: form.evidence,
      });

      showToast('Đã gửi khiếu nại thành công! Ban Chăm Sóc Khách Hàng (CC) sẽ xử lý trong 24h.', 'success');
      loadMyDisputes();
      setActiveTab('HISTORY');
      if (onSuccess) onSuccess();
    } catch (err) {
      showToast('Lỗi gửi khiếu nại: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleString('vi-VN');
    } catch {
      return dateStr;
    }
  };

  if (!isOpen) return null;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: 640 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, color: '#0f172a' }}>
            Khiếu Nại & Tranh Chấp 3 Bên (FE-11)
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', fontSize: '0.9rem', cursor: 'pointer', color: '#64748b' }}
          >
            Đóng
          </button>
        </div>

        {/* Tab switcher */}
        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.5rem' }}>
          <button
            type="button"
            onClick={() => setActiveTab('CREATE')}
            style={{
              padding: '6px 14px',
              border: 'none',
              borderRadius: 6,
              background: activeTab === 'CREATE' ? '#fee2e2' : 'transparent',
              color: activeTab === 'CREATE' ? '#b91c1c' : '#64748b',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer',
            }}
          >
            Gửi Đơn Khiếu Nại
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('HISTORY')}
            style={{
              padding: '6px 14px',
              border: 'none',
              borderRadius: 6,
              background: activeTab === 'HISTORY' ? '#eff6ff' : 'transparent',
              color: activeTab === 'HISTORY' ? '#1d4ed8' : '#64748b',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer',
            }}
          >
            Lịch Sử Khiếu Nại ({disputes.length})
          </button>
        </div>

        {activeTab === 'CREATE' ? (
          <form onSubmit={handleSubmit}>
            {assignment && (
              <div style={{ background: '#f8fafc', padding: '0.75rem 1rem', borderRadius: 8, marginBottom: '1rem', border: '1px solid #e2e8f0', fontSize: '0.85rem' }}>
                <div><strong>Mã phân công:</strong> {assignment.assignmentId}</div>
                <div><strong>Nhân sự liên quan:</strong> {assignment.workerName || 'Worker'} • <strong>Vị trí:</strong> {assignment.jobType || 'N/A'}</div>
              </div>
            )}

            <div className="eo-form-group">
              <label>Loại khiếu nại tranh chấp *</label>
              <select
                className="eo-form-select"
                value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value })}
                required
              >
                {DISPUTE_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>

            <div className="eo-form-group">
              <label>Tiêu đề khiếu nại *</label>
              <input
                type="text"
                required
                className="eo-form-input"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="Tóm tắt nội dung khiếu nại..."
              />
            </div>

            <div className="eo-form-group">
              <label>Nội dung chi tiết & Lý do khiếu nại *</label>
              <textarea
                rows="4"
                required
                className="eo-form-textarea"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder="Mô tả cụ thể sự việc, thời gian diễn ra, thiệt hại và yêu cầu mong muốn..."
              />
            </div>

            <div className="eo-form-group">
              <label>Bằng chứng kèm theo (Đường dẫn ảnh/biên lai/video)</label>
              <input
                type="text"
                className="eo-form-input"
                value={form.evidence}
                onChange={(e) => setForm({ ...form, evidence: e.target.value })}
                placeholder="Dán link ảnh bằng chứng, mã giao dịch, ảnh chụp tin nhắn..."
              />
              <span className="eo-field-helper">
                Bằng chứng xác thực giúp Customer Care đưa ra phán quyết công bằng và nhanh chóng hơn.
              </span>
            </div>

            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={onClose}>
                Hủy bỏ
              </button>
              <button
                type="submit"
                className="eo-btn-primary"
                style={{ background: '#dc2626' }}
                disabled={submitting}
              >
                {submitting ? 'Đang gửi...' : 'Gửi Đơn Khiếu Nại'}
              </button>
            </div>
          </form>
        ) : (
          <div style={{ maxHeight: 400, overflowY: 'auto' }}>
            {loading ? (
              <div style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                Đang tải lịch sử khiếu nại...
              </div>
            ) : disputes.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '2.5rem', color: '#64748b' }}>
                Chưa có đơn khiếu nại tranh chấp nào.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                {disputes.map((d) => (
                  <div
                    key={d.disputeId}
                    style={{
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: '1rem',
                      background: '#ffffff',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <strong style={{ fontSize: '0.95rem', color: '#0f172a' }}>{d.title}</strong>
                      <span
                        style={{
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          padding: '2px 8px',
                          borderRadius: 9999,
                          background: d.status === 'RESOLVED' ? '#dcfce7' : d.status === 'SUBMITTED' ? '#fee2e2' : '#fef3c7',
                          color: d.status === 'RESOLVED' ? '#15803d' : d.status === 'SUBMITTED' ? '#b91c1c' : '#b45309',
                        }}
                      >
                        {d.status === 'SUBMITTED'
                          ? 'Đã gửi (Chờ xử lý)'
                          : d.status === 'IN_REVIEW'
                          ? 'Đang điều tra'
                          : d.status === 'RESOLVED'
                          ? 'Đã giải quyết'
                          : d.status}
                      </span>
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b', margin: '4px 0' }}>
                      Phân loại: <strong>{d.type}</strong> • Ngày gửi: {formatDate(d.createdAt)}
                    </div>
                    <p style={{ fontSize: '0.85rem', color: '#334155', margin: '6px 0', lineHeight: 1.4 }}>
                      {d.description}
                    </p>
                    {d.resolution && (
                      <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', padding: '6px 10px', borderRadius: 6, fontSize: '0.8rem', color: '#166534', marginTop: 6 }}>
                        <strong>Phán quyết CC:</strong> {d.resolution}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}

            <div className="eo-modal-footer" style={{ marginTop: '1rem' }}>
              <button type="button" className="eo-btn-secondary" onClick={onClose}>
                Đóng
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

