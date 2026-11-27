/**
 * Hằng số & cấu hình hiển thị cho Biểu Ma Trận Thù Lao Region × JobTier (UC-30, UC-31)
 * Tuân thủ Bảng A theo Nghị định 293/2025/NĐ-CP (hiệu lực từ 01/01/2026).
 */

export const REGION_OPTIONS = [
  { value: 'REGION_1', label: 'Vùng I (25.500 đ/h)', floor: 25500 },
  { value: 'REGION_2', label: 'Vùng II (22.700 đ/h)', floor: 22700 },
  { value: 'REGION_3', label: 'Vùng III (20.000 đ/h)', floor: 20000 },
  { value: 'REGION_4', label: 'Vùng IV (17.800 đ/h)', floor: 17800 },
];

export const REGION_LABELS = {
  REGION_1: 'Vùng I',
  REGION_2: 'Vùng II',
  REGION_3: 'Vùng III',
  REGION_4: 'Vùng IV',
};

export const REGION_CONFIG = {
  REGION_1: { label: 'Vùng I', color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
  REGION_2: { label: 'Vùng II', color: '#0369a1', bg: '#f0f9ff', border: '#bae6fd' },
  REGION_3: { label: 'Vùng III', color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
  REGION_4: { label: 'Vùng IV', color: '#475569', bg: '#f8fafc', border: '#cbd5e1' },
};

export const JOB_TIER_OPTIONS = [
  {
    value: 'TIER_1_GENERAL',
    label: 'Phổ thông (1.0x – 1.3x)',
    defaultLabel: 'Phổ thông',
    defaultDescription: 'Phát tờ rơi, hậu cần nhẹ, trực khu vực',
    minMultiplier: 1.0,
    recommendedMultiplier: 1.15,
    maxMultiplier: 1.3,
  },
  {
    value: 'TIER_2_SERVICE',
    label: 'Giao tiếp & Dịch vụ (1.3x – 1.8x)',
    defaultLabel: 'Giao tiếp & Dịch vụ',
    defaultDescription: 'Lễ tân, check-in, hướng dẫn khách, phục vụ bàn, thu ngân',
    minMultiplier: 1.3,
    recommendedMultiplier: 1.55,
    maxMultiplier: 1.8,
  },
  {
    value: 'TIER_3_PHYSICAL',
    label: 'Thể lực / An ninh (1.5x – 2.2x)',
    defaultLabel: 'Thể lực / An ninh',
    defaultDescription: 'Bốc xếp, setup sân khấu, bảo vệ, an ninh',
    minMultiplier: 1.5,
    recommendedMultiplier: 1.85,
    maxMultiplier: 2.2,
  },
  {
    value: 'TIER_4_PROMOTION',
    label: 'PG / PB bán hàng & Pha chế (2.0x – 4.0x)',
    defaultLabel: 'PG / PB bán hàng & Pha chế',
    defaultDescription: 'PG/PB bán hàng, sampling, roadshow, pha chế (Bartender/Barista)',
    minMultiplier: 2.0,
    recommendedMultiplier: 2.8,
    maxMultiplier: 4.0,
  },
  {
    value: 'TIER_5_TECHNICAL',
    label: 'Điều phối & Kỹ thuật (2.5x – 5.0x)',
    defaultLabel: 'Điều phối & Kỹ thuật',
    defaultDescription: 'Điều phối sự kiện (Coordinator), kỹ thuật âm thanh - ánh sáng',
    minMultiplier: 2.5,
    recommendedMultiplier: 3.75,
    maxMultiplier: 5.0,
  },
  {
    value: 'TIER_6_SPECIALIST',
    label: 'Chuyên nghiệp / Ngoại ngữ (4.5x – 10.0x)',
    defaultLabel: 'Chuyên nghiệp / Ngoại ngữ',
    defaultDescription: 'PG/PB sự kiện chuyên nghiệp, có ngoại ngữ, MC song ngữ',
    minMultiplier: 4.5,
    recommendedMultiplier: 6.5,
    maxMultiplier: 10.0,
  },
];

export const JOB_TIER_LABELS = Object.fromEntries(
  JOB_TIER_OPTIONS.map((t) => [t.value, t.defaultLabel])
);

export const JOB_TIER_CONFIG = {
  TIER_1_GENERAL: { label: 'Bậc 1 · Phổ thông', color: '#475569', bg: '#f1f5f9', border: '#cbd5e1' },
  TIER_2_SERVICE: { label: 'Bậc 2 · Giao tiếp & Dịch vụ', color: '#15803d', bg: '#f0fdf4', border: '#bbf7d0' },
  TIER_3_PHYSICAL: { label: 'Bậc 3 · Thể lực / An ninh', color: '#b45309', bg: '#fffbeb', border: '#fde68a' },
  TIER_4_PROMOTION: { label: 'Bậc 4 · PG/PB & Pha chế', color: '#1d4ed8', bg: '#eff6ff', border: '#bfdbfe' },
  TIER_5_TECHNICAL: { label: 'Bậc 5 · Điều phối & Kỹ thuật', color: '#7e22ce', bg: '#faf5ff', border: '#e9d5ff' },
  TIER_6_SPECIALIST: { label: 'Bậc 6 · Chuyên nghiệp / Ngoại ngữ', color: '#be123c', bg: '#fff1f2', border: '#fecdd3' },
};
