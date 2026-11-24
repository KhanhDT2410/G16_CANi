import { RS_APPEAL_STATUS_CONFIG } from '../types/rsAppeal.constants';

export const RsAppealStatusBadge = ({ status }) => {
  const config = RS_APPEAL_STATUS_CONFIG[status] || {
    label: status || '—',
    color: '#475569',
    bg: '#f8fafc',
    border: '#e2e8f0',
  };

  return (
    <span
      className="appeal-status-badge"
      style={{
        color: config.color,
        backgroundColor: config.bg,
        borderColor: config.border || '#cbd5e1',
      }}
    >
      {config.label}
    </span>
  );
};

