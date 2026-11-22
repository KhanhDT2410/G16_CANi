import React from 'react';
import {
  getDevelopmentLevelInfo,
  getVerificationTierInfo,
} from '../../utils/skillDisplayUtils';

/**
 * WorkerSkillsSection V3 (Web EO):
 * Hiển thị song song nhưng độc lập 2 khái niệm:
 * - Mức xác minh: Tự khai báo / Đã xác thực tài liệu / Đã chứng minh qua ca làm
 * - Mức phát triển: Mới bắt đầu / Có kinh nghiệm / Thành thạo
 * Kèm số ca hoàn thành, đánh giá thật & trạng thái chứng chỉ còn hạn (KHÔNG lộ URL tài liệu riêng tư).
 */
export const WorkerSkillsSection = ({ skills = [] }) => {
  if (!skills || skills.length === 0) {
    return (
      <div
        style={{
          padding: '2rem 1rem',
          textAlign: 'center',
          background: '#f8fafc',
          borderRadius: 10,
          border: '1px dashed #cbd5e1',
          color: '#64748b',
          fontSize: '0.85rem',
        }}
      >
        Nhân sự chưa cập nhật danh sách kỹ năng trên CANi.
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.65rem' }}>
      {skills.map((s) => {
        const verInfo = getVerificationTierInfo(s.verificationTier);
        const devInfo = getDevelopmentLevelInfo(s.skillTier);
        const completed = s.totalShiftsCompleted || 0;
        const ratingCount = s.ratingCount || 0;
        const hasRealRating = ratingCount > 0 && Number(s.avgRating || 0) > 0;

        return (
          <div
            key={s.skillId || s.skillName}
            style={{
              background: '#ffffff',
              border: '1px solid #e2e8f0',
              borderRadius: 10,
              padding: '0.8rem 1rem',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.45rem',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <span style={{ fontWeight: 700, fontSize: '0.92rem', color: '#0f172a' }}>
                  {s.skillName}
                </span>
                {s.category && (
                  <span style={{ fontSize: '0.73rem', color: '#64748b' }}>• {s.category}</span>
                )}
              </div>

              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <span
                  style={{
                    fontSize: '0.73rem',
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 999,
                    background: devInfo.bg,
                    color: devInfo.color,
                  }}
                >
                  {devInfo.label} ({devInfo.tierName})
                </span>
                <span
                  style={{
                    fontSize: '0.73rem',
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 999,
                    background: verInfo.bg,
                    color: verInfo.color,
                    border: `1px solid ${verInfo.border}`,
                  }}
                >
                  {verInfo.label}
                </span>
              </div>
            </div>

            <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', fontSize: '0.78rem', color: '#475569' }}>
              <span>
                Ca hoàn thành: <strong style={{ color: '#0f172a' }}>{completed} ca</strong>
              </span>
              <span>
                Điểm SP: <strong style={{ color: '#0369a1' }}>{s.totalSp || 0} SP</strong>
              </span>
              <span>
                Đánh giá:{' '}
                <strong style={{ color: hasRealRating ? '#b45309' : '#64748b' }}>
                  {hasRealRating ? `${Number(s.avgRating).toFixed(1)} ★ (${ratingCount} lượt)` : 'Chưa có đánh giá'}
                </strong>
              </span>
              {s.hasValidCertificate && (
                <span style={{ color: '#047857', fontWeight: 700 }}>
                  ✓ Chứng chỉ còn hiệu lực
                  {s.certificateExpiresAt ? ` (đến ${String(s.certificateExpiresAt).split('T')[0]})` : ''}
                </span>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};

export default WorkerSkillsSection;
