import React from 'react';

/**
 * EventCreateModal
 * Modal tạo sự kiện mới kèm GPS Reverse Geocoding qua OpenStreetMap
 */
const EventCreateModal = ({
  isOpen,
  onClose,
  onSubmit,
  newEvent,
  setNewEvent,
  onGetCurrentLocation,
  locating,
}) => {
  if (!isOpen) return null;

  return (
    <div className="eo-modal-overlay">
      <div className="eo-modal-box">
        <h3>Tạo Sự Kiện Mới</h3>
        <form onSubmit={onSubmit}>
          <div className="eo-form-group">
            <label>Tên Sự Kiện *</label>
            <input
              type="text"
              required
              className="eo-form-input"
              placeholder="Ví dụ: Lễ Ra Mắt Sản Phẩm Mới 2026"
              value={newEvent.name}
              onChange={(e) => setNewEvent({ ...newEvent, name: e.target.value })}
            />
          </div>

          <div className="eo-form-group">
            <label>Mô Tả Sự Kiện</label>
            <textarea
              rows="3"
              className="eo-form-textarea"
              placeholder="Mô tả quy mô, đối tượng khách mời và yêu cầu chung..."
              value={newEvent.description}
              onChange={(e) => setNewEvent({ ...newEvent, description: e.target.value })}
            />
          </div>

          <div className="eo-form-group">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
              <label style={{ margin: 0 }}>Địa Điểm Tổ Chức *</label>
              <button
                type="button"
                className="eo-btn-gps"
                onClick={onGetCurrentLocation}
                disabled={locating}
                title="Tự động phát hiện vị trí GPS hiện tại và lấy địa chỉ"
              >
                {locating ? 'Đang định vị...' : 'Lấy vị trí hiện tại'}
              </button>
            </div>
            <input
              type="text"
              required
              className="eo-form-input"
              placeholder="Nhập địa chỉ hoặc bấm 'Lấy vị trí hiện tại'..."
              value={newEvent.venueAddress}
              onChange={(e) => setNewEvent({ ...newEvent, venueAddress: e.target.value })}
            />
            <span className="eo-field-helper" style={{ fontSize: '0.78rem', color: '#64748b', marginTop: '0.35rem', display: 'block' }}>
              Hệ thống tự động định vị tọa độ GPS để quét và ưu tiên các Worker trong bán kính &le; 15km.
            </span>
          </div>

          <div className="eo-modal-footer">
            <button
              type="button"
              className="eo-btn-secondary"
              onClick={onClose}
            >
              Hủy Bỏ
            </button>
            <button type="submit" className="eo-btn-primary">
              Tạo Sự Kiện
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default EventCreateModal;
