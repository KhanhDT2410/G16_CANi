# Kiến trúc Hệ thống CANi (System Architecture)

Hệ thống CANi được thiết kế theo mô hình **Three-Layer Clean Architecture**:
- **Presentation Layer (Controller / API):** RESTful endpoints, xác thực JWT (Supabase GoTrue / JWKS ES256), phân quyền RBAC (ADMIN, CUSTOMER_CARE, EVENT_ORGANIZER, WORKER).
- **Business Logic Layer (Service):** Xử lý nghiệp vụ sự kiện, ca làm việc, thuật toán Matching & Dispatch Engine, điểm danh QR động, đối soát thanh toán trực tiếp (Non-Escrow).
- **Data Access Layer (Repository):** Spring Data JPA / Hibernate tương tác với cơ sở dữ liệu quan hệ PostgreSQL (và H2 Database cho môi trường dev offline).

Chi tiết tài liệu thiết kế kiến trúc đầy đủ xem tại:
- [Report-4 Software Design Specification](../../Đồ án SEP490 - CANi/SEP490-G16-Report-4_Software%20Design%20Specification.docx)
- [CANI Flow v8.0](../../Đồ án SEP490 - CANi/CANi_Flow_v8.txt)

