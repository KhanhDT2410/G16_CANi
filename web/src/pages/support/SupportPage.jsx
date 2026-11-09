import { useState } from 'react';
import { SupportTicketsCenterView } from '../../features/support-tickets';
import { FaqPublicList, useFaqs } from '../../features/faqs';

export const SupportPage = () => {
  const [tab, setTab] = useState('faqs');
  const { publicFaqs, loading, readPublicFaq } = useFaqs('public');

  return (
    <div
      className="support-page-wrapper animate-fade-in"
      style={{ padding: '1.5rem 2rem', maxWidth: '1440px', margin: '0 auto' }}
    >
      <div
        style={{
          display: 'flex',
          gap: '0.75rem',
          marginBottom: '1.5rem',
          borderBottom: '1px solid #e2e8f0',
          paddingBottom: '0.5rem',
        }}
      >
        <button
          type="button"
          onClick={() => setTab('faqs')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: tab === 'faqs' ? '1px solid #2563eb' : '1px solid transparent',
            background: tab === 'faqs' ? '#eff6ff' : 'transparent',
            color: tab === 'faqs' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Câu Hỏi Thường Gặp (Help Center)
        </button>
        <button
          type="button"
          onClick={() => setTab('tickets')}
          style={{
            padding: '0.6rem 1.25rem',
            fontWeight: 600,
            fontSize: '0.92rem',
            borderRadius: '6px',
            border: tab === 'tickets' ? '1px solid #2563eb' : '1px solid transparent',
            background: tab === 'tickets' ? '#eff6ff' : 'transparent',
            color: tab === 'tickets' ? '#2563eb' : '#64748b',
            cursor: 'pointer',
            transition: 'all 0.2s',
          }}
        >
          Phiếu Hỗ Trợ (Tickets)
        </button>
      </div>

      {tab === 'faqs' && (
        <FaqPublicList
          publicFaqs={publicFaqs}
          loading={loading}
          onReadFaq={readPublicFaq}
        />
      )}
      {tab === 'tickets' && <SupportTicketsCenterView />}
    </div>
  );
};

export default SupportPage;
