# CANi API Documentation — Bảng Theo Dõi & Phân Tích Doanh Thu (UC-108)

> **Mục tiêu:** Tài liệu đặc tả kỹ thuật Backend API phục vụ màn hình Doanh thu và Chỉ số tài chính cho Admin / Finance (UC-108: View Revenue Dashboard).  
> **Người phụ trách Backend:** QuyTH  
> **Phiên bản:** 1.0.0 (Spring Boot 3.5.1 / Java 21)  
> **Base URL:** `/api/v1/admin/revenue`  
> **Quyền hạn bắt buộc (RBAC):** `ADMIN` (Lưu ý: SRS UC-108 ghi nhận vai trò Admin hoặc Finance, hiện tại hệ thống ánh xạ về Role `ADMIN` theo quy chuẩn phân quyền).

---

## 1. Tổng Quan Nghiệp Vụ & Nguồn Dữ Liệu (UC-108)

### 1.1. Phạm vi số liệu
1. **Doanh thu gói dịch vụ (Total Package Sales):**
   - Thu thập từ bảng `subscriptions` được mua bởi các Nhà tổ chức sự kiện (Event Organizer).
   - Phân loại theo gói dịch vụ:
     - `INVITE_ONLY`: Gói Giới Hạn Mời đích danh.
     - `MATCHING_PLUS`: Gói Đối Soát & Ghép Ca Tự Động (Matching + Invite Mode).
2. **Khối lượng giao dịch thù lao & điều phối (Direct Payments):**
   - Thu thập từ bảng `payments` ghi nhận việc khai báo chi trả thù lao giữa Event Organizer và Worker (tuân thủ nguyên lý Non-Escrow).
   - Phân loại theo hình thức sự kiện: `MATCHING` (ghép ca) vs `INVITE` (mời trực tiếp).
3. **So sánh theo kỳ (Period-over-Period):**
   - So sánh tự động kỳ được chọn `[from, to)` với kỳ trước đó có cùng độ dài `[from - duration, from)`.
   - Tính toán tỷ lệ tăng trưởng phần trăm (`growthRate`) cho doanh thu gói, số lượt mua, khối lượng thanh toán và tổng số giao dịch.
4. **Tổng hợp trạng thái giao dịch VNPay & Cờ cảnh báo (Flagged Transactions):**
   - **Nguồn dữ liệu VNPay:** Phân loại dựa trên `payment_method` hoặc `external_ref` chứa từ khóa `VNPAY`/`vnpay`.
   - **Giao dịch cảnh báo rủi ro:** Hệ thống tự động gắn cờ (`isFlagged: true`) đối với các giao dịch có trạng thái `DISPUTED` (đang có khiếu nại tranh chấp) hoặc `UNVERIFIED` (chưa được đối soát xác minh) để đội ngũ quản trị kịp thời theo dõi và xử lý.
   - *Lưu ý về nguồn dữ liệu:* Do hệ thống hiện chưa tích hợp webhook VNPay riêng, các chỉ số giao dịch cổng được tổng hợp trực tiếp từ bảng khai báo đối soát `payments`.

---

## 2. Danh Sách API Chi Tiết

