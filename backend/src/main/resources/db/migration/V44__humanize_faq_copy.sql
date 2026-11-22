-- =============================================================================
-- V44: Chuẩn hóa nội dung FAQ thân thiện, dễ hiểu cho người dùng cuối
-- Loại bỏ thuật ngữ kỹ thuật nội bộ (W_dist, W_rating, W_exp, W_rs, Matching Engine...)
-- =============================================================================

UPDATE faqs
SET
    question = 'Hệ thống gợi ý và gửi ca làm việc phù hợp cho tôi như thế nào?',
    summary = 'Cách CANi chọn các ca làm việc gần bạn, đúng kỹ năng và phù hợp nhất.',
    answer = 'Khi có sự kiện mới, CANi sẽ tự động đối chiếu với lịch rảnh, kỹ năng và khu vực bạn muốn làm việc (thường trong bán kính 15 km), sau đó ưu tiên gửi đề xuất dựa trên 4 tiêu chí: khoảng cách từ bạn đến địa điểm sự kiện (40%), điểm đánh giá từ các ban tổ chức trước đó (30%), số ca bạn đã hoàn thành (20%) và điểm uy tín tài khoản (10%). Nhờ vậy, bạn càng ở gần và giữ uy tín tốt thì cơ hội nhận ca phù hợp càng cao.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'b2000000-0000-0000-0000-000000000001';

UPDATE faqs
SET
    question = 'Điểm uy tín là gì và ảnh hưởng thế nào đến cơ hội nhận ca?',
    answer = 'Mỗi tài khoản bắt đầu với điểm uy tín nền tảng (khởi điểm 80–100 điểm tùy mức hoàn thiện hồ sơ). Khi bạn đi làm đúng giờ, hoàn thành tốt và nhận đánh giá tích cực, điểm uy tín sẽ tăng. Ngược lại, hủy ca sát giờ hoặc tự ý vắng mặt không báo trước sẽ bị trừ điểm nặng (tối đa 25 điểm/lần). Nếu điểm uy tín xuống dưới 60, hệ thống sẽ tạm ngừng gửi đề xuất ca tự động; dưới 20 điểm tài khoản sẽ bị khóa để đảm bảo chất lượng chung.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'a1000000-0000-0000-0000-000000000002';

UPDATE faqs
SET
    question = 'Làm sao để nâng hạng kỹ năng và tăng cơ hội nhận ca lương cao?',
    answer = 'Mỗi lần hoàn thành ca làm thuộc một nhóm công việc (Phục vụ bàn, Lễ tân, Thu ngân, Pha chế, Kỹ thuật sự kiện...), bạn sẽ được cộng điểm kinh nghiệm cho kỹ năng đó. Ngoài ra, bạn có thể tải lên chứng chỉ nghề nghiệp để hệ thống kiểm tra và gắn huy hiệu xác thực, giúp hồ sơ nổi bật hơn khi Ban tổ chức chọn nhân sự.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'a1000000-0000-0000-0000-000000000003';

UPDATE faqs
SET
    question = 'Cách quét mã QR Check-in và Check-out khi đi làm sự kiện?',
    answer = 'Khi đến địa điểm tổ chức đúng giờ quy định, bạn mở chi tiết ca làm trên ứng dụng CANi và bấm "Quét QR Check-in" để quét mã do Ban tổ chức hiển thị tại quầy điều phối. Cuối ca, sau khi hoàn tất công việc và xác nhận thù lao, bạn quét mã QR Check-out để hệ thống chốt giờ làm thực tế.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'b2000000-0000-0000-0000-000000000002';

UPDATE faqs
SET
    question = 'Hình thức trả thù lao trực tiếp trên CANi hoạt động ra sao?',
    answer = 'Ban tổ chức sẽ thanh toán thù lao trực tiếp cho bạn bằng chuyển khoản ngân hàng hoặc tiền mặt trong vòng 48 giờ sau khi kết thúc ca (CANi không giữ tiền hộ). Khi Ban tổ chức khai báo đã chuyển khoản và đính kèm chứng từ, bạn kiểm tra tài khoản rồi bấm xác nhận trên ứng dụng để hoàn tất.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'c3000000-0000-0000-0000-000000000001';

UPDATE faqs
SET
    question = 'Quy trình giải quyết khiếu nại & tranh chấp diễn ra như thế nào?',
    answer = 'Khi có tranh chấp về thù lao, giờ công hoặc tác phong, bạn mở chi tiết ca làm liên quan và gửi đơn khiếu nại kèm ảnh minh chứng trong vòng 14 ngày. Đội ngũ Chăm sóc khách hàng sẽ tiếp nhận, đối chiếu lịch sử điểm danh GPS/QR cùng chứng từ của hai bên để đưa ra hướng xử lý công bằng. Nếu chưa đồng thuận, bạn có thể yêu cầu Quản trị viên xem xét lại lần cuối trong 7 ngày.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'd4000000-0000-0000-0000-000000000002';
