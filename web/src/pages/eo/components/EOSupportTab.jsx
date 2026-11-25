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
          Câu hỏi thường gặp (FAQ)
        </button>

        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'tickets' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('tickets')}
        >
          Phiếu yêu cầu hỗ trợ (Tickets)
        </button>

        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'chat' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('chat')}
        >
          Chat trực tuyến CSKH
        </button>

        <button
          type="button"
          className={`eo-support-tab-btn ${activeSubTab === 'disputes' ? 'active' : ''}`}
          onClick={() => setActiveSubTab('disputes')}
        >
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
