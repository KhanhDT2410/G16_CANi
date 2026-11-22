import { useState, useEffect, useCallback } from 'react';
import { accountAppealApi } from '../api/accountAppealApi';

/**
 * Hook quản lý số lượng đơn giải trình tài khoản chờ Admin thẩm định
 * Cung cấp số lượng thời gian thực cho badge chấm đỏ / số thông báo trên Sidebar
 */
export function usePendingAccountAppeals() {
  const [pendingCount, setPendingCount] = useState(0);

  const fetchCount = useCallback(async () => {
    const count = await accountAppealApi.getPendingCount();
    setPendingCount(count);
  }, []);

  useEffect(() => {
    fetchCount();

    // Polling định kỳ mỗi 25 giây
    const intervalId = setInterval(fetchCount, 25000);

    // Lắng nghe sự kiện khi Admin vừa duyệt xong đơn hoặc có tương tác
    const handleRefresh = () => fetchCount();
    window.addEventListener('cani_appeal_reviewed', handleRefresh);
    window.addEventListener('cani_appeal_submitted', handleRefresh);

    return () => {
      clearInterval(intervalId);
      window.removeEventListener('cani_appeal_reviewed', handleRefresh);
      window.removeEventListener('cani_appeal_submitted', handleRefresh);
    };
  }, [fetchCount]);

  return {
    pendingCount,
    refreshPendingCount: fetchCount,
  };
}

export default usePendingAccountAppeals;
