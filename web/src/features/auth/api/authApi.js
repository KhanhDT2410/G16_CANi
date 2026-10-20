import { apiClient } from '../../../services/api';

/**
 * Service API cho phân hệ Xác thực (Authentication)
 */
export const authApi = {
  /**
   * Gửi yêu cầu đăng nhập
   * @param {Object} credentials - { email, password }
   * @returns {Promise<Object>} Thông tin user và access token
   */
  login: async (credentials) => {
    try {
      const response = await apiClient.post('/auth/login', credentials);
      return response.data;
    } catch (error) {
      // Nếu backend chưa chạy hoặc chưa có endpoint, cung cấp thông báo rõ ràng
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend (http://localhost:8080).');
      }
      const message = error.response.data?.message || 'Đăng nhập không thành công. Vui lòng kiểm tra lại thông tin.';
      throw new Error(message);
    }
  },

  /**
   * Đăng ký tài khoản người dùng mới (Worker hoặc EO)
   * @param {Object} userData - { email, password, fullName, phone, role, companyName }
   * @returns {Promise<Object>} Thông tin tài khoản vừa tạo
   */
  register: async (userData) => {
    try {
      const response = await apiClient.post('/auth/register', userData);
      return response.data;
    } catch (error) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend (http://localhost:8080).');
      }
      const message = error.response.data?.message || 'Đăng ký không thành công. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  /**
   * Đăng xuất người dùng
   */
  logout: async () => {
    try {
      await apiClient.post('/auth/logout');
    } finally {
      localStorage.removeItem('cani_token');
      localStorage.removeItem('cani_user');
    }
  },

  /**
   * Lấy thông tin user hiện tại từ token đã lưu
   */
  getCurrentUser: () => {
    const userStr = localStorage.getItem('cani_user');
    return userStr ? JSON.parse(userStr) : null;
  },
};
