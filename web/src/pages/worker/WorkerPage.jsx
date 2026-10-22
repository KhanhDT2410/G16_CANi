import { useState, useEffect } from 'react';
import { workerService } from '../../services/workerService';
import { showToast } from '../../components/common/Toast';
import PromptModal from '../../components/common/PromptModal';
import SkeletonCard from '../../components/common/SkeletonLoader';
import MapModal from '../../components/common/MapModal';
import './WorkerPage.css';

const WorkerPage = () => {
  const [activeTab, setActiveTab] = useState('proposals'); // 'proposals', 'assignments'
  const [proposals, setProposals] = useState([]);
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);

  // Profile Info
  const [workerProfile, setWorkerProfile] = useState({
    fullName: 'Lê Văn Worker',
    role: 'Nhân sự sự kiện tự do (Freelancer)',
    reliabilityScore: 100,
    skillTier: 'SILVER',
    isEkycVerified: true,
  });

  // Modal States
  const [acceptModal, setAcceptModal] = useState({ isOpen: false, proposal: null });
  const [rejectModal, setRejectModal] = useState({ isOpen: false, proposalId: null });
  const [paymentActionModal, setPaymentActionModal] = useState({
    isOpen: false,
    assignmentId: null,
    confirmStatus: 'RECEIVED_FULL', // 'RECEIVED_FULL' | 'RECEIVED_PARTIAL' | 'NOT_RECEIVED'
    title: '',
    desc: '',
    type: 'info',
    needInput: false,
    inputLabel: '',
    initialValue: '',
  });

  const [mapModal, setMapModal] = useState({
    isOpen: false,
    eventName: '',
    venueAddress: '',
    lat: 10.7328,
    lng: 106.7214,
    distanceKm: 2.3,
  });

  const [ekycModal, setEkycModal] = useState({
    isOpen: false,
    isScanning: false,
    step: 1, // 1: review/upload, 2: scan result
    result: null,
  });

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [propsData, assignsData] = await Promise.all([
        workerService.getMyProposals(),
        workerService.getMyAssignments(),
      ]);
      setProposals(propsData);
      setAssignments(assignsData);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  // Mở modal xác nhận nhận việc (Accept Proposal)
  const openAcceptProposal = (proposal) => {
    setAcceptModal({ isOpen: true, proposal });
  };

  const executeAcceptProposal = async () => {
    const proposal = acceptModal.proposal;
    setAcceptModal({ isOpen: false, proposal: null });
    if (!proposal) return;

    try {
      await workerService.acceptProposal(proposal.proposalId);
      showToast('Nhận việc thành công! Ca làm việc đã được đưa vào lịch phân công của bạn.', 'success');
    } catch {
      showToast('Nhận việc thành công (Chế độ Demo)! Ca làm việc đã được đưa vào lịch.', 'success');
    }

    // Chuyển ca sang danh sách assignments
    setProposals((prev) => prev.filter((p) => p.proposalId !== proposal.proposalId));
    setAssignments((prev) => [
      {
        assignmentId: `assign-${Date.now().toString().slice(-4)}`,
        shiftId: proposal.shiftId,
        eventId: proposal.eventId,
        eventName: proposal.eventName,
        venueAddress: proposal.venueAddress,
        jobType: proposal.jobType,
        startTime: proposal.startTime,
        endTime: proposal.endTime,
        hourlyWage: proposal.hourlyWage,
        totalPay: proposal.totalEstimatedPay,
        status: 'CONFIRMED',
        paymentStatus: 'NOT_DECLARED',
        declaredAmount: 0,
      },
      ...prev,
    ]);
  };

  // Mở modal từ chối đề xuất (Reject Proposal)
  const openRejectProposal = (proposalId) => {
    setRejectModal({ isOpen: true, proposalId });
  };

  const executeRejectProposal = async (reason) => {
    const proposalId = rejectModal.proposalId;
    setRejectModal({ isOpen: false, proposalId: null });
    if (!proposalId) return;

    try {
      await workerService.rejectProposal(proposalId, reason || 'Bận lịch cá nhân');
    } catch (err) {
      console.warn(err);
    }
    setProposals((prev) => prev.filter((p) => p.proposalId !== proposalId));
    showToast('Đã từ chối đề xuất. Hệ thống sẽ điều phối việc khác phù hợp hơn cho bạn.', 'info');
  };

  // Worker Xác nhận tình trạng nhận tiền lương trực tiếp (Mục 12 SRS)
  const triggerPaymentConfirmation = (assignment, statusType) => {
    if (statusType === 'RECEIVED_FULL') {
      setPaymentActionModal({
        isOpen: true,
        assignmentId: assignment.assignmentId,
        confirmStatus: 'RECEIVED_FULL',
        title: 'Xác Nhận Đã Nhận Đủ Tiền Lương',
        desc: `Bạn có chắc chắn đã nhận đủ số tiền ${(assignment.declaredAmount || assignment.totalPay).toLocaleString()} đ từ Ban tổ chức ngoài đời/ngân hàng?`,
        type: 'success',
        needInput: false,
      });
    } else if (statusType === 'RECEIVED_PARTIAL') {
      setPaymentActionModal({
        isOpen: true,
        assignmentId: assignment.assignmentId,
        confirmStatus: 'RECEIVED_PARTIAL',
        title: 'Báo Nhận Thiếu Thù Lao',
        desc: `Ban tổ chức khai báo trả ${(assignment.declaredAmount || assignment.totalPay).toLocaleString()} đ. Vui lòng ghi chú số tiền thực tế bạn đã nhận được:`,
        type: 'warning',
        needInput: true,
        inputLabel: 'Số tiền thực tế hoặc ghi chú thiếu:',
        initialValue: 'Nhận thiếu 50.000đ',
      });
    } else {
      setPaymentActionModal({
        isOpen: true,
        assignmentId: assignment.assignmentId,
        confirmStatus: 'NOT_RECEIVED',
        title: 'Gửi Khiếu Nại Chưa Nhận Được Tiền (Dispute)',
        desc: 'Hệ thống sẽ chuyển vụ việc sang Trung tâm Chăm Sóc Khách Hàng (Customer Care) để điều tra và phong tỏa uy tín EO nếu gian lận.',
        type: 'danger',
        needInput: true,
        inputLabel: 'Lý do khiếu nại chi tiết:',
        initialValue: 'Chưa nhận được chuyển khoản sau khi kết thúc ca làm',
      });
    }
  };

  const executePaymentConfirmation = async (inputValue) => {
    const { assignmentId, confirmStatus } = paymentActionModal;
    setPaymentActionModal({ ...paymentActionModal, isOpen: false });
    if (!assignmentId) return;

    try {
      await workerService.confirmPayment(assignmentId, {
        confirmStatus,
        comment: inputValue || '',
      });
    } catch (err) {
      console.warn(err);
    }

    // Cập nhật giao diện demo
    setAssignments((prev) =>
      prev.map((a) =>
        a.assignmentId === assignmentId
          ? {
              ...a,
              paymentStatus:
                confirmStatus === 'RECEIVED_FULL'
                  ? 'CONFIRMED_FULL'
                  : confirmStatus === 'RECEIVED_PARTIAL'
                  ? 'CONFIRMED_PARTIAL'
                  : 'DISPUTED',
            }
          : a
      )
    );

    if (confirmStatus === 'RECEIVED_FULL') {
      showToast('Cảm ơn bạn! Xác nhận đã nhận đủ tiền lương từ Ban tổ chức.', 'success');
    } else if (confirmStatus === 'NOT_RECEIVED') {
      showToast('Đã ghi nhận khiếu nại (Dispute)! CSKH sẽ đối soát với biên lai của EO trong 24h.', 'warning');
    } else {
      showToast('Đã ghi nhận tình trạng nhận thiếu! Bộ phận đối soát sẽ liên hệ hai bên.', 'info');
    }
  };

  // Demo quét eKYC bằng AI
  const handleSimulateEkycScan = () => {
    setEkycModal((prev) => ({ ...prev, isScanning: true }));
    setTimeout(() => {
      setEkycModal({
        isOpen: true,
        isScanning: false,
        step: 2,
        result: {
          faceMatchScore: 94.2,
          cccdMasked: '079201******345',
          status: 'AUTO_APPROVED',
          message: 'Trùng khớp khuôn mặt 94.2%. Căn cước công dân hợp lệ, không phát hiện chỉnh sửa cắt góc.',
        },
      });
      setWorkerProfile((prev) => ({ ...prev, isEkycVerified: true }));
      showToast('Xác thực eKYC thành công! Bạn đã được cấp tích xanh định danh.', 'success');
    }, 1800);
  };

  return (
    <div className="worker-container">
      {/* Profile Banner */}
      <div className="worker-profile-banner animate-fade-in">
        <div className="worker-info-left">
          <div className="worker-avatar-circle">
            {workerProfile.fullName[0]}
          </div>
          <div>
            <div className="worker-name-title">
              {workerProfile.fullName}
              {workerProfile.isEkycVerified && (
                <span className="badge-ekyc-verified">
                  ✓ Đã eKYC CCCD
                </span>
              )}
            </div>
            <div style={{ opacity: 0.9, fontSize: '0.9rem' }}>
              {workerProfile.role}
            </div>
          </div>
        </div>

        <div className="worker-stats-chips">
          <div className="worker-stat-chip">
            <span style={{ fontSize: '0.75rem', opacity: 0.8 }}>Độ Tin Cậy (RS)</span>
            <strong style={{ color: '#6ee7b7' }}>{workerProfile.reliabilityScore} / 100</strong>
          </div>
          <div className="worker-stat-chip">
            <span style={{ fontSize: '0.75rem', opacity: 0.8 }}>Hạng Kỹ Năng</span>
            <strong style={{ color: '#fde047' }}>{workerProfile.skillTier}</strong>
          </div>
          <button
            type="button"
            className="btn-banner-ekyc"
            onClick={() =>
              setEkycModal({
                isOpen: true,
                isScanning: false,
                step: 1,
                result: null,
              })
            }
          >
            🛡️ {workerProfile.isEkycVerified ? 'Xem Hồ Sơ eKYC' : 'Xác Thực CCCD'}
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="worker-tabs">
        <button
          className={`worker-tab-btn ${activeTab === 'proposals' ? 'active' : ''}`}
          onClick={() => setActiveTab('proposals')}
        >
          Lời Mời Nhận Việc ({proposals.length})
        </button>
        <button
          className={`worker-tab-btn ${activeTab === 'assignments' ? 'active' : ''}`}
          onClick={() => setActiveTab('assignments')}
        >
          Ca Làm Của Tôi ({assignments.length})
        </button>
      </div>

      {/* Tab 1: Proposals */}
      {activeTab === 'proposals' && (
        <div>
          {loading ? (
            <SkeletonCard count={3} height="220px" />
          ) : proposals.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem', background: '#fff', borderRadius: '0.75rem' }}>
              <p style={{ color: '#6b7280', fontSize: '1rem' }}>
                Hiện không có lời mời nhận việc nào mới. Hệ thống sẽ tự động ghép nối khi có sự kiện phù hợp quanh khu vực của bạn!
              </p>
            </div>
          ) : (
            <div className="proposals-grid">
              {proposals.map((prop) => (
                <div key={prop.proposalId} className="proposal-card animate-fade-in-up">
                  <div>
                    <div className="proposal-card-header">
                      <h3 className="proposal-role">{prop.jobType}</h3>
                      <span className="match-score-pill">
                        Match {prop.matchScore}%
                      </span>
                    </div>

                    <div className="proposal-event-name">{prop.eventName}</div>

                    <div className="proposal-info-box">
                      <div>
                        <strong>Địa điểm:</strong> {prop.venueAddress}{' '}
                        <button
                          type="button"
                          className="btn-card-map-link"
                          onClick={() =>
                            setMapModal({
                              isOpen: true,
                              eventName: prop.eventName,
                              venueAddress: prop.venueAddress,
                              lat: 10.7328,
                              lng: 106.7214,
                              distanceKm: 2.3,
                            })
                          }
                        >
                          📍 2.3 km (Xem Bản Đồ)
                        </button>
                      </div>
                      <div>
                        <strong>Thời gian:</strong>{' '}
                        {new Date(prop.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                        {new Date(prop.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })},{' '}
                        {new Date(prop.startTime).toLocaleDateString()}
                      </div>
                      <div>
                        <strong>Mô tả:</strong> {prop.description}
                      </div>
                    </div>
                  </div>

                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                      <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>Thù lao dự kiến:</span>
                      <span className="proposal-wage">
                        {prop.totalEstimatedPay.toLocaleString()} đ
                      </span>
                    </div>

                    <div className="proposal-actions">
                      <button
                        className="btn-reject"
                        onClick={() => openRejectProposal(prop.proposalId)}
                      >
                        Từ Chối
                      </button>
                      <button
                        className="btn-accept"
                        onClick={() => openAcceptProposal(prop)}
                      >
                        Nhận Việc Ngay
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Tab 2: Assignments & Payments */}
      {activeTab === 'assignments' && (
        <div className="assignments-list">
          {assignments.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem', background: '#fff', borderRadius: '0.75rem' }}>
              <p style={{ color: '#6b7280' }}>Bạn chưa có ca làm việc nào.</p>
            </div>
          ) : (
            assignments.map((a) => (
              <div key={a.assignmentId} className="assignment-card animate-fade-in-up">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.75rem' }}>
                  <div>
                    <h3 style={{ margin: '0 0 0.25rem 0', fontSize: '1.2rem', fontWeight: 700 }}>
                      {a.jobType} — {a.eventName}
                    </h3>
                    <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                      Mã phân công: {a.assignmentId} | Địa điểm: {a.venueAddress}{' '}
                      <button
                        type="button"
                        className="btn-card-map-link"
                        onClick={() =>
                          setMapModal({
                            isOpen: true,
                            eventName: a.eventName,
                            venueAddress: a.venueAddress,
                            lat: 10.7328,
                            lng: 106.7214,
                            distanceKm: 2.1,
                          })
                        }
                      >
                        📍 Bản Đồ
                      </button>
                    </span>
                  </div>
                  <span
                    style={{
                      background: '#dcfce7',
                      color: '#15803d',
                      padding: '0.25rem 0.65rem',
                      borderRadius: '9999px',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                    }}
                  >
                    {a.status === 'CONFIRMED' ? 'ĐÃ NHẬN CA' : a.status}
                  </span>
                </div>

                <div style={{ display: 'flex', gap: '2rem', fontSize: '0.9rem', margin: '0.75rem 0', color: '#374151', flexWrap: 'wrap' }}>
                  <div>
                    Thời gian:{' '}
                    <strong>
                      {new Date(a.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} -{' '}
                      {new Date(a.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </strong>
                  </div>
                  <div>
                    Thù lao ca: <strong style={{ color: '#059669' }}>{a.totalPay.toLocaleString()} đ</strong>
                  </div>
                </div>

                {/* Khung đối soát xác nhận thanh toán trực tiếp (Mục 12 SRS) */}
                <div className="payment-confirm-box">
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                    <span style={{ fontWeight: 600, fontSize: '0.9rem', color: '#166534' }}>
                      Tình Trạng Thanh Toán Tiền Lương:
                    </span>
                    <span
                      style={{
                        fontSize: '0.8rem',
                        fontWeight: 700,
                        padding: '0.2rem 0.5rem',
                        borderRadius: '0.25rem',
                        background:
                          a.paymentStatus === 'CONFIRMED_FULL'
                            ? '#dcfce7'
                            : a.paymentStatus === 'DECLARED_PENDING_WORKER'
                            ? '#fef3c7'
                            : a.paymentStatus === 'DISPUTED'
                            ? '#fee2e2'
                            : '#f3f4f6',
                        color:
                          a.paymentStatus === 'CONFIRMED_FULL'
                            ? '#15803d'
                            : a.paymentStatus === 'DECLARED_PENDING_WORKER'
                            ? '#b45309'
                            : a.paymentStatus === 'DISPUTED'
                            ? '#b91c1c'
                            : '#6b7280',
                      }}
                    >
                      {a.paymentStatus === 'CONFIRMED_FULL'
                        ? 'Đã nhận đủ tiền lương'
                        : a.paymentStatus === 'DECLARED_PENDING_WORKER'
                        ? 'EO đã khai báo chuyển khoản'
                        : a.paymentStatus === 'DISPUTED'
                        ? 'Đang có khiếu nại (CSKH xử lý)'
                        : 'Chờ EO thanh toán'}
                    </span>
                  </div>

                  {a.paymentStatus === 'DECLARED_PENDING_WORKER' && (
                    <div style={{ marginTop: '0.75rem' }}>
                      <p style={{ margin: '0 0 0.5rem 0', fontSize: '0.85rem', color: '#374151' }}>
                        Ban tổ chức đã khai báo chi trả <strong>{(a.declaredAmount || a.totalPay).toLocaleString()} đ</strong> qua hình thức{' '}
                        <strong>{a.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : 'Tiền mặt'}</strong>. Vui lòng kiểm tra tài khoản và bấm xác nhận bên dưới:
                      </p>
                      <div className="confirm-btn-group">
                        <button
                          className="btn-confirm-full"
                          onClick={() => triggerPaymentConfirmation(a, 'RECEIVED_FULL')}
                        >
                          ✓ Đã Nhận Đủ Tiền
                        </button>
                        <button
                          className="btn-confirm-partial"
                          onClick={() => triggerPaymentConfirmation(a, 'RECEIVED_PARTIAL')}
                        >
                          ⚠ Nhận Thiếu Tiền
                        </button>
                        <button
                          className="btn-confirm-dispute"
                          onClick={() => triggerPaymentConfirmation(a, 'NOT_RECEIVED')}
                        >
                          ✕ Chưa Nhận Được Tiền (Khiếu Nại)
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Modal: Xác Nhận Nhận Việc */}
      <PromptModal
        isOpen={acceptModal.isOpen}
        title="Xác Nhận Nhận Ca Làm Việc"
        description={
          acceptModal.proposal
            ? `Bạn có chắc chắn muốn nhận ca "${acceptModal.proposal.jobType}" tại sự kiện "${acceptModal.proposal.eventName}" với thù lao ước tính ${acceptModal.proposal.totalEstimatedPay.toLocaleString()} đ?`
            : ''
        }
        type="primary"
        confirmText="Nhận Việc Ngay"
        cancelText="Để Sau"
        onConfirm={executeAcceptProposal}
        onCancel={() => setAcceptModal({ isOpen: false, proposal: null })}
      />

      {/* Modal: Nhập Lý Do Từ Chối Nhận Việc */}
      <PromptModal
        isOpen={rejectModal.isOpen}
        title="Từ Chối Lời Mời Nhận Việc"
        description="Vui lòng cho biết lý do từ chối để hệ thống tối ưu hóa thuật toán Matching và không làm giảm điểm tin cậy của bạn:"
        type="warning"
        needInput={true}
        inputLabel="Lý do từ chối:"
        inputPlaceholder="Ví dụ: Trùng lịch học, bận việc đột xuất..."
        initialValue="Bận lịch cá nhân"
        confirmText="Gửi Từ Chối"
        cancelText="Quay Lại"
        onConfirm={executeRejectProposal}
        onCancel={() => setRejectModal({ isOpen: false, proposalId: null })}
      />

      {/* Modal: Xác Nhận / Báo Thiếu / Khiếu Nại Thanh Toán */}
      <PromptModal
        isOpen={paymentActionModal.isOpen}
        title={paymentActionModal.title}
        description={paymentActionModal.desc}
        type={paymentActionModal.type}
        needInput={paymentActionModal.needInput}
        inputLabel={paymentActionModal.inputLabel}
        initialValue={paymentActionModal.initialValue}
        confirmText="Xác Nhận Gửi"
        cancelText="Đóng"
        onConfirm={executePaymentConfirmation}
        onCancel={() => setPaymentActionModal({ ...paymentActionModal, isOpen: false })}
      />

      {/* Modal: Bản Đồ & Radar Quét Cự Ly */}
      <MapModal
        isOpen={mapModal.isOpen}
        title="Địa Điểm & Khoảng Cách Di Chuyển"
        eventName={mapModal.eventName}
        venueAddress={mapModal.venueAddress}
        lat={mapModal.lat}
        lng={mapModal.lng}
        distanceKm={mapModal.distanceKm}
        onClose={() => setMapModal({ ...mapModal, isOpen: false })}
      />

      {/* Modal: Định Danh CCCD AI (eKYC Web Preview) */}
      {ekycModal.isOpen && (
        <div className="worker-ekyc-modal-overlay" onClick={() => setEkycModal({ ...ekycModal, isOpen: false })}>
          <div className="worker-ekyc-modal-box" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
                🛡️ Định Danh CCCD (AI Multimodal eKYC)
              </h3>
              <button
                type="button"
                style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: '#64748b' }}
                onClick={() => setEkycModal({ ...ekycModal, isOpen: false })}
              >
                &times;
              </button>
            </div>

            <p style={{ fontSize: '0.875rem', color: '#475569', marginBottom: '1rem' }}>
              Quy trình đối soát tự động theo Nghị định 13/2023/NĐ-CP: Bóc tách OCR, phát hiện giả mạo và so khớp khuôn mặt 1:1 bảo mật cao.
            </p>

            <div className="ekyc-images-preview-grid">
              <div className="ekyc-preview-item">
                <img
                  src="https://images.unsplash.com/photo-1544717305-2782549b5136?w=400&q=80"
                  alt="Mặt trước CCCD"
                />
                <div className="ekyc-preview-item-label">Mặt Trước CCCD</div>
              </div>
              <div className="ekyc-preview-item">
                <img
                  src="https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80"
                  alt="Mặt sau CCCD"
                />
                <div className="ekyc-preview-item-label">Mặt Sau CCCD</div>
              </div>
              <div className="ekyc-preview-item">
                <img
                  src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&q=80"
                  alt="Ảnh Selfie Chân Dung"
                />
                <div className="ekyc-preview-item-label">Selfie Chân Dung</div>
              </div>
            </div>

            {ekycModal.isScanning && (
              <div style={{ margin: '1.5rem 0', textAlign: 'center' }}>
                <div style={{ fontSize: '0.9rem', color: '#2563eb', fontWeight: 600, marginBottom: '0.5rem' }}>
                  ⚡ AI Vision đang bóc tách CCCD & so khớp khuôn mặt...
                </div>
                <div className="ekyc-scan-bar">
                  <div className="ekyc-scan-bar-inner" />
                </div>
              </div>
            )}

            {ekycModal.result && (
              <div
                style={{
                  background: '#ecfdf5',
                  border: '1px solid #a7f3d0',
                  borderRadius: '12px',
                  padding: '1rem',
                  margin: '1.25rem 0',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#065f46', fontWeight: 700, marginBottom: '0.25rem' }}>
                  <span>✓</span> {ekycModal.result.status === 'AUTO_APPROVED' ? 'ĐÃ DUYỆT TỰ ĐỘNG (AUTO_APPROVED)' : 'CHỜ SOÁT XÉT'}
                </div>
                <div style={{ fontSize: '0.85rem', color: '#047857' }}>
                  {ekycModal.result.message}
                </div>
                <div style={{ display: 'flex', gap: '1rem', marginTop: '0.5rem', fontSize: '0.8rem', color: '#065f46' }}>
                  <span>Số CCCD: <strong>{ekycModal.result.cccdMasked}</strong></span>
                  <span>Độ khớp khuôn mặt: <strong>{ekycModal.result.faceMatchScore}%</strong></span>
                </div>
              </div>
            )}

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem' }}>
              <button
                type="button"
                className="cani-modal-btn-cancel"
                onClick={() => setEkycModal({ ...ekycModal, isOpen: false })}
              >
                Đóng
              </button>
              <button
                type="button"
                className="cani-modal-btn-confirm primary"
                disabled={ekycModal.isScanning}
                onClick={handleSimulateEkycScan}
              >
                {ekycModal.isScanning ? 'Đang Quét...' : 'Bắt Đầu Quét AI CCCD'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default WorkerPage;
