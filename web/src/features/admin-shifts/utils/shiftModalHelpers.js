/**
 * Helper utils cho modal chi tiết ca làm việc Admin
 */

export const formatDateTime = (isoStr) => {
  if (!isoStr) return '—';
  try {
    return new Date(isoStr).toLocaleString('vi-VN', {
      hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric',
    });
  } catch {
    return '—';
  }
};

export const formatTimeOnly = (isoStr) => {
  if (!isoStr) return '—';
  try {
    return new Date(isoStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '—';
  }
};

export const formatDateOnly = (dateStr) => {
  if (!dateStr) return '—';
  try {
    return new Date(dateStr).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  } catch {
    return '—';
  }
};

export const parseTemplateChecklist = (rawChecklist) => {
  if (!rawChecklist) return [];
  try {
    const list = typeof rawChecklist === 'string' ? JSON.parse(rawChecklist) : rawChecklist;
    if (Array.isArray(list)) {
      return list.map((item, idx) => {
        if (typeof item === 'string') return { id: `chk_${idx + 1}`, title: item };
        return { id: item.id || `chk_${idx + 1}`, title: item.title || item.name || `Nhiệm vụ ${idx + 1}` };
      });
    }
  } catch (e) {
    console.warn('Lỗi phân tích checklist template:', e);
  }
  return [];
};
