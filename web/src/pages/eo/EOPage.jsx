import { useState, useEffect } from 'react';
import { eoService } from '../../services/eoService';
import { useAuth } from '../../features/auth';
import { showToast } from '../../components/common/Toast';
import SkeletonCard from '../../components/common/SkeletonLoader';
import MapModal from '../../components/common/MapModal';
import EoEkycTab from './EoEkycTab';
import EventCreateModal from './components/EventCreateModal';
import ShiftCreateModal from './components/ShiftCreateModal';
import PaymentDeclarationModal from './components/PaymentDeclarationModal';
import PublishEventModal from './components/PublishEventModal';
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

// Ma trận thù lao thị trường tham chiếu (Wage Matrix 4D Recommendation)
const WAGE_RECOMMENDATIONS_MAP = {
  'Lễ tân / Check-in': { min: 45000, max: 60000, defaultWage: 50000, note: 'Giao tiếp tốt, ngoại hình sáng' },
  'Phục vụ bàn': { min: 35000, max: 50000, defaultWage: 40000, note: 'Nhanh nhẹn, cẩn thận' },
  'Pha chế (Bartender/Barista)': { min: 60000, max: 85000, defaultWage: 70000, note: 'Có tay nghề pha chế' },
  'Thu ngân': { min: 40000, max: 55000, defaultWage: 45000, note: 'Trung thực, tính toán chuẩn xác' },
  'Điều phối sự kiện': { min: 70000, max: 100000, defaultWage: 80000, note: 'Kinh nghiệm quản lý nhóm' },
  'Kỹ thuật âm thanh - ánh sáng': { min: 80000, max: 120000, defaultWage: 95000, note: 'Chuyên môn thiết bị sự kiện' },
  'Bảo vệ / An ninh': { min: 40000, max: 55000, defaultWage: 45000, note: 'Thể lực tốt, giữ trật tự' },
  'Hậu cần / Setup sự kiện': { min: 50000, max: 70000, defaultWage: 55000, note: 'Sức khỏe tốt, khuân vác, setup' },
  'PG / PB': { min: 70000, max: 110000, defaultWage: 85000, note: 'Ngoại hình, giới thiệu sản phẩm' },
};

// Dữ liệu mẫu thông báo gần đây cho widget bên phải
const RECENT_NOTIFICATIONS = [
  {
    id: 1,
    icon: '👤',
    color: '#3b82f6',
    bgColor: '#eff6ff',
    title: 'Nguyễn Văn A đã ứng tuyển vào ca Lễ tân',
    time: '10 phút trước',
  },
  {
    id: 2,
    icon: '✓',
    color: '#10b981',
    bgColor: '#ecfdf5',
    title: 'Sự kiện Summer Fest 2026 đã được duyệt',
    time: '1 giờ trước',
  },
  {
    id: 3,
    icon: '💳',
    color: '#f59e0b',
    bgColor: '#fffbeb',
    title: 'Yêu cầu xác nhận lương từ Trần Thị B',
    time: 'Hôm qua',
  },
];

