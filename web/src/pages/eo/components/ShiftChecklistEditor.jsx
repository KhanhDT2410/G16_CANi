import React, { useState } from 'react';
import { normalizeChecklistItems } from '../hooks/useShifts';

const QUICK_SUGGESTIONS = [
  'Có mặt trước 20 phút nhận thẻ và vị trí',
  'Đồng phục chỉn chu theo quy định',
  'Quét mã QR Check-in đầu ca',
  'Báo cáo quản lý trước khi kết thúc ca',
];

/**
 * ShiftChecklistEditor
 * Quản lý Checklist Đầu Việc Cho Ca Làm Việc:
 * - Bật/tắt yêu cầu minh chứng hình ảnh (isRequiredEvidence) cho từng đầu việc
 * - Kéo thả (Drag & Drop) hoặc bấm nút Lên/Xuống để đảo vị trí thứ tự đầu việc
 */
const ShiftChecklistEditor = ({
  items = [],
  onChange,
  titlePrefix = 'Checklist Đầu Việc Cho Ca Này',
}) => {
  const normalizedList = normalizeChecklistItems(items);
  const [newTitle, setNewTitle] = useState('');
  const [newRequireEvidence, setNewRequireEvidence] = useState(false);
  const [draggedIdx, setDraggedIdx] = useState(null);
  const [dragOverIdx, setDragOverIdx] = useState(null);

  const emitChange = (updatedList) => {
    onChange?.(
      updatedList.map((item, idx) => ({
        id: `chk_${idx + 1}`,
        title: item.title,
        isRequiredEvidence: Boolean(item.isRequiredEvidence),
      }))
    );
  };

  const handleAddItem = (e) => {
    e?.preventDefault();
    const trimmed = newTitle.trim();
    if (!trimmed) return;
    const nextList = [
      ...normalizedList,
      {
        id: `chk_${normalizedList.length + 1}`,
        title: trimmed,
        isRequiredEvidence: newRequireEvidence,
      },
    ];
    emitChange(nextList);
    setNewTitle('');
  };

  const handleQuickAdd = (text) => {
    if (normalizedList.some((i) => i.title.toLowerCase() === text.toLowerCase())) return;
    emitChange([
      ...normalizedList,
      {
        id: `chk_${normalizedList.length + 1}`,
        title: text,
        isRequiredEvidence: false,
      },
    ]);
  };

  const handleToggleEvidence = (idx) => {
    const nextList = normalizedList.map((item, i) =>
      i === idx ? { ...item, isRequiredEvidence: !item.isRequiredEvidence } : item
    );
    emitChange(nextList);
  };

  const handleRemoveItem = (idx) => {
    const nextList = normalizedList.filter((_, i) => i !== idx);
    emitChange(nextList);
  };

  const handleMoveItem = (fromIdx, toIdx) => {
    if (toIdx < 0 || toIdx >= normalizedList.length || fromIdx === toIdx) return;
    const nextList = [...normalizedList];
    const [moved] = nextList.splice(fromIdx, 1);
    nextList.splice(toIdx, 0, moved);
    emitChange(nextList);
  };

  const handleDragStart = (e, idx) => {
    setDraggedIdx(idx);
    e.dataTransfer.effectAllowed = 'move';
    try {
      e.dataTransfer.setData('text/plain', String(idx));
    } catch {
      // ignore
    }
  };

  const handleDragOver = (e, idx) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (dragOverIdx !== idx) {
      setDragOverIdx(idx);
    }
  };

  const handleDrop = (e, targetIdx) => {
    e.preventDefault();
    const sourceIdx = draggedIdx !== null ? draggedIdx : parseInt(e.dataTransfer.getData('text/plain'), 10);
    setDraggedIdx(null);
    setDragOverIdx(null);
    if (!isNaN(sourceIdx) && sourceIdx !== targetIdx) {
      handleMoveItem(sourceIdx, targetIdx);
    }
  };

  const handleDragEnd = () => {
    setDraggedIdx(null);
    setDragOverIdx(null);
  };

  return (
    <div
      className="eo-form-group"
      style={{
        backgroundColor: '#ffffff',
        padding: '1rem',
        borderRadius: '0.625rem',
        border: '1px solid #cbd5e1',
      }}
    >
      <label
        style={{
          fontWeight: 700,
          color: '#1e293b',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '0.5rem',
          marginBottom: '0.25rem',
        }}
      >
        <span>
          {titlePrefix} ({normalizedList.length})
        </span>
        <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 500 }}>
          Kéo thả ⋮⋮ để đảo thứ tự • Bật &ldquo;Yêu cầu minh chứng&rdquo; nếu cần nhân sự chụp ảnh
        </span>
      </label>

      {/* Thanh nhập đầu việc mới + Nút bật Yêu cầu minh chứng */}
      <div style={{ display: 'flex', gap: '0.5rem', margin: '0.6rem 0', flexWrap: 'wrap' }}>
        <input
          type="text"
          className="eo-form-input"
          style={{ flex: 1, minWidth: '200px' }}
          placeholder="Nhập tên đầu việc cần làm trong ca..."
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              handleAddItem(e);
            }
          }}
        />
        <button
          type="button"
          onClick={() => setNewRequireEvidence((prev) => !prev)}
          style={{
            padding: '0.45rem 0.75rem',
            borderRadius: '0.4rem',
            border: newRequireEvidence ? '1.5px solid #2563eb' : '1px solid #cbd5e1',
            backgroundColor: newRequireEvidence ? '#eff6ff' : '#f8fafc',
            color: newRequireEvidence ? '#1d4ed8' : '#475569',
            fontSize: '0.78rem',
            fontWeight: 600,
            cursor: 'pointer',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.35rem',
            whiteSpace: 'nowrap',
          }}
          title="Bật nếu đầu việc này bắt buộc nhân sự chụp ảnh minh chứng trên app"
        >
          <span>{newRequireEvidence ? '☑' : '☐'}</span>
          <span>Yêu cầu minh chứng</span>
        </button>
        <button
          type="button"
          className="eo-btn-secondary"
          style={{
            padding: '0.45rem 0.9rem',
            fontWeight: 700,
            backgroundColor: '#2563eb',
            color: '#ffffff',
            border: 'none',
            whiteSpace: 'nowrap',
          }}
          onClick={handleAddItem}
        >
          + Thêm đầu việc
        </button>
      </div>

      {/* Gợi ý thêm nhanh */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginBottom: '0.75rem' }}>
        <span style={{ fontSize: '0.74rem', color: '#64748b', alignSelf: 'center', marginRight: '0.2rem' }}>
          Gợi ý nhanh:
        </span>
        {QUICK_SUGGESTIONS.map((sItem) => (
          <button
            key={sItem}
            type="button"
            onClick={() => handleQuickAdd(sItem)}
            style={{
              fontSize: '0.73rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '4px',
              border: '1px solid #cbd5e1',
              background: '#f8fafc',
              cursor: 'pointer',
              color: '#334155',
            }}
          >
            + {sItem}
          </button>
        ))}
      </div>

      {/* Danh sách đầu việc có Kéo Thả (Drag & Drop) + Nút Yêu Cầu Minh Chứng */}
      {normalizedList.length > 0 ? (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '0.4rem',
            maxHeight: '240px',
            overflowY: 'auto',
            paddingRight: '0.2rem',
          }}
        >
          {normalizedList.map((item, idx) => {
            const isDragging = draggedIdx === idx;
            const isDragOver = dragOverIdx === idx && draggedIdx !== idx;
            return (
              <div
                key={item.id || idx}
                draggable
                onDragStart={(e) => handleDragStart(e, idx)}
                onDragOver={(e) => handleDragOver(e, idx)}
                onDrop={(e) => handleDrop(e, idx)}
                onDragEnd={handleDragEnd}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '0.5rem',
                  backgroundColor: isDragOver ? '#eff6ff' : isDragging ? '#f1f5f9' : '#f8fafc',
                  border: isDragOver ? '1.5px dashed #2563eb' : '1px solid #e2e8f0',
                  borderRadius: '0.4rem',
                  padding: '0.45rem 0.65rem',
                  fontSize: '0.825rem',
                  opacity: isDragging ? 0.6 : 1,
                  cursor: 'grab',
                  transition: 'background-color 0.15s, border-color 0.15s',
                }}
                title="Kéo thả để thay đổi vị trí thứ tự đầu việc"
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1, minWidth: 0 }}>
                  <span
                    style={{
                      color: '#94a3b8',
                      fontWeight: 900,
                      cursor: 'grab',
                      userSelect: 'none',
                      fontSize: '0.95rem',
                      lineHeight: 1,
                    }}
                    title="Giữ chuột kéo thả để đảo vị trí"
                  >
                    ⋮⋮
                  </span>
                  <strong style={{ color: '#2563eb', flexShrink: 0 }}>#{idx + 1}</strong>
                  <span style={{ color: '#1e293b', fontWeight: 500, wordBreak: 'break-word' }}>
                    {item.title}
                  </span>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', flexShrink: 0 }}>
                  {/* Nút bật/tắt Yêu cầu có minh chứng (Evidence) */}
                  <button
                    type="button"
                    onClick={() => handleToggleEvidence(idx)}
                    style={{
                      fontSize: '0.73rem',
                      padding: '0.22rem 0.55rem',
                      borderRadius: '999px',
                      border: item.isRequiredEvidence ? '1px solid #16a34a' : '1px solid #cbd5e1',
                      backgroundColor: item.isRequiredEvidence ? '#dcfce7' : '#ffffff',
                      color: item.isRequiredEvidence ? '#15803d' : '#64748b',
                      fontWeight: item.isRequiredEvidence ? 700 : 500,
                      cursor: 'pointer',
                      whiteSpace: 'nowrap',
                    }}
                    title="Nhấn để bật/tắt yêu cầu nhân sự gửi ảnh minh chứng hoàn thành đầu việc này"
                  >
                    {item.isRequiredEvidence ? '✓ Cần minh chứng (Evidence)' : '+ Yêu cầu minh chứng'}
                  </button>

                  {/* Nút đảo vị trí nhanh Lên / Xuống */}
                  <div style={{ display: 'inline-flex', gap: '2px' }}>
                    <button
                      type="button"
                      disabled={idx === 0}
                      onClick={() => handleMoveItem(idx, idx - 1)}
                      style={{
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '4px',
                        padding: '0.1rem 0.35rem',
                        fontSize: '0.72rem',
                        cursor: idx === 0 ? 'not-allowed' : 'pointer',
                        color: idx === 0 ? '#cbd5e1' : '#475569',
                      }}
                      title="Đưa đầu việc lên trên"
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      disabled={idx === normalizedList.length - 1}
                      onClick={() => handleMoveItem(idx, idx + 1)}
                      style={{
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '4px',
                        padding: '0.1rem 0.35rem',
                        fontSize: '0.72rem',
                        cursor: idx === normalizedList.length - 1 ? 'not-allowed' : 'pointer',
                        color: idx === normalizedList.length - 1 ? '#cbd5e1' : '#475569',
                      }}
                      title="Đưa đầu việc xuống dưới"
                    >
                      ↓
                    </button>
                  </div>

                  {/* Nút xóa đầu việc */}
                  <button
                    type="button"
                    onClick={() => handleRemoveItem(idx)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: '#ef4444',
                      cursor: 'pointer',
                      fontWeight: 700,
                      padding: '0.15rem 0.35rem',
                      fontSize: '0.85rem',
                    }}
                    title="Xóa đầu việc này"
                  >
                    ✕
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div
          style={{
            fontSize: '0.8rem',
            color: '#94a3b8',
            fontStyle: 'italic',
            textAlign: 'center',
            padding: '0.65rem',
            backgroundColor: '#f8fafc',
            borderRadius: '0.4rem',
            border: '1px dashed #cbd5e1',
          }}
        >
          Chưa có đầu việc nào cho ca này. Hãy nhập tên đầu việc ở trên và bấm &ldquo;+ Thêm đầu việc&rdquo;.
        </div>
      )}
    </div>
  );
};

export default ShiftChecklistEditor;
