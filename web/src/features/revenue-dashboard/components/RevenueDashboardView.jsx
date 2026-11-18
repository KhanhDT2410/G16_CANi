import { useRevenueDashboard } from '../hooks/useRevenueDashboard';
import { RevenueMetricsCards } from './RevenueMetricsCards';
import { RevenuePackageBreakdown } from './RevenuePackageBreakdown';
import { RevenueVnPaySummary } from './RevenueVnPaySummary';
import { RevenueFlaggedTable } from './RevenueFlaggedTable';
import { RevenueTransactionsTable } from './RevenueTransactionsTable';
import './RevenueDashboard.css';

export const RevenueDashboardView = () => {
  const {
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
  } = useRevenueDashboard();

  if (loading && !summary) {
    return (
      <div className="rev-loading-box">
        <div className="rev-spinner" />
        <p>Đang tổng hợp dữ liệu doanh thu và đối soát từ máy chủ...</p>
      </div>
    );
  }

  if (error && !summary) {
    return (
      <div className="rev-error-box">
        <h3>Không thể tải dữ liệu doanh thu</h3>
        <p>{error}</p>
        <button className="rev-btn-refresh" onClick={handleRefresh}>
          Thử Lại
        </button>
      </div>
    );
  }

  return (
    <div className="rev-container">
      {/* Top Filter Bar */}
      <div className="rev-top-bar">
        <div>
          <h2 className="rev-page-title">Báo Cáo Doanh Thu & Chỉ Số Tài Chính</h2>
          <p className="rev-page-subtitle">
            Giám sát doanh số gói dịch vụ, khối lượng đối soát thanh toán trực tiếp và các giao dịch cổng VNPay
          </p>
        </div>

        <div className="rev-date-selector">
          <span className="rev-date-label">Khoảng thời gian:</span>
          <button
            className={`rev-date-pill ${dateFilterDays === 7 ? 'active' : ''}`}
            onClick={() => setDateFilterDays(7)}
          >
            7 ngày qua
          </button>
          <button
            className={`rev-date-pill ${dateFilterDays === 30 ? 'active' : ''}`}
            onClick={() => setDateFilterDays(30)}
          >
            30 ngày qua
          </button>
          <button
            className={`rev-date-pill ${dateFilterDays === 90 ? 'active' : ''}`}
            onClick={() => setDateFilterDays(90)}
          >
            Quý này (90 ngày)
          </button>
          <button className="rev-btn-refresh-clean" title="Làm mới" onClick={handleRefresh}>
            Làm mới
          </button>
        </div>
      </div>

      {/* 4 Thẻ KPI */}
      <RevenueMetricsCards
        currentPeriod={summary?.currentPeriod}
        growth={summary?.growth}
      />

      {/* Phân loại theo gói & hình thức sự kiện */}
      <RevenuePackageBreakdown
        packageTypeBreakdown={summary?.packageTypeBreakdown}
        eventModeBreakdown={summary?.eventModeBreakdown}
      />

      {/* Tóm tắt giao dịch VNPay */}
      <RevenueVnPaySummary vnpaySummary={summary?.vnpaySummary} />

      {/* Giao dịch cần theo dõi (Disputed / Unverified) */}
      <RevenueFlaggedTable flaggedTransactions={summary?.flaggedTransactions} />

      {/* Bảng giao dịch chi tiết */}
      <RevenueTransactionsTable
        transactions={transactions}
        totalElements={totalElements}
        currentPage={currentPage}
        setCurrentPage={setCurrentPage}
        pageSize={pageSize}
        statusFilter={statusFilter}
        setStatusFilter={setStatusFilter}
        packageTypeFilter={packageTypeFilter}
        setPackageTypeFilter={setPackageTypeFilter}
      />
    </div>
  );
};

