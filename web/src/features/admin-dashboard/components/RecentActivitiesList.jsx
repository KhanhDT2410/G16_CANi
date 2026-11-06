import { ActivityLogIcon } from './Icons';

/**
 * Danh sách 10 hoạt động quản trị gần nhất từ bảng audit_logs
 */
const RecentActivitiesList = ({ activities = [] }) => {
  const formatTime = (dateStr) => {
    if (!dateStr) return '';
    try {
      const date = new Date(dateStr);
      return date.toLocaleString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  const getActionChipClass = (action = '') => {
    const act = action.toUpperCase();
    if (act.includes('STATUS') || act.includes('LOCK')) return 'status-update';
    if (act.includes('SKILL')) return 'skill-approval';
    if (act.includes('EKYC')) return 'ekyc-approval';
    return '';
  };

  return (
    <div className="recent-activities-card">
      <div className="activities-header">
        <div className="activities-title">
          <div className="metric-icon-box activities">
            <ActivityLogIcon size={18} />
          </div>
          <span>Nhật Ký Quản Trị Gần Nhất (Audit Logs)</span>
        </div>
        <span className="activities-header-meta">
          Tối đa 10 hoạt động mới nhất
        </span>
      </div>

      {activities.length === 0 ? (
        <div className="activities-empty">
          <p>Chưa có hoạt động quản trị nào được ghi nhận gần đây.</p>
        </div>
      ) : (
        <div className="activities-list">
          {activities.map((item) => {
            const chipClass = getActionChipClass(item.action);
            const performerName = item.adminName || 'Hệ thống';
            const performerInitial = performerName.charAt(0).toUpperCase();

            return (
              <div key={item.id} className="activity-item">
                <div className="activity-avatar" title={performerName}>
                  {item.adminAvatarUrl ? (
                    <img src={item.adminAvatarUrl} alt={performerName} />
                  ) : (
                    <span>{performerInitial}</span>
                  )}
                </div>

                <div className="activity-content">
                  <div className="activity-top">
                    <span className="activity-performer">
                      {performerName}
                      {item.adminEmail && (
                        <span className="activity-performer-email">
                          ({item.adminEmail})
                        </span>
                      )}
                    </span>
                    <span className="activity-time">{formatTime(item.createdAt)}</span>
                  </div>

                  <div className="activity-desc">
                    <span className={`activity-chip ${chipClass}`}>
                      {item.actionDescription || item.action}
                    </span>
                    <span className="activity-entity">
                      Đối tượng: <strong>{item.entityType}</strong>
                      {item.entityId && (
                        <span className="activity-entity-id">
                          (ID: {item.entityId.slice(0, 8)}...)
                        </span>
                      )}
                    </span>
                  </div>

                  {item.newValues && (
                    <div className="activity-meta">
                      Chi tiết: {item.newValues}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default RecentActivitiesList;
