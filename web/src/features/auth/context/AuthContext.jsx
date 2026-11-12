import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { authApi } from '../api/authApi';

const AuthContext = createContext(null);

/**
 * AuthProvider - Quản lý trạng thái xác thực tập trung cho toàn bộ ứng dụng Web CANi.
 * Chỉ gọi GET /api/v1/auth/me 1 lần duy nhất khi ứng dụng khởi chạy.
 * Đảm bảo đồng bộ 100% giữa Header, Sidebar, Route Guards và các trang chức năng.
 */
export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => authApi.getCurrentUser());
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const token = localStorage.getItem('cani_token');
    if (!token) {
      setUser(null);
      setIsLoading(false);
      return null;
    }

    try {
      const currentUser = await authApi.getMe();
      setUser(currentUser);
      localStorage.setItem('cani_user', JSON.stringify(currentUser));
      return currentUser;
    } catch (err) {
      console.warn('[AuthContext] Phiên làm việc hết hạn hoặc không hợp lệ:', err);
      setUser(null);
      localStorage.removeItem('cani_token');
      localStorage.removeItem('cani_refresh_token');
      localStorage.removeItem('cani_user');
      return null;
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Gọi getMe đúng 1 lần duy nhất khi Provider mount
  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const login = useCallback((userData, token, refreshToken) => {
    if (token) localStorage.setItem('cani_token', token);
    if (refreshToken) localStorage.setItem('cani_refresh_token', refreshToken);
    if (userData) {
      localStorage.setItem('cani_user', JSON.stringify(userData));
      setUser(userData);
    }
    setIsLoading(false);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (e) {
      console.warn('[AuthContext] Lỗi khi logout:', e);
    } finally {
      setUser(null);
      setIsLoading(false);
    }
  }, []);

  const value = useMemo(
    () => ({
      user,
      isAuthenticated: !!user,
      isLoading,
      refreshUser,
      login,
      logout,
      setUser,
    }),
    [user, isLoading, refreshUser, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

/**
 * useAuth hook dùng chung toàn ứng dụng.
 * Tự động lấy trạng thái từ AuthContext; fallback an toàn nếu dùng ngoài Provider.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    const storedUser = authApi.getCurrentUser();
    return {
      user: storedUser,
      isAuthenticated: !!storedUser,
      isLoading: false,
      refreshUser: async () => storedUser,
      login: () => {},
      logout: () => authApi.logout(),
      setUser: () => {},
    };
  }
  return context;
};
