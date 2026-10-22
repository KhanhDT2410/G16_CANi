import { useState } from 'react';
import { showToast } from '../../components/common/Toast';
import './CustomerCarePage.css';

export const CustomerCarePage = () => {
  const [activeFilter, setActiveFilter] = useState('ALL'); // 'ALL' | 'SUBMITTED' | 'IN_REVIEW' | 'RESOLVED'
  const [selectedDispute, setSelectedDispute] = useState(null);
  const [resolutionNote, setResolutionNote] = useState('');
  const [selectedAction, setSelectedAction] = useState('SETTLE');

  // Danh sách khiếu nại tranh chấp thực tế
  const [disputes, setDisputes] = useState([
    {
      id: 'DISP-9021',
      workerName: 'Nguyễn Văn Worker',
      eoName: 'Công ty Cổ phần Sự kiện Sao Mai',
      eventName: 'Lễ hội Âm nhạc Summer Beat 2026',
      shiftRole: 'Kỹ thuật âm thanh - ánh sáng',
      violationType: 'PAYMENT_UNPAID',
      violationLabel: 'Chưa thanh toán sau ca làm',
      disputedAmount: 320000,
      status: 'SUBMITTED',
      createdAt: '22/10/2026 21:30',
      reason: 'Đã hoàn thành ca làm lúc 21h ngày 22/10 nhưng EO chưa thực hiện chuyển khoản và không phản hồi tin nhắn.',
      externalRef: '—',
      proofUrl: 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=400&q=80',
    },
    {
      id: 'DISP-9018',
      workerName: 'Trần Thị Thu Thảo',
      eoName: 'Tập đoàn Truyền thông Metan',
      eventName: 'Hội nghị Triển Lãm Công Nghệ Quốc Tế',
      shiftRole: 'Lễ tân / Check-in',
      violationType: 'PAYMENT_SHORTAGE',
      violationLabel: 'Nhận thiếu 70.000đ phụ cấp',
      disputedAmount: 70000,
      status: 'IN_REVIEW',
      createdAt: '21/10/2026 18:15',
      reason: 'EO chỉ chuyển 250.000đ tiền thù lao giờ, chưa chuyển 70.000đ tiền phụ cấp ăn trưa và gửi xe như cam kết trên hợp đồng ca.',
      externalRef: 'VCB-88392019',
      proofUrl: 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=400&q=80',
    },
    {
      id: 'DISP-9014',
      workerName: 'Phạm Hùng Cường',
      eoName: 'Apex Event Agency',
      eventName: 'Tiệc Cưới Gala Royal Palace',
      shiftRole: 'Phục vụ bàn',
      violationType: 'PAYMENT_UNPAID',
      violationLabel: 'Sai số tài khoản nhận tiền',
      disputedAmount: 220000,
      status: 'RESOLVED_SETTLED',
      createdAt: '19/10/2026 14:00',
      reason: 'EO đã chuyển vào số tài khoản cũ của Worker. Sau khi đối soát, EO đã chuyển lại vào STK mới.',
      externalRef: 'MB-10293847',
      proofUrl: 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=400&q=80',
    },
  ]);

  const filteredDisputes = disputes.filter((d) => {
    if (activeFilter === 'ALL') return true;
    if (activeFilter === 'RESOLVED') return d.status.startsWith('RESOLVED');
    return d.status === activeFilter;
  });

  const handleOpenModal = (dispute) => {
    setSelectedDispute(dispute);
    setResolutionNote(
      dispute.status === 'SUBMITTED'
        ? 'Đã liên hệ EO qua điện thoại, yêu cầu cung cấp sao kê giao dịch ngân hàng trong 4 giờ.'
        : 'Hai bên đã thống nhất chuyển khoản bổ sung phần tiền thiếu.'
    );
    setSelectedAction('SETTLE');
  };

  const handleResolveDispute = (e) => {
    e.preventDefault();
    if (!selectedDispute) return;

    let nextStatus = 'RESOLVED_SETTLED';
    let toastMessage = 'Đã hoàn tất hòa giải! Đóng khiếu nại thành công.';

    if (selectedAction === 'IN_REVIEW') {
      nextStatus = 'IN_REVIEW';
      toastMessage = 'Đã chuyển trạng thái sang Đang điều tra. Hệ thống đã gửi email yêu cầu sao kê tới EO.';
    } else if (selectedAction === 'PENALIZE') {
      nextStatus = 'RESOLVED_PENALIZED';
      toastMessage = 'Đã xử phạt trừ điểm tín nhiệm EO và tạm khóa quyền đăng tuyển ca mới do vi phạm nghĩa vụ trả lương.';
    } else if (selectedAction === 'REJECT') {
      nextStatus = 'RESOLVED_REJECTED';
      toastMessage = 'Đã bác bỏ khiếu nại do EO chứng minh đã chuyển khoản đủ và đúng hạn.';
    }

    setDisputes((prev) =>
      prev.map((d) =>
        d.id === selectedDispute.id
          ? {
              ...d,
              status: nextStatus,
              reason: `${d.reason} [CSKH: ${resolutionNote}]`,
            }
          : d
      )
    );

    setSelectedDispute(null);
    showToast(toastMessage, selectedAction === 'PENALIZE' ? 'error' : 'success');
  };

  return (
    <div className="customer-care-container animate-fade-in">
      <div className="cc-header">
        <div>
          <h1 className="cc-title">Customer Care & Dispute Resolution Center</h1>
          <p className="cc-subtitle">
            Tiếp nhận khiếu nại ca làm việc, đối soát biên lai thanh toán trực tiếp và giải quyết tranh chấp Non-Escrow.
          </p>
        </div>
        <span className="cc-role-badge">Role: CUSTOMER_CARE</span>
      </div>

      <div className="cc-stats-grid">
        <div className="cc-stat-card">
          <div className="cc-stat-label">Khiếu nại ca mới (SUBMITTED)</div>
          <div className="cc-stat-value danger">
            {disputes.filter((d) => d.status === 'SUBMITTED').length}
          </div>
          <div className="cc-stat-desc">Cần đối soát trong 2h</div>
        </div>

        <div className="cc-stat-card">
          <div className="cc-stat-label">Đang điều tra (IN_REVIEW)</div>
          <div className="cc-stat-value warning">
            {disputes.filter((d) => d.status === 'IN_REVIEW').length}
          </div>
          <div className="cc-stat-desc">Đã gửi yêu cầu sao kê</div>
        </div>

        <div className="cc-stat-card">
          <div className="cc-stat-label">Đã giải quyết thành công</div>
          <div className="cc-stat-value success">
            {disputes.filter((d) => d.status.startsWith('RESOLVED')).length}
          </div>
          <div className="cc-stat-desc success-text">Tỷ lệ hòa giải 98.4%</div>
        </div>
      </div>

      <div className="cc-table-section">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem', marginBottom: '1rem' }}>
          <h2 className="cc-table-title" style={{ margin: 0 }}>Danh Sách Hồ Sơ Khiếu Nại & Tranh Chấp</h2>

          {/* Filter Tabs */}
          <div className="cc-filter-tabs" style={{ marginBottom: 0, border: 'none' }}>
            <button
              className={`cc-tab-btn ${activeFilter === 'ALL' ? 'active' : ''}`}
              onClick={() => setActiveFilter('ALL')}
            >
              Tất Cả ({disputes.length})
            </button>
            <button
              className={`cc-tab-btn ${activeFilter === 'SUBMITTED' ? 'active' : ''}`}
              onClick={() => setActiveFilter('SUBMITTED')}
            >
              Chờ Xử Lý ({disputes.filter((d) => d.status === 'SUBMITTED').length})
            </button>
            <button
              className={`cc-tab-btn ${activeFilter === 'IN_REVIEW' ? 'active' : ''}`}
              onClick={() => setActiveFilter('IN_REVIEW')}
            >
              Đang Điều Tra ({disputes.filter((d) => d.status === 'IN_REVIEW').length})
            </button>
            <button
              className={`cc-tab-btn ${activeFilter === 'RESOLVED' ? 'active' : ''}`}
              onClick={() => setActiveFilter('RESOLVED')}
            >
              Đã Xử Lý ({disputes.filter((d) => d.status.startsWith('RESOLVED')).length})
            </button>
          </div>
        </div>

        <table className="cc-table">
          <thead>
            <tr>
              <th>Mã Khiếu Nại</th>
              <th>Nhân Sự (Worker)</th>
              <th>Ban Tổ Chức (EO)</th>
              <th>Sự Kiện & Ca Làm</th>
              <th>Số Tiền Tranh Chấp</th>
              <th>Thời Điểm</th>
              <th>Trạng Thái</th>
              <th>Thao Tác</th>
            </tr>
          </thead>
          <tbody>
            {filteredDisputes.map((d) => (
              <tr key={d.id}>
                <td className="cc-code-cell">{d.id}</td>
                <td className="cc-name-cell">{d.workerName}</td>
                <td>{d.eoName}</td>
                <td>
                  <strong>{d.shiftRole}</strong>
                  <div style={{ fontSize: '0.8rem', color: '#64748b' }}>{d.eventName}</div>
                </td>
                <td style={{ fontWeight: 700, color: '#ef4444' }}>
                  {d.disputedAmount.toLocaleString()} đ
                </td>
                <td style={{ fontSize: '0.825rem', color: '#64748b' }}>{d.createdAt}</td>
                <td>
                  <span className={`cc-status-badge ${d.status}`}>
                    {d.status === 'SUBMITTED'
                      ? 'Chờ Xử Lý'
                      : d.status === 'IN_REVIEW'
                      ? 'Đang Điều Tra'
                      : 'Đã Giải Quyết'}
                  </span>
                </td>
                <td>
                  <button
                    type="button"
                    className="cc-btn-action"
                    onClick={() => handleOpenModal(d)}
                  >
                    Xử Lý Vụ Việc
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Modal Xử Lý Tranh Chấp */}
      {selectedDispute && (
        <div className="cc-modal-overlay" onClick={() => setSelectedDispute(null)}>
          <div className="cc-modal-box" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                ⚖️ Hồ Sơ Tranh Chấp: {selectedDispute.id}
              </h3>
              <button
                type="button"
                style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#64748b' }}
                onClick={() => setSelectedDispute(null)}
              >
                &times;
              </button>
            </div>

            <div className="cc-detail-grid">
              <div className="cc-detail-item">
                <span>Người Khiếu Nại (Worker)</span>
                <strong>{selectedDispute.workerName}</strong>
              </div>
              <div className="cc-detail-item">
                <span>Ban Tổ Chức Bị Khiếu Nại (EO)</span>
                <strong>{selectedDispute.eoName}</strong>
              </div>
              <div className="cc-detail-item">
                <span>Sự Kiện & Ca Làm Việc</span>
                <strong>{selectedDispute.shiftRole}</strong> ({selectedDispute.eventName})
              </div>
              <div className="cc-detail-item">
                <span>Số Tiền Tranh Chấp</span>
                <strong style={{ color: '#ef4444' }}>{selectedDispute.disputedAmount.toLocaleString()} đ</strong>
              </div>
            </div>

            <div style={{ background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '10px', padding: '0.85rem', marginBottom: '1rem' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 700, color: '#b45309', marginBottom: '0.25rem' }}>
                NỘI DUNG KHIẾU NẠI TỪ WORKER:
              </div>
              <div style={{ fontSize: '0.875rem', color: '#78350f' }}>
                "{selectedDispute.reason}"
              </div>
            </div>

            <form onSubmit={handleResolveDispute}>
              <div style={{ marginBottom: '1rem' }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Hành Động Giải Quyết (CSKH Decision):
                </label>
                <select
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.9rem',
                  }}
                  value={selectedAction}
                  onChange={(e) => setSelectedAction(e.target.value)}
                >
                  <option value="SETTLE">Hòa giải thành công (EO đã bổ sung tiền/chuyển khoản đủ)</option>
                  <option value="IN_REVIEW">Yêu cầu EO nộp sao kê giao dịch trong 4h (Chuyển IN_REVIEW)</option>
                  <option value="PENALIZE">Xử phạt EO (Trừ điểm tín nhiệm, cấm tạo ca mới)</option>
                  <option value="REJECT">Bác khiếu nại (Worker khiếu nại sai/đã nhận đủ)</option>
                </select>
              </div>

              <div style={{ marginBottom: '1.25rem' }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, color: '#334155', marginBottom: '0.35rem' }}>
                  Ghi Chú Kết Luận & Biên Bản Đối Soát:
                </label>
                <textarea
                  rows="3"
                  style={{
                    width: '100%',
                    padding: '0.65rem',
                    borderRadius: '8px',
                    border: '1px solid #cbd5e1',
                    fontSize: '0.875rem',
                    boxSizing: 'border-box',
                  }}
                  value={resolutionNote}
                  onChange={(e) => setResolutionNote(e.target.value)}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                <button
                  type="button"
                  className="cani-modal-btn-cancel"
                  onClick={() => setSelectedDispute(null)}
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  className="cani-modal-btn-confirm primary"
                >
                  Cập Nhật Kết Quả Xử Lý
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default CustomerCarePage;
