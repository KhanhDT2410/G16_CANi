/**
 * Shared validation & Region × JobTier wage benchmark helpers (Bảng A - NĐ 293/2025/NĐ-CP).
 * Backend (/api/v1/wage-recommendations) là Single Source of Truth; helper client-side chỉ dùng
 * để kiểm tra sàn pháp lý và hiển thị tạm khi offline/đang chờ API.
 */

export const ROLES = [
  'Lễ tân / Check-in',
  'Phục vụ bàn',
  'Pha chế',
  'Thu ngân',
  'Điều phối sự kiện',
  'Kỹ thuật âm thanh - ánh sáng',
  'Bảo vệ / An ninh',
  'Hậu cần / Setup sự kiện',
  'PG / PB',
];

export const JOB_TIER_BENCHMARKS = [
  {
    code: 'TIER_1_GENERAL',
    label: 'Phổ thông',
    description: 'Phát tờ rơi, hậu cần nhẹ, trực khu vực',
    minMult: 1.0,
    recMult: 1.15,
    maxMult: 1.3,
  },
  {
    code: 'TIER_2_SERVICE',
    label: 'Giao tiếp & Dịch vụ',
    description: 'Lễ tân, check-in, hướng dẫn khách, phục vụ bàn, thu ngân',
    minMult: 1.3,
    recMult: 1.55,
    maxMult: 1.8,
  },
  {
    code: 'TIER_3_PHYSICAL',
    label: 'Thể lực / An ninh',
    description: 'Bốc xếp, setup sân khấu, bảo vệ, an ninh',
    minMult: 1.5,
    recMult: 1.85,
    maxMult: 2.2,
  },
  {
    code: 'TIER_4_PROMOTION',
    label: 'PG / PB bán hàng & Pha chế',
    description: 'PG/PB bán hàng, sampling, roadshow, pha chế',
    minMult: 2.0,
    recMult: 2.8,
    maxMult: 4.0,
  },
  {
    code: 'TIER_5_TECHNICAL',
    label: 'Điều phối & Kỹ thuật',
    description: 'Điều phối sự kiện, kỹ thuật âm thanh - ánh sáng',
    minMult: 2.5,
    recMult: 3.75,
    maxMult: 5.0,
  },
  {
    code: 'TIER_6_SPECIALIST',
    label: 'Chuyên nghiệp / Ngoại ngữ',
    description: 'PG/PB chuyên nghiệp, ngoại ngữ, MC song ngữ',
    minMult: 4.5,
    recMult: 6.5,
    maxMult: 10.0,
  },
];

const TIER_MAP = Object.fromEntries(JOB_TIER_BENCHMARKS.map((t) => [t.code, t]));

function normalizeAscii(str) {
  return String(str || '')
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/\s+/g, ' ');
}

