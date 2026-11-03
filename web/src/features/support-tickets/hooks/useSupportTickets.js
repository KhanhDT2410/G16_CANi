import { useState, useCallback, useEffect } from 'react';
import { supportTicketApi } from '../api/supportTicketApi';
import { showToast } from '../../../components/common/Toast';

export const useSupportTickets = (initialMode = 'queue') => {
  const [queue, setQueue] = useState([]);
  const [myTickets, setMyTickets] = useState([]);
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);

  // Tải hàng đợi ticket cho Customer Care & Admin (SCR-25)
  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await supportTicketApi.getQueue();
      setQueue(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải hàng đợi phiếu hỗ trợ';
      showToast(msg, 'warning');
      setQueue([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải danh sách ticket của tôi (UC-106)
  const loadMyTickets = useCallback(async () => {
    setLoading(true);
    try {
      const data = await supportTicketApi.getMyTickets();
      setMyTickets(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách phiếu hỗ trợ của bạn';
      showToast(msg, 'warning');
      setMyTickets([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải chi tiết một ticket kèm thread tin nhắn (SCR-26 / UC-106)
  const loadTicketDetail = useCallback(async (id) => {
    setDetailLoading(true);
    try {
      const detail = await supportTicketApi.getTicketDetail(id);
      setSelectedTicket(detail);
      return detail;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không tìm thấy phiếu hỗ trợ';
      showToast(msg, 'error');
      return null;
    } finally {
      setDetailLoading(false);
    }
  }, []);

  // UC-105: Nộp ticket mới
  const createTicket = async (payload) => {
    setLoading(true);
    try {
      const newTicket = await supportTicketApi.createTicket(payload);
      showToast('Phiếu hỗ trợ đã được tạo thành công!', 'success');
      await loadMyTickets();
      return newTicket;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể nộp phiếu hỗ trợ';
      showToast(msg, 'error');
      return null;
    } finally {
      setLoading(false);
    }
  };

  // SCR-26 / UC-106: Phản hồi ticket (hỗ trợ cả reply công khai và internal note)
  const replyTicket = async (id, payload, isStaff) => {
    try {
      const updated = isStaff
        ? await supportTicketApi.replyTicketAdmin(id, payload)
        : await supportTicketApi.replyTicketUser(id, payload);

      showToast(
        payload.isInternal ? 'Đã thêm ghi chú nội bộ thành công' : 'Đã gửi phản hồi thành công',
        'success'
      );
      setSelectedTicket(updated);
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể gửi phản hồi cho phiếu';
      showToast(msg, 'error');
      return null;
    }
  };

  // SCR-25 / SCR-26: Phân công hoặc nhận xử lý ticket
  const assignTicket = async (id, targetCcUserId = null) => {
    try {
      const updated = await supportTicketApi.assignTicket(id, targetCcUserId);
      showToast('Tiếp nhận phân công phiếu hỗ trợ thành công', 'success');
      setSelectedTicket(updated);
      await loadQueue();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể phân công phiếu hỗ trợ';
      showToast(msg, 'error');
      return null;
    }
  };

  // SCR-26: CC/Admin đóng ticket kèm lý do
  const closeTicket = async (id, rationale) => {
    try {
      const updated = await supportTicketApi.closeTicket(id, rationale);
      showToast('Đã đóng phiếu hỗ trợ thành công', 'success');
      setSelectedTicket(updated);
      await loadQueue();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể đóng phiếu hỗ trợ';
      showToast(msg, 'error');
      return null;
    }
  };

  // UC-106: Người dùng đánh dấu đã giải quyết
  const resolveTicket = async (id) => {
    try {
      const updated = await supportTicketApi.resolveTicket(id);
      showToast('Đã xác nhận giải quyết vấn đề và đóng phiếu', 'success');
      setSelectedTicket(updated);
      await loadMyTickets();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể cập nhật trạng thái phiếu';
      showToast(msg, 'error');
      return null;
    }
  };

  useEffect(() => {
    if (initialMode === 'queue') {
      loadQueue();
    } else {
      loadMyTickets();
    }
  }, [initialMode, loadQueue, loadMyTickets]);

  return {
    queue,
    myTickets,
    selectedTicket,
    loading,
    detailLoading,
    setSelectedTicket,
    loadQueue,
    loadMyTickets,
    loadTicketDetail,
    createTicket,
    replyTicket,
    assignTicket,
    closeTicket,
    resolveTicket,
  };
};

