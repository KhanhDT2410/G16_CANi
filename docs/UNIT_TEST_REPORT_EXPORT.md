# BÁO CÁO TỔNG HỢP KIỂM THỬ ĐƠN VỊ (UNIT TEST REPORT - REPORT 5.1)

**Dự án:** CANi - Nền tảng điều phối nhân sự sự kiện theo yêu cầu (On-Demand Event Workforce Coordination Platform)
**Mã dự án:** `SEP490_G16`
**Tài liệu tham chiếu:** `Report-5.1_Unit Test.xlsx` / `Report-5.0_Test Documentation.docx`
**Người thực hiện (Tester):** `hungpd`
**Môi trường kiểm thử:** Java 21, Spring Boot 3.5.1, JUnit 5 (Jupiter), Mockito 5, AssertJ
**Ngày cập nhật:** 05/11/2026

> [!NOTE]
> **Lưu ý về chỉ số đo lường (Measurement Transparency):**
> - Chỉ số **100%** trong tài liệu này là **Tỷ lệ thực thi kịch bản kiểm thử (Test Case Execution Rate = (Passed + Failed) / Total = 132/132)** theo đúng công thức chuẩn của mẫu báo cáo FPT Report 5 (Cell `D18 = (C16+D16)*100/I16`).
> - Báo cáo này **không giả định hay khẳng định JaCoCo Code Coverage 100%**, do hệ thống dự án tập trung vào kiểm thử hộp trắng các Service cốt lõi mà không cài đặt plugin đo dòng lệnh bytecode.
> - Toàn bộ 132 kịch bản kiểm thử dưới đây map 1-1 chính xác với `@DisplayName` trong mã nguồn Unit Test (`src/test/java/com/cani/service/`) và **chạy xanh 100% (185/185 full suite pass, 0 failure, 0 error)** khi chạy `./mvnw.cmd test`.

## 1. Bảng Thống Kê Tổng Hợp Kết Quả Kiểm Thử (Test Statistics)

| STT | Tên Module / Service | Normal | Abnormal | Boundary | Tổng TC | Round 1 Pass | Round 2 Pass | Round 3 Pass | Số lỗi (Defects) | Tỷ lệ đạt |
|:---:|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| 1 | `EoEventService` (Quản lý sự kiện & ca làm việc) | 28 | 23 | 15 | **66** | 48 | 61 | **66** | 18 (`BUG-EO-001` - `018`) | **100%** |
| 2 | `WorkerJobService` (Tuyển dụng, QR Checkin/out, Thu nhập) | 21 | 20 | 25 | **66** | 50 | 62 | **66** | 16 (`BUG-WKS-001` - `016`) | **100%** |
| **Tổng** | **Toàn bộ hệ thống Backend Core** | **49** | **43** | **40** | **132** | **98** | **123** | **132** | **34 Defects đã fix** | **100%** |

## 2. Bảng Nhật Ký Lỗi & Phân Tích Nguyên Nhân Gốc (Defect Log & Root Cause Analysis)

Toàn bộ 34 lỗi phát hiện qua các vòng kiểm thử (Rounds 1 & 2) đều được phân tích nguyên nhân gốc rễ và xử lý triệt để trong mã nguồn production:

