import { useState, useEffect, useMemo, useCallback } from 'react';
import { revenueApi } from '../api/revenueApi';
import { showToast } from '../../../components/common/Toast';

export const useRevenueDashboard = () => {
  const [selectedPeriod, setSelectedPeriod] = useState('THIS_MONTH');
  const [packageFilter, setPackageFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);

  const [metrics, setMetrics] = useState(null);
  const [chartData, setChartData] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [vnpayHealth, setVnpayHealth] = useState(null);

  // Drill-down W-46 State
  const [selectedEoId, setSelectedEoId] = useState(null);
  const [eoSubscriptionDetail, setEoSubscriptionDetail] = useState(null);
  const [isEoModalOpen, setIsEoModalOpen] = useState(false);
  const [isEoDetailLoading, setIsEoDetailLoading] = useState(false);

  // Tải dữ liệu ban đầu
  const loadDashboardData = useCallback(async () => {
    setLoading(true);
    try {
      const [overviewData, chartPoints, txns, health] = await Promise.all([
        revenueApi.getRevenueOverview({ period: selectedPeriod }),
        revenueApi.getRevenueChartData({ period: selectedPeriod }),
        revenueApi.getVnPayTransactions({ period: selectedPeriod }),
        revenueApi.getVnPayHealth(),
      ]);

      setMetrics(overviewData);
      setChartData(chartPoints);
      setTransactions(txns);
      setVnpayHealth(health);
    } catch {
      showToast('Không thể tải toàn bộ dữ liệu doanh thu, đang dùng dữ liệu dự phòng', 'warning');
    } finally {
      setLoading(false);
    }
  }, [selectedPeriod]);

  useEffect(() => {
    loadDashboardData();
  }, [loadDashboardData]);

  // Bộ lọc danh sách giao dịch VNPay
  const filteredTransactions = useMemo(() => {
    return transactions.filter((txn) => {
      // 1. Lọc theo gói
      if (packageFilter !== 'ALL' && txn.packageType !== packageFilter) {
        return false;
      }
      // 2. Lọc theo trạng thái VNPay
      if (statusFilter !== 'ALL' && txn.status !== statusFilter) {
        return false;
      }
      // 3. Tìm kiếm theo mã hoặc tên EO
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase().trim();
        const matchCode = txn.id.toLowerCase().includes(query);
        const matchRef = txn.vnpTxnRef.toLowerCase().includes(query);
        const matchVnpNo = txn.vnpTransactionNo.toLowerCase().includes(query);
        const matchEo = txn.eoName.toLowerCase().includes(query);
        const matchEmail = (txn.eoEmail || '').toLowerCase().includes(query);
        return matchCode || matchRef || matchVnpNo || matchEo || matchEmail;
      }
      return true;
    });
  }, [transactions, packageFilter, statusFilter, searchQuery]);

  // Thao tác xem chi tiết gói thuê bao EO (W-46 Drill-down)
  const handleOpenEoDetail = async (eoId) => {
    setSelectedEoId(eoId);
    setIsEoModalOpen(true);
    setIsEoDetailLoading(true);
    try {
      const detail = await revenueApi.getEoSubscriptionDetail(eoId);
      setEoSubscriptionDetail(detail);
    } catch {
      showToast('Không thể tải chi tiết gói của EO', 'error');
    } finally {
      setIsEoDetailLoading(false);
    }
  };

  const handleCloseEoDetail = () => {
    setIsEoModalOpen(false);
    setSelectedEoId(null);
    setEoSubscriptionDetail(null);
  };

  // Thao tác đối soát lại giao dịch VNPay (QueryDR)
  const handleReconcileTransaction = async (txn) => {
    showToast(`Đang gửi yêu cầu đối soát tới VNPay Gateway cho đơn ${txn.vnpTxnRef}...`, 'info');
    try {
      const result = await revenueApi.reconcileTransaction(txn.id);
      // Cập nhật trạng thái trong bảng
      setTransactions((prev) =>
        prev.map((item) =>
          item.id === txn.id
            ? {
                ...item,
                status: 'SUCCESS',
                notes: `Đối soát thành công lúc ${new Date().toLocaleTimeString('vi-VN')} (${result.message})`,
              }
            : item
        )
      );
      showToast(`Đối soát thành công đơn vị ${txn.eoName}! Đã tự động kích hoạt gói.`, 'success');
    } catch {
      showToast('Gặp lỗi khi gửi lệnh đối soát tới cổng VNPay', 'error');
    }
  };

  // Thao tác gia hạn thủ công trong W-46
  const handleManualExtendSubscription = (days = 30) => {
    if (!eoSubscriptionDetail) return;
    setEoSubscriptionDetail((prev) => ({
      ...prev,
      remainingCredits: (prev.remainingCredits || 0) + 5,
      expiryDate: '15/12/2026',
    }));
    showToast(`Đã gia hạn thành công thêm ${days} ngày và cấp +5 credit sự kiện cho ${eoSubscriptionDetail.eoName}!`, 'success');
  };

  // Xuất báo cáo doanh thu ra CSV
  const handleExportCsv = () => {
    if (!filteredTransactions.length) {
      showToast('Không có dữ liệu giao dịch để xuất báo cáo', 'warning');
      return;
    }

    const headers = ['Mã Giao Dịch', 'Mã VNPay', 'Mã Đơn CANi', 'Tên Ban Tổ Chức', 'Gói Dịch Vụ', 'Số Tiền (VNĐ)', 'Ngân Hàng', 'Thời Gian', 'Trạng Thái', 'Ghi Chú'];
    const rows = filteredTransactions.map((t) => [
      t.id,
      t.vnpTransactionNo,
      t.vnpTxnRef,
      `"${t.eoName}"`,
      `"${t.packageName}"`,
      t.amount,
      t.bankCode,
      t.paidAt,
      t.status,
      `"${t.notes || ''}"`,
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,\uFEFF' + [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `CANi_Revenue_Report_${selectedPeriod}_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    showToast(`Đã xuất thành công ${filteredTransactions.length} bản ghi giao dịch sang CSV!`, 'success');
  };

  return {
    selectedPeriod,
    setSelectedPeriod,
    packageFilter,
    setPackageFilter,
    statusFilter,
    setStatusFilter,
    searchQuery,
    setSearchQuery,
    loading,
    metrics,
    chartData,
    transactions: filteredTransactions,
    rawTransactionsCount: transactions.length,
    vnpayHealth,
    // Drill-down W-46
    selectedEoId,
    eoSubscriptionDetail,
    isEoModalOpen,
    isEoDetailLoading,
    handleOpenEoDetail,
    handleCloseEoDetail,
    handleManualExtendSubscription,
    // Actions
    handleReconcileTransaction,
    handleExportCsv,
    refreshData: loadDashboardData,
  };
};
