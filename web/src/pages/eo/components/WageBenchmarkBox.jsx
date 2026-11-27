import React, { useEffect, useMemo, useRef, useState } from 'react';
import { eoService } from '../../../services/eoService';
import {
  JOB_TIER_BENCHMARKS,
  MIN_LEGAL_HOURLY_WAGE,
  buildEvidenceWageBenchmark,
  classifyJobTier,
  detectRegionFromAddress,
  validateJobType,
} from '../utils/shiftValidation';

/**
 * Shared component hiển thị Khung thù lao tham chiếu Region × JobTier (Bảng A - NĐ 293/2025/NĐ-CP).
 * Hỗ trợ EO đổi Nhóm công việc (JobTier) khi nhập vị trí tự do ngoài danh mục chuẩn.
 */
export default function WageBenchmarkBox({
  jobType,
  jobTypeError = null,
  hourlyWage,
  startTime,
  endTime,
  venueAddress = '',
  venueLat = null,
  venueLng = null,
  onSelectWage,
  onBenchmarkLoaded,
}) {
  const roleError = useMemo(
    () => jobTypeError || validateJobType(jobType),
    [jobTypeError, jobType]
  );

  const [apiBenchmark, setApiBenchmark] = useState(null);
  const [selectedTierOverride, setSelectedTierOverride] = useState(null);
  const cacheRef = useRef({});

  const trimmedRole = String(jobType || '').trim();
  const detectedRegion = detectRegionFromAddress(venueAddress, venueLat, venueLng);

  // Khi EO đổi tên vị trí, reset override để tự phân loại lại theo từ khóa mới
  useEffect(() => {
    setSelectedTierOverride(null);
  }, [trimmedRole]);

  const effectiveTier = useMemo(
    () => selectedTierOverride || classifyJobTier(trimmedRole),
    [selectedTierOverride, trimmedRole]
  );

  useEffect(() => {
    if (roleError || !trimmedRole || !startTime) {
      setApiBenchmark(null);
      return;
    }

    const parsedStart = new Date(startTime);
    const parsedEnd = endTime ? new Date(endTime) : new Date(parsedStart.getTime() + 4 * 3600 * 1000);
    if (isNaN(parsedStart.getTime())) return;

    const safeEnd =
      !isNaN(parsedEnd.getTime()) && parsedEnd > parsedStart
        ? parsedEnd
        : new Date(parsedStart.getTime() + 4 * 3600 * 1000);

    const startIso = parsedStart.toISOString();
    const endIso = safeEnd.toISOString();
    const cacheKey = `${detectedRegion.regionCode}|${trimmedRole.toLowerCase()}|${selectedTierOverride || 'AUTO'}|${startIso.slice(0, 16)}|${endIso.slice(0, 16)}`;

    if (cacheRef.current[cacheKey]) {
      setApiBenchmark(cacheRef.current[cacheKey]);
      return;
    }

    let cancelled = false;
    const timer = setTimeout(async () => {
      try {
        const res = await eoService.getWageRecommendation({
          regionCode: detectedRegion.regionCode,
          city: detectedRegion.city,
          venueAddress,
          venueLat,
          venueLng,
          jobType: trimmedRole,
          jobTier: selectedTierOverride || null,
          startTime: startIso,
          endTime: endIso,
        });
        if (!cancelled && res) {
          cacheRef.current[cacheKey] = res;
          setApiBenchmark(res);
        }
      } catch {
        // Fallback client-side cùng mô hình Region × JobTier khi offline
      }
    }, 320);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [
    roleError,
    trimmedRole,
    detectedRegion.regionCode,
    detectedRegion.city,
    venueAddress,
    venueLat,
    venueLng,
    selectedTierOverride,
    startTime,
    endTime,
  ]);

  const benchmark = useMemo(() => {
    if (roleError) return null;
    return buildEvidenceWageBenchmark({
      jobType: trimmedRole,
      jobTier: selectedTierOverride,
      startTime,
      venueAddress,
      venueLat,
      venueLng,
      apiBenchmark,
    });
  }, [roleError, trimmedRole, selectedTierOverride, startTime, venueAddress, venueLat, venueLng, apiBenchmark]);

  useEffect(() => {
    onBenchmarkLoaded?.(benchmark);
  }, [benchmark, onBenchmarkLoaded]);

  if (roleError || !benchmark) {
    return null;
  }

  const numericWage = Number(hourlyWage || 0);
  const legalFloor = Number(benchmark.legalFloorWage || MIN_LEGAL_HOURLY_WAGE);
  const isAboveLegalFloor = numericWage >= legalFloor;
  const isBelowRecommendedHint = isAboveLegalFloor && numericWage < benchmark.minWage;

  return (
    <div className="ecw-wage-benchmark-container" style={{ margin: '0.25rem 0 0.75rem' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '0.4rem',
          marginBottom: '0.4rem',
        }}
      >
        <span style={{ fontSize: '0.75rem', color: '#475569' }}>
          Chuẩn <strong>{benchmark.legalRegionLabel}</strong> (sàn {legalFloor.toLocaleString('vi-VN')}đ/h)
          {benchmark.nightHourRatio > 0 && ' · gồm phụ phí làm đêm'}
        </span>

        <label style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', fontSize: '0.75rem', color: '#475569' }}>
          <span>Nhóm công việc:</span>
          <select
            value={effectiveTier}
            onChange={(e) => setSelectedTierOverride(e.target.value)}
            style={{
              fontSize: '0.75rem',
              padding: '0.18rem 0.45rem',
              borderRadius: '6px',
              border: '1px solid #cbd5e1',
              backgroundColor: '#f8fafc',
              color: '#0f172a',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            {JOB_TIER_BENCHMARKS.map((t) => (
              <option key={t.code} value={t.code}>
                {t.label} ({t.minMult.toFixed(1)}x–{t.maxMult.toFixed(1)}x)
              </option>
            ))}
          </select>
        </label>
      </div>

      <div
        className="ecw-wage-benchmark-line"
        style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '0.45rem',
        }}
      >
        <span style={{ fontSize: '0.8rem', color: '#475569', fontWeight: 600 }}>
          Gợi ý:
        </span>
        <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '0.35rem' }}>
          <button
            type="button"
            className={`ecw-wage-chip ${numericWage === benchmark.minWage ? 'is-selected' : ''}`}
            onClick={() => onSelectWage?.(benchmark.minWage)}
            style={{
              fontSize: '0.75rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '6px',
              border: numericWage === benchmark.minWage ? '1px solid #2563eb' : '1px solid #cbd5e1',
              backgroundColor: numericWage === benchmark.minWage ? '#2563eb' : '#ffffff',
              color: numericWage === benchmark.minWage ? '#ffffff' : '#334155',
              fontWeight: numericWage === benchmark.minWage ? 700 : 500,
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            Tối thiểu gợi ý: {benchmark.minWage.toLocaleString('vi-VN')}đ
          </button>
          <button
            type="button"
            className={`ecw-wage-chip is-recommended ${numericWage === benchmark.suggestedWage ? 'is-selected' : ''}`}
            onClick={() => onSelectWage?.(benchmark.suggestedWage)}
            style={{
              fontSize: '0.75rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '6px',
              border: numericWage === benchmark.suggestedWage ? '1px solid #2563eb' : '1px solid #93c5fd',
              backgroundColor: numericWage === benchmark.suggestedWage ? '#2563eb' : '#eff6ff',
              color: numericWage === benchmark.suggestedWage ? '#ffffff' : '#1d4ed8',
              fontWeight: 700,
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            Khuyên dùng: {benchmark.suggestedWage.toLocaleString('vi-VN')}đ
          </button>
          <button
            type="button"
            className={`ecw-wage-chip ${numericWage === benchmark.maxWage ? 'is-selected' : ''}`}
            onClick={() => onSelectWage?.(benchmark.maxWage)}
            style={{
              fontSize: '0.75rem',
              padding: '0.2rem 0.55rem',
              borderRadius: '6px',
              border: numericWage === benchmark.maxWage ? '1px solid #2563eb' : '1px solid #cbd5e1',
              backgroundColor: numericWage === benchmark.maxWage ? '#2563eb' : '#ffffff',
              color: numericWage === benchmark.maxWage ? '#ffffff' : '#334155',
              fontWeight: numericWage === benchmark.maxWage ? 700 : 500,
              cursor: 'pointer',
              transition: 'all 0.15s ease',
            }}
          >
            Thu hút nhanh: {benchmark.maxWage.toLocaleString('vi-VN')}đ
          </button>
        </div>
      </div>

      {isBelowRecommendedHint && (
        <div
          style={{
            fontSize: '0.76rem',
            color: '#d97706',
            fontWeight: 600,
            marginTop: '0.25rem',
          }}
        >
          Nhắc nhở nhẹ: Mức thù lao đang thấp hơn mức gợi ý tham khảo ({benchmark.minWage.toLocaleString('vi-VN')} VNĐ/giờ) — giá ca vẫn do Ban tổ chức quyết định.{' '}
          <button
            type="button"
            onClick={() => onSelectWage?.(benchmark.minWage)}
            style={{
              background: 'none',
              border: 'none',
              color: '#2563eb',
              textDecoration: 'underline',
              cursor: 'pointer',
              padding: 0,
              fontWeight: 700,
              fontSize: '0.76rem',
            }}
          >
            Áp dụng mức gợi ý
          </button>
        </div>
      )}
    </div>
  );
}
