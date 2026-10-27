import { useState } from 'react';

/**
 * Modal Khởi Tạo Vai Trò Mới (Create Custom Role - UC-113)
 * Tuân thủ quy cách Modal tại DESIGN.md: Kính mờ backdrop-filter, animation scaleIn.
 */
export const CreateRoleModal = ({
  isOpen,
  onClose,
  onConfirm,
  existingRoles,
}) => {
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [description, setDescription] = useState('');
  const [cloneFromRoleId, setCloneFromRoleId] = useState('');
  const [color, setColor] = useState('#8b5cf6');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  if (!isOpen) return null;

  const COLOR_PRESETS = [
    { label: 'Tím Hoàng Gia', value: '#8b5cf6' },
    { label: 'Hồng Fuchsia', value: '#ec4899' },
    { label: 'Cam Hổ Phách', value: '#f97316' },
    { label: 'Xanh Teal', value: '#14b8a6' },
    { label: 'Xanh Indigo', value: '#6366f1' },
  ];

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Vui lòng nhập tên vai trò.');
      return;
    }
    if (!code.trim()) {
      setError('Vui lòng nhập mã định danh vai trò (VD: EVENT_COORDINATOR).');
      return;
    }

    // Check duplicate code
    const isDuplicate = existingRoles.some(
      (r) => r.code.toUpperCase() === code.trim().toUpperCase()
    );
    if (isDuplicate) {
      setError('Mã vai trò này đã tồn tại trong hệ thống. Vui lòng chọn mã khác.');
      return;
    }

    onConfirm({
      name,
      code,
      description,
      cloneFromRoleId: cloneFromRoleId || null,
      color,
      reason,
    });
  };

  return (
    <div className="rbac-modal-backdrop" onClick={onClose}>
      <div
        className="rbac-modal-card animate-scale-in"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="rbac-modal-header">
          <div className="rbac-modal-header-icon">🛡️</div>
          <div>
            <h3 className="rbac-modal-title">Tạo Vai Trò Tùy Chỉnh Mới</h3>
            <p className="rbac-modal-sub">
              Khởi tạo nhóm thẩm quyền mới theo yêu cầu vận hành (UC-113)
            </p>
          </div>
          <button type="button" className="rbac-modal-close" onClick={onClose}>
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="rbac-modal-form">
          {error && <div className="rbac-form-error">{error}</div>}

          {/* Tên vai trò */}
          <div className="rbac-form-group">
            <label className="rbac-label">
              Tên vai trò hiển thị <span className="req">*</span>
            </label>
            <input
              type="text"
              className="rbac-input"
              placeholder="VD: Giám Sát Hiện Trường, Điều Phối Viên..."
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                setError('');
              }}
              required
            />
          </div>

          {/* Mã vai trò */}
          <div className="rbac-form-group">
            <label className="rbac-label">
              Mã định danh hệ thống (UPPERCASE) <span className="req">*</span>
            </label>
            <input
              type="text"
              className="rbac-input code-input"
              placeholder="VD: EVENT_SUPERVISOR"
              value={code}
              onChange={(e) => {
                setCode(e.target.value.toUpperCase().replace(/\s+/g, '_'));
                setError('');
              }}
              required
            />
          </div>

          {/* Sao chép mẫu phân quyền */}
          <div className="rbac-form-group">
            <label className="rbac-label">Sao chép quyền từ vai trò có sẵn</label>
            <select
              className="rbac-select"
              value={cloneFromRoleId}
              onChange={(e) => setCloneFromRoleId(e.target.value)}
            >
              <option value="">-- Không sao chép (Bắt đầu với 0 quyền) --</option>
              {existingRoles.map((r) => (
                <option key={r.id} value={r.id}>
                  Sao chép từ: {r.name} ({r.permissions.length} quyền)
                </option>
              ))}
            </select>
          </div>

          {/* Màu sắc nhận diện */}
          <div className="rbac-form-group">
            <label className="rbac-label">Màu sắc nhận diện vai trò</label>
            <div className="rbac-color-presets">
              {COLOR_PRESETS.map((p) => (
                <button
                  key={p.value}
                  type="button"
                  className={`rbac-color-btn ${color === p.value ? 'selected' : ''}`}
                  style={{ backgroundColor: p.value }}
                  onClick={() => setColor(p.value)}
                  title={p.label}
                />
              ))}
            </div>
          </div>

          {/* Mô tả vai trò */}
          <div className="rbac-form-group">
            <label className="rbac-label">Mô tả mục đích & phạm vi</label>
            <textarea
              className="rbac-textarea"
              rows={2}
              placeholder="Mô tả chức năng, đối tượng người dùng áp dụng..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          {/* Lý do tạo vai trò (cho Kiểm toán W-42) */}
          <div className="rbac-form-group">
            <label className="rbac-label">Căn cứ / Lý do khởi tạo</label>
            <input
              type="text"
              className="rbac-input"
              placeholder="VD: Căn cứ đề xuất vận hành ca làm việc quy mô lớn..."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>

          <div className="rbac-modal-footer">
            <button
              type="button"
              className="rbac-btn-secondary"
              onClick={onClose}
            >
              Hủy Bỏ
            </button>
            <button type="submit" className="rbac-btn-primary">
              ✓ Khởi Tạo Vai Trò
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default CreateRoleModal;
