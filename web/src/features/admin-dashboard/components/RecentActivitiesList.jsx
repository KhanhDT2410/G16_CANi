/**
 * Danh sách 10 hoạt động quản trị gần nhất từ bảng audit_logs (UC-86)
 * Giao diện thoáng đãng, thân thiện, tự động bóc tách dữ liệu kỹ thuật thành ngôn ngữ dễ hiểu.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */

const ENTITY_LABELS = {
  USER: 'Tài khoản người dùng',
  WORKER_EKYC: 'Hồ sơ eKYC nhân viên',
  EO_EKYC: 'Hồ sơ eKYC ban tổ chức',
  SKILL_VERIFICATION: 'Chứng chỉ kỹ năng',
  DISPUTE: 'Tranh chấp khiếu nại',
  SUPPORT_TICKET: 'Phiếu hỗ trợ',
  RS_APPEAL: 'Kháng cáo điểm uy tín',
  FAQ: 'Câu hỏi thường gặp',
  SYSTEM_CONFIG: 'Cấu hình tham số',
  NO_SHOW: 'Vi phạm vắng mặt',
};

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

const parseActivity = (item) => {
  let parsed = null;
  if (item.newValues) {
    try {
      parsed = typeof item.newValues === 'string' ? JSON.parse(item.newValues) : item.newValues;
    } catch {
      parsed = null;
    }
  }

  const action = (item.action || '').toUpperCase();
  let actionLabel = item.actionDescription || item.action;
  let badgeVariant = 'neutral'; // 'danger' | 'success' | 'info' | 'neutral'

  // Phân tích hành động đổi trạng thái người dùng sang ngôn ngữ tự nhiên
  if (action === 'UPDATE_USER_STATUS' || action.includes('LOCK')) {
    if (parsed?.status === 'LOCKED') {
      actionLabel = 'Khóa tài khoản';
      badgeVariant = 'danger';
    } else if (parsed?.status === 'ACTIVE') {
      actionLabel = 'Mở khóa tài khoản';
      badgeVariant = 'success';
    } else {
      actionLabel = 'Cập nhật trạng thái';
      badgeVariant = 'info';
    }
  } else if (action.includes('APPROVE') || action.includes('PUBLISH')) {
    badgeVariant = 'success';
  } else if (action.includes('REJECT') || action.includes('BREACH')) {
    badgeVariant = 'danger';
  } else if (action.includes('ASSIGN') || action.includes('SUBMIT')) {
    badgeVariant = 'info';
  } else if (action.includes('SKILL') || action.includes('EKYC')) {
    badgeVariant = 'info';
  }

  const reason = parsed?.reason || parsed?.note || parsed?.resolutionNotes || null;
  const entityName = ENTITY_LABELS[item.entityType] || item.entityType || 'Đối tượng';
  const shortId = item.entityId ? item.entityId.slice(0, 8) : null;
  const targetName = item.targetEntityName || null;
  const targetEmail = item.targetEntityEmail || null;

  return {
    actionLabel,
    badgeVariant,
    reason,
    entityName,
    shortId,
    targetName,
    targetEmail,
  };
};

const RecentActivitiesList = ({ activities = [] }) => {
  return (
    <div className="recent-activities-card">
      <div className="activities-header">
        <div className="activities-title-group">
          <h3 className="activities-title">Nhật Ký Quản Trị Gần Nhất</h3>
          <p className="activities-subtitle">Giám sát 10 hoạt động vận hành và kiểm toán mới nhất của hệ thống</p>
        </div>
      </div>

      {activities.length === 0 ? (
        <div className="activities-empty">
          <p>Chưa có hoạt động quản trị nào được ghi nhận gần đây.</p>
        </div>
      ) : (
        <div className="activities-list">
          {activities.map((item) => {
            const { actionLabel, badgeVariant, reason, entityName, shortId, targetName, targetEmail } = parseActivity(item);
            const performerName = item.adminName || 'Hệ thống';
            const performerInitial = performerName.charAt(0).toUpperCase();

            return (
              <div key={item.id} className="activity-item">
                <div className={`activity-avatar ${item.adminName ? 'avatar-admin' : 'avatar-system'}`} title={performerName}>
                  {item.adminAvatarUrl ? (
                    <img src={item.adminAvatarUrl} alt={performerName} />
                  ) : (
                    <span>{performerInitial}</span>
                  )}
                </div>

                <div className="activity-content">
                  <div className="activity-top">
                    <div className="activity-performer-wrap">
                      <span className="activity-performer">{performerName}</span>
                      {item.adminEmail && (
                        <span className="activity-performer-email">{item.adminEmail}</span>
                      )}
                    </div>
                    <span className="activity-time">{formatTime(item.createdAt)}</span>
                  </div>

                  <div className="activity-action-row">
                    <span className={`activity-badge badge-${badgeVariant}`}>
                      {actionLabel}
                    </span>
                    <span className="activity-target-desc">
                      tác động lên <strong>{entityName}</strong>
                      {targetName && (
                        <span className="activity-target-name-badge">
                          {targetName}
                          {targetEmail && <span className="activity-target-email-sub">({targetEmail})</span>}
                        </span>
                      )}
                      {shortId && <span className="activity-id-tag">#{shortId}</span>}
                    </span>
                  </div>

                  {reason && (
                    <div className="activity-reason-note">
                      <span className="activity-reason-label">Lý do ghi nhận:</span>
                      <span className="activity-reason-val">"{reason}"</span>
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
