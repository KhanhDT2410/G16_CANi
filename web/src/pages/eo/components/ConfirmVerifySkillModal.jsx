import React, { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';

/**
 * Modal xác nhận kỹ năng thực hiện chính trước khi Duyệt Công & cấp SP kinh nghiệm (+20 SP/ca).
 * Không yêu cầu Worker nộp lại chứng chỉ, không chặn thanh toán ngay cả khi ca cũ chưa gán skill.
 */
const ConfirmVerifySkillModal = ({
  isOpen,
  onClose,
  assignment,
  shift,
  skillTaxonomy = [],
  onConfirm,
  submitting = false,
}) => {
  const [fetchedTaxonomy, setFetchedTaxonomy] = useState([]);

  useEffect(() => {
    if (isOpen && (!Array.isArray(skillTaxonomy) || skillTaxonomy.length === 0) && fetchedTaxonomy.length === 0) {
      eoService
        .getSkillTaxonomy()
        .then((list) => setFetchedTaxonomy(Array.isArray(list) ? list : []))
        .catch(() => {});
    }
  }, [isOpen, skillTaxonomy, fetchedTaxonomy.length]);

  const resolvedTaxonomy =
    Array.isArray(skillTaxonomy) && skillTaxonomy.length > 0 ? skillTaxonomy : fetchedTaxonomy;

  const activeSkills = (Array.isArray(resolvedTaxonomy) ? resolvedTaxonomy : []).filter(
    (s) => s && s.skillId && s.isActive !== false
  );

  const defaultSkillId =
    assignment?.confirmedPerformedSkillId ||
    assignment?.performedSkillId ||
    shift?.performedSkillId ||
    '';

  const [selectedSkillId, setSelectedSkillId] = useState(defaultSkillId);

  useEffect(() => {
    if (isOpen) {
      setSelectedSkillId(defaultSkillId);
    }
  }, [isOpen, defaultSkillId]);

  if (!isOpen || !assignment) return null;

  const selectedSkill = activeSkills.find((s) => s.skillId === selectedSkillId);

  const handleConfirm = (withSkill = true) => {
    onConfirm({
      assignmentId: assignment.assignmentId,
      confirmedPerformedSkillId: withSkill && selectedSkillId ? selectedSkillId : null,
      confirmPerformedSkill: withSkill && Boolean(selectedSkillId),
    });
  };

  return (
    <div className="eo-modal-overlay" style={{ zIndex: 1300 }}>
      <div className="eo-modal-box" style={{ maxWidth: 480, padding: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.85rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
            Xác Nhận Kỹ Năng &amp; Duyệt Công
          </h3>
          <button
            type="button"
            onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', fontWeight: 600 }}
          >
            Đóng
          </button>
        </div>

        <div style={{ background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: 8, padding: '0.75rem 1rem', marginBottom: '1rem' }}>
          <div style={{ fontWeight: 700, color: '#0f172a', fontSize: '0.92rem' }}>
            {assignment.workerName || 'Nhân sự'}
          </div>
          <div style={{ fontSize: '0.8rem', color: '#475569', marginTop: 2 }}>
            Vị trí ca: <strong>{shift?.jobType || assignment.jobType || 'Ca sự kiện'}</strong>
          </div>
        </div>

        <div className="eo-form-group" style={{ marginBottom: '1rem' }}>
          <label style={{ display: 'block', fontWeight: 600, fontSize: '0.84rem', color: '#1e293b', marginBottom: 6 }}>
            Kỹ năng thực hiện chính để ghi nhận điểm kinh nghiệm (+20 SP/ca)
          </label>
          <select
            className="eo-form-input"
            value={selectedSkillId}
            onChange={(e) => setSelectedSkillId(e.target.value)}
          >
            <option value="">-- Chưa chọn kỹ năng cụ thể (giữ SP chờ xác nhận sau) --</option>
            {activeSkills.map((sk) => (
              <option key={sk.skillId} value={sk.skillId}>
                {sk.name}
              </option>
            ))}
          </select>
          <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: 6, lineHeight: 1.4 }}>
            {selectedSkill
              ? `Sau khi hoàn thành ca hợp lệ, hệ thống sẽ cộng +20 SP kinh nghiệm duy nhất cho kỹ năng "${selectedSkill.name}".`
              : 'Nếu ca cũ chưa chọn kỹ năng chính, việc duyệt công và thanh toán vẫn diễn ra bình thường; điểm SP sẽ ở trạng thái chờ xác nhận.'}
          </div>
        </div>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', flexWrap: 'wrap' }}>
          <button type="button" className="eo-btn-secondary" onClick={onClose} disabled={submitting}>
            Hủy
          </button>
          <button
            type="button"
            className="eo-btn-primary"
            disabled={submitting}
            onClick={() => handleConfirm(Boolean(selectedSkillId))}
            style={{ backgroundColor: '#16a34a' }}
          >
            {submitting
              ? 'Đang duyệt...'
              : selectedSkillId
              ? 'Xác nhận kỹ năng & Duyệt công'
              : 'Duyệt công (Chờ gắn kỹ năng)'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default ConfirmVerifySkillModal;
