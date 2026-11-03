import React, { useState } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { ROLES, WAGE_RECOMMENDATIONS_MAP } from '../hooks/useShifts';

/**
 * EventCreationWizardModal
 * Quy trình liên hoàn 4 bước (Clean Enterprise UI - No Emoji): 
 * 1. Tạo Event
 * 2. Tạo nhiều Ca làm việc (Shifts) kèm Giá tiền, Thời gian, Số người, Mô tả, Checklist, nút Thêm ca & Xóa ca
 * 3. Thanh toán gói cước (PayOS VietQR MBBank hoặc Trừ Quota)
 * 4. Bắt đầu Matching Radar
 */
const EventCreationWizardModal = ({
  isOpen,
  onClose,
  onSuccess,
  newEvent,
  setNewEvent,
  onGetCurrentLocation,
  locating,
}) => {
  const [currentStep, setCurrentStep] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [createdEvent, setCreatedEvent] = useState(null);
  
  // Danh sách các ca làm việc (Shifts) đã thêm vào sự kiện này
  const [shiftsList, setShiftsList] = useState([]);
  const [isAddingShift, setIsAddingShift] = useState(true);

  // State cho Form tạo ca hiện tại
  const [currentShift, setCurrentShift] = useState({
    jobType: ROLES[0],
    description: 'Hỗ trợ công tác đón tiếp khách, hướng dẫn vị trí và điều phối sự kiện theo phân công.',
    startTime: '2026-11-20T08:00',
    endTime: '2026-11-20T12:00',
    requiredWorkers: 4,
    hourlyWage: 50000,
    allowance: 20000,
    maxDistanceKm: 15,
    checklistItems: [
      'Có mặt trước 20 phút nhận thẻ và vị trí',
      'Đồng phục chỉn chu theo quy định',
      'Quét mã QR Check-in đầu ca',
      'Báo cáo quản lý trước khi kết thúc ca',
    ],
  });

  const [newChecklistItem, setNewChecklistItem] = useState('');

  // State cho Bước 3: Thanh toán
  const [payMethod, setPayMethod] = useState('VIETQR'); // 'VIETQR' | 'SUBSCRIPTION'
  const [payOSData, setPayOSData] = useState(null);
  const [loadingPayOS, setLoadingPayOS] = useState(false);

  if (!isOpen) return null;

  // Tính số giờ làm việc của ca hiện tại
  const getDurationHours = (start, end) => {
    try {
      const s = new Date(start);
      const e = new Date(end);
      const diff = (e - s) / (1000 * 60 * 60);
      return diff > 0 ? parseFloat(diff.toFixed(1)) : 4;
    } catch {
      return 4;
    }
  };

  const currentDurationHours = getDurationHours(currentShift.startTime, currentShift.endTime);
  const currentShiftTotal =
    currentDurationHours *
      Number(currentShift.requiredWorkers || 1) *
      Number(currentShift.hourlyWage || 0) +
    Number(currentShift.allowance || 0);

  // Đổi vai trò cho ca hiện tại
  const handleRoleChange = (role) => {
    const rec = WAGE_RECOMMENDATIONS_MAP[role];
    setCurrentShift((prev) => ({
      ...prev,
      jobType: role,
      hourlyWage: rec ? rec.defaultWage : prev.hourlyWage,
    }));
  };

  // Thêm checklist cho ca hiện tại
  const handleAddChecklist = (e) => {
    e?.preventDefault();
    if (!newChecklistItem.trim()) return;
    setCurrentShift((prev) => ({
      ...prev,
      checklistItems: [...(prev.checklistItems || []), newChecklistItem.trim()],
    }));
    setNewChecklistItem('');
  };

  // Xóa checklist
  const handleRemoveChecklist = (index) => {
    setCurrentShift((prev) => ({
      ...prev,
      checklistItems: (prev.checklistItems || []).filter((_, idx) => idx !== index),
    }));
  };

  // ==========================================
  // BƯỚC 1: TẠO SỰ KIỆN VÀ CHUYỂN SANG BƯỚC 2
  // ==========================================
  const handleStep1Submit = async (e) => {
    e.preventDefault();
    if (!newEvent.name?.trim() || !newEvent.venueAddress?.trim()) {
      showToast('Vui lòng nhập tên sự kiện và địa chỉ tổ chức', 'error');
      return;
    }

    setSubmitting(true);
    try {
      let evt = createdEvent;
      if (!evt) {
        evt = await eoService.createEvent(newEvent);
        setCreatedEvent(evt);
      }
      setCurrentStep(2);
      setIsAddingShift(true);
      showToast('Tạo sự kiện thành công! Hãy tạo các ca làm việc cho sự kiện.', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi tạo sự kiện', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // ==========================================
  // BƯỚC 2: THÊM CA LÀM VIỆC (SHIFT) VÀO SỰ KIỆN
  // ==========================================
  const handleSaveShift = async (e) => {
    e?.preventDefault();
    if (!createdEvent?.eventId) {
      showToast('Không tìm thấy sự kiện. Vui lòng thử lại.', 'error');
      return;
    }

    if (!currentShift.description?.trim()) {
      showToast('Vui lòng nhập mô tả công việc cho ca làm', 'error');
      return;
    }

    setSubmitting(true);
    try {
      const checklistJson = JSON.stringify(currentShift.checklistItems || []);
      const payload = {
        ...currentShift,
        checklist: checklistJson,
        startTime: new Date(currentShift.startTime).toISOString(),
        endTime: new Date(currentShift.endTime).toISOString(),
      };

      const addedShift = await eoService.addShiftToEvent(createdEvent.eventId, payload);
      setShiftsList((prev) => [...prev, addedShift]);
      
      // Reset form để sẵn sàng tạo ca tiếp theo
      const nextRoleIndex = (shiftsList.length + 1) % ROLES.length;
      const nextRole = ROLES[nextRoleIndex];
      const rec = WAGE_RECOMMENDATIONS_MAP[nextRole];

      setCurrentShift({
        jobType: nextRole,
        description: 'Phối hợp thực hiện nhiệm vụ theo kế hoạch tổ chức sự kiện.',
        startTime: currentShift.startTime,
        endTime: currentShift.endTime,
        requiredWorkers: 2,
        hourlyWage: rec ? rec.defaultWage : 50000,
        allowance: 0,
        maxDistanceKm: 15,
        checklistItems: [
          'Có mặt đúng giờ quy định',
          'Quét mã QR Check-in',
          'Hoàn thành nhiệm vụ được giao',
        ],
      });

      setIsAddingShift(false);
      showToast(`Đã thêm ca "${addedShift.jobType}" thành công!`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi lưu ca làm', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // XÓA CA LÀM VIỆC (SHIFT)
  const handleDeleteShift = async (shiftId, index) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa ca làm việc này khỏi sự kiện?')) return;
    try {
      if (shiftId) {
        await eoService.cancelShift(shiftId, 'Ban tổ chức xóa ca');
      }
      setShiftsList((prev) => prev.filter((_, idx) => idx !== index));
      showToast('Đã xóa ca làm việc thành công!', 'info');
    } catch (err) {
      showToast('Lỗi khi xóa ca: ' + (err.response?.data?.message || err.message), 'error');
    }
  };

  // CHUYỂN SANG BƯỚC 3 (THANH TOÁN GÓI)
  const handleProceedToStep3 = async () => {
    if (shiftsList.length === 0) {
      showToast('Vui lòng thêm ít nhất một ca làm việc trước khi tiếp tục!', 'warning');
      return;
    }
    setCurrentStep(3);
    initPayOSSession();
  };

  // ==========================================
  // BƯỚC 3: KHỞI TẠO VÀ XỬ LÝ PAYOS VIETQR
  // ==========================================
  const totalWorkers = shiftsList.reduce((sum, s) => sum + (s.requiredWorkers || 0), 0);
  const totalEstimatedCost = shiftsList.reduce((sum, s) => sum + (Number(s.totalCost) || 0), 0);
  const platformFee = 50000; // 50k phí đăng tin theo gói

  const initPayOSSession = async () => {
    setLoadingPayOS(true);
    try {
      const res = await eoService.purchaseSubscriptionPayOS({ planType: 'INVITE_ONLY' });
      if (res) {
        setPayOSData(res);
      }
    } catch (e) {
      console.warn('Lỗi kết nối PayOS, sử dụng fallback VietQR:', e);
    } finally {
      setLoadingPayOS(false);
    }
  };

  const handleStep3ConfirmPayment = () => {
    setCurrentStep(4);
    showToast('Xác nhận thanh toán thành công! Sẵn sàng kích hoạt Matching.', 'success');
  };

  // ==========================================
  // BƯỚC 4: KÍCH HOẠT MATCHING RADAR AI
  // ==========================================
  const handleStep4ActivateMatching = async () => {
    if (!createdEvent?.eventId) return;
    setSubmitting(true);
    try {
      await eoService.publishEvent(createdEvent.eventId);
      showToast('Chúc mừng! Đã kích hoạt Matching Radar AI tìm kiếm nhân sự thành công!', 'success');
      onSuccess?.();
      handleClose();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || 'Lỗi khi kích hoạt Matching', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = () => {
    setCurrentStep(1);
    setCreatedEvent(null);
    setShiftsList([]);
    setIsAddingShift(true);
    setPayOSData(null);
    onClose();
  };

  // QR VietQR MBBank chính chủ
  const eventCode = (createdEvent?.eventId || 'EVT').replace(/[^a-zA-Z0-9]/g, '').slice(-6).toUpperCase();
  const transferMemo = `CANI PHI ${eventCode}`;
  const vietQrUrl = payOSData?.checkoutUrl?.startsWith('http') && payOSData?.checkoutUrl?.includes('vietqr.io')
    ? payOSData.checkoutUrl
    : `https://img.vietqr.io/image/MB-2622014789096-compact2.png?amount=${platformFee}&addInfo=${encodeURIComponent(transferMemo)}&accountName=PHAM%20DUY%20HUNG`;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '780px', maxHeight: '92vh', overflowY: 'auto' }}>
        {/* THANH ĐIỀU HƯỚNG BƯỚC (STEPPER) */}
        <div style={{ marginBottom: '1.5rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Quy trình thiết lập sự kiện & ca làm việc (Shifts)
            </span>
            <button
              type="button"
              onClick={handleClose}
              style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#94a3b8' }}
            >
              Dong
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
            <div style={{ position: 'absolute', top: '50%', left: '8%', right: '8%', height: '2px', backgroundColor: '#e2e8f0', zIndex: 0 }} />
            
            {[
              { num: 1, label: '1. Sự Kiện' },
              { num: 2, label: `2. Các Ca Làm (${shiftsList.length})` },
              { num: 3, label: '3. Thanh Toán Gói' },
              { num: 4, label: '4. Bắt Đầu Matching' },
            ].map((st) => {
              const isActive = currentStep === st.num;
              const isPassed = currentStep > st.num;
              return (
                <div
                  key={st.num}
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '0.35rem',
                    zIndex: 1,
                    background: '#ffffff',
                    padding: '0 0.5rem',
                  }}
                >
                  <div
                    style={{
                      width: '32px',
                      height: '32px',
                      borderRadius: '50%',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: '0.875rem',
                      backgroundColor: isPassed ? '#10b981' : isActive ? '#2563eb' : '#f1f5f9',
                      color: isPassed || isActive ? '#ffffff' : '#64748b',
                      border: isActive ? '2px solid #93c5fd' : 'none',
                      transition: 'all 0.2s',
                    }}
                  >
                    {isPassed ? 'OK' : st.num}
                  </div>
                  <span
                    style={{
                      fontSize: '0.75rem',
                      fontWeight: isActive ? 700 : 500,
                      color: isActive ? '#2563eb' : isPassed ? '#10b981' : '#64748b',
                    }}
                  >
                    {st.label}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* ======================================================== */}
        {/* BƯỚC 1: THÔNG TIN SỰ KIỆN                               */}
        {/* ======================================================== */}
        {currentStep === 1 && (
          <form onSubmit={handleStep1Submit} className="animate-fade-in-up">
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
              Bước 1: Khởi Tạo Sự Kiện & Địa Điểm
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 1.25rem' }}>
              Nhập tên sự kiện và tọa độ GPS tổ chức để định vị bán kính tìm kiếm ứng viên.
            </p>

            <div className="eo-form-group">
              <label>Tên Sự Kiện *</label>
              <input
                type="text"
                required
                className="eo-form-input"
                placeholder="Ví dụ: Triển lãm Công nghệ TechExpo 2026"
                value={newEvent.name}
                onChange={(e) => setNewEvent({ ...newEvent, name: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Mô Tả Quy Mô Sự Kiện</label>
              <textarea
                rows="2"
                className="eo-form-textarea"
                placeholder="Mô tả quy mô, đối tượng khách mời, yêu cầu chung..."
                value={newEvent.description}
                onChange={(e) => setNewEvent({ ...newEvent, description: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                <label style={{ margin: 0 }}>Địa Điểm Tổ Chức (Venue Address) *</label>
                <button
                  type="button"
                  className="eo-btn-gps"
                  onClick={onGetCurrentLocation}
                  disabled={locating}
                >
                  {locating ? 'Đang định vị...' : 'Lấy vị trí hiện tại'}
                </button>
              </div>
              <input
                type="text"
                required
                className="eo-form-input"
                placeholder="Nhập địa chỉ hoặc bấm nút lấy vị trí hiện tại..."
                value={newEvent.venueAddress}
                onChange={(e) => setNewEvent({ ...newEvent, venueAddress: e.target.value })}
              />
            </div>

            <div className="eo-form-row">
              <div className="eo-form-group">
                <label>Tọa Độ Vĩ Độ (Lat)</label>
                <input
                  type="number"
                  step="any"
                  className="eo-form-input"
                  value={newEvent.venueLat}
                  onChange={(e) => setNewEvent({ ...newEvent, venueLat: parseFloat(e.target.value) })}
                />
              </div>
              <div className="eo-form-group">
                <label>Tọa Độ Kinh Độ (Lng)</label>
                <input
                  type="number"
                  step="any"
                  className="eo-form-input"
                  value={newEvent.venueLng}
                  onChange={(e) => setNewEvent({ ...newEvent, venueLng: parseFloat(e.target.value) })}
                />
              </div>
            </div>

            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={handleClose}>
                Hủy Bỏ
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="eo-btn-primary"
              >
                {submitting ? 'Đang tạo sự kiện...' : 'Tiếp tục: Tạo các Ca làm việc'}
              </button>
            </div>
          </form>
        )}

        {/* ======================================================== */}
        {/* BƯỚC 2: TẠO NHIỀU CA LÀM VIỆC (SHIFTS) KÈM NÚT THÊM & XÓA */}
        {/* ======================================================== */}
        {currentStep === 2 && (
          <div className="animate-fade-in-up">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
                  Bước 2: Thiết Lập Danh Sách Ca Làm Việc (Shifts)
                </h3>
                <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0.25rem 0 0' }}>
                  Sự kiện: <strong>{createdEvent?.name || newEvent.name}</strong>. Một sự kiện có thể chia làm nhiều ca khác nhau.
                </p>
              </div>

              {!isAddingShift && (
                <button
                  type="button"
                  className="eo-btn-primary"
                  style={{ fontSize: '0.85rem', padding: '0.5rem 1rem' }}
                  onClick={() => setIsAddingShift(true)}
                >
                  + Thêm Ca Làm Mới
                </button>
              )}
            </div>

            {/* DANH SÁCH CÁC CA ĐÃ TẠO */}
            {shiftsList.length > 0 && (
              <div style={{ marginBottom: '1.5rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <div style={{ fontSize: '0.875rem', fontWeight: 700, color: '#334155', display: 'flex', justifyContent: 'space-between' }}>
                  <span>Danh sách ca đã thêm ({shiftsList.length} ca):</span>
                  <span style={{ color: '#2563eb' }}>
                    Tổng: {totalWorkers} nhân sự • Dự toán: {totalEstimatedCost.toLocaleString()} đ
                  </span>
                </div>

                {shiftsList.map((shift, idx) => {
                  const sHours = getDurationHours(shift.startTime, shift.endTime);
                  return (
                    <div
                      key={shift.shiftId || idx}
                      style={{
                        backgroundColor: '#ffffff',
                        border: '1px solid #cbd5e1',
                        borderRadius: '0.625rem',
                        padding: '1rem',
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: '1rem',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.03)',
                      }}
                    >
                      <div style={{ flex: 1 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.35rem' }}>
                          <span style={{ backgroundColor: '#eff6ff', color: '#1d4ed8', fontWeight: 700, fontSize: '0.85rem', padding: '0.2rem 0.5rem', borderRadius: '0.35rem' }}>
                            Ca #{idx + 1}
                          </span>
                          <strong style={{ fontSize: '1rem', color: '#0f172a' }}>{shift.jobType}</strong>
                          <span style={{ fontSize: '0.85rem', color: '#16a34a', fontWeight: 600 }}>
                            {shift.requiredWorkers} người x {Number(shift.hourlyWage || 0).toLocaleString()} đ/h
                          </span>
                        </div>

                        <div style={{ fontSize: '0.825rem', color: '#64748b', display: 'flex', flexWrap: 'wrap', gap: '0.85rem' }}>
                          <span>Thời gian: {sHours}h ({new Date(shift.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} - {new Date(shift.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })})</span>
                          <span>Dự toán ca: <strong>{(Number(shift.totalCost) || 0).toLocaleString()} đ</strong></span>
                          {shift.allowance > 0 && <span>Phụ cấp: {Number(shift.allowance).toLocaleString()} đ</span>}
                        </div>

                        {shift.description && (
                          <div style={{ fontSize: '0.8rem', color: '#475569', marginTop: '0.35rem', fontStyle: 'italic' }}>
                            &ldquo;{shift.description}&rdquo;
                          </div>
                        )}
                      </div>

                      <button
                        type="button"
                        onClick={() => handleDeleteShift(shift.shiftId, idx)}
                        style={{
                          backgroundColor: '#fef2f2',
                          color: '#dc2626',
                          border: '1px solid #fecaca',
                          borderRadius: '0.375rem',
                          padding: '0.45rem 0.75rem',
                          fontSize: '0.8rem',
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                        title="Xóa ca làm này khỏi sự kiện"
                      >
                        Xóa Ca
                      </button>
                    </div>
                  );
                })}
              </div>
            )}

            {/* FORM TẠO CA LÀM VIỆC (SHIFT FORM) */}
            {isAddingShift ? (
              <form onSubmit={handleSaveShift} style={{ backgroundColor: '#f8fafc', padding: '1.25rem', borderRadius: '0.75rem', border: '1.5px dashed #93c5fd', marginBottom: '1.5rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                  <h4 style={{ margin: 0, fontSize: '1rem', fontWeight: 700, color: '#1e40af' }}>
                    Nhập Thông Tin Ca Làm Việc {shiftsList.length > 0 ? `Thứ ${shiftsList.length + 1}` : 'Đầu Tiên'}
                  </h4>
                  {shiftsList.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setIsAddingShift(false)}
                      style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '0.85rem' }}
                    >
                      Thu gọn
                    </button>
                  )}
                </div>

                <div className="eo-form-group">
                  <label>Vị Trí / Vai Trò Cần Tuyển *</label>
                  <select
                    className="eo-form-select"
                    value={currentShift.jobType}
                    onChange={(e) => handleRoleChange(e.target.value)}
                  >
                    {ROLES.map((role) => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>

                  {WAGE_RECOMMENDATIONS_MAP[currentShift.jobType] && (
                    <div className="wage-recommendation-badge animate-fade-in-up" style={{ marginTop: '0.5rem' }}>
                      <div>
                        <strong>Gợi ý Ma trận Lương (Wage Matrix 4D):</strong>{' '}
                        <span className="wage-range">
                          {WAGE_RECOMMENDATIONS_MAP[currentShift.jobType].min.toLocaleString()} đ -{' '}
                          {WAGE_RECOMMENDATIONS_MAP[currentShift.jobType].max.toLocaleString()} đ / giờ
                        </span>
                        <div className="wage-subnote">
                          Đặc điểm: {WAGE_RECOMMENDATIONS_MAP[currentShift.jobType].note}
                        </div>
                      </div>
                    </div>
                  )}
                </div>

                {/* Mô tả công việc */}
                <div className="eo-form-group">
                  <label>Mô Tả Nhiệm Vụ Công Việc Trong Ca *</label>
                  <textarea
                    rows="2"
                    required
                    className="eo-form-textarea"
                    placeholder="VD: Hỗ trợ đón tiếp khách, hướng dẫn vị trí, điều phối micro..."
                    value={currentShift.description}
                    onChange={(e) => setCurrentShift({ ...currentShift, description: e.target.value })}
                  />
                </div>

                <div className="eo-form-row">
                  <div className="eo-form-group">
                    <label>Thời Gian Bắt Đầu *</label>
                    <input
                      type="datetime-local"
                      required
                      className="eo-form-input"
                      value={currentShift.startTime}
                      onChange={(e) => setCurrentShift({ ...currentShift, startTime: e.target.value })}
                    />
                  </div>
                  <div className="eo-form-group">
                    <label>Thời Gian Kết Thúc *</label>
                    <input
                      type="datetime-local"
                      required
                      className="eo-form-input"
                      value={currentShift.endTime}
                      onChange={(e) => setCurrentShift({ ...currentShift, endTime: e.target.value })}
                    />
                  </div>
                </div>

                <div className="eo-form-row">
                  <div className="eo-form-group">
                    <label>Số Lượng Nhân Sự Cần Tuyển *</label>
                    <input
                      type="number"
                      min="1"
                      required
                      className="eo-form-input"
                      value={currentShift.requiredWorkers}
                      onChange={(e) => setCurrentShift({ ...currentShift, requiredWorkers: parseInt(e.target.value) || 1 })}
                    />
                  </div>
                  <div className="eo-form-group">
                    <label>Mức Thù Lao Giờ (VNĐ/giờ) *</label>
                    <input
                      type="number"
                      min="20000"
                      step="5000"
                      required
                      className="eo-form-input"
                      value={currentShift.hourlyWage}
                      onChange={(e) => setCurrentShift({ ...currentShift, hourlyWage: parseFloat(e.target.value) || 0 })}
                    />
                  </div>
                </div>

                <div className="eo-form-row">
                  <div className="eo-form-group">
                    <label>Phụ Cấp Thêm (VNĐ)</label>
                    <input
                      type="number"
                      step="10000"
                      className="eo-form-input"
                      value={currentShift.allowance}
                      onChange={(e) => setCurrentShift({ ...currentShift, allowance: parseFloat(e.target.value) || 0 })}
                    />
                  </div>
                  <div className="eo-form-group">
                    <label>Bán Kính Quét Ứng Viên (km)</label>
                    <input
                      type="number"
                      min="1"
                      max="50"
                      className="eo-form-input"
                      value={currentShift.maxDistanceKm}
                      onChange={(e) => setCurrentShift({ ...currentShift, maxDistanceKm: parseFloat(e.target.value) || 15 })}
                    />
                  </div>
                </div>

                {/* Checklist công việc */}
                <div className="eo-form-group" style={{ backgroundColor: '#ffffff', padding: '1rem', borderRadius: '0.5rem', border: '1px solid #e2e8f0' }}>
                  <label style={{ fontWeight: 600, color: '#334155', display: 'flex', justifyContent: 'space-between' }}>
                    <span>Checklist Đầu Việc Cho Ca Này ({currentShift.checklistItems.length})</span>
                    <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Đồng bộ app Mobile Worker</span>
                  </label>

                  <div style={{ display: 'flex', gap: '0.5rem', margin: '0.5rem 0' }}>
                    <input
                      type="text"
                      className="eo-form-input"
                      style={{ flex: 1 }}
                      placeholder="Thêm đầu việc mới..."
                      value={newChecklistItem}
                      onChange={(e) => setNewChecklistItem(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') {
                          e.preventDefault();
                          handleAddChecklist();
                        }
                      }}
                    />
                    <button
                      type="button"
                      className="eo-btn-secondary"
                      style={{ padding: '0.4rem 0.85rem' }}
                      onClick={handleAddChecklist}
                    >
                      + Thêm
                    </button>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3rem', maxHeight: '120px', overflowY: 'auto' }}>
                    {currentShift.checklistItems.map((item, idx) => (
                      <div
                        key={idx}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'space-between',
                          backgroundColor: '#f8fafc',
                          border: '1px solid #e2e8f0',
                          borderRadius: '0.25rem',
                          padding: '0.35rem 0.6rem',
                          fontSize: '0.8rem',
                        }}
                      >
                        <span>
                          <strong style={{ color: '#2563eb', marginRight: '0.35rem' }}>#{idx + 1}</strong>
                          {item}
                        </span>
                        <button
                          type="button"
                          onClick={() => handleRemoveChecklist(idx)}
                          style={{ background: 'none', border: 'none', color: '#ef4444', cursor: 'pointer', fontWeight: 'bold' }}
                        >
                          X
                        </button>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Hộp dự toán cho ca này */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem', backgroundColor: '#eff6ff', borderRadius: '0.5rem', marginBottom: '1rem' }}>
                  <span style={{ fontSize: '0.85rem', color: '#1e40af' }}>
                    Dự toán ca này: <strong>{currentDurationHours}h x {currentShift.requiredWorkers} người</strong>
                  </span>
                  <span style={{ fontSize: '1.05rem', fontWeight: 800, color: '#1d4ed8' }}>
                    {currentShiftTotal.toLocaleString()} đ
                  </span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                  <button
                    type="submit"
                    disabled={submitting}
                    className="eo-btn-primary"
                    style={{ background: '#2563eb' }}
                  >
                    {submitting ? 'Đang lưu ca...' : 'Lưu Ca Vào Sự Kiện'}
                  </button>
                </div>
              </form>
            ) : null}

            {/* ĐIỀU HƯỚNG CUỐI BƯỚC 2 */}
            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={() => setCurrentStep(1)}>
                Quay lại
              </button>
              <button
                type="button"
                className="eo-btn-primary"
                disabled={shiftsList.length === 0}
                onClick={handleProceedToStep3}
                style={{
                  background: shiftsList.length > 0 ? '#2563eb' : '#94a3b8',
                  cursor: shiftsList.length > 0 ? 'pointer' : 'not-allowed',
                }}
              >
                Tiếp tục: Thanh toán gói ({shiftsList.length} ca)
              </button>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* BƯỚC 3: THANH TOÁN THEO GÓI (PAYOS VIETQR HOẶC QUOTA)    */}
        {/* ======================================================== */}
        {currentStep === 3 && (
          <div className="animate-fade-in-up">
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
              Bước 3: Thanh Toán Phí Đăng Tin Theo Gói
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 1.25rem' }}>
              Sự kiện <strong>{createdEvent?.name}</strong> gồm <strong>{shiftsList.length} ca làm việc</strong> và <strong>{totalWorkers} nhân sự cần tuyển</strong>.
            </p>

            <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.25rem' }}>
              <button
                type="button"
                onClick={() => setPayMethod('VIETQR')}
                style={{
                  flex: 1,
                  padding: '0.75rem',
                  borderRadius: '0.5rem',
                  border: payMethod === 'VIETQR' ? '2px solid #2563eb' : '1px solid #e2e8f0',
                  backgroundColor: payMethod === 'VIETQR' ? '#eff6ff' : '#ffffff',
                  fontWeight: 600,
                  cursor: 'pointer',
                  color: payMethod === 'VIETQR' ? '#1d4ed8' : '#475569',
                }}
              >
                Thanh toán PayOS VietQR (MBBank)
              </button>

              <button
                type="button"
                onClick={() => setPayMethod('SUBSCRIPTION')}
                style={{
                  flex: 1,
                  padding: '0.75rem',
                  borderRadius: '0.5rem',
                  border: payMethod === 'SUBSCRIPTION' ? '2px solid #2563eb' : '1px solid #e2e8f0',
                  backgroundColor: payMethod === 'SUBSCRIPTION' ? '#eff6ff' : '#ffffff',
                  fontWeight: 600,
                  cursor: 'pointer',
                  color: payMethod === 'SUBSCRIPTION' ? '#1d4ed8' : '#475569',
                }}
              >
                Trừ Quota Gói Hội Viên (SaaS)
              </button>
            </div>

            {payMethod === 'VIETQR' && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', backgroundColor: '#f8fafc', padding: '1.25rem', borderRadius: '0.75rem', border: '1px solid #e2e8f0' }}>
                <div style={{ textAlign: 'center' }}>
                  <img
                    src={vietQrUrl}
                    alt="PayOS VietQR MBBank"
                    style={{ width: '100%', maxWidth: '230px', borderRadius: '0.5rem', border: '1px solid #cbd5e1' }}
                  />
                  <div style={{ fontSize: '0.75rem', color: '#16a34a', fontWeight: 600, marginTop: '0.5rem' }}>
                    Tự động điền số tiền & nội dung khi quét
                  </div>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', fontSize: '0.875rem' }}>
                  <div>
                    <span style={{ color: '#64748b' }}>Ngân hàng thụ hưởng:</span>
                    <div style={{ fontWeight: 700, color: '#1e293b' }}>MBBank (Quân Đội)</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Số tài khoản:</span>
                    <div style={{ fontWeight: 700, color: '#2563eb', fontSize: '1.05rem' }}>2622 0147 8909 6</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Chủ tài khoản:</span>
                    <div style={{ fontWeight: 700, color: '#1e293b' }}>PHAM DUY HUNG</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Số tiền thanh toán:</span>
                    <div style={{ fontWeight: 700, color: '#dc2626', fontSize: '1.1rem' }}>{platformFee.toLocaleString()} VNĐ</div>
                  </div>
                  <div>
                    <span style={{ color: '#64748b' }}>Nội dung chuyển khoản:</span>
                    <code style={{ background: '#e2e8f0', padding: '0.2rem 0.4rem', borderRadius: '0.25rem', fontWeight: 700 }}>{transferMemo}</code>
                  </div>

                  {payOSData?.checkoutUrl && payOSData.checkoutUrl.startsWith('http') && !payOSData.checkoutUrl.includes('vietqr.io') && (
                    <a
                      href={payOSData.checkoutUrl}
                      target="_blank"
                      rel="noreferrer"
                      style={{
                        marginTop: '0.5rem',
                        display: 'inline-block',
                        backgroundColor: '#0284c7',
                        color: '#ffffff',
                        padding: '0.5rem 0.75rem',
                        borderRadius: '0.375rem',
                        textDecoration: 'none',
                        fontWeight: 600,
                        fontSize: '0.8rem',
                        textAlign: 'center',
                      }}
                    >
                      Mở Cổng Thanh Toán PayOS Checkout
                    </a>
                  )}
                </div>
              </div>
            )}

            {payMethod === 'SUBSCRIPTION' && (
              <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.75rem', padding: '1.25rem' }}>
                <div style={{ fontWeight: 700, color: '#15803d', marginBottom: '0.4rem' }}>
                  Gói Dịch Vụ Hội Viên Hoạt Động (Matching Plus)
                </div>
                <p style={{ fontSize: '0.875rem', color: '#166534', margin: 0 }}>
                  Hệ thống sẽ tự động trừ <strong>1 lượt quota sự kiện</strong> trong gói hội viên của bạn. Bao gồm toàn bộ {shiftsList.length} ca làm việc và {totalWorkers} vị trí nhân sự.
                </p>
              </div>
            )}

            <div className="eo-modal-footer" style={{ marginTop: '1.5rem' }}>
              <button type="button" className="eo-btn-secondary" onClick={() => setCurrentStep(2)}>
                Quay lại danh sách ca
              </button>
              <button
                type="button"
                className="eo-btn-primary"
                onClick={handleStep3ConfirmPayment}
                style={{ background: '#10b981', border: 'none' }}
              >
                {payMethod === 'VIETQR' ? 'Tôi Đã Chuyển Khoản & Tiếp Tục' : 'Xác Nhận Trừ Quota & Tiếp Tục'}
              </button>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* BƯỚC 4: BẮT ĐẦU MATCHING RADAR ENGINE                   */}
        {/* ======================================================== */}
        {currentStep === 4 && (
          <div className="animate-fade-in-up" style={{ textAlign: 'center', padding: '1.5rem 1rem' }}>
            <h3 style={{ fontSize: '1.5rem', fontWeight: 800, color: '#1e293b', margin: '0 0 0.5rem' }}>
              Tất Cả Đã Sẵn Sàng Để Matching
            </h3>
            <p style={{ color: '#64748b', fontSize: '0.95rem', maxWidth: '520px', margin: '0 auto 1.5rem' }}>
              Sự kiện <strong>{createdEvent?.name || newEvent.name}</strong> đã hoàn tất thiết lập <strong>{shiftsList.length} ca làm việc</strong> và thanh toán gói dịch vụ.
            </p>

            <div style={{ backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '0.75rem', padding: '1.25rem', textAlign: 'left', maxWidth: '540px', margin: '0 auto 2rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ color: '#64748b' }}>Sự kiện:</span>
                <strong>{createdEvent?.name || newEvent.name}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ color: '#64748b' }}>Tổng số ca làm việc:</span>
                <strong>{shiftsList.length} ca làm việc</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ color: '#64748b' }}>Tổng nhân sự cần tuyển:</span>
                <strong style={{ color: '#2563eb' }}>{totalWorkers} nhân sự</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.5rem' }}>
                <span style={{ color: '#64748b' }}>Tổng dự toán thù lao ca:</span>
                <strong style={{ color: '#16a34a' }}>{totalEstimatedCost.toLocaleString()} đ</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#64748b' }}>Trạng thái thanh toán:</span>
                <span style={{ color: '#16a34a', fontWeight: 700 }}>Đã xác nhận gói cước</span>
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'center', gap: '1rem' }}>
              <button
                type="button"
                className="eo-btn-secondary"
                onClick={handleClose}
                disabled={submitting}
              >
                Để sau (Giữ bản nháp)
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={handleStep4ActivateMatching}
                style={{
                  padding: '0.85rem 2.25rem',
                  fontSize: '1.05rem',
                  fontWeight: 700,
                  borderRadius: '0.625rem',
                  background: '#2563eb',
                  color: '#ffffff',
                  border: 'none',
                  cursor: submitting ? 'not-allowed' : 'pointer',
                  boxShadow: '0 4px 14px rgba(37, 99, 235, 0.4)',
                }}
              >
                {submitting ? 'Đang kích hoạt Radar...' : 'Bắt Đầu Matching'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default EventCreationWizardModal;
