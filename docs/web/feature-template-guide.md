# CANi Web - Mẫu Chuẩn Cho Một Feature (Feature Golden Template)

> **Mục tiêu:** Đây là **Khuôn Mẫu Mẫu (Golden Reference)**. Khi bất kỳ ai trong nhóm nhờ AI code một chức năng mới (ví dụ: `events`, `staffing`, `attendance`, `payroll`), hãy yêu cầu AI **bắt chước chính xác cấu trúc và phân tầng** của template này.

---

## 1. Cấu Trúc File Của Một Feature Chuẩn

Giả sử chúng ta xây dựng tính năng Quản lý sự kiện (`features/events`):

```text
src/features/events/
├── api/
│   └── eventApi.ts          # Gọi backend API qua axios
├── components/
│   ├── EventCard.tsx        # UI hiển thị từng card sự kiện
│   └── EventList.tsx        # UI danh sách sự kiện (ghép EventCard)
├── hooks/
│   └── useEvents.ts         # Quản lý state tải dữ liệu, loading, error
├── types/
│   └── index.ts             # Định nghĩa Type / Interface DTO
└── index.ts                 # Barrel export (Cổng giao tiếp duy nhất ra ngoài)
```

---

## 2. Mã Nguồn Mẫu Chuẩn Từng Tầng

### 📄 Tầng 1: `types/index.ts` (Định nghĩa kiểu dữ liệu)
```typescript
export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED';

export interface EventItem {
  id: string;
  title: string;
  location: string;
  startDate: string;
  endDate: string;
  requiredStaff: number;
  status: EventStatus;
}

export interface CreateEventDto {
  title: string;
  location: string;
  startDate: string;
  endDate: string;
  requiredStaff: number;
}
```

---

### 📄 Tầng 2: `api/eventApi.ts` (Gọi API tập trung)
```typescript
import { api } from '@/services/api'; // Sử dụng instance dùng chung
import { EventItem, CreateEventDto } from '../types';

export const eventApi = {
  getEvents: async (): Promise<EventItem[]> => {
    const response = await api.get<EventItem[]>('/events');
    return response.data;
  },

  getEventById: async (id: string): Promise<EventItem> => {
    const response = await api.get<EventItem>(`/events/${id}`);
    return response.data;
  },

  createEvent: async (data: CreateEventDto): Promise<EventItem> => {
    const response = await api.post<EventItem>('/events', data);
    return response.data;
  },
};
```

---

### 📄 Tầng 3: `hooks/useEvents.ts` (Tách logic nghiệp vụ & State)
```typescript
import { useState, useEffect } from 'react';
import { eventApi } from '../api/eventApi';
import { EventItem } from '../types';

export const useEvents = () => {
  const [events, setEvents] = useState<EventItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchEvents = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await eventApi.getEvents();
      setEvents(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Lỗi không xác định khi tải sự kiện');
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

### 📄 Tầng 4: `components/EventList.tsx` (Component UI thuần túy)
```typescript
import React from 'react';
import { useEvents } from '../hooks/useEvents';

export const EventList: React.FC = () => {
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
```

---

### 📄 Tầng 5: `index.ts` (Public Barrel File)
```typescript
// Chỉ export những gì các page hoặc layout bên ngoài cần dùng
export * from './types';
export * from './components/EventList';
export * from './hooks/useEvents';
```

---

## 3. Cách Áp Dụng Khi Dùng AI
Mỗi khi bạn muốn AI viết feature mới (ví dụ: `attendance`), chỉ cần nạp câu lệnh:
> *"Hãy tạo feature `attendance` với các tính năng check-in/out. Cấu trúc thư mục, quy cách tách tầng và phân quyền API/Hook/Component PHẢI tuân thủ chính xác theo mẫu trong file `docs/web/feature-template-guide.md`."*
