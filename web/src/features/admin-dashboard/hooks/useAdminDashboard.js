import { useState, useEffect, useCallback } from 'react';
import { adminDashboardApi } from '../api/adminDashboardApi';
import { revenueApi } from '../../revenue-dashboard/api/revenueApi';
import { showToast } from '../../../components/common/Toast';

const DEFAULT_REVENUE_METRICS = {
  totalPackageRevenue: 0,
  totalPackageSales: 0,
  totalPaymentVolume: 0,
  totalPaymentTransactions: 0,
  combinedVolume: 0,
  growthRate: 0,
};

/**
 * Hook quản lý trạng thái và dữ liệu thực tế cho Admin Dashboard (UC-86).
 * 100% dữ liệu truy vấn trực tiếp từ cơ sở dữ liệu qua REST API, không dùng mock fallback.
 */
export const useAdminDashboard = () => {
  const [metrics, setMetrics] = useState(null);
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const loadData = useCallback(async (isRefresh = false) => {
    if (isRefresh) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    setError(null);

    try {
      const [metricsData, activitiesData, revenueData] = await Promise.all([
        adminDashboardApi.getMetrics(),
        adminDashboardApi.getRecentActivities(),
        revenueApi.getRevenueSummary().catch((err) => {
          console.warn('Chưa có số liệu doanh thu từ API:', err?.response?.status || err.message);
          return null;
        }),
      ]);

      if (metricsData) {
        if (revenueData?.currentPeriod) {
          metricsData.revenue = {
            totalPackageRevenue: revenueData.currentPeriod.totalPackageRevenue ?? 0,
            totalPackageSales: revenueData.currentPeriod.totalPackageSales ?? 0,
            totalPaymentVolume: revenueData.currentPeriod.totalPaymentVolume ?? 0,
            totalPaymentTransactions: revenueData.currentPeriod.totalPaymentTransactions ?? 0,
            combinedVolume: revenueData.currentPeriod.combinedVolume ?? 0,
            growthRate: revenueData.growth?.packageRevenueGrowthRate ?? 0,
          };
        } else {
          metricsData.revenue = DEFAULT_REVENUE_METRICS;
        }

        setMetrics(metricsData);
        setActivities(activitiesData || []);
        if (isRefresh) {
          showToast('Đã làm mới dữ liệu Bảng điều khiển từ Backend!', 'success');
        }
      } else {
        throw new Error('Không nhận được dữ liệu hợp lệ từ máy chủ');
      }
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message || 'Lỗi khi tải dữ liệu Bảng điều khiển';
      console.error('Lỗi tải dữ liệu Bảng điều khiển:', err);
      setError(errorMsg);
      setMetrics(null);
      setActivities([]);
      if (isRefresh) {
        showToast(errorMsg, 'error');
      }
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
    refresh: handleRefresh,
  };
};
