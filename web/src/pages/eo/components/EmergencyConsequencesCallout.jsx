import React from 'react';

/**
 * EmergencyConsequencesCallout (Ticket EO-010)
 * Khối cảnh báo chi tiết các hệ quả thực tế khi kích hoạt điều phối khẩn cấp.
 */
export default function EmergencyConsequencesCallout({
  radiusKm,
  wageFormatted,
  skillRelaxation = 'NONE',
  emergencyBonus = 0,
}) {
  const bonusFormatted =
    Number(emergencyBonus) > 0
      ? new Intl.NumberFormat('vi-VN').format(Number(emergencyBonus)) + ' đ/suất'
      : null;

  const skillDesc =
    skillRelaxation === 'PREFER_ONLY'
      ? 'Không chặn ứng viên chưa gắn kỹ năng phổ thông (vẫn ưu tiên điểm người có kỹ năng & giữ cứng chứng chỉ an toàn).'
      : skillRelaxation === 'LOWER_TIER'
      ? 'Giữ nguyên kỹ năng bắt buộc, hạ 1 bậc mức xác minh hồ sơ (trừ kỹ năng đòi hỏi chứng chỉ).'
      : 'Giữ nguyên toàn bộ kỹ năng, chứng chỉ & sàn uy tín; chỉ mở khóa nhận ca ngoài lịch rảnh tuần cố định cho Worker đang Online.';

  return (
    <div
      style={{
        background: '#fffbeb',
        border: '1.5px solid #fde68a',
        borderRadius: 10,
        padding: '0.85rem 1rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.45rem',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontWeight: 800, color: '#92400e', fontSize: '0.88rem' }}>
        <span>Các hệ quả khi mở rộng tìm kiếm khẩn cấp:</span>
      </div>
      <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.81rem', color: '#78350f', lineHeight: 1.5 }}>
        <li>
          <strong>Mở rộng bán kính địa lý ({radiusKm} km):</strong> Quét Worker đang Online trong phạm vi {radiusKm} km (bỏ qua trần bán kính cá nhân) và mở ứng tuyển trực tiếp trên Job Radar.
        </li>
        <li>
          <strong>Chính sách kỹ năng &amp; an toàn:</strong> {skillDesc}
        </li>
        <li>
          <strong>Thời hạn đề xuất:</strong> Lời mời gửi đến Worker hết hạn sau <strong>30 phút</strong>; hệ thống tự tắt khẩn cấp ngay khi chốt đủ nhân sự.
        </li>
        <li>
          <strong>Thù lao:</strong> Giữ mức <strong>{wageFormatted}</strong>
          {bonusFormatted ? (
            <>
              {' '}và cộng thêm <strong>{bonusFormatted}</strong> cho nhân sự nhận ca trong đợt khẩn cấp (không ảnh hưởng người đã chốt trước đó).
            </>
          ) : (
            <> theo thỏa thuận ban đầu.</>
          )}
        </li>
      </ul>
    </div>
  );
}
