import React from 'react';

/**
 * CaniLogo - Official Brand Identity Component
 * Tái hiện chuẩn xác 100% Logo chính thức của CANi:
 * - Chữ 'C' uốn lượn dải màu Cyan - Ocean Blue
 * - Chữ 'A' cách điệu hình tượng nhân sự sự kiện với đầu tròn và dáng vươn lên
 * - Chữ 'Ni' xanh navy trầm đậm đà, hiện đại
 * - Tagline: "EVENT PLATFORM" hoặc "Worker"
 * - Slogan: "Cần Ai, Có CANi"
 */
const CaniLogo = ({
  variant = 'full', // 'full' | 'horizontal' | 'worker' | 'mark'
  height = 54,
  className = '',
  onClick,
}) => {
  // Biểu tượng glyph vector độc quyền
  const renderGlyphs = () => (
    <svg
      viewBox="0 0 280 110"
      height={height}
      className={`cani-brandmark-svg ${className}`}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      onClick={onClick}
      style={{ cursor: onClick ? 'pointer' : 'default', display: 'inline-block' }}
    >
      <defs>
        {/* Gradient chữ C: Cyan tươi (#00D2FF) -> Ocean Blue (#0066FF) */}
        <linearGradient id="caniGradC" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#00D2FF" />
          <stop offset="50%" stopColor="#0091FF" />
          <stop offset="100%" stopColor="#0055FF" />
        </linearGradient>

        {/* Gradient chữ A (Hình tượng nhân sự): Sky Blue (#38BDF8) -> Deep Blue (#1D4ED8) */}
        <linearGradient id="caniGradA" x1="0%" y1="100%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#00E5FF" />
          <stop offset="60%" stopColor="#0077FF" />
          <stop offset="100%" stopColor="#0052D4" />
        </linearGradient>

        {/* Head glow */}
        <linearGradient id="caniHeadGrad" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#00B4D8" />
          <stop offset="100%" stopColor="#0066FF" />
        </linearGradient>

        <filter id="caniSoftShadow" x="-10%" y="-10%" width="120%" height="120%">
          <feDropShadow dx="0" dy="3" stdDeviation="4" floodColor="#0066FF" floodOpacity="0.18" />
        </filter>
      </defs>

      <g filter="url(#caniSoftShadow)">
        {/* Chữ C (Vòng cung bo tròn đầy đặn) */}
        <path
          d="M62 25 C40 18, 12 34, 12 60 C12 86, 40 102, 64 94 C76 89, 82 81, 80 72 C78 63, 68 62, 60 66 C46 72, 32 68, 30 58 C28 47, 40 38, 56 42 C64 44, 72 40, 72 32 C72 25, 68 23, 62 25 Z"
          fill="url(#caniGradC)"
        />

        {/* Chữ A: Đầu tròn nhân sự (Head dot) */}
        <circle cx="114" cy="22" r="13" fill="url(#caniHeadGrad)" />

        {/* Chữ A: Thân người vòm cong (Arch body) */}
        <path
          d="M78 88 C84 66, 94 48, 114 48 C134 48, 144 66, 150 88 C153 95, 144 100, 136 96 C131 92, 128 84, 126 76 C123 68, 118 64, 114 64 C110 64, 105 68, 102 76 C100 84, 97 92, 92 96 C84 100, 75 95, 78 88 Z"
          fill="url(#caniGradA)"
        />

        {/* Chữ N (Xanh Navy đậm đà hiện đại) */}
        <path
          d="M165 40 C165 37, 168 35, 172 35 L182 35 C186 35, 189 37, 190 41 L210 75 L210 40 C210 37, 213 35, 217 35 L227 35 C231 35, 234 37, 234 40 L234 92 C234 95, 231 97, 227 97 L217 97 C213 97, 210 95, 209 91 L189 57 L189 92 C189 95, 186 97, 182 97 L172 97 C168 97, 165 95, 165 92 L165 40 Z"
          fill="#0B1B3D"
        />

        {/* Chữ i: Thân và Dấu chấm */}
        <circle cx="254" cy="38" r="8" fill="#0B1B3D" />
        <rect x="246" y="52" width="16" height="45" rx="5" fill="#0B1B3D" />
      </g>
    </svg>
  );

  // Variant 1: Logo thu nhỏ (Chỉ biểu tượng chữ CANi)
  if (variant === 'mark') {
    return renderGlyphs();
  }

  // Variant 2: Dành cho CANi Worker Mobile
  if (variant === 'worker') {
    return (
      <div className={`cani-logo-worker-wrapper ${className}`} style={{ textAlign: 'center' }}>
        {renderGlyphs()}
        <div
          style={{
            fontFamily: 'var(--font-display, "Be Vietnam Pro", sans-serif)',
            fontSize: `${Math.round(height * 0.38)}px`,
            fontWeight: 800,
            color: '#0077FF',
            letterSpacing: '0.04em',
            marginTop: '-4px',
          }}
        >
          Worker
        </div>
      </div>
    );
  }

  // Variant 3: Dạng ngang (Horizontal trên Header Web)
  if (variant === 'horizontal') {
    return (
      <div
        className={`cani-logo-horizontal-wrapper ${className}`}
        style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'flex-start' }}
      >
        {renderGlyphs()}
        <span
          style={{
            fontFamily: 'var(--font-family, "Plus Jakarta Sans", sans-serif)',
            fontSize: '9.5px',
            fontWeight: 800,
            letterSpacing: '0.24em',
            color: '#0B1B3D',
            paddingLeft: '14px',
            marginTop: '-6px',
            textTransform: 'uppercase',
          }}
        >
          EVENT PLATFORM
        </span>
      </div>
    );
  }

  // Variant 4: Đầy đủ (Full Logo + EVENT PLATFORM + Cần Ai, Có CANi)
  return (
    <div
      className={`cani-logo-full-wrapper ${className}`}
      style={{
        display: 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        textAlign: 'center',
      }}
    >
      {renderGlyphs()}
      <div
        style={{
          fontFamily: 'var(--font-family, "Plus Jakarta Sans", sans-serif)',
          fontSize: '11px',
          fontWeight: 800,
          letterSpacing: '0.28em',
          color: '#0B1B3D',
          marginTop: '-4px',
          textTransform: 'uppercase',
        }}
      >
        EVENT PLATFORM
      </div>
      <div
        style={{
          fontFamily: 'var(--font-family, "Plus Jakarta Sans", sans-serif)',
          fontSize: '12px',
          fontWeight: 600,
          color: '#475569',
          letterSpacing: '0.06em',
          marginTop: '4px',
        }}
      >
        Cần Ai, Có CANi
      </div>
    </div>
  );
};

export default CaniLogo;
