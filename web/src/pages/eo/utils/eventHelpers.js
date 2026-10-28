export const DEFAULT_NEW_EVENT = {
  name: '',
  description: '',
  venueAddress: '799 Nguyễn Văn Linh, Quận 7, TP.HCM',
  venueLat: 10.7328,
  venueLng: 106.7214,
  eventDate: '2026-11-20T08:00:00Z',
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
        (statusFilter === 'ACTIVE' && evt.status === 'ACTIVE') ||
        (statusFilter === 'DRAFT' && evt.status === 'DRAFT');
      return matchSearch && matchStatus;
    })
    .sort((a, b) => {
      if (sortBy === 'NAME') return (a.name || '').localeCompare(b.name || '');
      if (sortBy === 'WORKERS') return (b.totalRequiredWorkers || 0) - (a.totalRequiredWorkers || 0);
      return 0;
    });
}

export function calculateEventMetrics(events) {
  const totalEvents = events.length;
  const totalRequiredWorkers = events.reduce((sum, e) => sum + (e.totalRequiredWorkers || 0), 0);
  const totalConfirmedWorkers = events.reduce((sum, e) => sum + (e.totalConfirmedWorkers || 0), 0);
  const totalEstimatedCost = events.reduce((sum, e) => sum + (e.totalEstimatedCost || 0), 0);
  const fillRate = totalRequiredWorkers > 0 ? Math.round((totalConfirmedWorkers / totalRequiredWorkers) * 100) : 73;

  return {
    totalEvents,
    totalRequiredWorkers,
    totalConfirmedWorkers,
    totalEstimatedCost,
    fillRate,
  };
}

