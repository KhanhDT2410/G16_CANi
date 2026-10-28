import { useAuthGuard } from '../hooks/useAuthGuard';

export default function RequireRole({ allowedRoles, children }) {
  const { user, isLoading, isAuthenticated } = useAuthGuard(allowedRoles);

  if (isLoading) {
    return (
      <div className="page-loading-fallback">
        <div className="cani-spinner" />
        <span>Đang xác thực quyền truy cập...</span>
      </div>
    );
  }

  if (!isAuthenticated || (allowedRoles && user && !allowedRoles.includes(user.role))) {
    return null;
  }

  return children;
}
