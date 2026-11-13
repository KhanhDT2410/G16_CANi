-- Thêm ràng buộc UNIQUE cho assignment_id trong bảng payments (H3)
-- Đảm bảo mỗi phân công công việc (ShiftAssignment) chỉ có tối đa một bản ghi thanh toán duy nhất,
-- triệt tiêu rủi ro race condition tạo trùng bản ghi thù lao đối soát.
ALTER TABLE payments
    ADD CONSTRAINT uk_payments_assignment_id UNIQUE (assignment_id);

