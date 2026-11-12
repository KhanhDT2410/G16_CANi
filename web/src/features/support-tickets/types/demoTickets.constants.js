/**
 * Dữ liệu mô phỏng hàng đợi Phiếu Hỗ Trợ Khách Hàng (Preview Layout).
 * Chỉ dùng để hiển thị giao diện xem trước khi Database chưa có dữ liệu phiếu hỗ trợ.
 * Khi CSDL có dữ liệu thực tế, hệ thống sẽ tự động ưu tiên dữ liệu thật từ Backend.
 */

export const DEMO_PREVIEW_TICKETS = [
  {
    id: 'demo-tkt-01',
    referenceNo: 'TKT-20261111-QR01',
    category: 'TECHNICAL',
    priority: 'HIGH',
    subject: 'Lỗi GPS không quét được mã QR điểm danh ca',
    description: 'Ca làm việc Lễ tân đón khách tại Trung tâm Hội nghị Quốc gia. Em đứng đúng cổng sảnh chính nhưng ứng dụng báo sai lệch vị trí GPS hơn 300m, không quét được mã QR điểm danh ca vào lúc 07:45. Quản lý ca bảo em nhắn CSKH để được hỗ trợ check-in thủ công.',
    status: 'OPEN',
    assignedTo: null,
    slaDeadline: new Date(Date.now() + 3.5 * 3600 * 1000).toISOString(),
    slaAtRisk: true,
    createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
    userName: 'Nguyễn Văn An',
    userPhone: '0987.654.321',
    userEmail: 'an.nguyen@gmail.com',
    userRole: 'WORKER',
    messages: [
      {
        id: 'msg-demo-1',
        senderId: 'demo-user-01',
        body: 'Em đứng đúng cổng sảnh chính nhưng ứng dụng báo sai lệch vị trí GPS hơn 300m, không quét được mã QR điểm danh ca lúc 07:45. Nhờ admin hỗ trợ xác nhận điểm danh giúp em với ạ!',
        isInternal: false,
        createdAt: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
      },
    ],
    chatHistory: [
      {
        id: 'chat-1',
        sender: 'user',
        senderName: 'Nguyễn Văn An',
        text: 'Chào CSKH, ca làm việc sáng nay tại Trung tâm Hội nghị em không quét được QR điểm danh do GPS bị lệch. Nhờ hỗ trợ xác nhận điểm danh giúp em với ạ!',
        time: '07:50',
      },
      {
        id: 'chat-2',
        sender: 'staff',
        senderName: 'CSKH CANi',
        text: 'Chào bạn An, mình đã tiếp nhận phiếu hỗ trợ của bạn. Bạn gửi giúp mình ảnh chụp màn hình thông báo lỗi và ảnh thẻ ca làm việc nhé!',
        time: '07:52',
      },
      {
        id: 'chat-3',
        sender: 'user',
        senderName: 'Nguyễn Văn An',
        text: 'Em vừa gửi ảnh qua đây rồi ạ, nhờ CSKH duyệt thủ công giúp em kẻo muộn ca.',
        time: '07:54',
      },
    ],
  },
  {
    id: 'demo-tkt-02',
    referenceNo: 'TKT-20261111-KC02',
    category: 'GENERAL',
    priority: 'MEDIUM',
    subject: 'Kháng cáo hoàn điểm uy tín ca vắng mặt bất khả kháng',
    description: 'Em bị trừ 15 điểm uy tín do vắng mặt ca Setup sân khấu tối ngày 10/11. Em có việc gia đình đột xuất và đã thông báo cho Trưởng nhóm trước 4 tiếng kèm giấy xác nhận. Kính đề nghị CSKH hỗ trợ hoàn lại điểm uy tín.',
    status: 'IN_PROGRESS',
    assignedTo: 'cc-staff-01',
    assignedName: 'Nguyễn Văn A',
    assignedRole: 'CSKH',
    slaDeadline: new Date(Date.now() + 20 * 3600 * 1000).toISOString(),
    slaAtRisk: false,
    createdAt: new Date(Date.now() - 4 * 3600 * 1000).toISOString(),
    userName: 'Trần Thị Mai',
    userPhone: '0912.345.678',
    userEmail: 'mai.tran@eventhub.vn',
    userRole: 'EVENT_ORGANIZER',
    messages: [
      {
        id: 'msg-demo-2',
        senderId: 'demo-user-02',
        body: 'Em gửi hồ sơ kháng cáo kèm chứng minh thư và xác nhận của địa phương về lý do bất khả kháng.',
        isInternal: false,
        createdAt: new Date(Date.now() - 3.5 * 3600 * 1000).toISOString(),
      },
    ],
    chatHistory: [
      {
        id: 'chat-4',
        sender: 'user',
        senderName: 'Trần Thị Mai',
        text: 'Dạ chào anh/chị, em gửi hồ sơ kháng cáo điểm uy tín ca ngày 10/11. Nhờ CSKH xem xét giúp em ạ.',
        time: '14:20',
      },
      {
        id: 'chat-5',
        sender: 'staff',
        senderName: 'CSKH CANi',
        text: 'Chào bạn Mai, hồ sơ của bạn đang được phòng vận hành thẩm định chứng từ. Dự kiến trong chiều nay sẽ có kết quả nhé!',
        time: '14:25',
      },
    ],
  },
  {
    id: 'demo-tkt-03',
    referenceNo: 'TKT-20261111-TL03',
    category: 'BILLING',
    priority: 'HIGH',
    subject: 'Chưa nhận được thù lao ca điều phối âm thanh sau 24h đối soát',
    description: 'Ca làm việc Kỹ thuật viên âm thanh sự kiện Tech Expo kết thúc lúc 22:00 ngày 09/11. Quản lý ca đã bấm hoàn tất đối soát ca nhưng đến nay tài khoản ví CANi của em vẫn chưa được cộng thù lao 450.000 VNĐ.',
    status: 'OPEN',
    assignedTo: null,
    slaDeadline: new Date(Date.now() + 2 * 3600 * 1000).toISOString(),
    slaAtRisk: true,
    createdAt: new Date(Date.now() - 2 * 3600 * 1000).toISOString(),
    userName: 'Lê Hoàng Quân',
    userPhone: '0903.888.999',
    userEmail: 'quan.le@soundlight.pro',
    userRole: 'WORKER',
    messages: [
      {
        id: 'msg-demo-3',
        senderId: 'demo-user-03',
        body: 'Ca âm thanh Tech Expo của em đã đối soát xong nhưng ví chưa nhảy số dư. Nhờ CSKH kiểm tra giúp.',
        isInternal: false,
        createdAt: new Date(Date.now() - 1.8 * 3600 * 1000).toISOString(),
      },
    ],
    chatHistory: [
      {
        id: 'chat-6',
        sender: 'user',
        senderName: 'Lê Hoàng Quân',
        text: 'Alo CSKH ơi, ca âm thanh Tech Expo của em kết thúc hơn 24h rồi mà chưa nhận được tiền thù lao, kiểm tra giùm em với ạ.',
        time: '16:05',
      },
      {
        id: 'chat-7',
        sender: 'staff',
        senderName: 'CSKH CANi',
        text: 'Chào bạn Quân, mình đang đối soát lại lệnh giải ngân từ phía Nhà tổ chức sự kiện Tech Expo, bạn đợi mình 5 phút nhé!',
        time: '16:08',
      },
    ],
  },
];

