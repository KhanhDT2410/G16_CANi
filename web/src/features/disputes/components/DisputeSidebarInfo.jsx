const formatShiftTime = (start, end) => {
  if (!start) return null;
  const startDate = new Date(start);
  const timeStr = startDate.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  const dateStr = startDate.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  if (!end) return `${timeStr}, ${dateStr}`;
  const endTimeStr = new Date(end).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  return `${timeStr} - ${endTimeStr}, ${dateStr}`;
};

const formatRoleLabel = (role) => {
  if (role === 'EVENT_ORGANIZER') return 'Nhà tổ chức';
  if (role === 'WORKER') return 'Nhân sự';
  if (role === 'CUSTOMER_CARE') return 'CSKH';
  if (role === 'ADMIN') return 'Quản trị viên';
  return role || '';
};

/**
 * Cột thông tin chi tiết vụ việc & quy tắc hòa giải.
 * Tuân thủ < 200 dòng theo AGENTS.md.
 */
export const DisputeSidebarInfo = ({ dispute, copiedCode, onCopyId }) => {
  if (!dispute) return null;

  return (
    <div className="dispute-workspace-sidebar">
      <div className="sidebar-info-card">
        <div className="sidebar-card-header">
          <h4 className="sidebar-card-title">Hồ Sơ Vụ Việc</h4>
          <button
            type="button"
            className="case-ref-chip"
            onClick={onCopyId}
            title="Bấm để sao chép mã khiếu nại"
          >
            <span>#{dispute.id ? dispute.id.slice(0, 8).toUpperCase() : 'HỒ SƠ'}</span>
            <span className="copy-hint">{copiedCode ? 'Đã sao chép' : 'Sao chép'}</span>
          </button>
        </div>

        {/* Thông tin ca & sự kiện */}
        <div className="sidebar-info-section">
          <span className="info-label">Ca làm việc liên quan:</span>
          <div className="info-value-primary">
            {dispute.eventName ? (
              <>
                <div className="font-semibold text-main">{dispute.eventName}</div>
                {dispute.jobType && <div className="text-muted text-sm">{dispute.jobType}</div>}
              </>
            ) : (
              <span className="font-semibold">{dispute.jobType || `Ca #${dispute.assignmentId ? dispute.assignmentId.slice(0, 8).toUpperCase() : 'N/A'}`}</span>
            )}
          </div>
          {dispute.shiftStartTime && (
            <div className="info-subtext text-xs text-muted">
              Thời gian: {formatShiftTime(dispute.shiftStartTime, dispute.shiftEndTime)}
            </div>
          )}
          {dispute.shiftLocation && (
            <div className="info-subtext text-xs text-muted">
              Địa điểm: {dispute.shiftLocation}
            </div>
          )}
        </div>

        {/* Các bên liên quan */}
        <div className="sidebar-info-section">
          <div className="sidebar-parties-grid">
            <div className="party-box">
              <span className="info-label">Người gửi khiếu nại:</span>
              <div className="party-name">{dispute.complainantName || 'Người gửi đơn'}</div>
              {dispute.complainantRole && (
                <span className="party-role-tag">{formatRoleLabel(dispute.complainantRole)}</span>
              )}
            </div>
            <div className="party-box">
              <span className="info-label">Đối tác liên quan:</span>
              <div className="party-name">{dispute.respondentName || 'Đối tác trong ca'}</div>
              {dispute.respondentRole && (
                <span className="party-role-tag">{formatRoleLabel(dispute.respondentRole)}</span>
              )}
            </div>
          </div>
        </div>

        {/* Phụ trách & Tiến độ */}
        <div className="sidebar-info-item">
          <span className="info-label">Chuyên viên CSKH:</span>
          <span className="info-text">
            {dispute.assignedToName ? (
              <strong className="text-primary">{dispute.assignedToName}</strong>
            ) : (
              <span className="badge-unassigned">Đang chờ chuyên viên tiếp nhận</span>
            )}
          </span>
        </div>

        <div className="sidebar-info-item">
          <span className="info-label">Thời gian gửi:</span>
          <span className="info-text">{new Date(dispute.createdAt).toLocaleString('vi-VN')}</span>
        </div>

        {dispute.slaDeadline && (
          <div className="sidebar-info-item">
            <span className="info-label">Hạn xử lý:</span>
            <span className="info-text font-semibold" style={{ color: '#d97706' }}>
              {new Date(dispute.slaDeadline).toLocaleDateString('vi-VN')}
            </span>
          </div>
        )}
      </div>

      {/* Quy tắc hòa giải */}
      <div className="sidebar-rules-card">
        <h4 className="sidebar-card-title">Quy Tắc Hòa Giải</h4>
        <ul className="sidebar-rules-list">
          <li>Mỗi bên có quyền nộp tài liệu giải trình, hóa đơn và hình ảnh đối soát.</li>
          <li>CSKH giữ vai trò trung gian xác minh chứng cứ và đưa ra phán quyết độc lập.</li>
          <li>Trường hợp bên có lỗi là Nhân sự, hệ thống tự động khấu trừ điểm uy tín.</li>
          <li>Nếu không đồng ý phán quyết, các bên có quyền kháng nghị trong vòng 7 ngày.</li>
        </ul>
      </div>
    </div>
  );
};

export default DisputeSidebarInfo;
