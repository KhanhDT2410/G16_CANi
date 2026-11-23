# Kế hoạch tối ưu code và tốc độ tải CANi

Phạm vi là nhánh `test`, gồm Android Worker, Web EO, backend Spring Boot và Supabase.

**Thứ tự cho demo:** đo nhanh cấu hình cũ → thử pool `15/5` → đo đối chứng → tối ưu Radar Worker → tối ưu Web EO. PR 2 về API sự kiện EO có thể chạy song song với PR 1 sau khi có baseline.

| PR | Công việc | Ước lượng |
|---|---|---:|
| 0.5 | Tăng Hikari pool trên môi trường demo | Khoảng 5 phút chỉnh cấu hình, xác minh cùng PR 0 |
| 0 | Đo baseline và tác động của connection pool | 0,5–1 ngày |
| 1 | Khởi động Flutter, GPS, Radar và map tile | 2–3 ngày |
| 2 | Tách luồng đọc EO và batch truy vấn | 2–3 ngày |
| 3 | Tách UI EO, giảm bundle và polling | 2–3 ngày |
| 4 | Thu hẹp scheduler và rút ngắn transaction | 2–3 ngày |
| 5 | Tách API tìm ca gần và lọc ứng viên | 1–2 ngày |

Tổng ước lượng **10–15 ngày công cho một dev**. Phạm vi demo ưu tiên PR 0.5, PR 0 và PR 1.

## PR 0.5 · Tăng connection pool

Áp dụng cho Spring profile thực sự chạy bản demo:

```yaml
maximum-pool-size: 15
minimum-idle: 5
```

- Xác nhận cấu hình hiệu lực trước khi sửa. Trên `test`, profile `supabase` và cấu hình chung dùng `5/1`, timeout lấy connection 20 giây. Profile `local` ghi đè thành `8/2`, timeout 10 giây.
- Kiểm tra ngân sách kết nối nếu nhiều instance dùng chung DB. Supabase `cani` trả về `max_connections = 60` tại lần kiểm tra.
- Đo nhanh cấu hình cũ, áp dụng `15/5`, rồi lặp lại cùng tải. So sánh số request chờ, p95 thời gian lấy connection, số lần timeout và p95 API.
- Giữ `15/5` nếu hàng chờ giảm và DB còn đủ kết nối. Khôi phục cấu hình cũ nếu p95 xấu đi hoặc kết nối tiến sát giới hạn.

## PR 0 · Baseline trước/sau

- **Backend:** tách thời gian chờ pool, SQL và xử lý HTTP. Ghi số SQL mỗi request. Đo cả lúc scheduler chạy. Trên `test`, expire proposal chạy mỗi 5 phút, NoShow mỗi 10 phút và ShiftLifecycle mỗi 1 phút theo mặc định. Đối chiếu lại lịch hiệu lực trên bản demo.
- **Android Worker:** đo first frame, danh sách ca/lời mời đầu tiên, tile đầu tiên và thời gian GPS. Chạy trên bản release, gồm cold start, mở lại và mở từ push. Thử cache tile trống, cache đã có dữ liệu và mạng yếu.
- **Web EO:** đo `/`, reload `/eo` khi đã đăng nhập và mở chi tiết từ thông báo. Ghi thời gian `/api/auth/me`, `/api/v1/eo/events`, thời điểm dashboard có dữ liệu và số request nền trong 60 giây.
- **Đầu ra:** bảng p50/p95 trước/sau. Chạy 20 lượt cho mỗi kịch bản chính trên cùng thiết bị, mạng và dữ liệu. Chụp thống kê Supabase đầu/cuối cửa sổ đo để đối chiếu với trace ứng dụng.

## PR 1 · Flutter và Radar Worker

**File chính:** `main.dart`, `fcm_service.dart`, `home_screen.dart`, `job_radar_screen.dart`, `job_radar_provider.dart` và `job_radar_map_painter.dart`.

1. Dời xin quyền thông báo và lấy token FCM ra sau first frame. Giữ payload mở app từ push đến khi phiên và navigator sẵn sàng.
2. Tách Radar theo bản đồ, bộ lọc, danh sách ca, lời mời và eKYC. Đặt Riverpod subscription ở widget dùng dữ liệu. Mount tab ẩn khi mở lần đầu và giữ state khi chuyển tab.
3. Dùng `LocationAccuracy.medium` cho tìm ca. Tải ca từ tọa độ ưu tiên hợp lệ trước khi có thể, cập nhật GPS sau. Gộp request cùng tham số và bỏ kết quả cũ khi đổi tâm hoặc bán kính. Giữ độ chính xác hiện tại của check-in/check-out.
4. Đo tile theo nguồn. Trên `test`, nguồn chính là Google `mt*.google.com`, fallback là CARTO Voyager. Kiểm tra cache tích hợp của `flutter_map` 8.3.x trước khi thêm dependency. Ghi thời gian tới tile đầu tiên, cache hit, lỗi và fallback. Cho danh sách ca hiển thị độc lập với tile. Nếu cần tải theo vùng để dùng offline, chọn nguồn tile cho phép.
5. Giữ fallback thông báo cho thiết bị thiếu Google Play Services. Tạm dừng polling khi app ở nền và tránh request chồng nhau. Rà lại cursor khi có **trên 50 ca active cùng lúc**, dựa trên dung lượng và tần suất tải thông báo.

