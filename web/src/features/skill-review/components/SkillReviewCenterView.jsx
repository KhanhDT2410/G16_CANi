import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { skillReviewApi } from '../api/skillReviewApi';
import SkillReviewModal from './SkillReviewModal';
import { showToast } from '../../../components/common/Toast';
import './SkillReview.css';

const STATUS_LABELS = {
  PENDING_REVIEW: 'Chờ duyệt thủ công',
  VERIFICATION_FAILED: 'Lỗi cũ — Cần chuyển duyệt',
  APPROVED: 'Đã phê duyệt',
  NEEDS_MORE_INFO: 'Cần bổ sung',
  REJECTED: 'Đã từ chối',
};

const formatDateTime = (iso) => {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('vi-VN');
  } catch {
    return iso;
  }
};

export default function SkillReviewCenterView() {
  const [filterTab, setFilterTab] = useState('PENDING_REVIEW');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedItem, setSelectedItem] = useState(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const data =
        filterTab === 'PENDING_REVIEW'
          ? await skillReviewApi.getPendingQueue()
          : await skillReviewApi.getAllVerifications(filterTab === 'ALL' ? null : filterTab);
      setItems(Array.isArray(data) ? data : []);
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể tải danh sách chứng chỉ.', 'error');
    } finally {
      setLoading(false);
    }
  }, [filterTab]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const filteredItems = useMemo(() => {
    if (!searchTerm.trim()) return items;
    const term = searchTerm.toLowerCase().trim();
    return items.filter((row) =>
      [row.workerName, row.skillName, row.jobType, row.institution, row.recipientName]
        .some((f) => f && String(f).toLowerCase().includes(term))
    );
  }, [items, searchTerm]);

  const handleMoveToManual = async (row) => {
    try {
      await skillReviewApi.moveFailedToManualReview(row.id);
      showToast('Đã chuyển hồ sơ sang hàng đợi duyệt thủ công.', 'success');
      fetchData();
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể chuyển sang duyệt thủ công.', 'error');
    }
  };

  return (
    <div className="skill-review-container">
      <div className="skill-review-toolbar">
        <div className="skill-review-tabs">
          {[
            { id: 'PENDING_REVIEW', label: 'Chờ duyệt thủ công' },
            { id: 'VERIFICATION_FAILED', label: 'Hồ sơ lỗi cũ' },
            { id: 'ALL', label: 'Tất cả hồ sơ' },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={`skill-review-tab ${filterTab === tab.id ? 'active' : ''}`}
              onClick={() => setFilterTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="skill-review-actions">
          <input
            type="text"
            className="skill-search-input"
            placeholder="Tìm theo Worker, kỹ năng, đơn vị cấp..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <button type="button" className="btn-skill-secondary" onClick={fetchData} disabled={loading}>
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </div>

      <div className="skill-table-card">
        {loading && items.length === 0 ? (
          <div className="skill-empty-state">Đang tải danh sách hồ sơ chứng chỉ...</div>
        ) : filteredItems.length === 0 ? (
          <div className="skill-empty-state">Không có hồ sơ chứng chỉ phù hợp trong mục này.</div>
        ) : (
          <div className="skill-table-scroll">
            <table className="skill-queue-table">
              <thead>
                <tr>
                  <th>Worker</th>
                  <th>Kỹ năng đăng ký</th>
                  <th>Đơn vị cấp / Số hiệu</th>
                  <th>Trạng thái</th>
                  <th>Ngày nộp</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <div className="skill-cell-title">{row.workerName || '—'}</div>
                      {row.recipientName && <div className="skill-cell-sub">Trên bằng: {row.recipientName}</div>}
                    </td>
                    <td>{row.skillName || row.jobType || '—'}</td>
                    <td>
                      <div>{row.institution || 'Chưa ghi rõ'}</div>
                      {row.certificateNumber && <div className="skill-cell-sub">{row.certificateNumber}</div>}
                    </td>
                    <td>
                      <span className={`skill-status-pill ${String(row.status || '').toLowerCase()}`}>
                        {STATUS_LABELS[row.status] || row.status}
                      </span>
                    </td>
                    <td>{formatDateTime(row.createdAt)}</td>
                    <td className="skill-action-cell">
                      <button type="button" className="btn-skill-primary" onClick={() => setSelectedItem(row)}>
                        {row.status === 'PENDING_REVIEW' ? 'Duyệt' : 'Xem'}
                      </button>
                      {row.status === 'VERIFICATION_FAILED' && (
                        <button type="button" className="btn-skill-secondary" onClick={() => handleMoveToManual(row)}>
                          Chuyển duyệt tay
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedItem && (
        <SkillReviewModal
          item={selectedItem}
          onClose={() => setSelectedItem(null)}
          onUpdated={fetchData}
        />
      )}
    </div>
  );
}
