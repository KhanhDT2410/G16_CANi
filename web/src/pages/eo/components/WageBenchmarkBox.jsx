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
  const [showEvidence, setShowEvidence] = useState(true);
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
    return (
      <div className="ecw-wage-placeholder">
        <div className="ecw-wage-placeholder-title">Khung thù lao tham chiếu</div>
        <p className="ecw-wage-placeholder-desc">
          {roleError
            ? 'Vui lòng nhập tên vị trí hợp lệ để hệ thống đối chiếu dữ liệu thị trường và sàn pháp lý theo khu vực.'
            : 'Chọn hoặc nhập vị trí công việc để xem khung thù lao tham chiếu.'}
        </p>
      </div>
    );
  }

  const numericWage = Number(hourlyWage || 0);
  const isBelowLegalFloor = numericWage > 0 && numericWage < benchmark.legalFloorWage;
  const isBelowAdminFloor = numericWage > 0 && numericWage < benchmark.minWage;

  return (
    <div className="ecw-wage-benchmark-card">
      <div className="ecw-wage-benchmark-top">
        <div className="ecw-wage-benchmark-info">
          <div className="ecw-wage-source-row">
            <span
              className={`ecw-wage-source-badge ${
                benchmark.catalogMatched ? 'is-matched' : 'is-custom'
              }`}
            >
              {benchmark.sourceLabel}
            </span>
            {loadingApi && (
              <span className="ecw-wage-sync-hint">Đang đối chiếu dữ liệu máy chủ...</span>
            )}
          </div>

          <div className="ecw-wage-range-title">
            {benchmark.catalogMatched
              ? `Khung tham chiếu (${trimmedRole}): `
              : 'Khung tham chiếu chung ngành Sự kiện: '}
            <strong>
              {benchmark.minWage.toLocaleString('vi-VN')}đ –{' '}
              {benchmark.maxWage.toLocaleString('vi-VN')}đ/giờ
            </strong>
          </div>

          <div className="ecw-wage-optimal-line">
            Mức đề xuất tối ưu:{' '}
            <strong>{benchmark.suggestedWage.toLocaleString('vi-VN')}đ/giờ</strong>
            {benchmark.platformSampleCount > 0 && benchmark.platformAvgWage ? (
              <span className="ecw-wage-sample-note">
                {' '}
                (Trung bình {benchmark.platformSampleCount} ca trên CANi:{' '}
                {benchmark.platformAvgWage.toLocaleString('vi-VN')}đ/h)
              </span>
            ) : null}
          </div>
        </div>

        <div className="ecw-wage-preset-actions">
          <button
            type="button"
            className={`ecw-wage-preset-btn ${
              numericWage === benchmark.minWage ? 'is-selected' : ''
            }`}
            onClick={() => onSelectWage?.(benchmark.minWage)}
          >
            Sàn: {benchmark.minWage.toLocaleString('vi-VN')}đ
          </button>
          <button
            type="button"
            className={`ecw-wage-preset-btn is-recommended ${
              numericWage === benchmark.suggestedWage ? 'is-selected' : ''
            }`}
            onClick={() => onSelectWage?.(benchmark.suggestedWage)}
          >
            Khuyên dùng: {benchmark.suggestedWage.toLocaleString('vi-VN')}đ
          </button>
          <button
            type="button"
            className={`ecw-wage-preset-btn ${
              numericWage === benchmark.maxWage ? 'is-selected' : ''
            }`}
            onClick={() => onSelectWage?.(benchmark.maxWage)}
          >
            Thu hút nhanh: {benchmark.maxWage.toLocaleString('vi-VN')}đ
          </button>
        </div>
      </div>

      {isBelowAdminFloor && (
        <div
          className="ecw-wage-legal-warning"
          style={{
            backgroundColor: isBelowLegalFloor ? '#fef2f2' : '#fffbeb',
            borderColor: isBelowLegalFloor ? '#fecaca' : '#fde68a',
            color: isBelowLegalFloor ? '#b91c1c' : '#b45309',
            padding: '0.65rem 0.85rem',
            borderRadius: '0.5rem',
            border: '1px solid',
            marginTop: '0.65rem',
          }}
        >
          <div style={{ fontWeight: 700, marginBottom: '0.2rem', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
            <span>⚠️</span>
            <span>
              {isBelowLegalFloor
                ? 'Cảnh báo vi phạm sàn thù lao pháp lý & sàn CANi'
                : 'Cảnh báo vi phạm Biểu lương sàn của Admin'}
            </span>
          </div>
          <div style={{ fontSize: '0.8rem', lineHeight: 1.45 }}>
            Mức thù lao đang nhập (<strong>{numericWage.toLocaleString('vi-VN')}đ/giờ</strong>) thấp hơn định mức lương sàn tối thiểu của CANi (<strong>{benchmark.minWage.toLocaleString('vi-VN')}đ/h</strong>)
            {isBelowLegalFloor && (
              <span>
                {' '}và thấp hơn mức lương tối thiểu vùng của <strong>{benchmark.legalRegionLabel}</strong> ({benchmark.legalFloorWage.toLocaleString('vi-VN')}đ/giờ theo {benchmark.legalReference}).
              </span>
            )}.
          </div>
          <button
            type="button"
            onClick={() => onSelectWage?.(benchmark.minWage)}
            style={{
              marginTop: '0.45rem',
              padding: '0.25rem 0.65rem',
              fontSize: '0.75rem',
              fontWeight: 700,
              borderRadius: '0.375rem',
              border: `1px solid ${isBelowLegalFloor ? '#dc2626' : '#d97706'}`,
              backgroundColor: '#ffffff',
              color: isBelowLegalFloor ? '#dc2626' : '#d97706',
              cursor: 'pointer',
            }}
          >
            Áp dụng mức sàn CANi: {benchmark.minWage.toLocaleString('vi-VN')}đ/h
          </button>
        </div>
      )}

      <div className="ecw-wage-evidence-block">
        <button
          type="button"
          className="ecw-wage-evidence-toggle"
          onClick={() => setShowEvidence((prev) => !prev)}
        >
          <span>
            Căn cứ & dẫn chứng tính toán ({benchmark.evidenceFactors.length} yếu tố)
          </span>
          <span>{showEvidence ? 'Thu gọn' : 'Xem chi tiết'}</span>
        </button>

        {showEvidence && (
          <ul className="ecw-wage-evidence-list">
            {benchmark.evidenceFactors.map((factor, idx) => (
              <li key={idx}>{factor}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
