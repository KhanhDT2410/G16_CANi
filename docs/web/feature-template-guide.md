# CANi Web - Mẫu Chuẩn Cho Một Feature (Feature Golden Template)

> **Mục tiêu:** Đây là **Khuôn Mẫu Mẫu (Golden Reference)**. Khi bất kỳ ai trong nhóm nhờ AI code một chức năng mới (ví dụ: `events`, `staffing`, `attendance`, `payroll`), hãy yêu cầu AI **bắt chước chính xác cấu trúc và phân tầng** của template này bằng **React + Vite thuần JavaScript (JSX)**.

---

## 1. Cấu Trúc File Của Một Feature Chuẩn

Giả sử chúng ta xây dựng tính năng Quản lý sự kiện (`features/events`):

```text
src/features/events/
├── api/
│   └── eventApi.js          # Gọi backend API qua axios
├── components/
│   ├── EventCard.jsx        # UI hiển thị từng card sự kiện
│   └── EventList.jsx        # UI danh sách sự kiện (ghép EventCard)
├── hooks/
│   └── useEvents.js         # Quản lý state tải dữ liệu, loading, error
├── types/
│   └── event.constants.js   # Định nghĩa hằng số / Enum trạng thái
└── index.js                 # Barrel export (Cổng giao tiếp duy nhất ra ngoài)
```

---

## 2. Mã Nguồn Mẫu Chuẩn Từng Tầng

### 📄 Tầng 1: `types/event.constants.js` (Hằng số trạng thái & Enum)
```javascript
export const EventStatus = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  ONGOING: 'ONGOING',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
};
```

---

### 📄 Tầng 2: `api/eventApi.js` (Gọi API tập trung)
```javascript
import { apiClient } from '@/services/api'; // Sử dụng instance dùng chung

export const eventApi = {
  getEvents: async () => {
    const response = await apiClient.get('/events');
    return response.data;
  },

  getEventById: async (id) => {
    const response = await apiClient.get(`/events/${id}`);
    return response.data;
  },

  createEvent: async (data) => {
    const response = await apiClient.post('/events', data);
    return response.data;
  },
};
```

---

### 📄 Tầng 3: `hooks/useEvents.js` (Tách logic nghiệp vụ & State)
```javascript
import { useState, useEffect } from 'react';
import { eventApi } from '../api/eventApi';

export const useEvents = () => {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchEvents = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await eventApi.getEvents();
      setEvents(data);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Lỗi không xác định khi tải sự kiện');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  return { events, loading, error, refetch: fetchEvents };
};
```

---

### 📄 Tầng 4: `components/EventList.jsx` (Component UI thuần túy)
```jsx
import React from 'react';
import { useEvents } from '../hooks/useEvents';

export const EventList = () => {
  const { events, loading, error, refetch } = useEvents();

  if (loading) return <div>Đang tải danh sách sự kiện...</div>;
  if (error) return <div>Lỗi: {error} <button onClick={refetch}>Thử lại</button></div>;

  return (
    <div className="event-list-container">
      <h3>Danh sách sự kiện ({events.length})</h3>
      {events.map((event) => (
        <div key={event.id} className="event-item">
          <h4>{event.title}</h4>
          <p>Địa điểm: {event.location}</p>
          <span>Trạng thái: {event.status}</span>
        </div>
      ))}
    </div>
  );
};

export default EventList;
```

---

### 📄 Tầng 5: `index.js` (Public Barrel File)
```javascript
// Chỉ export những gì các page hoặc layout bên ngoài cần dùng
export * from './types/event.constants';
export { EventList } from './components/EventList';
export { useEvents } from './hooks/useEvents';
```

---

## 3. Cách Áp Dụng Khi Dùng AI
Mỗi khi bạn muốn AI viết feature mới (ví dụ: `attendance`), chỉ cần nạp câu lệnh:
> *"Hãy tạo feature `attendance` bằng React + Vite thuần JavaScript (JSX). Cấu trúc thư mục, quy cách tách tầng và phân quyền API/Hook/Component PHẢI tuân thủ chính xác theo mẫu trong file `docs/web/feature-template-guide.md`."*
