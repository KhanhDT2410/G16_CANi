# HƯỚNG DẪN BỘ KIỂM THỬ THẨM ĐỊNH KỸ NĂNG BẰNG AI (UC-46)
**Dự án:** CANi - Event Staffing Platform (SEP490_G16)  
**Phạm vi:** Thẩm định chứng chỉ nghề nghiệp tự động bằng Vision AI (Gemini 3.8 Flash)

---

## 1. Cấu trúc thư mục bàn giao

Thư mục này được khởi tạo độc lập tại:  
`Đồ án SEP490 - CANi\Report 5\AI_Skill_Verification_Tests\`

```
AI_Skill_Verification_Tests/
│
├── AI_Skill_Verification_Test_Matrix.xlsx  <-- Bảng Excel chuẩn hóa 25 test cases (kèm dashboard chỉ số)
├── AI_Skill_Verification_Test_Matrix.csv   <-- File CSV nạp vào code tự động (Automated Benchmark Runner)
├── README_HUONG_DAN_SU_DUNG.md            <-- Tài liệu hướng dẫn này
│
└── sample_images/                          <-- Bộ 25 ảnh mẫu đại diện cho 5 nhóm kịch bản
    ├── TC_AI_01_Barista_Saigontourist.jpg
    ├── TC_AI_02_PhuBep_HuongNghiepAAu.jpg
    ├── ...
    ├── TC_AI_08_Mismatch_TranVanAn.jpg      (Lệch tên / Mượn bằng)
    ├── TC_AI_13_Blurry_Unreadable.jpg       (Ảnh mờ nhòe out of focus)
    ├── TC_AI_18_Bill_EVN.jpg                (Tài liệu rác: Hóa đơn EVN)
    ├── TC_AI_20_Internet_Meme.jpg           (Ảnh meme không hợp lệ)
    └── TC_AI_23_Visual_Prompt_Injection.jpg (Tấn công chèn lệnh hệ thống)
```

---

## 2. Phân loại 25 Ca Kiểm Thử (Ground Truth Test Suite)

| Nhóm | Số lượng | Mã test | Kỳ vọng chuẩn (Ground Truth) | Mục đích đánh giá |
| :--- | :---: | :--- | :--- | :--- |
| **1. HAPPY PATH** | 7 | `TC_AI_01` – `07` | `AUTO_APPROVE` (Confidence $\ge 0.84$) | Đo lường độ nhạy (Recall), công nhận bằng thật, đúng tên, đủ mộc đỏ. |
| **2. IDENTITY MISMATCH** | 5 | `TC_AI_08` – `12` | `REJECT` (Confidence $\le 0.30$) | **Chống gian lận:** Mượn bằng bạn, mượn bằng người yêu, cắt dán Photoshop. |
| **3. POOR QUALITY** | 5 | `TC_AI_13` – `17` | `REJECT` (Confidence $\le 0.50$) | **Độ bền vững:** Ảnh mờ nét, lóa đèn flash, thiếu mộc đỏ, scan đen trắng, chụp ngược. |
| **4. OUT OF DOMAIN** | 5 | `TC_AI_18` – `22` | `REJECT` (Confidence $\le 0.30$) | **Chặn rác:** Hóa đơn tiền điện, ảnh selfie, meme, bằng lái xe, bằng IT không thuộc ngành sự kiện. |
| **5. ADVERSARIAL** | 3 | `TC_AI_23` – `25` | `REJECT` (Confidence $\le 0.55$) | **An toàn AI:** Prompt injection chèn trong ảnh, bằng tự vẽ Word, bằng hết hạn 10 năm. |

---

## 3. Cách sử dụng cho Đồ án & Báo cáo

### A. Dùng cho Báo cáo Kiểm thử (Report 5)
1. Mở file `AI_Skill_Verification_Test_Matrix.xlsx`.
2. Sheet `AI_Skill_Benchmark_Matrix`: Chứa toàn bộ 25 kịch bản với đầy đủ thông tin chuẩn hóa theo form của Report 5.3 (System Test FRs).
3. Sheet `Summary_Metrics_Report`: Đã tổng hợp sẵn bảng chỉ số đo lường hiệu năng AI. Bạn có thể chụp ảnh hoặc copy bảng này vào mục **Non-Functional / AI Evaluation** của `Report-5.0_Test Documentation.docx`.

### B. Dùng cho Kiểm thử tự động (Backend JUnit Benchmark)
Mã nguồn backend tại:  
`Code/backend/src/test/java/com/cani/service/worker/SkillVerificationAiBenchmarkTest.java`  
đã được lập trình sẵn để đánh giá ma trận nhầm lẫn (Confusion Matrix). Bạn có thể map trực tiếp 25 file ảnh trong thư mục `sample_images/` để chạy kiểm thử hồi quy mỗi khi cập nhật Prompt hay mô hình AI.

---

## 4. Cam kết Chỉ số Vàng khi bảo vệ trước Hội đồng
- **False Positive Rate (FPR) = 0.0%**: Tuyệt đối không để AI duyệt nhầm bất kỳ chứng chỉ giả, mượn bằng hoặc ảnh rác nào (Tránh rủi ro pháp lý và chất lượng cho nhà tổ chức sự kiện EO).
- **Precision = 100.0%**: 100% hồ sơ được AI duyệt tự động đều là hồ sơ chuẩn chỉ.
- **Explainability (Khả năng giải trình)**: Khi từ chối, AI luôn đưa ra lý do tiếng Việt rõ ràng (`routingReason`) để Worker biết cách chụp lại đúng chuẩn.