export function classifyJobTier(jobType, explicitTier = null) {
  if (explicitTier && TIER_MAP[explicitTier]) return explicitTier;
  const norm = normalizeAscii(jobType);
  if (!norm) return 'TIER_1_GENERAL';

  const exactMap = {
    'phat to roi': 'TIER_1_GENERAL',
    'hau can / setup su kien': 'TIER_3_PHYSICAL',
    'phuc vu ban': 'TIER_2_SERVICE',
    'phu bep': 'TIER_2_SERVICE',
    'thu ngan': 'TIER_2_SERVICE',
    'le tan / check-in': 'TIER_2_SERVICE',
    'bao ve / an ninh': 'TIER_3_PHYSICAL',
    'pha che': 'TIER_4_PROMOTION',
    'pha che (bartender/barista)': 'TIER_4_PROMOTION',
    'pg / pb': 'TIER_4_PROMOTION',
    'dieu phoi su kien': 'TIER_5_TECHNICAL',
    'ky thuat am thanh - anh sang': 'TIER_5_TECHNICAL',
  };
  if (exactMap[norm]) return exactMap[norm];

  const padded = ` ${norm.replace(/[^a-z0-9]+/g, ' ')} `;
  const hasAny = (kws) =>
    kws.some((kw) =>
      kw.length <= 3 && !kw.includes(' ') ? padded.includes(` ${kw} `) : norm.includes(kw)
    );

  if (hasAny(['mc', 'song ngu', 'ngoai ngu', 'phien dich', 'bilingual', 'english', 'chuyen nghiep'])) {
    return 'TIER_6_SPECIALIST';
  }
  if (hasAny(['dieu phoi', 'coordinator', 'ky thuat', 'am thanh', 'anh sang', 'led', 'av', 'quay phim', 'chup anh'])) {
    return 'TIER_5_TECHNICAL';
  }
  if (hasAny(['pg', 'pb', 'pha che', 'bartender', 'barista', 'sampling', 'roadshow', 'ban hang', 'sales'])) {
    return 'TIER_4_PROMOTION';
  }
  if (hasAny(['boc xep', 'setup', 'san khau', 'bao ve', 'an ninh', 'security', 'van chuyen', 'khuon vac'])) {
    return 'TIER_3_PHYSICAL';
  }
  if (hasAny(['le tan', 'check-in', 'checkin', 'phuc vu', 'thu ngan', 'cashier', 'phu bep', 'huong dan', 'chay ban'])) {
    return 'TIER_2_SERVICE';
  }
  return 'TIER_1_GENERAL';
}

function buildRoleDefaultFromTier(tierCode, note) {
  const tier = TIER_MAP[tierCode] || TIER_MAP.TIER_1_GENERAL;
  const floor = 25500;
  const round1000 = (v) => Math.round(v / 1000) * 1000;
  const rec = round1000(floor * tier.recMult);
  return {
    min: round1000(floor * tier.minMult),
    max: round1000(floor * tier.maxMult),
    defaultWage: rec,
    recommended: rec,
    note,
  };
}

export const WAGE_RECOMMENDATIONS_MAP = {
  'Lễ tân / Check-in': buildRoleDefaultFromTier('TIER_2_SERVICE', 'Giao tiếp tốt, ngoại hình sáng'),
  'Phục vụ bàn': buildRoleDefaultFromTier('TIER_2_SERVICE', 'Nhanh nhẹn, cẩn thận'),
  'Pha chế': buildRoleDefaultFromTier('TIER_4_PROMOTION', 'Có tay nghề pha chế'),
  'Pha chế (Bartender/Barista)': buildRoleDefaultFromTier('TIER_4_PROMOTION', 'Có tay nghề pha chế'),
  'Thu ngân': buildRoleDefaultFromTier('TIER_2_SERVICE', 'Trung thực, tính toán chuẩn xác'),
  'Điều phối sự kiện': buildRoleDefaultFromTier('TIER_5_TECHNICAL', 'Kinh nghiệm quản lý nhóm'),
  'Kỹ thuật âm thanh - ánh sáng': buildRoleDefaultFromTier('TIER_5_TECHNICAL', 'Chuyên môn thiết bị sự kiện'),
  'Bảo vệ / An ninh': buildRoleDefaultFromTier('TIER_3_PHYSICAL', 'Thể lực tốt, giữ trật tự'),
  'Hậu cần / Setup sự kiện': buildRoleDefaultFromTier('TIER_3_PHYSICAL', 'Sức khỏe tốt, khuân vác, setup'),
  'PG / PB': buildRoleDefaultFromTier('TIER_4_PROMOTION', 'Ngoại hình, giới thiệu sản phẩm'),
};

export const VALID_EVENT_ROLE_ACRONYMS = new Set([
  'mc', 'pg', 'pb', 'dj', 'pr', 'it', 'av', 'led', 'vip',
  'f&b', 'fnb', 'qc', 'qa', 'cskh', 'ctv', 'hr', 'pa', 'pm',
]);

