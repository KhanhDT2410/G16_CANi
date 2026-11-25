import { useState, useEffect, useCallback } from 'react';
import { wageMatrixApi } from '../api/wageMatrixApi';
import { showToast } from '../../../components/common/Toast';
import { showConfirm } from '../../../components/common/PromptModal';

export const useWageMatrix = () => {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(15);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  const [filters, setFilters] = useState({
    city: '',
    jobType: '',
    isActive: '',
  });

  const [modalState, setModalState] = useState({
    isOpen: false,
    mode: 'create', // 'create' | 'edit'
    initialData: null,
  });
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState(null);

  const fetchData = useCallback(
    async (currentPage = page, currentFilters = filters) => {
      setLoading(true);
      try {
        const params = {
          page: currentPage,
          size: pageSize,
          sort: 'createdAt,desc',
        };
        if (currentFilters.city) params.city = currentFilters.city;
        if (currentFilters.jobType && currentFilters.jobType.trim()) {
          params.jobType = currentFilters.jobType.trim();
        }
        if (currentFilters.isActive !== '') {
          params.isActive = currentFilters.isActive === 'true';
        }

        const data = await wageMatrixApi.getWageMatrixList(params);
        if (data) {
          setItems(data.content || []);
          setTotalPages(data.totalPages || 1);
          setTotalElements(data.totalElements || 0);
        }
      } catch (error) {
        console.error('Lỗi khi tải danh sách ma trận lương:', error);
        const msg = error.response?.data?.message || error.message || 'Vui lòng thử lại.';
        showToast(`Không thể tải danh sách mức lương: ${msg}`, 'error');
      } finally {
        setLoading(false);
      }
    },
    [page, pageSize, filters]
  );

  useEffect(() => {
    fetchData(page, filters);
  }, [fetchData, page]);

  const handleApplyFilters = (newFilters) => {
    setFilters(newFilters);
    setPage(0);
    fetchData(0, newFilters);
  };

  const handleResetFilters = () => {
    const emptyFilters = { city: '', jobType: '', isActive: '' };
    setFilters(emptyFilters);
    setPage(0);
    fetchData(0, emptyFilters);
  };

  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  };

  const handleOpenCreateModal = () => {
    setModalState({
      isOpen: true,
      mode: 'create',
      initialData: null,
    });
  };

  const handleOpenEditModal = (item) => {
    setModalState({
      isOpen: true,
      mode: 'edit',
      initialData: item,
    });
  };

  const handleCloseModal = () => {
    setModalState({
      isOpen: false,
      mode: 'create',
      initialData: null,
    });
  };

  const handleSubmitModal = async (formData) => {
    setSubmitting(true);
    try {
      if (modalState.mode === 'create') {
        await wageMatrixApi.createWageMatrix(formData);
        showToast('Tạo mới cấu hình thù lao thành công', 'success');
      } else {
        await wageMatrixApi.updateWageMatrix(modalState.initialData.matrixId, formData);
        showToast('Cập nhật cấu hình thù lao thành công', 'success');
      }
      handleCloseModal();
      fetchData(page, filters);
    } catch (error) {
      console.error('Lỗi khi lưu ma trận lương:', error);
      const msg = error.response?.data?.message || 'Có lỗi xảy ra khi lưu cấu hình';
      showToast(msg, 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (item) => {
    if (togglingId) return;
    setTogglingId(item.matrixId);
    try {
      await wageMatrixApi.toggleActiveWageMatrix(item.matrixId);
      showToast(
        `Đã ${item.isActive ? 'tạm ngưng' : 'kích hoạt'} biểu thù lao thành công`,
        'success'
      );
      fetchData(page, filters);
    } catch (error) {
      console.error('Lỗi khi thay đổi trạng thái:', error);
      showToast('Không thể thay đổi trạng thái biểu thù lao', 'error');
    } finally {
      setTogglingId(null);
    }
  };

  const handleDelete = async (matrixId) => {
    const confirmed = await showConfirm({
      title: 'Xóa biểu thù lao',
      description: 'Bạn có chắc chắn muốn xóa vĩnh viễn mức thù lao này khỏi hệ thống?',
      type: 'danger',
      confirmText: 'Xóa vĩnh viễn',
    });
    if (!confirmed) {
      return;
    }
    try {
      await wageMatrixApi.deleteWageMatrix(matrixId);
      showToast('Đã xóa biểu thù lao thành công', 'success');
      fetchData(page, filters);
    } catch (error) {
      console.error('Lỗi khi xóa ma trận lương:', error);
      showToast('Không thể xóa biểu thù lao này', 'error');
    }
  };

  return {
    items,
    loading,
    page,
    totalPages,
    totalElements,
    filters,
    modalState,
    submitting,
    togglingId,
    handleApplyFilters,
    handleResetFilters,
    handlePageChange,
    handleOpenCreateModal,
    handleOpenEditModal,
    handleCloseModal,
    handleSubmitModal,
    handleToggleActive,
    handleDelete,
    refreshData: () => fetchData(page, filters),
  };
};
