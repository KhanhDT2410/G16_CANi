import { useRevenueDashboard } from '../hooks/useRevenueDashboard';
import RevenueFilterBar from './RevenueFilterBar';
import RevenueMetricsGrid from './RevenueMetricsGrid';
import RevenueChartSection from './RevenueChartSection';
import VnPayHealthCard from './VnPayHealthCard';
import TransactionTable from './TransactionTable';
import EoSubscriptionDetailModal from './EoSubscriptionDetailModal';
import '../revenueDashboard.css';

/**
 * W-34 Revenue Dashboard - Báo Cáo Doanh Thu Gói Dịch Vụ VNPay (UC-108)
 *
 * Màn hình quản trị doanh thu bán gói sự kiện (Invite Mode & Matching Mode).
 * Phân định kiến trúc: Lương Worker thanh toán trực tiếp Non-Escrow ngoài hệ thống.
 * Doanh thu nền tảng CANi đến duy nhất từ việc bán gói tính năng qua VNPay.
 */
const RevenueDashboard = () => {
  const {
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
    transactions,
    vnpayHealth,
    // Drill-down W-46
    eoSubscriptionDetail,
    isEoModalOpen,
    isEoDetailLoading,
    handleOpenEoDetail,
    handleCloseEoDetail,
    handleManualExtendSubscription,
    // Actions
    handleReconcileTransaction,
    handleExportCsv,
    refreshData,
  } = useRevenueDashboard();

  return (
    <div className="rev-dashboard-container animate-fade-in">
      {/* 1. Header Màn hình W-34 */}
      <div className="rev-header">
        <div className="rev-header-left">
          <div className="rev-badge-group">
            <span className="rev-header-badge">W-34 • REVENUE DASHBOARD</span>
            <span className="rev-header-subbadge">Hệ Thống Phân Tích VNPay (UC-108)</span>
          </div>
          <h1 className="rev-header-title">Báo Cáo Doanh Thu Gói Dịch Vụ</h1>
          <p className="rev-header-desc">
            Phân tích số liệu bán gói sự kiện Invite Mode (50k) và Matching Mode (80k, Combo) từ đối tác Event Organizer.
          </p>
          <div className="rev-header-policy">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>Quy chế tài chính: Lương Worker thanh toán Non-Escrow trực tiếp. Doanh thu CANi 100% từ gói dịch vụ VNPay.</span>
          </div>
        </div>

        <div className="rev-header-actions">
          <button
            type="button"
            className="rev-btn-secondary"
            onClick={refreshData}
            title="Đồng bộ lại chỉ số mới nhất từ VNPay"
          >
            🔄 Đồng Bộ Dữ Liệu
          </button>
          <button
            type="button"
            className="rev-btn-primary"
            onClick={handleExportCsv}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            Xuất Báo Cáo
          </button>
        </div>
      </div>

      {/* 2. Lưới 4 Thẻ KPI Doanh Thu & Tăng Trưởng */}
      <RevenueMetricsGrid metrics={metrics} loading={loading} />

      {/* 3. Biểu đồ Period-over-Period & Cơ Cấu Doanh Thu */}
      <RevenueChartSection chartData={chartData} metrics={metrics} />

      {/* 4. Thẻ Giám Sát Sức Khỏe VNPay & Cảnh Báo Giao Dịch Lỗi */}
      <VnPayHealthCard
        vnpayHealth={vnpayHealth}
        transactions={transactions}
        onReconcile={handleReconcileTransaction}
      />

      {/* 5. Thanh Điều Khiển Bộ Lọc & Tìm Kiếm */}
      <RevenueFilterBar
        selectedPeriod={selectedPeriod}
        onPeriodChange={setSelectedPeriod}
        packageFilter={packageFilter}
        onPackageFilterChange={setPackageFilter}
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        onExportCsv={handleExportCsv}
        onRefresh={refreshData}
        totalResults={transactions.length}
      />

      {/* 6. Bảng Chi Tiết Giao Dịch Nạp Gói VNPay */}
      <TransactionTable
        transactions={transactions}
        loading={loading}
        onOpenEoDetail={handleOpenEoDetail}
        onReconcile={handleReconcileTransaction}
      />

      {/* 7. Modal Khoan Sâu Gói Thuê Bao EO (W-46 / UC-12, UC-114) */}
      <EoSubscriptionDetailModal
        isOpen={isEoModalOpen}
        onClose={handleCloseEoDetail}
        eoDetail={eoSubscriptionDetail}
        loading={isEoDetailLoading}
        onManualExtend={handleManualExtendSubscription}
      />
    </div>
  );
};

export default RevenueDashboard;
