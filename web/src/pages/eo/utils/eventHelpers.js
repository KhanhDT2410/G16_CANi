export const DEFAULT_NEW_EVENT = {
  name: '',
  description: '',
  venueAddress: '',
  venueLat: 10.7328,
  venueLng: 106.7214,
  eventDate: '2026-11-20T08:00:00Z',
  startTime: '2026-11-20T08:00',
  endTime: '2026-11-20T22:00',
  imageUrl: '',
  mode: 'MATCHING',
};

export const DEMO_EVENTS = [
  {
    eventId: 'EVT-98721',
    name: 'Lễ Hội Âm Nhạc Mùa Hè 2026 - Summer Fest Live Stage',
    description: 'Đại nhạc hội ngoài trời quy tụ các nghệ sĩ trẻ hàng đầu, dự kiến đón tiếp 5.000 khán giả.',
    venueAddress: 'Trung tâm Hội chợ & Triển lãm Sài Gòn (SECC), 799 Nguyễn Văn Linh, Q.7, TP.HCM',
    venueLat: 10.7328,
    venueLng: 106.7214,
    eventDate: '2026-10-20T18:00:00Z',
    mode: 'MATCHING',
    status: 'ACTIVE',
    totalShifts: 3,
    totalRequiredWorkers: 15,
    totalConfirmedWorkers: 12,
    totalEstimatedCost: 4500000,
  },
];

export function filterAndSortEvents(events, searchQuery, statusFilter, sortBy) {
  return events
    .filter((evt) => {
      const matchSearch =
        !searchQuery ||
        evt.name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        evt.venueAddress?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        evt.eventId?.toLowerCase().includes(searchQuery.toLowerCase());
      const matchStatus =
        statusFilter === 'ALL' ||
        evt.status === statusFilter;
      return matchSearch && matchStatus;
    })
    .sort((a, b) => {
      if (sortBy === 'NAME') return (a.name || '').localeCompare(b.name || '');
      if (sortBy === 'WORKERS') return (b.totalRequiredWorkers || 0) - (a.totalRequiredWorkers || 0);
      if (sortBy === 'OLDEST') return new Date(a.createdAt || a.eventDate || 0) - new Date(b.createdAt || b.eventDate || 0);
      return new Date(b.createdAt || b.eventDate || 0) - new Date(a.createdAt || a.eventDate || 0);
    });
}

export function calculateEventMetrics(events) {
  // Loại trừ các sự kiện đã hủy khỏi số liệu vận hành và tuyển dụng
  const nonCancelled = events.filter((e) => e.status !== 'CANCELLED');
  const activeEvents = events.filter((e) => e.status === 'ACTIVE' || e.status === 'APPROVED');

  const totalEvents = nonCancelled.length;
  const activeEventsCount = activeEvents.length;
  const totalRequiredWorkers = nonCancelled.reduce((sum, e) => sum + (e.totalRequiredWorkers || 0), 0);
  const totalConfirmedWorkers = nonCancelled.reduce((sum, e) => sum + (e.totalConfirmedWorkers || 0), 0);
  const totalEstimatedCost = nonCancelled.reduce((sum, e) => sum + (e.totalEstimatedCost || 0), 0);
  const fillRate = totalRequiredWorkers > 0 ? Math.round((totalConfirmedWorkers / totalRequiredWorkers) * 100) : 0;

  return {
    totalEvents,
    activeEventsCount,
    totalRequiredWorkers,
    totalConfirmedWorkers,
    totalEstimatedCost,
    fillRate,
  };
}

