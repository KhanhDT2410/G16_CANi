import React, { useState, useEffect } from 'react';
import { showToast } from '../../../components/common/Toast';

/**
 * EOSettingsTab
 * Màn hình Cài đặt Tổng thể cho Ban Tổ Chức (EO), bao gồm Cài đặt Gói Cước, Hóa Đơn & Tiêu Chuẩn Tuyển Dụng
 */
export default function EOSettingsTab({ onNavigateToSubscription }) {
  const [activeSection, setActiveSection] = useState('PACKAGE'); // 'PACKAGE' | 'RECRUITMENT' | 'NOTIFICATIONS'

  const [packageSettings, setPackageSettings] = useState({
    defaultPlan: 'MATCHING_PLUS', // 'INVITE_ONLY' | 'MATCHING_PLUS'
    alertThreshold: 1, // Báo động khi còn <= 1 event quota
    autoPayOSPrompt: true, // Tự động gợi ý PayOS VietQR khi hết quota
    paymentMethod: 'PAYOS_VIETQR', // 'PAYOS_VIETQR' | 'VNPAY' | 'MANUAL_TRANSFER'
  });

  const [recruitmentSettings, setRecruitmentSettings] = useState({
    defaultRadiusKm: 15,
    minReliabilityScore: 60,
    defaultHourlyWage: 50000,
    defaultAllowance: 20000,
    requireDressCode: true,
    autoApproveAboveScore: 85,
  });

  const [notificationSettings, setNotificationSettings] = useState({
    notifyOnWorkerAccept: true,
    notifyOnCheckinLate: true,
    notifyOnShiftComplete: true,
    dailyReportEmail: true,
  });

  // Load cài đặt đã lưu từ LocalStorage (nếu có)
  useEffect(() => {
    try {
      const savedPkg = localStorage.getItem('cani_eo_package_settings');
      if (savedPkg) setPackageSettings(JSON.parse(savedPkg));

      const savedRec = localStorage.getItem('cani_eo_recruitment_settings');
      if (savedRec) setRecruitmentSettings(JSON.parse(savedRec));

      const savedNotif = localStorage.getItem('cani_eo_notif_settings');
      if (savedNotif) setNotificationSettings(JSON.parse(savedNotif));
    } catch (e) {
      console.warn('Lỗi khi đọc cài đặt từ bộ nhớ cục bộ:', e);
    }
  }, []);

  const handleSaveAll = (e) => {
    e?.preventDefault();
    try {
      localStorage.setItem('cani_eo_package_settings', JSON.stringify(packageSettings));
      localStorage.setItem('cani_eo_recruitment_settings', JSON.stringify(recruitmentSettings));
      localStorage.setItem('cani_eo_notif_settings', JSON.stringify(notificationSettings));
      showToast('Đã lưu cấu hình cài đặt thành công!', 'success');
    } catch {
      showToast('Lỗi khi lưu cấu hình', 'error');
    }
  };

  return (
    <div className="eo-settings-container animate-fade-in-up" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* 1. HEADER */}
      <div className="eo-widget-card" style={{ padding: '1.25rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 800, color: '#0f172a' }}>
              Cài Đặt Hệ Thống & Cấu Hình Gói Dịch Vụ
            </h3>
            <p style={{ margin: '0.25rem 0 0', fontSize: '0.875rem', color: '#64748b' }}>
              Quản lý gói dịch vụ đăng tin, định mức thù lao tuyển dụng và thông tin pháp nhân xuất hóa đơn.
            </p>
          </div>

          <button
            type="button"
            className="eo-btn-primary"
            style={{ fontSize: '0.85rem', padding: '0.55rem 1.25rem' }}
            onClick={handleSaveAll}
          >
            Lưu Toàn Bộ Cài Đặt
          </button>
        </div>
      </div>

      {/* 2. SUB-NAVIGATION TABS */}
      <div className="sub-tabs-bar" style={{ display: 'flex', gap: '0.5rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.5rem' }}>
        <button
          type="button"
          className={`sub-tab-btn ${activeSection === 'PACKAGE' ? 'active' : ''}`}
          onClick={() => setActiveSection('PACKAGE')}
          style={{
            padding: '8px 16px',
            border: 'none',
            borderRadius: 6,
            background: activeSection === 'PACKAGE' ? '#eff6ff' : 'transparent',
            color: activeSection === 'PACKAGE' ? '#1d4ed8' : '#64748b',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Cài Đặt Gói Cước & Hạn Mức
        </button>

        <button
          type="button"
          className={`sub-tab-btn ${activeSection === 'RECRUITMENT' ? 'active' : ''}`}
          onClick={() => setActiveSection('RECRUITMENT')}
          style={{
            padding: '8px 16px',
            border: 'none',
            borderRadius: 6,
            background: activeSection === 'RECRUITMENT' ? '#eff6ff' : 'transparent',
            color: activeSection === 'RECRUITMENT' ? '#1d4ed8' : '#64748b',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Tiêu Chuẩn Tuyển Dụng & Matching
        </button>

        <button
          type="button"
          className={`sub-tab-btn ${activeSection === 'NOTIFICATIONS' ? 'active' : ''}`}
          onClick={() => setActiveSection('NOTIFICATIONS')}
          style={{
            padding: '8px 16px',
            border: 'none',
            borderRadius: 6,
            background: activeSection === 'NOTIFICATIONS' ? '#eff6ff' : 'transparent',
            color: activeSection === 'NOTIFICATIONS' ? '#1d4ed8' : '#64748b',
            fontWeight: 600,
            cursor: 'pointer',
          }}
        >
          Thông Báo & Vận Hành
        </button>
      </div>

      {/* 3. NỘI DUNG TỪNG SECTION */}

      {/* SECTION 1: CÀI ĐẶT GÓI CƯỚC & HẠN MỨC */}
      {activeSection === 'PACKAGE' && (
        <div className="eo-widget-card" style={{ padding: '1.5rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
            <h4 style={{ margin: 0, fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
              Cấu Hình Gói Dịch Vụ Đăng Tin & Điều Phối
            </h4>
            <button
              type="button"
              className="widget-link-btn"
              onClick={onNavigateToSubscription}
              style={{ fontSize: '0.85rem' }}
            >
              Xem danh mục gói & mua thêm lượt &rarr;
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem' }}>
            <div className="eo-form-group">
              <label>Gói Cước Mặc Định Khi Tạo Sự Kiện Mới</label>
              <select
                className="eo-form-select"
                value={packageSettings.defaultPlan}
                onChange={(e) => setPackageSettings({ ...packageSettings, defaultPlan: e.target.value })}
              >
                <option value="MATCHING_PLUS">Gói Tự Động Ghép Đôi (80,000 đ / sự kiện)</option>
                <option value="INVITE_ONLY">Gói Mời Trực Tiếp - Invite Mode (50,000 đ / sự kiện)</option>
              </select>
              <span className="eo-field-helper">
                Gói được chọn sẵn khi bạn bước vào quy trình tạo sự kiện.
              </span>
            </div>

            <div className="eo-form-group">
              <label>Cổng Thanh Toán Ưu Tiên Mặc Định</label>
              <select
                className="eo-form-select"
                value={packageSettings.paymentMethod}
                onChange={(e) => setPackageSettings({ ...packageSettings, paymentMethod: e.target.value })}
              >
                <option value="PAYOS_VIETQR">Cổng PayOS VietQR MBBank (Tự động khớp tiền)</option>
                <option value="VNPAY">Cổng VNPay Sandbox Gateway</option>
                <option value="MANUAL_TRANSFER">Chuyển khoản thủ công</option>
              </select>
              <span className="eo-field-helper">
                Hệ thống ưu tiên sinh mã QR VietQR Napas 24/7 tức thì.
              </span>
            </div>

            <div className="eo-form-group">
              <label>Cảnh Báo Khi Lượt Sự Kiện Còn Lại</label>
              <select
                className="eo-form-select"
                value={packageSettings.alertThreshold}
                onChange={(e) => setPackageSettings({ ...packageSettings, alertThreshold: parseInt(e.target.value) })}
              >
                <option value={1}>Còn &le; 1 sự kiện (Khuyên dùng)</option>
                <option value={2}>Còn &le; 2 sự kiện</option>
                <option value={3}>Còn &le; 3 sự kiện</option>
              </select>
              <span className="eo-field-helper">
                Hệ thống sẽ gửi thông báo để bạn chủ động bổ sung gói trước khi sự kiện bắt đầu.
              </span>
            </div>

            <div className="eo-form-group">
              <label>Tự Động Mở Thanh Toán Khi Hết Lượt Sự Kiện</label>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.4rem' }}>
                <input
                  type="checkbox"
                  id="autoPayOS"
                  checked={packageSettings.autoPayOSPrompt}
                  onChange={(e) => setPackageSettings({ ...packageSettings, autoPayOSPrompt: e.target.checked })}
                  style={{ width: 18, height: 18 }}
                />
                <label htmlFor="autoPayOS" style={{ margin: 0, fontWeight: 500, cursor: 'pointer' }}>
                  Hiển thị mã thanh toán PayOS tức thì ở Bước 3 khi tạo ca
                </label>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* SECTION 2: TIÊU CHUẨN TUYỂN DỤNG & MATCHING */}
      {activeSection === 'RECRUITMENT' && (
        <div className="eo-widget-card" style={{ padding: '1.5rem' }}>
          <h4 style={{ margin: '0 0 1.25rem', fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
            Quy Chuẩn Tuyển Dụng & Tiêu Chí Ghép Nối Ứng Viên (Matching Engine)
          </h4>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '1.25rem' }}>
            <div className="eo-form-group">
              <label>Bán Kính Quét Radar Mặc Định (km)</label>
              <input
                type="number"
                min="1"
                max="50"
                className="eo-form-input"
                value={recruitmentSettings.defaultRadiusKm}
                onChange={(e) => setRecruitmentSettings({ ...recruitmentSettings, defaultRadiusKm: parseInt(e.target.value) || 15 })}
              />
              <span className="eo-field-helper">
                Bán kính ưu tiên phát tín hiệu tuyển dụng tới Worker quanh địa điểm tổ chức.
              </span>
            </div>

            <div className="eo-form-group">
              <label>Điểm Uy Tín Tối Thiểu (Reliability Score / 100)</label>
              <input
                type="number"
                min="0"
                max="100"
                className="eo-form-input"
                value={recruitmentSettings.minReliabilityScore}
                onChange={(e) => setRecruitmentSettings({ ...recruitmentSettings, minReliabilityScore: parseInt(e.target.value) || 60 })}
              />
              <span className="eo-field-helper">
                Chỉ ghép nối những Worker có điểm độ tin cậy đạt từ mức này trở lên.
              </span>
            </div>

            <div className="eo-form-group">
              <label>Thù Lao Đề Xuất Mặc Định (VNĐ/giờ)</label>
              <input
                type="number"
                min="20000"
                step="5000"
                className="eo-form-input"
                value={recruitmentSettings.defaultHourlyWage}
                onChange={(e) => setRecruitmentSettings({ ...recruitmentSettings, defaultHourlyWage: parseInt(e.target.value) || 50000 })}
              />
              <span className="eo-field-helper">
                Mức lương tự động điền khi tạo ca mới (tối thiểu 20,000 đ/h theo quy định).
              </span>
            </div>

            <div className="eo-form-group">
              <label>Phụ Cấp Ca Mặc Định (VNĐ/ca)</label>
              <input
                type="number"
                step="10000"
                className="eo-form-input"
                value={recruitmentSettings.defaultAllowance}
                onChange={(e) => setRecruitmentSettings({ ...recruitmentSettings, defaultAllowance: parseInt(e.target.value) || 0 })}
              />
              <span className="eo-field-helper">
                Hỗ trợ xăng xe hoặc ăn trưa cho nhân sự ca.
              </span>
            </div>
          </div>
        </div>
      )}

      {/* SECTION 3: THÔNG BÁO & VẬN HÀNH */}
      {activeSection === 'NOTIFICATIONS' && (
        <div className="eo-widget-card" style={{ padding: '1.5rem' }}>
          <h4 style={{ margin: '0 0 1rem', fontSize: '1.1rem', fontWeight: 700, color: '#0f172a' }}>
            Cấu Hình Thông Báo & Cảnh Báo Vận Hành
          </h4>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {[
              {
                id: 'notifWorker',
                title: 'Thông báo tức thì khi có Worker nhận ca',
                desc: 'Gửi tin nhắn thông báo khi hệ thống tự động kết nối thành công ứng viên',
                checked: notificationSettings.notifyOnWorkerAccept,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, notifyOnWorkerAccept: v }),
              },
              {
                id: 'notifLate',
                title: 'Cảnh báo khi nhân sự Check-in trễ giờ',
                desc: 'Hệ thống gửi cảnh báo nếu Worker chưa quét mã QR sau 15 phút kể từ giờ bắt đầu ca',
                checked: notificationSettings.notifyOnCheckinLate,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, notifyOnCheckinLate: v }),
              },
              {
                id: 'notifDone',
                title: 'Nhắc nhở đối soát & duyệt công sau ca',
                desc: 'Tự động mở danh sách kiểm tra khi ca làm kết thúc để Ban tổ chức duyệt thù lao',
                checked: notificationSettings.notifyOnShiftComplete,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, notifyOnShiftComplete: v }),
              },
              {
                id: 'notifSummary',
                title: 'Báo cáo tổng kết sự kiện qua Email',
                desc: 'Nhận bảng tổng hợp đối soát thanh toán và đánh giá nhân sự sau khi hoàn thành sự kiện',
                checked: notificationSettings.dailyReportEmail,
                onChange: (v) => setNotificationSettings({ ...notificationSettings, dailyReportEmail: v }),
              },
            ].map((item) => (
              <div
                key={item.id}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: '0.75rem',
                  padding: '0.75rem',
                  backgroundColor: '#f8fafc',
                  borderRadius: 8,
                  border: '1px solid #e2e8f0',
                }}
              >
                <input
                  type="checkbox"
                  id={item.id}
                  checked={item.checked}
                  onChange={(e) => item.onChange(e.target.checked)}
                  style={{ width: 18, height: 18, marginTop: 2, cursor: 'pointer' }}
                />
                <div>
                  <label htmlFor={item.id} style={{ margin: 0, fontWeight: 600, color: '#0f172a', cursor: 'pointer' }}>
                    {item.title}
                  </label>
                  <p style={{ margin: '0.2rem 0 0', fontSize: '0.825rem', color: '#64748b' }}>
                    {item.desc}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

