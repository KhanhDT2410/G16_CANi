/**
 * Single Source of Truth cho ánh xạ Skill Rank (7 hạng DB -> 3 Mức phát triển UI)
 * và 3 Verification Tiers tiếng Việt trên Web EO CANi.
 */

export const TIER_TITLE_VI = {
  STARTER: 'Khởi đầu',
  BRONZE: 'Đồng',
  SILVER: 'Bạc',
  GOLD: 'Vàng',
  PLATINUM: 'Bạch kim',
  DIAMOND: 'Kim cương',
  MASTER: 'Tinh anh',
};

export const VERIFICATION_TIER_OPTIONS = [
  { value: 'SELF_DECLARED', label: 'Tự khai báo (Mặc định)' },
  { value: 'AI_DOCUMENT', label: 'Đã xác thực tài liệu' },
  { value: 'PLATFORM_PROVEN', label: 'Đã chứng minh qua ca làm' },
];

export const getDevelopmentLevelInfo = (skillTier) => {
  const code = (skillTier || 'STARTER').toUpperCase();
  const tierName = TIER_TITLE_VI[code] || 'Khởi đầu';
  if (code === 'DIAMOND' || code === 'MASTER') {
    return { code: 'PROVEN', label: 'Thành thạo', tierName, color: '#047857', bg: '#d1fae5' };
  }
  if (code === 'SILVER' || code === 'GOLD' || code === 'PLATINUM') {
    return { code: 'RELIABLE', label: 'Có kinh nghiệm', tierName, color: '#b45309', bg: '#fef3c7' };
  }
  return { code: 'NOVICE', label: 'Mới bắt đầu', tierName, color: '#0369a1', bg: '#e0f2fe' };
};

export const formatDevelopmentAndRank = (skillTier) => {
  const info = getDevelopmentLevelInfo(skillTier);
  return `${info.label} (${info.tierName})`;
};

export const getVerificationTierInfo = (verificationTier) => {
  switch ((verificationTier || 'SELF_DECLARED').toUpperCase()) {
    case 'PLATFORM_PROVEN':
      return { label: 'Đã chứng minh qua ca làm', color: '#047857', bg: '#ecfdf5', border: '#a7f3d0' };
    case 'AI_DOCUMENT':
      return { label: 'Đã xác thực tài liệu', color: '#0369a1', bg: '#e0f2fe', border: '#bae6fd' };
    case 'SELF_DECLARED':
    default:
      return { label: 'Tự khai báo', color: '#475569', bg: '#f1f5f9', border: '#cbd5e1' };
  }
};

export const normalizeVietnameseText = (str = '') => {
  return String(str)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
};

const JOB_ALIAS_HINTS = {
  'phuc vu ban': ['phuc vu', 'chay ban', 'waiter', 'waitress', 'tiec', 'nha hang'],
  'phu bep': ['bep', 'kitchen', 'che bien'],
  'pha che': ['bartender', 'barista', 'pha che', 'do uong'],
  'thu ngan': ['cashier', 'thu ngan', 'pos', 'thanh toan'],
  'le tan': ['le tan', 'check in', 'checkin', 'don khach', 'vip', 'mc'],
  'dieu phoi su kien': ['dieu phoi', 'coordinator', 'quan ly', 'ho tro'],
  'ky thuat am thanh': ['am thanh', 'anh sang', 'sound', 'lighting', 'av', 'ky thuat'],
  'bao ve': ['bao ve', 'an ninh', 'security'],
  'hau can': ['hau can', 'setup', 'logistics', 'kho', 'van chuyen'],
  'pg': ['pg', 'pb', 'model', 'sampling', 'booth'],
};

/**
 * Tìm các Skill chuẩn đang active phù hợp với vị trí công việc hoặc chuỗi tìm kiếm.
 */
export const suggestSkillsForQuery = (query, taxonomy = []) => {
  const list = Array.isArray(taxonomy) ? taxonomy.filter((s) => s && s.skillId && s.isActive !== false) : [];
  const qNorm = normalizeVietnameseText(query);
  if (!qNorm) return [];

  const matched = [];
  const seen = new Set();

  for (const sk of list) {
    const skNorm = normalizeVietnameseText(sk.name);
    if (!skNorm) continue;
    if (skNorm === qNorm || skNorm.includes(qNorm) || qNorm.includes(skNorm)) {
      if (!seen.has(sk.skillId)) {
        seen.add(sk.skillId);
        matched.push(sk);
      }
    }
  }

  for (const sk of list) {
    if (seen.has(sk.skillId)) continue;
    const skNorm = normalizeVietnameseText(sk.name);
    for (const [key, hints] of Object.entries(JOB_ALIAS_HINTS)) {
      if (skNorm.includes(key) && hints.some((h) => qNorm.includes(h) || h.includes(qNorm))) {
        seen.add(sk.skillId);
        matched.push(sk);
        break;
      }
    }
  }

  return matched;
};
