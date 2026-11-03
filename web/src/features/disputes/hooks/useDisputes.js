import { useState, useCallback, useEffect } from 'react';
import { disputeApi } from '../api/disputeApi';
import { showToast } from '../../../components/common/Toast';

export const useDisputes = (initialTab = 'queue') => {
  const [activeTab, setActiveTab] = useState(initialTab);
  const [queue, setQueue] = useState([]);
  const [myDisputes, setMyDisputes] = useState([]);
  const [selectedDispute, setSelectedDispute] = useState(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  // Tải hàng đợi tranh chấp (Customer Care & Admin)
  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await disputeApi.getDisputeQueue();
      setQueue(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải hàng đợi tranh chấp từ máy chủ';
      showToast(msg, 'warning');
      setQueue([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải lịch sử tranh chấp của tài khoản hiện tại (Worker / Event Organizer)
  const loadMyDisputes = useCallback(async () => {
    setLoading(true);
    try {
      const data = await disputeApi.getMyDisputes();
      setMyDisputes(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải lịch sử tranh chấp cá nhân';
      showToast(msg, 'warning');
      setMyDisputes([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Lấy chi tiết một tranh chấp theo ID
  const loadDisputeDetail = useCallback(async (id) => {
    setDetailLoading(true);
    try {
      const detail = await disputeApi.getDisputeDetail(id);
      setSelectedDispute(detail);
      return detail;
    } catch (err) {
      const msg = err.response?.data?.message || `Không tìm thấy tranh chấp với mã: ${id}`;
      showToast(msg, 'error');
      return null;
    } finally {
      setDetailLoading(false);
    }
  }, []);

  // UC-96: Nộp tranh chấp mới
  const createDispute = async (payload) => {
    try {
      const result = await disputeApi.createDispute(payload);
      showToast('Đã nộp đơn tranh chấp thành công đến bộ phận CSKH', 'success');
      loadMyDisputes();
      loadQueue();
      return result;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi gửi đơn tranh chấp';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-98: Bổ sung bằng chứng
  const addEvidence = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.addEvidence(disputeId, payload);
      showToast('Đã lưu bằng chứng mới thành công', 'success');
      setSelectedDispute(updated);
      loadDisputeDetail(disputeId);
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải lên bằng chứng';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-99: Phân công hoặc tự nhận xử lý
  const assignDispute = async (disputeId, targetCcUserId = null) => {
    try {
      const updated = await disputeApi.assignDispute(disputeId, targetCcUserId);
      showToast('Đã tiếp nhận phân công xử lý tranh chấp', 'success');
      setSelectedDispute(updated);
      loadQueue();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi phân công tranh chấp';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-100 (CC) / UC-102 (Admin): Ban hành phán quyết
  const issueDecision = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.issueDecision(disputeId, payload);
      showToast('Đã ghi nhận và ban hành quyết định giải quyết tranh chấp', 'success');
      setSelectedDispute(updated);
      loadQueue();
      loadMyDisputes();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể ban hành quyết định';
      showToast(msg, 'error');
      throw err;
    }
  };

  // UC-101: Kháng nghị lên Ban Quản trị viên
  const escalateDispute = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.escalateDispute(disputeId, payload);
      showToast('Đã chuyển kháng nghị lên Quản trị viên thẩm định', 'success');
      setSelectedDispute(updated);
      loadQueue();
      loadMyDisputes();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi gửi kháng nghị';
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
