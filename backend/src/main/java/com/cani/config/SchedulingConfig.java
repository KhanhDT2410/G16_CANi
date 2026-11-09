package com.cani.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Cấu hình kích hoạt cơ chế lập lịch tác vụ nền (Task Scheduling) trong CANi.
 * Đặt @EnableScheduling duy nhất tại đây theo quy định kiến trúc của hệ thống.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

