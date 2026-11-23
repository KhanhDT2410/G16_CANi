import React from 'react';

/**
 * EmergencyConsequencesCallout (Ticket EO-010)
 * Khối cảnh báo chi tiết các hệ quả khi mở rộng tìm kiếm khẩn cấp.
 */
export default function EmergencyConsequencesCallout({ radiusKm, wageFormatted }) {
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
        <span>⚠️</span>
        <span>Các hệ quả khi mở rộng tìm kiếm khẩn cấp:</span>
      </div>
      <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.81rem', color: '#78350f', lineHeight: 1.5 }}>
        <li>
          <strong>Mở rộng bán kính địa lý ({radiusKm} km):</strong> Hệ thống quét Worker ở xa điểm tổ chức hơn, cần lưu ý thời gian di chuyển của Worker khi sát giờ.
        </li>
        <li>
          <strong>Nới lỏng điều kiện kinh nghiệm theo Preference:</strong> Ưu tiên ghép nhanh với Worker đang Online, áp dụng Fairness Boost cho ứng viên ít ca hơn (vẫn bảo đảm đáp ứng kỹ năng bắt buộc).
        </li>
        <li>
          <strong>Thời hạn đề xuất:</strong> Lời mời gửi đến Worker sẽ tự động hết hạn sau <strong>30 phút</strong> để tránh giữ chỗ ảo.
        </li>
        <li>
          <strong>Thù lao giữ nguyên:</strong> Giữ cố định mức <strong>{wageFormatted}</strong> theo thỏa thuận ban đầu.
        </li>
      </ul>
    </div>
  );
}
