# CANi Team - Hướng Dẫn Chuẩn Hóa Code Khi Cả Nhóm Dùng AI

> **Mục tiêu:** Đảm bảo toàn bộ thành viên trong nhóm dù sử dụng bất kỳ công cụ AI nào (Cursor, GitHub Copilot, Claude Code, Antigravity/Gemini, ChatGPT) đều sinh ra mã nguồn đồng nhất, chuẩn Clean Code, không gây xung đột kiến trúc ("Frankenstein Codebase").

---

## 1. Bản Chất Vấn Đề: Tại Sao Dùng AI Dễ Biến Dự Án Thành "Mỗi Người Một Kiểu"?

1. **AI là "kẻ không có trí nhớ tập thể":** Mỗi bạn mở một cửa sổ chat riêng. AI trên máy bạn A không hề biết bạn B đã viết gì, tạo hook gì hay dùng thư viện nào nếu không được cung cấp ngữ cảnh đầy đủ.
2. **AI là "kẻ nịnh hót" (Yes-Man):** Bạn hỏi kiểu gì nó sẽ trả lời theo kiểu đó. Bạn thích dùng `fetch` nó viết `fetch`, bạn khác thích `axios` nó viết `axios`, bạn khác bảo dùng Redux nó cài Redux.
3. **Ảo tưởng "AI làm tất cả":** Nhiều bạn copy nguyên cả đoạn code 500 dòng của AI paste vào dự án mà không hề đọc lại diff, không kiểm tra xem AI có tự bịa hàm hay phá hỏng chuẩn type hay không.

---

## 2. Giải Pháp 4 Trụ Cột: "Lồng Kính Kỷ Luật Cho AI"

Để kiểm soát triệt để, nhóm triển khai 4 tầng bảo vệ sau:

```
┌────────────────────────────────────────────────────────┐
│  Tầng 4: Human Code Review (Đồng đội review chéo)     │
├────────────────────────────────────────────────────────┤
│  Tầng 3: Automated Guardrails (Pre-commit / CI Linter) │
├────────────────────────────────────────────────────────┤
│  Tầng 2: Shared Component First (Tái sử dụng UI chung) │
├────────────────────────────────────────────────────────┤
│  Tầng 1: AI Context Anchors (.cursorrules / AGENTS.md) │
└────────────────────────────────────────────────────────┘
```

---

### Trụ Cột 1: Cung Cấp Ngữ Cảnh Bắt Buộc Cho AI (AI Context Anchors)

Tất cả các công cụ AI hiện đại (Cursor, Windsurf, Claude Code, Antigravity...) đều tự động đọc các file hướng dẫn đặt ở thư mục gốc của dự án. 
Dự án CANi đã cấu hình sẵn 2 file chuẩn:
1. `AGENTS.md`: Dành cho các công cụ tác tử (Gemini, Antigravity, Claude Code).
2. `.cursorrules`: Dành cho Cursor Editor và Windsurf.

**Mọi thành viên PHẢI clone repo và giữ nguyên các file này**. Khi chat với AI, AI sẽ bị "khóa chân" trong luật chung của dự án.

---

### Trụ Cột 2: Triết Lý "Shared Component & Utility First"

Trước khi yêu cầu AI viết một giao diện hay tính năng mới, thành viên **bắt buộc phải nhắc AI**:
- **UI:** Kiểm tra và dùng component trong `@/components/common` (Button, Input, Modal, Table...) thay vì để AI tự chế các thẻ HTML/CSS tùy tiện.
- **API Client:** Tuyệt đối không để AI tự tạo `fetch` hay `axios.create` mới. Bắt buộc import instance duy nhất từ `@/lib/axiosClient` (hoặc `@/services/api`).
- **Xử lý ngày tháng/tiền tệ:** Dùng các hàm tiện ích trong `@/utils/` (`formatCurrency`, `formatDate`), cấm để AI tự viết hàm format riêng rẽ trong component.

---

