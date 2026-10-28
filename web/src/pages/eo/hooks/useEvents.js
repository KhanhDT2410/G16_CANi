import { useState, useEffect, useMemo } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { getAddressFromCoordinates } from '../utils/geoUtils';
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
      const data = await eoService.getMyEvents();
      if (data && data.length > 0) {
        setEvents(data);
      } else if (import.meta.env.VITE_DEMO === 'true') {
        setEvents(DEMO_EVENTS);
      } else {
        setEvents([]);
      }
    } catch (e) {
      console.error(e);
      setEvents(import.meta.env.VITE_DEMO === 'true' ? DEMO_EVENTS : []);
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
          setNewEvent((prev) => ({ ...prev, venueAddress: addr, venueLat: lat, venueLng: lng }));
          showToast('Đã định vị tọa độ GPS & địa chỉ thành công!', 'success');
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
      await eoService.createEvent(newEvent);
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

  const handleCancelEvent = async (event) => {
    if (!window.confirm(`Bạn có chắc chắn muốn HỦY sự kiện "${event.name}"? Tất cả ca làm việc sẽ bị hủy.`)) {
      return;
    }
    try {
      await eoService.cancelEvent(event.eventId, 'Ban tổ chức hủy sự kiện');
      showToast('Đã hủy sự kiện thành công!', 'success');
      loadEvents();
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
    confirmPublish,
    setConfirmPublish,
    publishPayMethod,
    setPublishPayMethod,
    openConfirmPublish,
    handleExecutePublish,
  };
}
