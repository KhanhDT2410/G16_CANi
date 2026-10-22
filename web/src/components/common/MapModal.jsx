import React from 'react';
import './MapModal.css';

/**
 * MapModal - Hiển thị bản đồ radar GPS và bán kính quét ứng viên / khoảng cách di chuyển
 */
export const MapModal = ({
  isOpen,
  onClose,
  title = 'Tọa Độ Sự Kiện & Bán Kính Điều Phối',
  eventName = '',
  venueAddress = '',
  lat = 10.7328,
  lng = 106.7214,
  radiusKm = 15,
  distanceKm = null,
}) => {
  if (!isOpen) return null;

  const googleMapsUrl = `https://www.google.com/maps/search/?api=1&query=${lat},${lng}`;

  return (
    <div className="map-modal-backdrop" onClick={onClose}>
      <div className="map-modal-box" onClick={(e) => e.stopPropagation()}>
        <div className="map-modal-header">
          <h3 className="map-modal-title">📍 {title}</h3>
          <button type="button" className="map-modal-close" onClick={onClose}>
            &times;
          </button>
        </div>

        {/* Visual Radar GPS Sweep */}
        <div className="map-radar-container">
          <div className="radar-grid" />
          <div className="radar-circle radar-circle-1" />
          <div className="radar-circle radar-circle-2" />
          <div className="radar-circle radar-circle-3" />
          <div className="radar-sweep" />

          <div className="radar-marker">
            <div className="radar-pin" />
            <div className="radar-label">{eventName || 'Địa Điểm Sự Kiện'}</div>
          </div>
        </div>

        <div className="map-modal-body">
          <div className="map-info-grid">
            <div className="map-info-item">
              <div className="map-info-label">Địa Chỉ Tổ Chức</div>
              <div className="map-info-value">{venueAddress}</div>
            </div>

            <div className="map-info-item">
              <div className="map-info-label">Tọa Độ GPS (WGS84)</div>
              <div className="map-info-value">
                {lat.toFixed(4)}° N, {lng.toFixed(4)}° E
              </div>
            </div>

            <div className="map-info-item">
              <div className="map-info-label">Bán Kính Quét Dispatch</div>
              <div className="map-info-value" style={{ color: '#2563eb' }}>
                {radiusKm} km (Bán kính ưu tiên)
              </div>
            </div>

            <div className="map-info-item">
              <div className="map-info-label">Cự Ly Ước Tính</div>
              <div className="map-info-value" style={{ color: '#059669' }}>
                {distanceKm !== null ? `${distanceKm} km (Khoảng 12-18 phút)` : 'Tâm điểm điều phối'}
              </div>
            </div>
          </div>

          <div className="map-modal-actions">
            <button
              type="button"
              className="cani-modal-btn-cancel"
              onClick={onClose}
            >
              Đóng
            </button>
            <a
              href={googleMapsUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="btn-open-google-maps"
            >
              🗺️ Mở Bản Đồ Google Maps
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MapModal;
