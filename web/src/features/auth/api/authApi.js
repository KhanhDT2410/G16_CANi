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
      throw new Error(error.message);
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

  verifyOtp: async (params) => {
    await apiClient.post('/auth/verify-otp', params);
  },

  resetPassword: async (params) => {
    await apiClient.post('/auth/reset-password', params);
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
