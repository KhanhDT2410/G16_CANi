import React from 'react';
import { showToast } from '../../../components/common/Toast';

const RECENT_NOTIFICATIONS = [
  {
    id: 1,
    title: 'Nguyễn Văn A đã ứng tuyển vào ca Lễ tân',
    time: '10 phút trước',
  },
  {
    id: 2,
    title: 'Sự kiện Summer Fest 2026 đã được duyệt',
    time: '1 giờ trước',
  },
  {
    id: 3,
    title: 'Yêu cầu xác nhận lương từ Trần Thị B',
    time: 'Hôm qua',
  },
];

export default function EORightSidebar() {
  return (
    <aside className="eo-right-sidebar eo-widget-panel">
      {/* Widget Thông báo và hoạt động gần đây */}
      <div className="eo-widget-card activity-widget">
        <div className="widget-header">
          <h4 className="widget-title">Thông báo gần đây</h4>
          <button
            type="button"
            className="widget-link-btn"
            onClick={() => showToast('Mở trung tâm thông báo', 'info')}
          >
            Tất cả
          </button>
        </div>

        <div className="notification-list">
          {RECENT_NOTIFICATIONS.map((item) => (
            <div key={item.id} className="notification-item">
              <div
                className="notif-avatar"
                style={{ backgroundColor: '#eff6ff', color: '#2563eb' }}
              >
                {item.id}
              </div>
              <div className="notif-body">
                <p className="notif-title">{item.title}</p>
                <span className="notif-time">{item.time}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </aside>
  );
}
