import React, { useState, useEffect, useRef, useCallback } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { searchAddressSuggestions, getAddressFromCoordinates } from '../utils/geoUtils';
import './VenueLocationPicker.css';

// Custom SVG Pin Icon cho CANi (Xanh dương hiện đại, không phụ thuộc file ảnh tĩnh)
const createCaniPinIcon = () =>
  L.divIcon({
    className: 'cani-custom-pin',
    html: `
      <div style="position: relative; width: 34px; height: 34px; transform: translate(-50%, -100%); cursor: grab;">
        <svg viewBox="0 0 24 24" width="34" height="34" fill="#2563eb" style="filter: drop-shadow(0 3px 5px rgba(0,0,0,0.35));">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
        </svg>
        <div style="position: absolute; top: 7px; left: 12px; width: 10px; height: 10px; background: white; border-radius: 50%;"></div>
      </div>
    `,
    iconSize: [0, 0],
    iconAnchor: [0, 0],
  });

/**
 * VenueLocationPicker
 * Component chọn địa điểm tổ chức sự kiện tích hợp:
 * 1. Ô tìm kiếm địa chỉ kèm Autocomplete gợi ý thông minh
 * 2. Bản đồ tương tác Leaflet (OpenStreetMap)
 * 3. Kéo thả ghim hoặc click trên bản đồ để tinh chỉnh tọa độ và tự cập nhật địa chỉ
 */
