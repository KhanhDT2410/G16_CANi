import { apiClient } from './api';

export const eoService = {
  // Lấy danh sách sự kiện của EO
  getMyEvents: async () => {
    try {
      const response = await apiClient.get('/v1/eo/events');
      return response.data?.data || [];
    } catch (error) {
      if (import.meta.env.VITE_DEMO === 'true') {
        console.warn('Lỗi getMyEvents, sử dụng dữ liệu mẫu demo:', error);
        return [
          {
            eventId: 'evt-01',
            name: 'Triển lãm Công nghệ Quốc tế TechExpo 2026',
            description: 'Sự kiện công nghệ quy mô 5.000 khách tại SECC',
            venueAddress: '799 Nguyễn Văn Linh, Quận 7, TP.HCM',
            venueLat: 10.7328,
            venueLng: 106.7214,
            eventDate: '2026-11-15T08:00:00Z',
            mode: 'MATCHING',
            status: 'ACTIVE',
            totalShifts: 3,
            totalRequiredWorkers: 15,
            totalConfirmedWorkers: 11,
            totalEstimatedCost: 18500000,
          },
          {
            eventId: 'evt-02',
            name: 'Đêm Nhạc Hội Tri Ân Khách Hàng',
            description: 'Gala Dinner tại Gem Center',
            venueAddress: '8 Nguyễn Bỉnh Khiêm, Đa Kao, Quận 1, TP.HCM',
            venueLat: 10.7877,
            venueLng: 106.7003,
            eventDate: '2026-11-20T17:00:00Z',
            mode: 'MATCHING',
            status: 'DRAFT',
            totalShifts: 2,
            totalRequiredWorkers: 8,
            totalConfirmedWorkers: 0,
            totalEstimatedCost: 9600000,
          },
        ];
      }
      throw error;
    }
  },

  // Chi tiết sự kiện
  getEventDetail: async (eventId) => {
    try {
      const response = await apiClient.get(`/v1/eo/events/${eventId}`);
      return response.data?.data;
    } catch (error) {
      console.warn('Lỗi getEventDetail, sử dụng fallback:', error);
      return null;
    }
  },

  // Tạo sự kiện mới
  createEvent: async (eventData) => {
    const response = await apiClient.post('/v1/eo/events', eventData);
    return response.data?.data;
  },

  // Thêm ca làm việc vào sự kiện
  addShiftToEvent: async (eventId, shiftData) => {
    const response = await apiClient.post(`/v1/eo/events/${eventId}/shifts`, shiftData);
    return response.data?.data;
  },

  // Xuất bản sự kiện & kích hoạt Matching Engine (Non-Escrow)
  publishEvent: async (eventId) => {
    const response = await apiClient.post(`/v1/eo/events/${eventId}/publish`);
    return response.data?.data;
  },

  // Kích hoạt tìm ứng viên cho 1 ca cụ thể
  publishShift: async (shiftId) => {
    const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/publish`);
    return response.data?.data;
  },

  // Xem danh sách thanh toán của một ca làm việc
  getShiftPayments: async (shiftId) => {
    try {
      const response = await apiClient.get(`/v1/payments/shifts/${shiftId}`);
      return response.data?.data || [];
    } catch (error) {
      console.warn('Lỗi getShiftPayments:', error);
      return [];
    }
  },

  // EO Khai báo đã thanh toán tiền công trực tiếp cho Worker (Mục 11 SRS)
  declarePayment: async (paymentData) => {
    const response = await apiClient.post('/v1/payments/eo/declare', paymentData);
    return response.data?.data;
  },

  // EO Lấy trạng thái hồ sơ định danh doanh nghiệp eKYC
  getEkycStatus: async () => {
    try {
      const response = await apiClient.get('/v1/eo/ekyc/status');
      return response.data?.data;
    } catch (error) {
      console.warn('Lỗi getEkycStatus:', error);
      return null;
    }
  },

  // EO Lấy thông tin hồ sơ doanh nghiệp (Profile)
  getEoProfile: async () => {
    const response = await apiClient.get('/v1/eo/profile');
    return response.data?.data;
  },

  // EO Cập nhật thông tin hồ sơ doanh nghiệp (Profile)
  updateEoProfile: async (profileData) => {
    const response = await apiClient.put('/v1/eo/profile', profileData);
    return response.data?.data;
  },

  // EO Nộp hồ sơ định danh eKYC doanh nghiệp (CCCD trước, sau, selfie, GPKD)
  submitEkyc: async (formData) => {
    const response = await apiClient.post('/v1/eo/ekyc/submit', formData, {
      headers: {
        'Content-Type': undefined,
      },
    });
    return response.data?.data;
  },

  // UC-11: Cập nhật thông tin sự kiện
  updateEvent: async (eventId, eventData) => {
    const response = await apiClient.put(`/v1/eo/events/${eventId}`, eventData);
    return response.data?.data;
  },

  // UC-12: Hủy sự kiện
  cancelEvent: async (eventId, reason) => {
    const response = await apiClient.post(`/v1/eo/events/${eventId}/cancel`, { reason });
    return response.data?.data;
  },

  // Xóa sự kiện khỏi danh sách (Soft Delete)
  deleteEvent: async (eventId) => {
    const response = await apiClient.delete(`/v1/eo/events/${eventId}`);
    return response.data?.data;
  },

  // UC-18: Cập nhật ca làm việc
  updateShift: async (shiftId, shiftData) => {
    const response = await apiClient.put(`/v1/eo/events/shifts/${shiftId}`, shiftData);
    return response.data?.data;
  },

  // UC-19: Hủy ca làm việc
  cancelShift: async (shiftId, reason) => {
    const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/cancel`, { reason });
    return response.data?.data;
  },

  // UC-52: Lấy mã QR điểm danh của ca (Check-in & Check-out)
  getShiftQr: async (shiftId) => {
    const response = await apiClient.get(`/v1/eo/events/shifts/${shiftId}/qr`);
    return response.data?.data;
  },

  // UC-48: Lấy danh sách điểm danh và tiến độ ca (Live Attendance)
  getShiftAttendance: async (shiftId) => {
    const response = await apiClient.get(`/v1/eo/events/shifts/${shiftId}/attendance`);
    return response.data?.data || [];
  },

  // UC-49: EO xác nhận hoàn thành ca & duyệt công cho Worker
  verifyAttendance: async (assignmentId) => {
    const response = await apiClient.post(`/v1/eo/events/assignments/${assignmentId}/verify`);
    return response.data?.data;
  },

  // UC-69: EO đánh giá Worker sau ca làm việc
  rateWorker: async (assignmentId, reviewData) => {
    const response = await apiClient.post(`/v1/eo/events/assignments/${assignmentId}/reviews`, reviewData);
    return response.data?.data;
  },

  // ==========================================
  // W-06: QUẢN LÝ GÓI CƯỚC & THANH TOÁN VNPAY (UC-10, UC-11, UC-12)
  // ==========================================

  // UC-10: Xem danh mục gói cước có sẵn (Invite 50k & Matching 80k)
  getSubscriptionPlans: async () => {
    try {
      const response = await apiClient.get('/v1/eo/subscriptions/plans');
      return response.data?.data || [];
    } catch (error) {
      console.warn('Lỗi getSubscriptionPlans, dùng fallback:', error);
      return [
        {
          planType: 'INVITE_ONLY',
          name: 'Gói Mời Trực Tiếp (Invite Mode)',
          description: 'Tạo 1 sự kiện và điều phối nhân sự qua mã QR/link mời riêng. Không giới hạn số lượng ca làm.',
          price: 50000,
          currency: 'VND',
          eventsQuota: 1,
          features: [
            'Hạn mức 1 Sự kiện',
            'Không giới hạn số ca làm việc',
            'Tạo link mời & mã QR bảo mật riêng cho Worker',
            'Điểm danh Check-in / Check-out QR thời gian thực',
            'Duyệt công & Khai báo trả lương Non-Escrow trực tiếp',
          ],
        },
        {
          planType: 'MATCHING_PLUS',
          name: 'Gói Radar Matching + Mời (Matching Plus)',
          description: 'Toàn bộ tính năng Invite Mode + Thuật toán AI Radar tự động quét và ghép nối Worker phù hợp nhất trong bán kính.',
          price: 80000,
          currency: 'VND',
          eventsQuota: 1,
          features: [
            'Bao gồm toàn bộ tính năng Gói Mời Trực Tiếp',
            'Kích hoạt Matching Radar AI đề xuất Worker tự động',
            'Quét ứng viên theo bán kính GPS (15km), độ tin cậy >= 80%',
            'Worker chủ động nhận việc qua Mobile App tức thì',
            'Tối ưu hóa tốc độ lấp đầy ca làm việc trong 15 phút',
          ],
        },
      ];
    }
  },

  // UC-10: Lấy gói cước hiện tại & hạn mức Quota của EO
  getCurrentSubscription: async () => {
    try {
      const response = await apiClient.get('/v1/eo/subscriptions/current');
      return response.data?.data;
    } catch (error) {
      console.warn('Lỗi getCurrentSubscription:', error);
      return null;
    }
  },

  // UC-12: Xem lịch sử mua gói cước của EO
  getSubscriptionHistory: async () => {
    try {
      const response = await apiClient.get('/v1/eo/subscriptions/history');
      return response.data?.data || [];
    } catch (error) {
      console.warn('Lỗi getSubscriptionHistory:', error);
      return [];
    }
  },

  // UC-11: Khởi tạo phiên thanh toán VNPay Sandbox
  purchaseSubscription: async (purchaseData) => {
    const response = await apiClient.post('/v1/eo/subscriptions/purchase', purchaseData);
    return response.data?.data;
  },

  // UC-11: Nhận kết quả callback từ VNPay
  processVNPayCallback: async (queryParams) => {
    const response = await apiClient.get('/v1/eo/subscriptions/vnpay-callback', { params: queryParams });
    return response.data?.data;
  },

  // UC-11 (Demo Sandbox): Giả lập thanh toán thành công tức thì phục vụ demo hội đồng
  simulateSubscriptionDemo: async (planType = 'MATCHING_PLUS') => {
    const response = await apiClient.post('/v1/eo/subscriptions/demo-simulate', null, {
      params: { planType },
    });
    return response.data?.data;
  },

  // ==========================================
  // FE-08: GÓI MỜI TRỰC TIẾP (INVITE MODE - UC-72 & UC-73)
  // ==========================================

  // UC-72: EO sinh Link & Mã mời trực tiếp cho ca làm việc
  createShiftInvite: async (shiftId, data = {}) => {
    const response = await apiClient.post(`/v1/eo/shifts/${shiftId}/invites`, data);
    return response.data?.data;
  },

  // UC-72: EO lấy mã mời đang hoạt động của ca
  getShiftInvite: async (shiftId) => {
    try {
      const response = await apiClient.get(`/v1/eo/shifts/${shiftId}/invites`);
      return response.data?.data;
    } catch {
      return null;
    }
  },

  // Xem chi tiết lời mời qua mã code (Công khai cho Worker)
  getInviteDetails: async (code) => {
    const response = await apiClient.get(`/v1/invites/${code}`);
    return response.data?.data;
  },

  // UC-73: Worker tham gia ca làm việc qua mã mời
  joinShiftViaInvite: async (code) => {
    const response = await apiClient.post('/v1/workers/invites/join', { code });
    return response.data?.data;
  },

  // ==========================================
  // FE-11: KHIẾU NẠI & TRANH CHẤP (DISPUTE - UC-96, UC-97, UC-104)
  // ==========================================

  // UC-96: Nộp đơn khiếu nại tranh chấp (Worker hoặc EO)
  submitDispute: async (disputeData) => {
    const response = await apiClient.post('/v1/disputes', disputeData);
    return response.data?.data;
  },

  // UC-104: Lấy danh sách lịch sử khiếu nại của tôi
  getMyDisputes: async () => {
    try {
      const response = await apiClient.get('/v1/disputes/my');
      return response.data?.data || [];
    } catch (error) {
      console.warn('Lỗi getMyDisputes:', error);
      return [];
    }
  },

  // UC-97: Xem chi tiết một khiếu nại
  getDisputeDetail: async (disputeId) => {
    const response = await apiClient.get(`/v1/disputes/${disputeId}`);
    return response.data?.data;
  },

  // ==========================================
  // CỔNG THANH TOÁN VIETQR PAYOS (TỰ ĐỘNG KHỚP TIỀN MBBANK)
  // ==========================================

  // Khởi tạo link thanh toán & mã VietQR PayOS
  purchaseSubscriptionPayOS: async (purchaseData) => {
    const response = await apiClient.post('/v1/eo/subscriptions/purchase-payos', purchaseData);
    return response.data?.data;
  },

  // Xác nhận đã thanh toán PayOS thành công
  confirmPayOSPayment: async (orderCode) => {
    const response = await apiClient.post(`/v1/eo/subscriptions/payos-confirm/${orderCode}`);
    return response.data?.data;
  },

  // ==========================================
  // BONUS 1: EMERGENCY BROADCAST POOL (SECTION 11 CANi_Flow_v8.txt)
  // ==========================================
  triggerEmergencyBroadcast: async (shiftId, emergencyData = {}) => {
    try {
      const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/emergency-dispatch`, emergencyData);
      return response.data?.data;
    } catch {
      // Fallback tự động gọi dispatch chuẩn kèm metadata
      const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/publish`, emergencyData);
      return response.data?.data;
    }
  },
};
