import { apiClient } from '../../../services/api';

export const skillVerificationApi = {
  /**
   * Lấy danh sách hàng đợi thẩm định kỹ năng AI chờ duyệt (UC-47)
   */
  getPendingSkillQueue: async () => {
    const response = await apiClient.get('/v1/workers/skills/admin/queue');
    return response.data?.data || [];
  },

  /**
   * Admin / Customer Care phê duyệt hoặc từ chối chứng chỉ kỹ năng (UC-47)
   */
  reviewSkill: async (id, approved, reviewNotes) => {
    const response = await apiClient.post(`/v1/workers/skills/admin/${id}/review`, null, {
      params: { approved, reviewNotes },
    });
    return response.data?.data;
  },
};

