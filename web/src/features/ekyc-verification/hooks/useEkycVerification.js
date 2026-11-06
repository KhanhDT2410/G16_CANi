import { useState, useEffect, useCallback } from 'react';
import { ekycVerificationApi } from '../api/ekycVerificationApi';
import { showToast } from '../../../components/common/Toast';
import { EKYC_DECISION } from '../types/ekycVerification.constants';

export const useEkycVerification = ({ onCountChange } = {}) => {
  const [ekycQueue, setEkycQueue] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await ekycVerificationApi.getWorkerEkycQueue();
      setEkycQueue(data);
      if (onCountChange) onCountChange(data.length);
    } catch (err) {
      setEkycQueue([]);
      if (onCountChange) onCountChange(0);
      showToast(err.response?.data?.message || 'Không thể tải hàng đợi eKYC từ máy chủ', 'error');
    } finally {
      setLoading(false);
    }
  }, [onCountChange]);

  useEffect(() => {
    loadQueue();
  }, [loadQueue]);

  const handleReview = async (verificationId, decision) => {
    const notes = decision === EKYC_DECISION.APPROVED
      ? 'Đã đối soát mắt người rõ ràng.'
      : 'Ảnh mờ hoặc không trùng khớp thông tin.';

    try {
      await ekycVerificationApi.reviewWorkerEkyc(verificationId, decision, notes);
      setEkycQueue((prev) => {
        const next = prev.filter((item) => item.verificationId !== verificationId);
        if (onCountChange) onCountChange(next.length);
        return next;
      });

      showToast(
        decision === EKYC_DECISION.APPROVED
          ? 'Đã phê duyệt eKYC thành công! Worker đã được cấp tích xanh.'
          : 'Đã từ chối hồ sơ eKYC.',
        decision === EKYC_DECISION.APPROVED ? 'success' : 'warning'
      );
    } catch (err) {
      showToast(err.response?.data?.message || 'Có lỗi xảy ra khi xử lý hồ sơ eKYC', 'error');
    }
  };

  return {
    ekycQueue,
    loading,
    handleReview,
    refresh: loadQueue,
  };
};

