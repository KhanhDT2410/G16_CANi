import { useState, useEffect, useCallback } from 'react';
import { rsAppealApi } from '../api/rsAppealApi';
import { showToast } from '../../../components/common/Toast';

export const useRsAppeals = () => {
  const [appeals, setAppeals] = useState([]);
  const [totalElements, setTotalElements] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState('');

  const [selectedAppeal, setSelectedAppeal] = useState(null);
  const [detailModalOpen, setDetailModalOpen] = useState(false);
  const [reviewModalOpen, setReviewModalOpen] = useState(false);

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const fetchAppeals = useCallback(async () => {
    try {
      setLoading(true);
      const data = await rsAppealApi.getAdminAppeals({
        status: statusFilter || undefined,
        page: currentPage,
        size: pageSize,
      });
      setAppeals(data?.content || []);
      setTotalElements(data?.totalElements || 0);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải hàng đợi kháng cáo từ máy chủ';
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, [statusFilter, currentPage, pageSize]);

  useEffect(() => {
    fetchAppeals();
  }, [fetchAppeals]);

  const handleOpenDetail = (appeal) => {
    setSelectedAppeal(appeal);
    setDetailModalOpen(true);
  };

  const handleOpenReview = (appeal) => {
    setSelectedAppeal(appeal);
    setReviewModalOpen(true);
  };

  const handleReviewDecision = async ({ approved, decisionNote }) => {
    if (!selectedAppeal) return;
    try {
      setSubmitting(true);
      const updated = await rsAppealApi.reviewAppeal(selectedAppeal.id, {
        approved,
        decisionNote,
      });

      const successMsg = approved
        ? `Đã chấp thuận kháng cáo! Khôi phục ${updated.penaltyAmount} điểm RS cho Worker.`
        : 'Đã từ chối đơn kháng cáo điểm uy tín.';

      showToast(successMsg, approved ? 'success' : 'info');
      setReviewModalOpen(false);
      setDetailModalOpen(false);
      fetchAppeals();
    } catch (err) {
      const msg = err.response?.data?.message || 'Có lỗi xảy ra khi xử lý đơn kháng cáo';
      showToast(msg, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  return {
    appeals,
    totalElements,
    currentPage,
    setCurrentPage,
    pageSize,
    statusFilter,
    setStatusFilter,
    selectedAppeal,
    detailModalOpen,
    setDetailModalOpen,
    reviewModalOpen,
    setReviewModalOpen,
    loading,
    submitting,
    handleOpenDetail,
    handleOpenReview,
    handleReviewDecision,
    fetchAppeals,
  };
};

