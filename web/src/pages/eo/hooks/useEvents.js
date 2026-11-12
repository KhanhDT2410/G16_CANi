import { useState, useEffect, useMemo } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { getAddressFromCoordinates, getCoordinatesFromAddress } from '../utils/geoUtils';
import {
  DEFAULT_NEW_EVENT,
  DEMO_EVENTS,
  filterAndSortEvents,
  calculateEventMetrics,
} from '../utils/eventHelpers';

export function useEvents() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('NEWEST');

  const [showEventModal, setShowEventModal] = useState(false);
  const [editingEvent, setEditingEvent] = useState(null);
  const [submittingEditEvent, setSubmittingEditEvent] = useState(false);

  const [confirmPublish, setConfirmPublish] = useState({ isOpen: false, event: null });
  const [publishPayMethod, setPublishPayMethod] = useState('VIETQR');
  const [locating, setLocating] = useState(false);

  const [newEvent, setNewEvent] = useState(DEFAULT_NEW_EVENT);

  const loadEvents = async () => {
    setLoading(true);
    try {
      const deletedIds = JSON.parse(localStorage.getItem('cani_deleted_events') || '[]');
      const data = await eoService.getMyEvents();
      const raw = data && data.length > 0 ? data : (import.meta.env.VITE_DEMO === 'true' ? DEMO_EVENTS : []);
      const visible = raw.filter((e) => !deletedIds.includes(e.eventId) && !e.deletedAt);
      setEvents(visible);
      return visible;
    } catch (e) {
      console.error(e);
      const fallback = import.meta.env.VITE_DEMO === 'true' ? DEMO_EVENTS : [];
      setEvents(fallback);
      return fallback;
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadEvents();
  }, []);

  const handleGetCurrentLocation = () => {
    if (!navigator.geolocation) {
      showToast('Trình duyệt của bạn không hỗ trợ định vị GPS.', 'error');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = parseFloat(pos.coords.latitude.toFixed(6));
        const lng = parseFloat(pos.coords.longitude.toFixed(6));
        try {
          const addr = await getAddressFromCoordinates(lat, lng);
          setNewEvent((prev) => ({
            ...prev,
            venueAddress: addr || prev.venueAddress,
            venueLat: lat,
            venueLng: lng,
          }));
          if (addr) {
            showToast('Đã xác định địa chỉ tổ chức thành công!', 'success');
          } else {
            showToast('Đã ghim vị trí trên bản đồ. Vui lòng nhập rõ địa chỉ số nhà, tên đường cụ thể.', 'info');
          }
        } finally {
          setLocating(false);
        }
      },
      (err) => {
        setLocating(false);
        showToast('Không thể lấy vị trí: ' + (err.message || 'Quyền GPS bị từ chối'), 'error');
      },
      { timeout: 10000, enableHighAccuracy: true }
    );
  };

  const handleCreateEvent = async (e) => {
    e.preventDefault();
    try {
      let eventPayload = { ...newEvent };
      if (eventPayload.venueAddress?.trim()) {
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
      eventPayload.venueLat = eventPayload.venueLat || 10.7328;
      eventPayload.venueLng = eventPayload.venueLng || 106.7214;

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
    if (!window.confirm(`Bạn có chắc chắn muốn XÓA sự kiện "${event.name}" khỏi danh sách? Thao tác này sẽ dọn dẹp sự kiện khỏi bảng.`)) {
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
      showToast('Đã xóa sự kiện khỏi danh sách thành công!', 'success');
    } catch (err) {
      showToast('Lỗi xóa sự kiện: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  const handleCancelEvent = async (event) => {
    if (!window.confirm(`Bạn có chắc chắn muốn HỦY sự kiện "${event.name}"? Tất cả ca làm việc sẽ bị hủy.`)) {
      return;
    }
    try {
      await eoService.cancelEvent(event.eventId, 'Ban tổ chức hủy sự kiện');
      showToast('Đã hủy sự kiện thành công!', 'success');

      // Hỏi người dùng có muốn gỡ/xóa khỏi bảng hiển thị luôn không
      const wantDelete = window.confirm(`Sự kiện "${event.name}" đã được hủy. Bạn có muốn XÓA KHỎI DANH SÁCH hiển thị ngay bây giờ không?`);
      if (wantDelete) {
        await handleDeleteEvent(event);
      } else {
        loadEvents();
      }
    } catch (err) {
      showToast('Lỗi hủy sự kiện: ' + (err.response?.data?.message || err.message), 'error');
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

  const metrics = useMemo(() => calculateEventMetrics(events), [events]);

  return {
    events,
    setEvents,
    loading,
    loadEvents,
    searchQuery,
    setSearchQuery,
    statusFilter,
    setStatusFilter,
    sortBy,
    setSortBy,
    filteredEvents,
    metrics,
    showEventModal,
    setShowEventModal,
    editingEvent,
    setEditingEvent,
    submittingEditEvent,
    newEvent,
    setNewEvent,
    locating,
    handleGetCurrentLocation,
    handleCreateEvent,
    handleUpdateEvent,
    handleCancelEvent,
    handleDeleteEvent,
    confirmPublish,
    setConfirmPublish,
    publishPayMethod,
    setPublishPayMethod,
    openConfirmPublish,
    handleExecutePublish,
  };
}
