package com.cani.dto.event;

import com.cani.entity.enums.ShiftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponseDto {
    private UUID shiftId;
    private UUID eventId;
    private String jobType;
    private String description;
    private Instant startTime;
    private Instant endTime;
    /** Giờ mở cửa điểm danh; null -> mặc định = startTime */
    private Instant arrivalTime;
    private Integer requiredWorkers;
    private Integer confirmedWorkers;
    private Integer remainingWorkers;
    private BigDecimal hourlyWage;
    private BigDecimal allowance;
    private BigDecimal totalCost;
    private ShiftStatus status;
    private String locationAddress;
    private Double locationLat;
    private Double locationLng;
    private Integer minReliabilityScore;
    private String dressCode;
    private String imageUrl;
    private String checklist;
    /** Kỹ năng bắt buộc của ca (matching hard filter + hiển thị web) */
    private UUID[] requiredSkills;
    private UUID performedSkillId;
    private String performedSkillName;
    private List<ShiftSkillRequirementDto> skillRequirements;
    private String customSkillNotes;
    /** Cờ điều phối khẩn cấp (Emergency Dispatch) cho phép radar free-pick */
    private Boolean emergency;
    /** Số nhân sự còn thiếu thực tế (= max(0, requiredWorkers - confirmedWorkers)) */
    private Integer missingWorkers;
    /** Ca đang thiếu nhân sự trong khung cảnh báo sát giờ (<= N giờ trước giờ bắt đầu) */
    private Boolean nearStartUnderstaffed;
    /** Có được phép bấm Kích hoạt điều phối khẩn cấp thủ công hay không (Single Source of Truth với API) */
    private Boolean canTriggerEmergency;
    /** Lý do không thể kích hoạt khẩn cấp thủ công (nếu canTriggerEmergency = false) */
    private String emergencyBlockReason;
    /** Số lượng đề xuất (proposal) đang chờ Worker phản hồi cho ca này */
    private Integer pendingProposalCount;
    /** Bán kính tìm kiếm khẩn cấp mặc định sẽ áp dụng (km) */
    private Double defaultEmergencyRadiusKm;
    /** Bán kính khẩn cấp EO đang cấu hình cho ca (km) */
    private Double emergencyRadiusKm;
    /** Mức nới lỏng kỹ năng khẩn cấp đang áp dụng (NONE, LOWER_TIER, PREFER_ONLY) */
    private String emergencySkillRelaxation;
    /** Sàn điểm uy tín riêng cho chế độ khẩn cấp (nếu có) */
    private Integer emergencyMinRs;
    /** Phụ cấp khẩn cấp bổ sung cho nhân sự nhận ca trong đợt khẩn cấp */
    private BigDecimal emergencyBonus;
    /** Thời điểm kích hoạt/quét khẩn cấp gần nhất */
    private Instant emergencyTriggeredAt;
    /** Cho phép EO cập nhật cấu hình và quét lại khẩn cấp khi ca đang bật emergency */
    private Boolean canRedispatchEmergency;
    /** Số giây còn lại trước khi hết cooldown quét lại khẩn cấp */
    private Long emergencyCooldownRemainingSeconds;
    /** Ca có thuộc nhóm phổ thông cho phép tùy chọn PREFER_ONLY hay không */
    private Boolean emergencyPreferOnlyAllowed;
}
