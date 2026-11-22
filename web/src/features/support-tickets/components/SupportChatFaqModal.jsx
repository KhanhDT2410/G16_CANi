import { useState, useEffect, useMemo } from 'react';
import { faqApi } from '../../faqs/api/faqApi';
import { FAQ_CATEGORY, FAQ_CATEGORY_CONFIG } from '../../faqs/types/faq.constants';
import './SupportChatFaqModal.css';

const DEFAULT_FALLBACK_FAQS = [
  {
    id: 'fb-1',
    category: 'ACCOUNT',
    question: 'Quy trình xác thực định danh (eKYC) trên ứng dụng CANi diễn ra như thế nào?',
    answer: 'Người lao động cần chụp rõ nét hai mặt Căn cước công dân (CCCD) gắn chip và một ảnh chân dung selfie khớp khuôn mặt. Hệ thống VNPT eKYC tích hợp của CANi sẽ tự động trích xuất thông tin OCR và đối soát sinh trắc học để kích hoạt tài khoản trong vòng 1-3 phút.',
  },
  {
    id: 'fb-2',
    category: 'SHIFT',
    question: 'Quy trình quét mã QR Check-in và Check-out tại sự kiện được thực hiện ra sao?',
    answer: 'Trước giờ bắt đầu ca làm 15-30 phút, người lao động có mặt tại địa điểm tổ chức, mở ứng dụng CANi và quét mã QR do Quản lý sự kiện hiển thị. Khi kết thúc ca, người lao động quét mã QR Check-out lần 2 để chốt giờ làm thực tế.',
  },
  {
    id: 'fb-3',
    category: 'PAYMENT',
    question: 'Phương thức thanh toán thù lao trực tiếp (Non-Escrow) trên CANi vận hành như thế nào?',
    answer: 'Sau khi ca làm việc kết thúc và hoàn tất Check-out, Nhà tổ chức sự kiện (EO) có thời hạn 48 giờ để thực hiện chuyển khoản thù lao trực tiếp vào số tài khoản ngân hàng của người lao động và đính kèm ủy nhiệm chi.',
  },
  {
    id: 'fb-4',
    category: 'DISPUTE',
    question: 'Cần chuẩn bị những bằng chứng nào khi tạo khiếu nại tranh chấp thù lao hoặc giờ làm việc?',
    answer: 'Bạn cần cung cấp: 1) Ảnh chụp màn hình tin nhắn hoặc thỏa thuận với Quản lý sự kiện; 2) Biên bản xác nhận giờ làm thực tế tại sự kiện (nếu có); 3) Sao kê tài khoản ngân hàng thể hiện chưa nhận được tiền trong vòng 48 giờ.',
  },
];

/**
 * Bảng chọn câu hỏi thường gặp FAQ tích hợp trong khung Chat hỗ trợ.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export const SupportChatFaqModal = ({ isOpen, onClose, onSelectFaq }) => {
  const [faqs, setFaqs] = useState(DEFAULT_FALLBACK_FAQS);
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    let isMounted = true;
    setLoading(true);

    faqApi.getPublicFaqs()
      .then((data) => {
        if (isMounted && Array.isArray(data) && data.length > 0) {
          setFaqs(data);
        }
      })
      .catch(() => {
        // Giữ fallback FAQs
      })
      .finally(() => {
        if (isMounted) setLoading(false);
      });

    return () => { isMounted = false; };
  }, [isOpen]);

  const filteredFaqs = useMemo(() => {
    return faqs.filter((f) => {
      if (activeCategory !== 'ALL' && f.category !== activeCategory) return false;
      if (searchTerm.trim()) {
        const term = searchTerm.toLowerCase();
        const qMatch = (f.question || f.q || '').toLowerCase().includes(term);
        const aMatch = (f.answer || f.a || '').toLowerCase().includes(term);
        if (!qMatch && !aMatch) return false;
      }
      return true;
    });
  }, [faqs, activeCategory, searchTerm]);

  if (!isOpen) return null;

  return (
    <div className="support-faq-modal-overlay" onClick={onClose}>
      <div className="support-faq-modal-panel animate-scale-in" onClick={(e) => e.stopPropagation()}>
        <div className="support-faq-modal-header">
          <div className="support-faq-modal-title">
            <span>Câu hỏi thường gặp (FAQ)</span>
          </div>
          <button type="button" className="support-faq-modal-close" onClick={onClose} aria-label="Đóng">
            ✕
          </button>
        </div>

        <div className="support-faq-search-wrap">
          <input
            type="text"
            className="support-faq-search-input"
            placeholder="Tìm nhanh câu hỏi trợ giúp..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        <div className="support-faq-category-pills">
          <button
            type="button"
            className={`support-faq-cat-btn ${activeCategory === 'ALL' ? 'active' : ''}`}
            onClick={() => setActiveCategory('ALL')}
          >
            Tất cả
          </button>
          {Object.keys(FAQ_CATEGORY).map((catKey) => (
            <button
              key={catKey}
              type="button"
              className={`support-faq-cat-btn ${activeCategory === catKey ? 'active' : ''}`}
              onClick={() => setActiveCategory(catKey)}
            >
              {FAQ_CATEGORY_CONFIG[catKey]?.label || catKey}
            </button>
          ))}
        </div>

        <div className="support-faq-items-list">
          {loading ? (
            <div className="support-faq-empty">Đang tải danh mục câu hỏi...</div>
          ) : filteredFaqs.length === 0 ? (
            <div className="support-faq-empty">Không tìm thấy câu hỏi phù hợp.</div>
          ) : (
            filteredFaqs.map((item, idx) => (
              <div
                key={item.id || idx}
                className="support-faq-item-row"
                onClick={() => onSelectFaq(item)}
                role="button"
                tabIndex={0}
              >
                <div className="support-faq-item-q">{item.question || item.q}</div>
                {item.summary && <div className="support-faq-item-summary">{item.summary}</div>}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};

export default SupportChatFaqModal;