// Sàn lương tối thiểu giờ 4 vùng theo Nghị định 293/2025/NĐ-CP (áp dụng từ 01/01/2026)
export const STATUTORY_HOURLY_FLOORS = {
  REGION_1: { wage: 25500, regionLabel: 'Vùng I', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  REGION_2: { wage: 22700, regionLabel: 'Vùng II', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  REGION_3: { wage: 20000, regionLabel: 'Vùng III', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  REGION_4: { wage: 17800, regionLabel: 'Vùng IV', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  // Legacy keys cho tương thích ngược
  HANOI: { wage: 25500, regionLabel: 'Vùng I (Hà Nội)', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  TP_HCM: { wage: 25500, regionLabel: 'Vùng I (TP.HCM)', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  DANANG: { wage: 22700, regionLabel: 'Vùng II (Đà Nẵng)', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
};

const VOWELS_REGEX = /[aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]/i;
const VOWELLESS_CONSONANT_RUN_REGEX = /[^aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ\s\d,.\-_/()&+]{5,}/i;

export function isGibberishOrSpam(text, { allowShortEventAcronyms = false } = {}) {
  if (!text) return false;
  const str = String(text).trim().toLowerCase();
  if (str.length < 2) return false;

  if (allowShortEventAcronyms && VALID_EVENT_ROLE_ACRONYMS.has(str)) {
    return false;
  }

  if (/(.)\1{3,}/.test(str)) return true;
  if (/(.{2})\1{2,}/.test(str)) return true;

  const words = str.split(/[\s,./()&+-]+/).filter(Boolean);
  let prevWord = null;
  let repeatCount = 0;
  for (const w of words) {
    if (w === prevWord) {
      repeatCount += 1;
      if (repeatCount >= 3) return true;
    } else {
      prevWord = w;
      repeatCount = 1;
    }
  }

  if (/(asdasd|sdasd|ádasd|sdada|adasd|sdfsdf|dfghj|fghjk|ghjkl|qwerty|zxcvb|sdfh|asdf)/i.test(str)) {
    return true;
  }

  if (words.length >= 4) {
    const counts = {};
    for (const w of words) counts[w] = (counts[w] || 0) + 1;
    if (Math.max(...Object.values(counts)) / words.length >= 0.45) return true;
  }

  const cleanLetters = str.replace(/[^a-zàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]/gi, '');
  if (cleanLetters.length >= 10) {
    const uniqueChars = new Set(cleanLetters.split('')).size;
    if (cleanLetters.length <= 30) {
      if (uniqueChars <= 4 || uniqueChars / cleanLetters.length < 0.3) return true;
    } else if (uniqueChars <= 5) {
      return true;
    }
  }

  const minVowellessLen = allowShortEventAcronyms ? 3 : 4;
  for (const w of words) {
    if (/^\d+$/.test(w)) continue;
    if (allowShortEventAcronyms && VALID_EVENT_ROLE_ACRONYMS.has(w)) continue;
    const cleanWord = w.replace(/[^\p{L}]/gu, '');
    if (cleanWord.length >= minVowellessLen && !VOWELS_REGEX.test(cleanWord)) {
      return true;
    }
  }

  if (VOWELLESS_CONSONANT_RUN_REGEX.test(str)) return true;

  const nonSpace = str.replace(/\s+/g, '');
  if (nonSpace.length >= 5 && !VOWELS_REGEX.test(nonSpace) && !/^\d+$/.test(nonSpace)) {
    if (!(allowShortEventAcronyms && VALID_EVENT_ROLE_ACRONYMS.has(nonSpace))) {
      return true;
    }
  }

  return false;
}

export function validateJobType(rawJobType) {
  const trimmed = String(rawJobType || '').trim();
  if (!trimmed) return 'Vui lòng nhập hoặc chọn tên vị trí / vai trò cần tuyển.';
  if (trimmed.length > 80) return 'Tên vị trí công việc không được vượt quá 80 ký tự.';
  if (!/^[\p{L}\p{N}\s,./()&+-]+$/u.test(trimmed)) {
    return 'Tên vị trí chỉ được chứa chữ cái, số, khoảng trắng và các dấu , . - / ( ) & +.';
  }
  const lower = trimmed.toLowerCase();
  if (trimmed.length < 3 && !VALID_EVENT_ROLE_ACRONYMS.has(lower)) {
    return 'Tên vị trí quá ngắn (tối thiểu 3 ký tự, trừ viết tắt hợp lệ như MC, PG, PB, DJ).';
  }
  const tokens = trimmed.split(/[\s,./()&+-]+/).filter(Boolean);
  const hasLetterWord = tokens.some((tok) => {
    const letters = tok.replace(/[^\p{L}]/gu, '');
    return letters.length >= 2 || VALID_EVENT_ROLE_ACRONYMS.has(tok.toLowerCase());
  });
  if (!hasLetterWord) {
    return 'Tên vị trí phải chứa ít nhất một từ khóa mô tả công việc hợp lệ (không thể chỉ nhập số hoặc ký tự đơn).';
  }
  if (isGibberishOrSpam(trimmed, { allowShortEventAcronyms: true })) {
    return 'Tên vị trí chứa chuỗi ký tự vô nghĩa. Vui lòng chọn gợi ý có sẵn hoặc nhập chức danh thực tế (VD: MC song ngữ, Barista, Kỹ thuật LED).';
  }
  return null;
}

/**
 * Phân giải địa bàn sự kiện toàn quốc về 4 Vùng lương tối thiểu theo NĐ 293/2025/NĐ-CP.
 */
export function detectRegionFromAddress(address = '', lat = null, lng = null) {
  const nLat = Number(lat);
  const nLng = Number(lng);
  if (Number.isFinite(nLat) && Number.isFinite(nLng) && (nLat !== 0 || nLng !== 0)) {
    if (nLat >= 20.5 && nLat <= 21.45 && nLng >= 105.2 && nLng <= 106.1) {
      return { regionCode: 'REGION_1', city: 'HANOI', regionLabel: 'Vùng I (Hà Nội)', wage: 25500 };
    }
    if (nLat >= 15.8 && nLat <= 16.35 && nLng >= 107.8 && nLng <= 108.4) {
      return { regionCode: 'REGION_2', city: 'DANANG', regionLabel: 'Vùng II (Đà Nẵng)', wage: 22700 };
    }
    if (nLat >= 10.3 && nLat <= 11.2 && nLng >= 106.3 && nLng <= 107.15) {
      return { regionCode: 'REGION_1', city: 'TP_HCM', regionLabel: 'Vùng I (TP. Hồ Chí Minh)', wage: 25500 };
    }
  }

  const norm = normalizeAscii(address);
  if (norm) {
    const padded = ` ${norm.replace(/[^a-z0-9]+/g, ' ')} `;
    const matchAny = (kws) =>
      kws.some((kw) => padded.includes(` ${kw.replace(/[^a-z0-9]+/g, ' ').trim()} `));

    if (matchAny(['da nang', 'danang', 'hai chau', 'son tra', 'thanh khe', 'ngu hanh son', 'lien chieu', 'cam le', 'hoa vang'])) {
      return { regionCode: 'REGION_2', city: 'DANANG', regionLabel: 'Vùng II (Đà Nẵng)', wage: 22700 };
    }
    if (matchAny(['ha noi', 'hanoi', 'ba dinh', 'cau giay', 'hoan kiem', 'dong da', 'tay ho', 'hai ba trung', 'nam tu liem', 'bac tu liem', 'thanh xuan', 'ha dong', 'long bien', 'hoang mai', 'hoa lac', 'thach that', 'gia lam', 'dong anh', 'soc son'])) {
      return { regionCode: 'REGION_1', city: 'HANOI', regionLabel: 'Vùng I (Hà Nội)', wage: 25500 };
    }
    if (matchAny(['ho chi minh', 'tp hcm', 'tphcm', 'sai gon', 'saigon', 'secc', 'thu duc', 'binh thanh', 'phu nhuan', 'tan binh', 'go vap', 'tan phu', 'binh tan', 'nha be', 'binh chanh'])) {
      return { regionCode: 'REGION_1', city: 'TP_HCM', regionLabel: 'Vùng I (TP. Hồ Chí Minh)', wage: 25500 };
    }
    if (matchAny(['ha giang', 'cao bang', 'bac kan', 'dien bien', 'lai chau', 'son la', 'dak nong', 'gia nghia', 'hau giang', 'vi thanh'])) {
      return { regionCode: 'REGION_4', city: null, regionLabel: 'Vùng IV', wage: 17800 };
    }
    if (matchAny(['ha nam', 'phu ly', 'ninh thuan', 'phan rang', 'binh thuan', 'phan thiet', 'quang ngai', 'phu yen', 'tuy hoa', 'quang binh', 'dong hoi', 'quang tri', 'dong ha', 'ha tinh', 'lao cai', 'sapa', 'sa pa', 'hoa binh', 'tuyen quang', 'lang son', 'yen bai', 'gia lai', 'pleiku', 'kon tum', 'binh phuoc', 'dong xoai', 'ben tre', 'tra vinh', 'vinh long', 'dong thap', 'cao lanh', 'sa dec', 'bac lieu', 'ca mau', 'soc trang'])) {
      return { regionCode: 'REGION_3', city: null, regionLabel: 'Vùng III', wage: 20000 };
    }
    if (matchAny(['can tho', 'thai nguyen', 'bac ninh', 'bac giang', 'hai duong', 'hung yen', 'vinh phuc', 'viet tri', 'phu tho', 'ninh binh', 'thanh hoa', 'vinh', 'nghe an', 'hue', 'thua thien hue', 'nha trang', 'khanh hoa', 'da lat', 'lam dong', 'quy nhon', 'binh dinh', 'buon ma thuot', 'dak lak', 'long an', 'tan an', 'tien giang', 'my tho', 'tay ninh', 'long xuyen', 'an giang', 'kien giang', 'phu quoc', 'rach gia', 'thai binh', 'nam dinh', 'quang nam', 'tam ky', 'hoi an'])) {
      return { regionCode: 'REGION_2', city: 'DANANG', regionLabel: 'Vùng II', wage: 22700 };
    }
    if (matchAny(['hai phong', 'binh duong', 'thu dau mot', 'di an', 'thuan an', 'dong nai', 'bien hoa', 'ba ria', 'vung tau', 'quang ninh', 'ha long'])) {
      return { regionCode: 'REGION_1', city: 'TP_HCM', regionLabel: 'Vùng I', wage: 25500 };
    }
  }

  return { regionCode: 'REGION_1', city: 'TP_HCM', regionLabel: 'Vùng I (TP. Hồ Chí Minh)', wage: 25500 };
}

export function detectCityFromAddress(address = '', lat = null, lng = null) {
  return detectRegionFromAddress(address, lat, lng).city || 'TP_HCM';
}

export function resolveWageRegionInfo(address = '', lat = null, lng = null, hasPickedLocation = false) {
  const hasAddress = Boolean(String(address || '').trim());
  const isDefaultSeccPin =
    lat != null &&
    lng != null &&
    Math.abs(Number(lat) - 10.7328) < 0.0005 &&
    Math.abs(Number(lng) - 106.7214) < 0.0005;

  if (!hasAddress && (!hasPickedLocation || isDefaultSeccPin)) {
    return {
      isResolved: false,
      regionCode: null,
      city: null,
      regionLabel: 'Chưa xác định khu vực',
      wage: null,
      decree: null,
    };
  }

  const detected = detectRegionFromAddress(address, lat, lng);
  const floorInfo = STATUTORY_HOURLY_FLOORS[detected.regionCode] || STATUTORY_HOURLY_FLOORS.REGION_1;
  return {
    isResolved: true,
    regionCode: detected.regionCode,
    city: detected.city,
    regionLabel: detected.regionLabel || floorInfo.regionLabel,
    wage: detected.wage || floorInfo.wage,
    decree: floorInfo.decree,
  };
}

function roundToThousand(val) {
  return Math.round((Number(val) || 0) / 1000) * 1000;
}

function computeClientTimeAndLeadAdjustments(startTime) {
  const d = startTime ? new Date(startTime) : null;
  if (!d || isNaN(d.getTime())) {
    return {
      timeMultiplier: 1,
      timeFactorText: 'Khung giờ ban ngày tiêu chuẩn: giữ nguyên hệ số 100%.',
      leadMultiplier: 1,
      leadFactorText: null,
    };
  }
  const hour = d.getHours();
  const day = d.getDay();
  const isWeekend = day === 0 || day === 6;

  let timeMultiplier = 1;
  let timeFactorText = 'Khung giờ ban ngày ngày thường (06h–18h): áp dụng hệ số chuẩn 100%.';
  if (isWeekend && (hour < 6 || hour >= 18)) {
    timeMultiplier = hour >= 22 || hour < 6 ? 1.35 : 1.15;
    timeFactorText = 'Khung giờ tối/đêm cuối tuần: điều chỉnh theo nhu cầu cao điểm.';
  } else if (isWeekend) {
    timeMultiplier = 1.1;
    timeFactorText = 'Khung giờ ban ngày cuối tuần: điều chỉnh +10%.';
  } else if (hour >= 22 || hour < 6) {
    timeMultiplier = 1.3;
    timeFactorText = 'Khung giờ ban đêm (22h–06h): cộng tối thiểu +30% (Điều 98 BLLĐ 2019).';
  } else if (hour >= 18) {
    timeMultiplier = 1.1;
    timeFactorText = 'Khung giờ buổi tối (18h–22h): điều chỉnh +10%.';
  }

  const hoursUntilStart = (d.getTime() - Date.now()) / (1000 * 60 * 60);
  let leadMultiplier = 1;
  let leadFactorText = null;
  if (hoursUntilStart < 24) {
    leadMultiplier = 1.2;
    leadFactorText = 'Độ gấp tuyển dụng [Khẩn cấp < 24h]: cộng +20%.';
  } else if (hoursUntilStart < 72) {
    leadMultiplier = 1.1;
    leadFactorText = 'Độ gấp tuyển dụng [Cận ngày 24h–72h]: cộng +10%.';
  }

  return { timeMultiplier, timeFactorText, leadMultiplier, leadFactorText };
}

/**
 * Tổng hợp khung thù lao tham chiếu Region × JobTier (ưu tiên tuyệt đối kết quả Backend API).
 */
export function buildEvidenceWageBenchmark({
  jobType,
  jobTier = null,
  startTime,
  venueAddress,
  venueLat = null,
  venueLng = null,
  apiBenchmark,
}) {
  const detectedRegion = detectRegionFromAddress(venueAddress, venueLat, venueLng);
  const decree = STATUTORY_HOURLY_FLOORS[detectedRegion.regionCode]?.decree || STATUTORY_HOURLY_FLOORS.REGION_1.decree;
  const trimmedRole = String(jobType || '').trim();

  if (apiBenchmark && Number(apiBenchmark.suggestedWage) > 0) {
    const sampleCount = Number(apiBenchmark.platformSampleCount || 0);
    const catalogMatched = Boolean(apiBenchmark.catalogMatched);
    return {
      city: apiBenchmark.city || detectedRegion.city,
      regionCode: apiBenchmark.regionCode || detectedRegion.regionCode,
      regionName: apiBenchmark.regionName || detectedRegion.regionLabel,
      jobTier: apiBenchmark.jobTier || classifyJobTier(trimmedRole, jobTier),
      jobTierLabel: apiBenchmark.jobTierLabel || TIER_MAP[classifyJobTier(trimmedRole, jobTier)]?.label,
      minWage: Number(apiBenchmark.minWage || detectedRegion.wage),
      suggestedWage: Number(apiBenchmark.suggestedWage),
      maxWage: Number(apiBenchmark.maxWage || Number(apiBenchmark.suggestedWage) + 15000),
      legalFloorWage: Number(apiBenchmark.legalFloorWage || detectedRegion.wage),
      legalRegionLabel: apiBenchmark.legalRegionLabel || detectedRegion.regionLabel,
      legalReference: apiBenchmark.legalReference || decree,
      nightHourRatio: Number(apiBenchmark.nightHourRatio || 0),
      platformSampleCount: sampleCount,
      platformAvgWage: apiBenchmark.platformAvgWage ? Number(apiBenchmark.platformAvgWage) : null,
      catalogMatched,
      sourceLabel: apiBenchmark.jobTierLabel
        ? `Bảng A (${apiBenchmark.regionName || detectedRegion.regionLabel} · ${apiBenchmark.jobTierLabel})`
        : 'Biểu Ma trận Region × JobTier',
      evidenceFactors: Array.isArray(apiBenchmark.evidenceFactors) ? apiBenchmark.evidenceFactors : [],
      explanation: apiBenchmark.explanation || '',
    };
  }

  // Fallback tạm khi offline hoặc đang chờ phản hồi API
  const resolvedTierCode = classifyJobTier(trimmedRole, jobTier);
  const tierMeta = TIER_MAP[resolvedTierCode] || TIER_MAP.TIER_1_GENERAL;
  const { timeMultiplier, timeFactorText, leadMultiplier, leadFactorText } =
    computeClientTimeAndLeadAdjustments(startTime);

  const combined = timeMultiplier * leadMultiplier;
  const floorWage = detectedRegion.wage;
  const suggestedWage = Math.max(floorWage, roundToThousand(floorWage * tierMeta.recMult * combined));
  const minWage = Math.max(floorWage, roundToThousand(floorWage * tierMeta.minMult * combined));
  const maxWage = Math.max(suggestedWage + 5000, roundToThousand(floorWage * tierMeta.maxMult * combined));

  const evidenceFactors = [
    `Sàn pháp lý ${detectedRegion.regionLabel}: ${floorWage.toLocaleString('vi-VN')} đ/giờ theo ${decree}.`,
    `Nhóm công việc [${tierMeta.label}]: hệ số Bảng A ${tierMeta.minMult.toFixed(2)}x – ${tierMeta.maxMult.toFixed(2)}x.`,
    timeFactorText,
  ];
  if (leadFactorText) evidenceFactors.push(leadFactorText);

  return {
    city: detectedRegion.city,
    regionCode: detectedRegion.regionCode,
    regionName: detectedRegion.regionLabel,
    jobTier: resolvedTierCode,
    jobTierLabel: tierMeta.label,
    minWage,
    suggestedWage,
    maxWage,
    legalFloorWage: floorWage,
    legalRegionLabel: detectedRegion.regionLabel,
    legalReference: decree,
    nightHourRatio: 0,
    platformSampleCount: 0,
    platformAvgWage: null,
    catalogMatched: true,
    sourceLabel: `Bảng A (${detectedRegion.regionLabel} · ${tierMeta.label})`,
    evidenceFactors,
    explanation: '',
  };
}

export const MIN_LEGAL_HOURLY_WAGE = 17800;
export const MIN_LEGAL_HOURLY_WAGE_ERROR =
  'Mức thù lao không được thấp hơn mức tối thiểu quy định theo Nghị định 293/2025/NĐ-CP';

export function validateShiftWageAgainstFloor({
  hourlyWage,
  benchmark = null,
  city = null,
  venueAddress = '',
  venueLat = null,
  venueLng = null,
} = {}) {
  const detected = detectRegionFromAddress(venueAddress, venueLat, venueLng);
  const statutoryFloor =
    (city && STATUTORY_HOURLY_FLOORS[city]) ||
    STATUTORY_HOURLY_FLOORS[detected.regionCode] ||
    STATUTORY_HOURLY_FLOORS.REGION_1;

  const targetFloorWage = Number(benchmark?.legalFloorWage || statutoryFloor.wage);
  const regionLabel = benchmark?.legalRegionLabel || detected.regionLabel || statutoryFloor.regionLabel;
  const numWage = Math.round(Number(hourlyWage || 0));
  const minFloor = Number(benchmark?.minWage || benchmark?.suggestedWage || targetFloorWage);

  if (!Number.isFinite(numWage) || numWage < targetFloorWage) {
    return {
      isValid: false,
      error: `Mức thù lao không được thấp hơn mức tối thiểu quy định của ${regionLabel}: ${targetFloorWage.toLocaleString('vi-VN')} VNĐ/giờ (theo ${statutoryFloor.decree})`,
      warning: null,
      floorWage: targetFloorWage,
      recommendedMinWage: minFloor,
    };
  }

  if (numWage < minFloor) {
    return {
      isValid: true,
      error: null,
      warning: `Gợi ý: Mức thù lao thấp hơn mức tham chiếu (${minFloor.toLocaleString('vi-VN')} VNĐ/giờ), có thể ảnh hưởng tốc độ nhận ca.`,
      floorWage: targetFloorWage,
      recommendedMinWage: minFloor,
      isBelowAdmin: true,
    };
  }

  return {
    isValid: true,
    error: null,
    warning: null,
    floorWage: targetFloorWage,
    recommendedMinWage: minFloor,
  };
}

export function normalizeShiftJobType(jobType) {
  return String(jobType || '')
    .trim()
    .replace(/\s+/g, ' ')
    .toLowerCase();
}

function toMinuteEpoch(dateInput) {
  if (!dateInput) return null;
  const ms = new Date(dateInput).getTime();
  if (!Number.isFinite(ms)) return null;
  return Math.floor(ms / 60000);
}

export function validateDuplicateShift(
  candidate,
  existingShifts = [],
  { excludeShiftId = null, excludeIndex = null } = {}
) {
  if (!candidate || !Array.isArray(existingShifts) || existingShifts.length === 0) {
    return null;
  }
  const candidateRole = normalizeShiftJobType(candidate.jobType);
  const candidateStart = toMinuteEpoch(candidate.startTime);
  const candidateEnd = toMinuteEpoch(candidate.endTime);
  if (!candidateRole || candidateStart === null || candidateEnd === null) {
    return null;
  }

  const duplicate = existingShifts.find((sh, idx) => {
    if (!sh) return false;
    if (String(sh.status || '').toUpperCase() === 'CANCELLED') return false;
    if (excludeIndex !== null && excludeIndex !== undefined && idx === excludeIndex) return false;
    if (excludeShiftId && sh.shiftId && String(sh.shiftId) === String(excludeShiftId)) return false;

    const existingRole = normalizeShiftJobType(sh.jobType);
    const existingStart = toMinuteEpoch(sh.startTime);
    const existingEnd = toMinuteEpoch(sh.endTime);
    return (
      existingRole === candidateRole &&
      existingStart === candidateStart &&
      existingEnd === candidateEnd
    );
  });

  if (!duplicate) return null;
  const trimmedRole = String(candidate.jobType || '').trim();
  return `Đã tồn tại ca "${trimmedRole}" trong cùng khung giờ này. Vui lòng chọn giờ khác hoặc tăng số nhân sự cần tuyển ở ca đã có.`;
}
