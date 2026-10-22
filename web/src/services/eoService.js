import { apiClient } from './api';

export const eoService = {
  // Lấy danh sách sự kiện của EO
  getMyEvents: async () => {
    try {
      const response = await apiClient.get('/v1/eo/events');
      return response.data?.data || [];
    } catch (error) {
      console.warn('Lỗi getMyEvents, sử dụng dữ liệu mẫu:', error);
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
};
