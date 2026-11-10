-- =============================================================================
-- V20: Dữ liệu mẫu Câu hỏi thường gặp FAQ & Help Center (SRS FE-12, UC-110)
-- 12 câu hỏi chuẩn nghiệp vụ bám sát đặc tả dự án CANi:
-- ACCOUNT (3 câu), SHIFT (3 câu), PAYMENT (3 câu), DISPUTE (3 câu)
-- =============================================================================

INSERT INTO faqs (id, category, question, answer, status, view_count, published_at, created_at, updated_at)
VALUES
-- === 1. ACCOUNT (Tài khoản & eKYC & Điểm uy tín RS) ===
(
    'a1000000-0000-0000-0000-000000000001',
    'ACCOUNT',
    'Quy trình xác thực định danh (eKYC) trên ứng dụng CANi diễn ra như thế nào?',
    'Người lao động cần chụp rõ nét hai mặt Căn cước công dân (CCCD) gắn chip và một ảnh chân dung selfie khớp khuôn mặt. Hệ thống VNPT eKYC tích hợp của CANi sẽ tự động trích xuất thông tin OCR và đối soát sinh trắc học để kích hoạt tài khoản nhận ca trong vòng 1-3 phút.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'a1000000-0000-0000-0000-000000000002',
    'ACCOUNT',
    'Điểm uy tín (RS - Reliability Score) là gì và ảnh hưởng như thế nào đến việc nhận ca làm?',
    'Điểm uy tín (RS) khởi điểm của mỗi tài khoản là 80 điểm. Hoàn thành tốt ca làm và nhận đánh giá 5 sao từ nhà tổ chức sẽ được cộng điểm; vi phạm hủy ca sát giờ hoặc vắng mặt không phép (No-show) sẽ bị trừ 25 điểm. Nếu điểm RS dưới 60, hệ thống sẽ tạm dừng đề xuất ca tự động; nếu giảm dưới 20 điểm, tài khoản sẽ bị tự động khóa để bảo vệ uy tín nền tảng.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'a1000000-0000-0000-0000-000000000003',
    'ACCOUNT',
    'Làm thế nào để nâng bậc Điểm Kỹ Năng (Skill Points - SP) cho các vị trí chuyên môn?',
    'Mỗi ca làm hoàn thành thành công theo từng loại kỹ năng (Phục vụ bàn, Lễ tân, Thu ngân, Pha chế, Kỹ thuật AV, v.v.) sẽ tích lũy Điểm Kỹ Năng (SP). Người lao động cũng có thể tải lên chứng chỉ chuyên môn hoặc tham gia bài kiểm tra thẩm định AI để nâng hạng từ Cơ bản (Tier 1) lên Chuyên nghiệp (Tier 2) và Trưởng nhóm (Tier 3) để nhận thù lao cao hơn.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),

-- === 2. SHIFT (Ca làm & Ghép nối Matching & Check-in QR) ===
(
    'b2000000-0000-0000-0000-000000000001',
    'SHIFT',
    'Thuật toán Ghép nối tự động (Matching Engine) hoạt động dựa trên các tiêu chí nào?',
    'Hệ thống tự động chấm Điểm Phù Hợp (Matching Score) theo 4 trọng số nghiệp vụ: Khoảng cách GPS địa lý (W_dist = 40%), Điểm đánh giá sao trung bình (W_rating = 30%), Số ca làm đã hoàn thành (W_exp = 20%) và Điểm uy tín sàn (W_rs = 10%). Người lao động có điểm phù hợp cao nhất trong bán kính tối đa (15 km) sẽ được ưu tiên nhận đề xuất nhận ca trước.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'b2000000-0000-0000-0000-000000000002',
    'SHIFT',
    'Quy trình quét mã QR Check-in và Check-out tại sự kiện được thực hiện ra sao?',
    'Trước giờ bắt đầu ca làm 15-30 phút, người lao động có mặt tại địa điểm tổ chức, mở ứng dụng CANi và quét mã QR do Quản lý sự kiện (Event Coordinator) hiển thị tại bàn tiếp đón. Khi kết thúc ca, người lao động thực hiện quét mã QR Check-out lần 2 để hệ thống chốt số giờ làm việc thực tế và chuyển sang trạng thái chờ đối soát thù lao.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'b2000000-0000-0000-0000-000000000003',
    'SHIFT',
    'Tôi có thể hủy ca làm việc sau khi đã bấm Chấp nhận không?',
    'Người lao động có thể hủy ca tự do trước thời điểm bắt đầu ca trên 24 giờ mà không bị phạt. Nếu hủy ca trong vòng 4 đến 24 giờ trước ca làm, bạn sẽ bị trừ 8 điểm uy tín (RS). Nếu hủy ca sát giờ dưới 4 tiếng hoặc không đến làm việc, hệ thống sẽ tự động ghi nhận lỗi No-show và trừ 25 điểm RS.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),

-- === 3. PAYMENT (Thanh toán, Thù lao trực tiếp & Gói đăng ký) ===
(
    'c3000000-0000-0000-0000-000000000001',
    'PAYMENT',
    'Phương thức thanh toán thù lao trực tiếp (Non-Escrow) trên CANi vận hành như thế nào?',
    'Sau khi ca làm việc kết thúc và hoàn tất Check-out, Nhà tổ chức sự kiện (EO) có thời hạn 48 giờ để thực hiện chuyển khoản thù lao trực tiếp vào số tài khoản ngân hàng của người lao động. Khi chuyển khoản thành công, EO đính kèm ảnh chụp biên lai giao dịch trên Cổng EO để hệ thống xác nhận thanh toán.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'c3000000-0000-0000-0000-000000000002',
    'PAYMENT',
    'Nhà tổ chức sự kiện có phải trả phí hoa hồng khi tuyển dụng nhân sự trên sàn không?',
    'Trong giai đoạn hiện tại (MVP), tỷ lệ hoa hồng nền tảng áp dụng là 0.00%. Nhà tổ chức chỉ chi trả đúng mức thù lao theo thỏa thuận trên hợp đồng ca làm việc cho người lao động và phí gói đăng ký dịch vụ (Subscription) hàng tháng nếu sử dụng các tính năng nâng cao (Radar tìm nhân sự khẩn cấp, Đề xuất AI).',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'c3000000-0000-0000-0000-000000000003',
    'PAYMENT',
    'Các phương thức thanh toán gói đăng ký dịch vụ (Subscription) được hỗ trợ?',
    'Nhà tổ chức sự kiện có thể nâng cấp và gia hạn các gói dịch vụ (Gói Cơ bản, Gói Doanh nghiệp, Gói Sự kiện lớn) thông qua cổng thanh toán trực tuyến PayOS (hỗ trợ VietQR quét mã chuyển khoản nhanh 24/7) hoặc cổng VNPAY (thẻ ATM nội địa, Visa/MasterCard, ví điện tử).',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),

-- === 4. DISPUTE (Tranh chấp, Khiếu nại ca & Kháng cáo No-show) ===
(
    'd4000000-0000-0000-0000-000000000001',
    'DISPUTE',
    'Khi nào người lao động hoặc nhà tổ chức nên gửi đơn Tranh chấp & Khiếu nại?',
    'Người dùng có thể tạo đơn khiếu nại trong các trường hợp: Nhà tổ chức chậm trễ thanh toán thù lao quá 48 giờ sau ca làm, thù lao nhận được không đúng thỏa thuận ban đầu, bị xếp lỗi No-show oan uổng do lỗi kỹ thuật quét QR, hoặc phát sinh mâu thuẫn về khối lượng công việc thực tế tại sự kiện.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'd4000000-0000-0000-0000-000000000002',
    'DISPUTE',
    'Quy trình giải quyết tranh chấp (Dispute Resolution) qua bộ phận Chăm sóc khách hàng?',
    'Khi nhận được đơn khiếu nại, nhân viên CSKH (Customer Care) sẽ tiếp nhận xử lý Cấp độ 1 trong vòng 24 giờ làm việc. CSKH mở luồng hội thoại đối soát 3 bên trực tiếp, kiểm tra nhật ký GPS, thời gian quét QR và hình ảnh chứng từ thanh toán. Nếu hai bên không thống nhất, Admin sẽ ra phán quyết chung thẩm cuối cùng.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'd4000000-0000-0000-0000-000000000003',
    'DISPUTE',
    'Làm thế nào để người lao động nộp đơn Kháng cáo điểm uy tín (RS Appeal) khi bị trừ điểm No-show?',
    'Nếu bị ghi nhận No-show do trường hợp bất khả kháng (ốm đau đột xuất có giấy khám bệnh, tai nạn giao thông, sự cố bất khả kháng trên đường đi), người lao động vào mục Hồ sơ -> Lịch sử điểm uy tín -> chọn vi phạm và bấm Kháng cáo. Sau khi tải lên tài liệu minh chứng hợp lệ, bộ phận CSKH sẽ thẩm định và hoàn trả lại điểm RS đã trừ trong vòng 48 giờ.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
