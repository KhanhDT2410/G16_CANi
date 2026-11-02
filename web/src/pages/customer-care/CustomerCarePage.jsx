import { DisputeCenterView } from '../../features/disputes';
import './CustomerCarePage.css';

export const CustomerCarePage = () => {
  return (
    <div className="customer-care-page-wrapper animate-fade-in" style={{ padding: '1.5rem 2rem', maxWidth: '1440px', margin: '0 auto' }}>
      <DisputeCenterView />
    </div>
  );
};

export default CustomerCarePage;
