import { apiClient } from './api';

export const workerService = {
  // Lấy các đề xuất việc làm đang chờ Worker phản hồi (Status = PENDING)
  getMyProposals: async () => {
    try {
      const response = await apiClient.get('/v1/workers/proposals');
      return response.data?.data || [];
    } catch (error) {
      if (import.meta.env.VITE_DEMO === 'true') {
        console.warn('Lỗi getMyProposals, sử dụng dữ liệu mẫu demo:', error);
        return [
          {
            proposalId: 'prop-01',
            shiftId: 'shift-101',
            eventId: 'evt-01',
            eventName: 'Triển lãm Công nghệ Quốc tế TechExpo 2026',
            venueAddress: '799 Nguyễn Văn Linh, Quận 7, TP.HCM',
            jobType: 'Lễ tân / Check-in',
            description: 'Đón tiếp đại biểu VIP, hướng dẫn check-in mã QR tại sảnh A.',
            startTime: '2026-11-15T08:00:00Z',
            endTime: '2026-11-15T12:00:00Z',
            hourlyWage: 65000,
            allowance: 30000,
            totalEstimatedPay: 290000,
            matchScore: 94.5,
            scoreBreakdown: '{"skillMatch": 95, "distanceScore": 92, "reliability": 100}',
            status: 'PENDING',
            expiresAt: '2026-11-14T20:00:00Z',
          },
          {
            proposalId: 'prop-02',
            shiftId: 'shift-102',
            eventId: 'evt-02',
            eventName: 'Đêm Nhạc Hội Tri Ân Khách Hàng',
            venueAddress: '8 Nguyễn Bỉnh Khiêm, Quận 1, TP.HCM',
            jobType: 'Pha chế (Bartender/Barista)',
            description: 'Phục vụ đồ uống cocktail & mocktail tại quầy bar chính.',
            startTime: '2026-11-20T17:00:00Z',
            endTime: '2026-11-20T22:00:00Z',
            hourlyWage: 80000,
            allowance: 50000,
            totalEstimatedPay: 450000,
            matchScore: 88.0,
            status: 'PENDING',
            expiresAt: '2026-11-19T22:00:00Z',
          },
        ];
      }
      throw error;
    }
  },

  // Worker chấp nhận đề xuất việc làm
  acceptProposal: async (proposalId) => {
    const response = await apiClient.post(`/v1/workers/proposals/${proposalId}/accept`);
    return response.data?.data;
  },

  // Worker từ chối lời mời
  rejectProposal: async (proposalId, reason) => {
    const response = await apiClient.post(`/v1/workers/proposals/${proposalId}/reject`, null, {
      params: { reason: reason || 'Bận lịch đột xuất' },
    });
    return response.data?.data;
  },

  // Lấy danh sách ca làm việc đã được phân công (Assignments)
  getMyAssignments: async () => {
    try {
      const response = await apiClient.get('/v1/workers/assignments');
      return response.data?.data || [];
    } catch (error) {
      if (import.meta.env.VITE_DEMO === 'true') {
        console.warn('Lỗi getMyAssignments, sử dụng dữ liệu mẫu (VITE_DEMO=true):', error);
        return [
          {
            assignmentId: 'assign-01',
            shiftId: 'shift-100',
            eventId: 'evt-00',
            eventName: 'Hội nghị Khoa học & Đổi mới Sáng tạo 2026',
            venueAddress: 'Nhà thi đấu Quân Khu 7, Tân Bình, TP.HCM',
            jobType: 'Điều phối sự kiện',
            startTime: '2026-11-10T07:30:00Z',
            endTime: '2026-11-10T12:30:00Z',
            hourlyWage: 60000,
            totalPay: 300000,
            status: 'COMPLETED',
            source: 'MATCHING',
            eoConfirmed: true,
            paymentStatus: 'DECLARED_PENDING_WORKER',
            declaredAmount: 300000,
            paymentMethod: 'BANK_TRANSFER',
            paymentProofUrl: 'https://storage.cani.vn/proofs/bill_demo.jpg',
          },
        ];
      }
      throw error;
    }
  },

  // Worker xác nhận tình trạng nhận tiền lương trực tiếp (Mục 12 SRS)
  confirmPayment: async (assignmentId, data) => {
    const response = await apiClient.post(`/v1/payments/worker/confirm/${assignmentId}`, data);
    return response.data?.data;
  },

  // Bật / tắt trạng thái sẵn sàng kiếm việc
  toggleOnline: async (isOnline, currentLat, currentLng) => {
    const response = await apiClient.post('/v1/workers/status', {
      isOnline,
      currentLat,
      currentLng,
    });
    return response.data?.data;
  },
};
