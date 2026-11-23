import { apiClient } from '../../../services/api';

const resolveApiOrigin = () => {
  const base = apiClient?.defaults?.baseURL || '';
  return base.replace(/\/api\/?$/, '');
};

export const resolveEkycSignedImageUrl = (signedPath) => {
  if (!signedPath) return '';
  if (/^https?:\/\//i.test(signedPath)) return signedPath;
  const normalizedPath = signedPath.startsWith('/') ? signedPath : `/${signedPath}`;
  return `${resolveApiOrigin()}${normalizedPath}`;
};

export const ekycReviewApi = {
  getQueue: async (targetType) => {
    const endpoint = targetType === 'eo' ? '/v1/admin/eo-ekyc/queue' : '/v1/admin/ekyc/queue';
    const response = await apiClient.get(endpoint);
    return response.data?.data || [];
  },

  getDetail: async (targetType, verificationId) => {
    const endpoint =
      targetType === 'eo'
        ? `/v1/admin/eo-ekyc/${verificationId}`
        : `/v1/admin/ekyc/${verificationId}`;
    const response = await apiClient.get(endpoint);
    return response.data?.data;
  },

  reviewDossier: async (targetType, verificationId, { decision, reviewNotes }) => {
    const endpoint =
      targetType === 'eo'
        ? `/v1/admin/eo-ekyc/${verificationId}/review`
        : `/v1/admin/ekyc/${verificationId}/review`;
    const response = await apiClient.post(endpoint, {
      decision,
      reviewNotes,
    });
    return response.data?.data;
  },

  updateLegalHold: async (targetType, verificationId, { legalHold, reason }) => {
    const endpoint =
      targetType === 'eo'
        ? `/v1/admin/eo-ekyc/${verificationId}/legal-hold`
        : `/v1/admin/ekyc/${verificationId}/legal-hold`;
    const response = await apiClient.patch(endpoint, {
      legalHold,
      reason,
    });
    return response.data?.data;
  },

  getSignedImageUrl: async (targetType, verificationId, slot) => {
    const response = await apiClient.get(
      `/v1/ekyc/images/${targetType}/${verificationId}/${slot}/signed-url`
    );
    return resolveEkycSignedImageUrl(response.data?.data?.signedUrl);
  },
};