### 2.1. Lấy Tổng Quan Doanh Thu & So Sánh Cùng Kỳ
* **Endpoint:** `GET /api/v1/admin/revenue/summary`
* **Method:** `GET`
* **Quyền:** `ADMIN`
* **Query Parameters:**
  - `from` *(tùy chọn, ISO-8601)*: Thời điểm bắt đầu (mặc định: 30 ngày trước).
  - `to` *(tùy chọn, ISO-8601)*: Thời điểm kết thúc (mặc định: thời điểm hiện tại).

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy dữ liệu tổng quan doanh thu thành công",
  "data": {
    "currentPeriod": {
      "from": "2026-10-09T05:00:00Z",
      "to": "2026-11-08T05:00:00Z",
      "totalPackageRevenue": 25000000.00,
      "totalPackageSales": 15,
      "totalPaymentVolume": 45000000.00,
      "totalPaymentTransactions": 120,
      "combinedVolume": 70000000.00
    },
    "previousPeriod": {
      "from": "2026-09-09T05:00:00Z",
      "to": "2026-10-09T05:00:00Z",
      "totalPackageRevenue": 20000000.00,
      "totalPackageSales": 12,
      "totalPaymentVolume": 40000000.00,
      "totalPaymentTransactions": 105,
      "combinedVolume": 60000000.00
    },
    "growth": {
      "packageRevenueGrowthRate": 25.0,
      "packageSalesGrowthRate": 25.0,
      "paymentVolumeGrowthRate": 12.5,
      "paymentTransactionsGrowthRate": 14.29,
      "combinedVolumeGrowthRate": 16.67
    },
    "packageTypeBreakdown": [
      {
        "planType": "INVITE_ONLY",
        "planTypeName": "Gói Giới Hạn Mời (Invite Only)",
        "salesCount": 5,
        "totalRevenue": 5000000.00,
        "percentageOfRevenue": 20.0
      },
      {
        "planType": "MATCHING_PLUS",
        "planTypeName": "Gói Đối Soát & Ghép Nâng Cao (Matching + Invite)",
        "salesCount": 10,
        "totalRevenue": 20000000.00,
        "percentageOfRevenue": 80.0
      }
    ],
    "eventModeBreakdown": [
      {
        "mode": "MATCHING",
        "modeName": "Ghép ca làm việc (Matching)",
        "eventCount": 18,
        "totalPaymentAmount": 32000000.00,
        "transactionCount": 85
      },
      {
        "mode": "INVITE",
        "modeName": "Mời đích danh (Invite)",
        "eventCount": 8,
        "totalPaymentAmount": 13000000.00,
        "transactionCount": 35
      }
    ],
    "vnpaySummary": {
      "totalTransactions": 65,
      "totalAmount": 28000000.00,
      "confirmedCount": 58,
      "pendingCount": 4,
      "flaggedCount": 3,
      "dataReliabilityNote": "Dữ liệu VNPay và cờ cảnh báo được tổng hợp dựa trên payment_method/external_ref và trạng thái đối soát (DISPUTED, UNVERIFIED) do hệ thống hiện chưa có webhook VNPay và bảng giao dịch cổng riêng."
    },
    "flaggedTransactions": [
      {
        "id": "e4f8a7e0-911a-4c92-80ea-31fa6370bb01",
        "transactionType": "DIRECT_PAYMENT",
        "organizerId": "a1b2c3d4-0000-0000-0000-000000000001",
        "organizerName": "Công ty TNHH Sự kiện Sao Kim",
        "workerId": "b1b2c3d4-0000-0000-0000-000000000002",
        "workerName": "Trần Thị Mai",
        "eventId": "c1b2c3d4-0000-0000-0000-000000000003",
        "amount": 450000.00,
        "status": "DISPUTED",
        "paymentMethod": "VNPAY_QR",
        "externalRef": "VNPAY_19283912",
        "isFlagged": true,
        "flagReason": "Giao dịch đang có tranh chấp giữa Worker và Nhà tổ chức",
        "createdAt": "2026-11-06T10:15:00Z"
      }
    ]
  },
  "timestamp": "2026-11-08T12:00:00"
}
```

---

### 2.2. Bảng Danh Sách Giao Dịch Chi Tiết
* **Endpoint:** `GET /api/v1/admin/revenue/transactions`
* **Method:** `GET`
* **Quyền:** `ADMIN`
* **Query Parameters:**
  - `from` *(tùy chọn)*: Thời điểm bắt đầu
  - `to` *(tùy chọn)*: Thời điểm kết thúc
  - `status` *(tùy chọn)*: Lọc theo trạng thái (ví dụ: `CONFIRMED_FULL`, `DISPUTED`, `ACTIVE`, `CANCELLED`...)
  - `packageType` *(tùy chọn)*: `INVITE_ONLY` hoặc `MATCHING_PLUS`
  - `page` *(mặc định: 0)*: Số trang
  - `size` *(mặc định: 20)*: Kích thước trang

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy danh sách giao dịch doanh thu thành công",
  "data": {
    "content": [
      {
        "id": "189ec66d-cb9e-4e86-90a4-17adfaef5924",
        "transactionType": "PACKAGE_SALE",
        "organizerId": "38fe974e-f8ae-4f05-87a4-9e32f508eb67",
        "organizerName": "Công ty Sự kiện Sài Gòn",
        "planType": "MATCHING_PLUS",
        "planName": "Gói Đối Soát & Ghép Nâng Cao",
        "amount": 2000000.00,
        "status": "ACTIVE",
        "paymentMethod": "VNPAY",
        "isFlagged": false,
        "createdAt": "2026-11-07T08:30:00Z"
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 20
    },
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-11-08T12:00:00"
}
```

---

### 2.3. Thống Kê Xu Hướng Doanh Thu Theo Chu Kỳ
* **Endpoint:** `GET /api/v1/admin/revenue/analytics`
* **Method:** `GET`
* **Quyền:** `ADMIN`
* **Query Parameters:**
  - `period`: `day` (theo ngày), `week` (theo tuần), `month` (theo tháng) — mặc định `day`.
  - `from` *(tùy chọn)*: Thời điểm bắt đầu
  - `to` *(tùy chọn)*: Thời điểm kết thúc

#### Response (200 OK):
```json
{
  "success": true,
  "message": "Lấy dữ liệu phân tích doanh thu thành công",
  "data": {
    "period": "day",
    "from": "2026-11-01T00:00:00Z",
    "to": "2026-11-08T00:00:00Z",
    "dataPoints": [
      {
        "periodLabel": "2026-11-01",
        "periodStart": "2026-10-31T17:00:00Z",
        "periodEnd": "2026-11-01T17:00:00Z",
        "packageRevenue": 2000000.00,
        "packageSalesCount": 1,
        "paymentVolume": 3500000.00,
        "paymentTransactionsCount": 12,
        "totalCombinedVolume": 5500000.00
      }
    ],
    "totalPackageRevenue": 2000000.00,
    "totalPaymentVolume": 3500000.00,
    "totalCombinedVolume": 5500000.00
  },
  "timestamp": "2026-11-08T12:00:00"
}
```

