import { useState } from 'react';
import { FaqPublicList, useFaqs } from '../../../features/faqs';
import { SupportTicketsCenterView } from '../../../features/support-tickets';
import EOSupportChatPanel from './EOSupportChatPanel';
import EODisputesPanel from './EODisputesPanel';
import './EOSupportTab.css';

/**
 * Trung Tâm Trợ Giúp & Hỗ Trợ Kỹ Thuật dành cho Nhà tổ chức sự kiện (EO Support Workspace).
 * Khóa vai trò theo đối tượng EVENT_ORGANIZER (chỉ hiển thị câu hỏi của Nhà tổ chức & câu hỏi chung).
 * Tích hợp 3 phân hệ: Câu hỏi thường gặp (FAQ), Phiếu hỗ trợ (Tickets), Chat trực tuyến CSKH 24/7.
 * Tiêu chuẩn < 200 dòng theo AGENTS.md.
 */
export default function EOSupportTab() {
  const [activeSubTab, setActiveSubTab] = useState('faq');
  const { publicFaqs, loading, readPublicFaq } = useFaqs('public', 'EVENT_ORGANIZER');

  return (
    <div className="eo-support-container animate-fade-in">
      <div className="eo-support-nav-bar">
        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'faq' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('faq')}
        >
          <span className="eo-support-tab-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
              <line x1="12" y1="17" x2="12.01" y2="17" />
            </svg>
          </span>
          Câu hỏi thường gặp (FAQ)
        </button>

        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'tickets' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('tickets')}
        >
          <span className="eo-support-tab-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
            </svg>
          </span>
          Phiếu yêu cầu hỗ trợ (Tickets)
        </button>

        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'chat' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('chat')}
        >
          <span className="eo-support-tab-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>
          </span>
          Chat trực tuyến CSKH
        </button>

        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'disputes' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('disputes')}
        >
          <span className="eo-support-tab-icon">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
          </span>
          Khiếu nại & Tranh chấp
        </button>
      </div>

      <div className="eo-support-tab-content">
        {activeSubTab === 'faq' && (
          <FaqPublicList
            publicFaqs={publicFaqs}
            loading={loading}
            onReadFaq={readPublicFaq}
            lockedRole="EVENT_ORGANIZER"
            title="Trung Tâm Trợ Giúp Nhà Tổ Chức Sự Kiện"
            subtitle="Giải đáp thắc mắc về tạo sự kiện, điều phối nhân sự, đối soát thù lao và quản lý ca làm"
          />
        )}

        {activeSubTab === 'tickets' && (
          <div className="animate-fade-in-up">
            <SupportTicketsCenterView />
          </div>
        )}

        {activeSubTab === 'chat' && (
          <div className="animate-fade-in-up">
            <EOSupportChatPanel />
          </div>
        )}

        {activeSubTab === 'disputes' && (
          <div className="animate-fade-in-up">
            <EODisputesPanel />
          </div>
        )}
      </div>
    </div>
  );
}
