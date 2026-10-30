package com.cani.dto.admin.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMetricsDto {

    /**
     * Tổng số người dùng đã đăng ký trên hệ thống
     */
    private long totalUsers;

    /**
     * Số người dùng có vai trò là Worker
     */
    private long workerCount;

    /**
     * Số người dùng có vai trò là Nhà tổ chức sự kiện (Event Organizer)
     */
    private long eventOrganizerCount;

    /**
     * Số nhân sự Chăm sóc khách hàng (Customer Care)
     */
    private long customerCareCount;

    /**
     * Số Quản trị viên (Admin)
     */
    private long adminCount;

    /**
     * Số tài khoản hiện đang bị khóa (LOCKED)
     */
    private long lockedUsersCount;
}
