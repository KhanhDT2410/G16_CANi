import React, { useState, useEffect } from 'react';
import { eoService } from '../../../services/eoService';

/**
 * PublishEventModal
 * Modal xác nhận xuất bản sự kiện & kích hoạt Matching Engine theo mô hình GÓI CƯỚC (Subscription Quota)
 * ĐÃ LOẠI BỎ hoàn toàn cơ chế tính phí lắt nhắt theo đầu người (3.000đ/slot).
 */
const PublishEventModal = ({
  isOpen,
  onClose,
  onExecutePublish,
  event,
}) => {
  const [currentSub, setCurrentSub] = useState(null);
  const [loadingSub, setLoadingSub] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setLoadingSub(true);
      eoService.getCurrentSubscription()
        .then((res) => {
          setCurrentSub(res || null);
        })
        .catch((err) => {
          console.warn('Không thể lấy thông tin gói cước hiện tại, áp dụng chế độ trải nghiệm:', err);
          setCurrentSub(null);
        })
        .finally(() => {
          setLoadingSub(false);
        });
    }
  }, [isOpen]);

  if (!isOpen || !event) return null;

  const totalWorkers = event.totalRequiredWorkers || 0;
  const totalShifts = event.totalShifts || 0;
  const totalCost = event.totalEstimatedCost || 0;

  const hasActiveQuota = currentSub && currentSub.status === 'ACTIVE' && (currentSub.remainingEvents > 0);
  const remainingQuota = currentSub?.remainingEvents ?? 0;
  const planName = currentSub?.planName || (currentSub?.planType === 'MATCHING_PLUS' ? 'Gói Tự Động Ghép Đôi' : 'Gói Hội Viên CANi');

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box publish-modal-box animate-fade-in-up" style={{ maxWidth: '620px' }}>
        <div className="publish-modal-header">
          <div>
            <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', margin: '0 0 0.25rem' }}>
              Xác Nhận Xuất Bản Sự Kiện
            </h3>
            <p style={{ margin: 0, fontSize: '0.875rem', color: '#64748b' }}>
              Hệ thống CANi sẽ tự động kết nối và đề xuất nhân sự phù hợp theo bán kính
            </p>
          </div>
        </div>

        {/* 1. Thông tin tóm tắt sự kiện */}
        <div className="publish-summary-card">
          <div className="publish-summary-title" style={{ fontSize: '1rem', fontWeight: 700, color: '#1e293b' }}>
            {event.name}
          </div>
          <div className="publish-grid">
            <div className="publish-grid-item">
              <span className="lbl">Số ca làm việc:</span>
              <span className="val">{totalShifts} ca</span>
            </div>
            <div className="publish-grid-item">
              <span className="lbl">Tổng nhân sự tuyển:</span>
              <span className="val highlight" style={{ color: '#2563eb', fontWeight: 700 }}>
                {totalWorkers} người
              </span>
            </div>
            <div className="publish-grid-item full-width">
              <span className="lbl">Tổng dự toán thù lao chi trả nhân sự:</span>
              <span className="val wage" style={{ color: '#059669', fontSize: '1.1rem', fontWeight: 800 }}>
                {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(totalCost)}
              </span>
              <div className="sub-note" style={{ color: '#94a3b8', fontSize: '0.75rem', marginTop: '0.25rem' }}>
                (Ban tổ chức thanh toán trực tiếp cho nhân sự sau khi kết thúc ca làm việc)
              </div>
            </div>
          </div>
        </div>

        {/* 2. Trạng thái Hạn mức Gói Cước & Chính sách Khuyến Mãi */}
        <div style={{ marginBottom: '1.5rem' }}>
          {loadingSub ? (
            <div style={{ padding: '1rem', textAlign: 'center', color: '#64748b', fontSize: '0.875rem' }}>
              Đang kiểm tra hạn mức gói dịch vụ...
            </div>
          ) : hasActiveQuota ? (
            /* TRƯỜNG HỢP 1: ĐANG CÓ GÓI HỘI VIÊN VÀ CÒN LƯỢT */
            <div
              style={{
                backgroundColor: '#f0fdf4',
                border: '1px solid #bbf7d0',
                borderRadius: '12px',
                padding: '1.15rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                <span
                  style={{
                    backgroundColor: '#dcfce7',
                    color: '#166534',
                    fontSize: '0.775rem',
                    fontWeight: 700,
                    padding: '0.25rem 0.65rem',
                    borderRadius: '20px',
                    textTransform: 'uppercase',
                  }}
                >
                  Đang Sử Dụng: {planName}
                </span>
                <span style={{ fontSize: '0.85rem', color: '#15803d', fontWeight: 600 }}>
                  Còn {remainingQuota} lượt sự kiện
                </span>
              </div>
              <p style={{ margin: '0.5rem 0 0', fontSize: '0.85rem', color: '#374151', lineHeight: 1.5 }}>
                Khi xác nhận, hệ thống sẽ <strong>trừ 1 lượt sự kiện</strong> trong gói cước của bạn và tự động kết nối các Worker phù hợp nhất trong bán kính 15km.
              </p>
              <div style={{ marginTop: '0.5rem', fontSize: '0.775rem', color: '#16a34a', fontWeight: 600 }}>
                ✓ Miễn phí toàn bộ phí điều phối & tìm kiếm theo chính sách gói.
              </div>
            </div>
          ) : (
            /* TRƯỜNG HỢP 2: CHƯA CÓ GÓI HOẶC ĐÃ HẾT LƯỢT -> BẬT CHÍNH SÁCH ƯU ĐÃI CHÀO MỪNG */
            <div
              style={{
                backgroundColor: '#eff6ff',
                border: '1px solid #bfdbfe',
                borderRadius: '12px',
                padding: '1.15rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '1.25rem' }}>🎁</span>
                <span style={{ fontSize: '0.9rem', fontWeight: 700, color: '#1e40af' }}>
                  ƯU ĐÃI CHÀO MỪNG: TẶNG 1 SỰ KIỆN TRẢI NGHIỆM MIỄN PHÍ
                </span>
              </div>
              <p style={{ margin: '0.25rem 0 0.5rem', fontSize: '0.85rem', color: '#334155', lineHeight: 1.5 }}>
                Chào mừng bạn đến với CANi! Bạn được <strong>tặng miễn phí 100%</strong> lượt xuất bản và tự động kết nối nhân sự cho sự kiện này để trải nghiệm tốc độ tìm người.
              </p>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '0.75rem', paddingTop: '0.75rem', borderTop: '1px dashed #cbd5e1' }}>
                <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Phí kích hoạt sự kiện: <strong style={{ color: '#059669', textDecoration: 'line-through' }}>80.000 VNĐ</strong>
                </span>
                <span style={{ fontSize: '0.95rem', fontWeight: 800, color: '#16a34a' }}>
                  0 VNĐ (Miễn Phí)
                </span>
              </div>
            </div>
          )}
        </div>

        {/* Footer Actions */}
        <div className="eo-modal-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
          <button
            type="button"
            className="eo-btn-secondary"
            onClick={onClose}
            style={{ padding: '0.6rem 1.25rem', borderRadius: '8px', cursor: 'pointer' }}
          >
            Để Sau
          </button>
          <button
            type="button"
            className="eo-btn-primary"
            style={{
              background: '#10b981',
              color: '#ffffff',
              padding: '0.6rem 1.5rem',
              borderRadius: '8px',
              fontWeight: 700,
              cursor: 'pointer',
              border: 'none',
              display: 'flex',
              alignItems: 'center',
              gap: '0.5rem',
            }}
            onClick={onExecutePublish}
          >
            <span>{hasActiveQuota ? 'Xuất Bản & Trừ 1 Lượt Gói' : 'Kích Hoạt Miễn Phí (0 VNĐ)'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default PublishEventModal;