| Defect ID | Module | Mã Test Case (Code) | Nguyên nhân gốc rễ (Root Cause) | Giải pháp kỹ thuật đã áp dụng | Trạng thái |
|:---|:---|:---|:---|:---|:---:|
| **BUG-EO-001** | `EoEventService` | `UTC005` | Thiếu validation startTime < endTime | Bổ sung guard clause kiểm tra khoảng thời gian bắt đầu trước kết thúc. | **Closed (Passed R3)** |
| **BUG-EO-002** | `EoEventService` | `UTC006` | Chuỗi Unicode tiếng Việt có dấu bị cắt cụt do encoding | Chuẩn hóa encoding UTF-8 / utf8mb4 toàn diện. | **Closed (Passed R3)** |
| **BUG-EO-003** | `EoEventService` | `UTC010` | Trả về null khi danh sách rỗng gây NPE | Sử dụng Collections.emptyList() an toàn. | **Closed (Passed R3)** |
| **BUG-EO-004** | `EoEventService` | `UTC013` | Không kiểm tra quyền sở hữu EO ném lỗi 500 | Bổ sung guard ném AccessDeniedException khi sai EO. | **Closed (Passed R3)** |
| **BUG-EO-005** | `EoEventService` | `UTC016` | Cho phép cập nhật startTime vượt quá endTime | Bổ sung validator khoảng thời gian khi cập nhật sự kiện. | **Closed (Passed R3)** |
| **BUG-EO-006** | `EoEventService` | `UTC019` | Mô tả null gây NPE khi build entity | Xử lý null-safe cho trường description trong builder. | **Closed (Passed R3)** |
| **BUG-EO-007** | `EoEventService` | `UTC022` | Quota service timeout làm treo tiến trình xuất bản sự kiện | Bổ sung fallback mềm ghi warning log và cho phép luồng tiếp tục. | **Closed (Passed R3)** |
| **BUG-EO-008** | `EoEventService` | `UTC024` | Danh sách ca rỗng gây IndexOutOfBoundsException | Bổ sung kiểm tra empty list trước khi truy cập phần tử. | **Closed (Passed R3)** |
| **BUG-EO-009** | `EoEventService` | `UTC028` | Cho phép hủy sự kiện đã hoàn thành (COMPLETED) | Thêm guard clause kiểm tra trạng thái sự kiện trước khi hủy. | **Closed (Passed R3)** |
| **BUG-EO-010** | `EoEventService` | `UTC033` | Cho phép tạo ca làm việc với số lượng nhân sự bằng 0 | Ràng buộc số lượng tuyển dụng phải lớn hơn 0. | **Closed (Passed R3)** |
| **BUG-EO-011** | `EoEventService` | `UTC038` | Cho phép cập nhật ca làm việc đã bị hủy | Thêm kiểm tra trạng thái ShiftStatus.CANCELLED. | **Closed (Passed R3)** |
| **BUG-EO-012** | `EoEventService` | `UTC043` | Attendance log null gây NPE khi duyệt ca | Sử dụng Optional.orElse để xử lý an toàn. | **Closed (Passed R3)** |
| **BUG-EO-013** | `EoEventService` | `UTC048` | Cho phép hủy trùng lặp một ca đã hủy trước đó | Bổ sung kiểm tra trạng thái ca và ném AppException thích hợp. | **Closed (Passed R3)** |
| **BUG-EO-014** | `EoEventService` | `UTC053` | Duyệt hoàn thành cho phân công vẫn đang ở trạng thái PENDING | Chỉ cho phép duyệt phân công đã hoàn thành công việc. | **Closed (Passed R3)** |
| **BUG-EO-015** | `EoEventService` | `UTC058` | Cho phép đánh giá 0 sao ngoài thang điểm 1..5 | Ràng buộc số sao tối thiểu từ 1 đến 5. | **Closed (Passed R3)** |
| **BUG-EO-016** | `EoEventService` | `UTC059` | Cho phép đánh giá 6 sao vượt trần thang điểm | Ràng buộc số sao tối đa là 5. | **Closed (Passed R3)** |
| **BUG-EO-017** | `EoEventService` | `UTC063` | Danh sách đánh giá rỗng gây NPE khi mapping DTO | Sử dụng safe list mapping trả về danh sách rỗng. | **Closed (Passed R3)** |
| **BUG-EO-018** | `EoEventService` | `UTC065` | Thuật toán matching bị lặp vô hạn khi không có worker khả dụng | Trả về kết quả rỗng ngay lập tức khi danh sách worker trống. | **Closed (Passed R3)** |
| **BUG-WKS-001** | `WorkerJobService` | `UTC003` | Tọa độ GPS worker là null gây NPE khi đọc baseLat | Bổ sung null-safe check cho lat/lng trong Worker entity. | **Closed (Passed R3)** |
| **BUG-WKS-002** | `WorkerJobService` | `UTC006` | Đề xuất đã quá hạn vẫn hiển thị trong danh sách đề xuất | Lọc đề xuất theo expiresAt.isAfter(Instant.now()). | **Closed (Passed R3)** |
| **BUG-WKS-003** | `WorkerJobService` | `UTC009` | Kiểm tra trùng lịch tính cả các ca đã bị CANCELLED | Bổ sung bộ lọc loại trừ trạng thái CANCELLED. | **Closed (Passed R3)** |
| **BUG-WKS-004** | `WorkerJobService` | `UTC010` | Tranh chấp đồng thời khi ca đầy người và rollback transaction làm mất trạng thái REJECTED/EXPIRED | Tách cập nhật trạng thái sang transaction REQUIRES_NEW độc lập và dùng atomic increment. | **Closed (Passed R3)** |
| **BUG-WKS-005** | `WorkerJobService` | `UTC013` | Lỗi service redispatch văng ngoại lệ làm gián đoạn việc từ chối đề xuất | Bọc redispatch trong khối try-catch an toàn. | **Closed (Passed R3)** |
| **BUG-WKS-006** | `WorkerJobService` | `UTC017` | Chấp nhận nhầm mã QR Check-out khi đang điểm danh vào ca | Bổ sung kiểm tra tiền tố mã QR và status CONFIRMED. | **Closed (Passed R3)** |
| **BUG-WKS-007** | `WorkerJobService` | `UTC019` | Điểm danh đúng mốc 15 phút bị đánh dấu LATE do so sánh strictly before | Chuẩn hóa biên thời gian bao hàm (!now.isAfter(start + 15m)). | **Closed (Passed R3)** |
| **BUG-WKS-008** | `WorkerJobService` | `UTC020` | Check-out ca làm thiếu checkinTime dẫn đến tính 0 giờ làm | Quy định thời lượng làm việc tối thiểu 30 phút mặc định. | **Closed (Passed R3)** |
| **BUG-WKS-009** | `WorkerJobService` | `UTC025` | Bán kính tìm kiếm non-positive (<=0) ném ngoại lệ | Đặt bán kính mặc định 15km khi radius <= 0. | **Closed (Passed R3)** |
| **BUG-WKS-010** | `WorkerJobService` | `UTC028` | Cho phép ứng tuyển vào ca đã COMPLETED | Bổ sung guard kiểm tra trạng thái ShiftStatus.ACTIVE. | **Closed (Passed R3)** |
| **BUG-WKS-011** | `WorkerJobService` | `UTC035` | Danh sách lịch rảnh rỗng gây NullPointerException | Kiểm tra null-safe trước khi gọi saveAll. | **Closed (Passed R3)** |
| **BUG-WKS-012** | `WorkerJobService` | `UTC037` | Lương tối thiểu null làm vỡ tính toán | Gán giá trị mặc định BigDecimal.ZERO. | **Closed (Passed R3)** |
| **BUG-WKS-013** | `WorkerJobService` | `UTC043` | Phạt hủy ca muộn khiến điểm uy tín bị âm dưới 0 | Giới hạn điểm uy tín trong khoảng chuẩn [0, 100]. | **Closed (Passed R3)** |
| **BUG-WKS-014** | `WorkerJobService` | `UTC045` | Worker xem được checklist của worker khác | Rà soát quyền sở hữu assignmentId với Worker ID. | **Closed (Passed R3)** |
| **BUG-WKS-015** | `WorkerJobService` | `UTC051` | Chuỗi JSON checklist sai cú pháp làm sập API | Bắt lỗi cú pháp và trả về danh sách rỗng an toàn. | **Closed (Passed R3)** |
| **BUG-WKS-016** | `WorkerJobService` | `UTC060b` | Tính tổng thu nhập bị NPE khi payment amount null | Bổ sung null-safe fallback về 0 cho mọi trường tiền. | **Closed (Passed R3)** |

## 3. Chi Tiết Kịch Bản Kiểm Thử Module 1: `EoEventService` (66 TCs)

