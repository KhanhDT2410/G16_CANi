import { useState, useCallback } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

/**
 * Hook quản lý trạng thái hiển thị hồ sơ Worker cho EO
 * Lấy dữ liệu thực tế trực tiếp từ Database qua backend API
 */
export function useWorkerProfile() {
  const [isOpen, setIsOpen] = useState(false);
  const [workerId, setWorkerId] = useState(null);
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const openProfile = useCallback(async (id) => {
    if (!id) return;
    setWorkerId(id);
    setIsOpen(true);
    setLoading(true);
    setError(null);
    setProfile(null);

    try {
      const data = await eoService.getWorkerProfile(id);
      setProfile(data);
    } catch (err) {
      console.error('Lỗi khi tải hồ sơ Worker:', err);
      const msg = err.response?.data?.message || 'Không thể tải hồ sơ nhân sự từ hệ thống';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  const closeProfile = useCallback(() => {
    setIsOpen(false);
    setWorkerId(null);
    setProfile(null);
    setError(null);
  }, []);

  return {
    isOpen,
    workerId,
    profile,
    loading,
    error,
    openProfile,
    closeProfile,
    setProfile,
  };
}
