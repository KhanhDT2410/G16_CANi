import { useState, useEffect, useCallback } from 'react';
import { adminSkillApi } from '../api/adminSkillApi';
import { showToast } from '../../../components/common/Toast';

export const useAdminSkills = () => {
  const [skills, setSkills] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [pageSize] = useState(15);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [filters, setFilters] = useState({
    keyword: '',
    category: 'ALL',
    isActive: 'ALL',
  });

  const [modalState, setModalState] = useState({
    isOpen: false,
    mode: 'create', // 'create' | 'edit'
    initialData: null,
  });
  const [submitting, setSubmitting] = useState(false);
  const [togglingId, setTogglingId] = useState(null);

  const fetchCategories = useCallback(async () => {
    try {
      const data = await adminSkillApi.getCategories();
      if (Array.isArray(data)) {
        setCategories(data);
      }
    } catch (err) {
      console.warn('Không tải được danh mục kỹ năng:', err.message);
    }
  }, []);

  const fetchSkills = useCallback(
    async (currentPage = page, currentFilters = filters) => {
      setLoading(true);
      try {
        const params = {
          page: currentPage,
          size: pageSize,
          sort: 'name,asc',
        };
        if (currentFilters.keyword && currentFilters.keyword.trim()) {
          params.keyword = currentFilters.keyword.trim();
        }
        if (currentFilters.category && currentFilters.category !== 'ALL') {
          params.category = currentFilters.category;
        }
        if (currentFilters.isActive !== 'ALL') {
          params.isActive = currentFilters.isActive === 'true';
        }

        const data = await adminSkillApi.getSkills(params);
        if (data) {
          setSkills(data.content || []);
          setTotalPages(data.totalPages || 1);
          setTotalElements(data.totalElements || 0);
        }
      } catch (error) {
        console.error('Lỗi khi tải danh sách kỹ năng:', error);
        const msg = error.response?.data?.message || 'Không thể tải danh sách kỹ năng';
        showToast(msg, 'error');
      } finally {
        setLoading(false);
      }
    },
    [page, pageSize, filters]
  );

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  useEffect(() => {
    fetchSkills(page, filters);
  }, [fetchSkills, page, filters]);

  const openCreateModal = () => {
    setModalState({ isOpen: true, mode: 'create', initialData: null });
  };

  const openEditModal = (skill) => {
    setModalState({ isOpen: true, mode: 'edit', initialData: skill });
  };

  const closeModal = () => {
    setModalState({ isOpen: false, mode: 'create', initialData: null });
  };

  const handleSaveSkill = async (formData) => {
    setSubmitting(true);
    try {
      if (modalState.mode === 'edit' && modalState.initialData?.skillId) {
        await adminSkillApi.updateSkill(modalState.initialData.skillId, formData);
        showToast('Cập nhật kỹ năng thành công', 'success');
      } else {
        await adminSkillApi.createSkill(formData);
        showToast('Thêm kỹ năng mới thành công', 'success');
      }
      closeModal();
      fetchSkills(page, filters);
      fetchCategories();
    } catch (error) {
      console.error('Lỗi khi lưu kỹ năng:', error);
      const msg = error.response?.data?.message || 'Không thể lưu thông tin kỹ năng';
      showToast(msg, 'error');
      throw error;
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleStatus = async (skillId) => {
    setTogglingId(skillId);
    try {
      const updated = await adminSkillApi.toggleStatus(skillId);
      const nextStatus = updated?.isActive;
      showToast(
        nextStatus ? 'Đã kích hoạt kỹ năng vào hệ thống' : 'Đã tạm ẩn kỹ năng khỏi sự kiện',
        'success'
      );
      setSkills((prev) =>
        prev.map((item) =>
          item.skillId === skillId ? { ...item, isActive: nextStatus } : item
        )
      );
    } catch (error) {
      console.error('Lỗi khi thay đổi trạng thái:', error);
      const msg = error.response?.data?.message || 'Không thể thay đổi trạng thái kỹ năng';
      showToast(msg, 'error');
    } finally {
      setTogglingId(null);
    }
  };

  const handleFilterChange = (key, value) => {
    setPage(0);
    setFilters((prev) => ({ ...prev, [key]: value }));
  };

  const handlePageChange = (newPage) => {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  };

  return {
    skills,
    categories,
    loading,
    page,
    totalPages,
    totalElements,
    filters,
    modalState,
    submitting,
    togglingId,
    openCreateModal,
    openEditModal,
    closeModal,
    handleSaveSkill,
    handleToggleStatus,
    handleFilterChange,
    handlePageChange,
    refreshSkills: () => fetchSkills(page, filters),
  };
};
