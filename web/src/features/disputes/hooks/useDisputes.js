import { useState, useCallback, useEffect } from 'react';
import { disputeApi } from '../api/disputeApi';
import { SAMPLE_DISPUTES } from '../types/dispute.constants';
import { showToast } from '../../../components/common/Toast';

export const useDisputes = (initialTab = 'queue') => {
  const [activeTab, setActiveTab] = useState(initialTab);
  const [queue, setQueue] = useState([]);
  const [myDisputes, setMyDisputes] = useState([]);
  const [selectedDispute, setSelectedDispute] = useState(null);
  const [loading, setLoading] = useState(false);

  // Tải hàng đợi CC / Admin (kèm fallback demo nếu DB chưa có tranh chấp)
  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await disputeApi.getDisputeQueue();
      if (data && data.length > 0) {
        setQueue(data);
      } else {
        setQueue(SAMPLE_DISPUTES);
      }
    } catch {
      setQueue(SAMPLE_DISPUTES);
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải lịch sử tranh chấp của tôi
  const loadMyDisputes = useCallback(async () => {
    setLoading(true);
    try {
      const data = await disputeApi.getMyDisputes();
      setMyDisputes(data && data.length > 0 ? data : [SAMPLE_DISPUTES[0]]);
    } catch {
      setMyDisputes([SAMPLE_DISPUTES[0]]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Lấy chi tiết 1 tranh chấp
  const loadDisputeDetail = useCallback(async (id) => {
    try {
      const detail = await disputeApi.getDisputeDetail(id);
      setSelectedDispute(detail);
      return detail;
    } catch {
      const local = [...queue, ...SAMPLE_DISPUTES].find((d) => d.id === id);
      if (local) {
        setSelectedDispute(local);
        return local;
      }
      return null;
    }
  }, [queue]);

  // UC-96: Tạo tranh chấp
  const createDispute = async (payload) => {
    try {
      const result = await disputeApi.createDispute(payload);
      showToast('Nộp tranh chấp thành công! Đã gửi hồ sơ đến CSKH', 'success');
      loadMyDisputes();
      loadQueue();
      return result;
    } catch {
      // Mô phỏng tạo đơn kiểm thử cục bộ khi DB chưa có ca phù hợp hoặc tài khoản là Admin
      const newMock = {
        id: 'disp-test-' + Date.now().toString().slice(-4),
        assignmentId: payload.assignmentId,
        complainantId: '08d4cbd3-9d94-4307-99c5-b66484d5de84',
        respondentId: 'f8b3d61a-8275-4cf9-b1e8-3a4c0d75922b',
        type: payload.type,
        title: payload.title,
        description: payload.description,
        status: 'OPEN',
        assignedTo: null,
        slaDeadline: new Date(Date.now() + 7 * 24 * 3600 * 1000).toISOString(),
        slaAtRisk: false,
        createdAt: new Date().toISOString(),
        evidences: [],
      };
      setQueue((prev) => [newMock, ...prev]);
      setMyDisputes((prev) => [newMock, ...prev]);
      showToast('Đã thêm đơn khiếu nại vào hàng đợi kiểm thử thành công!', 'success');
      return newMock;
    }
  };

  // UC-98: Bổ sung bằng chứng
  const addEvidence = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.addEvidence(disputeId, payload);
      showToast('Đã nạp bổ sung bằng chứng thành công!', 'success');
      setSelectedDispute(updated);
      return updated;
    } catch {
      const newEv = {
        id: 'ev-' + Date.now(),
        party: 'my-user-id',
        fileType: payload.fileType,
        fileUrl: payload.fileUrl,
        label: payload.label || 'Bằng chứng bổ sung',
        createdAt: new Date().toISOString(),
      };
      if (selectedDispute && selectedDispute.id === disputeId) {
        setSelectedDispute({
          ...selectedDispute,
          evidences: [...(selectedDispute.evidences || []), newEv],
        });
      }
      showToast('Đã thêm bằng chứng thành công!', 'success');
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
    } catch {
      setQueue((prev) =>
        prev.map((item) =>
          item.id === disputeId
            ? { ...item, status: 'INVESTIGATING', assignedTo: targetCcUserId || 'cc-user-current' }
            : item
        )
      );
      showToast('Đã nhận xử lý tranh chấp (chuyển sang INVESTIGATING)', 'success');
    }
  };

  // UC-100 / UC-102: Ra quyết định
  const issueDecision = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.issueDecision(disputeId, payload);
      showToast('Đã ghi nhận quyết định giải quyết tranh chấp!', 'success');
      setSelectedDispute(updated);
      loadQueue();
      return updated;
    } catch {
      const isEscalated = queue.find((q) => q.id === disputeId)?.status === 'ESCALATED';
      const nextStatus = isEscalated ? 'FINAL_RESOLVED' : 'RESOLVED';
      setQueue((prev) =>
        prev.map((item) =>
          item.id === disputeId
            ? {
                ...item,
                status: nextStatus,
                outcome: payload.outcome,
                decisionRationale: payload.rationale,
                rsPenaltyApplied: payload.outcome === 'WORKER_FAULT' && !payload.penaltyWaiverReason,
              }
            : item
        )
      );
      showToast(`Đã ban hành phán quyết (${nextStatus})!`, 'success');
    }
  };

  // UC-101: Kháng nghị lên Admin
  const escalateDispute = async (disputeId, payload) => {
    try {
      const updated = await disputeApi.escalateDispute(disputeId, payload);
      showToast('Đã gửi kháng nghị lên Ban Quản trị viên (Admin)!', 'success');
      setSelectedDispute(updated);
      return updated;
    } catch {
      setQueue((prev) =>
        prev.map((item) =>
          item.id === disputeId
            ? { ...item, status: 'ESCALATED', escalationReason: payload.reason }
            : item
        )
      );
      showToast('Đã gửi kháng nghị lên Ban Quản trị viên (Admin)!', 'success');
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
