import React, { useState, useEffect } from 'react';
import { showToast } from '../../../components/common/Toast';
import { showConfirm } from '../../../components/common/PromptModal';
import { blockListService } from '../../../services/blockListService';

/**
 * EOSettingsTab
 * Màn hình Quản Lý Danh Sách Nhân Sự Bị Chặn của Ban Tổ Chức (dữ liệu thực từ CSDL)
 */
export default function EOSettingsTab() {
  const [blockedList, setBlockedList] = useState([]);
  const [loadingBlocked, setLoadingBlocked] = useState(false);

  const fetchBlockedList = async () => {
    setLoadingBlocked(true);
    try {
      const data = await blockListService.getBlockedList();
      setBlockedList(Array.isArray(data) ? data : []);
    } catch (err) {
      console.warn('Lỗi tải danh sách chặn:', err);
      setBlockedList([]);
    } finally {
      setLoadingBlocked(false);
    }
  };

  useEffect(() => {
    fetchBlockedList();
  }, []);

  const handleUnblock = async (blockedUserId, name) => {
    const confirmed = await showConfirm({
      title: 'Bỏ chặn nhân sự',
      description: `Bạn có chắc chắn muốn bỏ chặn "${name || 'nhân sự này'}" không?`,
      type: 'info',
      confirmText: 'Bỏ chặn',
    });
    if (!confirmed) return;
    try {
      await blockListService.unblockUser(blockedUserId);
      showToast('Đã bỏ chặn nhân sự thành công.', 'success');
      fetchBlockedList();
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi bỏ chặn', 'error');
    }
  };

  return (
    <div className="eo-settings-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      <div className="eo-widget-card" style={{ padding: '1.5rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.2rem', fontWeight: 800, color: '#0f172a' }}>
              Danh Sách Nhân Sự Bị Chặn ({blockedList.length})
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.85rem', color: '#64748b' }}>
              Những nhân sự trong danh sách này sẽ không thể nhìn thấy hoặc nhận ca làm việc của bạn trong tương lai.
            </p>
          </div>
          <button
            type="button"
            className="eo-btn-secondary"
            onClick={fetchBlockedList}
            style={{ fontSize: '0.82rem', padding: '0.45rem 1rem' }}
          >
            Làm mới
          </button>
        </div>

        {loadingBlocked ? (
          <div style={{ padding: '2rem 0', textAlign: 'center', color: '#64748b' }}>
            Đang tải danh sách chặn...
          </div>
        ) : blockedList.length === 0 ? (
          <div
            style={{
              padding: '2.5rem 1.5rem',
              textAlign: 'center',
              backgroundColor: '#f8fafc',
              borderRadius: '12px',
              border: '1.5px dashed #cbd5e1',
              color: '#64748b',
            }}
          >
            <p style={{ margin: '0 0 0.5rem', fontWeight: 700, color: '#334155' }}>
              Hiện tại bạn chưa chặn bất kỳ nhân sự nào.
            </p>
            <p style={{ margin: 0, fontSize: '0.84rem' }}>
              Bạn có thể chặn nhân sự trực tiếp từ trang quản lý từng ca hoặc trang chi tiết sự kiện nếu họ vi phạm quy chế.
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {blockedList.map((item) => (
              <div
                key={item.id || item.blockedUserId}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  padding: '0.9rem 1.15rem',
                  backgroundColor: '#ffffff',
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
                  gap: '1rem',
                  flexWrap: 'wrap',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', flexWrap: 'wrap' }}>
                    <strong style={{ fontSize: '0.95rem', color: '#0f172a' }}>
                      {item.blockedUserName || item.blockedName || 'Nhân sự đã chặn'}
                    </strong>
                    {(item.blockedUserEmail || item.blockedEmail) && (
                      <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                        {item.blockedUserEmail || item.blockedEmail}
                      </span>
                    )}
                  </div>
                  {item.reason && (
                    <div style={{ marginTop: '0.25rem', fontSize: '0.82rem', color: '#dc2626' }}>
                      Lý do: <em>{item.reason}</em>
                    </div>
                  )}
                  <div style={{ marginTop: '0.25rem', fontSize: '0.75rem', color: '#94a3b8' }}>
                    Chặn lúc: {item.createdAt ? new Date(item.createdAt).toLocaleString('vi-VN') : 'Gần đây'}
                  </div>
                </div>

                <button
                  type="button"
                  onClick={() => handleUnblock(item.blockedUserId || item.id, item.blockedUserName || item.blockedName)}
                  style={{
                    padding: '0.45rem 0.95rem',
                    backgroundColor: '#f1f5f9',
                    color: '#0f172a',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.82rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                  }}
                >
                  Bỏ Chặn
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
