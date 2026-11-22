import React, { useEffect, useMemo, useRef, useState } from 'react';
import { eoService } from '../../../services/eoService';
import {
  buildEvidenceWageBenchmark,
  detectCityFromAddress,
  validateJobType,
} from '../utils/shiftValidation';

/**
 * Shared component hiển thị Khung thù lao tham chiếu kèm dẫn chứng pháp lý & dữ liệu nội bộ CANi (UC-14).
 * Dùng chung cho Bước 2 Tạo Sự Kiện và Modal Thêm Ca Làm Việc.
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
  onValidationChange,
}) {
  const roleError = useMemo(
    () => jobTypeError || validateJobType(jobType),
    [jobTypeError, jobType]
  );

  const [apiBenchmark, setApiBenchmark] = useState(null);
  const [loadingApi, setLoadingApi] = useState(false);
  const cacheRef = useRef({});

  const trimmedRole = String(jobType || '').trim();
  const city = detectCityFromAddress(venueAddress, venueLat, venueLng);

  useEffect(() => {
    if (roleError || !trimmedRole || !startTime) {
      setApiBenchmark(null);
      setLoadingApi(false);
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
    const cacheKey = `${city}|${trimmedRole.toLowerCase()}|${startIso.slice(0, 16)}`;

    if (cacheRef.current[cacheKey]) {
      setApiBenchmark(cacheRef.current[cacheKey]);
      return;
    }

    let cancelled = false;
    const timer = setTimeout(async () => {
      setLoadingApi(true);
      try {
        const res = await eoService.getWageRecommendation({
          city,
          jobType: trimmedRole,
          startTime: startIso,
          endTime: endIso,
        });
        if (!cancelled && res) {
          cacheRef.current[cacheKey] = res;
          setApiBenchmark(res);
        }
      } catch {
        // Giữ fallback client-side theo cùng công thức & sàn pháp lý
      } finally {
        if (!cancelled) setLoadingApi(false);
      }
    }, 380);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [roleError, trimmedRole, city, startTime, endTime]);

  const benchmark = useMemo(() => {
    if (roleError) return null;
    return buildEvidenceWageBenchmark({
      jobType: trimmedRole,
      startTime,
      venueAddress,
      venueLat,
      venueLng,
      apiBenchmark,
    });
  }, [roleError, trimmedRole, startTime, venueAddress, venueLat, venueLng, apiBenchmark]);

  useEffect(() => {
    onBenchmarkLoaded?.(benchmark);
  }, [benchmark, onBenchmarkLoaded]);

  if (roleError || !benchmark) {
    return null;
  }

  const numericWage = Number(hourlyWage || 0);
  const isBelowLegalFloor = numericWage > 0 && numericWage < benchmark.legalFloorWage;
  const isBelowAdminFloor = numericWage > 0 && numericWage < benchmark.minWage;

  return (
    <div className="ecw-wage-benchmark-container" style={{ margin: '0.25rem 0 0.75rem' }}>
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
            Sàn: {benchmark.minWage.toLocaleString('vi-VN')}đ
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

      {isBelowAdminFloor && (
        <div
          style={{
            fontSize: '0.76rem',
            color: isBelowLegalFloor ? '#dc2626' : '#d97706',
            fontWeight: 600,
            marginTop: '0.25rem',
          }}
        >
          Mức thù lao thấp hơn sàn tối thiểu ({benchmark.minWage.toLocaleString('vi-VN')}đ/h).{' '}
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
            Áp dụng mức sàn
          </button>
        </div>
      )}
    </div>
  );
}
