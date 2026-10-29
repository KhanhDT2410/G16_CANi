/**
 * Bảng danh sách sự kiện đang hoạt động trên nền tảng.
 */
const StaffingBar = ({ filled, total }) => {
  const pct = total > 0 ? Math.round((filled / total) * 100) : 0;
  let barClass = 'success';
  if (pct <= 50) barClass = 'danger';
  else if (pct < 100) barClass = 'warning';

  return (
    <div className="adm-staffing-bar-wrap">
      <div className="adm-staffing-bar-track">
        <div
          className={`adm-staffing-bar-fill ${barClass}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <span className="adm-staffing-label">
        {filled}/{total} ({pct}%)
      </span>
    </div>
  );
};

const EventStatusBadge = ({ status }) => {
  const map = {
    ACTIVE: { label: 'Đang diễn ra', cls: 'success' },
    RECRUITING: { label: 'Đang tuyển', cls: 'info' },
    COMPLETED: { label: 'Hoàn thành', cls: 'muted' },
  };
  const { label, cls } = map[status] || { label: status, cls: 'muted' };
  return <span className={`adm-event-badge ${cls}`}>{label}</span>;
};

const ActiveEventsTable = ({ events = [] }) => {
  return (
    <div className="adm-panel animate-fade-in-up">
      <div className="adm-panel-header">
        <h3 className="adm-panel-title">
          <span className="adm-panel-icon">📅</span>
          Sự Kiện Đang Hoạt Động
        </h3>
        <span className="adm-panel-count">{events.length} sự kiện</span>
      </div>
      <div className="adm-table-wrap">
        <table className="adm-table">
          <thead>
            <tr>
              <th>Mã Sự Kiện</th>
              <th>Tên Sự Kiện</th>
              <th>Ban Tổ Chức</th>
              <th>Địa Điểm</th>
              <th>Thời Gian</th>
              <th>Nhân Sự</th>
              <th>Trạng Thái</th>
            </tr>
          </thead>
          <tbody>
            {events.map((evt) => (
              <tr key={evt.id}>
                <td className="adm-code-cell">{evt.id}</td>
                <td className="adm-name-cell">{evt.name}</td>
                <td>{evt.eoName}</td>
                <td>{evt.location}</td>
                <td className="adm-date-cell">
                  {evt.startDate} — {evt.endDate}
                </td>
                <td>
                  <StaffingBar
                    filled={evt.filledShifts}
                    total={evt.totalShifts}
                  />
                </td>
                <td>
                  <EventStatusBadge status={evt.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default ActiveEventsTable;
