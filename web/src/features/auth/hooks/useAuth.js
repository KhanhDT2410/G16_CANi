import { useState, useEffect, useCallback } from 'react';
import { authApi } from '../api/authApi';

export const useAuth = () => {
  const [user, setUser] = useState(() => authApi.getCurrentUser());
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const token = localStorage.getItem('cani_token');
    if (!token) {
      setUser(null);
      setIsLoading(false);
      return;
    }

    try {
      const currentUser = await authApi.getMe();
      setUser(currentUser);
      localStorage.setItem('cani_user', JSON.stringify(currentUser));
    } catch (err) {
      console.warn('Phiên làm việc hết hạn hoặc không hợp lệ:', err);
      setUser(null);
      localStorage.removeItem('cani_token');
      localStorage.removeItem('cani_user');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const logout = async () => {
    await authApi.logout();
    setUser(null);
  };

  return {
    user,
    isAuthenticated: !!user,
    isLoading,
    refreshUser,
    logout,
  };
};
