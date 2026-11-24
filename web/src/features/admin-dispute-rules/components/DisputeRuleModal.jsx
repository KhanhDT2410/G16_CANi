import { useState } from 'react';
import { createPortal } from 'react-dom';
import { showToast } from '../../../components/common/Toast';

export const DisputeRuleModal = ({ rule, onClose, onSave }) => {
  const isEdit = Boolean(rule?.id);
  const [formData, setFormData] = useState({
    code: rule?.code || '',
    behavior: rule?.behavior || '',
    violatingParty: rule?.violatingParty || 'WORKER',
    sanctionForm: rule?.sanctionForm || '',
    specificPenalty: rule?.specificPenalty || '',
    ruleType: rule?.ruleType || 'ATTENDANCE',
    faultOutcome: rule?.faultOutcome || 'WORKER_FAULT',
    repeatWindowDays: rule?.repeatWindowDays || '',
    repeatIndex: rule?.repeatIndex || 1,
    sanctionText: rule?.sanctionText || '',
    adminActionText: rule?.adminActionText || '',
    enabled: rule?.enabled ?? true,
  });
  const [saving, setSaving] = useState(false);

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (formData.specificPenalty.trim().length < 10) {
      showToast('Chế tài cụ thể phải có tối thiểu 10 ký tự', 'warning');
      return;
    }
    if (formData.sanctionText.trim().length < 10) {
      showToast('Nội dung chế tài hiển thị phải có tối thiểu 10 ký tự', 'warning');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        ...formData,
        code: formData.code.trim().toUpperCase(),
        behavior: formData.behavior.trim(),
        sanctionForm: formData.sanctionForm.trim(),
        specificPenalty: formData.specificPenalty.trim(),
        sanctionText: formData.sanctionText.trim(),
        adminActionText: formData.adminActionText?.trim() || null,
        repeatWindowDays: formData.repeatWindowDays ? Number(formData.repeatWindowDays) : null,
        repeatIndex: Number(formData.repeatIndex) || 1,
      };
      await onSave(payload);
      onClose();
    } catch {
      // Toast lỗi do service/caller xử lý
    } finally {
      setSaving(false);
    }
  };

  return createPortal(
    <div className="dispute-modal-overlay" onClick={onClose}>
      <div className="dispute-modal dispute-rule-form-modal" onClick={(e) => e.stopPropagation()}>
        <div className="dispute-modal-header">
          <div>
            <h3>{isEdit ? 'Chỉnh Sửa Quy Tắc Khiếu Nại' : 'Thêm Quy Tắc Khiếu Nại Mới'}</h3>
            <span className="dispute-modal-subtitle">
              {isEdit ? `Mã: ${rule.code} (v${rule.version ?? 0})` : 'Tạo mới quy tắc và mức chế tài tương ứng'}
            </span>
          </div>
          <button type="button" className="dispute-modal-close" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="dispute-modal-body dispute-rule-modal-body">
            <div className="dispute-rule-form-row">
              <div className="dispute-field-group">
                <label>Mã quy tắc *</label>
                <input
                  type="text"
                  name="code"
                  className="dispute-input"
                  placeholder="VD: RULE_NO_SHOW"
                  value={formData.code}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="dispute-field-group">
                <label>Bên vi phạm *</label>
                <select
                  name="violatingParty"
                  className="dispute-select"
                  value={formData.violatingParty}
                  onChange={handleChange}
                  required
                >
                  <option value="WORKER">Nhân sự</option>
                  <option value="EO">Nhà tổ chức</option>
                  <option value="BOTH">Cả hai bên</option>
                </select>
              </div>
            </div>

            <div className="dispute-field-group">
              <label>Mô tả hành vi vi phạm *</label>
              <textarea
                name="behavior"
                className="dispute-textarea"
                rows={2}
                placeholder="VD: Đi làm trễ >30 phút hoặc không check-in ca..."
                value={formData.behavior}
                onChange={handleChange}
                required
              />
            </div>

            <div className="dispute-rule-form-row">
              <div className="dispute-field-group">
                <label>Loại tranh chấp *</label>
                <select
                  name="ruleType"
                  className="dispute-select"
                  value={formData.ruleType}
                  onChange={handleChange}
                  required
                >
                  <option value="ATTENDANCE">Điểm danh / Bỏ ca / Đi trễ</option>
                  <option value="WORK_QUALITY">Chất lượng công việc</option>
                  <option value="PAYMENT">Tranh chấp thù lao</option>
                  <option value="CONDUCT">Hành vi ứng xử</option>
                  <option value="OTHER">Khác / Vu khống / Giả mạo</option>
                </select>
              </div>

              <div className="dispute-field-group">
                <label>Kết quả phán quyết tương ứng *</label>
                <select
                  name="faultOutcome"
                  className="dispute-select"
                  value={formData.faultOutcome}
                  onChange={handleChange}
                  required
                >
                  <option value="WORKER_FAULT">Lỗi thuộc về Nhân sự</option>
                  <option value="EO_FAULT">Lỗi thuộc về Nhà tổ chức</option>
                  <option value="PARTIAL">Lỗi cả hai phía</option>
                  <option value="UNVERIFIED">Không đủ căn cứ xác minh</option>
                  <option value="UPHOLD">Bác đơn / Giữ nguyên</option>
                </select>
              </div>
            </div>

            <div className="dispute-rule-form-row">
              <div className="dispute-field-group">
                <label>Hình thức chế tài tóm tắt *</label>
                <input
                  type="text"
                  name="sanctionForm"
                  className="dispute-input"
                  placeholder="VD: Trừ điểm uy tín + Tạm khóa"
                  value={formData.sanctionForm}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="dispute-field-group">
                <label>Cửa sổ tái phạm (Ngày)</label>
                <select
                  name="repeatWindowDays"
                  className="dispute-select"
                  value={formData.repeatWindowDays}
                  onChange={handleChange}
                >
                  <option value="">Không xét cửa sổ (Lần 1 hoặc mặc định)</option>
                  <option value="30">30 ngày</option>
                  <option value="60">60 ngày</option>
                  <option value="90">90 ngày</option>
                </select>
              </div>

              <div className="dispute-field-group">
                <label>Lần vi phạm *</label>
                <input
                  type="number"
                  name="repeatIndex"
                  className="dispute-input"
                  min={1}
                  max={10}
                  value={formData.repeatIndex}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="dispute-field-group">
              <label>Chế tài cụ thể (Tối thiểu 10 ký tự) *</label>
              <textarea
                name="specificPenalty"
                className="dispute-textarea"
                rows={2}
                placeholder="VD: Trừ 25 điểm uy tín qua điều chỉnh của Quản trị viên + tạm khóa tài khoản 7 ngày"
                value={formData.specificPenalty}
                onChange={handleChange}
                required
              />
            </div>

            <div className="dispute-field-group">
              <label>Nội dung chế tài hiển thị cho CSKH (Tối thiểu 10 ký tự) *</label>
              <textarea
                name="sanctionText"
                className="dispute-textarea"
                rows={2}
                placeholder="VD: Lần 2 trong 60 ngày: Trừ 25 điểm uy tín + tạm khóa tài khoản 7 ngày."
                value={formData.sanctionText}
                onChange={handleChange}
                required
              />
            </div>

            <div className="dispute-field-group">
              <label>Lệnh cần Quản trị viên can thiệp thủ công</label>
              <input
                type="text"
                name="adminActionText"
                className="dispute-input"
                placeholder="VD: Quản trị viên điều chỉnh giảm 25 điểm uy tín và tạm khóa 7 ngày"
                value={formData.adminActionText}
                onChange={handleChange}
              />
            </div>

            <div className="dispute-rule-checkbox-group">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  name="enabled"
                  checked={formData.enabled}
                  onChange={handleChange}
                />
                <span>Kích hoạt áp dụng quy tắc này trong màn hình phán quyết của Chăm sóc khách hàng</span>
              </label>
            </div>
          </div>

          <div className="dispute-modal-footer">
            <button type="button" className="btn-cancel" onClick={onClose} disabled={saving}>
              Hủy
            </button>
            <button type="submit" className="btn-submit" disabled={saving}>
              {saving ? 'Đang lưu...' : isEdit ? 'Cập nhật quy tắc' : 'Tạo mới quy tắc'}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
};
