import { useState, useEffect, useCallback } from 'react';
import { adminDashboardApi } from '../api/adminDashboardApi';
import { showToast } from '../../../components/common/Toast';

const MOCK_FALLBACK_METRICS = {
  users: {
    totalUsers: 142,
    workerCount: 108,
    eventOrganizerCount: 28,
    customerCareCount: 4,
    adminCount: 2,
    lockedUsersCount: 3,
  },
  verifications: {
    pendingWorkerEkycCount: 6,
    pendingEoEkycCount: 2,
    pendingSkillVerificationsCount: 8,
    totalPendingCount: 16,
  },
  operations: {
    totalEventsCount: 35,
    activeEventsCount: 14,
    totalShiftsCount: 96,
    activeShiftsCount: 38,
  },
};

const MOCK_FALLBACK_ACTIVITIES = [
  {
    id: 'act-01',
    userId: 'admin-01',
    adminName: 'Quản Trị Viên Hệ Thống',
    adminEmail: 'admin@cani.vn',
    action: 'UPDATE_USER_STATUS',
    actionDescription: 'Cập nhật trạng thái người dùng',
    entityType: 'USER',
    entityId: 'e4a2c58e-7164-4bf8-a0d7-2f3b9c64811a',
    newValues: '{"status":"LOCKED","reason":"Vi phạm nội quy hủy ca 3 lần liên tiếp"}',
    createdAt: new Date().toISOString(),
  },
  {
    id: 'act-02',
    userId: 'admin-01',
    adminName: 'Quản Trị Viên Hệ Thống',
    adminEmail: 'admin@cani.vn',
    action: 'APPROVE_SKILL_VERIFICATION',
    actionDescription: 'Thẩm định chứng chỉ kỹ năng',
    entityType: 'SKILL_VERIFICATION',
    entityId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
    newValues: '{"status":"APPROVED","spAwarded":25}',
    createdAt: new Date(Date.now() - 3600000).toISOString(),
  },
  {
    id: 'act-03',
    userId: null,
    adminName: 'Hệ thống',
    adminEmail: null,
    action: 'REVIEW_WORKER_EKYC',
    actionDescription: 'Thẩm định hồ sơ eKYC Worker',
    entityType: 'WORKER_EKYC',
    entityId: 'b2c3d4e5-f6a7-b8c9-0d1e-2f3a4b5c6d7e',
    newValues: '{"status":"APPROVED","confidence":96.8}',
    createdAt: new Date(Date.now() - 7200000).toISOString(),
  },
];

/**
 * Hook quản lý trạng thái và dữ liệu cho Admin Dashboard (UC-86)
 */
export const useAdminDashboard = () => {
  const [metrics, setMetrics] = useState(null);
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);
  const [isDemoFallback, setIsDemoFallback] = useState(false);

  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      const [metricsData, activitiesData] = await Promise.all([
        adminDashboardApi.getMetrics(),
        adminDashboardApi.getRecentActivities(),
      ]);

      if (metricsData) {
        setMetrics(metricsData);
        setActivities(activitiesData || []);
        setIsDemoFallback(false);
        if (isRefresh) {
          showToast('Đã làm mới dữ liệu Bảng điều khiển từ Backend!', 'success');
        }
      } else {
        // Fallback demo data
        setMetrics(MOCK_FALLBACK_METRICS);
        setActivities(MOCK_FALLBACK_ACTIVITIES);
        setIsDemoFallback(true);
      }
    } catch (err) {
      console.warn('Backend chưa sẵn sàng hoặc chưa đăng nhập Admin, sử dụng dữ liệu Demo mẫu:', err);
      // Fallback demo data để người dùng test giao diện mượt mà
      setMetrics(MOCK_FALLBACK_METRICS);
      setActivities(MOCK_FALLBACK_ACTIVITIES);
      setIsDemoFallback(true);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleRefresh = () => {
    loadData(true);
  };

  return {
    metrics,
    activities,
    loading,
    refreshing,
    error,
    isDemoFallback,
    refresh: handleRefresh,
  };
};
