import React, { useState, useEffect, useRef } from 'react';
import { eoService } from '../../../services/eoService';
import { showToast } from '../../../components/common/Toast';

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
    startTime: '',
    endTime: '',
    imageUrl: '',
    mode: 'MATCHING',
  });
  const [uploadingImg, setUploadingImg] = useState(false);
  const fileInputRef = useRef(null);

  const toDateTimeInputVal = (date) => {
    if (!date) return '';
    const d = new Date(date);
    if (isNaN(d.getTime())) return '';
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  };

  useEffect(() => {
    if (event) {
      setFormData({
        name: event.name || '',
        description: event.description || '',
        venueAddress: event.venueAddress || '',
        venueLat: event.venueLat || 10.7328,
        venueLng: event.venueLng || 106.7214,
        eventDate: event.eventDate ? event.eventDate.substring(0, 10) : '',
        startTime: event.startTime ? toDateTimeInputVal(event.startTime) : '',
        endTime: event.endTime ? toDateTimeInputVal(event.endTime) : '',
        imageUrl: event.imageUrl || '',
        mode: event.mode || 'MATCHING',
      });
    }
  }, [event]);

  if (!isOpen || !event) return null;

  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      showToast('Vui lòng chọn tệp hình ảnh (JPG, PNG, WEBP, GIF)', 'error');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showToast('Dung lượng ảnh tối đa là 5MB', 'error');
      return;
    }
    setUploadingImg(true);
    try {
      const res = await eoService.uploadImage(file);
      const url = res.url || res;
      setFormData((prev) => ({ ...prev, imageUrl: url }));
      showToast('Tải ảnh poster sự kiện thành công!', 'success');
    } catch (err) {
      showToast('Lỗi khi tải ảnh: ' + (err.response?.data?.message || err.message), 'error');
    } finally {
      setUploadingImg(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (formData.startTime && formData.endTime) {
      const s = new Date(formData.startTime);
      const end = new Date(formData.endTime);
      if (s >= end) {
        showToast('Thời gian kết thúc sự kiện phải diễn ra sau thời gian bắt đầu!', 'error');
        return;
      }
    }
    const payload = {
      ...formData,
      startTime: formData.startTime ? new Date(formData.startTime).toISOString() : null,
      endTime: formData.endTime ? new Date(formData.endTime).toISOString() : null,
      imageUrl: formData.imageUrl?.trim() || null,
    };
    onSubmit(event.eventId, payload);
  };

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <h3 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700, color: '#1e293b' }}>Chỉnh Sửa Thông Tin Sự Kiện</h3>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '0.9rem',
              cursor: 'pointer',
              color: '#64748b',
              fontWeight: 600,
            }}
          >
            Đóng
          </button>
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
              <label>Thời Gian Bắt Đầu</label>
              <input
                type="datetime-local"
                className="eo-form-input"
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
              />
            </div>

            <div className="eo-form-group">
              <label>Thời Gian Kết Thúc</label>
              <input
                type="datetime-local"
                className="eo-form-input"
                value={formData.endTime}
                onChange={(e) => setFormData({ ...formData, endTime: e.target.value })}
              />
            </div>
          </div>

          {/* ẢNH POSTER / BANNER SỰ KIỆN - CHỈ TẢI ẢNH TỪ MÁY TÍNH */}
          <div className="eo-form-group">
            <label style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Ảnh Poster / Banner Sự Kiện (Tùy chọn)</span>
              <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Chỉ tải ảnh từ máy tính (JPG, PNG - Tối đa 5MB)</span>
            </label>

            <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '0.5rem', flexWrap: 'wrap' }}>
              <input
                type="file"
                ref={fileInputRef}
                accept="image/*"
                style={{ display: 'none' }}
                onChange={handleImageUpload}
              />
              <button
                type="button"
                disabled={uploadingImg}
                onClick={() => fileInputRef.current?.click()}
                className="eo-btn-secondary"
                style={{ fontSize: '0.825rem', padding: '0.45rem 0.95rem', whiteSpace: 'nowrap', fontWeight: 600 }}
              >
                {uploadingImg ? 'Đang tải ảnh lên...' : 'Tải ảnh từ máy tính'}
              </button>
              <span style={{ fontSize: '0.78rem', color: '#64748b' }}>
                {formData.imageUrl ? 'Đã chọn ảnh từ máy tính' : 'Chưa chọn ảnh từ máy tính'}
              </span>
            </div>

            {formData.imageUrl && (
              <div style={{ marginTop: '0.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem', padding: '0.5rem', backgroundColor: '#ffffff', borderRadius: '0.375rem', border: '1px solid #cbd5e1' }}>
                <img
                  src={formData.imageUrl}
                  alt="Event Poster Preview"
                  style={{ width: '70px', height: '42px', objectFit: 'cover', borderRadius: '0.25rem' }}
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
                <span style={{ fontSize: '0.8rem', color: '#15803d', fontWeight: 600, flex: 1 }}>
                  Đã tải ảnh minh họa từ máy tính
                </span>
                <button
                  type="button"
                  onClick={() => setFormData({ ...formData, imageUrl: '' })}
                  style={{ background: 'none', border: 'none', color: '#dc2626', fontSize: '0.8rem', cursor: 'pointer', fontWeight: 600 }}
                >
                  Gỡ ảnh
                </button>
              </div>
            )}
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

          <div className="eo-modal-footer">
            <button type="button" className="eo-btn-secondary" onClick={onClose}>
              Hủy Bỏ
            </button>
            <button type="submit" className="eo-btn-primary" disabled={submitting}>
              {submitting ? 'Đang lưu...' : 'Lưu Thay Đổi'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EditEventModal;

