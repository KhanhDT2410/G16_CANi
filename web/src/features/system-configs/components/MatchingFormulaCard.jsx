import { useMemo } from 'react';

/**
 * Thẻ mô phỏng mô hình công thức tính Điểm Phù Hợp Matching Score (ADM-09).
 * Hiển thị công thức toán học, thanh tỷ lệ trọng số và kiểm tra tổng trọng số chuẩn hóa.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const MatchingFormulaCard = ({ configs = [], dirtyValues = {} }) => {
  const getVal = (key, fallback) => {
    if (dirtyValues[key]?.value !== undefined) {
      return parseFloat(dirtyValues[key].value) || fallback;
    }
    const found = configs.find((c) => c.key === key);
    return found ? parseFloat(found.value) || fallback : fallback;
  };

  const wDist = getVal('matching_weight_distance', 0.40);
  const wRating = getVal('matching_weight_rating', 0.30);
  const wExp = getVal('matching_weight_experience', 0.20);
  const wRs = getVal('matching_weight_reliability', 0.10);
  const maxDistance = getVal('matching_max_distance_km', 15.0);

  const totalWeight = useMemo(() => {
    return Math.round((wDist + wRating + wExp + wRs) * 100) / 100;
  }, [wDist, wRating, wExp, wRs]);

  const isValidSum = Math.abs(totalWeight - 1.0) < 0.01;

  // Tính tỷ lệ hiển thị trên thanh tiến trình
  const safeSum = totalWeight > 0 ? totalWeight : 1.0;
  const pctDist = Math.round((wDist / safeSum) * 100);
  const pctRating = Math.round((wRating / safeSum) * 100);
  const pctExp = Math.round((wExp / safeSum) * 100);
  const pctRs = Math.round((wRs / safeSum) * 100);

  return (
    <div className="matching-formula-card">
      <div className="formula-header">
        <div className="formula-badge">Thuật toán lõi</div>
        <div className="formula-title-group">
          <h3>Mô Hình Công Thức Tính Điểm Phù Hợp (Matching Score)</h3>
          <p>
            Thuật toán tự động xếp hạng và điều phối nhân sự phù hợp nhất cho từng ca làm việc của nhà tổ chức sự kiện
          </p>
        </div>
        <div className={`formula-status-pill ${isValidSum ? 'valid' : 'invalid'}`}>
          {isValidSum ? 'Tổng trọng số: 100% (Chuẩn)' : `Tổng trọng số: ${Math.round(totalWeight * 100)}% (Cần cân bằng về 100%)`}
        </div>
      </div>

      {/* Math Formula Display */}
      <div className="formula-math-box">
        <span className="formula-highlight">Điểm Đề Xuất (Score)</span>
        <span className="formula-op">=</span>
        <span className="formula-term dist">({wDist.toFixed(2)} × Điểm_Khoảng_Cách)</span>
        <span className="formula-op">+</span>
        <span className="formula-term rating">({wRating.toFixed(2)} × Điểm_Đánh_Giá)</span>
        <span className="formula-op">+</span>
        <span className="formula-term exp">({wExp.toFixed(2)} × Điểm_Kinh_Nghiệm)</span>
        <span className="formula-op">+</span>
        <span className="formula-term rs">({wRs.toFixed(2)} × Điểm_Uy_Tín)</span>
        <span className="formula-op">+</span>
        <span className="formula-term bonus">Điểm_Thưởng (Mới/Quay lại)</span>
      </div>

      {/* Visual Weight Distribution Bar */}
      <div className="formula-bar-container">
        <div className="formula-bar-labels">
          <span>Phân bổ tỷ trọng điểm thành phần:</span>
          <span className="formula-bar-hint">Bán kính quét tối đa: <strong>{maxDistance} km</strong></span>
        </div>
        <div className="formula-bar">
          <div
            className="bar-seg seg-dist"
            style={{ width: `${pctDist}%` }}
            title={`Khoảng cách: ${pctDist}%`}
          >
            {pctDist > 8 && `Khoảng cách ${pctDist}%`}
          </div>
          <div
            className="bar-seg seg-rating"
            style={{ width: `${pctRating}%` }}
            title={`Đánh giá sao: ${pctRating}%`}
          >
            {pctRating > 8 && `Đánh giá ${pctRating}%`}
          </div>
          <div
            className="bar-seg seg-exp"
            style={{ width: `${pctExp}%` }}
            title={`Kinh nghiệm: ${pctExp}%`}
          >
            {pctExp > 8 && `Kinh nghiệm ${pctExp}%`}
          </div>
          <div
            className="bar-seg seg-rs"
            style={{ width: `${pctRs}%` }}
            title={`Điểm uy tín: ${pctRs}%`}
          >
            {pctRs > 8 && `Uy tín ${pctRs}%`}
          </div>
        </div>
      </div>

      {/* 4 Factor Details */}
      <div className="formula-factors-grid">
        <div className="factor-item">
          <div className="factor-dot dot-dist" />
          <div className="factor-text">
            <strong>Khoảng cách (W_dist = {wDist.toFixed(2)})</strong>
            <span>100 - (Khoảng_cách / {maxDistance}) × 100</span>
          </div>
        </div>
        <div className="factor-item">
          <div className="factor-dot dot-rating" />
          <div className="factor-text">
            <strong>Đánh giá sao (W_rating = {wRating.toFixed(2)})</strong>
            <span>(Sao_trung_bình / 5.0) × 100</span>
          </div>
        </div>
        <div className="factor-item">
          <div className="factor-dot dot-exp" />
          <div className="factor-text">
            <strong>Kinh nghiệm (W_exp = {wExp.toFixed(2)})</strong>
            <span>(Số_ca_hoàn_thành / 10) × 100</span>
          </div>
        </div>
        <div className="factor-item">
          <div className="factor-dot dot-rs" />
          <div className="factor-text">
            <strong>Điểm uy tín (W_rs = {wRs.toFixed(2)})</strong>
            <span>Điểm uy tín hiện tại / 100</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MatchingFormulaCard;

