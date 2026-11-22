/**
 * Shared validation & evidence-based wage benchmark helpers for EO shift creation/editing.
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

export const WAGE_RECOMMENDATIONS_MAP = {
  'Lễ tân / Check-in': { min: 45000, max: 60000, defaultWage: 50000, recommended: 50000, note: 'Giao tiếp tốt, ngoại hình sáng' },
  'Phục vụ bàn': { min: 35000, max: 50000, defaultWage: 40000, recommended: 40000, note: 'Nhanh nhẹn, cẩn thận' },
  'Pha chế': { min: 60000, max: 85000, defaultWage: 70000, recommended: 70000, note: 'Có tay nghề pha chế' },
  'Pha chế (Bartender/Barista)': { min: 60000, max: 85000, defaultWage: 70000, recommended: 70000, note: 'Có tay nghề pha chế' },
  'Thu ngân': { min: 40000, max: 55000, defaultWage: 45000, recommended: 45000, note: 'Trung thực, tính toán chuẩn xác' },
  'Điều phối sự kiện': { min: 70000, max: 100000, defaultWage: 80000, recommended: 80000, note: 'Kinh nghiệm quản lý nhóm' },
  'Kỹ thuật âm thanh - ánh sáng': { min: 80000, max: 120000, defaultWage: 95000, recommended: 95000, note: 'Chuyên môn thiết bị sự kiện' },
  'Bảo vệ / An ninh': { min: 40000, max: 55000, defaultWage: 45000, recommended: 45000, note: 'Thể lực tốt, giữ trật tự' },
  'Hậu cần / Setup sự kiện': { min: 50000, max: 70000, defaultWage: 55000, recommended: 55000, note: 'Sức khỏe tốt, khuân vác, setup' },
  'PG / PB': { min: 70000, max: 110000, defaultWage: 85000, recommended: 85000, note: 'Ngoại hình, giới thiệu sản phẩm' },
};

// Các viết tắt phổ biến và hợp lệ trong ngành tổ chức sự kiện
export const VALID_EVENT_ROLE_ACRONYMS = new Set([
  'mc',
  'pg',
  'pb',
  'dj',
  'pr',
  'it',
  'av',
  'led',
  'vip',
  'f&b',
  'fnb',
  'qc',
  'qa',
  'cskh',
  'ctv',
  'hr',
  'pa',
  'pm',
]);

// Sàn lương tối thiểu giờ theo Nghị định 293/2025/NĐ-CP (áp dụng từ 01/01/2026)
export const STATUTORY_HOURLY_FLOORS = {
  HANOI: { wage: 25500, regionLabel: 'Vùng I (Hà Nội)', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  TP_HCM: { wage: 25500, regionLabel: 'Vùng I (TP.HCM)', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
  DANANG: { wage: 22700, regionLabel: 'Vùng II (Đà Nẵng nội thành)', decree: 'Nghị định 293/2025/NĐ-CP (từ 01/01/2026)' },
};

const VOWELS_REGEX = /[aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]/i;
const VOWELLESS_CONSONANT_RUN_REGEX = /[^aàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ\s\d,.\-_/()&+]{5,}/i;

/**
 * Kiểm tra chuỗi nhập liệu có phải spam / cào phím / ký tự vô nghĩa không.
 */
