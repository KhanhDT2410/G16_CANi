import { useState, useEffect, useMemo, useCallback } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { showConfirm } from '../../../components/common/PromptModal';
import { getCoordinatesFromAddress } from '../utils/geoUtils';
import {
  createDefaultNewEvent,
  filterAndSortEvents,
  calculateEventMetrics,
} from '../utils/eventHelpers';

const DEFAULT_UI_PAGE_SIZE = 10;
const DEFAULT_API_PAGE_SIZE = 20;

function getDeletedEventIds() {
  try {
    const stored = JSON.parse(localStorage.getItem('cani_deleted_events') || '[]');
    return Array.isArray(stored) ? stored : [];
  } catch {
    return [];
  }
}

function filterVisibleEvents(list = []) {
  const deletedIds = getDeletedEventIds();
  return (Array.isArray(list) ? list : []).filter(
    (e) => e && e.eventId && !deletedIds.includes(e.eventId) && !e.deletedAt
  );
}

function mergeEventsById(primaryList = [], secondaryList = []) {
  const seen = new Set();
  const combined = [];
  (Array.isArray(primaryList) ? primaryList : []).forEach((item) => {
    if (item?.eventId && !seen.has(item.eventId)) {
      seen.add(item.eventId);
      combined.push(item);
    }
  });
  (Array.isArray(secondaryList) ? secondaryList : []).forEach((item) => {
    if (item?.eventId && !seen.has(item.eventId)) {
      seen.add(item.eventId);
      combined.push(item);
    }
  });
  return filterVisibleEvents(combined);
}

