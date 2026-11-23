import { useState, useEffect } from 'react';
import { DEFAULT_SKILL_CATEGORIES } from '../types/adminSkill.constants';

/**
 * Modal thêm / chỉnh sửa kỹ năng
 * Đảm bảo luôn căn giữa viewport (fixed overlay), giao diện tối giản không icon
 */
export const SkillFormModal = ({
  isOpen,
  mode = 'create',
  initialData = null,
  categories = [],
  onClose,
  onSubmit,
  submitting = false,
}) => {
  const [formData, setFormData] = useState({
    name: '',
    category: 'F&B',
    customCategory: '',
    description: '',
    isActive: true,
  });
  const [isCustomCategory, setIsCustomCategory] = useState(false);
  const [errors, setErrors] = useState({});

  const availableCategories = Array.from(
    new Set([...DEFAULT_SKILL_CATEGORIES, ...categories])
  ).filter(Boolean);

  useEffect(() => {
    if (initialData && mode === 'edit') {
      const isCustom = !availableCategories.includes(initialData.category);
      setFormData({
        name: initialData.name || '',
        category: isCustom ? '__CUSTOM__' : initialData.category || 'Khác',
        customCategory: isCustom ? initialData.category : '',
        description: initialData.description || '',
        isActive: initialData.isActive !== false,
      });
      setIsCustomCategory(isCustom);
    } else {
      setFormData({
        name: '',
        category: availableCategories[0] || 'F&B',
        customCategory: '',
        description: '',
        isActive: true,
      });
      setIsCustomCategory(false);
    }
    setErrors({});
  }, [isOpen, mode, initialData]);

  if (!isOpen) return null;

  const handleCategorySelectChange = (e) => {
    const val = e.target.value;
    if (val === '__CUSTOM__') {
      setIsCustomCategory(true);
      setFormData((prev) => ({ ...prev, category: val }));
    } else {
      setIsCustomCategory(false);
      setFormData((prev) => ({ ...prev, category: val, customCategory: '' }));
    }
  };

  const validate = () => {
    const nextErrors = {};
    if (!formData.name.trim()) {
      nextErrors.name = 'Vui lòng nhập tên kỹ năng';
    } else if (formData.name.trim().length > 100) {
      nextErrors.name = 'Tên kỹ năng không được vượt quá 100 ký tự';
    }

    if (isCustomCategory) {
      if (!formData.customCategory.trim()) {
        nextErrors.category = 'Vui lòng nhập tên danh mục tùy chỉnh';
      }
    } else if (!formData.category) {
      nextErrors.category = 'Vui lòng chọn danh mục';
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!validate()) return;

    const finalCategory = isCustomCategory
      ? formData.customCategory.trim()
      : formData.category;

    const payload = {
      name: formData.name.trim(),
      category: finalCategory,
      description: formData.description.trim(),
      isActive: formData.isActive,
    };

    onSubmit(payload);
  };

  return (
    <div className="skill-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true">
      <div className="skill-modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="skill-modal-header">
          <h3>{mode === 'edit' ? 'Chỉnh Sửa Kỹ Năng' : 'Thêm Kỹ Năng Chuẩn Mới'}</h3>
          <button type="button" className="skill-modal-close-btn" onClick={onClose}>
            Đóng
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="skill-modal-body">
            <div className="skill-form-group">
              <label className="skill-form-label">
                Tên Kỹ Năng <span className="required-mark">*</span>
              </label>
              <input
                type="text"
                className="skill-form-input"
                placeholder="Ví dụ: Pha chế (Bartender/Barista)"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                autoFocus
              />
              {errors.name && <div className="skill-form-error">{errors.name}</div>}
            </div>

            <div className="skill-form-group">
              <label className="skill-form-label">
                Danh Mục / Phân Nhóm Nghiệp Vụ <span className="required-mark">*</span>
              </label>
              <select
                className="skill-form-select"
                value={formData.category}
                onChange={handleCategorySelectChange}
              >
                {availableCategories.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
                <option value="__CUSTOM__">+ Nhập danh mục mới...</option>
              </select>

              {isCustomCategory && (
                <input
                  type="text"
                  className="skill-form-input"
                  placeholder="Nhập tên danh mục mới..."
                  style={{ marginTop: '0.45rem' }}
                  value={formData.customCategory}
                  onChange={(e) => setFormData({ ...formData, customCategory: e.target.value })}
                />
              )}
              {errors.category && <div className="skill-form-error">{errors.category}</div>}
            </div>

            <div className="skill-form-group">
              <label className="skill-form-label">Mô Tả Nghiệp Vụ</label>
              <textarea
                className="skill-form-textarea"
                placeholder="Mô tả tóm tắt nội dung công việc và yêu cầu chính..."
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              />
              <div className="skill-form-hint">
                Hiển thị để định hướng cho EO khi tạo ca và Worker khi khai báo kỹ năng.
              </div>
            </div>

            <div className="skill-form-group" style={{ marginTop: '0.25rem' }}>
              <label className="skill-form-checkbox-label">
                <input
                  type="checkbox"
                  checked={formData.isActive}
                  onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
                />
                Kích hoạt hiển thị cho toàn hệ thống
              </label>
              <div className="skill-form-hint">
                Kỹ năng đang hoạt động sẽ xuất hiện trong danh mục chọn khi EO tạo sự kiện và Worker đăng ký hồ sơ.
              </div>
            </div>
          </div>

          <div className="skill-modal-footer">
            <button
              type="button"
              className="admin-skills-btn-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              Hủy
            </button>
            <button
              type="submit"
              className="admin-skills-btn-primary"
              disabled={submitting}
            >
              {submitting ? 'Đang lưu...' : mode === 'edit' ? 'Lưu Thay Đổi' : 'Thêm Kỹ Năng'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