**Nghiệm thu:** mục tiêu giảm ít nhất **30% p95 tới first frame**. Đo riêng thời gian tới danh sách ca và tile đầu tiên. Kiểm tra mở từ push, nhận/từ chối lời mời, từ chối GPS, đổi vị trí khi có request đang chạy và thiết bị thiếu FCM.

## PR 2 · Luồng đọc sự kiện EO

**File chính:** `EoEventService.java`, `EoEventController.java`, `ShiftStaffingPolicy.java` và các repository liên quan.

1. Tách service đọc danh sách/chi tiết bằng commit nhỏ. Giữ endpoint, JSON, thứ tự, quyền sở hữu EO và lọc xóa mềm.
2. Batch dữ liệu ca, assignment, proposal, kỹ năng và thanh toán. Dùng query batch kỹ năng có sẵn. Giữ phân biệt ca chưa có assignment với ca có assignment nhưng số hợp lệ bằng 0 để fallback đếm nhân sự đúng.
3. Thêm API danh sách nhẹ có phân trang sau khi đã đối chiếu response. Giữ endpoint chi tiết theo ID để mở sự kiện từ thông báo kể cả khi nằm ngoài trang hiện tại.

**Nghiệm thu:** đối chiếu fixture 10 sự kiện × 5 ca và 30 sự kiện × 5 ca. Với cùng kích thước trang, số câu SQL không tăng theo từng event hoặc từng ca. Kiểm tra group proposal, trạng thái ca, số người, thanh toán một phần và cảnh báo thiếu nhân sự.

## PR 3 · Web EO

- Giữ `EOPage` phụ trách tab và URL. Dời tải dữ liệu về tab hoặc form sử dụng. Soát callback tạo/sửa có thể cùng gọi reload.
- Giữ lazy-load hiện có của wizard và tách theo từng bước với một nguồn state bản nháp. Kiểm tra import tĩnh trong `EOModals` và chỉ tải modal nặng khi mở.
- Khi chat đóng, dùng API đếm chưa đọc. Dừng polling khi tab ẩn. Nén ảnh hero và đo lại bundle trước khi tách Supabase SDK khỏi đường tải chung.
- Chuyển UI sang API phân trang sau PR 2. Kiểm tra tìm kiếm, Back/Forward, link từ cảnh báo khẩn, Google OAuth và PayOS callback.

**Nghiệm thu:** mục tiêu giảm ít nhất **30% p95 tới lúc dashboard có dữ liệu**. Trong 60 giây ở Tổng quan với chat đóng, số request lấy toàn bộ hội thoại bằng 0.

## PR 4 · Scheduler, transaction và Supabase

- Thay `findAll()` trong `ShiftLifecycleService` bằng truy vấn ứng viên lọc theo trạng thái và thời điểm. Batch số liệu nhân sự và trạng thái ca theo event.
- Tìm nguồn `UPDATE shifts` chậm bằng trace và đo thời gian giữ transaction. Group proposal đã dùng `afterCommit`. Áp dụng cơ chế gửi sau commit cho những đường FCM còn gửi đồng bộ trong transaction.
- Thay đổi index khi kế hoạch truy vấn cho thấy có lợi. Dùng Flyway migration mới cho thay đổi schema.

**Nghiệm thu:** chạy scheduler cùng lúc với tải dashboard. Đối soát DB test ở các mốc vào ca, hết ca, thiếu người, no-show và ghi thông báo.

## PR 5 · API tìm ca gần

- Tách `getNearbyShifts` khỏi `WorkerJobService` và giữ hợp đồng API.
- Lọc trạng thái, ca còn slot và chưa hết giờ ngay trong SQL. Batch kỹ năng và quan hệ cần cho DTO.
- Khi trace cho thấy tập ứng viên lớn, dùng bounding box để lọc sơ bộ rồi tính khoảng cách thực. Với `dispatchGroupsForShift`, dùng mốc khoảng 2.000 group active để đánh giá lại bước lọc vùng.

**Nghiệm thu:** giữ đúng bán kính, tọa độ fallback, eKYC, cảnh báo trùng giờ và ca khẩn đang diễn ra.

## Điều kiện merge

- Tách component/widget theo trách nhiệm và quy ước dưới 200 dòng của repo. Giữ các commit đổi cấu trúc và đổi hành vi dễ review riêng.
- Mỗi PR ghi số đo trước/sau, kết quả regression và cách hoàn tác. Giữ API cũ trong giai đoạn chuyển đổi frontend.
- Hai mốc giảm 30% là mục tiêu đề xuất. Chốt kết quả dựa trên số đo PR 0.