export function isGibberishOrSpam(text, { allowShortEventAcronyms = false } = {}) {
  if (!text) return false;
  const str = String(text).trim().toLowerCase();
  if (str.length < 2) return false;

  if (allowShortEventAcronyms && VALID_EVENT_ROLE_ACRONYMS.has(str)) {
    return false;
  }

  // 1. Ký tự đơn lặp liên tiếp >= 4 lần (aaaa, 1111)
  if (/(.)\1{3,}/.test(str)) return true;

  // 2. Cặp ký tự lặp liên tiếp >= 3 lần (ababab)
  if (/(.{2})\1{2,}/.test(str)) return true;

  const words = str.split(/[\s,./()&+-]+/).filter(Boolean);

  // 3. Từ lặp liên tiếp >= 3 lần
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

  // 4. Mẫu cào phím phổ biến
  if (/(asdasd|sdasd|ádasd|sdada|adasd|sdfsdf|dfghj|fghjk|ghjkl|qwerty|zxcvb|sdfh|asdf)/i.test(str)) {
    return true;
  }

  // 5. Độ lặp từ chiếm ưu thế bất thường
  if (words.length >= 4) {
    const counts = {};
    for (const w of words) counts[w] = (counts[w] || 0) + 1;
    if (Math.max(...Object.values(counts)) / words.length >= 0.45) return true;
  }

  // 6. Độ đa dạng ký tự quá thấp trên chuỗi (chỉ áp dụng tỷ lệ 0.3 cho chuỗi ngắn <= 30 ký tự)
  const cleanLetters = str.replace(/[^a-zàáảãạăằắẳẵặâầấẩẫậeèéẻẽẹêềếểễệiìíỉĩịoòóỏõọôồốổỗộơờớởỡợuùúủũụưừứửữựyỳýỷỹỵ]/gi, '');
  if (cleanLetters.length >= 10) {
    const uniqueChars = new Set(cleanLetters.split('')).size;
    if (cleanLetters.length <= 30) {
      if (uniqueChars <= 4 || uniqueChars / cleanLetters.length < 0.3) return true;
    } else {
      if (uniqueChars <= 5) return true;
    }
  }

  // 7. Từ >= 3 chữ cái nhưng không có bất kỳ nguyên âm nào (vd: sdfh, zxc, dfg) trừ viết tắt hợp lệ
  const minVowellessLen = allowShortEventAcronyms ? 3 : 4;
  for (const w of words) {
    if (/^\d+$/.test(w)) continue;
    if (allowShortEventAcronyms && VALID_EVENT_ROLE_ACRONYMS.has(w)) continue;
    const cleanWord = w.replace(/[^\p{L}]/gu, '');
    if (cleanWord.length >= minVowellessLen && !VOWELS_REGEX.test(cleanWord)) {
      return true;
    }
  }

  // 8. Chuỗi phụ âm liên tiếp >= 4 ký tự không ngắt quãng
  if (VOWELLESS_CONSONANT_RUN_REGEX.test(str)) return true;

  // 9. Chuỗi tổng thể >= 5 ký tự không chứa nguyên âm
  const nonSpace = str.replace(/\s+/g, '');
  if (nonSpace.length >= 5 && !VOWELS_REGEX.test(nonSpace) && !/^\d+$/.test(nonSpace)) {
    if (!(allowShortEventAcronyms && VALID_EVENT_ROLE_ACRONYMS.has(nonSpace))) {
      return true;
    }
  }

  return false;
}

/**
 * Kiểm tra tính hợp lệ của tên Vị trí / Vai trò cần tuyển ở Bước 2 & Form thêm ca.
 * Trả về chuỗi thông báo lỗi nếu không hợp lệ, hoặc null nếu hợp lệ.
 */
export function validateJobType(rawJobType) {
  const trimmed = String(rawJobType || '').trim();
  if (!trimmed) {
    return 'Vui lòng nhập hoặc chọn tên vị trí / vai trò cần tuyển.';
  }
  if (trimmed.length > 80) {
    return 'Tên vị trí công việc không được vượt quá 80 ký tự.';
  }
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
 * Suy luận mã Thành phố (City enum của backend) từ địa chỉ hoặc tọa độ GPS sự kiện.
 */
export function detectCityFromAddress(address = '', lat = null, lng = null) {
  const norm = String(address || '').toLowerCase();
  if (
    /(hà nội|ha noi|hoàn kiếm|đống đa|cầu giấy|ba đình|hai bà trưng|thanh xuân|tây hồ|nam từ liêm|bắc từ liêm|long biên|hà đông|hoàng mai|hòa lạc|thạch thất|cầu diễn|phú diễn|hồ tùng mậu|xuân thủy|mỹ đình|sư phạm|goldmark|đông anh|gia lâm|thanh trì|sóc sơn)/.test(
      norm
    )
  ) {
    return 'HANOI';
  }
  if (
    /(đà nẵng|da nang|hải châu|sơn trà|thanh khê|ngũ hành sơn|liên chiểu|cẩm lệ|hòa vang)/.test(
      norm
    )
  ) {
    return 'DANANG';
  }
  if (
    /(tp\.?\s*hcm|hồ chí minh|ho chi minh|sài gòn|sai gon|secc|quận 1\b|quận 3\b|quận 4\b|quận 5\b|quận 7\b|quận 10\b|quận 11\b|quận 12\b|thủ đức|bình thạnh|phú nhuận|tân bình|gò vấp|tân phú|bình tân|nhà bè|bình chánh)/.test(
      norm
    )
  ) {
    return 'TP_HCM';
  }

  // Nếu text chưa ghi tên tỉnh/quận, suy luận từ tọa độ GPS đã ghim trên bản đồ
  const nLat = Number(lat);
  const nLng = Number(lng);
  if (Number.isFinite(nLat) && Number.isFinite(nLng) && (nLat !== 0 || nLng !== 0)) {
    if (nLat >= 20.5 && nLat <= 21.45 && nLng >= 105.2 && nLng <= 106.1) {
      return 'HANOI';
    }
    if (nLat >= 15.8 && nLat <= 16.35 && nLng >= 107.8 && nLng <= 108.4) {
      return 'DANANG';
    }
    if (nLat >= 10.3 && nLat <= 11.2 && nLng >= 106.3 && nLng <= 107.15) {
      return 'TP_HCM';
    }
  }

  return 'TP_HCM';
}

/**
 * Trả về thông tin vùng lương cho Sidebar: tránh hiển thị cứng TP.HCM khi EO chưa chọn địa chỉ hoặc chưa ghim vị trí mới.
 */
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
      city: null,
      regionLabel: 'Chưa xác định khu vực',
      wage: null,
      decree: null,
    };
  }

  const city = detectCityFromAddress(address, lat, lng);
  const floorInfo = STATUTORY_HOURLY_FLOORS[city] || STATUTORY_HOURLY_FLOORS.TP_HCM;
  return {
    isResolved: true,
    city,
    regionLabel: floorInfo.regionLabel,
    wage: floorInfo.wage,
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
    timeMultiplier = 1.15;
    timeFactorText = 'Khung giờ tối cuối tuần: điều chỉnh +15% theo nhu cầu nhân sự sự kiện cao điểm.';
  } else if (isWeekend) {
    timeMultiplier = 1.1;
    timeFactorText = 'Khung giờ ban ngày cuối tuần: điều chỉnh +10% theo nhu cầu thị trường cuối tuần.';
  } else if (hour >= 22 || hour < 6) {
    timeMultiplier = 1.3;
    timeFactorText =
      'Khung giờ ban đêm (22h–06h): cộng tối thiểu +30% theo nguyên tắc làm việc ban đêm (Điều 98 Bộ luật Lao động 2019).';
  } else if (hour >= 18) {
    timeMultiplier = 1.1;
    timeFactorText = 'Khung giờ buổi tối (18h–22h): điều chỉnh +10% cho ca tối.';
  }

  const hoursUntilStart = (d.getTime() - Date.now()) / (1000 * 60 * 60);
  let leadMultiplier = 1;
  let leadFactorText = null;
  if (hoursUntilStart < 24) {
    leadMultiplier = 1.2;
    leadFactorText = 'Độ gấp tuyển dụng [Khẩn cấp < 24h]: cộng +20% để tăng tỷ lệ Worker nhận ca kịp giờ.';
  } else if (hoursUntilStart < 72) {
    leadMultiplier = 1.1;
    leadFactorText = 'Độ gấp tuyển dụng [Cận ngày 24h–72h]: cộng +10% để thu hút ứng viên sớm.';
  }

  return { timeMultiplier, timeFactorText, leadMultiplier, leadFactorText };
}

