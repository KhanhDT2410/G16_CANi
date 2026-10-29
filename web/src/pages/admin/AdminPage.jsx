import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { apiClient } from '../../services/api';
import { showToast } from '../../components/common/Toast';
import { AdminDashboard } from '../../features/admin-dashboard';
import { UserManagement } from '../../features/user-management';
import { RbacManagement } from '../../features/rbac-management';
import { RevenueDashboard } from '../../features/revenue-dashboard';
import { SystemConfig } from '../../features/system-configuration';
import './AdminPage.css';

const AdminPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab') || 'dashboard';
  const [activeTab, setActiveTab] = useState(initialTab);
  const [ekycQueue, setEkycQueue] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const tabParam = searchParams.get('tab');
    if (tabParam && tabParam !== activeTab) {
      setActiveTab(tabParam);
    }
  }, [searchParams, activeTab]);

  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setSearchParams({ tab });
  };

  useEffect(() => {
    if (activeTab === 'ekyc') {
      loadEkycQueue();
    }
  }, [activeTab]);

  const loadEkycQueue = async () => {
    setLoading(true);
    try {
      const response = await apiClient.get('/v1/admin/ekyc/queue');
      setEkycQueue(response.data?.data || []);
    } catch {
      // Mock Fallback Data cho Demo
      setEkycQueue([
        {
          verificationId: 'ekyc-q01',
          workerId: 'w-101',
          workerName: 'Nguyễn Văn Minh',
          cccdNumberMasked: '079201******789',
          fullName: 'NGUYỄN VĂN MINH',
          dob: '20/04/2000',
          residenceAddress: 'Phường Đa Kao, Quận 1, TP. Hồ Chí Minh',
          faceMatchScore: 78.5,
          ocrConfidence: 0.88,
          frontImageUrl: 'https://images.unsplash.com/photo-1544717305-2782549b5136?w=400&q=80',
          backImageUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80',
          selfieImageUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&q=80',
          routingDecision: 'MANUAL_REVIEW',
          routingReason: 'Ảnh selfie có độ tương đồng 78.5% (ngưỡng 80%), cần người đối soát mắt để duyệt',
          status: 'PENDING_REVIEW',
        },
        {
          verificationId: 'ekyc-q02',
          workerId: 'w-102',
          workerName: 'Phạm Thị Mai',
          cccdNumberMasked: '001198******456',
          fullName: 'PHẠM THỊ MAI',
          dob: '10/11/1998',
          residenceAddress: 'Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội',
          faceMatchScore: 72.0,
          ocrConfidence: 0.91,
          frontImageUrl: 'https://images.unsplash.com/photo-1544717305-2782549b5136?w=400&q=80',
          backImageUrl: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80',
          selfieImageUrl: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=400&q=80',
          routingDecision: 'MANUAL_REVIEW',
          routingReason: 'Ảnh thẻ CCCD bị lóa sáng nhẹ ở góc phải, cần Admin kiểm tra',
          status: 'PENDING_REVIEW',
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleAdminReview = async (verificationId, decision) => {
    const notes = decision === 'APPROVED' ? 'Đã đối soát mắt người rõ ràng.' : 'Ảnh mờ hoặc không trùng khớp.';
    try {
      await apiClient.post(`/v1/admin/ekyc/${verificationId}/review`, {
        decision,
        reviewNotes: notes,
      });
    } catch {
      // Mock thành công cho demo
    }

    setEkycQueue((prev) => prev.filter((item) => item.verificationId !== verificationId));
    showToast(
      decision === 'APPROVED'
        ? 'Đã phê duyệt eKYC thành công! Worker đã được cấp tích xanh.'
        : 'Đã từ chối hồ sơ eKYC.',
      decision === 'APPROVED' ? 'success' : 'warning'
    );
  };

  return (
    <div className="admin-container">
      {/* Navigation Tabs */}
      <div className="admin-tabs">
        <button
          className={`admin-tab-btn ${activeTab === 'dashboard' ? 'active' : ''}`}
          onClick={() => handleTabChange('dashboard')}
        >
          Dashboard Tổng Quan
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'users' ? 'active' : ''}`}
          onClick={() => handleTabChange('users')}
        >
          Quản Lý Người Dùng
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'rbac' ? 'active' : ''}`}
          onClick={() => handleTabChange('rbac')}
        >
          Phân Quyền & Vai Trò
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'revenue' ? 'active' : ''}`}
          onClick={() => handleTabChange('revenue')}
        >
          Doanh Thu Gói VNPay
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'ekyc' ? 'active' : ''}`}
          onClick={() => handleTabChange('ekyc')}
        >
          Hàng Đợi eKYC ({ekycQueue.length})
        </button>
        <button
          className={`admin-tab-btn ${activeTab === 'config' ? 'active' : ''}`}
          onClick={() => handleTabChange('config')}
        >
          Cấu Hình Hệ Thống
        </button>
      </div>

      {/* Tab: Dashboard (W-38) */}
      {activeTab === 'dashboard' && <AdminDashboard />}

      {/* Tab: User Management (W-39) */}
      {activeTab === 'users' && <UserManagement />}

      {/* Tab: RBAC Roles & Permissions (W-40) */}
      {activeTab === 'rbac' && <RbacManagement />}

      {/* Tab: Revenue Dashboard (W-34) */}
      {activeTab === 'revenue' && <RevenueDashboard />}

      {/* Tab: System Configuration (W-41) */}
      {activeTab === 'config' && <SystemConfig />}

      {/* Tab: eKYC Queue (giữ nguyên) */}
      {activeTab === 'ekyc' && (
        <div className="admin-card-list" style={{ marginTop: '1.5rem' }}>
          {loading ? (
            <div style={{ padding: '1rem 0' }}>
              <div className="skeleton" style={{ height: '320px', borderRadius: '14px', marginBottom: '1rem' }} />
              <div className="skeleton" style={{ height: '320px', borderRadius: '14px' }} />
            </div>
          ) : ekycQueue.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '3rem', background: '#fff', borderRadius: '0.75rem' }}>
              <p style={{ color: '#059669', fontWeight: 'bold' }}>
                ✓ Tuyệt vời! Hiện tại không còn hồ sơ eKYC nào đang chờ duyệt.
              </p>
            </div>
          ) : (
            ekycQueue.map((item) => (
              <div key={item.verificationId} className="admin-verif-card animate-fade-in-up">
                <div className="admin-card-top">
                  <div>
                    <h3 style={{ margin: '0 0 0.25rem 0', fontSize: '1.2rem', fontWeight: 700 }}>
                      {item.fullName}
                    </h3>
                    <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>
                      CCCD: <strong>{item.cccdNumberMasked}</strong> | Ngày sinh: {item.dob}
                    </span>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.85rem', fontWeight: 700, color: '#2563eb' }}>
                      Độ khớp khuôn mặt: {item.faceMatchScore}%
                    </div>
                    <div style={{ fontSize: '0.75rem', color: '#6b7280' }}>
                      Độ tin cậy OCR: {(item.ocrConfidence * 100).toFixed(0)}%
                    </div>
                  </div>
                </div>

                <div style={{ background: '#fef3c7', padding: '0.75rem 1rem', borderRadius: '0.5rem', fontSize: '0.85rem', color: '#92400e', marginBottom: '1rem' }}>
                  <strong>Lý do AI cần can thiệp:</strong> {item.routingReason}
                </div>

                {/* Grid 3 ảnh đối soát */}
                <div className="admin-images-grid">
                  <div className="admin-img-box">
                    <img src={item.frontImageUrl} alt="Mặt trước CCCD" />
                    <div className="admin-img-label">1. Mặt Trước CCCD</div>
                  </div>
                  <div className="admin-img-box">
                    <img src={item.backImageUrl} alt="Mặt sau CCCD" />
                    <div className="admin-img-label">2. Mặt Sau CCCD</div>
                  </div>
                  <div className="admin-img-box">
                    <img src={item.selfieImageUrl} alt="Selfie Chân dung" />
                    <div className="admin-img-label">3. Chân Dung Selfie</div>
                  </div>
                </div>

                <div className="admin-action-row">
                  <button
                    className="btn-admin-reject"
                    onClick={() => handleAdminReview(item.verificationId, 'REJECTED')}
                  >
                    ✕ Từ Chối
                  </button>
                  <button
                    className="btn-admin-approve"
                    onClick={() => handleAdminReview(item.verificationId, 'APPROVED')}
                  >
                    ✓ Phê Duyệt Cấp Tích Xanh
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default AdminPage;
