package com.cani.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tác vụ định kỳ dọn dẹp và áp dụng chính sách lưu trữ (Retention Policy) cho hội thoại chat:
 * 1. Tin nhắn chat ca làm việc realtime: lưu trữ 7 ngày (1 tuần) rồi tự động dọn dẹp.
 * 2. Tin nhắn hội thoại đối chất tranh chấp: lưu trữ 5 ngày sau khi vụ việc được giải quyết rồi dọn dẹp.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRetentionScheduler {

    private final DisputeChatService disputeChatService;

    /**
     * Chạy định kỳ vào 02:00 sáng mỗi ngày.
     */
    @Scheduled(cron = "${cani.chat-retention.cron:0 0 2 * * ?}")
    public void runRetentionCleanup() {
        log.info("[Retention Scheduler] Bắt đầu quét và dọn dẹp dữ liệu hội thoại hết hạn...");
        try {
            disputeChatService.purgeExpiredRegularChats();
            disputeChatService.cleanupResolvedDisputeChats();
            log.info("[Retention Scheduler] Hoàn thành quy trình dọn dẹp retention.");
        } catch (Exception ex) {
            log.error("[Retention Scheduler] Lỗi khi thực hiện dọn dẹp chat: {}", ex.getMessage(), ex);
        }
    }
}
