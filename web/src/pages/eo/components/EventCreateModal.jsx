import React from 'react';
import VenueLocationPicker from './VenueLocationPicker';

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
