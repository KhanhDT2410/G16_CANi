# CANi Mobile - Mẫu Chuẩn Cho Một Feature (Feature Golden Template)

> **Mục tiêu:** Đây là **Khuôn Mẫu (Golden Reference)** dành cho Flutter. Khi bất kỳ ai trong nhóm code một chức năng mới (ví dụ: `events`, `staffing`, `attendance`), hãy **bắt chước chính xác cấu trúc và phân tầng** của template này.

---

## 1. Cấu Trúc File Của Một Feature Chuẩn

Giả sử chúng ta xây dựng tính năng Quản lý sự kiện (`features/events`):

```text
lib/features/events/
├── data/
│   ├── event_api.dart          # Gọi backend API qua Dio
│   └── event_repository.dart   # Repository: mapping, error handling
├── models/
│   ├── event_item.dart         # Data class cho Event
│   └── create_event_dto.dart   # DTO request tạo sự kiện
├── providers/
│   └── event_list_provider.dart # Riverpod provider quản lý state
├── screens/
│   └── event_list_screen.dart  # Full-page screen
├── widgets/
│   ├── event_card.dart         # UI card sự kiện
│   └── event_filter_bar.dart   # Widget bộ lọc
└── events.dart                 # Barrel export (Public API duy nhất)
```

---

## 2. Mã Nguồn Mẫu Chuẩn Từng Tầng

### 📄 Tầng 1: `models/event_item.dart` (Định nghĩa kiểu dữ liệu)
```dart
class EventItem {
  final String id;
  final String title;
  final String location;
  final DateTime startDate;
  final DateTime endDate;
  final int requiredStaff;
  final EventStatus status;

  const EventItem({
    required this.id,
    required this.title,
    required this.location,
    required this.startDate,
    required this.endDate,
    required this.requiredStaff,
    required this.status,
  });

  factory EventItem.fromJson(Map<String, dynamic> json) {
    return EventItem(
      id: json['id'] as String,
      title: json['title'] as String,
      location: json['location'] as String,
      startDate: DateTime.parse(json['startDate'] as String),
      endDate: DateTime.parse(json['endDate'] as String),
      requiredStaff: json['requiredStaff'] as int,
      status: EventStatus.values.byName(json['status'] as String),
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'location': location,
    'startDate': startDate.toIso8601String(),
    'endDate': endDate.toIso8601String(),
    'requiredStaff': requiredStaff,
    'status': status.name,
  };
}

enum EventStatus { draft, published, ongoing, completed, cancelled }
```

### 📄 Tầng 1b: `models/create_event_dto.dart`
```dart
class CreateEventDto {
  final String title;
  final String location;
  final DateTime startDate;
  final DateTime endDate;
  final int requiredStaff;

  const CreateEventDto({
    required this.title,
    required this.location,
    required this.startDate,
    required this.endDate,
    required this.requiredStaff,
  });

  Map<String, dynamic> toJson() => {
    'title': title,
    'location': location,
    'startDate': startDate.toIso8601String(),
    'endDate': endDate.toIso8601String(),
    'requiredStaff': requiredStaff,
  };
}
```

---

### 📄 Tầng 2: `data/event_api.dart` (Gọi API tập trung)
```dart
import 'package:dio/dio.dart';
import '../models/event_item.dart';
import '../models/create_event_dto.dart';

class EventApi {
  final Dio _dio;

  EventApi(this._dio);

  Future<List<EventItem>> getEvents() async {
    final response = await _dio.get('/events');
    final List<dynamic> data = response.data['content'];
    return data.map((json) => EventItem.fromJson(json)).toList();
  }

  Future<EventItem> getEventById(String id) async {
    final response = await _dio.get('/events/$id');
    return EventItem.fromJson(response.data['data']);
  }

  Future<EventItem> createEvent(CreateEventDto dto) async {
    final response = await _dio.post('/events', data: dto.toJson());
    return EventItem.fromJson(response.data['data']);
  }
}
```

---

