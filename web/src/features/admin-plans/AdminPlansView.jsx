import React, { useState, useEffect, useCallback } from 'react';
import { apiClient } from '../../services/api';
import { showToast } from '../../components/common/Toast';
import { showConfirm } from '../../components/common/PromptModal';
import PlanCard from './components/PlanCard';
import PlanFormModal from './components/PlanFormModal';
import './AdminPlans.css';

export default function AdminPlansView() {
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editingPlan, setEditingPlan] = useState(null);
  const [isCreating, setIsCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

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

  const handleOpenEdit = (plan) => {
    setIsCreating(false);
    setEditingPlan(plan);
  };

  const handleOpenCreate = () => {
    setIsCreating(true);
    setEditingPlan(null);
  };

  const handleCloseModal = () => {
    setEditingPlan(null);
    setIsCreating(false);
  };

  const handleSavePlan = async (payload) => {
    setSaving(true);
    try {
      if (isCreating) {
        await apiClient.post('/v1/admin/plans', payload);
        showToast('Đã tạo gói dịch vụ mới thành công', 'success');
      } else {
        await apiClient.put(`/v1/admin/plans/${editingPlan.planId}`, payload);
        showToast('Cập nhật cấu hình và giá gói thành công', 'success');
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

  const handleDeletePlan = async (plan) => {
    const confirmed = await showConfirm({
      title: 'Xóa gói dịch vụ',
      description: `Bạn có chắc chắn muốn xóa gói "${plan.planName}" không? Lưu ý: Không thể xóa nếu đã có Ban tổ chức đăng ký sử dụng.`,
      type: 'danger',
      confirmText: 'Xóa gói',
    });
    if (!confirmed) {
      return;
    }

    setDeletingId(plan.planId);
    try {
      await apiClient.delete(`/v1/admin/plans/${plan.planId}`);
      showToast(`Đã xóa gói "${plan.planName}" thành công`, 'success');
      await loadPlans();
    } catch (err) {
      console.error('Lỗi xóa gói cước:', err);
      showToast(err.response?.data?.message || 'Không thể xóa gói cước này', 'error');
    } finally {
      setDeletingId(null);
    }
  };

  const freeCount = plans.filter((p) => Number(p.price) === 0).length;
  const paidCount = plans.filter((p) => Number(p.price) > 0).length;

  return (
    <div className="admin-plans-container">
      {/* Sleek Toolbar: Statistics on left, Action buttons on right */}
      <div className="plans-toolbar">
        <div className="plans-stats-group">
          <div className="stat-pill">
            <span>Tổng số gói:</span> <strong>{plans.length}</strong>
          </div>
          <div className="stat-pill">
            <span>Gói trả phí:</span> <strong style={{ color: '#2563eb' }}>{paidCount}</strong>
          </div>
          <div className="stat-pill">
            <span>Gói dùng thử / Miễn phí:</span> <strong style={{ color: '#16a34a' }}>{freeCount}</strong>
          </div>
        </div>

        <div className="plans-actions-group">
          <button
            type="button"
            className="btn-refresh-plans"
            onClick={loadPlans}
            disabled={loading}
          >
            Làm Mới
          </button>
          <button
            type="button"
            className="btn-add-plan"
            onClick={handleOpenCreate}
          >
            Thêm Gói Cước Mới
          </button>
        </div>
      </div>

      {/* Grid of Plans */}
      {loading ? (
        <div className="plans-loading">
          <div className="plans-spinner" />
          <span>Đang tải danh mục gói cước từ CSDL...</span>
        </div>
      ) : plans.length === 0 ? (
        <div className="plans-empty-state">
          <h3>Chưa có gói cước nào trong cơ sở dữ liệu</h3>
          <p>Nhấn vào nút "Thêm Gói Cước Mới" để thiết lập gói dịch vụ đầu tiên.</p>
        </div>
      ) : (
        <div className="admin-plans-grid">
          {plans.map((p) => (
            <PlanCard
              key={p.planId}
              plan={p}
              onEdit={handleOpenEdit}
              onDelete={handleDeletePlan}
              isDeleting={deletingId === p.planId}
            />
          ))}
        </div>
      )}

      {/* Modal Form */}
      <PlanFormModal
        isOpen={isCreating || editingPlan !== null}
        isCreating={isCreating}
        plan={editingPlan}
        onClose={handleCloseModal}
        onSave={handleSavePlan}
        saving={saving}
      />
    </div>
  );
}
