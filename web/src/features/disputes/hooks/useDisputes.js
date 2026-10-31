import { useState, useCallback, useEffect } from 'react';
import { disputeApi } from '../api/disputeApi';
import { showToast } from '../../../components/common/Toast';

export const useDisputes = (initialTab = 'queue') => {
  const [activeTab, setActiveTab] = useState(initialTab); // 'queue' | 'my'
  const [queue, setQueue] = useState([]);
  const [myDisputes, setMyDisputes] = useState([]);
  const [selectedDispute, setSelectedDispute] = useState(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  // Tải hàng đợi CC / Admin
  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await disputeApi.getDisputeQueue();
      setQueue(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải hàng đợi tranh chấp';
      showToast(msg, 'warning');
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải lịch sử tranh chấp của tôi
  const loadMyDisputes = useCallback(async () => {
    setLoading(true);
    try {
      const data = await disputeApi.getMyDisputes();
      setMyDisputes(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải lịch sử tranh chấp cá nhân';
      showToast(msg, 'warning');
    } finally {
      setLoading(false);
    }
  }, []);

  // Lấy chi tiết 1 tranh chấp
  const loadDisputeDetail = useCallback(async (id) => {
    setDetailLoading(true);
    try {
      const detail = await disputeApi.getDisputeDetail(id);
      setSelectedDispute(detail);
      return detail;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể lấy thông tin chi tiết tranh chấp';
      showToast(msg, 'error');
      return null;
    } finally {
      setDetailLoading(false);
    }
  }, []);

  // UC-96: Tạo tranh chấp
  const createDispute = async (payload) => {
    try {
      const result = await disputeApi.createDispute(payload);
      showToast('Nộp tranh chấp thành công! Đã gửi hồ sơ đến CSKH', 'success');
      loadMyDisputes();
      loadQueue();
      return result;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể nộp tranh chấp';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-98: Bổ sung bằng chứng
  const addEvidence = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.addEvidence(disputeId, payload);
      showToast('Đã nạp bổ sung bằng chứng thành công!', 'success');
      setSelectedDispute(updated);
      loadMyDisputes();
      loadQueue();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi nạp bằng chứng';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-99: Nhận / Phân công xử lý
  const assignDispute = async (disputeId, targetCcUserId = null) => {
    try {
      const updated = await disputeApi.assignDispute(disputeId, targetCcUserId);
      showToast('Đã phân công / tự nhận tranh chấp thành công', 'success');
      setSelectedDispute(updated);
      loadQueue();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể phân công tranh chấp';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-100 / UC-102: Ra quyết định
  const issueDecision = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.issueDecision(disputeId, payload);
      showToast('Đã ghi nhận quyết định giải quyết tranh chấp!', 'success');
      setSelectedDispute(updated);
      loadQueue();
      loadMyDisputes();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể ghi nhận quyết định';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-101: Kháng nghị lên Admin
  const escalateDispute = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.escalateDispute(disputeId, payload);
      showToast('Đã gửi kháng nghị lên Ban Quản trị viên (Admin)!', 'success');
      setSelectedDispute(updated);
      loadMyDisputes();
      loadQueue();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể gửi kháng nghị';
      showToast(msg, 'error');
      throw err;
    }
  };

  useEffect(() => {
    if (activeTab === 'queue') {
      loadQueue();
    } else if (activeTab === 'my') {
      loadMyDisputes();
    }
  }, [activeTab, loadQueue, loadMyDisputes]);

  return {
    activeTab,
    setActiveTab,
    queue,
    myDisputes,
    selectedDispute,
    setSelectedDispute,
    loading,
    detailLoading,
    loadQueue,
    loadMyDisputes,
    loadDisputeDetail,
    createDispute,
    addEvidence,
    assignDispute,
    issueDecision,
    escalateDispute,
  };
};

