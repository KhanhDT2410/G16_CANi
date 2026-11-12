import { useState, useEffect, useCallback } from 'react';
import { noShowApi } from '../api/noShowApi';
import { showToast } from '../../../components/common/Toast';

export const useNoShows = () => {
  const [items, setItems] = useState([]);
  const [stats, setStats] = useState(null);
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(15);
  const [keyword, setKeyword] = useState('');

  const [selectedItem, setSelectedItem] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [manualModalOpen, setManualModalOpen] = useState(false);

  const [loading, setLoading] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fetchStats = useCallback(async () => {
    try {
      const data = await noShowApi.getStats();
      setStats(data);
    } catch (err) {
      console.error('Lỗi khi tải thống kê No-show:', err);
    }
  }, []);

  const fetchItems = useCallback(async () => {
    try {
      setLoading(true);
      const data = await noShowApi.getNoShows({
        keyword: keyword || undefined,
        page: currentPage,
        size: pageSize,
      });
      setItems(data?.content || []);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách No-show từ máy chủ';
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, [keyword, currentPage, pageSize]);

  useEffect(() => {
    fetchItems();
    fetchStats();
  }, [fetchItems, fetchStats]);

  const handleOpenDetail = (item) => {
    setSelectedItem(item);
    setDetailModalOpen(true);
  };

  const handleCloseDetail = () => {
    setSelectedItem(null);
    setDetailModalOpen(false);
  };

  const handleManualMark = async (assignmentId, { notes }) => {
    try {
      setSubmitting(true);
      await noShowApi.markNoShow(assignmentId, { notes });
      showToast('Đã xác nhận No-show thành công!', 'success');
      setManualModalOpen(false);
      fetchItems();
      fetchStats();
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể đánh dấu No-show cho ca này';
      showToast(msg, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleTriggerScan = async () => {
    try {
      setScanning(true);
      const result = await noShowApi.triggerScan();
      showToast(result?.message || 'Đã hoàn tất quét No-show toàn hệ thống!', 'success');
      fetchItems();
      fetchStats();
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi kích hoạt tiến trình quét No-show';
      showToast(msg, 'error');
    } finally {
      setScanning(false);
    }
  };

  return {
    items,
    stats,
    totalElements,
    currentPage,
    setCurrentPage,
    keyword,
    setKeyword,
    selectedItem,
    detailModalOpen,
    manualModalOpen,
    setManualModalOpen,
    loading,
    scanning,
    submitting,
    handleOpenDetail,
    handleCloseDetail,
    handleManualMark,
    handleTriggerScan,
    refresh: () => {
      fetchItems();
      fetchStats();
    },
  };
};

