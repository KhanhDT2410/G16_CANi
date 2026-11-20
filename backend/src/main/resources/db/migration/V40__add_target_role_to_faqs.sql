-- =============================================================================
-- V40: Bổ sung phân loại đối tượng áp dụng (Target Role) cho câu hỏi thường gặp FAQ (SRS UC-110 / ADM-08)
-- Phân chia câu hỏi riêng biệt cho Người lao động (WORKER), Nhà tổ chức sự kiện (EVENT_ORGANIZER) hoặc Tất cả (ALL)
-- =============================================================================

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'faq_target_role') THEN
        CREATE TYPE faq_target_role AS ENUM ('ALL', 'WORKER', 'EVENT_ORGANIZER');
    END IF;
END $$;

ALTER TABLE faqs
    ADD COLUMN IF NOT EXISTS target_role faq_target_role NOT NULL DEFAULT 'ALL';

CREATE INDEX IF NOT EXISTS idx_faqs_target_role ON faqs(target_role);

-- Chuẩn hóa dữ liệu 12 câu hỏi đã seed từ V20 theo đối tượng phù hợp:
-- 1. ACCOUNT: eKYC, RS score, Skill Points -> WORKER
UPDATE faqs SET target_role = 'WORKER' WHERE id IN (
    'a1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000003'
);

-- 2. SHIFT:
UPDATE faqs SET target_role = 'WORKER' WHERE id IN (
    'b2000000-0000-0000-0000-000000000001',
    'b2000000-0000-0000-0000-000000000002'
);

-- 3. PAYMENT:
UPDATE faqs SET target_role = 'WORKER' WHERE id = 'c3000000-0000-0000-0000-000000000001';
UPDATE faqs SET target_role = 'EVENT_ORGANIZER' WHERE id = 'c3000000-0000-0000-0000-000000000002';

-- 4. DISPUTE:
UPDATE faqs SET target_role = 'WORKER' WHERE id IN (
    'd4000000-0000-0000-0000-000000000002',
    'd4000000-0000-0000-0000-000000000003'
);

-- =============================================================================
-- Bổ sung bộ câu hỏi FAQ chuẩn nghiệp vụ cho từng nhóm đối tượng (WORKER, EO, ALL)
-- =============================================================================

