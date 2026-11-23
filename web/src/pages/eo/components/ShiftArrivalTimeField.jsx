import React, { useMemo } from 'react';
import { toDateTimeLocalInput } from '../utils/eventHelpers';

const ARRIVAL_PRESETS = [
  { label: 'Đúng giờ ca (0p)', minutes: 0 },
  { label: 'Trước 15 phút', minutes: 15 },
  { label: 'Trước 20 phút', minutes: 20 },
  { label: 'Trước 30 phút', minutes: 30 },
];

/**
 * Shared component chọn Giờ yêu cầu Worker có mặt (Arrival Time) trước khi bắt đầu ca.
 * Dùng chung cho Wizard Bước 2, ShiftCreateModal và EditShiftModal.
 */
export default function ShiftArrivalTimeField({
  startTime,
  arrivalTime,
  onChange,
  error = null,
}) {
  const offsetInfo = useMemo(() => {
    const s = startTime ? new Date(startTime) : null;
    if (!s || isNaN(s.getTime())) {
      return { activePreset: 0, isCustom: false, diffMins: 0, previewTime: '' };
    }
    if (!arrivalTime) {
      return {
        activePreset: 0,
        isCustom: false,
        diffMins: 0,
        previewTime: s.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
      };
    }
    const a = new Date(arrivalTime);
    if (isNaN(a.getTime())) {
      return { activePreset: null, isCustom: true, diffMins: 0, previewTime: '' };
    }
    const diffMins = Math.round((s.getTime() - a.getTime()) / (60 * 1000));
    const matchedPreset = ARRIVAL_PRESETS.find((p) => p.minutes === diffMins);
    return {
      activePreset: matchedPreset ? matchedPreset.minutes : null,
      isCustom: !matchedPreset,
      diffMins,
      previewTime: a.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
    };
  }, [startTime, arrivalTime]);

  const applyPresetMinutes = (minutes) => {
    if (minutes === 0) {
      onChange('');
      return;
    }
    const s = startTime ? new Date(startTime) : null;
    if (!s || isNaN(s.getTime())) {
      onChange('');
      return;
    }
    const computed = new Date(s.getTime() - minutes * 60 * 1000);
    onChange(toDateTimeLocalInput(computed));
  };

  return (
    <div className="eo-form-group">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem', flexWrap: 'wrap', gap: '0.4rem' }}>
        <label style={{ margin: 0 }}>Giờ Yêu Cầu Có Mặt Trước Ca</label>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
          {ARRIVAL_PRESETS.map((preset) => {
            const isActive = offsetInfo.activePreset === preset.minutes;
            return (
              <button
                key={preset.minutes}
                type="button"
                onClick={() => applyPresetMinutes(preset.minutes)}
                style={{
                  padding: '0.25rem 0.6rem',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  borderRadius: '999px',
                  border: isActive ? '1px solid #2563eb' : '1px solid #cbd5e1',
                  backgroundColor: isActive ? '#eff6ff' : '#ffffff',
                  color: isActive ? '#1d4ed8' : '#475569',
                  cursor: 'pointer',
                }}
              >
                {preset.label}
              </button>
            );
          })}
        </div>
      </div>

      <input
        type="datetime-local"
        className={`eo-form-input ${error ? 'has-error' : ''}`}
        max={startTime || undefined}
        style={{
          borderColor: error ? '#ef4444' : undefined,
          boxShadow: error ? '0 0 0 2px rgba(239, 68, 68, 0.2)' : undefined,
        }}
        value={arrivalTime || startTime || ''}
        onChange={(e) => {
          const val = e.target.value;
          onChange(val === startTime ? '' : val);
        }}
      />

      {error ? (
        <div className="eo-field-error ecw-field-error">
          <span>{error}</span>
        </div>
      ) : (
        <div style={{ fontSize: '0.74rem', color: '#64748b', marginTop: '0.25rem' }}>
          {offsetInfo.diffMins > 0 && offsetInfo.previewTime
            ? `Nhân sự được yêu cầu có mặt từ ${offsetInfo.previewTime} (trước giờ vào ca ${offsetInfo.diffMins} phút) — cổng check-in mở từ mốc này.`
            : 'Mặc định trùng giờ bắt đầu ca (check-in đúng giờ vào ca). Chọn mốc trước 15–30 phút nếu cần briefing trước giờ chạy.'}
        </div>
      )}
    </div>
  );
}
