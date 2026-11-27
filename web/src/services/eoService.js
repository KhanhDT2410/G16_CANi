import { apiClient } from './api';

export const eoService = {
  // Lấy danh sách sự kiện của EO
  getMyEvents: async () => {
    const response = await apiClient.get('/v1/eo/events');
    return response.data?.data || [];
  },

  // Lấy danh sách sự kiện của EO theo trang (Server-side Pagination)
  getMyEventsPage: async ({ page = 0, size = 20 } = {}) => {
    const response = await apiClient.get('/v1/eo/events/page', { params: { page, size } });
    return response.data?.data;
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

  // UC-28: Đề xuất mức thù lao theo mô hình Region × JobTier (Bảng A - NĐ 293/2025/NĐ-CP)
  getWageRecommendation: async ({
    shiftId = null,
    regionCode = null,
    city = null,
    venueAddress = '',
    venueLat = null,
    venueLng = null,
    jobType,
    jobTier = null,
    startTime,
    endTime,
  }) => {
    const response = await apiClient.post('/v1/wage-recommendations', {
      shiftId,
      regionCode,
      city,
      venueAddress,
      venueLat,
      venueLng,
      jobType,
      jobTier,
      startTime,
      endTime,
    });
    return response.data?.data || null;
  },

  // Xuất bản sự kiện & kích hoạt Matching Engine (Non-Escrow)
  publishEvent: async (eventId) => {
    const response = await apiClient.post(`/v1/eo/events/${eventId}/publish`);
    return response.data?.data;
  },

  // Lấy toàn bộ danh sách khoản thanh toán của EO (Khai báo lương & Quyết toán)
  getEoPayments: async () => {
    const response = await apiClient.get('/v1/payments/eo/history');
    return response.data?.data || [];
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

  // EO Lấy thông báo đồng ý xử lý dữ liệu sinh trắc học (Nghị định 13/2023/NĐ-CP)
  getEkycConsentPolicy: async () => {
    try {
      const response = await apiClient.get('/v1/eo/ekyc/consent-policy');
      return response.data?.data;
    } catch {
      return {
        required: true,
        noticeVersion: '2026.11-nd13-v1',
        title: 'Thông báo xử lý dữ liệu cá nhân & sinh trắc học (Nghị định 13/2023/NĐ-CP)',
        summary: 'Ảnh CCCD và chân dung chỉ dùng để xác thực danh tính qua VNPT eKYC, kiểm tra độ tuổi tối thiểu và chống trùng lặp tài khoản.',
      };
    }
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

  // UC-83: EO báo cáo nhân sự chưa hoàn thành đủ công việc trong ca
  reportIncompleteWork: async (assignmentId, payload) => {
    const body = typeof payload === 'string'
      ? { reason: payload }
      : {
          reason: payload?.reason || payload?.description || '',
          proofUrls: payload?.proofUrls || [],
          incompleteChecklistItems: payload?.incompleteChecklistItems || [],
        };
    const response = await apiClient.post(`/v1/eo/events/assignments/${assignmentId}/report-incomplete`, body);
    return response.data?.data;
  },

  // UC-88: EO báo cáo Worker vắng mặt không báo trước (No-show) & kích hoạt Emergency Dispatch
  reportNoShow: async (assignmentId, payload) => {
    const body = typeof payload === 'string'
      ? { notes: payload }
      : { notes: payload?.notes || '' };
    const response = await apiClient.post(`/v1/eo/events/assignments/${assignmentId}/no-show`, body);
    return response.data?.data;
  },

  // UC-18: Cập nhật ca làm việc
  updateShift: async (shiftId, shiftData) => {
    const response = await apiClient.put(`/v1/eo/events/shifts/${shiftId}`, shiftData);
    return response.data?.data;
  },

  // Lấy danh mục kỹ năng (taxonomy) để chọn requiredSkills cho ca (endpoint công khai)
  getSkillTaxonomy: async () => {
    try {
      const response = await apiClient.get('/v1/workers/skills/taxonomy');
      return response.data?.data || [];
    } catch (error) {
      console.warn('Không tải được danh mục kỹ năng, bỏ qua requiredSkills:', error.message);
      return [];
    }
  },

  // BR-16: Tạo hoặc lấy kỹ năng nhập tay (Dynamic Custom Skill)
  createCustomSkill: async (name, category = 'Tự chọn') => {
    const response = await apiClient.post('/v1/workers/skills/custom', { name, category });
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

  // UC-49 + Skill V3: EO xác nhận hoàn thành ca, duyệt công và xác nhận kỹ năng thực hiện chính
  verifyAttendance: async (assignmentId, payload = null) => {
    const response = await apiClient.post(
      `/v1/eo/events/assignments/${assignmentId}/verify`,
      payload || undefined
    );
    return response.data?.data;
  },

  // UC-69: EO đánh giá Worker sau ca làm việc
  rateWorker: async (assignmentId, reviewData) => {
    const response = await apiClient.post(`/v1/eo/events/assignments/${assignmentId}/reviews`, reviewData);
    return response.data?.data;
  },

  // Lấy tổng hợp danh sách nhân sự chờ đánh giá & đã đánh giá qua các sự kiện của EO
  getWorkerReviewOverview: async () => {
    const response = await apiClient.get('/v1/eo/events/worker-reviews');
    return response.data?.data || [];
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
          name: 'Gói Tự Động Ghép Đôi + Mời (Matching Plus)',
          description: 'Toàn bộ tính năng Invite Mode + Thuật toán tự động kết nối Worker phù hợp nhất trong bán kính.',
          price: 80000,
          currency: 'VND',
          eventsQuota: 1,
          features: [
            'Bao gồm toàn bộ tính năng Gói Mời Trực Tiếp',
            'Tự động đề xuất và kết nối Worker phù hợp nhất',
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

  // UC-11: Nhận kết quả callback từ VNPay
  processVNPayCallback: async (queryParams) => {
    const response = await apiClient.get('/v1/eo/subscriptions/vnpay-callback', { params: queryParams });
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
  // ĐIỀU PHỐI KHẨN CẤP (EMERGENCY DISPATCH - UC-59)
  // ==========================================
  // Bật điều phối khẩn cấp (Emergency Dispatch) cho ca làm việc
  enableEmergencyDispatch: async (shiftId, optionsOrRadius = 25) => {
    const payload =
      typeof optionsOrRadius === 'object' && optionsOrRadius !== null
        ? {
            radiusKm: optionsOrRadius.radiusKm != null ? Number(optionsOrRadius.radiusKm) : 25,
            skillRelaxation: optionsOrRadius.skillRelaxation || 'NONE',
            minReliabilityScore:
              optionsOrRadius.minReliabilityScore != null && optionsOrRadius.minReliabilityScore !== ''
                ? Number(optionsOrRadius.minReliabilityScore)
                : null,
            emergencyBonus:
              optionsOrRadius.emergencyBonus != null && Number(optionsOrRadius.emergencyBonus) > 0
                ? Number(optionsOrRadius.emergencyBonus)
                : 0,
          }
        : {
            radiusKm: optionsOrRadius != null && Number(optionsOrRadius) > 0 ? Number(optionsOrRadius) : 25,
          };
    const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/emergency-dispatch`, payload);
    return response.data?.data;
  },

  // Cập nhật tham số (bán kính, nới lỏng kỹ năng, phụ cấp) và quét lại điều phối khẩn cấp
  redispatchEmergencyDispatch: async (shiftId, options = {}) => {
    const payload = {
      radiusKm: options.radiusKm != null ? Number(options.radiusKm) : undefined,
      skillRelaxation: options.skillRelaxation || undefined,
      minReliabilityScore:
        options.minReliabilityScore != null && options.minReliabilityScore !== ''
          ? Number(options.minReliabilityScore)
          : undefined,
      emergencyBonus:
        options.emergencyBonus != null && Number(options.emergencyBonus) >= 0
          ? Number(options.emergencyBonus)
          : undefined,
    };
    const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/emergency-dispatch/redispatch`, payload);
    return response.data?.data;
  },

  // Tắt chế độ điều phối khẩn cấp cho ca làm việc
  disableEmergencyDispatch: async (shiftId) => {
    const response = await apiClient.post(`/v1/eo/events/shifts/${shiftId}/emergency-dispatch/off`);
    return response.data?.data;
  },

  // Tải ảnh sự kiện hoặc ca làm việc trực tiếp từ máy tính lên
  uploadImage: async (file) => {
    const formData = new FormData();
    formData.append('file', file);
    const response = await apiClient.post('/v1/eo/events/upload-image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data?.data?.url;
  },

  // Lấy danh sách toàn bộ nhân sự tham gia trong sự kiện kèm điểm uy tín, tuổi, trạng thái
  getEventWorkers: async (eventId) => {
    try {
      const response = await apiClient.get(`/v1/eo/events/${eventId}/workers`);
      return response.data?.data || [];
    } catch (error) {
      console.warn('Lỗi getEventWorkers:', error);
      return [];
    }
  },

  // Lấy hồ sơ nghề nghiệp chi tiết của Worker cho EO (trực tiếp từ Database)
  getWorkerProfile: async (workerId) => {
    const response = await apiClient.get(`/v1/eo/workers/${workerId}/profile`);
    return response.data?.data;
  },

  // Lấy danh sách đánh giá & nhận xét từ người lao động dành cho EO
  getEoReviews: async () => {
    const response = await apiClient.get('/v1/eo/reviews');
    return response.data?.data || [];
  },
};
