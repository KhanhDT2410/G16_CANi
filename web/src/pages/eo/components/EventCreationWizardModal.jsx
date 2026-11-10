import React, { useState } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';
import { ROLES } from '../hooks/useShifts';
import { getCoordinatesFromAddress } from '../utils/geoUtils';
import VenueLocationPicker from './VenueLocationPicker';

/**
 * EventCreationWizardModal
 * Quy trình liên hoàn 3 bước (Clean Enterprise UI):
 * 1. Khởi tạo Sự kiện & Địa điểm (Google Maps Picker)
 * 2. Thiết lập Danh sách Ca làm việc (Shifts) kèm Ảnh đồng phục/khu vực
 * 3. Xác nhận & Kích hoạt Matching Radar AI (Áp dụng gói cước / Ưu đãi trải nghiệm)
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
    imageUrl: '',
    checklistItems: [
      'Có mặt trước 20 phút nhận thẻ và vị trí',
      'Đồng phục chỉn chu theo quy định',
      'Quét mã QR Check-in đầu ca',
      'Báo cáo quản lý trước khi kết thúc ca',
    ],
  });

  const [newChecklistItem, setNewChecklistItem] = useState('');

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
    setCurrentShift((prev) => ({
      ...prev,
      jobType: role,
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
      let eventPayload = { ...newEvent };
      // Tự động phân giải tọa độ GPS theo địa chỉ nếu có
      if (eventPayload.venueAddress?.trim()) {
        try {
          const coords = await getCoordinatesFromAddress(eventPayload.venueAddress);
          if (coords && coords.lat && coords.lng) {
            eventPayload.venueLat = coords.lat;
            eventPayload.venueLng = coords.lng;
            setNewEvent((prev) => ({ ...prev, venueLat: coords.lat, venueLng: coords.lng }));
          }
        } catch {
          // Bỏ qua lỗi mạng, sử dụng fallback an toàn
        }
      }
      eventPayload.venueLat = eventPayload.venueLat || 10.7328;
      eventPayload.venueLng = eventPayload.venueLng || 106.7214;

      let evt = createdEvent;
      if (!evt) {
        evt = await eoService.createEvent(eventPayload);
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
      const checklistJson = JSON.stringify(
        (currentShift.checklistItems || []).map((item, idx) =>
          typeof item === 'object' && item.title
            ? item
            : { id: `chk_${idx + 1}`, title: String(item).trim() }
        )
      );
      const payload = {
        ...currentShift,
        imageUrl: currentShift.imageUrl?.trim() || null,
        checklist: checklistJson,
        startTime: new Date(currentShift.startTime).toISOString(),
        endTime: new Date(currentShift.endTime).toISOString(),
      };

      const addedShift = await eoService.addShiftToEvent(createdEvent.eventId, payload);
      setShiftsList((prev) => [...prev, addedShift]);
      
      // Reset form để sẵn sàng tạo ca tiếp theo
      const nextRoleIndex = (shiftsList.length + 1) % ROLES.length;
      const nextRole = ROLES[nextRoleIndex];

      setCurrentShift({
        jobType: nextRole,
        description: 'Phối hợp thực hiện nhiệm vụ theo kế hoạch tổ chức sự kiện.',
        startTime: currentShift.startTime,
        endTime: currentShift.endTime,
        requiredWorkers: 2,
        hourlyWage: 50000,
        allowance: 0,
        maxDistanceKm: 15,
        imageUrl: '',
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

  // CHUYỂN SANG BƯỚC 3
  const handleProceedToStep3 = () => {
    if (shiftsList.length === 0) {
      showToast('Vui lòng thêm ít nhất một ca làm việc trước khi tiếp tục!', 'warning');
      return;
    }
    setCurrentStep(3);
  };

  // ==========================================
  // BƯỚC 3: KÍCH HOẠT MATCHING RADAR AI
  // ==========================================
  const totalWorkers = shiftsList.reduce((sum, s) => sum + (s.requiredWorkers || 0), 0);
  const totalEstimatedCost = shiftsList.reduce((sum, s) => sum + (Number(s.totalCost) || 0), 0);

  const handleActivateMatching = async () => {
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
    onClose();
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box" style={{ maxWidth: '780px', maxHeight: '92vh', overflowY: 'auto' }}>
        {/* THANH ĐIỀU HƯỚNG BƯỚC (STEPPER 3 BƯỚC GỌN GÀNG) */}
        <div style={{ marginBottom: '1.5rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '1rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
            <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#2563eb', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Quy trình thiết lập sự kiện & ca làm việc (Shifts)
            </span>
            <button
              type="button"
              onClick={handleClose}
              style={{ background: 'none', border: 'none', fontSize: '1rem', cursor: 'pointer', color: '#64748b', fontWeight: 600 }}
            >
              Đóng
            </button>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
            <div style={{ position: 'absolute', top: '50%', left: '12%', right: '12%', height: '2px', backgroundColor: '#e2e8f0', zIndex: 0 }} />
            
            {[
              { num: 1, label: '1. Sự Kiện & Địa Điểm' },
              { num: 2, label: '2. Các Ca Làm Việc' },
              { num: 3, label: '3. Kích Hoạt Tìm Kiếm' },
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
                    padding: '0 0.75rem',
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
              Nhập tên sự kiện và địa chỉ tổ chức để hệ thống tự động định vị tìm kiếm nhân sự phù hợp.
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

            <VenueLocationPicker
              address={newEvent.venueAddress}
              lat={newEvent.venueLat}
              lng={newEvent.venueLng}
              onLocationChange={({ address, lat, lng }) => {
                setNewEvent((prev) => ({
                  ...prev,
                  venueAddress: address,
                  venueLat: lat,
                  venueLng: lng,
                }));
              }}
              onGetCurrentLocation={onGetCurrentLocation}
              locating={locating}
              required
            />

            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={handleClose}>
                Hủy Bỏ
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="eo-btn-primary"
              >
                {submitting ? 'Đang tạo...' : 'Tiếp Tục'}
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
                  Bước 2: Thiết Lập Danh Sách Ca Làm Việc
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
                  Thêm Ca Mới
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
                      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flex: 1 }}>
                        {shift.imageUrl && (
                          <img
                            src={shift.imageUrl}
                            alt={shift.jobType}
                            style={{
                              width: '54px',
                              height: '54px',
                              objectFit: 'cover',
                              borderRadius: '0.5rem',
                              border: '1px solid #cbd5e1',
                              flexShrink: 0,
                            }}
                            onError={(e) => { e.target.style.display = 'none'; }}
                          />
                        )}

                        <div style={{ flex: 1 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.35rem', flexWrap: 'wrap' }}>
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
                      style={{ background: 'none', border: 'none', color: '#64748b', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 600 }}
                    >
                      Thu Gọn
                    </button>
                  )}
                </div>

                <div className="eo-form-group">
                  <label>Vị Trí / Vai Trò Cần Tuyển *</label>
                  <input
                    type="text"
                    list="wizard-roles-list"
                    className="eo-form-input"
                    placeholder="Nhập vị trí hoặc chọn gợi ý (VD: Phục vụ bàn, Lễ tân, MC, Điều phối...)"
                    value={currentShift.jobType}
                    onChange={(e) => handleRoleChange(e.target.value)}
                    required
                  />
                  <datalist id="wizard-roles-list">
                    {ROLES.map((role) => (
                      <option key={role} value={role} />
                    ))}
                  </datalist>
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

                {/* Ảnh minh họa ca làm / đồng phục */}
                <div className="eo-form-group">
                  <label>Ảnh Minh Họa Ca Làm / Đồng Phục (Tùy chọn)</label>
                  <input
                    type="url"
                    className="eo-form-input"
                    placeholder="Dán đường dẫn ảnh (URL) minh họa đồng phục hoặc khu vực làm việc..."
                    value={currentShift.imageUrl}
                    onChange={(e) => setCurrentShift({ ...currentShift, imageUrl: e.target.value })}
                  />
                  {currentShift.imageUrl && (
                    <div style={{ marginTop: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.5rem', backgroundColor: '#ffffff', borderRadius: '0.375rem', border: '1px solid #cbd5e1' }}>
                      <img
                        src={currentShift.imageUrl}
                        alt="Preview"
                        style={{ width: '50px', height: '50px', objectFit: 'cover', borderRadius: '0.25rem' }}
                        onError={(e) => { e.target.style.display = 'none'; }}
                      />
                      <span style={{ fontSize: '0.8rem', color: '#475569', flex: 1 }}>
                        Ảnh minh họa sẽ hiển thị trên ứng dụng của Worker khi nhận việc.
                      </span>
                      <button
                        type="button"
                        onClick={() => setCurrentShift({ ...currentShift, imageUrl: '' })}
                        style={{ background: 'none', border: 'none', color: '#dc2626', fontSize: '0.8rem', cursor: 'pointer', fontWeight: 600 }}
                      >
                        Gỡ ảnh
                      </button>
                    </div>
                  )}
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
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.35rem', padding: '0.85rem 1rem', backgroundColor: '#eff6ff', borderRadius: '0.5rem', marginBottom: '1rem', border: '1px solid #bfdbfe' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span style={{ fontSize: '0.875rem', color: '#1e40af' }}>
                      Dự toán thù lao ca này: <strong>{currentDurationHours}h x {currentShift.requiredWorkers} người</strong>
                    </span>
                    <span style={{ fontSize: '1.15rem', fontWeight: 800, color: '#1d4ed8' }}>
                      {currentShiftTotal.toLocaleString()} đ
                    </span>
                  </div>
                  <div style={{ fontSize: '0.75rem', color: '#64748b' }}>
                    * Tiền thù lao ca do Nhà tổ chức thanh toán trực tiếp cho nhân sự sau khi hoàn thành ca làm việc (không qua CANi).
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
                  <button
                    type="submit"
                    disabled={submitting}
                    className="eo-btn-primary"
                    style={{ background: '#2563eb' }}
                  >
                    {submitting ? 'Đang lưu...' : 'Lưu Ca'}
                  </button>
                </div>
              </form>
            ) : null}

            {/* ĐIỀU HƯỚNG CUỐI BƯỚC 2 */}
            <div className="eo-modal-footer">
              <button type="button" className="eo-btn-secondary" onClick={() => setCurrentStep(1)}>
                Quay Lại
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
                Tiếp Tục
              </button>
            </div>
          </div>
        )}

        {/* ======================================================== */}
        {/* BƯỚC 3: XÁC NHẬN & KÍCH HOẠT TÌM KIẾM (MATCHING RADAR)   */}
        {/* ======================================================== */}
        {currentStep === 3 && (
          <div className="animate-fade-in-up">
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>
              Bước 3: Xác Nhận & Kích Hoạt Tìm Kiếm Nhân Sự
            </h3>
            <p style={{ fontSize: '0.85rem', color: '#64748b', margin: '0 0 1.25rem' }}>
              Kiểm tra lại thông tin sự kiện và các ca làm việc trước khi Matching Radar AI bắt đầu quét và gửi lời mời đến các ứng viên phù hợp nhất.
            </p>

            <div style={{ backgroundColor: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '0.75rem', padding: '1.25rem', marginBottom: '1.25rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tên sự kiện:</span>
                <strong style={{ color: '#0f172a' }}>{createdEvent?.name || newEvent.name}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Địa điểm tổ chức:</span>
                <span style={{ fontWeight: 600, color: '#1e293b', textAlign: 'right', maxWidth: '65%' }}>
                  {createdEvent?.venueAddress || newEvent.venueAddress}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng số ca làm việc:</span>
                <strong>{shiftsList.length} ca</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.65rem' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng nhân sự cần tuyển:</span>
                <strong style={{ color: '#2563eb' }}>{totalWorkers} nhân sự</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '0.5rem', borderTop: '1px dashed #cbd5e1' }}>
                <span style={{ color: '#64748b', fontSize: '0.875rem' }}>Tổng dự toán thù lao ca:</span>
                <strong style={{ color: '#16a34a', fontSize: '1.05rem' }}>{totalEstimatedCost.toLocaleString()} đ</strong>
              </div>
              <div style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.35rem', textAlign: 'right' }}>
                (Thanh toán trực tiếp cho nhân sự sau khi xong ca)
              </div>
            </div>

            {/* Thông báo chính sách hạn mức gói dịch vụ */}
            <div style={{ backgroundColor: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '0.75rem', padding: '1.1rem', marginBottom: '1.5rem' }}>
              <div style={{ fontWeight: 700, color: '#15803d', marginBottom: '0.35rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <span>Chính Sách Gói Dịch Vụ & Ưu Đãi Hội Viên</span>
              </div>
              <p style={{ fontSize: '0.825rem', color: '#166534', margin: 0, lineHeight: 1.5 }}>
                Khi kích hoạt, hệ thống sẽ tự động trừ <strong>1 sự kiện</strong> trong gói dịch vụ của bạn (hoặc tự động cấp <strong>Ưu Đãi Trải Nghiệm Miễn Phí</strong> nếu đây là sự kiện đầu tiên của bạn).
              </p>
              <div style={{ marginTop: '0.5rem', fontSize: '0.8rem', color: '#15803d', fontWeight: 600 }}>
                ✓ Đã bao gồm tính năng Matching Radar AI quét ứng viên tự động trong bán kính ca làm.
              </div>
            </div>

            <div className="eo-modal-footer">
              <button
                type="button"
                className="eo-btn-secondary"
                onClick={() => setCurrentStep(2)}
                disabled={submitting}
              >
                Quay Lại Sửa Ca
              </button>
              <button
                type="button"
                className="eo-btn-primary"
                disabled={submitting}
                onClick={handleActivateMatching}
                style={{
                  background: '#2563eb',
                  padding: '0.75rem 1.75rem',
                  fontSize: '0.95rem',
                  fontWeight: 700,
                  boxShadow: '0 4px 12px rgba(37, 99, 235, 0.3)',
                }}
              >
                {submitting ? 'Đang kích hoạt...' : 'Kích Hoạt Tìm Kiếm Ngay'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default EventCreationWizardModal;
