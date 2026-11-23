import { useState } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

/**
 * Modal Báo cáo Nhân sự Vắng mặt (No-Show) dành cho Event Organizer.
 * Kích hoạt trừ 25 RS, tự động mở slot và chạy Emergency Dispatch tìm người thay thế.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export default function ReportNoShowModal({ isOpen, onClose, assignment, shift, onSuccess }) {
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);

  if (!isOpen || !assignment) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!assignment?.assignmentId) {
      showToast('Không xác định được mã phân công nhân sự.', 'error');
      return;
    }

    setSubmitting(true);
    try {
      await eoService.reportNoShow(assignment.assignmentId, { notes: notes.trim() });
      showToast('Đã ghi nhận vắng mặt! Hệ thống CANi đang tự động tìm nhân sự thay thế khẩn cấp cho ca trực.', 'success');
      setNotes('');
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      showToast('Không thể báo vắng mặt: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: 520 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: '1.4rem' }}>⚠️</span>
            <h3 style={{ margin: 0, color: '#0f172a', fontSize: '1.15rem', fontWeight: 800 }}>
              Báo Cáo Nhân Sự Vắng Mặt
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          {/* Thông tin nhân sự & ca trực */}
          <div style={{ background: '#f8fafc', padding: '0.85rem 1rem', borderRadius: 10, marginBottom: '1rem', border: '1px solid #e2e8f0', fontSize: '0.85rem' }}>
            <div style={{ marginBottom: 4 }}>
              <strong>Nhân sự:</strong> <span style={{ color: '#0f172a', fontWeight: 700 }}>{assignment.workerName || 'Nhân sự'}</span> {assignment.workerPhone ? `(${assignment.workerPhone})` : ''}
            </div>
            <div>
              <strong>Vị trí ca:</strong> {assignment.jobType || shift?.jobType || 'Ca trực sự kiện'}
            </div>
          </div>

          {/* Cảnh báo chế tài & Cơ chế bù người */}
          <div style={{ background: '#fef2f2', border: '1px solid #fecaca', borderRadius: 10, padding: '0.85rem 1rem', marginBottom: '1rem', fontSize: '0.82rem', color: '#991b1b', lineHeight: 1.5 }}>
            <div style={{ fontWeight: 800, marginBottom: 4, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span>🚨</span> Hệ thống CANi sẽ tự động thực hiện:
            </div>
            <ul style={{ margin: 0, paddingLeft: '1.2rem' }}>
              <li>Đánh dấu vi phạm <strong>No-Show</strong> và trừ <strong>25 điểm uy tín (RS)</strong> của Worker.</li>
              <li>Mở lại <strong>+1 vị trí còn thiếu</strong> trong ca trực ngay lập tức.</li>
              <li>Kích hoạt <strong>Điều phối khẩn cấp (Emergency Dispatch)</strong> để gửi lời mời đến các ứng viên thay thế gần nhất.</li>
            </ul>
          </div>

          <div className="eo-form-group">
            <label style={{ fontSize: '0.85rem', fontWeight: 700, color: '#334155', display: 'block', marginBottom: 6 }}>
              Lý do / Ghi chú vắng mặt (tùy chọn)
            </label>
            <textarea
              rows="3"
              className="eo-form-textarea"
              maxLength={500}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Ví dụ: Nhân sự không có mặt khi ca bắt đầu, gọi điện thoại không nhấc máy..."
              style={{ width: '100%', boxSizing: 'border-box' }}
            />
            <span style={{ fontSize: '0.75rem', color: '#64748b', display: 'block', marginTop: 4 }}>
              Ghi chú giúp bộ phận Chăm sóc khách hàng đối soát nếu Worker có đơn kháng cáo.
            </span>
          </div>

          <div className="eo-modal-footer" style={{ marginTop: '1.25rem', display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
            <button type="button" className="eo-btn-secondary" onClick={onClose} disabled={submitting}>
              Hủy bỏ
            </button>
            <button
              type="submit"
              className="eo-btn-primary"
              style={{ background: '#dc2626', borderColor: '#b91c1c' }}
              disabled={submitting}
            >
              {submitting ? 'Đang ghi nhận...' : 'Xác Nhận Báo Vắng Mặt'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
