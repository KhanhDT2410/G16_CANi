import { apiClient } from '../../../services/api';

/**
 * API client giao tiếp với AdminWageMatrixController (/api/v1/admin/wage-matrix)
 * Hỗ trợ quản trị 4 vùng lương và bảng hệ số Region × JobTier (UC-30, UC-31).
 */
export const wageMatrixApi = {
  getWageRegions: async () => {
    const response = await apiClient.get('/v1/admin/wage-matrix/regions');
    return response.data?.data || [];
  },

  updateWageRegion: async (regionCode, payload) => {
    const response = await apiClient.put(`/v1/admin/wage-matrix/regions/${regionCode}`, payload);
    return response.data?.data;
  },

  getWageMatrixList: async (params = {}) => {
    const response = await apiClient.get('/v1/admin/wage-matrix', { params });
    return response.data?.data;
  },

  createWageMatrix: async (payload) => {
    const response = await apiClient.post('/v1/admin/wage-matrix', payload);
    return response.data?.data;
  },

  updateWageMatrix: async (matrixId, payload) => {
    const response = await apiClient.put(`/v1/admin/wage-matrix/${matrixId}`, payload);
    return response.data?.data;
  },

  toggleActiveWageMatrix: async (matrixId) => {
    const response = await apiClient.patch(`/v1/admin/wage-matrix/${matrixId}/toggle-active`);
    return response.data?.data;
  },

  deleteWageMatrix: async (matrixId) => {
    const response = await apiClient.delete(`/v1/admin/wage-matrix/${matrixId}`);
    return response.data;
  },
};