/**
 * Tổng hợp khung thù lao tham chiếu kèm dẫn chứng pháp lý & dữ liệu CANi (dùng chung cho Wizard Step 2 & ShiftCreateModal).
 */
export function buildEvidenceWageBenchmark({
  jobType,
  startTime,
  venueAddress,
  venueLat = null,
  venueLng = null,
  apiBenchmark,
}) {
  const city = detectCityFromAddress(venueAddress, venueLat, venueLng);
  const floorInfo = STATUTORY_HOURLY_FLOORS[city] || STATUTORY_HOURLY_FLOORS.TP_HCM;
  const trimmedRole = String(jobType || '').trim();

  if (apiBenchmark && Number(apiBenchmark.suggestedWage) > 0) {
    const sampleCount = Number(apiBenchmark.platformSampleCount || 0);
    const catalogMatched = Boolean(apiBenchmark.catalogMatched);
    const sourceLabel =
      apiBenchmark.benchmarkSource === 'CANI_WAGE_MATRIX'
        ? 'Biểu Ma trận Thù lao CANi'
        : sampleCount >= 3
        ? `Dữ liệu thực tế CANi (${sampleCount} ca)`
        : catalogMatched
        ? 'Biểu chuẩn ngành Sự kiện CANi'
        : 'Khung tham chiếu chung (Vị trí tùy chỉnh)';

    return {
      city,
      minWage: Number(apiBenchmark.minWage || floorInfo.wage),
      suggestedWage: Number(apiBenchmark.suggestedWage),
      maxWage: Number(apiBenchmark.maxWage || Number(apiBenchmark.suggestedWage) + 15000),
      legalFloorWage: Number(apiBenchmark.legalFloorWage || floorInfo.wage),
      legalRegionLabel: apiBenchmark.legalRegionLabel || floorInfo.regionLabel,
      legalReference: apiBenchmark.legalReference || floorInfo.decree,
      platformSampleCount: sampleCount,
      platformAvgWage: apiBenchmark.platformAvgWage ? Number(apiBenchmark.platformAvgWage) : null,
      catalogMatched,
      sourceLabel,
      evidenceFactors: Array.isArray(apiBenchmark.evidenceFactors) ? apiBenchmark.evidenceFactors : [],
      explanation: apiBenchmark.explanation || '',
    };
  }

  // Fallback client-side cùng công thức khi API chưa phản hồi hoặc offline
  const catalogRec = WAGE_RECOMMENDATIONS_MAP[trimmedRole];
  const catalogMatched = Boolean(catalogRec);
  const { timeMultiplier, timeFactorText, leadMultiplier, leadFactorText } =
    computeClientTimeAndLeadAdjustments(startTime);

  const baseWage = catalogRec ? catalogRec.defaultWage : 45000;
  const suggestedWage = Math.max(
    floorInfo.wage,
    roundToThousand(baseWage * timeMultiplier * leadMultiplier)
  );
  const minWage = Math.max(
    floorInfo.wage,
    roundToThousand(
      (catalogRec ? catalogRec.min * timeMultiplier : suggestedWage * 0.85)
    )
  );
  const maxWage = Math.max(
    suggestedWage + 5000,
    roundToThousand(
      (catalogRec ? catalogRec.max * timeMultiplier * leadMultiplier : suggestedWage * 1.25)
    )
  );

  const evidenceFactors = [
    `Sàn pháp lý ${floorInfo.regionLabel}: ${floorInfo.wage.toLocaleString('vi-VN')} đ/giờ theo ${floorInfo.decree}.`,
    catalogMatched
      ? `Mức chuẩn ngành sự kiện cho "${trimmedRole}" (${catalogRec.note}).`
      : `Vị trí tùy chỉnh "${trimmedRole}" chưa có trong danh mục chuẩn — đang áp dụng khung tham chiếu chung ngành Sự kiện.`,
    timeFactorText,
  ];
  if (leadFactorText) evidenceFactors.push(leadFactorText);

  return {
    city,
    minWage,
    suggestedWage,
    maxWage,
    legalFloorWage: floorInfo.wage,
    legalRegionLabel: floorInfo.regionLabel,
    legalReference: floorInfo.decree,
    platformSampleCount: 0,
    platformAvgWage: null,
    catalogMatched,
    sourceLabel: catalogMatched ? 'Biểu chuẩn ngành Sự kiện CANi' : 'Khung tham chiếu chung (Vị trí tùy chỉnh)',
    evidenceFactors,
    explanation: '',
  };
}