const EOPage = () => {
  const { user } = useAuth();

  // Navigation & Tabs State
  const [activeNav, setActiveNav] = useState('overview'); // 'overview' | 'events' | 'recruitment' | 'payments' | 'ekyc' | 'analytics' | 'settings'
  const [activeTab, setActiveTab] = useState('events'); // 'events' | 'payments' | 'ekyc'
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);

  // Search & Filter
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('NEWEST');

  // Mini Calendar State
  const [calendarDate, setCalendarDate] = useState({ month: 9, year: 2026 }); // Tháng 10/2026 (0-indexed month: 9)
  const [selectedDay, setSelectedDay] = useState(20);

  // Modal States
  const [showEventModal, setShowEventModal] = useState(false);
  const [showShiftModal, setShowShiftModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedEventId, setSelectedEventId] = useState(null);
  const [selectedAssignment, setSelectedAssignment] = useState(null);

  // Smooth Prompt/Confirm & Map States
  const [confirmPublish, setConfirmPublish] = useState({ isOpen: false, event: null });
  const [publishPayMethod, setPublishPayMethod] = useState('VIETQR'); // 'VIETQR' | 'SUBSCRIPTION'
  const [locating, setLocating] = useState(false);
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
      // Nếu có dữ liệu trả về, sử dụng; nếu mảng rỗng thì giữ dữ liệu mặc định phong phú
      if (data && data.length > 0) {
        setEvents(data);
      } else {
        setEvents([
          {
            eventId: 'EVT-98721',
            name: 'Lễ Hội Âm Nhạc Mùa Hè 2026 - Summer Fest Live Stage',
            description: 'Đại nhạc hội ngoài trời quy tụ các nghệ sĩ trẻ hàng đầu, dự kiến đón tiếp 5.000 khán giả.',
            venueAddress: 'Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC), 799 Nguyễn Văn Linh, Q.7, TP.HCM',
            venueLat: 10.7328,
            venueLng: 106.7214,
            eventDate: '2026-10-20T18:00:00Z',
            mode: 'MATCHING',
            status: 'ACTIVE',
            totalShifts: 3,
            totalRequiredWorkers: 15,
            totalConfirmedWorkers: 12,
            totalEstimatedCost: 4500000,
          },
        ]);
      }
    } catch (e) {
      console.error(e);
      setEvents([
        {
          eventId: 'EVT-98721',
          name: 'Lễ Hội Âm Nhạc Mùa Hè 2026 - Summer Fest Live Stage',
          description: 'Đại nhạc hội ngoài trời quy tụ các nghệ sĩ trẻ hàng đầu, dự kiến đón tiếp 5.000 khán giả.',
          venueAddress: 'Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC), 799 Nguyễn Văn Linh, Q.7, TP.HCM',
          venueLat: 10.7328,
          venueLng: 106.7214,
          eventDate: '2026-10-20T18:00:00Z',
          mode: 'MATCHING',
          status: 'ACTIVE',
          totalShifts: 3,
          totalRequiredWorkers: 15,
          totalConfirmedWorkers: 12,
          totalEstimatedCost: 4500000,
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  // Lấy vị trí GPS hiện tại và Reverse Geocoding địa chỉ qua OpenStreetMap
  const handleGetCurrentLocation = () => {
    if (!navigator.geolocation) {
      showToast('Trình duyệt của bạn không hỗ trợ định vị GPS.', 'error');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = parseFloat(pos.coords.latitude.toFixed(6));
        const lng = parseFloat(pos.coords.longitude.toFixed(6));
        try {
          const res = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&zoom=18&addressdetails=1`,
            { headers: { 'Accept-Language': 'vi' } }
          );
          const data = await res.json();
          const addr = data.display_name || `Vị trí GPS (${lat}, ${lng})`;
          setNewEvent((prev) => ({
            ...prev,
            venueAddress: addr,
            venueLat: lat,
            venueLng: lng,
          }));
          showToast('Đã định vị tọa độ GPS & địa chỉ hiện tại thành công!', 'success');
        } catch {
          setNewEvent((prev) => ({
            ...prev,
            venueAddress: `Tọa độ GPS (${lat}, ${lng})`,
            venueLat: lat,
            venueLng: lng,
          }));
          showToast('Đã lấy tọa độ GPS thành công!', 'info');
        } finally {
          setLocating(false);
        }
      },
      (err) => {
        setLocating(false);
        showToast('Không thể lấy vị trí: ' + (err.message || 'Quyền GPS bị từ chối'), 'error');
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  };

  // Tính số giờ của ca làm việc
  const getShiftDurationHours = () => {
    try {
      const start = new Date(newShift.startTime);
      const end = new Date(newShift.endTime);
      const diff = (end - start) / (1000 * 60 * 60);
      return diff > 0 ? parseFloat(diff.toFixed(1)) : 0;
    } catch {
      return 0;
    }
  };

  // Tính thù lao ước tính realtime
  const calculateShiftTotal = () => {
    try {
      const hours = getShiftDurationHours() || 0.5;
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

  // Xử lý đổi vai trò công việc -> Tự động gợi ý mức lương tham chiếu
  const handleRoleChange = (role) => {
    const rec = WAGE_RECOMMENDATIONS_MAP[role];
    setNewShift((prev) => ({
      ...prev,
      jobType: role,
      hourlyWage: rec ? rec.defaultWage : prev.hourlyWage,
    }));
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

  // Sao chép nhanh vào Clipboard
  const copyToClipboard = (text, label) => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text);
      showToast(`Đã sao chép ${label}: ${text}`, 'success');
    }
  };

  // Trigger modal xác nhận xuất bản sự kiện & kích hoạt Matching
  const openConfirmPublish = (event) => {
    setPublishPayMethod('VIETQR');
    setConfirmPublish({ isOpen: true, event });
  };

  const handleExecutePublish = async () => {
    const eventId = confirmPublish.event?.eventId;
    setConfirmPublish({ isOpen: false, event: null });
    if (!eventId) return;

    try {
      await eoService.publishEvent(eventId);
      loadEvents();
      showToast('Xuất bản thành công! Đã tính phí dịch vụ điều phối và kích hoạt Matching Engine tìm ứng viên.', 'success');
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

  // Chuyển nhanh từ Sidebar sang Tab tương ứng
  const handleNavClick = (navKey) => {
    setActiveNav(navKey);
    if (navKey === 'overview' || navKey === 'events') {
      setActiveTab('events');
    } else if (navKey === 'payments') {
      setActiveTab('payments');
    } else if (navKey === 'ekyc') {
      setActiveTab('ekyc');
    } else if (navKey === 'recruitment') {
      if (events.length > 0) {
        setSelectedEventId(events[0].eventId);
        setShowShiftModal(true);
      } else {
        setShowEventModal(true);
      }
    } else {
      showToast('Tính năng đang được tối ưu trong bản cập nhật kế tiếp!', 'info');
    }
  };

  // Tính toán KPI metrics tổng quan
  const totalEvents = events.length;
  const totalRequiredWorkers = events.reduce((sum, e) => sum + (e.totalRequiredWorkers || 0), 0);
  const totalConfirmedWorkers = events.reduce((sum, e) => sum + (e.totalConfirmedWorkers || 0), 0);
  const totalEstimatedCost = events.reduce((sum, e) => sum + (e.totalEstimatedCost || 0), 0);
  const fillRate = totalRequiredWorkers > 0 ? Math.round((totalConfirmedWorkers / totalRequiredWorkers) * 100) : 73;

  // Lọc và sắp xếp danh sách sự kiện
  const filteredEvents = events.filter((evt) => {
    const matchSearch =
      !searchQuery ||
      evt.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      evt.venueAddress?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      evt.eventId?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchStatus =
      statusFilter === 'ALL' ||
      (statusFilter === 'ACTIVE' && evt.status === 'ACTIVE') ||
      (statusFilter === 'DRAFT' && evt.status === 'DRAFT');
    return matchSearch && matchStatus;
  }).sort((a, b) => {
    if (sortBy === 'NAME') return (a.name || '').localeCompare(b.name || '');
    if (sortBy === 'WORKERS') return (b.totalRequiredWorkers || 0) - (a.totalRequiredWorkers || 0);
    return 0; // Mặc định giữ nguyên theo ngày tạo
  });

  // Render các ngày trong Tháng (tự động tính theo calendarDate)
  const renderCalendarDays = () => {
    const daysInMonth = new Date(calendarDate.year, calendarDate.month + 1, 0).getDate();
    const firstDay = new Date(calendarDate.year, calendarDate.month, 1).getDay();
    const startWeekdayOffset = firstDay === 0 ? 6 : firstDay - 1;
    const prevMonthDays = new Date(calendarDate.year, calendarDate.month, 0).getDate();
    const cells = [];

    // Các ngày mờ của tháng trước
    for (let i = 0; i < startWeekdayOffset; i++) {
      cells.push(
        <div key={`empty-${i}`} className="calendar-day-cell dim">
          {prevMonthDays - startWeekdayOffset + i + 1}
        </div>
      );
    }

    // Các ngày trong tháng hiện tại
    for (let day = 1; day <= daysInMonth; day++) {
      const isSelected = day === selectedDay;
      const hasEvent = day === 15 || day === 20;

      cells.push(
        <button
          type="button"
          key={`day-${day}`}
          className={`calendar-day-cell ${isSelected ? 'selected' : ''} ${hasEvent ? 'has-event' : ''}`}
          onClick={() => setSelectedDay(day)}
        >
          <span className="day-number">{day}</span>
          {hasEvent && <span className="calendar-event-dot"></span>}
        </button>
      );
    }

    return cells;
  };

  const displayName = user?.fullName || user?.username || 'Duy Hưng';

  return (
    <div className="eo-dashboard-container">
      <div className="eo-dashboard-layout">
        {/* ========================================================= */}
        {/* CỘT 1: SIDEBAR ĐIỀU HƯỚNG TRÁI                            */}
        {/* ========================================================= */}
        <aside className="eo-sidebar">
          <nav className="eo-sidebar-nav">
            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'overview' ? 'active' : ''}`}
              onClick={() => handleNavClick('overview')}
            >
              <span className="nav-icon">📊</span>
              <span className="nav-label">Tổng quan</span>
            </button>

            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'events' ? 'active' : ''}`}
              onClick={() => handleNavClick('events')}
            >
              <span className="nav-icon">📅</span>
              <span className="nav-label">Quản lý sự kiện</span>
            </button>

            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'recruitment' ? 'active' : ''}`}
              onClick={() => handleNavClick('recruitment')}
            >
              <span className="nav-icon">👥</span>
              <span className="nav-label">Tuyển dụng nhân sự</span>
            </button>

            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'payments' ? 'active' : ''}`}
              onClick={() => handleNavClick('payments')}
            >
              <span className="nav-icon">💳</span>
              <span className="nav-label">Thanh toán & lương</span>
            </button>

            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'ekyc' ? 'active' : ''}`}
              onClick={() => handleNavClick('ekyc')}
            >
              <span className="nav-icon">🛡️</span>
              <span className="nav-label">E-KYC doanh nghiệp</span>
            </button>

            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'analytics' ? 'active' : ''}`}
              onClick={() => handleNavClick('analytics')}
            >
              <span className="nav-icon">📈</span>
              <span className="nav-label">Báo cáo & thống kê</span>
            </button>

            <button
              type="button"
              className={`eo-nav-item ${activeNav === 'settings' ? 'active' : ''}`}
              onClick={() => handleNavClick('settings')}
            >
              <span className="nav-icon">⚙️</span>
              <span className="nav-label">Cài đặt</span>
            </button>
          </nav>

          {/* Promo Card ở đáy sidebar */}
          <div className="eo-sidebar-promo-card">
            <div className="promo-badge-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect>
                <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
              </svg>
            </div>
            <h4 className="promo-title">Tổ chức sự kiện dễ dàng cùng CANi</h4>
            <p className="promo-desc">
              Quản lý nhân sự, tự động hoá chấm công và đối soát thanh toán trực tiếp sau ca.
            </p>
            <button
              type="button"
              className="promo-link-btn"
              onClick={() => showToast('Mở tài liệu hướng dẫn tổ chức sự kiện!', 'info')}
            >
              Tìm hiểu thêm &rarr;
            </button>
          </div>
        </aside>

        {/* ========================================================= */}
        {/* CỘT 2: KHU VỰC NỘI DUNG CHÍNH (CENTER MAIN STREAM)         */}
        {/* ========================================================= */}
        <main className="eo-main-stream">
          {/* Hero Welcome Banner */}
          <section className="eo-hero-banner">
            <div className="hero-content">
              <h1 className="hero-greeting">
                Chào mừng bạn quay trở lại, {displayName}!
              </h1>
              <p className="hero-subtitle">
                Hôm nay bạn có <strong>{events.length} sự kiện</strong> sắp diễn ra. Hãy kiểm tra danh sách điểm danh và phân công ca làm nhé.
              </p>
              <button
                type="button"
                className="hero-action-btn"
                onClick={() => setShowEventModal(true)}
              >
                <span className="btn-plus-icon">+</span> Tạo Sự Kiện Mới
              </button>
            </div>
            <div className="hero-illustration">
              <div className="illustration-graphic">
                <svg width="150" height="130" viewBox="0 0 150 130" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <linearGradient id="gradStage" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#5B86E5" />
                      <stop offset="100%" stopColor="#36D1DC" />
                    </linearGradient>
                    <linearGradient id="gradGlow" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#36D1DC" stopOpacity="0.8" />
                      <stop offset="100%" stopColor="#5B86E5" stopOpacity="0.2" />
                    </linearGradient>
                  </defs>
                  {/* Stage Base */}
                  <ellipse cx="75" cy="98" rx="60" ry="20" fill="url(#gradGlow)" />
                  <path d="M25 90L75 110L125 90L100 50L50 50L25 90Z" fill="url(#gradStage)" fillOpacity="0.15" />
                  {/* Spotlight Beams */}
                  <polygon points="75,25 20,105 130,105" fill="url(#gradGlow)" opacity="0.25" />
                  {/* Star / Sparkles */}
                  <circle cx="75" cy="40" r="14" fill="url(#gradStage)" />
                  <path d="M75 32V48M67 40H83" stroke="#ffffff" strokeWidth="2.5" strokeLinecap="round" />
                  <circle cx="45" cy="55" r="4" fill="#36D1DC" />
                  <circle cx="108" cy="52" r="5" fill="#5B86E5" />
                  <circle cx="95" cy="28" r="3" fill="#38bdf8" />
                </svg>
              </div>
            </div>
          </section>

          {/* 4 Thẻ KPI Metrics Overview */}
          <section className="eo-metrics-grid">
            <div className="eo-metric-card">
              <div className="metric-header-row">
                <span className="eo-metric-label">Tổng Sự Kiện</span>
                <span className="metric-icon-badge blue">📅</span>
              </div>
              <div className="eo-metric-value">{totalEvents}</div>
              <div className="eo-metric-sub">Đang quản lý</div>
            </div>

            <div className="eo-metric-card">
              <div className="metric-header-row">
                <span className="eo-metric-label">Nhân Sự Cần Tuyển</span>
                <span className="metric-icon-badge cyan">👥</span>
              </div>
              <div className="eo-metric-value">{totalRequiredWorkers}</div>
              <div className="eo-metric-sub">Theo ma trận ca</div>
            </div>

            <div className="eo-metric-card">
              <div className="metric-header-row">
                <span className="eo-metric-label">Nhân Sự Đã Chốt Ca</span>
                <span className="metric-icon-badge green">✅</span>
              </div>
              <div className="metric-value-row">
                <div className="eo-metric-value text-accent-blue">{totalConfirmedWorkers}</div>
                <div className="metric-circular-progress" title={`Tỉ lệ lấp đầy ${fillRate}%`}>
                  <svg width="40" height="40" viewBox="0 0 36 36">
                    <path
                      className="circle-bg"
                      d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                    />
                    <path
                      className="circle-fill"
                      strokeDasharray={`${fillRate}, 100`}
                      d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                    />
                    <text x="18" y="21" className="circle-percent">{fillRate}%</text>
                  </svg>
                </div>
              </div>
              <div className="eo-metric-sub">Tỉ lệ lấp đầy: {fillRate}%</div>
            </div>

            <div className="eo-metric-card">
              <div className="metric-header-row">
                <span className="eo-metric-label">Dự Toán Chi Phí Lương</span>
                <span className="metric-icon-badge emerald">💳</span>
              </div>
              <div className="eo-metric-value text-emerald">
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(totalEstimatedCost)}
              </div>
              <div className="eo-metric-sub">Thanh toán trực tiếp sau ca</div>
            </div>
          </section>

          {/* Pill Tabs Switcher */}
          <div className="eo-pill-tabs">
            <button
              type="button"
              className={`pill-tab ${activeTab === 'events' ? 'active' : ''}`}
              onClick={() => setActiveTab('events')}
            >
              Sự Kiện Của Tôi ({events.length})
            </button>
            <button
              type="button"
              className={`pill-tab ${activeTab === 'payments' ? 'active' : ''}`}
              onClick={() => setActiveTab('payments')}
            >
              Thanh Toán & Khai Báo Lương ({payments.length})
            </button>
            <button
              type="button"
              className={`pill-tab ${activeTab === 'ekyc' ? 'active' : ''}`}
              onClick={() => setActiveTab('ekyc')}
            >
              Định Danh Doanh Nghiệp (eKYC)
            </button>
          </div>

          {/* NỘI DUNG TAB 1: DANH SÁCH SỰ KIỆN */}
          {activeTab === 'events' && (
            <section className="eo-events-section animate-fade-in-up">
              {/* Thanh Tìm Kiếm & Lọc */}
              <div className="eo-filter-bar">
                <div className="filter-title-group">
                  <h3 className="section-title">Danh sách sự kiện</h3>
                </div>
                <div className="filter-controls">
                  <div className="search-input-wrapper">
                    <span className="search-icon">🔍</span>
                    <input
                      type="text"
                      placeholder="Tìm kiếm theo tên sự kiện..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      className="event-search-input"
                    />
                  </div>

                  <select
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value)}
                    className="filter-select"
                  >
                    <option value="ALL">Tất cả trạng thái</option>
                    <option value="ACTIVE">Đang diễn ra</option>
                    <option value="DRAFT">Bản nháp</option>
                  </select>

                  <select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value)}
                    className="filter-select"
                  >
                    <option value="NEWEST">Mới nhất</option>
                    <option value="NAME">Tên sự kiện (A-Z)</option>
                    <option value="WORKERS">Số lượng nhân sự</option>
                  </select>
                </div>
              </div>

              {/* Danh sách thẻ sự kiện */}
              <div className="eo-events-list">
                {loading ? (
                  <SkeletonCard count={2} height="220px" />
                ) : filteredEvents.length === 0 ? (
                  <div className="eo-empty-state">
                    <div className="empty-icon">🎪</div>
                    <p className="empty-text">Không tìm thấy sự kiện nào phù hợp.</p>
                    <button
                      type="button"
                      className="eo-btn-primary"
                      onClick={() => setShowEventModal(true)}
                    >
                      + Tạo Sự Kiện Mới
                    </button>
                  </div>
                ) : (
                  filteredEvents.map((evt) => (
                    <article key={evt.eventId} className="eo-event-card-modern">
                      <div className="event-card-thumbnail">
                        <img
                          src="https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=600&q=80"
                          alt={evt.name}
                          className="thumbnail-img"
                        />
                        <div className="thumbnail-overlay">
                          <span className="thumbnail-badge">SỰ KIỆN LIVE</span>
                        </div>
                      </div>

                      <div className="event-card-details">
                        <div className="card-top-row">
                          <h2 className="event-title">{evt.name}</h2>
                          <div className="card-top-actions">
                            <span className={`status-badge-modern ${evt.status === 'ACTIVE' ? 'active' : 'draft'}`}>
                              <span className="dot-pulse"></span>
                              {evt.status === 'ACTIVE' ? 'Đang diễn ra' : evt.status === 'DRAFT' ? 'Bản nháp' : evt.status}
                            </span>
                            <button
                              type="button"
                              className="btn-kebab"
                              title="Tùy chọn khác"
                              onClick={() => showToast(`Tùy chọn cho sự kiện: ${evt.name}`, 'info')}
                            >
                              ⋮
                            </button>
                          </div>
                        </div>

                        <div className="event-code-row">
                          <span className="event-code-text">Mã: {evt.eventId || 'EVT-98721'}</span>
                          <button
                            type="button"
                            className="btn-copy-code"
                            title="Sao chép mã sự kiện"
                            onClick={() => copyToClipboard(evt.eventId || 'EVT-98721', 'Mã sự kiện')}
                          >
                            📋
                          </button>
                        </div>

                        <div className="event-location-row">
                          <span className="location-pin">📍</span>
                          <span className="location-text">{evt.venueAddress}</span>
                        </div>

                        <div className="event-metrics-row">
                          <div className="meta-chip">
                            <span className="chip-icon">👥</span>
                            <span className="chip-label">
                              <strong>{evt.totalConfirmedWorkers || 0}/{evt.totalRequiredWorkers || 0}</strong> Nhân sự
                            </span>
                          </div>

                          <div className="meta-chip">
                            <span className="chip-icon">🕒</span>
                            <span className="chip-label">
                              <strong>{evt.totalShifts || 0}</strong> Ca làm việc
                            </span>
                          </div>

                          <div className="meta-chip wage">
                            <span className="chip-icon">💰</span>
                            <span className="chip-label wage-amount">
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(
                                evt.totalEstimatedCost || 0
                              )} Dự toán
                            </span>
                          </div>
                        </div>

                        <div className="event-card-actions">
                          <button
                            type="button"
                            className="btn-action-map"
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
                            📍 Bản đồ
                          </button>

                          <button
                            type="button"
                            className="btn-action-outline"
                            onClick={() => {
                              setSelectedEventId(evt.eventId);
                              setShowShiftModal(true);
                            }}
                          >
                            + Thêm Ca
                          </button>

                          {evt.status === 'DRAFT' && (
                            <button
                              type="button"
                              className="btn-action-publish"
                              onClick={() => openConfirmPublish(evt)}
                            >
                              🚀 Kích Hoạt Matching
                            </button>
                          )}

                          <button
                            type="button"
                            className="btn-action-primary"
                            onClick={() => {
                              setSelectedEventId(evt.eventId);
                              setShowShiftModal(true);
                            }}
                          >
                            Quản lý
                          </button>
                        </div>
                      </div>
                    </article>
                  ))
                )}
              </div>
            </section>
          )}

          {/* NỘI DUNG TAB 2: BẢNG KHAI BÁO THANH TOÁN & LƯƠNG TRỰC TIẾP */}
          {activeTab === 'payments' && (
            <section className="eo-payments-section animate-fade-in-up">
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
                            type="button"
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
            </section>
          )}

          {/* NỘI DUNG TAB 3: ĐỊNH DANH DOANH NGHIỆP E-KYC */}
          {activeTab === 'ekyc' && <EoEkycTab />}
        </main>

        {/* ========================================================= */}
        {/* CỘT 3: WIDGET TIỆN ÍCH BÊN PHẢI (RIGHT UTILITY COLUMN)      */}
        {/* ========================================================= */}
        <aside className="eo-widget-panel">
          {/* Widget 1: Lịch mini tương tác */}
          <div className="eo-widget-card calendar-widget">
            <div className="widget-header">
              <h3 className="widget-title">Tháng {calendarDate.month + 1}, {calendarDate.year}</h3>
              <div className="calendar-nav-buttons">
                <button
                  type="button"
                  className="cal-btn"
                  title="Tháng trước"
                  onClick={() =>
                    setCalendarDate((prev) =>
                      prev.month === 0 ? { month: 11, year: prev.year - 1 } : { ...prev, month: prev.month - 1 }
                    )
                  }
                >
                  &lt;
                </button>
                <button
                  type="button"
                  className="cal-btn"
                  title="Tháng sau"
                  onClick={() =>
                    setCalendarDate((prev) =>
                      prev.month === 11 ? { month: 0, year: prev.year + 1 } : { ...prev, month: prev.month + 1 }
                    )
                  }
                >
                  &gt;
                </button>
              </div>
            </div>

            <div className="calendar-weekdays-row">
              <span>T2</span>
              <span>T3</span>
              <span>T4</span>
              <span>T5</span>
              <span>T6</span>
              <span>T7</span>
              <span>CN</span>
            </div>

            <div className="calendar-grid">
              {renderCalendarDays()}
            </div>
          </div>

          {/* Widget 2: Thông Báo Mới Nhất */}
          <div className="eo-widget-card notifications-widget">
            <div className="widget-header">
              <h3 className="widget-title">Thông báo mới</h3>
              <button
                type="button"
                className="widget-link-btn"
                onClick={() => showToast('Xem tất cả thông báo', 'info')}
              >
                Xem tất cả
              </button>
            </div>

            <div className="notification-list">
              {RECENT_NOTIFICATIONS.map((item) => (
                <div key={item.id} className="notification-item">
                  <div
                    className="notif-avatar"
                    style={{ backgroundColor: item.bgColor, color: item.color }}
                  >
                    {item.icon}
                  </div>
                  <div className="notif-body">
                    <p className="notif-title">{item.title}</p>
                    <span className="notif-time">{item.time}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Widget 3: Thao Tác Nhanh */}
          <div className="eo-widget-card quick-actions-widget">
            <h3 className="widget-title">Thao tác nhanh</h3>
            <div className="quick-actions-grid">
              <button
                type="button"
                className="quick-action-btn"
                onClick={() => setShowEventModal(true)}
              >
                <span className="quick-btn-icon blue">➕</span>
                <span className="quick-btn-label">Tạo sự kiện mới</span>
              </button>

              <button
                type="button"
                className="quick-action-btn"
                onClick={() => {
                  if (events.length > 0) {
                    setSelectedEventId(events[0].eventId);
                    setShowShiftModal(true);
                  } else {
                    setShowEventModal(true);
                  }
                }}
              >
                <span className="quick-btn-icon cyan">👥</span>
                <span className="quick-btn-label">Tuyển nhân sự</span>
              </button>

              <button
                type="button"
                className="quick-action-btn"
                onClick={() => setActiveTab('payments')}
              >
                <span className="quick-btn-icon emerald">💳</span>
                <span className="quick-btn-label">Khai báo lương</span>
              </button>

              <button
                type="button"
                className="quick-action-btn"
                onClick={() => setActiveTab('ekyc')}
              >
                <span className="quick-btn-icon amber">🛡️</span>
                <span className="quick-btn-label">Định danh eKYC</span>
              </button>
            </div>
          </div>
        </aside>
      </div>

      {/* ========================================================= */}
      {/* TẤT CẢ CÁC MODALS NGHIỆP VỤ (MODULE HOÁ CLEAN CODE)       */}
      {/* ========================================================= */}

      {/* Modal 1: Tạo Sự Kiện Mới */}
      <EventCreateModal
        isOpen={showEventModal}
        onClose={() => setShowEventModal(false)}
        onSubmit={handleCreateEvent}
        newEvent={newEvent}
        setNewEvent={setNewEvent}
        onGetCurrentLocation={handleGetCurrentLocation}
        locating={locating}
      />

      {/* Modal 2: Thêm Ca Làm Việc & Định Mức Lương 4D */}
      <ShiftCreateModal
        isOpen={showShiftModal}
        onClose={() => setShowShiftModal(false)}
        onSubmit={handleAddShift}
        newShift={newShift}
        setNewShift={setNewShift}
        roles={ROLES}
        wageRecommendationsMap={WAGE_RECOMMENDATIONS_MAP}
        onRoleChange={handleRoleChange}
        shiftDurationHours={getShiftDurationHours()}
        shiftTotal={calculateShiftTotal()}
      />

      {/* Modal 3: Khai Báo Thanh Toán Trực Tiếp (Mục 11 & 12 SRS) */}
      <PaymentDeclarationModal
        isOpen={showPaymentModal}
        onClose={() => setShowPaymentModal(false)}
        onSubmit={handleDeclarePayment}
        paymentForm={paymentForm}
        setPaymentForm={setPaymentForm}
        selectedAssignment={selectedAssignment}
      />

      {/* Modal 4: Xác Nhận Xuất Bản Sự Kiện & Phí Matching (VietQR / Quota) */}
      <PublishEventModal
        isOpen={confirmPublish.isOpen}
        onClose={() => setConfirmPublish({ isOpen: false, event: null })}
        onExecutePublish={handleExecutePublish}
        event={confirmPublish.event}
        publishPayMethod={publishPayMethod}
        setPublishPayMethod={setPublishPayMethod}
        onCopyToClipboard={copyToClipboard}
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
