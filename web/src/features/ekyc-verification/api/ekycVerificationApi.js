import { apiClient } from '../../../services/api';

export const ekycVerificationApi = {
  /**
   * Lấy danh sách hàng đợi hồ sơ eKYC Worker chờ thẩm định
   */
  getWorkerEkycQueue: async () => {
    const response = await apiClient.get('/v1/admin/ekyc/queue');
    return response.data?.data || [];
  },

  /**
   * Phê duyệt hoặc từ chối hồ sơ eKYC Worker
   */
  reviewWorkerEkyc: async (verificationId, decision, reviewNotes) => {
    const response = await apiClient.post(`/v1/admin/ekyc/${verificationId}/review`, {
      decision,
      reviewNotes,
    });
    return response.data?.data;
  },

  /**
   * Lấy danh sách hàng đợi hồ sơ eKYC Doanh nghiệp (EO) chờ thẩm định
   */
  getEoEkycQueue: async () => {
    const response = await apiClient.get('/v1/admin/eo-ekyc/queue');
    return response.data?.data || [];
  },

  /**
   * Phê duyệt hoặc từ chối hồ sơ eKYC Doanh nghiệp (EO)
   */
  reviewEoEkyc: async (verificationId, decision, reviewNotes) => {
    const response = await apiClient.post(`/v1/admin/eo-ekyc/${verificationId}/review`, {
      decision,
      reviewNotes,
    });
    return response.data?.data;
  },
};