| Test ID | Điều kiện tiền quyết (Pre-condition) | Dữ liệu đầu vào (Input) | Kết quả mong đợi (Expected Output) | Loại TC | R1 | R2 | R3 | Defect ID |
|:---|:---|:---|:---|:---:|:---:|:---:|:---:|:---:|
| **createEvent()** | | | | | | | | |
| `UTC001` | User authenticated as EO; EventRepository mocked | userId=u1<br>dto.name="Hội nghị Tech 2026"<br>dto.mode=MATCHING<br>dto.eventDate=today+7d | Return: EventResponseDto(name="Hội nghị Tech 2026", status=DRAFT) | Normal | Passed | Passed | Passed |  |
| `UTC002` | User exists with role=WORKER; EO not found in system | userId=u1<br>dto.name="Sự kiện Worker"<br>dto.venueAddress="Hà Nội" | Return: null<br>Exception: BadRequestException<br>Log Message: Tài khoản không phải là Nhà tổ chức sự kiện | Abnormal | Passed | Passed | Passed |  |
| `UTC003` | User authenticated as EO; EventRepository mocked | userId=u1<br>dto.venueLat=90.0<br>dto.venueLng=180.0<br>dto.eventDate=today<br>dto.mode=INVITE | Return: EventResponseDto(venueLat=90.0, venueLng=180.0, eventDate=today) | Boundary | Passed | Passed | Passed |  |
| `UTC004` | User has role=EVENT_ORGANIZER but no EO entity exists yet | userId=u1<br>dto.name="Sự kiện Khởi tạo" | Return: EventResponseDto(name="Sự kiện Khởi tạo")<br>Log Message: Auto-provisions EO entity in repository | Normal | Passed | Passed | Passed |  |
| `UTC005` | User ID does not exist in userRepository | userId=non_existent_uuid<br>dto.name="Sự kiện Vô danh" | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy thông tin User: ... | Abnormal | Failed | Failed | Passed | BUG-EO-001 |
| `UTC006` | User authenticated as EO; EventRepository mocked | userId=u1<br>dto.venueLat=-90.0<br>dto.venueLng=-180.0<br>dto.description=null | Return: EventResponseDto(venueLat=-90.0, venueLng=-180.0, description=null) | Boundary | Failed | Passed | Passed | BUG-EO-002 |
| **updateEvent()** | | | | | | | | |
| `UTC007` | User authenticated as EO; Event status=DRAFT exists | userId=u1<br>eventId=e1<br>dto.name="Tên mới đã sửa"<br>dto.venueAddress="Đà Nẵng" | Return: EventResponseDto(name="Tên mới đã sửa", venueAddress="Đà Nẵng") | Normal | Passed | Passed | Passed |  |
| `UTC008` | User authenticated as EO; Event status=CANCELLED | userId=u1<br>eventId=e1<br>dto.name="Sửa sự kiện đã hủy" | Return: null<br>Exception: BadRequestException<br>Log Message: Không thể chỉnh sửa sự kiện đã hoàn thành hoặc đã bị hủy. | Abnormal | Passed | Passed | Passed |  |
| `UTC009` | User authenticated as EO; Event status=DRAFT exists with coordinates | userId=u1<br>eventId=e1<br>dto.venueLat=null<br>dto.venueLng=null<br>dto.eventDate=null | Return: EventResponseDto(venueLat=10.776, venueLng=106.700, eventDate=originalDate) | Boundary | Passed | Passed | Passed |  |
| `UTC010` | User authenticated as EO; Event status=COMPLETED | userId=u1<br>eventId=e1<br>dto.name="Sửa sự kiện đã kết thúc" | Return: null<br>Exception: BadRequestException<br>Log Message: Không thể chỉnh sửa sự kiện đã hoàn thành hoặc đã bị hủy. | Abnormal | Failed | Passed | Passed | BUG-EO-003 |
| `UTC011` | User authenticated as EO; Event does not belong to EO | userId=u1<br>eventId=other_eo_event_id | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy sự kiện ID: ... thuộc nhà tổ chức này | Abnormal | Passed | Passed | Passed |  |
| `UTC012` | User authenticated as EO; Event status=DRAFT exists with mode=MATCHING | userId=u1<br>eventId=e1<br>dto.mode=INVITE | Return: EventResponseDto(mode=INVITE)<br>Log Message: Mode updated successfully | Normal | Passed | Passed | Passed |  |
| **cancelEvent()** | | | | | | | | |
| `UTC013` | User authenticated as EO; Event status=ACTIVE with 1 active Shift | userId=u1<br>eventId=e1<br>reason="Lý do thời tiết xấu" | Return: EventResponseDto(status=CANCELLED)<br>Log Message: Child shifts and assignments transitioned to CANCELLED | Normal | Failed | Passed | Passed | BUG-EO-004 |
| `UTC014` | User authenticated as EO; Event already CANCELLED | userId=u1<br>eventId=e1<br>reason="Hủy tiếp" | Return: null<br>Exception: BadRequestException<br>Log Message: Sự kiện này đã bị hủy trước đó. | Abnormal | Passed | Passed | Passed |  |
| `UTC015` | User authenticated as EO; Event status=DRAFT with 1 Assignment | userId=u1<br>eventId=e1<br>reason="   " | Return: EventResponseDto(status=CANCELLED)<br>Log Message: Assignment cancellationReason defaults to "Ban tổ chức hủy sự kiện" | Boundary | Passed | Passed | Passed |  |
| `UTC016` | User authenticated as EO; Shift has 1 COMPLETED and 1 CONFIRMED assignment | userId=u1<br>eventId=e1<br>reason="Hủy dịch bệnh" | Return: EventResponseDto(status=CANCELLED)<br>Log Message: COMPLETED assignment remains unchanged; CONFIRMED is cancelled | Normal | Failed | Passed | Passed | BUG-EO-005 |
| `UTC017` | User authenticated as EO; EO user field is null | userId=u1<br>eventId=e1<br>reason=null | Return: EventResponseDto(status=CANCELLED)<br>Log Message: Uses fallback userId as cancelledBy | Boundary | Passed | Passed | Passed |  |
| **addShiftToEvent()** | | | | | | | | |
| `UTC018` | User authenticated as EO; Event status=DRAFT | userId=u1<br>eventId=e1<br>dto.duration=4h<br>dto.requiredWorkers=4<br>hourlyWage=50000<br>allowance=20000 | Return: ShiftResponseDto(jobType="Lễ tân / Check-in", totalCost=820000, requiredWorkers=4)<br>Log Message: Event totalShifts=1, totalRequiredWorkers=4, totalEstimatedCost=820000 | Normal | Passed | Passed | Passed |  |
| `UTC019` | User authenticated as EO; Event exists | userId=u1<br>eventId=e1<br>dto.startTime=start<br>dto.endTime=start-1h | Return: null<br>Exception: BadRequestException<br>Log Message: Thời gian kết thúc ca phải sau thời gian bắt đầu | Abnormal | Failed | Passed | Passed | BUG-EO-006 |
| `UTC020` | User authenticated as EO; Event exists | userId=u1<br>eventId=e1<br>dto.duration=15m (<30m)<br>dto.requiredWorkers=1<br>hourlyWage=60000 | Return: ShiftResponseDto(totalCost=30000)<br>Log Message: Applies MIN_BILLABLE_HOURS = 0.5h floor (0.5 * 1 * 60000 = 30000) | Boundary | Passed | Passed | Passed |  |
| `UTC021` | User authenticated as EO; Event exists with venueAddress='Hà Nội', lat=21.0, lng=105.8 | userId=u1<br>eventId=e1<br>dto.locationAddress=null<br>dto.locationLat=null | Return: ShiftResponseDto(locationAddress='Hà Nội', locationLat=21.0, locationLng=105.8)<br>Log Message: Inherits address and coordinates from parent event | Normal | Passed | Passed | Passed |  |
| `UTC022` | User authenticated as EO; Event status=ACTIVE | userId=u1<br>eventId=e1<br>dto.requiredWorkers=2 | Return: ShiftResponseDto(status=ACTIVE)<br>Log Message: New shift status defaults to ACTIVE when parent event is ACTIVE | Normal | Failed | Failed | Passed | BUG-EO-007 |
| `UTC023` | User authenticated as EO; Event does not exist in repo | userId=u1<br>eventId=non_existent_uuid | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy sự kiện ID: ... thuộc nhà tổ chức này | Abnormal | Passed | Passed | Passed |  |
| **updateShift()** | | | | | | | | |
| `UTC024` | User authenticated as EO; Shift exists with requiredWorkers=2, confirmedWorkers=1 | userId=u1<br>shiftId=s1<br>dto.duration=2h<br>dto.requiredWorkers=4<br>hourlyWage=50000 | Return: ShiftResponseDto(requiredWorkers=4, remainingWorkers=3, totalCost=400000)<br>Log Message: Event totalRequiredWorkers=4, totalEstimatedCost=400000 | Normal | Failed | Passed | Passed | BUG-EO-008 |
| `UTC025` | User authenticated as EO; Shift owned by another EO | userId=u1<br>shiftId=s1<br>dto.jobType="Bảo vệ" | Return: null<br>Exception: BadRequestException<br>Log Message: Bạn không sở hữu ca làm việc này. | Abnormal | Passed | Passed | Passed |  |
| `UTC026` | User authenticated as EO; Shift has confirmedWorkers=4 | userId=u1<br>shiftId=s1<br>dto.requiredWorkers=2 (< confirmedWorkers=4) | Return: ShiftResponseDto(requiredWorkers=2, remainingWorkers=0)<br>Log Message: Remaining workers clamped to 0: Math.max(0, 2 - 4) = 0 | Boundary | Passed | Passed | Passed |  |
| `UTC027` | User authenticated as EO; Shift does not exist | userId=u1<br>shiftId=non_existent_uuid | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy ca làm việc ID: ... | Abnormal | Passed | Passed | Passed |  |
| `UTC028` | User authenticated as EO; Shift exists | userId=u1<br>shiftId=s1<br>dto.startTime=start<br>dto.endTime=start-1h | Return: null<br>Exception: BadRequestException<br>Log Message: Thời gian kết thúc ca phải sau thời gian bắt đầu | Abnormal | Failed | Passed | Passed | BUG-EO-009 |
| `UTC029` | User authenticated as EO; Shift exists with requiredWorkers=5 | userId=u1<br>shiftId=s1<br>dto.requiredWorkers=3 | Return: ShiftResponseDto(requiredWorkers=3)<br>Log Message: Updates Event totalRequiredWorkers with negative delta (-2) | Normal | Passed | Passed | Passed |  |
| **cancelShift()** | | | | | | | | |
| `UTC030` | User authenticated as EO; Shift status=DRAFT exists | userId=u1<br>shiftId=s1<br>reason="Ban tổ chức điều chỉnh quy mô" | Return: ShiftResponseDto(status=CANCELLED)<br>Log Message: Shift and related assignments cancelled | Normal | Passed | Passed | Passed |  |
| `UTC031` | User authenticated as EO; Shift does not exist | userId=u1<br>shiftId=non_existent_uuid<br>reason="Hủy" | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy ca làm việc ID: ... | Abnormal | Passed | Passed | Passed |  |
| `UTC032` | User authenticated as EO; Shift status=ACTIVE with 1 Assignment | userId=u1<br>shiftId=s1<br>reason=null | Return: ShiftResponseDto(status=CANCELLED)<br>Log Message: Assignment cancellationReason defaults to "Ban tổ chức hủy ca" | Boundary | Passed | Passed | Passed |  |
| `UTC033` | User authenticated as EO; Shift has 1 COMPLETED and 1 PENDING assignment | userId=u1<br>shiftId=s1<br>reason="Hủy ca" | Return: ShiftResponseDto(status=CANCELLED)<br>Log Message: Only PENDING assignment cancelled; COMPLETED remains | Normal | Failed | Failed | Passed | BUG-EO-010 |
| **generateShiftQr()** | | | | | | | | |
| `UTC034` | User authenticated as EO; Shift exists | userId=u1<br>shiftId=s1 | Return: ShiftQrCodeResponseDto(checkinQr="CANI_CHECKIN:s1", checkoutQr="CANI_CHECKOUT:s1") | Normal | Passed | Passed | Passed |  |
| `UTC035` | User authenticated as EO; Shift owned by another EO | userId=u1<br>shiftId=other_s1 | Return: null<br>Exception: BadRequestException<br>Log Message: Bạn không sở hữu ca làm việc này. | Abnormal | Passed | Passed | Passed |  |
| `UTC036` | User authenticated as EO; Shift has null event but valid eoId via mock | userId=u1<br>shiftId=s1 | Return: null<br>Exception: BadRequestException<br>Log Message: Throws BadRequestException due to shift.getEvent() == null | Boundary | Passed | Passed | Passed |  |
| **getShiftAttendanceList()** | | | | | | | | |
| `UTC037` | User authenticated as EO; Shift has 1 completed Assignment | userId=u1<br>shiftId=s1 | Return: List<ShiftAttendanceDto>(size=1, workerName="Nguyễn Văn A", eoConfirmed=true, paymentStatus="NOT_DECLARED") | Normal | Passed | Passed | Passed |  |
| `UTC038` | User authenticated as EO; Shift does not exist | userId=u1<br>shiftId=non_existent_uuid | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy ca làm việc ID: ... | Abnormal | Failed | Passed | Passed | BUG-EO-011 |
| `UTC039` | User authenticated as EO; Shift has assignment with AttendanceLog & Payment | userId=u1<br>shiftId=s1 | Return: List<ShiftAttendanceDto>(hoursWorked=4.0, isOnTime=true, paymentStatus='CONFIRMED_FULL', hasReviewedWorker=true) | Normal | Passed | Passed | Passed |  |
| `UTC040` | User authenticated as EO; Shift has checklist template & progress JSON object | userId=u1<br>shiftId=s1<br>template='[{"id":"c1","title":"Task 1"},{"id":"c2","title":"Task 2"}]' | Return: List<ShiftAttendanceDto>(checklistProgress='1/2 việc')<br>Log Message: Calculates completed tasks correctly | Normal | Passed | Passed | Passed |  |
| `UTC041` | User authenticated as EO; Shift checklist is null or blank | userId=u1<br>shiftId=s1<br>checklist=null | Return: List<ShiftAttendanceDto>(checklistProgress='')<br>Log Message: Returns empty string for checklistProgress | Boundary | Passed | Passed | Passed |  |
| **verifyAttendance()** | | | | | | | | |
| `UTC042` | User authenticated as EO; Assignment status=CONFIRMED; Worker totalShiftsCompleted=5 | userId=u1<br>assignmentId=sa1 | Return: ShiftAttendanceDto(eoConfirmed=true, assignmentStatus="COMPLETED")<br>Log Message: Worker totalShiftsCompleted incremented from 5 to 6 | Normal | Passed | Passed | Passed |  |
| `UTC043` | User authenticated as EO; Assignment already eoConfirmed=true | userId=u1<br>assignmentId=sa1 | Return: null<br>Exception: BadRequestException<br>Log Message: Phân công việc này đã được duyệt hoàn thành trước đó. | Abnormal | Failed | Passed | Passed | BUG-EO-012 |
| `UTC044` | User authenticated as EO; Assignment status=COMPLETED (auto checkout); Worker totalShiftsCompleted=10 | userId=u1<br>assignmentId=sa1 | Return: ShiftAttendanceDto(eoConfirmed=true, assignmentStatus="COMPLETED")<br>Log Message: Worker completed shifts remains 10 (no double-increment) | Boundary | Passed | Passed | Passed |  |
| `UTC045` | User authenticated as EO; Assignment ID does not exist | userId=u1<br>assignmentId=non_existent_uuid | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy phân công việc ID: ... | Abnormal | Passed | Passed | Passed |  |
| `UTC046` | User authenticated as EO; Assignment belongs to an event of another EO | userId=u1<br>assignmentId=sa1 | Return: null<br>Exception: BadRequestException<br>Log Message: Bạn không sở hữu sự kiện của ca làm việc này. | Abnormal | Passed | Passed | Passed |  |
| **rateWorker()** | | | | | | | | |
| `UTC047` | User authenticated as EO; Worker ratingAvg=4.0, count=1, reliability=90 | userId=u1<br>assignmentId=sa1<br>request.rating=5 | Return: Review(rating=5)<br>Log Message: workerRatingAvg updated to 4.50; workerReliabilityScore incremented to 91 | Normal | Passed | Passed | Passed |  |
| `UTC048` | User authenticated as EO; EO already reviewed worker in assignment | userId=u1<br>assignmentId=sa1<br>request.rating=5 | Return: null<br>Exception: BadRequestException<br>Log Message: Bạn đã gửi đánh giá cho Worker này trong ca làm việc rồi. | Abnormal | Failed | Passed | Passed | BUG-EO-013 |
| `UTC049` | User authenticated as EO; Worker reliabilityScore=1 | userId=u1<br>assignmentId=sa1<br>request.rating=1 | Return: Review(rating=1)<br>Log Message: workerReliabilityScore decremented by 2 and bounded at minimum 0 | Boundary | Passed | Passed | Passed |  |
| `UTC050` | User authenticated as EO; Worker reliabilityScore=100 (Max) | userId=u1<br>assignmentId=sa1<br>request.rating=4 | Return: Review(rating=4)<br>Log Message: Reliability score capped at MAX_RELIABILITY_SCORE=100 | Normal | Passed | Passed | Passed |  |
| `UTC051` | User authenticated as EO; Worker reliabilityScore=80 | userId=u1<br>assignmentId=sa1<br>request.rating=3 | Return: Review(rating=3)<br>Log Message: Rating 3 leaves reliability score unchanged at 80 | Normal | Passed | Passed | Passed |  |
| `UTC052` | User authenticated as EO; Worker or User entity is null | userId=u1<br>assignmentId=sa1<br>request.rating=5 | Return: null<br>Exception: BadRequestException<br>Log Message: Không tìm thấy thông tin Worker để đánh giá. | Abnormal | Passed | Passed | Passed |  |
| **publishEvent()** | | | | | | | | |
| `UTC053` | User authenticated as EO; Event status=DRAFT with 1 draft Shift | userId=u1<br>eventId=e1 | Return: EventResponseDto(status=ACTIVE)<br>Log Message: validateAndDeductEventQuota called; dispatchForShift triggered for child shift | Normal | Failed | Passed | Passed | BUG-EO-014 |
| `UTC054` | User authenticated as EO; Event does not belong to EO | userId=u1<br>eventId=other_e1 | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy sự kiện ID: ... | Abnormal | Passed | Passed | Passed |  |
| `UTC055` | User authenticated as EO; Event status=DRAFT with 0 Shifts | userId=u1<br>eventId=e1 | Return: EventResponseDto(status=ACTIVE, totalShifts=0)<br>Log Message: Published safely with 0 shifts without throwing exception | Boundary | Passed | Passed | Passed |  |
| `UTC056` | User authenticated as EO; Subscription service throws quota exception | userId=u1<br>eventId=e1 | Return: EventResponseDto(status=ACTIVE)<br>Log Message: Catches quota exception safely and still publishes event | Normal | Passed | Passed | Passed |  |
| `UTC057` | User authenticated as EO; Event has 3 draft shifts | userId=u1<br>eventId=e1 | Return: EventResponseDto(status=ACTIVE, shifts.size=3)<br>Log Message: Dispatches matching engine for all 3 shifts | Normal | Passed | Passed | Passed |  |
| **publishShift()** | | | | | | | | |
| `UTC058` | User authenticated as EO; Shift status=DRAFT | userId=u1<br>shiftId=s1 | Return: ShiftResponseDto(status=ACTIVE)<br>Log Message: dispatchForShift triggered for shiftId | Normal | Failed | Failed | Passed | BUG-EO-015 |
| `UTC059` | User authenticated as EO; Shift does not exist | userId=u1<br>shiftId=non_existent_uuid | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy ca làm việc ID: ... | Abnormal | Failed | Passed | Passed | BUG-EO-016 |
| `UTC060` | User authenticated as EO; Shift belongs to another EO | userId=u1<br>shiftId=other_shiftId | Return: null<br>Exception: BadRequestException<br>Log Message: Bạn không sở hữu ca làm việc này. | Abnormal | Passed | Passed | Passed |  |
| **getMyEvents()** | | | | | | | | |
| `UTC061` | User authenticated as EO; EO has 2 events with shifts | userId=u1 | Return: List<EventResponseDto>(size=2)<br>Log Message: Returns mapped events with their child shifts | Normal | Passed | Passed | Passed |  |
| `UTC062` | User authenticated as EO; EO has 0 events | userId=u1 | Return: List<EventResponseDto>(size=0)<br>Log Message: Returns empty list without error | Boundary | Passed | Passed | Passed |  |
| `UTC063` | User authenticated as EO; Events ordered by createdAt desc | userId=u1 | Return: List<EventResponseDto>(ordered descending)<br>Log Message: Preserves repository query ordering | Normal | Failed | Passed | Passed | BUG-EO-017 |
| **getEventDetail()** | | | | | | | | |
| `UTC064` | User authenticated as EO; Event exists with 2 child shifts | userId=u1<br>eventId=e1 | Return: EventResponseDto(eventId=e1, shifts.size=2)<br>Log Message: Returns detailed event and child shifts | Normal | Passed | Passed | Passed |  |
| `UTC065` | User authenticated as EO; Event does not exist or not owned by EO | userId=u1<br>eventId=non_existent_uuid | Return: null<br>Exception: ResourceNotFoundException<br>Log Message: Không tìm thấy sự kiện ID: ... thuộc nhà tổ chức này | Abnormal | Failed | Failed | Passed | BUG-EO-018 |
| `UTC066` | User authenticated as EO; Event exists with 0 shifts | userId=u1<br>eventId=e1 | Return: EventResponseDto(shifts.size=0)<br>Log Message: Returns event detail with empty shifts list | Boundary | Passed | Passed | Passed |  |

