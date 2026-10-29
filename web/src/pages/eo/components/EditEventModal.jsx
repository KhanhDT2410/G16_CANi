import React, { useState, useEffect } from 'react';

/**
 * EditEventModal
 * UC-11: Chỉnh sửa thông tin Sự kiện
 */
const EditEventModal = ({
  isOpen,
  onClose,
  event,
  onSubmit,
  submitting,
}) => {
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    venueAddress: '',
    venueLat: null,
    venueLng: null,
    eventDate: '',
    mode: 'MATCHING',
  });

  useEffect(() => {
    if (event) {
      setFormData({
        name: event.name || '',
        description: event.description || '',
        venueAddress: event.venueAddress || '',
        venueLat: event.venueLat || 10.7328,
        venueLng: event.venueLng || 106.7214,
        eventDate: event.eventDate ? event.eventDate.substring(0, 10) : '',
        mode: event.mode || 'MATCHING',
      });
    }
  }, [event]);

  if (!isOpen || !event) return null;

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(event.eventId, formData);
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 style={{ margin: 0 }}>✏️ Chỉnh Sửa Thông Tin Sự Kiện</h3>
          <button type="button" onClick={onClose} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: '#64748b' }}>✕</button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="eo-form-group">
            <label>Tên Sự Kiện *</label>
            <input
              type="text"
              required
              className="eo-form-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            />
          </div>

          <div className="eo-form-group">
            <label>Mô Tả Sự Kiện</label>
            <textarea
              rows="3"
              className="eo-form-textarea"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            />
          </div>

          <div className="eo-form-group">
            <label>Địa Điểm Tổ Chức *</label>
            <input
              type="text"
              required
              className="eo-form-input"
              value={formData.venueAddress}
              onChange={(e) => setFormData({ ...formData, venueAddress: e.target.value })}
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="eo-form-group">
              <label>Ngày Tổ Chức</label>
              <input
                type="date"
                className="eo-form-input"
                value={formData.eventDate}
                onChange={(e) => setFormData({ ...formData, eventDate: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Chế Độ Tuyển Dụng</label>
              <select
                className="eo-form-input"
                value={formData.mode}
                onChange={(e) => setFormData({ ...formData, mode: e.target.value })}
              >
                <option value="MATCHING">Điều Phối Tự Động (Matching)</option>
                <option value="INVITE_ONLY">Chỉ Mời Theo Mã (Invite Only)</option>
                <option value="HYBRID">Kết Hợp (Hybrid)</option>
              </select>
            </div>
          </div>

          <div className="eo-form-actions">
            <button type="button" className="eo-btn-cancel" onClick={onClose}>
              Hủy
            </button>
            <button type="submit" className="eo-btn-submit" disabled={submitting}>
              {submitting ? 'Đang lưu...' : 'Lưu Thay Đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditEventModal;

