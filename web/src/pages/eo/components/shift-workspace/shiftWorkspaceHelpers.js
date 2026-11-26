/**
 * Pure helpers for ShiftWorkspacePage (40+ worker scale, checklist parsing, status & sorting).
 */

export function parseShiftChecklistTasks(checklist) {
  if (!checklist) return [];
  try {
    const parsed = typeof checklist === 'string' ? JSON.parse(checklist) : checklist;
    if (Array.isArray(parsed)) {
      return parsed.map((t, idx) => {
        if (typeof t === 'string') {
          return {
            id: `task_${idx}`,
            label: t,
            isRequiredEvidence: false,
            instruction: null,
          };
        }
        const id = String(t.id ?? t.taskId ?? `chk_${idx + 1}`);
        const label = String(t.title ?? t.label ?? t.name ?? `Nhiệm vụ ${idx + 1}`);
        const isRequiredEvidence = Boolean(t.isRequiredEvidence ?? t.requiresEvidence ?? false);
        const instruction = t.instruction || null;
        return { id, label, isRequiredEvidence, instruction };
      });
    }
  } catch {
    // ignore invalid json
  }
  return [];
}

export function parseWorkerChecklistProgressMap(rawProgress) {
  if (!rawProgress) return {};
  try {
    const parsed = typeof rawProgress === 'string' ? JSON.parse(rawProgress) : rawProgress;
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
      return parsed;
    }
    if (Array.isArray(parsed)) {
      const map = {};
      parsed.forEach((item, idx) => {
        if (typeof item === 'string') {
          map[item] = { isCompleted: true };
        } else if (item && typeof item === 'object') {
          const key = String(item.id ?? item.taskId ?? item.title ?? `chk_${idx + 1}`);
          map[key] = {
            isCompleted: Boolean(item.isCompleted ?? item.completed ?? item.done ?? item.checked ?? false),
            evidenceImageUrl: item.evidenceImageUrl || item.proofUrl || null,
            evidenceSubmittedAt: item.evidenceSubmittedAt || null,
            completedAt: item.completedAt || null,
          };
        }
      });
      return map;
    }
  } catch {
    // ignore
  }
  return {};
}

export function canEoConfirmWorker(workerItem, shiftChecklistTasks = []) {
  const isCheckedIn = workerItem.assignmentStatus === 'CHECKED_IN' || Boolean(workerItem.checkinTime);
  if (!isCheckedIn) return false;
  if (workerItem.eoConfirmed) return false;

  if (!shiftChecklistTasks || shiftChecklistTasks.length === 0) {
    return true;
  }

  const progressMap = parseWorkerChecklistProgressMap(workerItem.checklistRawProgress);
  for (const task of shiftChecklistTasks) {
    const prog = progressMap[task.id] || progressMap[task.label];
    const isDone = Boolean(prog?.isCompleted || workerItem.isChecklistCompleted);
    if (!isDone) return false;
    if (task.isRequiredEvidence) {
      const hasProof = Boolean(prog?.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);
      if (!hasProof) return false;
    }
  }
  return true;
}

export function getWorkerChecklistSummary(workerItem, shiftChecklistTasks = []) {
  const progressMap = parseWorkerChecklistProgressMap(workerItem.checklistRawProgress);
  const total = shiftChecklistTasks.length;
  let done = 0;
  let missingEvidence = 0;
  const evidenceItems = [];

  shiftChecklistTasks.forEach((t) => {
    const prog = progressMap[t.id] || progressMap[t.label] || {};
    const isDone = Boolean(prog.isCompleted || workerItem.isChecklistCompleted);
    const hasUrl = Boolean(prog.evidenceImageUrl && String(prog.evidenceImageUrl).trim().length > 0);
    if (isDone) done += 1;
    if (t.isRequiredEvidence && !hasUrl) missingEvidence += 1;
    if (hasUrl) {
      evidenceItems.push({
        id: t.id,
        label: t.label,
        url: prog.evidenceImageUrl,
        submittedAt: prog.evidenceSubmittedAt || prog.completedAt || null,
      });
    }
  });

  if (workerItem.proofImageUrl) {
    evidenceItems.push({
      id: 'shift_proof_general',
      label: 'Ảnh nộp hoàn thành ca chung',
      url: workerItem.proofImageUrl,
      submittedAt: workerItem.checkoutTime || null,
    });
  }

  return { total, done, missingEvidence, evidenceItems };
}

export function getWorkerAttentionFlags(workerItem) {
  const isNoShow = workerItem.assignmentStatus === 'NO_SHOW';
  const isCheckedIn = workerItem.assignmentStatus === 'CHECKED_IN' || Boolean(workerItem.checkinTime);
  const isCompleted = workerItem.assignmentStatus === 'COMPLETED' || workerItem.checkoutTime != null;
  const isWaiting = !isNoShow && !isCheckedIn && !isCompleted;
  const isLate = Boolean(isCheckedIn && workerItem.isOnTime === false);
  const hasPaymentIssue =
    workerItem.paymentStatus === 'DISPUTED' ||
    workerItem.workerConfirmStatus === 'NOT_RECEIVED' ||
    workerItem.workerConfirmStatus === 'RECEIVED_PARTIAL';

  return {
    isNoShow,
    isCheckedIn,
    isCompleted,
    isWaiting,
    isLate,
    hasPaymentIssue,
    needsAttention: isWaiting || (isLate && !workerItem.eoConfirmed) || hasPaymentIssue,
  };
}

export function getWorkerPriorityRank(workerItem, shiftChecklistTasks = []) {
  const flags = getWorkerAttentionFlags(workerItem);
  if (flags.hasPaymentIssue) return 1;
  if (flags.isWaiting) return 2;
  if (flags.isLate && !workerItem.eoConfirmed) return 3;
  if (!flags.isNoShow && (flags.isCheckedIn || flags.isCompleted) && !workerItem.eoConfirmed) {
    return canEoConfirmWorker(workerItem, shiftChecklistTasks) ? 4 : 5;
  }
  if (!flags.isNoShow && workerItem.eoConfirmed && workerItem.paymentStatus !== 'CONFIRMED_FULL') {
    return workerItem.paymentStatus === 'NOT_DECLARED' || !workerItem.paymentStatus ? 6 : 7;
  }
  if (flags.isNoShow) return 8;
  return 9;
}

export function formatTimeShort(timeStr, fallback = '--:--') {
  if (!timeStr) return fallback;
  try {
    return new Date(timeStr).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return fallback;
  }
}

export function getSourceLabel(source) {
  switch (source) {
    case 'GROUP':
      return 'Nhóm';
    case 'MATCHING':
      return 'Hệ thống';
    case 'DIRECT':
      return 'Trực tiếp';
    case 'BULK':
      return 'Hàng loạt';
    default:
      return source || '';
  }
}
