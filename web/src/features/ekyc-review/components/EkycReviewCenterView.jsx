import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { ekycReviewApi } from '../api/ekycReviewApi';
import EkycReviewModal from './EkycReviewModal';
import { showToast } from '../../../components/common/Toast';
import './EkycReview.css';

const formatScore = (val) => {
  if (val == null || Number.isNaN(Number(val))) return '—';
  const num = Number(val);
  const pct = num <= 1 ? num * 100 : num;
  return `${pct.toFixed(1)}%`;
};

const formatDateTime = (iso) => {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString('vi-VN');
  } catch {
    return iso;
  }
};

export default function EkycReviewCenterView({ canManageLegalHold = false }) {
  const [targetType, setTargetType] = useState('worker');
  const [workerQueue, setWorkerQueue] = useState([]);
  const [eoQueue, setEoQueue] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedItem, setSelectedItem] = useState(null);

  const fetchQueues = useCallback(async () => {
    setLoading(true);
    try {
      const [workers, eos] = await Promise.all([
        ekycReviewApi.getQueue('worker'),
        ekycReviewApi.getQueue('eo'),
      ]);
      setWorkerQueue(Array.isArray(workers) ? workers : []);
      setEoQueue(Array.isArray(eos) ? eos : []);
    } catch (err) {
      showToast(
        err.response?.data?.message || 'Không thể tải hàng đợi thẩm định eKYC.',
        'error'
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchQueues();
  }, [fetchQueues]);

  const currentItems = targetType === 'eo' ? eoQueue : workerQueue;

  const filteredItems = useMemo(() => {
    if (!searchTerm.trim()) return currentItems;
    const term = searchTerm.toLowerCase().trim();
    return currentItems.filter((row) => {
      const fields = [
        row.fullName,
        row.workerName,
        row.representativeName,
        row.companyName,
        row.cccdNumberMasked,
        row.routingReason,
      ];
      return fields.some((f) => f && String(f).toLowerCase().includes(term));
    });
  }, [currentItems, searchTerm]);

  return (
    <div className="ekyc-review-container">
      <div className="ekyc-review-toolbar">
        <div className="ekyc-target-tabs">
          <button
            type="button"
            className={`ekyc-target-tab ${targetType === 'worker' ? 'active' : ''}`}
            onClick={() => setTargetType('worker')}
          >
            <span>Nhân sự</span>
            <span className="ekyc-count-badge">{workerQueue.length}</span>
          </button>
          <button
            type="button"
            className={`ekyc-target-tab ${targetType === 'eo' ? 'active' : ''}`}
            onClick={() => setTargetType('eo')}
          >
            <span>Nhà tổ chức</span>
            <span className="ekyc-count-badge">{eoQueue.length}</span>
          </button>
        </div>

        <div className="ekyc-toolbar-right">
          <input
            type="text"
            className="ekyc-search-input"
            placeholder="Tìm theo tên, đơn vị, số CCCD đã che, lý do..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
          <button
            type="button"
            className="btn-ekyc-secondary"
            onClick={fetchQueues}
            disabled={loading}
          >
            {loading ? 'Đang tải...' : 'Làm mới'}
          </button>
        </div>
      </div>

      <div className="ekyc-table-card">
        {loading && currentItems.length === 0 ? (
          <div className="ekyc-empty-state">Đang tải danh sách hồ sơ eKYC chờ duyệt...</div>
        ) : filteredItems.length === 0 ? (
          <div className="ekyc-empty-state">
            Không có hồ sơ eKYC {targetType === 'eo' ? 'Nhà tổ chức' : 'Worker'} nào đang chờ thẩm định.
          </div>
        ) : (
          <div className="ekyc-table-scroll">
            <table className="ekyc-queue-table">
              <thead>
                <tr>
                  <th>{targetType === 'eo' ? 'Đại diện / Đơn vị' : 'Họ và tên'}</th>
                  <th>Số CCCD (đã che)</th>
                  <th>Khớp mặt / OCR</th>
                  <th>Lý do cần duyệt tay</th>
                  <th>Thời gian nộp</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filteredItems.map((row) => (
                  <tr key={row.verificationId}>
                    <td>
                      <div className="ekyc-cell-title">
                        {targetType === 'eo'
                          ? row.representativeName || row.companyName || '—'
                          : row.fullName || row.workerName || '—'}
                      </div>
                      {targetType === 'eo' && row.companyName && (
                        <div className="ekyc-cell-sub">{row.companyName}</div>
                      )}
                      {row.legalHold && <span className="ekyc-hold-tag">Bảo lưu pháp lý</span>}
                    </td>
                    <td>
                      <code>{row.cccdNumberMasked || 'Chưa bóc tách'}</code>
                    </td>
                    <td>
                      <div>Mặt: {formatScore(row.faceMatchScore)}</div>
                      <div className="ekyc-cell-sub">OCR: {formatScore(row.ocrConfidence)}</div>
                    </td>
                    <td className="ekyc-reason-cell">
                      {row.routingReason || 'Chờ Admin / CSKH đối chiếu thủ công'}
                    </td>
                    <td>{formatDateTime(row.createdAt)}</td>
                    <td>
                      <button
                        type="button"
                        className="btn-ekyc-review"
                        onClick={() => setSelectedItem(row)}
                      >
                        Thẩm định
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {selectedItem && (
        <EkycReviewModal
          targetType={targetType}
          item={selectedItem}
          canManageLegalHold={canManageLegalHold}
          onClose={() => setSelectedItem(null)}
          onReviewed={fetchQueues}
        />
      )}
    </div>
  );
}
