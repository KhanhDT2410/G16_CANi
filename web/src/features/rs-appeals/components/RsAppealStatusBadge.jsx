import { RS_APPEAL_STATUS_CONFIG } from '../types/rsAppeal.constants';

export const RsAppealStatusBadge = ({ status }) => {
  const config = RS_APPEAL_STATUS_CONFIG[status] || {
    label: status || '—',
    color: '#4b5563',
    bg: '#f3f4f6',
  };

  return (
    <span
      className="appeal-status-badge"
      style={{
        color: config.color,
        backgroundColor: config.bg,
      }}
    >
      {config.label}
    </span>
  );
};

