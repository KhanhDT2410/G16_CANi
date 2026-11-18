import { useState, useEffect, useCallback } from 'react';
import { adminShiftApi } from '../api/adminShiftApi';
import { showToast } from '../../../components/common/Toast';

/**
 * Custom hook quản lý danh sách & xem chi tiết Ca làm việc cho Admin.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const useAdminShifts = () => {
  const [shifts, setShifts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Chi tiết ca
  const [selectedShift, setSelectedShift] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  const fetchShifts = useCallback(async () => {
    try {
      setLoading(true);
      const params = {
        page,
        size: 10,
      };
      if (searchTerm.trim()) {
        params.q = searchTerm.trim();
      }
      if (statusFilter !== 'ALL') {
        params.status = statusFilter;
      }

      const res = await adminShiftApi.getShifts(params);
      if (res) {
        setShifts(res.content || []);
        setTotalPages(res.totalPages || 0);
        setTotalElements(res.totalElements || 0);
      }
    } catch (err) {
      console.error('Lỗi khi tải danh sách ca làm việc:', err);
      showToast('Không thể tải danh sách ca làm việc', 'error');
    } finally {
      setLoading(false);
    }
  }, [page, searchTerm, statusFilter]);

  useEffect(() => {
    fetchShifts();
  }, [fetchShifts]);

  const handleSearchChange = (val) => {
    setSearchTerm(val);
    setPage(0);
  };

  const handleStatusChange = (status) => {
    setStatusFilter(status);
    setPage(0);
  };

  const openDetailModal = async (shiftId) => {
    try {
      setIsDetailOpen(true);
      setDetailLoading(true);
      const data = await adminShiftApi.getShiftDetail(shiftId);
      setSelectedShift(data);
    } catch (err) {
      console.error('Lỗi khi tải chi tiết ca làm:', err);
      showToast('Không thể tải chi tiết ca làm việc', 'error');
      setIsDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetailModal = () => {
    setIsDetailOpen(false);
    setSelectedShift(null);
  };

  return {
    shifts,
    loading,
    searchTerm,
    statusFilter,
    page,
    totalPages,
    totalElements,
    selectedShift,
    detailLoading,
    isDetailOpen,
    setPage,
    handleSearchChange,
    handleStatusChange,
    fetchShifts,
    openDetailModal,
    closeDetailModal,
  };
};

export default useAdminShifts;
