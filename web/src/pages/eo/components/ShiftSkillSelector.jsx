import React, { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';

/**
 * ShiftSkillSelector
 * Cho phép EO nhập tay kỹ năng mới tự do (Dynamic Custom Skill)
 * hoặc chọn nhanh từ danh mục hệ thống (Taxonomy Suggestions).
 * Đảm bảo cấp UUID chuẩn cho CSDL để Matching Dispatch Engine hoạt động chính xác.
 */
const ShiftSkillSelector = ({
  selectedSkillIds = [],
  onChange,
  taxonomy = [],
  onSkillCreated,
}) => {
  const [inputVal, setInputVal] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [localTaxonomy, setLocalTaxonomy] = useState(taxonomy || []);
  const [errorMsg, setErrorMsg] = useState('');

  // Đồng bộ localTaxonomy khi taxonomy prop thay đổi
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

  const selectedIds = Array.isArray(selectedSkillIds) ? selectedSkillIds : [];

  // Tạo map tra cứu tên nhanh theo ID
  const skillMap = new Map();
  localTaxonomy.forEach((sk) => {
    if (sk && sk.skillId) {
      skillMap.set(sk.skillId, sk);
    }
  });

  const handleAddSkill = async () => {
    const trimmed = inputVal.trim();
    if (!trimmed) return;
    setErrorMsg('');

    // 1. Kiểm tra xem kỹ năng đã tồn tại trong localTaxonomy chưa (không phân biệt hoa/thường)
    const existing = localTaxonomy.find(
      (s) => s.name && s.name.trim().toLowerCase() === trimmed.toLowerCase()
    );

    if (existing) {
      if (!selectedIds.includes(existing.skillId)) {
        onChange([...selectedIds, existing.skillId]);
      }
      setInputVal('');
      return;
    }

    // 2. Nếu chưa có, gọi API tạo kỹ năng tự do mới
    setSubmitting(true);
    try {
      const created = await eoService.createCustomSkill(trimmed, 'Tự chọn');
      if (created && created.skillId) {
        // Cập nhật localTaxonomy
        setLocalTaxonomy((prev) => [...prev, created]);
        if (typeof onSkillCreated === 'function') {
          onSkillCreated(created);
        }
        // Thêm vào selectedIds
        onChange([...selectedIds, created.skillId]);
      }
      setInputVal('');
    } catch (err) {
      console.error('Lỗi khi tạo kỹ năng nhập tay:', err);
      setErrorMsg(err.response?.data?.message || 'Không thể tạo kỹ năng mới. Vui lòng thử lại.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddSkill();
    }
  };

  const handleRemoveSkill = (skillIdToRemove) => {
    onChange(selectedIds.filter((id) => id !== skillIdToRemove));
  };

  const handleToggleSuggestion = (skillId) => {
    if (selectedIds.includes(skillId)) {
      handleRemoveSkill(skillId);
    } else {
      onChange([...selectedIds, skillId]);
    }
  };

  // Danh sách các kỹ năng gợi ý chưa được chọn
  const unselectedSuggestions = localTaxonomy.filter(
    (sk) => sk && sk.skillId && !selectedIds.includes(sk.skillId)
  );

  return (
    <div className="eo-form-group" style={{ marginBottom: '1.25rem' }}>
      <label style={{ display: 'block', fontWeight: 600, fontSize: '0.85rem', color: '#1e293b', marginBottom: '0.35rem' }}>
        Kỹ Năng Bắt Buộc Yêu Cầu Cho Ca (Nhập tay hoặc chọn nhanh)
      </label>

      {/* 1. KHU VỰC KỸ NĂNG ĐÃ CHỌN */}
      {selectedIds.length > 0 ? (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.45rem', marginBottom: '0.65rem' }}>
          {selectedIds.map((id) => {
            const skillObj = skillMap.get(id);
            const name = skillObj ? skillObj.name : `Kỹ năng (${id.slice(0, 6)}...)`;
            return (
              <span
                key={id}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  padding: '0.3rem 0.65rem',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  backgroundColor: '#eff6ff',
                  color: '#1d4ed8',
                  border: '1.5px solid #93c5fd',
                  borderRadius: '0.5rem',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
                }}
              >
                <span>✓ {name}</span>
                <button
                  type="button"
                  onClick={() => handleRemoveSkill(id)}
                  title="Xóa kỹ năng này"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '18px',
                    height: '18px',
                    padding: 0,
                    border: 'none',
                    background: '#dbeafe',
                    color: '#1e40af',
                    borderRadius: '50%',
                    cursor: 'pointer',
                    fontSize: '0.75rem',
                    fontWeight: 'bold',
                    lineHeight: 1,
                  }}
                >
                  ✕
                </button>
              </span>
            );
          })}
        </div>
      ) : (
        <div style={{ fontSize: '0.78rem', color: '#64748b', fontStyle: 'italic', marginBottom: '0.5rem' }}>
          Chưa chọn kỹ năng nào (Ca phổ thông, hệ thống sẽ mở rộng tìm kiếm ứng viên tự do).
        </div>
      )}

      {/* 2. THANH NHẬP TAY KỸ NĂNG MỚI */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
        <input
          type="text"
          value={inputVal}
          onChange={(e) => setInputVal(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Nhập tên kỹ năng cần cho ca (vd: Pha chế Latte Art, Lái xe nâng, Tiếng Trung HSK 4...)"
          disabled={submitting}
          style={{
            flex: 1,
            padding: '0.45rem 0.75rem',
            fontSize: '0.82rem',
            border: '1px solid #cbd5e1',
            borderRadius: '0.45rem',
            outline: 'none',
            backgroundColor: '#ffffff',
          }}
        />
        <button
          type="button"
          onClick={handleAddSkill}
          disabled={submitting || !inputVal.trim()}
          style={{
            padding: '0.45rem 0.9rem',
            fontSize: '0.82rem',
            fontWeight: 600,
            color: '#ffffff',
            backgroundColor: inputVal.trim() && !submitting ? '#2563eb' : '#94a3b8',
            border: 'none',
            borderRadius: '0.45rem',
            cursor: inputVal.trim() && !submitting ? 'pointer' : 'not-allowed',
            whiteSpace: 'nowrap',
            transition: 'background-color 0.15s ease',
          }}
        >
          {submitting ? 'Đang thêm...' : '+ Thêm kỹ năng'}
        </button>
      </div>

      {errorMsg && (
        <div style={{ fontSize: '0.76rem', color: '#dc2626', marginBottom: '0.4rem' }}>
          {errorMsg}
        </div>
      )}

      {/* 3. GỢI Ý NHANH TỪ HỆ THỐNG */}
      {unselectedSuggestions.length > 0 && (
        <div style={{ marginTop: '0.4rem' }}>
          <div style={{ fontSize: '0.74rem', color: '#64748b', marginBottom: '0.3rem', fontWeight: 500 }}>
            Gợi ý nhanh từ danh mục (click để thêm):
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
            {unselectedSuggestions.map((sk) => (
              <button
                key={sk.skillId}
                type="button"
                onClick={() => handleToggleSuggestion(sk.skillId)}
                style={{
                  fontSize: '0.76rem',
                  padding: '0.25rem 0.55rem',
                  borderRadius: '0.4rem',
                  border: '1px dashed #cbd5e1',
                  backgroundColor: '#f8fafc',
                  color: '#475569',
                  cursor: 'pointer',
                  transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.backgroundColor = '#e2e8f0';
                  e.currentTarget.style.borderColor = '#94a3b8';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.backgroundColor = '#f8fafc';
                  e.currentTarget.style.borderColor = '#cbd5e1';
                }}
              >
                + {sk.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* GHI CHÚ NGHIỆP VỤ */}
      <div style={{ fontSize: '0.72rem', color: '#64748b', marginTop: '0.45rem', lineHeight: 1.4 }}>
        Hệ thống chỉ mời ứng viên có đúng kỹ năng này (bộ lọc cứng trong thuật toán Matching). Bạn có thể tự nhập bất kỳ kỹ năng nào theo nhu cầu thực tế của ca làm việc.
      </div>
    </div>
  );
};

export default ShiftSkillSelector;
