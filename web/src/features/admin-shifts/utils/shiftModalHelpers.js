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
        if (typeof item === 'string') {
          return { id: `chk_${idx + 1}`, title: item, isRequiredEvidence: false };
        }
        return {
          id: item.id || `chk_${idx + 1}`,
          title: item.title || item.name || `Nhiệm vụ ${idx + 1}`,
          isRequiredEvidence: Boolean(item.isRequiredEvidence || item.requireProof || item.requireEvidence),
        };
      });
    }
  } catch (e) {
    console.warn('Lỗi phân tích checklist template:', e);
  }
  return [];
};

export const parseWorkerChecklistProgress = (templateChecklist = [], rawProgress) => {
  if (!templateChecklist || templateChecklist.length === 0) {
    return { total: 0, done: 0, percent: 0, items: [] };
  }
  const total = templateChecklist.length;
  let done = 0;
  const progressMap = {};

  try {
    if (rawProgress) {
      const parsed = typeof rawProgress === 'string' ? JSON.parse(rawProgress) : rawProgress;
      if (Array.isArray(parsed)) {
        parsed.forEach((p) => {
          if (p && typeof p === 'object') {
            progressMap[p.id] = {
              completed: p.completed !== false && p.isCompleted !== false,
              completedAt: p.completedAt,
              evidenceImageUrl: p.evidenceImageUrl || p.proofUrl,
              evidenceSubmittedAt: p.evidenceSubmittedAt,
            };
          } else if (typeof p === 'string') {
            progressMap[p] = { completed: true };
          }
        });
      } else if (typeof parsed === 'object') {
        Object.keys(parsed).forEach((k) => {
          const v = parsed[k];
          progressMap[k] = {
            completed: v === true || v?.completed === true || v?.isCompleted === true,
            completedAt: v?.completedAt,
            evidenceImageUrl: v?.evidenceImageUrl || v?.proofUrl,
            evidenceSubmittedAt: v?.evidenceSubmittedAt,
          };
        });
      }
    }
  } catch (e) {
    console.warn('Lỗi phân tích checklist progress:', e);
  }

  const items = templateChecklist.map((tpl) => {
    const prog = progressMap[tpl.id];
    const isDone = Boolean(prog && prog.completed);
    if (isDone) done++;
    return {
      id: tpl.id,
      title: tpl.title,
      completed: isDone,
      completedAt: prog?.completedAt || null,
      evidenceImageUrl: prog?.evidenceImageUrl || null,
      evidenceSubmittedAt: prog?.evidenceSubmittedAt || null,
      isRequiredEvidence: Boolean(tpl.isRequiredEvidence),
    };
  });

  const percent = total > 0 ? Math.round((done / total) * 100) : 0;
  return { total, done, percent, items };
};