### 📄 Tầng 2b: `data/event_repository.dart` (Repository Pattern)
```dart
import 'event_api.dart';
import '../models/event_item.dart';
import '../models/create_event_dto.dart';

class EventRepository {
  final EventApi _api;

  EventRepository(this._api);

  Future<List<EventItem>> fetchEvents() async {
    try {
      return await _api.getEvents();
    } on DioException catch (e) {
      throw Exception(
        e.response?.data?['message'] ?? 'Lỗi khi tải danh sách sự kiện',
      );
    }
  }

  Future<EventItem> fetchEventById(String id) async {
    try {
      return await _api.getEventById(id);
    } on DioException catch (e) {
      throw Exception(
        e.response?.data?['message'] ?? 'Lỗi khi tải chi tiết sự kiện',
      );
    }
  }

  Future<EventItem> createEvent(CreateEventDto dto) async {
    try {
      return await _api.createEvent(dto);
    } on DioException catch (e) {
      throw Exception(
        e.response?.data?['message'] ?? 'Lỗi khi tạo sự kiện',
      );
    }
  }
}
```

---

### 📄 Tầng 3: `providers/event_list_provider.dart` (Riverpod State Management)
```dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/event_api.dart';
import '../data/event_repository.dart';
import '../models/event_item.dart';
import 'package:cani/core/network/dio_client.dart';

// Provider cho EventApi (DI qua Riverpod)
final eventApiProvider = Provider<EventApi>((ref) {
  final dio = ref.watch(dioProvider);
  return EventApi(dio);
});

// Provider cho EventRepository
final eventRepositoryProvider = Provider<EventRepository>((ref) {
  final api = ref.watch(eventApiProvider);
  return EventRepository(api);
});

// AsyncNotifierProvider cho danh sách sự kiện
final eventListProvider =
    AsyncNotifierProvider<EventListNotifier, List<EventItem>>(
  EventListNotifier.new,
);

class EventListNotifier extends AsyncNotifier<List<EventItem>> {
  @override
  Future<List<EventItem>> build() async {
    final repository = ref.watch(eventRepositoryProvider);
    return repository.fetchEvents();
  }

  Future<void> refresh() async {
    state = const AsyncValue.loading();
    state = await AsyncValue.guard(() {
      final repository = ref.read(eventRepositoryProvider);
      return repository.fetchEvents();
    });
  }
}
```

---

### 📄 Tầng 4: `screens/event_list_screen.dart` (UI Screen thuần túy)
```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/event_list_provider.dart';
import '../widgets/event_card.dart';

class EventListScreen extends ConsumerWidget {
  const EventListScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final eventsAsync = ref.watch(eventListProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Danh sách sự kiện')),
      body: eventsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('Lỗi: $error'),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.read(eventListProvider.notifier).refresh(),
                child: const Text('Thử lại'),
              ),
            ],
          ),
        ),
        data: (events) => RefreshIndicator(
          onRefresh: () => ref.read(eventListProvider.notifier).refresh(),
          child: ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: events.length,
            itemBuilder: (context, index) => EventCard(event: events[index]),
          ),
        ),
      ),
    );
  }
}
```

---

### 📄 Tầng 4b: `widgets/event_card.dart` (Widget nhỏ, tái sử dụng)
```dart
import 'package:flutter/material.dart';
import '../models/event_item.dart';

class EventCard extends StatelessWidget {
  final EventItem event;

  const EventCard({super.key, required this.event});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(event.title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Row(
              children: [
                const Icon(Icons.location_on_outlined, size: 16),
                const SizedBox(width: 4),
                Expanded(child: Text(event.location)),
              ],
            ),
            const SizedBox(height: 8),
            Chip(label: Text(event.status.name.toUpperCase())),
          ],
        ),
      ),
    );
  }
}
```

---

### 📄 Tầng 5: `events.dart` (Barrel Export)
```dart
// Public API - Chỉ export những gì bên ngoài feature được sử dụng
export 'models/event_item.dart';
export 'providers/event_list_provider.dart';
export 'screens/event_list_screen.dart';
```

---

## 3. Cách Áp Dụng Khi Dùng AI
Mỗi khi bạn muốn AI viết feature mới (ví dụ: `attendance`), chỉ cần nạp câu lệnh:
> *"Hãy tạo feature `attendance` với các tính năng check-in/out qua QR code. Cấu trúc thư mục, quy cách tách tầng Model/API/Repository/Provider/Screen/Widget và barrel export PHẢI tuân thủ chính xác theo mẫu trong file `docs/mobile/feature-template-guide.md`."*
