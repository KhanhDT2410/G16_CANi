import { useState, useEffect, useCallback } from 'react';
import { revenueApi } from '../api/revenueApi';
import { showToast } from '../../../components/common/Toast';

export const useRevenueDashboard = () => {
  const [summary, setSummary] = useState(null);
  const [transactions, setTransactions] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState('');
  const [packageTypeFilter, setPackageTypeFilter] = useState('');

  const [dateFilterDays, setDateFilterDays] = useState(30); // 7, 30, 90
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Tính toán thời điểm from / to
  const calculateDateRange = useCallback(() => {
    const to = new Date().toISOString();
    const fromDate = new Date();
    fromDate.setDate(fromDate.getDate() - dateFilterDays);
    const from = fromDate.toISOString();
    return { from, to };
  }, [dateFilterDays]);

  const fetchSummary = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const { from, to } = calculateDateRange();
      const data = await revenueApi.getRevenueSummary(from, to);
      setSummary(data);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải tổng quan doanh thu từ máy chủ';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, [calculateDateRange]);

  const fetchTransactions = useCallback(async () => {
    try {
      const { from, to } = calculateDateRange();
      const pageData = await revenueApi.getRevenueTransactions({
        from,
        to,
        status: statusFilter || undefined,
        packageType: packageTypeFilter || undefined,
        page: currentPage,
        size: pageSize,
      });

      setTransactions(pageData?.content || []);
      setTotalElements(pageData?.totalElements || 0);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách giao dịch';
      showToast(msg, 'error');
    }
  }, [calculateDateRange, statusFilter, packageTypeFilter, currentPage, pageSize]);

  useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  useEffect(() => {
    fetchTransactions();
  }, [fetchTransactions]);

  const handleRefresh = () => {
    fetchSummary();
    fetchTransactions();
    showToast('Đã làm mới dữ liệu doanh thu', 'success');
  };

  return {
    summary,
    transactions,
    totalElements,
    currentPage,
    setCurrentPage,
    pageSize,
    statusFilter,
    setStatusFilter,
    packageTypeFilter,
    setPackageTypeFilter,
    dateFilterDays,
    setDateFilterDays,
    loading,
    error,
    handleRefresh,
  };
};

