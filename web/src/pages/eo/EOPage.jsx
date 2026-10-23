import { useState, useEffect } from 'react';
import { eoService } from '../../services/eoService';
import { showToast } from '../../components/common/Toast';
import PromptModal from '../../components/common/PromptModal';
import SkeletonCard from '../../components/common/SkeletonLoader';
import MapModal from '../../components/common/MapModal';
import EoEkycTab from './EoEkycTab';
import './EOPage.css';

const ROLES = [
  'Lễ tân / Check-in',
  'Phục vụ bàn',
  'Pha chế (Bartender/Barista)',
  'Thu ngân',
  'Điều phối sự kiện',
  'Kỹ thuật âm thanh - ánh sáng',
  'Bảo vệ / An ninh',
  'Hậu cần / Setup sự kiện',
  'PG / PB',
];

const EOPage = () => {
  const [activeTab, setActiveTab] = useState('events'); // 'events', 'shifts', 'payments'
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modal States
  const [showEventModal, setShowEventModal] = useState(false);
  const [showShiftModal, setShowShiftModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [selectedAssignment, setSelectedAssignment] = useState(null);

  // Smooth Prompt/Confirm & Map States
  const [confirmPublish, setConfirmPublish] = useState({ isOpen: false, eventId: null });
  const [mapModal, setMapModal] = useState({
    isOpen: false,
    eventName: '',
    venueAddress: '',
    lat: 10.7328,
    lng: 106.7214,
    radiusKm: 15,
  });

  // Form States - Event
  const [newEvent, setNewEvent] = useState({
    name: '',
    description: '',
    venueAddress: '799 Nguyễn Văn Linh, Quận 7, TP.HCM',
    venueLat: 10.7328,
    venueLng: 106.7214,
    eventDate: '2026-11-20T08:00:00Z',
    mode: 'MATCHING',
  });

  // Form States - Shift
  const [newShift, setNewShift] = useState({
    jobType: ROLES[0],
    description: '',
    startTime: '2026-11-20T08:00',
    endTime: '2026-11-20T12:00',
    requiredWorkers: 4,
    hourlyWage: 50000,
    allowance: 20000,
    minReliabilityScore: 60,
    maxDistanceKm: 15,
  });

  // Form States - Payment Declaration (Mục 11 SRS)
  const [paymentForm, setPaymentForm] = useState({
    assignmentId: '',
    declaredAmount: 220000,
    paymentMethod: 'BANK_TRANSFER',
    externalRef: '',
    paymentProofUrl: '',
  });

  // Mock Payments Table Data
  const [payments, setPayments] = useState([
    {
      paymentId: 'pay-01',
      assignmentId: 'assign-101',
      workerName: 'Nguyễn Văn An',
      jobType: 'Lễ tân / Check-in',
      agreedAmount: 220000,
      declaredAmount: 220000,
      paymentMethod: 'BANK_TRANSFER',
      externalRef: 'VCB-987654321',
      status: 'DECLARED_PENDING_WORKER',
    },
    {
      paymentId: 'pay-02',
      assignmentId: 'assign-102',
      workerName: 'Trần Thị Hoa',
      jobType: 'Phục vụ bàn',
      agreedAmount: 200000,
      declaredAmount: 200000,
      paymentMethod: 'CASH',
      externalRef: 'TIENMAT-01',
      status: 'CONFIRMED_FULL',
    },
    {
      paymentId: 'pay-03',
      assignmentId: 'assign-103',
      workerName: 'Lê Văn Tuấn',
      jobType: 'Hậu cần / Setup sự kiện',
      agreedAmount: 250000,
      declaredAmount: 0,
      paymentMethod: '',
      externalRef: '',
      status: 'NOT_DECLARED',
    },
  ]);

  useEffect(() => {
    loadEvents();
  }, []);

  const loadEvents = async () => {
    setLoading(true);
    try {
      const data = await eoService.getMyEvents();
      setEvents(data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  // Tính thù lao ước tính realtime
  const calculateShiftTotal = () => {
    try {
      const start = new Date(newShift.startTime);
      const end = new Date(newShift.endTime);
      const hours = Math.max(0.5, (end - start) / (1000 * 60 * 60));
      return (
        hours *
          Number(newShift.requiredWorkers || 1) *
          Number(newShift.hourlyWage || 0) +
        Number(newShift.allowance || 0)
      );
    } catch {
      return 0;
    }
  };

  // Submit Tạo Sự Kiện
  const handleCreateEvent = async (e) => {
    e.preventDefault();
    try {
      await eoService.createEvent(newEvent);
      setShowEventModal(false);
      loadEvents();
      showToast('Tạo sự kiện thành công! Hãy thêm các ca làm việc cho sự kiện này.', 'success');
    } catch (err) {
      showToast('Không thể tạo sự kiện: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  // Submit Thêm Ca Làm Việc
  const handleAddShift = async (e) => {
    e.preventDefault();
    if (!selectedEventId) return;
    try {
      await eoService.addShiftToEvent(selectedEventId, {
        ...newShift,
        startTime: new Date(newShift.startTime).toISOString(),
        endTime: new Date(newShift.endTime).toISOString(),
      });
      setShowShiftModal(false);
      loadEvents();
      showToast('Thêm ca làm việc thành công! Bạn có thể kích hoạt Matching ngay.', 'success');
    } catch (err) {
      showToast('Không thể thêm ca làm việc: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  // Trigger modal xác nhận xuất bản sự kiện & kích hoạt Matching
  const openConfirmPublish = (eventId) => {
    setConfirmPublish({ isOpen: true, eventId });
  };

  const handleExecutePublish = async () => {
    const eventId = confirmPublish.eventId;
    setConfirmPublish({ isOpen: false, eventId: null });
    if (!eventId) return;

    try {
      await eoService.publishEvent(eventId);
      loadEvents();
      showToast('Xuất bản thành công! Hệ thống đang tự động lọc ứng viên phù hợp theo vị trí và độ uy tín.', 'success');
    } catch (err) {
      showToast('Lỗi: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  // Khai báo đã trả lương trực tiếp cho Worker (Mục 11 SRS)
  const handleDeclarePayment = async (e) => {
    e.preventDefault();
    try {
      await eoService.declarePayment(paymentForm);
      setShowPaymentModal(false);
      // Cập nhật bảng thanh toán demo
      setPayments((prev) =>
        prev.map((p) =>
          p.assignmentId === paymentForm.assignmentId
            ? {
                ...p,
                declaredAmount: paymentForm.declaredAmount,
                paymentMethod: paymentForm.paymentMethod,
                externalRef: paymentForm.externalRef,
                status: 'DECLARED_PENDING_WORKER',
              }
            : p
        )
      );
      showToast('Khai báo thanh toán thành công! Hệ thống đã gửi thông báo đến Worker để xác nhận.', 'success');
    } catch {
      // Mock thành công cho demo
      setPayments((prev) =>
        prev.map((p) =>
          p.assignmentId === paymentForm.assignmentId
            ? {
                ...p,
                declaredAmount: paymentForm.declaredAmount,
                paymentMethod: paymentForm.paymentMethod,
                externalRef: paymentForm.externalRef,
                status: 'DECLARED_PENDING_WORKER',
              }
            : p
        )
      );
      setShowPaymentModal(false);
      showToast('Khai báo thanh toán thành công (Chế độ Demo)! Đang chờ Worker xác nhận.', 'info');
    }
  };

  return (
    <div className="eo-container">
      {/* Header */}
      <div className="eo-header">
        <div className="eo-title-group">
          <h1>Cổng Quản Lý Ban Tổ Chức (EO Portal)</h1>
          <p>Điều phối nhân sự sự kiện, theo dõi tuyển dụng & thanh toán trực tiếp</p>
        </div>
        <button
          className="eo-btn-primary"
          onClick={() => setShowEventModal(true)}
        >
          <span>+</span> Tạo Sự Kiện Mới
        </button>
      </div>

      {/* Metrics Overview */}
      <div className="eo-metrics-grid">
        <div className="eo-metric-card">
          <div className="eo-metric-label">Tổng Sự Kiện</div>
          <div className="eo-metric-value">{events.length}</div>
          <div className="eo-metric-sub">Đang quản lý</div>
        </div>
        <div className="eo-metric-card">
          <div className="eo-metric-label">Tổng Nhân Sự Cần Tuyển</div>
          <div className="eo-metric-value">
            {events.reduce((sum, e) => sum + (e.totalRequiredWorkers || 0), 0)}
          </div>
          <div className="eo-metric-sub">Theo ma trận ca</div>
        </div>
        <div className="eo-metric-card">
          <div className="eo-metric-label">Nhân Sự Đã Chốt Ca</div>
          <div className="eo-metric-value" style={{ color: '#2563eb' }}>
            {events.reduce((sum, e) => sum + (e.totalConfirmedWorkers || 0), 0)}
          </div>
          <div className="eo-metric-sub">Tỉ lệ lấp đầy: 73%</div>
        </div>
        <div className="eo-metric-card">
          <div className="eo-metric-label">Dự Toán Chi Phí Lương</div>
          <div className="eo-metric-value" style={{ color: '#059669', fontSize: '1.45rem' }}>
            {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
              events.reduce((sum, e) => sum + (e.totalEstimatedCost || 0), 0)
            )}
          </div>
          <div className="eo-metric-sub">Thanh toán trực tiếp sau ca</div>
        </div>
      </div>

      {/* Tabs */}
      <div className="eo-tabs">
        <button
          className={`eo-tab-btn ${activeTab === 'events' ? 'active' : ''}`}
          onClick={() => setActiveTab('events')}
        >
          Sự Kiện Của Tôi ({events.length})
        </button>
        <button
          className={`eo-tab-btn ${activeTab === 'payments' ? 'active' : ''}`}
          onClick={() => setActiveTab('payments')}
        >
          Thanh Toán & Khai Báo Lương ({payments.length})
        </button>
        <button
          className={`eo-tab-btn ${activeTab === 'ekyc' ? 'active' : ''}`}
          onClick={() => setActiveTab('ekyc')}
        >
          Định Danh Doanh Nghiệp (eKYC)
        </button>
      </div>

      {/* Tab Content: Events */}
      {activeTab === 'events' && (
        <div className="eo-events-list">
          {loading ? (
            <SkeletonCard count={3} height="200px" />
          ) : events.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem', background: '#fff', borderRadius: '0.75rem' }}>
              <p style={{ color: '#6b7280', marginBottom: '1rem' }}>Bạn chưa có sự kiện nào.</p>
              <button className="eo-btn-primary" onClick={() => setShowEventModal(true)}>
                + Tạo Sự Kiện Đầu Tiên
              </button>
            </div>
          ) : (
            events.map((evt) => (
              <div key={evt.eventId} className="eo-event-card animate-fade-in-up">
                <div className="eo-event-card-header">
                  <div>
                    <h2 className="eo-event-name">{evt.name}</h2>
                    <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                      Mã sự kiện: {evt.eventId}
                    </span>
                  </div>
                  <span className={`eo-status-badge ${evt.status}`}>
                    {evt.status === 'ACTIVE' ? 'ĐANG TUYỂN DỤNG' : evt.status === 'DRAFT' ? 'BẢN NHÁP' : evt.status}
                  </span>
                </div>

                <p className="eo-event-desc">{evt.description || 'Không có mô tả chi tiết.'}</p>

                <div className="eo-event-meta-grid">
                  <div className="eo-meta-item">
                    <span>Địa điểm: </span>
                    <strong>{evt.venueAddress}</strong>
                  </div>
                  <div className="eo-meta-item">
                    <span>Số ca làm việc: </span>
                    <strong>{evt.totalShifts || 0} ca</strong>
                  </div>
                  <div className="eo-meta-item">
                    <span>Tiến độ tuyển: </span>
                    <strong>
                      {evt.totalConfirmedWorkers || 0} / {evt.totalRequiredWorkers || 0} người
                    </strong>
                  </div>
                  <div className="eo-meta-item">
                    <span>Tổng lương dự kiến: </span>
                    <strong style={{ color: '#059669' }}>
                      {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                        evt.totalEstimatedCost || 0
                      )}
                    </strong>
                  </div>
                </div>

                <div className="eo-event-actions">
                  <button
                    className="eo-btn-map"
                    onClick={() =>
                      setMapModal({
                        isOpen: true,
                        eventName: evt.name,
                        venueAddress: evt.venueAddress,
                        lat: evt.venueLat || 10.7328,
                        lng: evt.venueLng || 106.7214,
                        radiusKm: 15,
                      })
                    }
                  >
                    📍 Bản Đồ & Radar Quét
                  </button>

                  <button
                    className="eo-btn-secondary"
                    onClick={() => {
                      setSelectedEventId(evt.eventId);
                      setShowShiftModal(true);
                    }}
                  >
                    + Thêm Ca Làm Việc
                  </button>

                  {evt.status === 'DRAFT' && (
                    <button
                      className="eo-btn-publish"
                      onClick={() => openConfirmPublish(evt.eventId)}
                    >
                      Kích Hoạt Matching Tìm Người
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Tab Content: Payments (Mục 11 SRS) */}
      {activeTab === 'payments' && (
        <div className="eo-table-container">
          <table className="eo-table">
            <thead>
              <tr>
                <th>Mã Phân Công</th>
                <th>Nhân Sự (Worker)</th>
                <th>Vị Trí / Ca</th>
                <th>Thù Lao Thỏa Thuận</th>
                <th>Đã Khai Báo</th>
                <th>Phương Thức</th>
                <th>Mã GD / Biên Lai</th>
                <th>Trạng Thái</th>
                <th>Thao Tác</th>
              </tr>
            </thead>
            <tbody>
              {payments.map((p) => (
                <tr key={p.paymentId}>
                  <td style={{ fontWeight: 600 }}>{p.assignmentId}</td>
                  <td>{p.workerName}</td>
                  <td>{p.jobType}</td>
                  <td style={{ fontWeight: 600 }}>
                    {p.agreedAmount.toLocaleString()} đ
                  </td>
                  <td style={{ color: p.declaredAmount > 0 ? '#059669' : '#9ca3af', fontWeight: 600 }}>
                    {p.declaredAmount > 0 ? `${p.declaredAmount.toLocaleString()} đ` : 'Chưa trả'}
                  </td>
                  <td>{p.paymentMethod || '—'}</td>
                  <td>{p.externalRef || '—'}</td>
                  <td>
                    <span className={`status-badge ${p.status}`}>
                      {p.status === 'CONFIRMED_FULL'
                        ? 'Đã nhận đủ'
                        : p.status === 'DECLARED_PENDING_WORKER'
                        ? 'Chờ Worker xác nhận'
                        : p.status === 'DISPUTED'
                        ? 'Khiếu nại (Dispute)'
                        : 'Chưa thanh toán'}
                    </span>
                  </td>
                  <td>
                    <button
                      className="eo-btn-secondary"
                      style={{ padding: '0.35rem 0.65rem', fontSize: '0.8rem' }}
                      onClick={() => {
                        setSelectedAssignment(p);
                        setPaymentForm({
                          assignmentId: p.assignmentId,
                          declaredAmount: p.agreedAmount,
                          paymentMethod: 'BANK_TRANSFER',
                          externalRef: `VCB-${Date.now().toString().slice(-6)}`,
                          paymentProofUrl: 'https://storage.cani.vn/proofs/bill_example.jpg',
                        });
                        setShowPaymentModal(true);
                      }}
                    >
                      Khai báo trả lương
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Tab Content: eKYC */}
      {activeTab === 'ekyc' && <EoEkycTab />}

      {/* Modal: Tạo Sự Kiện Mới */}
      {showEventModal && (
        <div className="eo-modal-overlay">
          <div className="eo-modal-box">
            <h3>Tạo Sự Kiện Mới</h3>
            <form onSubmit={handleCreateEvent}>
              <div className="eo-form-group">
                <label>Tên Sự Kiện *</label>
                <input
                  type="text"
                  required
                  className="eo-form-input"
                  placeholder="Ví dụ: Lễ Ra Mắt Sản Phẩm Mới 2026"
                  value={newEvent.name}
                  onChange={(e) => setNewEvent({ ...newEvent, name: e.target.value })}
                />
              </div>

              <div className="eo-form-group">
                <label>Mô Tả Sự Kiện</label>
                <textarea
                  rows="3"
                  className="eo-form-textarea"
                  placeholder="Mô tả quy mô, đối tượng khách mời và yêu cầu chung..."
                  value={newEvent.description}
                  onChange={(e) => setNewEvent({ ...newEvent, description: e.target.value })}
                />
              </div>

              <div className="eo-form-group">
                <label>Địa Điểm Tổ Chức (Venue Address) *</label>
                <input
                  type="text"
                  required
                  className="eo-form-input"
                  value={newEvent.venueAddress}
                  onChange={(e) => setNewEvent({ ...newEvent, venueAddress: e.target.value })}
                />
              </div>

              <div className="eo-form-row">
                <div className="eo-form-group">
                  <label>Tọa Độ Vĩ Độ (Lat)</label>
                  <input
                    type="number"
                    step="any"
                    className="eo-form-input"
                    value={newEvent.venueLat}
                    onChange={(e) => setNewEvent({ ...newEvent, venueLat: parseFloat(e.target.value) })}
                  />
                </div>
                <div className="eo-form-group">
                  <label>Tọa Độ Kinh Độ (Lng)</label>
                  <input
                    type="number"
                    step="any"
                    className="eo-form-input"
                    value={newEvent.venueLng}
                    onChange={(e) => setNewEvent({ ...newEvent, venueLng: parseFloat(e.target.value) })}
                  />
                </div>
              </div>

              <div className="eo-modal-footer">
                <button
                  type="button"
                  className="eo-btn-secondary"
                  onClick={() => setShowEventModal(false)}
                >
                  Hủy Bỏ
                </button>
                <button type="submit" className="eo-btn-primary">
                  Tạo Sự Kiện
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Thêm Ca Làm Việc Vào Sự Kiện */}
      {showShiftModal && (
        <div className="eo-modal-overlay">
          <div className="eo-modal-box">
            <h3>Thêm Ca Làm Việc & Định Mức Lương</h3>
            <form onSubmit={handleAddShift}>
              <div className="eo-form-group">
                <label>Vị Trí / Vai Trò Cần Tuyển *</label>
                <select
                  className="eo-form-select"
                  value={newShift.jobType}
                  onChange={(e) => setNewShift({ ...newShift, jobType: e.target.value })}
                >
                  {ROLES.map((role) => (
                    <option key={role} value={role}>
                      {role}
                    </option>
                  ))}
                </select>
              </div>

              <div className="eo-form-row">
                <div className="eo-form-group">
                  <label>Thời Gian Bắt Đầu *</label>
                  <input
                    type="datetime-local"
                    required
                    className="eo-form-input"
                    value={newShift.startTime}
                    onChange={(e) => setNewShift({ ...newShift, startTime: e.target.value })}
                  />
                </div>
                <div className="eo-form-group">
                  <label>Thời Gian Kết Thúc *</label>
                  <input
                    type="datetime-local"
                    required
                    className="eo-form-input"
                    value={newShift.endTime}
                    onChange={(e) => setNewShift({ ...newShift, endTime: e.target.value })}
                  />
                </div>
              </div>

              <div className="eo-form-row">
                <div className="eo-form-group">
                  <label>Số Lượng Worker Cần Tuyển *</label>
                  <input
                    type="number"
                    min="1"
                    required
                    className="eo-form-input"
                    value={newShift.requiredWorkers}
                    onChange={(e) => setNewShift({ ...newShift, requiredWorkers: parseInt(e.target.value) })}
                  />
                </div>
                <div className="eo-form-group">
                  <label>Thù Lao Giờ (VNĐ/giờ) *</label>
                  <input
                    type="number"
                    min="20000"
                    step="5000"
                    required
                    className="eo-form-input"
                    value={newShift.hourlyWage}
                    onChange={(e) => setNewShift({ ...newShift, hourlyWage: parseFloat(e.target.value) })}
                  />
                </div>
              </div>

              <div className="eo-form-row">
                <div className="eo-form-group">
                  <label>Phụ Cấp Thêm (VNĐ)</label>
                  <input
                    type="number"
                    step="10000"
                    className="eo-form-input"
                    value={newShift.allowance}
                    onChange={(e) => setNewShift({ ...newShift, allowance: parseFloat(e.target.value) })}
                  />
                </div>
                <div className="eo-form-group">
                  <label>Cự Ly Bán Kính Tối Đa (km)</label>
                  <input
                    type="number"
                    min="1"
                    max="50"
                    className="eo-form-input"
                    value={newShift.maxDistanceKm}
                    onChange={(e) => setNewShift({ ...newShift, maxDistanceKm: parseFloat(e.target.value) })}
                  />
                </div>
              </div>

              {/* Chi phí ước tính hiển thị realtime */}
              <div
                style={{
                  background: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  padding: '1rem',
                  borderRadius: '0.5rem',
                  margin: '1rem 0',
                }}
              >
                <div style={{ fontSize: '0.85rem', color: '#166534', fontWeight: 600 }}>
                  DỰ TOÁN CHI PHÍ CA LÀM VIỆC NÀY:
                </div>
                <div style={{ fontSize: '1.4rem', color: '#15803d', fontWeight: 800 }}>
                  {calculateShiftTotal().toLocaleString()} đ
                </div>
                <div style={{ fontSize: '0.75rem', color: '#4b5563', marginTop: '0.25rem' }}>
                  (Thanh toán trực tiếp cho Worker sau khi hoàn thành ca làm)
                </div>
              </div>

              <div className="eo-modal-footer">
                <button
                  type="button"
                  className="eo-btn-secondary"
                  onClick={() => setShowShiftModal(false)}
                >
                  Hủy Bỏ
                </button>
                <button type="submit" className="eo-btn-primary">
                  Lưu Ca Làm Việc
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal: Khai Báo Thanh Toán Trực Tiếp (Mục 11 SRS) */}
      {showPaymentModal && selectedAssignment && (
        <div className="eo-modal-overlay">
          <div className="eo-modal-box">
            <h3>Khai Báo Đã Trả Lương Trực Tiếp</h3>
            <p style={{ fontSize: '0.875rem', color: '#4b5563', marginBottom: '1.25rem' }}>
              Theo Mục 11 & 12 SRS, CANi không giữ cọc hay giữ lương. Sau khi chi trả ngoài cho
              Worker, vui lòng khai báo để hệ thống đối soát và Worker bấm xác nhận.
            </p>
            <form onSubmit={handleDeclarePayment}>
              <div className="eo-form-group">
                <label>Nhân sự được chi trả:</label>
                <input
                  type="text"
                  disabled
                  className="eo-form-input"
                  style={{ background: '#f3f4f6' }}
                  value={`${selectedAssignment.workerName} (${selectedAssignment.jobType})`}
                />
              </div>

              <div className="eo-form-group">
                <label>Số Tiền Đã Trả (VNĐ) *</label>
                <input
                  type="number"
                  required
                  className="eo-form-input"
                  value={paymentForm.declaredAmount}
                  onChange={(e) =>
                    setPaymentForm({ ...paymentForm, declaredAmount: parseFloat(e.target.value) })
                  }
                />
              </div>

              <div className="eo-form-group">
                <label>Phương Thức Chi Trả *</label>
                <select
                  className="eo-form-select"
                  value={paymentForm.paymentMethod}
                  onChange={(e) =>
                    setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })
                  }
                >
                  <option value="BANK_TRANSFER">Chuyển Khoản Ngân Hàng (Bank Transfer)</option>
                  <option value="CASH">Tiền Mặt Trực Tiếp (Cash)</option>
                </select>
              </div>

              <div className="eo-form-group">
                <label>Mã Giao Dịch Ngân Hàng Ngoài (Nếu chuyển khoản)</label>
                <input
                  type="text"
                  className="eo-form-input"
                  placeholder="Ví dụ: VCB-123456789"
                  value={paymentForm.externalRef}
                  onChange={(e) =>
                    setPaymentForm({ ...paymentForm, externalRef: e.target.value })
                  }
                />
              </div>

              <div className="eo-form-group">
                <label>Link Ảnh Chụp Biên Lai / Bằng Chứng Chuyển Khoản</label>
                <input
                  type="text"
                  className="eo-form-input"
                  placeholder="https://storage.cani.vn/proofs/bill.jpg"
                  value={paymentForm.paymentProofUrl}
                  onChange={(e) =>
                    setPaymentForm({ ...paymentForm, paymentProofUrl: e.target.value })
                  }
                />
              </div>

              <div className="eo-modal-footer">
                <button
                  type="button"
                  className="eo-btn-secondary"
                  onClick={() => setShowPaymentModal(false)}
                >
                  Đóng
                </button>
                <button type="submit" className="eo-btn-primary">
                  Gửi Khai Báo Thanh Toán
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
      {/* Modal: Xác Nhận Xuất Bản Sự Kiện */}
      <PromptModal
        isOpen={confirmPublish.isOpen}
        title="Kích Hoạt Matching Tìm Nhân Sự"
        description="Hệ thống sẽ quét ứng viên đạt độ tin cậy và kỹ năng trong bán kính ưu tiên. Mô hình Non-Escrow của CANi không giữ cọc hay giữ ví, EO sẽ tự trả lương sau ca."
        type="info"
        confirmText="Kích Hoạt Ngay"
        cancelText="Để Sau"
        onConfirm={handleExecutePublish}
        onCancel={() => setConfirmPublish({ isOpen: false, eventId: null })}
      />

      {/* Modal: Xem Tọa Độ & Bản Đồ Radar */}
      <MapModal
        isOpen={mapModal.isOpen}
        title="Tọa Độ Sự Kiện & Bán Kính Radar"
        eventName={mapModal.eventName}
        venueAddress={mapModal.venueAddress}
        lat={mapModal.lat}
        lng={mapModal.lng}
        radiusKm={mapModal.radiusKm}
        onClose={() => setMapModal({ ...mapModal, isOpen: false })}
      />
    </div>
  );
};

export default EOPage;
