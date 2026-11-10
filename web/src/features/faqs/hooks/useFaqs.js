import { useState, useCallback, useEffect } from 'react';
import { faqApi } from '../api/faqApi';
import { showToast } from '../../../components/common/Toast';

/**
 * Custom Hook quản lý dữ liệu và hành vi cho phân hệ FAQ (UC-110).
 * Quy tắc: Không mock fallback khi gọi API quản trị.
 */
export const useFaqs = (mode = 'admin') => {
  const [faqs, setFaqs] = useState([]);
  const [publicFaqs, setPublicFaqs] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(false);
  const [analyticsLoading, setAnalyticsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedFaq, setSelectedFaq] = useState(null);

  // Tải danh sách FAQ quản trị (Admin / CC)
  const loadFaqs = useCallback(async (filters = {}) => {
    setLoading(true);
    setError(null);
    try {
      const params = {};
      if (filters.status && filters.status !== 'ALL') params.status = filters.status;
      if (filters.category && filters.category !== 'ALL') params.category = filters.category;
      params.size = filters.size || 100;
      const data = await faqApi.getFaqs(params);
      setFaqs(data?.content || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải danh sách câu hỏi thường gặp';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải danh sách FAQ công khai (Help Center)
  const loadPublicFaqs = useCallback(async (category = null) => {
    setLoading(true);
    setError(null);
    try {
      const catParam = category && category !== 'ALL' ? category : null;
      const data = await faqApi.getPublicFaqs(catParam);
      setPublicFaqs(data || []);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải trung tâm trợ giúp FAQ';
      setError(msg);
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  // Tải thống kê phân tích (UC-110c)
  const loadAnalytics = useCallback(async () => {
    setAnalyticsLoading(true);
    try {
      const data = await faqApi.getFaqAnalytics();
      setAnalytics(data);
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể tải số liệu phân tích FAQ';
      showToast(msg, 'error');
    } finally {
      setAnalyticsLoading(false);
    }
  }, []);

  // Tạo mới FAQ
  const createFaq = async (payload) => {
    setLoading(true);
    try {
      const created = await faqApi.createFaq(payload);
      showToast('Tạo câu hỏi thường gặp thành công!', 'success');
      await loadFaqs();
      loadAnalytics();
      return created;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi tạo câu hỏi thường gặp';
      showToast(msg, 'error');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Cập nhật FAQ
  const updateFaq = async (id, payload) => {
    setLoading(true);
    try {
      const updated = await faqApi.updateFaq(id, payload);
      showToast('Cập nhật câu hỏi thường gặp thành công!', 'success');
      await loadFaqs();
      loadAnalytics();
      return updated;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi cập nhật câu hỏi thường gặp';
      showToast(msg, 'error');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Xuất bản FAQ
  const publishFaq = async (id) => {
    setLoading(true);
    try {
      const published = await faqApi.publishFaq(id);
      showToast('Xuất bản câu hỏi lên Help Center thành công!', 'success');
      await loadFaqs();
      loadAnalytics();
      return published;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi xuất bản câu hỏi';
      showToast(msg, 'error');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Lưu trữ FAQ
  const archiveFaq = async (id) => {
    setLoading(true);
    try {
      const archived = await faqApi.archiveFaq(id);
      showToast('Đã lưu trữ câu hỏi thường gặp thành công!', 'success');
      await loadFaqs();
      loadAnalytics();
      return archived;
    } catch (err) {
      const msg = err.response?.data?.message || 'Lỗi khi lưu trữ câu hỏi';
      showToast(msg, 'error');
      throw err;
    } finally {
      setLoading(false);
    }
  };

  // Xem chi tiết công khai và tăng lượt xem
  const readPublicFaq = async (id) => {
    try {
      const detail = await faqApi.getPublicFaqById(id);
      setPublicFaqs((prev) =>
        prev.map((item) => (item.id === id ? { ...item, viewCount: detail.viewCount } : item))
      );
      return detail;
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể xem chi tiết câu hỏi';
      showToast(msg, 'error');
      throw err;
    }
  };

  useEffect(() => {
    if (mode === 'admin') {
      loadFaqs();
      loadAnalytics();
    } else if (mode === 'public') {
      loadPublicFaqs();
    }
  }, [mode, loadFaqs, loadAnalytics, loadPublicFaqs]);

  return {
    faqs,
    publicFaqs,
    analytics,
    loading,
    analyticsLoading,
    error,
    selectedFaq,
    setSelectedFaq,
    loadFaqs,
    loadPublicFaqs,
    loadAnalytics,
    createFaq,
    updateFaq,
    publishFaq,
    archiveFaq,
    readPublicFaq,
  };
};