### Trụ Cột 3: Lưới An Toàn Tự Động (Automated Guardrails)

Không thể dựa hoàn toàn vào ý thức tự giác, nhóm dùng **công cụ tự động phạt lỗi**:

1. **TypeScript Strict Mode:** Bật cấu hình nghiêm ngặt (`noImplicitAny: true`). Bất kỳ đoạn code nào AI sinh ra dùng kiểu `any` đều bị gạch đỏ và không cho build.
2. **ESLint & Prettier thống nhất:**
   - Cài đặt plugin `prettier` và bật tính năng **"Format on Save"** trong VS Code/Cursor.
   - Nhờ đó, dù AI viết thụt đầu dòng 2 space hay 4 space, bấm Save một cái là toàn bộ code trở về cùng 1 định dạng chuẩn của nhóm.
3. **Pre-commit Hook (Husky & lint-staged):**
   - Trước khi lệnh `git commit` được thực thi, máy sẽ tự động chạy:
     ```bash
     npm run lint
     npm run build (hoặc tsc --noEmit)
     ```
   - Nếu code của AI sinh ra bị lỗi type hoặc lỗi format, Git sẽ **từ chối commit**. Dev buộc phải sửa sạch lỗi mới được đẩy lên GitHub!

---

### Trụ Cột 4: Quy Trình 3 Bước Chuẩn Khi Thành Viên Nhờ AI Code

Mọi thành viên trong nhóm cần tuân thủ **Quy trình 3 Bước (SOP)**:

#### 🔹 Bước 1: Yêu cầu AI Lập Kế Hoạch (Plan First, Code Later)
- **Không bao giờ:** Quăng nguyên yêu cầu lớn: *"Viết cho tôi trang quản lý sự kiện"*.
- **Cách làm chuẩn:** *"Tôi cần làm chức năng danh sách sự kiện trong `features/events`. Hãy liệt kê các file cần tạo, cấu trúc type DTO và các bước thực hiện. Chưa được sinh code ngay."*

#### 🔹 Bước 2: Kiểm soát Từng Lớp (Layer by Layer)
Yêu cầu AI sinh code theo thứ tự:
1. Viết `types/` trước (Định nghĩa rõ interface).
2. Viết `api/` (Định nghĩa hàm gọi backend qua axios).
3. Viết `hooks/` (Xử lý state & loading/error).
4. Viết `components/` (Render giao diện dùng shared components).

#### 🔹 Bước 3: Tự Review Diff Trước Khi Commit
- Không được tin tưởng AI 100%. Phải mở tab Git trong editor lên, đọc lại từng dòng thay đổi (diff):
  - Có bị dư thừa thư viện lạ nào không?
  - Có bị hard-code chuỗi URL hay token không?
  - Có bị sửa lan sang các file của bạn khác không?

---

## 3. Mẫu Prompt Chuẩn (Team Prompt Template)

Các bạn có thể copy mẫu prompt dưới đây mỗi khi bắt đầu một tính năng mới để "nắn gân" AI ngay từ đầu:

```markdown
Bạn là lập trình viên Frontend cao cấp cho dự án CANi (React 18 + TypeScript + Vite + Axios).
Hãy tuân thủ nghiêm ngặt các quy tắc sau:
1. Cấu trúc thư mục: Tuân thủ Feature-Driven Architecture trong `src/features/[tên-feature]`.
2. UI: Sử dụng các Shared Component có sẵn tại `@/components/common`. Tuyệt đối không tự bịa style tùy tiện.
3. API: Sử dụng `axiosClient` từ `@/lib/axiosClient`. Định nghĩa kiểu dữ liệu trả về rõ ràng trong `types/`, cấm dùng `any`.
4. Logic: Tách logic vào custom hook trong `hooks/`, component chỉ giữ vai trò hiển thị UI.

Yêu cầu hiện tại của tôi: [Mô tả tính năng ở đây]
Trước khi viết code, hãy tóm tắt các file bạn dự định tạo/sửa để tôi duyệt.
```
