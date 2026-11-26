import { apiClient } from '../../../services/api';
import { supabase } from './supabaseClient';

export const authApi = {
  login: async (credentials) => {
    try {
      const response = await apiClient.post('/auth/login', credentials);
      return response.data.data;
    } catch (error) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Đăng nhập thất bại. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  sendOtp: async (email, otpType = 'REGISTER') => {
    try {
      const response = await apiClient.post('/auth/send-otp', { email, otpType });
      return response.data;
    } catch (error) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Không thể gửi mã xác thực OTP. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  register: async (userData) => {
    try {
      const response = await apiClient.post('/auth/register', userData);
      return response.data.data;
    } catch (error) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Đăng ký không thành công. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  loginWithGoogle: async () => {
    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: {
        redirectTo: `${window.location.origin}/auth/callback`,
      },
    });
    if (error) {
      throw new Error('Không thể khởi tạo đăng nhập Google. Vui lòng thử lại.');
    }
  },

  syncGoogleAuth: async (request) => {
    const response = await apiClient.post('/auth/google', request);
    return response.data.data;
  },

  getMe: async () => {
    const response = await apiClient.get('/auth/me');
    return response.data.data;
  },

  resetPassword: async (params) => {
    try {
      const response = await apiClient.post('/auth/reset-password', params);
      return response.data;
    } catch (error) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Không thể gửi yêu cầu đặt lại mật khẩu. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  confirmResetPassword: async (params) => {
    try {
      const response = await apiClient.post('/auth/confirm-reset-password', params);
      return response.data;
    } catch (error) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Không thể đặt lại mật khẩu. Vui lòng kiểm tra mã OTP.';
      throw new Error(message);
    }
  },

  refreshToken: async (refreshToken) => {
    const response = await apiClient.post('/auth/refresh', { refreshToken });
    return response.data.data;
  },

  logout: async () => {
    try {
      await supabase.auth.signOut();
    } catch (e) {
      console.warn('Lỗi khi signOut Supabase client:', e);
    } finally {
      localStorage.removeItem('cani_token');
      localStorage.removeItem('cani_refresh_token');
      localStorage.removeItem('cani_user');
      localStorage.removeItem('cani_eo_avatar');
    }
  },

  getCurrentUser: () => {
    const userStr = localStorage.getItem('cani_user');
    if (!userStr) return null;
    try {
      return JSON.parse(userStr);
    } catch {
      return null;
    }
  },
};
