import { SupportTicketsCenterView } from '../../features/support-tickets';

export const SupportPage = () => {
  return (
    <div
      className="support-page-wrapper animate-fade-in"
      style={{ padding: '1.5rem 2rem', maxWidth: '1440px', margin: '0 auto' }}
    >
      <SupportTicketsCenterView />
    </div>
  );
};

export default SupportPage;