export const VenueLocationPicker = ({
  address = '',
  lat = 10.7328,
  lng = 106.7214,
  onLocationChange,
  onGetCurrentLocation,
  locating = false,
  required = true,
}) => {
  const [inputValue, setInputValue] = useState(address);
  const [suggestions, setSuggestions] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [searching, setSearching] = useState(false);

  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markerRef = useRef(null);
  const dropdownRef = useRef(null);
  const debounceTimerRef = useRef(null);

  // Đồng bộ giá trị input khi prop address từ bên ngoài thay đổi (ví dụ: bấm GPS)
  useEffect(() => {
    setInputValue(address);
  }, [address]);

  // Khởi tạo bản đồ Leaflet
  useEffect(() => {
    if (!mapContainerRef.current) return;

    const initialLat = lat || 10.7328;
    const initialLng = lng || 106.7214;

    const map = L.map(mapContainerRef.current, {
      center: [initialLat, initialLng],
      zoom: 15,
      zoomControl: true,
      attributionControl: false,
    });

    // Tile layer OpenStreetMap miễn phí
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
    }).addTo(map);

    // Marker có thể kéo thả (draggable)
    const marker = L.marker([initialLat, initialLng], {
      icon: createCaniPinIcon(),
      draggable: true,
      autoPan: true,
    }).addTo(map);

    marker.bindTooltip('Địa điểm tổ chức (Kéo để chỉnh)', {
      permanent: false,
      direction: 'top',
      offset: [0, -32],
    });

    // Khi người dùng kéo thả xong ghim
    marker.on('dragend', async () => {
      const position = marker.getLatLng();
      const newLat = parseFloat(position.lat.toFixed(6));
      const newLng = parseFloat(position.lng.toFixed(6));
      
      const resolvedAddress = await getAddressFromCoordinates(newLat, newLng);
      setInputValue(resolvedAddress);
      if (onLocationChange) {
        onLocationChange({
          address: resolvedAddress,
          lat: newLat,
          lng: newLng,
        });
      }
    });

    // Khi người dùng click bất kỳ đâu trên bản đồ
    map.on('click', async (e) => {
      const clickedLat = parseFloat(e.latlng.lat.toFixed(6));
      const clickedLng = parseFloat(e.latlng.lng.toFixed(6));

      marker.setLatLng([clickedLat, clickedLng]);
      map.panTo([clickedLat, clickedLng]);

      const resolvedAddress = await getAddressFromCoordinates(clickedLat, clickedLng);
      setInputValue(resolvedAddress);
      if (onLocationChange) {
        onLocationChange({
          address: resolvedAddress,
          lat: clickedLat,
          lng: clickedLng,
        });
      }
    });

    mapInstanceRef.current = map;
    markerRef.current = marker;

    // Fix lỗi hiển thị tile trong Modal bằng invalidateSize
    const resizeTimer = setTimeout(() => {
      map.invalidateSize();
    }, 250);

    return () => {
      clearTimeout(resizeTimer);
      map.remove();
      mapInstanceRef.current = null;
      markerRef.current = null;
    };
  }, []);

  // Cập nhật vị trí bản đồ khi tọa độ lat/lng từ ngoài thay đổi
  useEffect(() => {
    if (mapInstanceRef.current && markerRef.current && lat && lng) {
      const currentPos = markerRef.current.getLatLng();
      if (Math.abs(currentPos.lat - lat) > 0.0001 || Math.abs(currentPos.lng - lng) > 0.0001) {
        markerRef.current.setLatLng([lat, lng]);
        mapInstanceRef.current.setView([lat, lng], 15, { animate: true });
      }
    }
  }, [lat, lng]);

  // Click outside listener để đóng dropdown gợi ý
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Xử lý tìm kiếm gợi ý khi người dùng gõ
  const handleInputChange = (e) => {
    const val = e.target.value;
    setInputValue(val);

    if (onLocationChange) {
      onLocationChange({ address: val, lat, lng });
    }

    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    if (!val || val.trim().length < 2) {
      setSuggestions([]);
      setShowSuggestions(false);
      setSearching(false);
      return;
    }

    setSearching(true);
    debounceTimerRef.current = setTimeout(async () => {
      try {
        const results = await searchAddressSuggestions(val);
        setSuggestions(results);
        setShowSuggestions(results.length > 0);
      } finally {
        setSearching(false);
      }
    }, 350);
  };

  // Chọn 1 gợi ý từ danh sách
  const handleSelectSuggestion = (item) => {
    setInputValue(item.displayName);
    setShowSuggestions(false);
    setSuggestions([]);

    if (onLocationChange) {
      onLocationChange({
        address: item.displayName,
        lat: item.lat,
        lng: item.lng,
      });
    }

    if (mapInstanceRef.current && markerRef.current) {
      markerRef.current.setLatLng([item.lat, item.lng]);
      mapInstanceRef.current.setView([item.lat, item.lng], 16, { animate: true });
    }
  };

  // Xóa nội dung ô tìm kiếm
  const handleClear = () => {
    setInputValue('');
    setSuggestions([]);
    setShowSuggestions(false);
    if (onLocationChange) {
      onLocationChange({ address: '', lat, lng });
    }
  };

  return (
    <div className="venue-picker-container" ref={dropdownRef}>
      <div className="venue-picker-header">
        <label className="venue-picker-label">
          Địa Điểm Tổ Chức Sự Kiện {required && '*'}
        </label>
        {onGetCurrentLocation && (
          <button
            type="button"
            className="venue-picker-gps-btn"
            onClick={onGetCurrentLocation}
            disabled={locating}
            title="Định vị tọa độ GPS thiết bị hiện tại"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="7" />
              <line x1="12" y1="1" x2="12" y2="4" />
              <line x1="12" y1="20" x2="12" y2="23" />
              <line x1="1" y1="12" x2="4" y2="12" />
              <line x1="20" y1="12" x2="23" y2="12" />
            </svg>
            <span>{locating ? 'Đang định vị...' : 'Vị trí hiện tại'}</span>
          </button>
        )}
      </div>

      <div className="venue-search-wrapper">
        <div className="venue-search-input-box">
          <span className="venue-search-icon">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </span>
          <input
            type="text"
            required={required}
            className="venue-search-input"
            placeholder="Nhập tên địa điểm (VD: SECC, White Palace, Mỹ Đình...)"
            value={inputValue}
            onChange={handleInputChange}
            onFocus={() => {
              if (suggestions.length > 0) setShowSuggestions(true);
            }}
          />
          {inputValue && (
            <button
              type="button"
              className="venue-search-clear-btn"
              onClick={handleClear}
              title="Xóa tìm kiếm"
            >
              ✕
            </button>
          )}
        </div>

        {/* Dropdown Gợi Ý Địa Điểm */}
        {showSuggestions && (
          <ul className="venue-suggestions-dropdown">
            {searching && (
              <li className="venue-suggestions-loading">Đang tìm kiếm địa điểm...</li>
            )}
            {suggestions.map((item, index) => (
              <li
                key={`${item.lat}-${item.lng}-${index}`}
                className="venue-suggestion-item"
                onClick={() => handleSelectSuggestion(item)}
              >
                <span className="venue-suggestion-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                    <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z" />
                  </svg>
                </span>
                <div className="venue-suggestion-content">
                  <span className="venue-suggestion-name">{item.name}</span>
                  <span className="venue-suggestion-address">{item.displayName}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Bản Đồ Tương Tác Mini Leaflet */}
      <div className="venue-map-wrapper">
        <div ref={mapContainerRef} className="venue-leaflet-map" />
      </div>

      <div className="venue-map-statusbar">
        <span className="venue-map-hint">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="16" x2="12" y2="12" />
            <line x1="12" y1="8" x2="12.01" y2="8" />
          </svg>
          Click hoặc kéo thả ghim trên bản đồ để chọn đúng sảnh đón tiếp
        </span>
        <span className="venue-gps-badge" title="Tọa độ GPS hệ thống dùng quét tìm kiếm nhân sự">
          GPS: {lat?.toFixed(4)}, {lng?.toFixed(4)}
        </span>
      </div>
    </div>
  );
};

export default VenueLocationPicker;
