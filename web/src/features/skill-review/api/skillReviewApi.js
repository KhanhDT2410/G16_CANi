import { apiClient } from '../../../services/api';

export const skillReviewApi = {
  getPendingQueue: async () => {
    const res = await apiClient.get('/v1/skills/admin/queue');
    return res.data?.data || [];
  },

  getAllVerifications: async (status) => {
    const res = await apiClient.get('/v1/skills/admin/all', {
      params: status ? { status } : {},
    });
    return res.data?.data || [];
  },

  getDetail: async (verificationId) => {
    const res = await apiClient.get(`/v1/skills/admin/${verificationId}`);
    return res.data?.data;
  },

  reviewVerification: async (verificationId, { decision, reviewNotes }) => {
    const res = await apiClient.post(`/v1/skills/admin/${verificationId}/review`, null, {
      params: { decision, reviewNotes },
    });
    return res.data?.data;
  },

  moveFailedToManualReview: async (verificationId) => {
    const res = await apiClient.post(`/v1/skills/admin/${verificationId}/manual-review`);
    return res.data?.data;
  },

  revokeVerification: async (verificationId, reason) => {
    const res = await apiClient.post(`/v1/skills/admin/${verificationId}/revoke`, null, {
      params: reason ? { reason } : {},
    });
    return res.data?.data;
  },
};

export const resolveSkillDocumentUrl = (docUrl) => {
  if (!docUrl) return '';
  if (/^https?:\/\//i.test(docUrl)) return docUrl;
  const base = apiClient?.defaults?.baseURL || '';
  const origin = base.replace(/\/api\/?$/, '');
  const path = docUrl.startsWith('/') ? docUrl : `/${docUrl}`;
  return `${origin}${path}`;
};