/**
 * Kiểm tra mức thù lao ca làm việc đối chiếu với Biểu lương sàn của Admin và Sàn pháp lý.
 * @param {Object} params
 * @param {number|string} params.hourlyWage Mức thù lao mỗi giờ
 * @param {Object} [params.benchmark] Kết quả tra cứu khung thù lao từ API/Client
 * @param {string} [params.city] Mã thành phố (HANOI, TP_HCM, DANANG)
 * @param {string} [params.venueAddress] Địa chỉ tổ chức sự kiện
 * @param {number} [params.venueLat] Vĩ độ
 * @param {number} [params.venueLng] Kinh độ
 * @returns {{ isValid: boolean, error: string|null, floorWage: number }}
 */
export function validateShiftWageAgainstFloor({
  hourlyWage,
  benchmark = null,
  city = null,
  venueAddress = '',
  venueLat = null,
  venueLng = null,
} = {}) {
  const resolvedCity = city || detectCityFromAddress(venueAddress, venueLat, venueLng);
  const statutoryFloor = STATUTORY_HOURLY_FLOORS[resolvedCity] || STATUTORY_HOURLY_FLOORS.TP_HCM;
  const numWage = Math.round(Number(hourlyWage || 0));

  const minFloor = Number(benchmark?.minWage || statutoryFloor.wage);
  const legalFloor = Number(benchmark?.legalFloorWage || statutoryFloor.wage);
  const regionLabel = benchmark?.legalRegionLabel || statutoryFloor.regionLabel;
  const legalReference = benchmark?.legalReference || statutoryFloor.decree;

  if (numWage <= 0) {
    return {
      isValid: false,
      error: 'Vui lòng nhập mức thù lao mỗi giờ cho ca làm việc.',
      floorWage: minFloor,
    };
  }

  // Vi phạm mức sàn tối thiểu của luật lao động
  if (numWage < legalFloor) {
    return {
      isValid: false,
      error: `Mức lương này đang thấp hơn định mức lương sàn tối thiểu của CANi (${minFloor.toLocaleString('vi-VN')}đ/h) và dưới sàn pháp lý ${regionLabel} (${legalFloor.toLocaleString('vi-VN')}đ/h theo ${legalReference}).`,
      floorWage: minFloor,
      isBelowLegal: true,
    };
  }

  // Vi phạm Biểu ma trận thù lao tối thiểu của Admin
  if (numWage < minFloor) {
    return {
      isValid: false,
      error: `Mức lương này đang thấp hơn định mức lương sàn tối thiểu của CANi (${minFloor.toLocaleString('vi-VN')}đ/h).`,
      floorWage: minFloor,
      isBelowAdmin: true,
    };
  }

  return {
    isValid: true,
    error: null,
    floorWage: minFloor,
  };
}
