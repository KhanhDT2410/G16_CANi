import React, { useState, useEffect } from 'react';
import { normalizeChecklistItems } from '../hooks/useShifts';
import {
  CHECKLIST_ROLE_CATEGORIES,
  STANDARDIZED_CHECKLIST_TEMPLATES,
  resolveCategoryByJobType,
} from '../utils/checklistTemplates';

/**
 * ShiftChecklistEditor
 * Quản lý Checklist Đầu Việc Cho Ca Làm Việc:
 * - Chuẩn hóa danh mục đầu việc mẫu theo vị trí nghiệp vụ sự kiện (EO-001)
 * - Cho phép nhấp chọn và chỉnh sửa trực tiếp (Inline Edit) task vừa thêm (EO-003)
 * - Bật/tắt yêu cầu minh chứng hình ảnh (isRequiredEvidence) cho từng đầu việc
 * - Kéo thả (Drag & Drop) để đảo vị trí thứ tự đầu việc
 */
const ShiftChecklistEditor = ({
  items = [],
  onChange,
  titlePrefix = 'Checklist Đầu Việc Cho Ca Này',
  jobType = '',
}) => {
  const normalizedList = normalizeChecklistItems(items);
  const [newTitle, setNewTitle] = useState('');
  const [newRequireEvidence, setNewRequireEvidence] = useState(false);
  const [draggedIdx, setDraggedIdx] = useState(null);
  const [dragOverIdx, setDragOverIdx] = useState(null);

  // EO-001: Thanh gợi ý mẫu 1 dòng gọn gàng; bấm nút mới xổ bảng chọn theo từng vị trí
  const [isSuggestionsOpen, setIsSuggestionsOpen] = useState(false);
  const [expandedCategory, setExpandedCategory] = useState(null);

  useEffect(() => {
    setIsSuggestionsOpen(false);
    setExpandedCategory(null);
  }, [jobType]);

  // EO-003: Quản lý chỉnh sửa trực tiếp nội dung đầu việc
  const [editingIdx, setEditingIdx] = useState(null);
  const [editingText, setEditingText] = useState('');

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

  const handleQuickAdd = (suggestion) => {
    const title = typeof suggestion === 'string' ? suggestion : suggestion.title;
    const requireEvidence = typeof suggestion === 'object' ? Boolean(suggestion.isRequiredEvidence) : false;
    if (!title || normalizedList.some((i) => i.title.toLowerCase() === title.toLowerCase())) return;
    emitChange([
      ...normalizedList,
      {
        id: `chk_${normalizedList.length + 1}`,
        title,
        isRequiredEvidence: requireEvidence,
      },
    ]);
  };

  const handleAddAllInCategory = (categoryId) => {
    const currentTemplates = STANDARDIZED_CHECKLIST_TEMPLATES[categoryId] || [];
    const existingTitles = new Set(normalizedList.map((i) => i.title.toLowerCase()));
    const toAdd = currentTemplates.filter((item) => !existingTitles.has(item.title.toLowerCase()));
    if (toAdd.length === 0) return;
    const nextList = [
      ...normalizedList,
      ...toAdd.map((item, i) => ({
        id: `chk_${normalizedList.length + i + 1}`,
        title: item.title,
        isRequiredEvidence: Boolean(item.isRequiredEvidence),
      })),
    ];
    emitChange(nextList);
  };

  // EO-003 Handlers
  const handleStartEdit = (idx, currentTitle) => {
    setEditingIdx(idx);
    setEditingText(currentTitle);
  };

  const handleSaveEdit = (idx) => {
    const trimmed = editingText.trim();
    if (!trimmed) {
      setEditingIdx(null);
      setEditingText('');
      return;
    }
    const nextList = normalizedList.map((item, i) =>
      i === idx ? { ...item, title: trimmed } : item
    );
    emitChange(nextList);
    setEditingIdx(null);
    setEditingText('');
  };

  const handleCancelEdit = () => {
    setEditingIdx(null);
    setEditingText('');
  };

  const handleToggleEvidence = (idx) => {
    const nextList = normalizedList.map((item, i) =>
      i === idx ? { ...item, isRequiredEvidence: !item.isRequiredEvidence } : item
    );
    emitChange(nextList);
  };

  const handleRemoveItem = (idx) => {
    if (editingIdx === idx) {
      handleCancelEdit();
    }
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
    if (editingIdx !== null) return;
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

  const existingTitlesSet = new Set(normalizedList.map((i) => i.title.toLowerCase()));
  const matchedCategoryId = resolveCategoryByJobType(jobType);
  const matchedCategoryObj =
    CHECKLIST_ROLE_CATEGORIES.find((c) => c.id === matchedCategoryId) || CHECKLIST_ROLE_CATEGORIES[0];
  const matchedSuggestions = STANDARDIZED_CHECKLIST_TEMPLATES[matchedCategoryObj.id] || [];
  const matchedUnaddedCount = matchedSuggestions.filter(
    (i) => !existingTitlesSet.has(i.title.toLowerCase())
  ).length;

  const handleToggleSuggestionsPanel = () => {
    setIsSuggestionsOpen((prev) => {
      const next = !prev;
      if (next && expandedCategory === null) {
        setExpandedCategory(matchedCategoryObj.id);
      }
      return next;
    });
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
          Nhấp đúp hoặc bấm Sửa để chỉnh sửa task • Kéo thả để đảo thứ tự
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
          <span>{newRequireEvidence ? 'Yêu cầu minh chứng: Bật' : 'Yêu cầu minh chứng'}</span>
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
          Thêm đầu việc
        </button>
      </div>

      {/* EO-001 (Phương án A): Thanh 1 dòng gọn nhẹ + nút thêm nhanh + panel xổ theo từng vị trí khi cần */}
      <div style={{ marginTop: '0.35rem', marginBottom: '0.65rem' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '0.45rem',
            padding: '0.4rem 0.6rem',
            backgroundColor: '#f8fafc',
            borderRadius: isSuggestionsOpen ? '0.5rem 0.5rem 0 0' : '0.5rem',
            border: '1px solid #e2e8f0',
          }}
        >
          <button
            type="button"
            onClick={handleToggleSuggestionsPanel}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              background: 'none',
              border: 'none',
              padding: 0,
              cursor: 'pointer',
              color: isSuggestionsOpen ? '#1d4ed8' : '#334155',
              fontSize: '0.76rem',
              fontWeight: 700,
            }}
          >
            <span style={{ fontSize: '0.7rem', color: isSuggestionsOpen ? '#2563eb' : '#64748b' }}>
              {isSuggestionsOpen ? '▾' : '▸'}
            </span>
            <span>Chọn từ mẫu gợi ý</span>
            <span
              style={{
                fontSize: '0.68rem',
                padding: '0.08rem 0.42rem',
                borderRadius: '999px',
                backgroundColor: '#e0f2fe',
                color: '#0369a1',
                fontWeight: 600,
              }}
            >
              Gợi ý: {matchedCategoryObj.label}
            </span>
          </button>

          {matchedUnaddedCount > 0 ? (
            <button
              type="button"
              onClick={() => handleAddAllInCategory(matchedCategoryObj.id)}
              style={{
                fontSize: '0.71rem',
                padding: '0.2rem 0.55rem',
                borderRadius: '6px',
                border: '1px solid #bfdbfe',
                backgroundColor: '#eff6ff',
                color: '#1d4ed8',
                fontWeight: 700,
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
              title={`Thêm toàn bộ ${matchedUnaddedCount} đầu việc mẫu của nhóm "${matchedCategoryObj.label}"`}
            >
              + Thêm {matchedUnaddedCount} việc {matchedCategoryObj.label}
            </button>
          ) : (
            <span style={{ fontSize: '0.7rem', color: '#059669', fontWeight: 600 }}>
              Đã thêm đủ mẫu {matchedCategoryObj.label}
            </span>
          )}
        </div>

        {isSuggestionsOpen && (
          <div
            style={{
              padding: '0.5rem',
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderTop: 'none',
              borderRadius: '0 0 0.5rem 0.5rem',
              maxHeight: '280px',
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.35rem',
            }}
          >
            {CHECKLIST_ROLE_CATEGORIES.map((cat) => {
              const isExpanded = expandedCategory === cat.id;
              const suggestions = STANDARDIZED_CHECKLIST_TEMPLATES[cat.id] || [];
              const catUnaddedCount = suggestions.filter((i) => !existingTitlesSet.has(i.title.toLowerCase())).length;
              const isRecommended = matchedCategoryObj.id === cat.id;

              return (
                <div
                  key={cat.id}
                  style={{
                    flexShrink: 0,
                    borderRadius: '0.45rem',
                    border: isExpanded ? '1px solid #93c5fd' : '1px solid #e2e8f0',
                    backgroundColor: '#ffffff',
                    overflow: 'hidden',
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: '0.5rem',
                      padding: '0.38rem 0.55rem',
                      backgroundColor: isExpanded ? '#eff6ff' : '#ffffff',
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => setExpandedCategory((prev) => (prev === cat.id ? null : cat.id))}
                      style={{
                        flex: 1,
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.35rem',
                        background: 'none',
                        border: 'none',
                        padding: 0,
                        textAlign: 'left',
                        cursor: 'pointer',
                        color: isExpanded ? '#1d4ed8' : '#1e293b',
                        fontSize: '0.75rem',
                        fontWeight: isExpanded || isRecommended ? 700 : 600,
                      }}
                    >
                      <span style={{ fontSize: '0.68rem', width: '12px', color: isExpanded ? '#2563eb' : '#64748b' }}>
                        {isExpanded ? '▾' : '▸'}
                      </span>
                      <span>{cat.label}</span>
                      {isRecommended && (
                        <span
                          style={{
                            fontSize: '0.64rem',
                            padding: '0.04rem 0.35rem',
                            borderRadius: '999px',
                            backgroundColor: '#dbeafe',
                            color: '#1d4ed8',
                            fontWeight: 700,
                          }}
                        >
                          Phù hợp ca
                        </span>
                      )}
                      <span style={{ fontSize: '0.68rem', color: '#64748b', fontWeight: 500 }}>
                        ({catUnaddedCount}/{suggestions.length})
                      </span>
                    </button>

                    {catUnaddedCount > 0 && (
                      <button
                        type="button"
                        onClick={() => handleAddAllInCategory(cat.id)}
                        style={{
                          fontSize: '0.68rem',
                          padding: '0.14rem 0.45rem',
                          borderRadius: '4px',
                          border: '1px solid #bfdbfe',
                          backgroundColor: '#eff6ff',
                          color: '#1d4ed8',
                          fontWeight: 600,
                          cursor: 'pointer',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        + Thêm ({catUnaddedCount})
                      </button>
                    )}
                  </div>

                  {isExpanded && (
                    <div
                      style={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: '0.35rem',
                        padding: '0.45rem 0.55rem',
                        borderTop: '1px solid #e2e8f0',
                        backgroundColor: '#f8fafc',
                      }}
                    >
                      {suggestions.map((suggestion) => {
                        const isAdded = existingTitlesSet.has(suggestion.title.toLowerCase());
                        return (
                          <button
                            key={suggestion.title}
                            type="button"
                            disabled={isAdded}
                            onClick={() => handleQuickAdd(suggestion)}
                            style={{
                              fontSize: '0.72rem',
                              padding: '0.22rem 0.5rem',
                              borderRadius: '4px',
                              border: isAdded ? '1px dashed #cbd5e1' : '1px solid #cbd5e1',
                              background: isAdded ? '#f1f5f9' : '#ffffff',
                              cursor: isAdded ? 'default' : 'pointer',
                              color: isAdded ? '#94a3b8' : '#1e293b',
                              display: 'inline-flex',
                              alignItems: 'center',
                              gap: '0.3rem',
                            }}
                            title={
                              isAdded
                                ? 'Đầu việc này đã có trong danh sách'
                                : suggestion.isRequiredEvidence
                                ? 'Nhấp để thêm (Có yêu cầu ảnh minh chứng)'
                                : 'Nhấp để thêm nhanh'
                            }
                          >
                            <span>{suggestion.title}</span>
                            {suggestion.isRequiredEvidence && (
                              <span
                                style={{
                                  fontSize: '0.64rem',
                                  color: isAdded ? '#94a3b8' : '#15803d',
                                  backgroundColor: isAdded ? '#e2e8f0' : '#dcfce7',
                                  padding: '0.04rem 0.28rem',
                                  borderRadius: '3px',
                                  fontWeight: 600,
                                }}
                              >
                                Ảnh
                              </span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Danh sách đầu việc có Chỉnh sửa trực tiếp (EO-003) + Kéo Thả (Drag & Drop) */}
      {normalizedList.length > 0 ? (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: '0.4rem',
            maxHeight: '260px',
            overflowY: 'auto',
            paddingRight: '0.2rem',
          }}
        >
          {normalizedList.map((item, idx) => {
            const isDragging = draggedIdx === idx;
            const isDragOver = dragOverIdx === idx && draggedIdx !== idx;
            const isEditing = editingIdx === idx;

            return (
              <div
                key={item.id || idx}
                draggable={!isEditing}
                onDragStart={(e) => handleDragStart(e, idx)}
                onDragOver={(e) => handleDragOver(e, idx)}
                onDrop={(e) => handleDrop(e, idx)}
                onDragEnd={handleDragEnd}
                style={{
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '0.5rem',
                  backgroundColor: isDragOver ? '#eff6ff' : isDragging ? '#f1f5f9' : isEditing ? '#f0fdf4' : '#f8fafc',
                  border: isDragOver
                    ? '1.5px dashed #2563eb'
                    : isEditing
                    ? '1.5px solid #16a34a'
                    : '1px solid #e2e8f0',
                  borderRadius: '0.4rem',
                  padding: '0.45rem 0.65rem',
                  fontSize: '0.825rem',
                  opacity: isDragging ? 0.6 : 1,
                  cursor: isEditing ? 'default' : 'grab',
                  transition: 'background-color 0.15s, border-color 0.15s',
                }}
                title={isEditing ? 'Đang chỉnh sửa nội dung đầu việc' : 'Kéo thả để thay đổi vị trí thứ tự đầu việc'}
              >
                {/* Phần hiển thị / chỉnh sửa inline tiêu đề task (EO-003) */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', flex: 1, minWidth: 0 }}>
                  {!isEditing && (
                    <span
                      aria-hidden="true"
                      style={{ color: '#94a3b8', fontSize: '0.85rem', letterSpacing: '-1px', flexShrink: 0, userSelect: 'none' }}
                      title="Kéo để sắp xếp lại thứ tự"
                    >
                      ⋮⋮
                    </span>
                  )}
                  <strong style={{ color: '#2563eb', flexShrink: 0 }}>#{idx + 1}</strong>

                  {isEditing ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', flex: 1 }}>
                      <input
                        type="text"
                        className="eo-form-input"
                        autoFocus
                        value={editingText}
                        onChange={(e) => setEditingText(e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault();
                            handleSaveEdit(idx);
                          } else if (e.key === 'Escape') {
                            e.preventDefault();
                            handleCancelEdit();
                          }
                        }}
                        style={{
                          flex: 1,
                          fontSize: '0.825rem',
                          padding: '0.25rem 0.5rem',
                          border: '1.5px solid #2563eb',
                          borderRadius: '4px',
                          backgroundColor: '#ffffff',
                        }}
                        placeholder="Nhập tên đầu việc..."
                      />
                      <button
                        type="button"
                        onMouseDown={(e) => {
                          e.preventDefault();
                          handleSaveEdit(idx);
                        }}
                        style={{
                          background: '#16a34a',
                          color: '#ffffff',
                          border: 'none',
                          borderRadius: '4px',
                          padding: '0.25rem 0.5rem',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                          cursor: 'pointer',
                        }}
                        title="Lưu thay đổi (Enter)"
                      >
                        Lưu
                      </button>
                      <button
                        type="button"
                        onMouseDown={(e) => {
                          e.preventDefault();
                          handleCancelEdit();
                        }}
                        style={{
                          background: '#64748b',
                          color: '#ffffff',
                          border: 'none',
                          borderRadius: '4px',
                          padding: '0.25rem 0.5rem',
                          fontSize: '0.75rem',
                          cursor: 'pointer',
                        }}
                        title="Hủy bỏ (Esc)"
                      >
                        Hủy
                      </button>
                    </div>
                  ) : (
                    <span
                      onClick={() => handleStartEdit(idx, item.title)}
                      style={{
                        color: '#1e293b',
                        fontWeight: 500,
                        wordBreak: 'break-word',
                        cursor: 'pointer',
                        padding: '0.15rem 0.35rem',
                        borderRadius: '4px',
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '0.35rem',
                      }}
                      title="Nhấp vào để chỉnh sửa nội dung đầu việc"
                    >
                      <span>{item.title}</span>
                    </span>
                  )}
                </div>

                {/* Các nút hành động bên phải */}
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
                    {item.isRequiredEvidence ? 'Cần minh chứng' : 'Yêu cầu minh chứng'}
                  </button>

                  {/* Nút bấm mở sửa (nếu không đang sửa) */}
                  {!isEditing && (
                    <button
                      type="button"
                      onClick={() => handleStartEdit(idx, item.title)}
                      style={{
                        background: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '4px',
                        padding: '0.15rem 0.45rem',
                        fontSize: '0.73rem',
                        fontWeight: 600,
                        cursor: 'pointer',
                        color: '#475569',
                      }}
                      title="Chỉnh sửa nội dung đầu việc này"
                    >
                      Sửa
                    </button>
                  )}

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
                      padding: '0.15rem 0.4rem',
                      fontSize: '0.73rem',
                    }}
                    title="Xóa đầu việc này"
                  >
                    Xóa
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
          Chưa có đầu việc nào cho ca này. Hãy chọn từ danh mục gợi ý ở trên hoặc nhập tên và bấm &ldquo;Thêm đầu việc&rdquo;.
        </div>
      )}
    </div>
  );
};

export default ShiftChecklistEditor;
