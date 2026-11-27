import React from 'react';

const RELAXATION_OPTIONS = [
  {
    value: 'NONE',
    label: 'Giữ nguyên tiêu chuẩn',
    desc: 'Chỉ mở rộng bán kính tìm kiếm & bỏ lịch rảnh tuần cố định.',
  },
  {
    value: 'LOWER_TIER',
    label: 'Hạ 1 bậc xác minh kỹ năng',
    desc: 'Vẫn yêu cầu đúng kỹ năng, nhưng cho phép hồ sơ xác minh thấp hơn 1 bậc (trừ chứng chỉ an toàn).',
  },
  {
    value: 'PREFER_ONLY',
    label: 'Ưu tiên kỹ năng (ca phổ thông)',
    desc: 'Không chặn ứng viên chưa gắn kỹ năng; vẫn ưu tiên xếp trên người có kỹ năng và giữ nguyên chứng chỉ.',
  },
];

const BONUS_PRESETS = [0, 30000, 50000, 100000];

export default function EmergencyAdvancedOptions({
  skillRelaxation,
  onChangeSkillRelaxation,
  preferOnlyAllowed = true,
  emergencyBonus,
  onChangeEmergencyBonus,
  minReliabilityScore,
  onChangeMinReliabilityScore,
  baseMinRs = 60,
  disabled = false,
}) {
  const effectiveBaseRs = Math.max(50, Number(baseMinRs || 60));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
      <div>
        <label style={{ display: 'block', fontSize: '0.84rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.4rem' }}>
          Chính sách nới lỏng kỹ năng có kiểm soát:
        </label>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
          {RELAXATION_OPTIONS.map((opt) => {
            const isOptionDisabled = disabled || (opt.value === 'PREFER_ONLY' && !preferOnlyAllowed);
            const selected = skillRelaxation === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                disabled={isOptionDisabled}
                onClick={() => onChangeSkillRelaxation(opt.value)}
                style={{
                  textAlign: 'left',
                  padding: '0.5rem 0.75rem',
                  borderRadius: 8,
                  border: selected ? '2px solid #dc2626' : '1.5px solid #e2e8f0',
                  background: isOptionDisabled ? '#f8fafc' : selected ? '#fff1f2' : '#ffffff',
                  color: isOptionDisabled ? '#94a3b8' : selected ? '#9f1239' : '#334155',
                  cursor: isOptionDisabled ? 'not-allowed' : 'pointer',
                }}
              >
                <div style={{ fontSize: '0.81rem', fontWeight: 700 }}>
                  {opt.label}
                  {opt.value === 'PREFER_ONLY' && !preferOnlyAllowed ? ' (Không áp dụng cho vị trí kỹ thuật/chuyên môn)' : ''}
                </div>
                <div style={{ fontSize: '0.73rem', color: isOptionDisabled ? '#94a3b8' : '#64748b', marginTop: '0.1rem' }}>
                  {opt.desc}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
        <div>
          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.35rem' }}>
            Phụ cấp khẩn cấp / suất (đ):
          </label>
          <input
            type="number"
            min="0"
            step="10000"
            disabled={disabled}
            value={emergencyBonus}
            onChange={(e) => onChangeEmergencyBonus(e.target.value)}
            placeholder="0"
            style={{
              width: '100%',
              padding: '0.45rem 0.65rem',
              borderRadius: 8,
              border: '1.5px solid #cbd5e1',
              fontSize: '0.84rem',
              boxSizing: 'border-box',
            }}
          />
          <div style={{ display: 'flex', gap: '0.3rem', flexWrap: 'wrap', marginTop: '0.3rem' }}>
            {BONUS_PRESETS.map((amt) => (
              <button
                key={amt}
                type="button"
                disabled={disabled}
                onClick={() => onChangeEmergencyBonus(amt)}
                style={{
                  padding: '2px 7px',
                  fontSize: '0.71rem',
                  fontWeight: 600,
                  borderRadius: 6,
                  border: Number(emergencyBonus) === amt ? '1px solid #dc2626' : '1px solid #cbd5e1',
                  background: Number(emergencyBonus) === amt ? '#fee2e2' : '#f8fafc',
                  color: Number(emergencyBonus) === amt ? '#b91c1c' : '#475569',
                  cursor: disabled ? 'not-allowed' : 'pointer',
                }}
              >
                {amt === 0 ? '0đ' : `+${amt / 1000}k`}
              </button>
            ))}
          </div>
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 700, color: '#1e293b', marginBottom: '0.35rem' }}>
            Sàn uy tín (RS) đợt khẩn cấp:
          </label>
          <select
            disabled={disabled}
            value={minReliabilityScore ?? ''}
            onChange={(e) => onChangeMinReliabilityScore(e.target.value ? Number(e.target.value) : null)}
            style={{
              width: '100%',
              padding: '0.45rem 0.65rem',
              borderRadius: 8,
              border: '1.5px solid #cbd5e1',
              fontSize: '0.84rem',
              background: '#ffffff',
              boxSizing: 'border-box',
            }}
          >
            <option value="">Giữ mặc định ca ({effectiveBaseRs} điểm)</option>
            {effectiveBaseRs > 60 && <option value={60}>Hạ xuống 60 điểm</option>}
            {effectiveBaseRs > 50 && <option value={50}>Hạ xuống tối thiểu 50 điểm</option>}
          </select>
        </div>
      </div>
    </div>
  );
}
