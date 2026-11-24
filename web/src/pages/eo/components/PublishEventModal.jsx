import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
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
  const navigate = useNavigate();
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
          console.warn('Không thể lấy thông tin gói cước hiện tại:', err);
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

  const isInviteMode = event.mode === 'INVITE';
  const remainingQuota = currentSub?.remainingEvents ?? 0;
  const hasActiveQuota = currentSub && currentSub.status === 'ACTIVE' && remainingQuota > 0;
  const isOutOfQuota = !isInviteMode && currentSub && currentSub.subscriptionId && (remainingQuota <= 0 || currentSub.status === 'EXPIRED');
  const hasNoPlan = !isInviteMode && (!currentSub || !currentSub.subscriptionId);
  const planName = currentSub?.planName || 'Chưa có gói cước';

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

        {/* 1. Tóm tắt sự kiện */}
        <div style={{ marginBottom: '1.25rem' }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '0.75rem',
            }}
          >
            <span style={{ fontSize: '1.05rem', fontWeight: 700, color: '#1e293b' }}>
              {event.name}
            </span>
            <span
              style={{
                backgroundColor: '#f1f5f9',
                color: '#475569',
                padding: '0.2rem 0.5rem',
                borderRadius: '6px',
                fontSize: '0.75rem',
                fontWeight: 600,
              }}
            >
              {totalShifts} Ca Làm Việc
            </span>
          </div>

          <div
            style={{
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: '10px',
              padding: '1rem',
            }}
          >
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
              <div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '0.25rem' }}>
                  Tổng nhân sự cần tuyển:
                </div>
                <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
                  {totalWorkers} <span style={{ fontSize: '0.85rem', fontWeight: 500, color: '#64748b' }}>người</span>
                </div>
              </div>
              <div>
                <div style={{ fontSize: '0.8rem', color: '#64748b', marginBottom: '0.25rem' }}>
                  Dự toán thù lao nhân sự:
                </div>
                <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#2563eb' }}>
                  {new Intl.NumberFormat('vi-VN').format(totalCost)} <span style={{ fontSize: '0.85rem', fontWeight: 500 }}>VNĐ</span>
                </div>
              </div>
            </div>

            <div
              style={{
                marginTop: '0.75rem',
                paddingTop: '0.75rem',
                borderTop: '1px solid #f1f5f9',
                fontSize: '0.8rem',
                color: '#64748b',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
              }}
            >
              <div style={{ color: '#64748b' }}>
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
          ) : isInviteMode ? (
            /* TRƯỜNG HỢP 0: SỰ KIỆN CHẾ ĐỘ MỜI NGƯỜI -> 100% MIỄN PHÍ, KHÔNG TRỪ QUOTA */
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
                  CHẾ ĐỘ MỜI TRỰC TIẾP (MIỄN PHÍ)
                </span>
                <span style={{ fontSize: '0.85rem', color: '#15803d', fontWeight: 600 }}>
                  Không tiêu hao Quota
                </span>
              </div>
              <p style={{ margin: '0.5rem 0 0', fontSize: '0.85rem', color: '#374151', lineHeight: 1.5 }}>
                Sự kiện này được xuất bản ở chế độ <strong>Mời trực tiếp (Invite)</strong>. Bạn có thể tự do tìm kiếm ứng viên và gửi lời mời mà <strong>hoàn toàn không mất phí và không trừ hạn mức gói</strong>.
              </p>
              <div style={{ marginTop: '0.5rem', fontSize: '0.775rem', color: '#16a34a', fontWeight: 600 }}>
                Miễn phí tìm kiếm và mời nhân sự không giới hạn.
              </div>
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
                    borderRadius: '4px',
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
                Khi xác nhận, hệ thống sẽ <strong>trừ 1 lượt sự kiện</strong> trong gói cước của bạn và tự động kích hoạt Matching Engine để kết nối các Worker phù hợp nhất trong bán kính 15km.
              </p>
              <div style={{ marginTop: '0.5rem', fontSize: '0.775rem', color: '#16a34a', fontWeight: 600 }}>
                Bao gồm Matching Engine, Job Radar di động, FCM Push & Hỗ trợ biến động ca làm.
              </div>
            </div>
          ) : isOutOfQuota ? (
            /* TRƯỜNG HỢP 2: HẾT LƯỢT SỰ KIỆN TRONG GÓI -> THÔNG BÁO VÀ CHUYỂN HƯỚNG MUA GÓI */
            <div
              style={{
                backgroundColor: '#fffbeb',
                border: '1px solid #fde68a',
                borderRadius: '12px',
                padding: '1.15rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '0.9rem', fontWeight: 700, color: '#b45309' }}>
                  HẠN MỨC SỰ KIỆN ĐÃ HẾT (0 LƯỢT CÒN LẠI)
                </span>
              </div>
              <p style={{ margin: '0.25rem 0 0.5rem', fontSize: '0.85rem', color: '#92400e', lineHeight: 1.5 }}>
                Bạn đã sử dụng hết số lượt tạo sự kiện của gói dịch vụ ({currentSub?.eventsUsed || 0}/{currentSub?.maxEvents || 0} sự kiện). Vui lòng nâng cấp gói tại mục <strong>Gói Dịch Vụ</strong> để tiếp tục xuất bản sự kiện có Matching Engine.
              </p>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '0.75rem', paddingTop: '0.75rem', borderTop: '1px dashed #fcd34d' }}>
                <span style={{ fontSize: '0.8rem', color: '#78350f' }}>
                  Gói dịch vụ: <strong>{planName}</strong>
                </span>
                <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#dc2626' }}>
                  0 lượt khả dụng
                </span>
              </div>
            </div>
          ) : (
            /* TRƯỜNG HỢP 3: CHƯA CÓ GÓI DỊCH VỤ MATCHING */
            <div
              style={{
                backgroundColor: '#eff6ff',
                border: '1px solid #bfdbfe',
                borderRadius: '12px',
                padding: '1.15rem',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                <span style={{ fontSize: '1.25rem' }}>⚡</span>
                <span style={{ fontSize: '0.9rem', fontWeight: 700, color: '#1e40af' }}>
                  YÊU CẦU GÓI DỊCH VỤ MATCHING ENGINE
                </span>
              </div>
              <p style={{ margin: '0.25rem 0 0.5rem', fontSize: '0.85rem', color: '#334155', lineHeight: 1.5 }}>
                Sự kiện này đang chọn chế độ <strong>Ghép đôi tự động (Matching Engine)</strong>. Bạn cần kích hoạt một gói dịch vụ để thuật toán tự động kết nối và đề xuất ứng viên phù hợp.
              </p>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: '0.75rem', paddingTop: '0.75rem', borderTop: '1px dashed #cbd5e1' }}>
                <span style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Trạng thái: <strong>Chưa kích hoạt gói dịch vụ</strong>
                </span>
                <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#2563eb' }}>
                  Cần đăng ký gói
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
          {!isInviteMode && (isOutOfQuota || hasNoPlan) ? (
            <button
              type="button"
              className="eo-btn-primary"
              style={{
                background: '#2563eb',
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
              onClick={() => {
                onClose();
                navigate('/eo?tab=subscription');
              }}
            >
              <span>{hasNoPlan ? 'Đăng Ký Gói Dịch Vụ Ngay' : 'Nâng Cấp Gói Dịch Vụ'}</span>
            </button>
          ) : (
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
              <span>{isInviteMode ? 'Xuất Bản Sự Kiện (Miễn Phí)' : 'Xuất Bản & Trừ 1 Lượt Gói'}</span>
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default PublishEventModal;