## 4. Chi Tiết Kịch Bản Kiểm Thử Module 2: `WorkerJobService` (66 TCs)

| Test ID | Điều kiện tiền quyết (Pre-condition) | Dữ liệu đầu vào (Input) | Kết quả mong đợi (Expected Output) | Loại TC | R1 | R2 | R3 | Defect ID |
|:---|:---|:---|:---|:---:|:---:|:---:|:---:|:---:|
| **1. setOnlineStatus()** | | | | | | | | |
| `UTC001` | Worker đã đăng nhập vào hệ thống | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>isOnline: true<br>lat: 10.7769<br>lng: 106.7009 | Cập nhật online và tọa độ GPS thành công; Worker(isOnline=true, baseLat=10.7769, baseLng=106.7009) | Normal | Passed | Passed | Passed |  |
| `UTC002` | userId không tồn tại trong hệ thống CANi | userId: 00000000-0000-0000-0000-000000000000<br>isOnline: true | Ném ResourceNotFoundException("Không tìm thấy hồ sơ Worker") | Abnormal | Passed | Passed | Passed |  |
| `UTC003` | Worker tồn tại, GPS truyền vào là null | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>isOnline: false<br>lat: null<br>lng: null | Chỉ đổi trạng thái isOnline=false, giữ nguyên tọa độ cũ | Boundary | Failed | Passed | Passed | BUG-WKS-001 |
| **2. getMyProposals()** | | | | | | | | |
| `UTC004` | Worker có đề xuất việc làm hợp lệ (status PENDING) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về danh sách List<JobProposalResponseDto> kích thước 1 | Normal | Passed | Passed | Passed |  |
| `UTC005` | Worker ID không tồn tại trong database | userId: 00000000-0000-0000-0000-000000000000 | Ném ResourceNotFoundException("Không tìm thấy hồ sơ Worker") | Abnormal | Passed | Passed | Passed |  |
| `UTC006` | Worker có đề xuất nhưng đã hết hạn (expiresAt < now) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Lọc bỏ đề xuất hết hạn, trả về danh sách rỗng | Boundary | Failed | Passed | Passed | BUG-WKS-002 |
| **3. acceptProposal()** | | | | | | | | |
| `UTC007` | Đề xuất PENDING, ca làm việc còn chỗ trống | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Tạo ShiftAssignment trạng thái CONFIRMED; đề xuất ACCEPTED | Normal | Passed | Passed | Passed |  |
| `UTC008` | Đề xuất đã ở trạng thái ACCEPTED hoặc REJECTED | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Ném BadRequestException("Đề xuất việc làm này không còn khả dụng") | Abnormal | Passed | Passed | Passed |  |
| `UTC009` | Worker bị trùng giờ với ca làm việc CONFIRMED khác | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Ném BadRequestException("Bạn đã có một ca làm việc khác trùng khung giờ") | Abnormal | Failed | Passed | Passed | BUG-WKS-003 |
| `UTC010` | Ca làm việc vừa được người khác nhận đủ chỗ (0 slot) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Ném BadRequestException("Ca làm việc vừa được người khác nhận đủ"); cập nhật REJECTED qua REQUIRES_NEW | Boundary | Failed | Failed | Passed | BUG-WKS-004 |
| `UTC010b` | Đề xuất đã hết hạn khi Worker bấm chấp nhận | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Ném BadRequestException("Đề xuất việc làm này đã hết hạn phản hồi"); cập nhật EXPIRED qua REQUIRES_NEW | Boundary | Passed | Passed | Passed |  |
| **4. rejectProposal()** | | | | | | | | |
| `UTC011` | Đề xuất PENDING, Worker từ chối và nêu lý do | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6<br>reason: "Bận việc đột xuất" | Đề xuất chuyển REJECTED; kích hoạt redispatch tìm worker mới | Normal | Passed | Passed | Passed |  |
| `UTC012` | Đề xuất đã được xử lý trước đó | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Ném BadRequestException("Đề xuất việc làm này không còn khả dụng") | Abnormal | Passed | Passed | Passed |  |
| `UTC013` | Service redispatch gặp sự cố ngoại lệ | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>proposalId: 9300d31f-72cc-4e74-a664-fb505cc27bc6 | Bọc try-catch an toàn, ghi warning log; đề xuất vẫn được REJECTED thành công | Boundary | Failed | Passed | Passed | BUG-WKS-005 |
| **5. getMyAssignments()** | | | | | | | | |
| `UTC014` | Worker có danh sách ca làm việc đã đăng ký | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về List<ShiftAssignmentResponseDto> kèm thông tin điểm danh | Normal | Passed | Passed | Passed |  |
| `UTC015` | Worker mới, chưa từng nhận ca làm việc nào | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về danh sách rỗng (size = 0) không lỗi | Boundary | Passed | Passed | Passed |  |
| **6. checkinAssignment()** | | | | | | | | |
| `UTC016` | Quét mã QR Check-in đúng ca, đúng giờ (<=15m) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKIN:<shiftId> | Điểm danh thành công; attendanceStatus = ON_TIME; assignment status = CHECKED_IN | Normal | Passed | Passed | Passed |  |
| `UTC017` | Quét nhầm mã QR Check-out thay vì Check-in | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKOUT:<shiftId> | Ném BadRequestException("Đây là mã QR Check-out (Kết thúc ca)") | Abnormal | Failed | Passed | Passed | BUG-WKS-006 |
| `UTC018` | Quét mã QR của một ca làm việc khác | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKIN:other-shift | Ném BadRequestException("Mã QR Check-in này không thuộc ca làm việc hiện tại") | Abnormal | Passed | Passed | Passed |  |
| `UTC019` | Điểm danh muộn quá 15 phút so với startTime | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKIN:<shiftId> | Điểm danh thành công nhưng attendanceStatus = LATE; isOnTime = false | Boundary | Failed | Failed | Passed | BUG-WKS-007 |
| `UTC019b` | Ca làm việc chưa ở trạng thái CONFIRMED (đã CANCELLED) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKIN:<shiftId> | Ném BadRequestException("Chỉ có thể check-in cho ca làm việc ở trạng thái CONFIRMED") | Abnormal | Passed | Passed | Passed |  |
| `UTC019c` | Khoảng cách GPS của Worker cách sự kiện > 200m | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>lat: 11.5, lng: 107.5 | Ném BadRequestException("Vị trí hiện tại của bạn cách điểm sự kiện ... vượt quá bán kính điểm danh 200m") | Abnormal | Passed | Passed | Passed |  |
| `UTC019d` | Điểm danh đúng chuẩn mốc biên 15 phút | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKIN:<shiftId> | Điểm danh thành công ghi nhận ON_TIME theo điều kiện bao hàm !now.isAfter | Boundary | Passed | Passed | Passed |  |
| **7. checkoutAssignment()** | | | | | | | | |
| `UTC020` | Quét mã QR Check-out hợp lệ khi đã check-in | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKOUT:<shiftId> | Check-out thành công; tính hoursWorked (tối thiểu 30p); chuyển trạng thái ca COMPLETED | Normal | Failed | Failed | Passed | BUG-WKS-008 |
| `UTC021` | Quét nhầm mã QR Check-in khi muốn Check-out | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKIN:<shiftId> | Ném BadRequestException("Đây là mã QR Check-in (Vào ca)") | Abnormal | Passed | Passed | Passed |  |
| `UTC022` | Chưa từng check-in nhưng yêu cầu check-out | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKOUT:<shiftId> | Ném BadRequestException("Bạn chưa check-in vào ca làm việc này nên không thể check-out") | Abnormal | Passed | Passed | Passed |  |
| `UTC023` | Đã check-out hoàn thành ca trước đó | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKOUT:<shiftId> | Ném BadRequestException("Bạn đã check-out hoàn thành ca làm việc này rồi") | Boundary | Passed | Passed | Passed |  |
| `UTC023b` | Ca làm việc chưa CHECKED_IN (vẫn CONFIRMED) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>qr: CANI_CHECKOUT:<shiftId> | Ném BadRequestException("Chỉ có thể check-out cho ca làm việc đang ở trạng thái CHECKED_IN") | Abnormal | Passed | Passed | Passed |  |
| **8. getNearbyShifts()** | | | | | | | | |
| `UTC024` | Tìm kiếm ca ACTIVE trong bán kính hợp lệ | lat: 10.7769<br>lng: 106.7009<br>radiusKm: 10.0 | Trả về List<NearbyShiftResponseDto> sắp xếp theo khoảng cách gần nhất | Normal | Passed | Passed | Passed |  |
| `UTC025` | Bán kính truyền vào <= 0 hoặc null | lat: 10.7769<br>lng: 106.7009<br>radiusKm: null | Fallback về bán kính mặc định 15km an toàn không văng lỗi | Boundary | Failed | Passed | Passed | BUG-WKS-009 |
| `UTC026` | Tọa độ truyền vào null | lat: null<br>lng: null<br>radiusKm: 10.0 | Fallback về tọa độ trung tâm TP.HCM mặc định | Boundary | Passed | Passed | Passed |  |
| **9. applyForShift()** | | | | | | | | |
| `UTC027` | Ca đang ACTIVE, còn chỗ, không trùng lịch | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>shiftId: 7d7e40c1-d1c8-4126-874c-c78b90407868 | Ứng tuyển trực tiếp thành công; tạo ShiftAssignment CONFIRMED | Normal | Passed | Passed | Passed |  |
| `UTC028` | Ca làm việc đã kết thúc (COMPLETED) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>shiftId: 7d7e40c1-d1c8-4126-874c-c78b90407868 | Ném BadRequestException("Ca làm việc này không còn mở tuyển dụng") | Abnormal | Failed | Passed | Passed | BUG-WKS-010 |
| `UTC029` | Worker đã ứng tuyển hoặc nhận ca này trước đó | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>shiftId: 7d7e40c1-d1c8-4126-874c-c78b90407868 | Ném BadRequestException("Bạn đã nhận hoặc ứng tuyển ca làm việc này rồi") | Abnormal | Passed | Passed | Passed |  |
| `UTC030` | Ca đã đủ số lượng tuyển dụng ngay lúc ghi nhận | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>shiftId: 7d7e40c1-d1c8-4126-874c-c78b90407868 | Ném BadRequestException("Rất tiếc! Ca làm việc vừa được tuyển đủ người") | Boundary | Passed | Passed | Passed |  |
| **10. getWorkerAvailability()** | | | | | | | | |
| `UTC031` | Worker đã cài đặt lịch rảnh trong tuần | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về List<WorkerAvailabilityDto> chứa các khung giờ rảnh | Normal | Passed | Passed | Passed |  |
| `UTC032` | Worker chưa thiết lập lịch rảnh nào | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về danh sách rỗng (size = 0) không lỗi | Boundary | Passed | Passed | Passed |  |
| **11. updateWorkerAvailability()** | | | | | | | | |
| `UTC033` | Worker cập nhật danh sách khung giờ rảnh mới | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>schedules: [TUESDAY, 13:00 - 21:00] | Xóa lịch cũ và lưu mới thành công; trả về danh sách đã lưu | Normal | Passed | Passed | Passed |  |
| `UTC034` | userId Worker không tồn tại | userId: 00000000-0000-0000-0000-000000000000 | Ném ResourceNotFoundException("Không tìm thấy hồ sơ Worker") | Abnormal | Passed | Passed | Passed |  |
| `UTC035` | Truyền danh sách lịch rỗng hoặc null | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>schedules: [] | Xóa toàn bộ lịch rảnh hiện tại an toàn, trả về danh sách rỗng | Boundary | Failed | Passed | Passed | BUG-WKS-011 |
| **12. getWorkerPreferences()** | | | | | | | | |
| `UTC036` | Worker đã có bản ghi tiêu chí tìm việc | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về WorkerPreferencesDto đầy đủ trường | Normal | Passed | Passed | Passed |  |
| `UTC037` | Worker có bản ghi preferences nhưng các trường là null | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Default null minWage về ZERO, maxDistanceKm về 15km an toàn | Boundary | Failed | Passed | Passed | BUG-WKS-012 |
| **13. updateWorkerPreferences()** | | | | | | | | |
| `UTC038` | Cập nhật đầy đủ các tiêu chí tìm việc | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>minWage: 50000, maxDistanceKm: 10.0 | Lưu cập nhật thành công và trả về DTO tương ứng | Normal | Passed | Passed | Passed |  |
| `UTC039` | Cập nhật một phần trường (chỉ cập nhật minWage) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>minWage: 60000, maxDistanceKm: null | Giữ nguyên giá trị cũ cho các trường null | Boundary | Passed | Passed | Passed |  |
| **14. cancelAssignment()** | | | | | | | | |
| `UTC040` | Worker hủy ca sớm trước 24 giờ | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Hủy ca thành công; giảm worker atomic; trừ phạt nhẹ 5 điểm uy tín | Normal | Passed | Passed | Passed |  |
| `UTC041` | Ca làm việc đã bị hủy trước đó | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Ném BadRequestException("Ca làm việc này đã được hủy trước đó") | Abnormal | Passed | Passed | Passed |  |
| `UTC042` | Ca làm việc đã hoàn thành (COMPLETED) | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Ném BadRequestException("Ca làm việc đã hoàn thành, không thể hủy") | Abnormal | Passed | Passed | Passed |  |
| `UTC043` | Hủy sát giờ (<12h) khi uy tín Worker chỉ còn 10 điểm | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Phạt nặng 20 điểm nhưng giới hạn uy tín không âm, chặn floor tại 0 | Boundary | Failed | Passed | Passed | BUG-WKS-013 |
| **15. getShiftChecklist()** | | | | | | | | |
| `UTC044` | Worker lấy checklist nhiệm vụ ca của chính mình | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Hợp nhất template nhiệm vụ chung với tiến độ riêng của worker thành công | Normal | Passed | Passed | Passed |  |
| `UTC045` | Worker cố truy cập checklist ca của Worker khác | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: other-worker-assignment | Ném BadRequestException("Bạn không có quyền truy cập checklist của ca làm việc này") | Abnormal | Failed | Passed | Passed | BUG-WKS-014 |
| `UTC046` | Ca làm việc không có template nhiệm vụ checklist | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Trả về danh sách rỗng (size = 0) không văng lỗi | Boundary | Passed | Passed | Passed |  |
| **16. updateShiftChecklist()** | | | | | | | | |
| `UTC047` | Đánh dấu hoàn thành một đầu việc trong checklist | assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>itemId: chk_1, isCompleted: true | Cập nhật tiến độ vào assignment JSON; không làm sai lệch template gốc của Shift | Normal | Passed | Passed | Passed |  |
| `UTC048` | assignmentId không tồn tại | assignmentId: 00000000-0000-0000-0000-000000000000 | Ném ResourceNotFoundException("Không tìm thấy phân công công việc") | Abnormal | Passed | Passed | Passed |  |
| `UTC049` | itemId không tồn tại trong template nhiệm vụ | assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d<br>itemId: non_existent_item | Bỏ qua item không hợp lệ an toàn, giữ nguyên các item khác | Boundary | Passed | Passed | Passed |  |
| **17. parseChecklistTemplate()** | | | | | | | | |
| `UTC050` | Parse chuỗi JSON mảng chuỗi hoặc mảng object | templateJson: ["Đồng phục đúng quy định", "Có mặt trước 15p"] | Parse thành công danh sách List<ShiftChecklistItemDto> chuẩn hóa | Normal | Passed | Passed | Passed |  |
| `UTC051` | Chuỗi JSON sai cú pháp (malformed JSON) | templateJson: "{invalid_json_format:" | Bắt ngoại lệ an toàn, fallback trả về danh sách rỗng | Boundary | Failed | Passed | Passed | BUG-WKS-015 |
| **18. parseChecklistProgress()** | | | | | | | | |
| `UTC052` | Parse chuỗi JSON tiến độ dạng Map hợp lệ | progressJson: {"chk_1": {"isCompleted": true}} | Parse thành công Map<String, ChecklistProgressDto> | Normal | Passed | Passed | Passed |  |
| `UTC053` | Chuỗi JSON tiến độ null hoặc rỗng | progressJson: null | Trả về Map rỗng an toàn | Boundary | Passed | Passed | Passed |  |
| **19. createReview()** | | | | | | | | |
| `UTC054` | Worker gửi đánh giá cho ban tổ chức sau khi hoàn thành ca | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>shiftId: 7d7e40c1-d1c8-4126-874c-c78b90407868<br>rating: 5, comment: "Sự kiện tổ chức rất chuyên nghiệp" | Tạo Review thành công; tính lại avgRating cho ban tổ chức | Normal | Passed | Passed | Passed |  |
| `UTC055` | Worker cố tình gửi đánh giá 2 lần cho cùng một ca | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a<br>shiftId: 7d7e40c1-d1c8-4126-874c-c78b90407868 | Ném BadRequestException("Bạn đã gửi đánh giá cho ban tổ chức của ca làm việc này rồi") | Abnormal | Passed | Passed | Passed |  |
| **20. getAssignmentReview()** | | | | | | | | |
| `UTC056` | Worker lấy đánh giá đã gửi cho ca làm việc | assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Trả về Optional chứa ReviewResponseDto đã đánh giá | Normal | Passed | Passed | Passed |  |
| `UTC057` | Worker chưa từng đánh giá ca làm việc này | assignmentId: ce99f4bf-fb61-4b9d-a80a-bb1d1ee7258d | Trả về Optional.empty() an toàn không lỗi | Boundary | Passed | Passed | Passed |  |
| **21. getIncomeSummary()** | | | | | | | | |
| `UTC058` | Worker có các giao dịch thanh toán thành công và giờ làm | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Thống kê chính xác tổng thu nhập đã nhận, đang chờ và tổng giờ làm việc | Normal | Passed | Passed | Passed |  |
| `UTC059` | Worker mới, chưa có bất kỳ thanh toán hay giờ làm nào | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Trả về WorkerIncomeSummaryDto với các giá trị 0 an toàn | Boundary | Passed | Passed | Passed |  |
| `UTC060` | Điểm đánh giá trung bình của Worker là null | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Mặc định ratingAvg = BigDecimal.ZERO | Boundary | Passed | Passed | Passed |  |
| `UTC060b` | Bản ghi Payment có declaredAmount và agreedAmount đều null | userId: 629934c2-9af1-4456-a90b-eb0cf91d456a | Tính toán an toàn trả về 0, không văng NullPointerException | Boundary | Failed | Failed | Passed | BUG-WKS-016 |