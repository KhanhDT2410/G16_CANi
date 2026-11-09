import { useState } from 'react';
import { DisputeCenterView } from '../../features/disputes';
import { SupportTicketsCenterView } from '../../features/support-tickets';
import { FaqView } from '../../features/faqs';
import { RsAppealsView } from '../../features/rs-appeals';
import { NoShowView } from '../../features/no-shows';
// Tạm thời ẩn tính năng eKYC và Thẩm định kỹ năng AI theo yêu cầu nhóm để xem xét thêm
// import { EkycVerificationView } from '../../features/ekyc-verification';
// import { SkillVerificationView } from '../../features/skill-verification';
import './CustomerCarePage.css';

export const CustomerCarePage = () => {
  const [activeTab, setActiveTab] = useState('tickets');

  return (
    <div
      className="customer-care-page-wrapper animate-fade-in"
      style={{ padding: '1.5rem 2rem', maxWidth: '1440px', margin: '0 auto' }}
    >
      <div
        style={{
          display: 'flex',
          gap: '0.75rem',
          marginBottom: '1.5rem',
          borderBottom: '1px solid #e2e8f0',
          paddingBottom: '0.5rem',
          flexWrap: 'wrap',
        }}
      >
        <button
          type="button"
          onClick={() => setActiveTab('tickets')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'tickets' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'tickets' ? '#eff6ff' : 'transparent',
            color: activeTab === 'tickets' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Phiếu Hỗ Trợ Khách Hàng (SCR-25 / SCR-26)
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('disputes')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'disputes' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'disputes' ? '#eff6ff' : 'transparent',
            color: activeTab === 'disputes' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Tranh Chấp & Khiếu Nại (Disputes)
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('faqs')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'faqs' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'faqs' ? '#eff6ff' : 'transparent',
            color: activeTab === 'faqs' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Câu Hỏi Thường Gặp (FAQ)
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('appeals')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'appeals' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'appeals' ? '#eff6ff' : 'transparent',
            color: activeTab === 'appeals' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Kháng Cáo Điểm RS (UC-94)
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('noshows')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'noshows' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'noshows' ? '#eff6ff' : 'transparent',
            color: activeTab === 'noshows' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Vi Phạm No-Show (UC-88)
        </button>

        {/* Tạm thời ẩn 2 nút Hàng đợi eKYC và Kỹ năng AI theo yêu cầu nhóm để xem xét thêm */}
        {/*
        <button
          type="button"
          onClick={() => setActiveTab('ekyc')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'ekyc' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'ekyc' ? '#eff6ff' : 'transparent',
            color: activeTab === 'ekyc' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Hàng Đợi eKYC
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('skills')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: activeTab === 'skills' ? '1px solid #2563eb' : '1px solid transparent',
            background: activeTab === 'skills' ? '#eff6ff' : 'transparent',
            color: activeTab === 'skills' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Hàng Đợi Kỹ Năng AI
        </button>
        */}
      </div>

      {activeTab === 'tickets' && <SupportTicketsCenterView />}
      {activeTab === 'disputes' && <DisputeCenterView />}
      {activeTab === 'faqs' && <FaqView />}
      {activeTab === 'appeals' && <RsAppealsView />}
      {activeTab === 'noshows' && <NoShowView onNavigateAppeal={() => setActiveTab('appeals')} />}
      {/* Tạm thời ẩn view eKYC và Kỹ năng AI
      {activeTab === 'ekyc' && <EkycVerificationView />}
      {activeTab === 'skills' && <SkillVerificationView />}
      */}
    </div>
  );
};

export default CustomerCarePage;