INSERT INTO faqs (id, category, target_role, question, summary, answer, status, view_count, published_at, created_at, updated_at)
VALUES
-- --- NHÓM 1: DÀNH CHO NGƯỜI TÌM VIỆC (WORKER) ---
(
    'e1000000-0000-0000-0000-000000000001',
    'ACCOUNT',
    'WORKER',
    'Làm thế nào khi hình ảnh CCCD hoặc khuôn mặt bị hệ thống eKYC từ chối?',
    'Hướng dẫn khắc phục lỗi khi xác thực eKYC không thành công do mờ, lóa hoặc lệch thông tin.',
    'Hãy đảm bảo chụp CCCD ở nơi đủ sáng, không bị lóa đèn flash, không chụp lại màn hình hay bản photocopy. Đối với ảnh khuôn mặt, không đeo khẩu trang hay kính râm, đưa khuôn mặt vào giữa khung tròn theo hướng dẫn. Nếu sau 3 lần vẫn lỗi, hãy liên hệ Customer Care để được hỗ trợ xác minh thủ công.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'e1000000-0000-0000-0000-000000000002',
    'ACCOUNT',
    'WORKER',
    'Tôi có thể liên kết tài khoản ngân hàng không chính chủ để nhận thù lao không?',
    'Quy định về tính chính chủ của tài khoản ngân hàng nhận thù lao trên CANi.',
    'Để đảm bảo an toàn tài chính và phòng chống gian lận, tên chủ tài khoản ngân hàng liên kết bắt buộc phải trùng khớp 100% với họ và tên trên CCCD đã xác thực eKYC. Hệ thống sẽ tự động từ chối nếu tên tài khoản thụ hưởng không trùng khớp.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'e1000000-0000-0000-0000-000000000003',
    'SHIFT',
    'WORKER',
    'Nếu quản lý sự kiện yêu cầu làm thêm giờ (OT) ngoài khung giờ đã đăng ký thì thù lao tính như thế nào?',
    'Cách tính giờ làm thêm (OT) và xác nhận thù lao bổ sung giữa Worker và Nhà tổ chức.',
    'Mọi giờ làm phát sinh ngoài khung giờ chính thức cần được Quản lý sự kiện xác nhận rõ ràng trước khi thực hiện. Sau khi kết thúc, thời gian làm thực tế được đối chiếu qua thời gian quét mã Check-out. Nếu Nhà tổ chức không thanh toán phần OT đã thỏa thuận, bạn có quyền mở khiếu nại tranh chấp trong vòng 48 giờ.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'e1000000-0000-0000-0000-000000000004',
    'SHIFT',
    'WORKER',
    'Tôi có được phép nhờ bạn bè hoặc người khác đi làm thay ca đã nhận không?',
    'Quy định nghiêm cấm chuyển nhượng ca làm việc và hình thức xử lý vi phạm.',
    'Tuyệt đối KHÔNG được chuyển nhượng ca làm cho người khác dưới bất kỳ hình thức nào. Tài khoản đi làm phải trùng khớp với dữ liệu eKYC và mã QR cá nhân khi check-in tại bàn tiếp đón. Vi phạm này sẽ dẫn đến việc đình chỉ tài khoản ngay lập tức và trừ 50 điểm RS.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'e1000000-0000-0000-0000-000000000005',
    'PAYMENT',
    'WORKER',
    'Tôi phải làm gì nếu quá 48 giờ sau khi hoàn thành ca làm mà chưa nhận được chuyển khoản thù lao từ Nhà tổ chức?',
    'Các bước xử lý và kích hoạt tranh chấp khi Nhà tổ chức chậm trễ thanh toán thù lao.',
    'Theo điều khoản nền tảng, Nhà tổ chức có nghĩa vụ chuyển khoản thù lao trong vòng 48 giờ kể từ lúc ca làm kết thúc. Nếu sau thời hạn này chưa nhận được tiền và không có phản hồi, bạn hãy mở mục Chi tiết ca làm, bấm Tạo khiếu nại tranh chấp thù lao và đính kèm lịch sử tin nhắn. Bộ phận CSKH sẽ can thiệp và áp dụng biện pháp chế tài đóng băng tài khoản của Nhà tổ chức.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'e1000000-0000-0000-0000-000000000006',
    'DISPUTE',
    'WORKER',
    'Cần chuẩn bị những bằng chứng nào khi tạo khiếu nại tranh chấp thù lao hoặc giờ làm việc?',
    'Danh mục các chứng từ, hình ảnh cần thiết để đảm bảo khiếu nại được xử lý nhanh nhất.',
    'Bạn cần cung cấp: 1) Ảnh chụp màn hình tin nhắn hoặc thỏa thuận với Quản lý sự kiện; 2) Biên bản xác nhận giờ làm thực tế tại sự kiện (nếu có); 3) Sao kê tài khoản ngân hàng thể hiện chưa nhận được tiền trong vòng 48 giờ; 4) Ảnh chụp vị trí hoặc check-in tại địa điểm làm việc.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),

-- --- NHÓM 2: DÀNH CHO NHÀ TỔ CHỨC SỰ KIỆN (EVENT_ORGANIZER) ---
(
    'f2000000-0000-0000-0000-000000000001',
    'ACCOUNT',
    'EVENT_ORGANIZER',
    'Doanh nghiệp hoặc cá nhân tổ chức sự kiện cần những hồ sơ gì để xác thực tài khoản Nhà tổ chức (EO)?',
    'Yêu cầu pháp lý và quy trình phê duyệt hồ sơ dành cho Nhà tổ chức sự kiện.',
    'Đối với Doanh nghiệp: Cần tải lên Giấy chứng nhận Đăng ký kinh doanh (GPKD) hợp lệ và CCCD của người đại diện pháp luật. Đối với Đơn vị cá nhân/Freelance Organizer: Cần CCCD gắn chip và thư giới thiệu hoặc hợp đồng tổ chức sự kiện minh chứng năng lực tổ chức. Hồ sơ được phê duyệt trong vòng 24 giờ làm việc.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f2000000-0000-0000-0000-000000000002',
    'SHIFT',
    'EVENT_ORGANIZER',
    'Tính năng Radar nhân sự khẩn cấp hoạt động như thế nào khi sự kiện thiếu người sát giờ?',
    'Hướng dẫn sử dụng Radar quét nhân sự bán kính gần với thông báo đẩy ưu tiên cao.',
    'Khi phát sinh nhu cầu tuyển gấp (dưới 4 tiếng trước sự kiện), EO có thể bật tính năng Radar. Hệ thống sẽ phát thông báo đẩy khẩn cấp (Emergency Broadcast) đến tất cả Worker có kỹ năng phù hợp và đang online trong bán kính 10km quanh địa điểm sự kiện để tiếp nhận ca trong vòng 10-15 phút.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f2000000-0000-0000-0000-000000000003',
    'SHIFT',
    'EVENT_ORGANIZER',
    'Nhà tổ chức có thể hủy yêu cầu tuyển dụng ca làm sau khi đã đăng tuyển không?',
    'Quy định về hủy ca tuyển dụng và chính sách bồi hoàn cho nhân sự đã được chấp nhận.',
    'Nếu chưa có nhân sự nào chấp nhận ca, EO có thể hủy hoàn toàn miễn phí. Nếu đã có nhân sự được duyệt ca, EO phải thông báo trước tối thiểu 24 giờ. Trường hợp hủy dưới 12 giờ trước ca làm việc, EO có nghĩa vụ hỗ trợ 30% thù lao cơ bản cho nhân sự đã xếp lịch để bù đắp chi phí di chuyển và cơ hội làm việc.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f2000000-0000-0000-0000-000000000004',
    'PAYMENT',
    'EVENT_ORGANIZER',
    'Nhà tổ chức có thể tải lên biên lai chuyển khoản hàng loạt cho nhiều nhân sự trong một ca làm không?',
    'Hướng dẫn xác nhận thanh toán thù lao cho danh sách nhân viên tham gia ca làm việc.',
    'Trên Cổng quản trị sự kiện, EO vào mục Quản lý thanh toán ca làm, chọn nhân sự cần chốt thù lao và tải lên ảnh chụp biên lai giao dịch ngân hàng (UNC hoặc biên lai chuyển khoản nhanh 24/7). Hệ thống hỗ trợ đính kèm từng biên lai tương ứng hoặc một biên lai chuyển khoản theo danh sách lương kèm mã đối soát.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f2000000-0000-0000-0000-000000000005',
    'PAYMENT',
    'EVENT_ORGANIZER',
    'Nền tảng xử lý chứng từ khấu trừ thuế TNCN và hóa đơn VAT cho dịch vụ như thế nào?',
    'Chính sách chứng từ thuế đối với thù lao lao động thời vụ và hóa đơn gói dịch vụ CANi.',
    'Đối với phí gói dịch vụ hoặc phí tính năng nâng cao mua từ CANi: Hệ thống xuất hóa đơn điện tử GTGT gửi thẳng vào email công ty đã đăng ký. Đối với thù lao chi trả trực tiếp cho Worker: EO chịu trách nhiệm thực hiện kê khai khấu trừ thuế TNCN theo quy định pháp luật lao động thời vụ hiện hành.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f2000000-0000-0000-0000-000000000006',
    'DISPUTE',
    'EVENT_ORGANIZER',
    'Nhà tổ chức cần cung cấp những bằng chứng gì khi báo cáo nhân sự vi phạm tác phong hoặc No-show?',
    'Quy trình báo cáo vi phạm tác phong, bỏ ca hoặc đi trễ của nhân viên phục vụ.',
    'EO cần gửi yêu cầu qua Trung tâm Tranh chấp trong vòng 24 giờ kể từ thời điểm vi phạm, đính kèm: 1) Biên bản ghi nhận vi phạm tại chỗ có chữ ký của Quản lý sự kiện; 2) Trích xuất camera giám sát hoặc hình ảnh chứng minh nhân viên không có mặt đúng giờ; 3) Lịch sử cuộc gọi liên hệ bất thành.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),

-- --- NHÓM 3: CHUNG CHO TẤT CẢ (ALL) ---
(
    'f3000000-0000-0000-0000-000000000001',
    'ACCOUNT',
    'ALL',
    'Tôi quên mật khẩu đăng nhập hoặc muốn thay đổi số điện thoại tài khoản thì phải làm sao?',
    'Các bước lấy lại quyền truy cập tài khoản và cập nhật số điện thoại bảo mật.',
    'Tại màn hình đăng nhập, chọn Quên mật khẩu và nhập email hoặc số điện thoại đã đăng ký để nhận mã OTP xác thực. Trường hợp muốn đổi số điện thoại, bạn cần đăng nhập vào mục Cài đặt tài khoản > Bảo mật và thực hiện xác thực khuôn mặt sinh trắc học trước khi cập nhật số mới.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f3000000-0000-0000-0000-000000000002',
    'ACCOUNT',
    'ALL',
    'Làm cách nào để liên hệ trực tiếp với bộ phận Chăm sóc khách hàng (Customer Care) của CANi?',
    'Các kênh hỗ trợ trực tuyến chính thức của CANi hoạt động 24/7.',
    'Người dùng có thể: 1) Bấm vào nút Hỗ trợ trực tuyến (Live Chat) ở góc phải màn hình; 2) Gửi phiếu yêu cầu hỗ trợ (Support Ticket) trong ứng dụng; 3) Gửi email về support@cani.vn; hoặc 4) Gọi hotline hỗ trợ khẩn cấp 1900 6868 trong khung giờ từ 07:00 đến 23:00 hàng ngày.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f3000000-0000-0000-0000-000000000003',
    'SHIFT',
    'ALL',
    'Quy định về trang phục và tác phong tiêu chuẩn khi tham gia các sự kiện trên CANi?',
    'Tiêu chuẩn diện mạo, trang phục và đạo đức tác phong áp dụng cho toàn bộ nhân sự.',
    'Trang phục cơ bản mặc định nếu sự kiện không có yêu cầu riêng: Áo sơ mi đen/trắng trơn có cổ, quần tây đen sẫm màu, giày tây hoặc giày bata đen kín mũi, đầu tóc gọn gàng. Luôn có mặt trước giờ làm 20 phút để nghe phân công, giữ thái độ lịch sự, hòa nhã và tuyệt đối không sử dụng điện thoại cho việc cá nhân trong giờ làm.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
),
(
    'f3000000-0000-0000-0000-000000000004',
    'DISPUTE',
    'ALL',
    'Thời gian tối đa để bộ phận Chăm sóc khách hàng giải quyết một tranh chấp hoặc khiếu nại là bao lâu?',
    'Quy trình xử lý SLA giải quyết tranh chấp công bằng giữa các bên.',
    'Theo cam kết dịch vụ (SLA): Các tranh chấp về giờ làm và thù lao được tiếp nhận trong 2 giờ và có kết luận sơ bộ trong vòng 24 giờ làm việc. Trường hợp phức tạp cần xác minh từ bên thứ ba, thời gian tối đa không quá 72 giờ. Mọi quyết định của CANi đều căn cứ trên bằng chứng khách quan và lịch sử hệ thống.',
    'PUBLISHED',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    category = EXCLUDED.category,
    target_role = EXCLUDED.target_role,
    question = EXCLUDED.question,
    summary = EXCLUDED.summary,
    answer = EXCLUDED.answer,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP;
