import React, { useState, useEffect, useMemo } from 'react';
import { eoService } from '../../../services/eoService';
import {
  VERIFICATION_TIER_OPTIONS,
  normalizeVietnameseText,
  suggestSkillsForQuery,
} from '../utils/skillDisplayUtils';

/**
 * ShiftSkillSelector V3:
 * 1. Chọn kỹ năng thực hiện chính (nhận +20 SP kinh nghiệm/ca).
 * 2. Chọn kỹ năng Bắt buộc / Ưu tiên từ danh mục chuẩn, kèm mức xác minh & chứng chỉ còn hạn.
 * 3. Gợi ý chuẩn hóa/alias khi nhập tự do, không tự ý tạo skill active tràn lan vào taxonomy chính.
 */
const ShiftSkillSelector = ({
  jobType = '',
  selectedSkillIds = [],
  onChange,
  performedSkillId = null,
  onPerformedSkillChange,
  skillRequirements = [],
  onSkillRequirementsChange,
  customSkillNotes = '',
  onCustomSkillNotesChange,
  taxonomy = [],
  onSkillCreated,
}) => {
  const [searchVal, setSearchVal] = useState('');
  const [proposalSubmitting, setProposalSubmitting] = useState(false);
  const [localTaxonomy, setLocalTaxonomy] = useState(taxonomy || []);
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    if (Array.isArray(taxonomy) && taxonomy.length > 0) {
      setLocalTaxonomy((prev) => {
        const existingIds = new Set(prev.map((s) => s.skillId));
        const merged = [...prev];
        taxonomy.forEach((item) => {
          if (item && item.skillId && !existingIds.has(item.skillId)) {
            merged.push(item);
          }
        });
        return merged;
      });
    }
  }, [taxonomy]);

  const activeTaxonomy = useMemo(
    () => (Array.isArray(localTaxonomy) ? localTaxonomy.filter((s) => s && s.skillId && s.isActive !== false) : []),
    [localTaxonomy]
  );

  const skillMap = useMemo(() => {
    const m = new Map();
    activeTaxonomy.forEach((sk) => m.set(sk.skillId, sk));
    (Array.isArray(localTaxonomy) ? localTaxonomy : []).forEach((sk) => {
      if (sk?.skillId && !m.has(sk.skillId)) m.set(sk.skillId, sk);
    });
    return m;
  }, [activeTaxonomy, localTaxonomy]);

  // Chuẩn hóa danh sách requirement thống nhất từ skillRequirements hoặc selectedSkillIds fallback
  const normalizedRequirements = useMemo(() => {
    if (Array.isArray(skillRequirements) && skillRequirements.length > 0) {
      const seen = new Set();
      return skillRequirements
        .filter((r) => r && r.skillId && !seen.has(r.skillId) && seen.add(r.skillId))
        .map((r) => ({
          skillId: r.skillId,
          skillName: r.skillName || skillMap.get(r.skillId)?.name || '',
          requirementType: r.requirementType === 'PREFERRED' ? 'PREFERRED' : 'REQUIRED',
          minVerificationTier: r.minVerificationTier || 'SELF_DECLARED',
          requiresValidCertificate: Boolean(r.requiresValidCertificate),
        }));
    }
    const ids = Array.isArray(selectedSkillIds) ? selectedSkillIds : [];
    return [...new Set(ids.filter(Boolean))].map((id) => ({
      skillId: id,
      skillName: skillMap.get(id)?.name || '',
      requirementType: 'REQUIRED',
      minVerificationTier: 'SELF_DECLARED',
      requiresValidCertificate: false,
    }));
  }, [skillRequirements, selectedSkillIds, skillMap]);

  const emitRequirements = (nextReqs) => {
    const requiredOnlyIds = nextReqs
      .filter((r) => r.requirementType !== 'PREFERRED')
      .map((r) => r.skillId);
    if (typeof onSkillRequirementsChange === 'function') {
      onSkillRequirementsChange(nextReqs);
    }
    if (typeof onChange === 'function') {
      onChange(requiredOnlyIds);
    }
  };

  // Gợi ý kỹ năng theo jobType
  const jobSuggestedSkills = useMemo(
    () => suggestSkillsForQuery(jobType, activeTaxonomy),
    [jobType, activeTaxonomy]
  );

  // Tự động gợi ý kỹ năng thực hiện chính nếu chưa chọn và khớp jobType duy nhất
  useEffect(() => {
    if (!performedSkillId && typeof onPerformedSkillChange === 'function' && jobSuggestedSkills.length === 1) {
      onPerformedSkillChange(jobSuggestedSkills[0].skillId);
    }
  }, [jobType, performedSkillId, jobSuggestedSkills, onPerformedSkillChange]);

  const selectedReqIds = new Set(normalizedRequirements.map((r) => r.skillId));

  const filteredTaxonomy = useMemo(() => {
    const q = searchVal.trim();
    if (!q) {
      return activeTaxonomy.filter((sk) => !selectedReqIds.has(sk.skillId));
    }
    const qNorm = normalizeVietnameseText(q);
    const aliasMatched = suggestSkillsForQuery(q, activeTaxonomy);
    const directMatched = activeTaxonomy.filter((sk) =>
      normalizeVietnameseText(sk.name).includes(qNorm)
    );
    const combined = [...directMatched];
    aliasMatched.forEach((a) => {
      if (!combined.some((c) => c.skillId === a.skillId)) combined.push(a);
    });
    return combined.filter((sk) => !selectedReqIds.has(sk.skillId));
  }, [searchVal, activeTaxonomy, normalizedRequirements]);

  const addRequirement = (skill, requirementType = 'REQUIRED') => {
    if (!skill?.skillId) return;
    setErrorMsg('');
    if (selectedReqIds.has(skill.skillId)) return;

    const nextReqs = [
      ...normalizedRequirements,
      {
        skillId: skill.skillId,
        skillName: skill.name,
        requirementType,
        minVerificationTier: 'SELF_DECLARED',
        requiresValidCertificate: false,
      },
    ];
    emitRequirements(nextReqs);

    if (requirementType === 'REQUIRED' && !performedSkillId && typeof onPerformedSkillChange === 'function') {
      onPerformedSkillChange(skill.skillId);
    }
    setSearchVal('');
  };

  const updateRequirement = (skillId, patch) => {
    const nextReqs = normalizedRequirements.map((r) =>
      r.skillId === skillId ? { ...r, ...patch } : r
    );
    emitRequirements(nextReqs);
  };

  const removeRequirement = (skillId) => {
    const nextReqs = normalizedRequirements.filter((r) => r.skillId !== skillId);
    emitRequirements(nextReqs);
  };

  const handleSaveCustomNoteOrPropose = async () => {
    const trimmed = searchVal.trim();
    if (!trimmed) return;
    setErrorMsg('');

    const matched = suggestSkillsForQuery(trimmed, activeTaxonomy);
    if (matched.length > 0 && normalizeVietnameseText(matched[0].name) === normalizeVietnameseText(trimmed)) {
      addRequirement(matched[0], 'REQUIRED');
      return;
    }

    // Lưu vào custom_skill_notes trên Shift + gửi đề xuất is_active=false thay vì làm ô nhiễm taxonomy
    const nextNotes = customSkillNotes
      ? (customSkillNotes.includes(trimmed) ? customSkillNotes : `${customSkillNotes}, ${trimmed}`)
      : trimmed;
    if (typeof onCustomSkillNotesChange === 'function') {
      onCustomSkillNotesChange(nextNotes);
    }

    setProposalSubmitting(true);
    try {
      const res = await eoService.createCustomSkill(trimmed, 'Đề xuất');
      if (res && res.skillId && res.isActive !== false) {
        setLocalTaxonomy((prev) => [...prev, res]);
        if (typeof onSkillCreated === 'function') onSkillCreated(res);
        addRequirement(res, 'REQUIRED');
      }
      setSearchVal('');
    } catch (err) {
      setErrorMsg(err.response?.data?.message || 'Đã ghi nhận vào ghi chú kỹ năng bổ sung.');
    } finally {
      setProposalSubmitting(false);
    }
  };

  const requiredCount = normalizedRequirements.filter((r) => r.requirementType === 'REQUIRED').length;
  const preferredCount = normalizedRequirements.filter((r) => r.requirementType === 'PREFERRED').length;
  const certCount = normalizedRequirements.filter((r) => r.requiresValidCertificate).length;
  const performedSkillObj = performedSkillId ? skillMap.get(performedSkillId) : null;

  return (
    <div
      className="eo-form-group"
      style={{
        marginBottom: '1.25rem',
        padding: '0.9rem 1rem',
        background: '#f8fafc',
        border: '1px solid #e2e8f0',
        borderRadius: '0.75rem',
      }}
    >
      <div style={{ fontWeight: 700, fontSize: '0.9rem', color: '#0f172a', marginBottom: '0.75rem' }}>
        Yêu Cầu Kỹ Năng &amp; Nghiệp Vụ Cho Ca Làm
      </div>

      {/* 1. KỸ NĂNG THỰC HIỆN CHÍNH */}
      {typeof onPerformedSkillChange === 'function' && (
        <div style={{ marginBottom: '0.9rem' }}>
          <label style={{ display: 'block', fontWeight: 600, fontSize: '0.82rem', color: '#1e293b', marginBottom: '0.3rem' }}>
            Kỹ năng thực hiện chính (cộng +20 SP kinh nghiệm khi hoàn thành)
          </label>
          <select
            className="eo-form-input"
            value={performedSkillId || ''}
            onChange={(e) => onPerformedSkillChange(e.target.value || null)}
            style={{ backgroundColor: '#ffffff', fontSize: '0.83rem' }}
          >
            <option value="">-- Chọn 1 kỹ năng thực hiện chính --</option>
            {activeTaxonomy.map((sk) => {
              const isSuggested = jobSuggestedSkills.some((js) => js.skillId === sk.skillId);
              return (
                <option key={sk.skillId} value={sk.skillId}>
                  {sk.name}{isSuggested ? ' (Gợi ý theo vị trí)' : ''}
                </option>
              );
            })}
          </select>
        </div>
      )}

      {/* 2. DANH SÁCH YÊU CẦU ĐÃ CHỌN */}
      {normalizedRequirements.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '0.85rem' }}>
          {normalizedRequirements.map((req) => {
            const sk = skillMap.get(req.skillId);
            const name = req.skillName || sk?.name || `Kỹ năng (${req.skillId.slice(0, 6)})`;
            const isRequired = req.requirementType !== 'PREFERRED';
            return (
              <div
                key={req.skillId}
                style={{
                  background: '#ffffff',
                  border: `1.2px solid ${isRequired ? '#93c5fd' : '#bbf7d0'}`,
                  borderRadius: '0.6rem',
                  padding: '0.55rem 0.75rem',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.45rem',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                    <span style={{ fontWeight: 700, fontSize: '0.84rem', color: '#0f172a' }}>{name}</span>
                    <button
                      type="button"
                      onClick={() =>
                        updateRequirement(req.skillId, {
                          requirementType: isRequired ? 'PREFERRED' : 'REQUIRED',
                        })
                      }
                      style={{
                        border: 'none',
                        borderRadius: '999px',
                        padding: '2px 8px',
                        fontSize: '0.72rem',
                        fontWeight: 700,
                        cursor: 'pointer',
                        backgroundColor: isRequired ? '#dbeafe' : '#dcfce7',
                        color: isRequired ? '#1d4ed8' : '#15803d',
                      }}
                      title="Nhấn để chuyển đổi giữa Bắt buộc và Ưu tiên thêm"
                    >
                      {isRequired ? 'Bắt buộc' : 'Ưu tiên thêm'}
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => removeRequirement(req.skillId)}
                    style={{
                      border: 'none',
                      background: '#fee2e2',
                      color: '#b91c1c',
                      borderRadius: '0.35rem',
                      padding: '2px 7px',
                      fontSize: '0.72rem',
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    Xóa
                  </button>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem', flexWrap: 'wrap' }}>
                  <select
                    value={req.minVerificationTier}
                    onChange={(e) =>
                      updateRequirement(req.skillId, { minVerificationTier: e.target.value })
                    }
                    style={{
                      fontSize: '0.76rem',
                      padding: '0.25rem 0.45rem',
                      borderRadius: '0.4rem',
                      border: '1px solid #cbd5e1',
                      backgroundColor: '#f8fafc',
                    }}
                  >
                    {VERIFICATION_TIER_OPTIONS.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        Mức xác minh: {opt.label}
                      </option>
                    ))}
                  </select>

                  <label style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: '0.76rem', color: '#334155', margin: 0, cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={Boolean(req.requiresValidCertificate)}
                      onChange={(e) =>
                        updateRequirement(req.skillId, {
                          requiresValidCertificate: e.target.checked,
                          minVerificationTier:
                            e.target.checked && req.minVerificationTier === 'SELF_DECLARED'
                              ? 'AI_DOCUMENT'
                              : req.minVerificationTier,
                        })
                      }
                    />
                    Yêu cầu chứng chỉ còn hiệu lực
                  </label>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div style={{ fontSize: '0.78rem', color: '#64748b', fontStyle: 'italic', marginBottom: '0.65rem' }}>
          Chưa chọn yêu cầu kỹ năng cụ thể (ca phổ thông).
        </div>
      )}

      {/* 3. GỢI Ý NHANH THEO VỊ TRÍ & TÌM KIẾM TRONG TAXONOMY */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
        <input
          type="text"
          value={searchVal}
          onChange={(e) => setSearchVal(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              if (filteredTaxonomy.length > 0) {
                addRequirement(filteredTaxonomy[0], 'REQUIRED');
              } else {
                handleSaveCustomNoteOrPropose();
              }
            }
          }}
          placeholder="Tìm trong danh mục chuẩn (vd: phục vụ, bartender, thu ngân, an ninh)..."
          style={{
            flex: 1,
            padding: '0.45rem 0.7rem',
            fontSize: '0.8rem',
            border: '1px solid #cbd5e1',
            borderRadius: '0.45rem',
            backgroundColor: '#ffffff',
          }}
        />
        {searchVal.trim() && filteredTaxonomy.length === 0 && (
          <button
            type="button"
            disabled={proposalSubmitting}
            onClick={handleSaveCustomNoteOrPropose}
            style={{
              padding: '0.4rem 0.75rem',
              fontSize: '0.76rem',
              fontWeight: 600,
              color: '#0369a1',
              background: '#e0f2fe',
              border: '1px solid #bae6fd',
              borderRadius: '0.45rem',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            {proposalSubmitting ? 'Đang ghi...' : 'Thêm ghi chú ngoài danh mục'}
          </button>
        )}
      </div>

      {errorMsg && (
        <div style={{ fontSize: '0.75rem', color: '#b45309', marginBottom: '0.4rem' }}>{errorMsg}</div>
      )}

      {filteredTaxonomy.length > 0 && (
        <div style={{ marginTop: '0.35rem' }}>
          <div style={{ fontSize: '0.74rem', color: '#64748b', marginBottom: '0.3rem', fontWeight: 500 }}>
            Chọn từ danh mục chuẩn (Bắt buộc | Ưu tiên):
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem' }}>
            {filteredTaxonomy.map((sk) => (
              <div
                key={sk.skillId}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  border: '1px solid #cbd5e1',
                  borderRadius: '0.45rem',
                  background: '#ffffff',
                  overflow: 'hidden',
                }}
              >
                <button
                  type="button"
                  onClick={() => addRequirement(sk, 'REQUIRED')}
                  style={{
                    border: 'none',
                    background: 'transparent',
                    padding: '0.24rem 0.5rem',
                    fontSize: '0.75rem',
                    fontWeight: 600,
                    color: '#1e293b',
                    cursor: 'pointer',
                  }}
                  title="Thêm làm kỹ năng BẮT BUỘC"
                >
                  {sk.name}
                </button>
                <button
                  type="button"
                  onClick={() => addRequirement(sk, 'PREFERRED')}
                  style={{
                    border: 'none',
                    borderLeft: '1px solid #e2e8f0',
                    background: '#f0fdf4',
                    padding: '0.24rem 0.42rem',
                    fontSize: '0.72rem',
                    fontWeight: 700,
                    color: '#15803d',
                    cursor: 'pointer',
                  }}
                  title="Thêm làm kỹ năng ƯU TIÊN THÊM"
                >
                  Ưu tiên
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* GHI CHÚ KỸ NĂNG NGOÀI DANH MỤC (KHÔNG LÀM LỆCH MATCHING TAXONOMY) */}
      {typeof onCustomSkillNotesChange === 'function' && (
        <div style={{ marginTop: '0.7rem' }}>
          <label style={{ display: 'block', fontSize: '0.76rem', fontWeight: 600, color: '#475569', marginBottom: 4 }}>
            Ghi chú yêu cầu bổ sung (nếu kỹ năng chưa có trong danh mục chuẩn)
          </label>
          <input
            type="text"
            className="eo-form-input"
            value={customSkillNotes || ''}
            onChange={(e) => onCustomSkillNotesChange(e.target.value)}
            placeholder="VD: Biết pha Latte Art cơ bản hoặc giao tiếp tiếng Nhật..."
            style={{ backgroundColor: '#ffffff', fontSize: '0.78rem', padding: '0.4rem 0.65rem' }}
          />
        </div>
      )}

      {/* 4. TÓM TẮT YÊU CẦU TRƯỚC KHI LƯU */}
      <div
        style={{
          marginTop: '0.75rem',
          padding: '0.55rem 0.75rem',
          background: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '0.5rem',
          fontSize: '0.75rem',
          color: '#1e3a8a',
          lineHeight: 1.45,
        }}
      >
        <strong>Tóm tắt yêu cầu:</strong>{' '}
        Kỹ năng chính: <strong>{performedSkillObj ? performedSkillObj.name : 'Chưa chọn'}</strong> •{' '}
        <strong>{requiredCount}</strong> bắt buộc • <strong>{preferredCount}</strong> ưu tiên thêm
        {certCount > 0 ? ` • ${certCount} yêu cầu chứng chỉ còn hiệu lực` : ''}
      </div>
    </div>
  );
};

export default ShiftSkillSelector;
