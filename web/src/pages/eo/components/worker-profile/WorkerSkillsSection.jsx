import React from 'react';

/**
 * WorkerSkillsSection: Hiển thị ma trận kỹ năng đa tầng (3 Tiers) của Worker
 * Phân định rõ ràng: Kỹ năng có tích xanh AI (Tier 2) và Kỹ năng tự khai báo (Tier 1)
 * Tiêu chuẩn < 200 dòng theo AGENTS.md
 */
export const WorkerSkillsSection = ({ skills = [] }) => {
  if (!skills || skills.length === 0) {
    return (
      <div style={{
        padding: '2rem 1rem',
        textAlign: 'center',
        background: '#f8fafc',
        borderRadius: 10,
        border: '1px dashed #cbd5e1',
        color: '#64748b',
        fontSize: '0.85rem',
      }}>
        Nhân sự chưa cập nhật danh sách kỹ năng trên sàn CANi.
      </div>
    );
  }

  const aiVerifiedSkills = skills.filter(
    (s) => s.verificationTier === 'AI_DOCUMENT' || s.verificationTier === 'PLATFORM_PROVEN'
  );
  const selfDeclaredSkills = skills.filter(
    (s) => !s.verificationTier || s.verificationTier === 'SELF_DECLARED'
  );

  return (
    <div className="skills-container">
      {/* 1. Nhóm Kỹ năng đã thẩm định Tích xanh AI (Tier 2) */}
      {aiVerifiedSkills.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.85rem', fontWeight: 700, color: '#065f46' }}>
            <span>🛡️ Kỹ Năng Đã Thẩm Định Chứng Chỉ (Gemini Vision AI Verified):</span>
          </div>
          <div className="skills-chip-list">
            {aiVerifiedSkills.map((s) => (
              <span key={s.skillId || s.skillName} className="skill-chip ai-verified">
                <span className="ai-badge-icon">✓</span>
                <span>{s.skillName}</span>
                {s.category && (
                  <small style={{ opacity: 0.75, fontWeight: 500, fontSize: '0.7rem' }}>• {s.category}</small>
                )}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* 2. Nhóm Kỹ năng Tự khai báo (Tier 1) */}
      {selfDeclaredSkills.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem', marginTop: aiVerifiedSkills.length > 0 ? '0.5rem' : 0 }}>
          <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#475569' }}>
            Kỹ Năng Tự Khai Báo (Tier 1):
          </div>
          <div className="skills-chip-list">
            {selfDeclaredSkills.map((s) => (
              <span key={s.skillId || s.skillName} className="skill-chip">
                <span>{s.skillName}</span>
                {s.category && (
                  <small style={{ color: '#64748b', fontWeight: 500, fontSize: '0.7rem' }}>• {s.category}</small>
                )}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default WorkerSkillsSection;
