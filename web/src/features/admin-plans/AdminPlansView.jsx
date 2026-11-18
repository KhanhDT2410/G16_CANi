import React, { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../../services/api';
import { showToast } from '../../components/common/Toast';
import './AdminPlans.css';

export default function AdminPlansView() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editingPlan, setEditingPlan] = useState(null);
  const [isCreating, setIsCreating] = useState(false);
  const [saving, setSaving] = useState(false);

  const [formData, setFormData] = useState({
    planName: '',
    planType: 'MATCHING_PLUS',
    price: 0,
    durationDays: 30,
    maxEvents: 5,
    maxShifts: 25,
    maxWorkers: 80,
    baseMatchingQuota: 5,
    featuresText: '',
  });

  const loadPlans = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/v1/admin/plans');
      if (res.data?.success) {
        setPlans(res.data.data || []);
      }
    } catch (err) {
      console.error('Lỗi tải danh mục gói cước Admin:', err);
      showToast('Không thể tải danh sách gói cước từ máy chủ', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPlans();
  }, [loadPlans]);

  const parseFeaturesList = (featuresJson) => {
    if (!featuresJson) return [];
    if (Array.isArray(featuresJson)) return featuresJson;
    try {
      const parsed = typeof featuresJson === 'string' ? JSON.parse(featuresJson) : featuresJson;
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  };

  const handleOpenEdit = (plan) => {
    setIsCreating(false);
    setEditingPlan(plan);
    const feats = parseFeaturesList(plan.features);
    setFormData({
      planName: plan.planName || '',
      planType: plan.planType || 'MATCHING_PLUS',
      price: plan.price || 0,
      durationDays: plan.durationDays || 30,
      maxEvents: plan.maxEvents || 0,
      maxShifts: plan.maxShifts || 0,
      maxWorkers: plan.maxWorkers || 0,
      baseMatchingQuota: plan.baseMatchingQuota || plan.maxEvents || 0,
      featuresText: feats.join('\n'),
    });
  };

  const handleOpenCreate = () => {
    setIsCreating(true);
    setEditingPlan(null);
    setFormData({
      planName: '',
      planType: 'MATCHING_PLUS',
      price: 99000,
      durationDays: 30,
      maxEvents: 5,
      maxShifts: 25,
      maxWorkers: 80,
      baseMatchingQuota: 5,
      featuresText: 'Kích hoạt Thuật toán Ghép đôi (Matching Engine)\nHiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động\nBắn thông báo đẩy (FCM Push) đến ứng viên phù hợp\nTự động kích hoạt hỗ trợ khi có biến động ca làm',
    });
  };

  const handleCloseModal = () => {
    setEditingPlan(null);
    setIsCreating(false);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.planName.trim()) {
      showToast('Vui lòng nhập tên gói cước', 'warning');
      return;
    }

    const featureList = formData.featuresText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);

    const payload = {
      planName: formData.planName.trim(),
      planType: formData.planType,
      price: Number(formData.price),
      durationDays: Number(formData.durationDays),
      maxEvents: Number(formData.maxEvents),
      maxShifts: Number(formData.maxShifts),
      maxWorkers: Number(formData.maxWorkers),
      baseMatchingQuota: Number(formData.baseMatchingQuota),
      features: featureList,
    };

    setSaving(true);
    try {
      if (isCreating) {
        await apiClient.post('/v1/admin/plans', payload);
        showToast('🎉 Tạo gói cước mới thành công!', 'success');
      } else {
        await apiClient.put(`/v1/admin/plans/${editingPlan.planId}`, payload);
        showToast('✓ Cập nhật cấu hình gói cước thành công!', 'success');
      }
      handleCloseModal();
      await loadPlans();
    } catch (err) {
      console.error('Lỗi lưu gói cước:', err);
      showToast(err.response?.data?.message || 'Có lỗi khi lưu thông tin gói cước', 'error');
    } finally {
      setSaving(false);
    }
  };

  const formatCurrency = (val) => {
    return new Intl.NumberFormat('vi-VN').format(val || 0);
  };

  return (
    <div className="admin-plans-container">
      {/* Header Bar */}
      <div className="admin-plans-header">
        <div>
          <h2 className="admin-plans-title">Quản Trị Danh Mục Gói Dịch Vụ Sự Kiện</h2>
          <p className="admin-plans-subtitle">
            Cấu hình hạn mức số sự kiện, ca làm, nhân sự và đơn giá cho Ban tổ chức (EO). Dữ liệu được đồng bộ trực tiếp với Cổng thanh toán và Hệ thống Matching.
          </p>
        </div>
        <button type="button" className="btn-add-plan" onClick={handleOpenCreate}>
          + Thêm Gói Cước Mới
        </button>
      </div>

      {/* Grid of Plans */}
      {loading ? (
        <div className="plans-loading">Đang tải danh mục gói cước từ CSDL...</div>
      ) : (
        <div className="admin-plans-grid">
          {plans.map((p) => {
            const feats = parseFeaturesList(p.features);
            const isFree = Number(p.price) === 0;
            return (
              <div key={p.planId} className="admin-plan-card">
                <div className="plan-card-header">
                  <div className="plan-type-badge">{p.planType}</div>
                  <h3 className="plan-name">{p.planName}</h3>
                  <div className="plan-price-row">
                    <span className="plan-price">{formatCurrency(p.price)}</span>
                    <span className="plan-currency">đ</span>
                    <span className="plan-period">/ {p.durationDays} ngày</span>
                  </div>
                </div>

                <div className="plan-quotas-box">
                  <div className="quota-row">
                    <span>Hạn mức sự kiện:</span>
                    <strong>{p.maxEvents} sự kiện</strong>
                  </div>
                  <div className="quota-row">
                    <span>Hạn mức ca làm:</span>
                    <strong>{p.maxShifts} ca</strong>
                  </div>
                  <div className="quota-row">
                    <span>Hạn mức nhân sự:</span>
                    <strong>{p.maxWorkers} người</strong>
                  </div>
                  <div className="quota-row">
                    <span>Matching Radar:</span>
                    <strong style={{ color: '#2563eb' }}>{p.baseMatchingQuota || p.maxEvents} lượt</strong>
                  </div>
                </div>

                <div className="plan-features-box">
                  <div className="features-title">Tính năng gói:</div>
                  <ul className="features-list">
                    {feats.map((f, i) => (
                      <li key={i}>✓ {f}</li>
                    ))}
                  </ul>
                </div>

                <div className="plan-card-actions">
                  <button
                    type="button"
                    className="btn-edit-plan"
                    onClick={() => handleOpenEdit(p)}
                  >
                    Chỉnh sửa gói
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Chỉnh Sửa / Tạo Mới */}
      {(editingPlan || isCreating) && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{isCreating ? 'Thêm Gói Cước Mới' : `Chỉnh Sửa Gói: ${editingPlan?.planName}`}</h3>
              <button type="button" className="btn-close" onClick={handleCloseModal}>✕</button>
            </div>

            <form onSubmit={handleSubmit} className="modal-form">
              <div className="form-group">
                <label>Tên gói cước <span className="req">*</span></label>
                <input
                  type="text"
                  value={formData.planName}
                  onChange={(e) => setFormData({ ...formData, planName: e.target.value })}
                  placeholder="Ví dụ: Gói Tiêu Chuẩn (Standard)"
                  required
                />
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Mã loại gói (PlanType) <span className="req">*</span></label>
                  <select
                    value={formData.planType}
                    onChange={(e) => setFormData({ ...formData, planType: e.target.value })}
                  >
                    <option value="FREE_TRIAL">FREE_TRIAL (Dùng thử miễn phí)</option>
                    <option value="INVITE_ONLY">INVITE_ONLY (Mời đích danh)</option>
                    <option value="MATCHING_PLUS">MATCHING_PLUS (Tiêu chuẩn)</option>
                    <option value="ENTERPRISE">ENTERPRISE (Doanh nghiệp Pro)</option>
                  </select>
                </div>

                <div className="form-group">
                  <label>Đơn giá (VNĐ) <span className="req">*</span></label>
                  <input
                    type="number"
                    min="0"
                    step="1000"
                    value={formData.price}
                    onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Thời hạn hiệu lực (Ngày) <span className="req">*</span></label>
                  <input
                    type="number"
                    min="1"
                    value={formData.durationDays}
                    onChange={(e) => setFormData({ ...formData, durationDays: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label>Hạn mức sự kiện <span className="req">*</span></label>
                  <input
                    type="number"
                    min="1"
                    value={formData.maxEvents}
                    onChange={(e) => setFormData({ ...formData, maxEvents: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label>Tối đa số ca làm việc</label>
                  <input
                    type="number"
                    min="0"
                    value={formData.maxShifts}
                    onChange={(e) => setFormData({ ...formData, maxShifts: e.target.value })}
                  />
                </div>

                <div className="form-group">
                  <label>Tối đa số lượng nhân sự</label>
                  <input
                    type="number"
                    min="0"
                    value={formData.maxWorkers}
                    onChange={(e) => setFormData({ ...formData, maxWorkers: e.target.value })}
                  />
                </div>
              </div>

              <div className="form-group">
                <label>Danh sách tính năng nổi bật (Mỗi dòng là một tính năng)</label>
                <textarea
                  rows="5"
                  value={formData.featuresText}
                  onChange={(e) => setFormData({ ...formData, featuresText: e.target.value })}
                  placeholder="Kích hoạt Thuật toán Ghép đôi (Matching Engine)&#10;Hiển thị ca làm việc ưu tiên trên Bản đồ Job Radar di động&#10;Bắn thông báo đẩy (FCM Push) đến ứng viên phù hợp"
                />
              </div>

              <div className="modal-actions">
                <button type="button" className="btn-cancel" onClick={handleCloseModal}>
                  Hủy bỏ
                </button>
                <button type="submit" className="btn-save" disabled={saving}>
                  {saving ? 'Đang lưu...' : 'Lưu Cấu Hình Gói'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
