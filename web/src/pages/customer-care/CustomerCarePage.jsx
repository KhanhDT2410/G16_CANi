import { useState } from 'react';
import { DisputeCenterView } from '../../features/disputes';
import { SupportTicketsCenterView } from '../../features/support-tickets';
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
      {/* Tạm thời ẩn view eKYC và Kỹ năng AI
      {activeTab === 'ekyc' && <EkycVerificationView />}
      {activeTab === 'skills' && <SkillVerificationView />}
      */}
    </div>
  );
};

export default CustomerCarePage;
