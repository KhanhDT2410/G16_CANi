/**
 * Danh mục chuẩn hóa các đầu việc (Checklist Items) mẫu cho sự kiện CANi
 * Phân loại theo các vai trò thực tế: Check-in & Lễ tân, Điều phối sự kiện, Hậu cần & Setup, Kỹ thuật AV, F&B, Bảo vệ...
 * Đáp ứng yêu cầu chuẩn hóa nghiệp vụ sự kiện (EO-001)
 */

export const CHECKLIST_ROLE_CATEGORIES = [
  { id: 'all', label: 'Chung (Tất cả ca)', icon: '📋' },
  { id: 'checkin', label: 'Check-in & Lễ tân', icon: '🎫' },
  { id: 'coordination', label: 'Điều phối sự kiện', icon: '🎯' },
  { id: 'logistics', label: 'Hậu cần & Setup', icon: '📦' },
  { id: 'technical', label: 'Kỹ thuật (AV / LED)', icon: '🎧' },
  { id: 'fnb', label: 'Phục vụ bàn & Pha chế', icon: '🍽️' },
  { id: 'security', label: 'Bảo vệ & An ninh', icon: '🛡️' },
];

export const STANDARDIZED_CHECKLIST_TEMPLATES = {
  all: [
    { title: 'Có mặt trước 20 phút nhận thẻ đeo và vị trí làm việc', isRequiredEvidence: false },
    { title: 'Trang phục và tác phong chỉn chu theo quy định ca làm', isRequiredEvidence: false },
    { title: 'Quét mã QR Check-in điểm danh đầu ca', isRequiredEvidence: false },
    { title: 'Bàn giao công việc và báo cáo quản lý trước khi kết thúc ca', isRequiredEvidence: false },
  ],
  checkin: [
    { title: 'Đón tiếp đại biểu, khách mời và hướng dẫn lối vào sự kiện', isRequiredEvidence: false },
    { title: 'Quét mã QR vé / Phát thẻ đeo (badge) và tài liệu cho khách', isRequiredEvidence: false },
    { title: 'Hướng dẫn khách chụp ảnh tại Photobooth / Backdrop sự kiện', isRequiredEvidence: false },
    { title: 'Hỗ trợ giải đáp thắc mắc và điều tiết luồng khách sảnh đón', isRequiredEvidence: false },
    { title: 'Chụp ảnh quầy lễ tân / check-in sẵn sàng đón khách', isRequiredEvidence: true },
  ],
  coordination: [
    { title: 'Nắm vững timeline chi tiết và kịch bản chương trình sự kiện', isRequiredEvidence: false },
    { title: 'Hỗ trợ MC, diễn giả chuẩn bị và di chuyển đúng vị trí sân khấu', isRequiredEvidence: false },
    { title: 'Điều phối khách mời vào hội trường và khu vực ghế VIP', isRequiredEvidence: false },
    { title: 'Kiểm soát khu vực cánh gà và hỗ trợ di chuyển đạo cụ sân khấu', isRequiredEvidence: false },
    { title: 'Kịp thời xử lý và báo cáo các tình huống phát sinh tại hiện trường cho EO', isRequiredEvidence: false },
  ],
  logistics: [
    { title: 'Kiểm tra và sắp xếp bàn ghế, thiết bị hội trường theo sơ đồ', isRequiredEvidence: false },
    { title: 'Chuẩn bị teabreak, nước uống cho khách mời và Ban tổ chức', isRequiredEvidence: false },
    { title: 'Hỗ trợ bưng bê quà tặng, kỷ niệm chương, tài liệu trao giải', isRequiredEvidence: false },
    { title: 'Thu dọn, vệ sinh khu vực sự kiện và hoàn trả mặt bằng sau ca', isRequiredEvidence: false },
    { title: 'Chụp ảnh nghiệm thu khu vực hoàn trả mặt bằng sau sự kiện', isRequiredEvidence: true },
  ],
  technical: [
    { title: 'Kiểm tra hệ thống micro, loa, âm lượng trước giờ khai mạc', isRequiredEvidence: false },
    { title: 'Test kết nối màn hình LED / máy chiếu và chạy thử slide trình chiếu', isRequiredEvidence: false },
    { title: 'Điều chỉnh hệ thống đèn chiếu sân khấu và hiệu ứng theo kịch bản', isRequiredEvidence: false },
    { title: 'Trực kỹ thuật trong suốt ca, sẵn sàng backup thiết bị khi gặp sự cố', isRequiredEvidence: false },
    { title: 'Chụp ảnh kiểm tra bàn điều khiển âm thanh - ánh sáng trước giờ G', isRequiredEvidence: true },
  ],
  fnb: [
    { title: 'Chuẩn bị dụng cụ bàn tiệc, ly tách, khăn ăn sạch sẽ trước giờ tiệc', isRequiredEvidence: false },
    { title: 'Châm nước, phục vụ thức ăn và đồ uống theo đúng tiêu chuẩn', isRequiredEvidence: false },
    { title: 'Thu dọn chén đĩa bẩn và giữ gìn vệ sinh khu vực phục vụ', isRequiredEvidence: false },
    { title: 'Kiểm đếm và bảo quản nguyên vật liệu quầy pha chế / quầy tiệc', isRequiredEvidence: false },
  ],
  security: [
    { title: 'Kiểm soát an ninh tại cổng ra vào và khu vực bãi đỗ xe', isRequiredEvidence: false },
    { title: 'Hướng dẫn khách đỗ xe đúng vị trí quy định', isRequiredEvidence: false },
    { title: 'Tuần tra, nhắc nhở giữ trật tự và phòng chống cháy nổ', isRequiredEvidence: false },
    { title: 'Hỗ trợ xử lý các tình huống gây rối hoặc sự cố an ninh', isRequiredEvidence: false },
  ],
};

/**
 * Tự động ánh xạ tên vai trò ca làm (jobType) sang nhóm checklist danh mục tương ứng
 */
export function resolveCategoryByJobType(jobType = '') {
  if (!jobType) return 'all';
  const lower = String(jobType).toLowerCase();
  if (
    lower.includes('check-in') ||
    lower.includes('checkin') ||
    lower.includes('lễ tân') ||
    lower.includes('pg') ||
    lower.includes('pb') ||
    lower.includes('thu ngân')
  ) {
    return 'checkin';
  }
  if (
    lower.includes('điều phối') ||
    lower.includes('coordinator') ||
    lower.includes('quản lý') ||
    lower.includes('giám sát')
  ) {
    return 'coordination';
  }
  if (
    lower.includes('hậu cần') ||
    lower.includes('setup') ||
    lower.includes('kho') ||
    lower.includes('vận chuyển')
  ) {
    return 'logistics';
  }
  if (
    lower.includes('kỹ thuật') ||
    lower.includes('âm thanh') ||
    lower.includes('ánh sáng') ||
    lower.includes('av') ||
    lower.includes('led')
  ) {
    return 'technical';
  }
  if (
    lower.includes('phục vụ') ||
    lower.includes('pha chế') ||
    lower.includes('f&b') ||
    lower.includes('bàn') ||
    lower.includes('barista') ||
    lower.includes('bartender')
  ) {
    return 'fnb';
  }
  if (
    lower.includes('bảo vệ') ||
    lower.includes('an ninh') ||
    lower.includes('security')
  ) {
    return 'security';
  }
  return 'all';
}