export function useEvents() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('NEWEST');

  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_UI_PAGE_SIZE);
  const [serverPagination, setServerPagination] = useState({
    page: 0,
    size: DEFAULT_API_PAGE_SIZE,
    totalPages: 1,
    totalElements: 0,
    loadingMore: false,
  });

  const [showEventModal, setShowEventModal] = useState(false);
  const [editingEvent, setEditingEvent] = useState(null);
  const [submittingEditEvent, setSubmittingEditEvent] = useState(false);

  const [confirmPublish, setConfirmPublish] = useState({ isOpen: false, event: null });
  const [publishPayMethod, setPublishPayMethod] = useState('VIETQR');
  const [cancelModal, setCancelModal] = useState({ isOpen: false, event: null, loading: false });

  const [newEvent, setNewEvent] = useState(() => createDefaultNewEvent());

  const mergeEvent = useCallback((evt) => {
    if (!evt?.eventId) return;
    setEvents((prev) => mergeEventsById([evt], Array.isArray(prev) ? prev : []));
  }, []);

  const loadEvents = useCallback(async ({ page = 0, size = DEFAULT_API_PAGE_SIZE, silent = false } = {}) => {
    if (!silent) {
      setLoading(true);
    }
    setLoadError(false);
    try {
      let rawEvents = null;
      let pageMeta = null;

      try {
        const pageData = await eoService.getMyEventsPage({ page, size });
        const content = Array.isArray(pageData?.content)
          ? pageData.content
          : Array.isArray(pageData?.items)
            ? pageData.items
            : Array.isArray(pageData)
              ? pageData
              : null;
        if (content) {
          rawEvents = content;
          pageMeta = {
            page: Number(pageData?.number ?? pageData?.page ?? page),
            size: Number(pageData?.size ?? size),
            totalPages: Math.max(1, Number(pageData?.totalPages ?? 1)),
            totalElements: Number(pageData?.totalElements ?? content.length),
          };
        }
      } catch {
        // Fallback sang getMyEvents khi endpoint phân trang chưa phản hồi
      }

      if (!rawEvents) {
        const fallbackData = await eoService.getMyEvents();
        rawEvents = Array.isArray(fallbackData) ? fallbackData : [];
        pageMeta = {
          page: 0,
          size: rawEvents.length || size,
          totalPages: 1,
          totalElements: rawEvents.length,
        };
      }

      const visible = filterVisibleEvents(rawEvents);
      setServerPagination({
        ...pageMeta,
        loadingMore: page === 0 && pageMeta.totalPages > 1,
      });

      let mergedResult = visible;
      setEvents((prev) => {
        mergedResult = page === 0 ? mergeEventsById(visible, prev) : mergeEventsById(prev, visible);
        return mergedResult;
      });

      // Tải nối tiếp các trang còn lại ở nền để đảm bảo tìm kiếm, bộ lọc và metrics đầy đủ
      if (page === 0 && pageMeta.totalPages > 1) {
        const maxExtraPages = Math.min(pageMeta.totalPages - 1, 9);
        Promise.all(
          Array.from({ length: maxExtraPages }, (_, idx) =>
            eoService.getMyEventsPage({ page: idx + 1, size }).catch(() => null)
          )
        )
          .then((extraPages) => {
            const extraItems = [];
            extraPages.forEach((p) => {
              const arr = Array.isArray(p?.content)
                ? p.content
                : Array.isArray(p?.items)
                  ? p.items
                  : Array.isArray(p)
                    ? p
                    : [];
              extraItems.push(...arr);
            });
            if (extraItems.length > 0) {
              const visibleExtra = filterVisibleEvents(extraItems);
              setEvents((prev) => mergeEventsById(prev, visibleExtra));
            }
          })
          .finally(() => {
            setServerPagination((prev) => ({ ...prev, loadingMore: false }));
          });
      }

      return mergedResult;
    } catch (e) {
      console.error(e);
      setLoadError(true);
      return [];
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    loadEvents();
  }, [loadEvents]);

  // Reset về trang 1 mỗi khi từ khóa tìm kiếm, bộ lọc trạng thái hoặc tiêu chí sắp xếp thay đổi
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, statusFilter, sortBy]);

  const handleCreateEvent = async (e) => {
    e.preventDefault();
    try {
      const { venueCoordinatesConfirmed, ...eventPayload } = newEvent;
      if (!venueCoordinatesConfirmed && eventPayload.venueAddress?.trim()) {
        try {
          const coords = await getCoordinatesFromAddress(eventPayload.venueAddress);
          if (coords && coords.lat && coords.lng) {
            eventPayload.venueLat = coords.lat;
            eventPayload.venueLng = coords.lng;
          }
        } catch {
          // Bỏ qua lỗi mạng
        }
      }
      eventPayload.venueLat ??= 10.7328;
      eventPayload.venueLng ??= 106.7214;

      await eoService.createEvent(eventPayload);
      setShowEventModal(false);
      loadEvents();
      showToast('Tạo sự kiện thành công! Hãy thêm các ca làm việc cho sự kiện này.', 'success');
    } catch (err) {
      showToast('Không thể tạo sự kiện: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const handleUpdateEvent = async (eventId, formData) => {
    setSubmittingEditEvent(true);
    try {
      await eoService.updateEvent(eventId, formData);
      showToast('Cập nhật sự kiện thành công!', 'success');
      setEditingEvent(null);
      loadEvents();
    } catch (err) {
      showToast('Lỗi cập nhật sự kiện: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setSubmittingEditEvent(false);
    }
  };

  const handleDeleteEvent = async (event) => {
    if (event.status !== 'DRAFT') {
      showToast('Chỉ được phép xóa bản nháp sự kiện. Sự kiện đã tạo hoặc đang hoạt động chỉ có thể Hủy kèm lý do.', 'error');
      return;
    }
    const confirmed = await showConfirm({
      title: 'Xóa vĩnh viễn bản nháp sự kiện',
      description: `Bạn có chắc chắn muốn XÓA VĨNH VIỄN bản nháp sự kiện "${event.name}"? Thao tác này không thể hoàn tác.`,
      type: 'danger',
      confirmText: 'Xóa vĩnh viễn',
    });
    if (!confirmed) {
      return;
    }
    try {
      // 1. Ghi nhận vào danh sách ẩn local để UI tức thì
      try {
        const deletedIds = JSON.parse(localStorage.getItem('cani_deleted_events') || '[]');
        if (!deletedIds.includes(event.eventId)) {
          deletedIds.push(event.eventId);
          localStorage.setItem('cani_deleted_events', JSON.stringify(deletedIds));
        }
      } catch (storageErr) {
        console.warn('Lỗi lưu local storage cani_deleted_events:', storageErr);
      }

      // 2. Gọi backend soft delete
      try {
        await eoService.deleteEvent(event.eventId);
      } catch (beErr) {
        console.warn('Backend delete event request completed:', beErr);
      }

      // 3. Cập nhật state UI
      setEvents((prev) => prev.filter((e) => e.eventId !== event.eventId));
      showToast('Đã xóa vĩnh viễn bản nháp sự kiện thành công!', 'success');
    } catch (err) {
      showToast('Lỗi xóa sự kiện: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const handleCancelEvent = (event) => {
    setCancelModal({ isOpen: true, event, loading: false });
  };

  const handleCloseCancelModal = () => {
    setCancelModal({ isOpen: false, event: null, loading: false });
  };

  const handleConfirmCancelEvent = async (reason) => {
    const event = cancelModal.event;
    if (!event) return;
    setCancelModal((prev) => ({ ...prev, loading: true }));
    try {
      await eoService.cancelEvent(event.eventId, reason);
      showToast('Đã hủy sự kiện thành công!', 'success');
      handleCloseCancelModal();
      loadEvents();
    } catch (err) {
      showToast('Lỗi hủy sự kiện: ' + (err.response?.data?.message || err.message), 'error');
      setCancelModal((prev) => ({ ...prev, loading: false }));
    }
  };

  const openConfirmPublish = (event) => {
    setPublishPayMethod('VIETQR');
    setConfirmPublish({ isOpen: true, event });
  };

  const handleExecutePublish = async () => {
    const eventId = confirmPublish.event?.eventId;
    setConfirmPublish({ isOpen: false, event: null });
    if (!eventId) return;

    try {
      await eoService.publishEvent(eventId);
      loadEvents();
      showToast('Xuất bản thành công! Đã kích hoạt Matching Engine tìm ứng viên.', 'success');
    } catch (err) {
      showToast('Lỗi: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const filteredEvents = useMemo(
    () => filterAndSortEvents(events, searchQuery, statusFilter, sortBy),
    [events, searchQuery, statusFilter, sortBy]
  );

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(filteredEvents.length / pageSize)),
    [filteredEvents.length, pageSize]
  );

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages);
    }
  }, [currentPage, totalPages]);

  const paginatedEvents = useMemo(() => {
    const safePage = Math.min(Math.max(1, currentPage), totalPages);
    const startIndex = (safePage - 1) * pageSize;
    return filteredEvents.slice(startIndex, startIndex + pageSize);
  }, [filteredEvents, currentPage, pageSize, totalPages]);

  const metrics = useMemo(() => calculateEventMetrics(events), [events]);

  return {
    events,
    setEvents,
    mergeEvent,
    loading,
    loadError,
    loadEvents,
    searchQuery,
    setSearchQuery,
    statusFilter,
    setStatusFilter,
    sortBy,
    setSortBy,
    filteredEvents,
    paginatedEvents,
    currentPage,
    setCurrentPage,
    pageSize,
    setPageSize,
    totalPages,
    serverPagination,
    metrics,
    showEventModal,
    setShowEventModal,
    editingEvent,
    setEditingEvent,
    submittingEditEvent,
    newEvent,
    setNewEvent,
    handleCreateEvent,
    handleUpdateEvent,
    handleCancelEvent,
    handleDeleteEvent,
    cancelModal,
    setCancelModal,
    handleCloseCancelModal,
    handleConfirmCancelEvent,
    confirmPublish,
    setConfirmPublish,
    publishPayMethod,
    setPublishPayMethod,
    openConfirmPublish,
    handleExecutePublish,
  };
}

export default useEvents;

