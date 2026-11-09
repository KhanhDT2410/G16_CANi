import { useState, useEffect, useCallback } from 'react';
import { skillVerificationApi } from '../api/skillVerificationApi';
import { showToast } from '../../../components/common/Toast';

export const useSkillVerification = ({ onCountChange } = {}) => {
  const [skillQueue, setSkillQueue] = useState([]);
  const [loading, setLoading] = useState(true);

  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const data = await skillVerificationApi.getPendingSkillQueue();
      setSkillQueue(data);
      if (onCountChange) onCountChange(data.length);
    } catch (err) {
      setSkillQueue([]);
      if (onCountChange) onCountChange(0);
      showToast(err.response?.data?.message || 'Không thể tải hàng đợi kỹ năng từ máy chủ', 'error');
    } finally {
      setLoading(false);
    }
  }, [onCountChange]);

  useEffect(() => {
    loadQueue();
  }, [loadQueue]);

  const handleReview = async (id, approved) => {
    const notes = approved
      ? 'Chứng chỉ hợp lệ, đã đối soát mắt.'
      : 'Chứng chỉ mờ hoặc không trùng khớp thông tin.';

    try {
      await skillVerificationApi.reviewSkill(id, approved, notes);
      setSkillQueue((prev) => {
        const next = prev.filter((item) => item.id !== id);
        if (onCountChange) onCountChange(next.length);
        return next;
      });

      showToast(
        approved
          ? 'Đã duyệt kỹ năng thành công! Worker được cộng +25 SP_verify.'
          : 'Đã từ chối yêu cầu xác thực kỹ năng.',
        approved ? 'success' : 'warning'
      );
    } catch (err) {
      showToast(err.response?.data?.message || 'Không thể xử lý yêu cầu thẩm định kỹ năng.', 'error');
    }
  };

  return {
    skillQueue,
    loading,
    handleReview,
    refresh: loadQueue,
  };
};

