import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';
import type { UserRole } from '../types/auth.types';

export const useAuthGuard = (allowedRoles?: UserRole[]) => {
  const { user, isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (isLoading) return;

    if (!isAuthenticated) {
      navigate('/login', { state: { from: location.pathname }, replace: true });
      return;
    }

    if (allowedRoles && user && !allowedRoles.includes(user.role)) {
      // Điều hướng về trang mặc định theo role của user nếu truy cập trái phép
      if (user.role === 'ADMIN') navigate('/admin', { replace: true });
      else if (user.role === 'CUSTOMER_CARE') navigate('/cskh', { replace: true });
      else if (user.role === 'EVENT_ORGANIZER') navigate('/eo', { replace: true });
      else navigate('/worker', { replace: true });
    }
  }, [isAuthenticated, isLoading, user, allowedRoles, navigate, location.pathname]);

  return { user, isLoading, isAuthenticated };
};
