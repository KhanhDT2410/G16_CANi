import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { accountAppealApi } from '../api/accountAppealApi';
import { ACCOUNT_APPEAL_STATUS_CONFIG } from '../types/accountAppeal.constants';
import AdminAccountAppealReviewModal from './AdminAccountAppealReviewModal';
import './AdminAccountAppeals.css';

const STATUS_TABS = [
  { key: '', label: 'Tất cả', dotClass: 'dot-all' },
  { key: 'SUBMITTED', label: 'Chờ tiếp nhận', dotClass: 'dot-submitted' },
  { key: 'UNDER_REVIEW', label: 'Đang thẩm định', dotClass: 'dot-under-review' },
  { key: 'APPROVED', label: 'Đã chấp thuận', dotClass: 'dot-approved' },
  { key: 'REJECTED', label: 'Đã từ chối', dotClass: 'dot-rejected' },
];

export default function AdminAccountAppealsTable() {
  const [appeals, setAppeals] = useState([]);
  const [loading, setLoading] = useState(false);
  const [statusFilter, setStatusFilter] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedAppeal, setSelectedAppeal] = useState(null);

  const fetchAppeals = useCallback(async () => {
    setLoading(true);
    try {
      const data = await accountAppealApi.getAdminAppeals({ status: statusFilter });
      setAppeals(data?.content || []);
    } catch (err) {
      console.warn('Lỗi khi tải danh sách giải trình tài khoản:', err);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchAppeals();
  }, [fetchAppeals]);

  const handleReviewSuccess = async (id, reviewData) => {
    await accountAppealApi.reviewAppeal(id, reviewData);
    fetchAppeals();
    window.dispatchEvent(new CustomEvent('cani_appeal_reviewed'));
  };

  const filteredAppeals = useMemo(() => {
    if (!searchTerm.trim()) return appeals;
    const term = searchTerm.toLowerCase().trim();
    return appeals.filter(
      (item) =>
        item.email?.toLowerCase().includes(term) ||
        item.fullName?.toLowerCase().includes(term) ||
        item.companyName?.toLowerCase().includes(term) ||
        item.lockReasonSnapshot?.toLowerCase().includes(term) ||
        item.appealContent?.toLowerCase().includes(term)
    );
  }, [appeals, searchTerm]);

  return (
    <div className="admin-appeals-container">
      {/* Thanh công cụ: Bộ lọc trạng thái & Tìm kiếm + Nút Làm mới */}
      <div className="admin-appeals-toolbar">
        <div className="admin-status-tabs">
          {STATUS_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              className={`status-tab-btn ${statusFilter === tab.key ? 'active' : ''}`}
              onClick={() => setStatusFilter(tab.key)}
            >
              <span className={`status-dot ${tab.dotClass}`} />
              <span>{tab.label}</span>
            </button>
          ))}
        </div>

        <div className="admin-appeals-right-actions">
          <div className="admin-appeals-search-box">
            <svg
              className="admin-appeals-search-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              className="admin-appeals-search-input"
              placeholder="Tìm theo email, tên, lý do..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <button
            type="button"
            className="btn-appeals-refresh"
            onClick={fetchAppeals}
            disabled={loading}
            title="Làm mới danh sách"
          >
            <svg
              className={`refresh-icon ${loading ? 'spinning' : ''}`}
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <polyline points="23 4 23 10 17 10" />
              <polyline points="1 20 1 14 7 14" />
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
            </svg>
            <span>{loading ? 'Đang tải...' : 'Làm mới'}</span>
          </button>
        </div>
      </div>

      {/* Danh sách bảng đơn giải trình */}
      <div className="admin-appeals-table-card">
        {loading ? (
          <div className="admin-appeals-loading">
            <div className="cani-spinner" />
            <span>Đang tải danh sách giải trình...</span>
          </div>
        ) : filteredAppeals.length === 0 ? (
          <div className="admin-appeals-empty">
            <svg
              className="admin-appeals-empty-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
            >
              <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5.586a1 1 0 0 1 .707.293l5.414 5.414a1 1 0 0 1 .293.707V19a2 2 0 0 1-2 2z" />
            </svg>
            <div className="admin-appeals-empty-title">Không có đơn giải trình nào phù hợp</div>
            <div className="admin-appeals-empty-sub">
              {searchTerm ? 'Hãy thử tìm kiếm với từ khóa khác' : 'Hiện chưa có yêu cầu khiếu nại hoặc mở khóa tài khoản'}
            </div>
          </div>
        ) : (
          <div className="admin-table-scroll">
            <table className="admin-appeals-table">
              <thead>
                <tr>
                  <th>Thời Gian</th>
                  <th>Đối Tượng</th>
                  <th>Lý Do Khóa Ban Đầu</th>
                  <th>Nội Dung Giải Trình</th>
                  <th>Trạng Thái</th>
                  <th style={{ textAlign: 'right' }}>Thao Tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredAppeals.map((item) => {
                  const cfg = ACCOUNT_APPEAL_STATUS_CONFIG[item.status] || ACCOUNT_APPEAL_STATUS_CONFIG.SUBMITTED;
                  const isEO = item.userRole === 'EVENT_ORGANIZER';
                  const isPending = item.status === 'SUBMITTED' || item.status === 'UNDER_REVIEW';

                  return (
                    <tr key={item.id}>
                      <td style={{ fontSize: '0.825rem', whiteSpace: 'nowrap' }}>
                        <div>{new Date(item.createdAt).toLocaleDateString('vi-VN')}</div>
                        <div style={{ color: '#94a3b8', fontSize: '0.75rem' }}>
                          {new Date(item.createdAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </td>

                      <td>
                        <div className="appeal-user-block">
                          <span className="appeal-user-name">
                            {item.companyName || item.fullName || 'Chưa đặt tên'}
                          </span>
                          <span className="appeal-user-email">{item.email}</span>
                          <span className={`appeal-role-badge ${isEO ? 'eo' : 'worker'}`}>
                            {isEO ? 'Nhà tổ chức (EO)' : 'Người lao động (Worker)'}
                          </span>
                        </div>
                      </td>

                      <td>
                        <div className="appeal-lock-snippet" title={item.lockReasonSnapshot || 'Vi phạm chính sách'}>
                          {item.lockReasonSnapshot || 'Vi phạm chính sách vận hành'}
                        </div>
                      </td>

                      <td>
                        <div className="appeal-content-snippet" title={item.appealContent}>
                          {item.appealContent}
                        </div>
                      </td>

                      <td>
                        <span
                          className="appeal-status-badge"
                          style={{
                            backgroundColor: cfg.bg,
                            color: cfg.color,
                            borderColor: `${cfg.color}33`,
                          }}
                        >
                          <span
                            style={{
                              width: '6px',
                              height: '6px',
                              borderRadius: '50%',
                              backgroundColor: cfg.color,
                              display: 'inline-block',
                            }}
                          />
                          {cfg.label}
                        </span>
                      </td>

                      <td style={{ textAlign: 'right' }}>
                        <button
                          type="button"
                          className={`btn-review-appeal ${isPending ? 'action-primary' : ''}`}
                          onClick={() => setSelectedAppeal(item)}
                        >
                          {item.status === 'SUBMITTED' ? 'Thẩm định ngay' : 'Xem chi tiết'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedAppeal && (
        <AdminAccountAppealReviewModal
          appeal={selectedAppeal}
          onClose={() => setSelectedAppeal(null)}
          onReviewSuccess={handleReviewSuccess}
        />
      )}
    </div>
  );
}
