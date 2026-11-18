import React, { useState, useEffect } from 'react';
import { showToast } from '../../../components/common/Toast';
import { blockListService } from '../../../services/blockListService';

/**
 * EOSettingsTab
 * Màn hình Cài đặt cho Ban Tổ Chức (EO): Thông Báo & Vận Hành và Danh Sách Chặn
 */
export default function EOSettingsTab() {
  const [activeSection, setActiveSection] = useState('NOTIFICATIONS'); // 'NOTIFICATIONS' | 'BLOCKLIST'

  const [notificationSettings, setNotificationSettings] = useState({
    notifyOnWorkerAccept: true,
    notifyOnCheckinLate: true,
    notifyOnShiftComplete: true,
    dailyReportEmail: true,
  });

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
    if (activeSection === 'BLOCKLIST') {
      fetchBlockedList();
    }
  }, [activeSection]);

  const handleUnblock = async (blockedUserId, name) => {
    if (!window.confirm(`Bạn có chắc chắn muốn bỏ chặn "${name || 'người này'}" không?`)) return;
    try {
      await blockListService.unblockUser(blockedUserId);
      showToast('Đã bỏ chặn người dùng thành công!', 'success');
      fetchBlockedList();
    } catch (err) {
      showToast(err.response?.data?.message || 'Lỗi khi bỏ chặn', 'error');
    }
  };

  useEffect(() => {
    try {
      const savedNotif = localStorage.getItem('cani_eo_notif_settings');
      if (savedNotif) setNotificationSettings(JSON.parse(savedNotif));
    } catch (e) {
      console.warn('Lỗi khi đọc cài đặt từ bộ nhớ cục bộ:', e);
    }
  }, []);

  const handleSaveAll = (e) => {
    e?.preventDefault();
    try {
      localStorage.setItem('cani_eo_notif_settings', JSON.stringify(notificationSettings));
      showToast('Đã lưu cấu hình cài đặt thành công!', 'success');
    } catch {
      showToast('Lỗi khi lưu cấu hình', 'error');
    }
  };

  return (
    <div className="eo-settings-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* 1. HEADER */}
      <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
              Cài Đặt Thông Báo & Quản Lý Danh Sách Chặn
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.875rem', color: '#64748b' }}>
              Quản lý tuỳ chọn nhận thông báo vận hành sự kiện và danh sách nhân sự bị hạn chế.
            </p>
          </div>

          <button
            type="button"
            className="eo-btn-primary"
            style={{ fontSize: '0.85rem', padding: '0.55rem 1.25rem' }}
            onClick={handleSaveAll}
          >
            Lưu Cài Đặt
          </button>
        </div>
      </div>

      {/* 2. SUB-NAVIGATION TABS */}
      <div className="sub-tabs-bar" style={{ display: 'flex', gap: '0.5rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.5rem' }}>
        <button
          type="button"
          className={`sub-tab-btn ${activeSection === 'NOTIFICATIONS' ? 'active' : ''}`}
          onClick={() => setActiveSection('NOTIFICATIONS')}
          style={{
            padding: '8px 16px',
            border: 'none',
            borderRadius: 6,
            background: activeSection === 'NOTIFICATIONS' ? '#eff6ff' : 'transparent',
            color: activeSection === 'NOTIFICATIONS' ? '#1d4ed8' : '#64748b',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Thông Báo & Vận Hành
        </button>

        <button
          type="button"
          className={`sub-tab-btn ${activeSection === 'BLOCKLIST' ? 'active' : ''}`}
          onClick={() => setActiveSection('BLOCKLIST')}
          style={{
            padding: '8px 16px',
            border: 'none',
            borderRadius: 6,
            background: activeSection === 'BLOCKLIST' ? '#eff6ff' : 'transparent',
            color: activeSection === 'BLOCKLIST' ? '#1d4ed8' : '#64748b',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Danh sách chặn
        </button>
      </div>

      {/* SECTION 1: THÔNG BÁO & VẬN HÀNH */}
      {activeSection === 'NOTIFICATIONS' && (
        <div className="eo-widget-card" style={{ padding: '1.5rem' }}>
          <h4 style={{ margin: '0 0 1rem', fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
            Cấu Hình Thông Báo & Cảnh Báo Vận Hành
          </h4>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {[
              {
                id: 'notifWorker',
                title: 'Thông báo tức thì khi có Worker nhận ca',
                desc: 'Gửi tin nhắn thông báo khi hệ thống tự động kết nối thành công ứng viên',
                checked: notificationSettings.notifyOnWorkerAccept,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, notifyOnWorkerAccept: v }),
              },
              {
                id: 'notifLate',
                title: 'Cảnh báo khi nhân sự Check-in trễ giờ',
                desc: 'Hệ thống gửi cảnh báo nếu Worker chưa quét mã QR sau 15 phút kể từ giờ bắt đầu ca',
                checked: notificationSettings.notifyOnCheckinLate,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, notifyOnCheckinLate: v }),
              },
              {
                id: 'notifDone',
                title: 'Nhắc nhở đối soát & duyệt công sau ca',
                desc: 'Tự động mở danh sách kiểm tra khi ca làm kết thúc để Ban tổ chức duyệt thù lao',
                checked: notificationSettings.notifyOnShiftComplete,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, notifyOnShiftComplete: v }),
              },
              {
                id: 'notifSummary',
                title: 'Báo cáo tổng kết sự kiện qua Email',
                desc: 'Nhận bảng tổng hợp đối soát thanh toán và đánh giá nhân sự sau khi hoàn thành sự kiện',
                checked: notificationSettings.dailyReportEmail,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, dailyReportEmail: v }),
              },
            ].map((item) => (
              <div
                key={item.id}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                  padding: '0.75rem',
                  backgroundColor: '#f8fafc',
                  borderRadius: 8,
                  border: '1px solid #e2e8f0',
                }}
              >
                <input
                  type="checkbox"
                  id={item.id}
                  checked={item.checked}
                  onChange={(e) => item.onChange(e.target.checked)}
                  style={{ width: 18, height: 18, marginTop: 2, cursor: 'pointer' }}
                />
                <div>
                  <label htmlFor={item.id} style={{ margin: 0, fontWeight: 600, color: '#0f172a', cursor: 'pointer' }}>
                    {item.title}
                  </label>
                  <p style={{ margin: '0.2rem 0 0', fontSize: '0.825rem', color: '#64748b' }}>
                    {item.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* SECTION 2: DANH SÁCH CHẶN (BLOCK LIST) */}
      {activeSection === 'BLOCKLIST' && (
        <div className="eo-widget-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '0.75rem' }}>
            <div>
              <h4 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700, color: '#0f172a' }}>
                Danh Sách Nhân Sự Bị Chặn ({blockedList.length})
              </h4>
              <p style={{ margin: '0.25rem 0 0', fontSize: '0.825rem', color: '#64748b' }}>
                Những nhân sự trong danh sách này sẽ không thể nhìn thấy hoặc nhận ca làm việc của bạn trong tương lai.
              </p>
            </div>
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={fetchBlockedList}
              style={{ fontSize: '0.8rem', padding: '0.4rem 0.85rem' }}
            >
              Làm mới
            </button>
          </div>

          {loadingBlocked ? (
            <div style={{ padding: '2rem 0', textAlign: 'center', color: '#64748b' }}>
              Đang tải danh sách chặn...
            </div>
          ) : blockedList.length === 0 ? (
            <div style={{
              padding: '2.5rem 1.5rem',
              textAlign: 'center',
              backgroundColor: '#f8fafc',
              borderRadius: '12px',
              border: '1.5px dashed #cbd5e1',
              color: '#64748b',
            }}>
              <p style={{ margin: '0 0 0.5rem', fontWeight: 600, color: '#334155' }}>
                Hiện tại bạn chưa chặn bất kỳ nhân sự nào.
              </p>
              <p style={{ margin: 0, fontSize: '0.825rem' }}>
                Bạn có thể chặn nhân sự trực tiếp từ danh sách nhân sự của từng sự kiện nếu họ vi phạm quy chế.
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
                    padding: '0.85rem 1.15rem',
                    backgroundColor: '#ffffff',
                    border: '1px solid #e2e8f0',
                    borderRadius: '10px',
                    boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <strong style={{ fontSize: '0.9rem', color: '#0f172a' }}>
                        {item.blockedUserName || item.blockedName || 'Nhân sự đã chặn'}
                      </strong>
                      <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        {item.blockedUserEmail || item.blockedEmail || ''}
                      </span>
                    </div>
                    {item.reason && (
                      <div style={{ marginTop: '0.25rem', fontSize: '0.8rem', color: '#dc2626' }}>
                        Lý do: <em>{item.reason}</em>
                      </div>
                    )}
                    <div style={{ marginTop: '0.2rem', fontSize: '0.725rem', color: '#94a3b8' }}>
                      Chặn lúc: {item.createdAt ? new Date(item.createdAt).toLocaleString('vi-VN') : 'Gần đây'}
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={() => handleUnblock(item.blockedUserId || item.id, item.blockedUserName || item.blockedName)}
                    style={{
                      padding: '0.4rem 0.85rem',
                      backgroundColor: '#f1f5f9',
                      color: '#0f172a',
                      border: '1px solid #cbd5e1',
                      borderRadius: '6px',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      transition: 'all 0.15s ease',
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = '#e2e8f0'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = '#f1f5f9'; }}
                  >
                    Bỏ Chặn
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
