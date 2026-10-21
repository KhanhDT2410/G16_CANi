import { apiClient } from '../../../services/api';
import { supabase } from './supabaseClient';
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserDto,
  VerifyOtpRequest,
  ResetPasswordRequest,
  GoogleLoginRequest,
} from '../types/auth.types';

export const authApi = {
  /**
   * Đăng nhập thông qua Backend API (hoặc Supabase GoTrue proxy)
   */
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    try {
      const response = await apiClient.post<{ data: LoginResponse }>('/auth/login', credentials);
      return response.data.data;
    } catch (error: any) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Đăng nhập thất bại. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  /**
   * Đăng ký tài khoản Nhà Tổ Chức (hoặc Worker)
   */
  register: async (userData: RegisterRequest): Promise<UserDto> => {
    try {
      const response = await apiClient.post<{ data: UserDto }>('/auth/register', userData);
      return response.data.data;
    } catch (error: any) {
      if (!error.response) {
        throw new Error('Không thể kết nối đến máy chủ Backend.');
      }
      const message = error.response.data?.message || 'Đăng ký không thành công. Vui lòng thử lại.';
      throw new Error(message);
    }
  },

  /**
   * Đăng nhập qua Google sử dụng Supabase OAuth
   */
  loginWithGoogle: async (): Promise<void> => {
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

  /**
   * Đồng bộ token Google Supabase với Backend
   */
  syncGoogleAuth: async (request: GoogleLoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<{ data: LoginResponse }>('/auth/google', request);
    return response.data.data;
  },

  /**
   * Lấy thông tin người dùng hiện tại từ session / token
   */
  getMe: async (): Promise<UserDto> => {
    const response = await apiClient.get<{ data: UserDto }>('/auth/me');
    return response.data.data;
  },

  /**
   * Xác thực mã OTP email
   */
  verifyOtp: async (params: VerifyOtpRequest): Promise<void> => {
    await apiClient.post('/auth/verify-otp', params);
  },

  /**
   * Yêu cầu đặt lại mật khẩu qua email
   */
  resetPassword: async (params: ResetPasswordRequest): Promise<void> => {
    await apiClient.post('/auth/reset-password', params);
  },

  /**
   * Đăng xuất người dùng: xóa session ở Supabase và localStorage
   */
  logout: async (): Promise<void> => {
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

  /**
   * Lấy thông tin user hiện tại được cache trong localStorage
   */
  getCurrentUser: (): UserDto | null => {
    const userStr = localStorage.getItem('cani_user');
    if (!userStr) return null;
    try {
      return JSON.parse(userStr) as UserDto;
    } catch {
      return null;
    }
  },
};
