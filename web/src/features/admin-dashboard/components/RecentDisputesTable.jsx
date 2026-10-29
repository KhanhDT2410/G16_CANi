import {
  DISPUTE_STATUS,
  DISPUTE_STATUS_LABELS,
} from '../types/adminDashboard.constants';

/**
 * Bảng tranh chấp gần đây cần Admin giám sát / phán quyết.
 */
const PriorityDot = ({ priority }) => {
  const cls =
    priority === 'HIGH'
      ? 'danger'
      : priority === 'MEDIUM'
      ? 'warning'
      : 'muted';
  return <span className={`adm-priority-dot ${cls}`} />;
};

const DisputeStatusBadge = ({ status }) => {
  const clsMap = {
    [DISPUTE_STATUS.SUBMITTED]: 'warning',
    [DISPUTE_STATUS.IN_REVIEW]: 'info',
    [DISPUTE_STATUS.RESOLVED]: 'success',
    [DISPUTE_STATUS.ESCALATED]: 'danger',
  };
  return (
    <span className={`adm-event-badge ${clsMap[status] || 'muted'}`}>
      {DISPUTE_STATUS_LABELS[status] || status}
    </span>
  );
};

const RecentDisputesTable = ({ disputes = [] }) => {
  return (
    <div className="adm-panel animate-fade-in-up">
      <div className="adm-panel-header">
        <h3 className="adm-panel-title">
          <span className="adm-panel-icon">⚖️</span>
          Tranh Chấp Gần Đây
        </h3>
        <span className="adm-panel-count">
          {disputes.filter((d) => d.status !== DISPUTE_STATUS.RESOLVED).length}{' '}
          đang mở
        </span>
      </div>
      <div className="adm-table-wrap">
        <table className="adm-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Worker</th>
              <th>Ban Tổ Chức</th>
              <th>Loại Vi Phạm</th>
              <th>Số Tiền</th>
              <th>Ngày Tạo</th>
              <th>Ưu Tiên</th>
              <th>Trạng Thái</th>
            </tr>
          </thead>
          <tbody>
            {disputes.map((d) => (
              <tr key={d.id}>
                <td className="adm-code-cell">{d.id}</td>
                <td className="adm-name-cell">{d.workerName}</td>
                <td>{d.eoName}</td>
                <td>{d.type}</td>
                <td className="adm-amount-cell">
                  {d.amount > 0
                    ? `${d.amount.toLocaleString('vi-VN')}đ`
                    : '—'}
                </td>
                <td className="adm-date-cell">{d.createdAt}</td>
                <td>
                  <div className="adm-priority-wrap">
                    <PriorityDot priority={d.priority} />
                    <span>{d.priority}</span>
                  </div>
                </td>
                <td>
                  <DisputeStatusBadge status={d.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default RecentDisputesTable;
