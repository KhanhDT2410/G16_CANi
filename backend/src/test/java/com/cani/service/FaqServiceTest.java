package com.cani.service;

import com.cani.dto.faq.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Faq;
import com.cani.entity.User;
import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqStatus;
import com.cani.entity.enums.TicketCategory;
import com.cani.entity.enums.UserRole;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.FaqRepository;
import com.cani.repository.SupportTicketRepository;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock
    private FaqRepository faqRepository;

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FaqService faqService;

    private Validator validator;
    private UUID authorId;
    private UUID faqId;
    private Faq sampleFaq;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        authorId = UUID.randomUUID();
        faqId = UUID.randomUUID();

        sampleFaq = Faq.builder()
                .id(faqId)
                .category(FaqCategory.ACCOUNT)
                .question("Làm thế nào để thay đổi số điện thoại?")
                .answer("Bạn hãy truy cập vào mục Hồ sơ cá nhân và chọn Chỉnh sửa số điện thoại.")
                .status(FaqStatus.DRAFT)
                .authorId(authorId)
                .viewCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("1. Tạo FAQ thành công → status DRAFT, viewCount = 0, audit CREATE_FAQ được ghi nhận")
    void createFaq_Success() {
        CreateFaqRequestDto req = CreateFaqRequestDto.builder()
                .category(FaqCategory.ACCOUNT)
                .question("Làm thế nào để thay đổi số điện thoại?")
                .answer("Bạn hãy truy cập vào mục Hồ sơ cá nhân và chọn Chỉnh sửa số điện thoại.")
                .build();

        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> {
            Faq f = invocation.getArgument(0);
            f.setId(faqId);
            return f;
        });

        User author = User.builder().userId(authorId).fullName("Nguyễn Admin").role(UserRole.ADMIN).build();
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

        FaqResponseDto result = faqService.createFaq(authorId, req);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(faqId);
        assertThat(result.getStatus()).isEqualTo(FaqStatus.DRAFT);
        assertThat(result.getViewCount()).isEqualTo(0);
        assertThat(result.getAuthorName()).isEqualTo("Nguyễn Admin");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).logAction(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("CREATE_FAQ");
        assertThat(auditCaptor.getValue().getEntityType()).isEqualTo("FAQ");
    }

    @Test
    @DisplayName("2. Tạo FAQ với question quá ngắn (< 10 ký tự) → validation vi phạm ràng buộc")
    void createFaq_QuestionTooShort_ValidationFails() {
        CreateFaqRequestDto req = CreateFaqRequestDto.builder()
                .category(FaqCategory.ACCOUNT)
                .question("Ngắn")
                .answer("Câu trả lời hợp lệ có độ dài lớn hơn hai mươi ký tự.")
                .build();

        Set<ConstraintViolation<CreateFaqRequestDto>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("question") &&
                        v.getMessage().contains("10 đến 500 ký tự"));
    }

    @Test
    @DisplayName("3. Tạo FAQ với answer quá ngắn (< 20 ký tự) → validation vi phạm ràng buộc")
    void createFaq_AnswerTooShort_ValidationFails() {
        CreateFaqRequestDto req = CreateFaqRequestDto.builder()
                .category(FaqCategory.SHIFT)
                .question("Câu hỏi hợp lệ có độ dài vừa đủ?")
                .answer("Quá ngắn")
                .build();

        Set<ConstraintViolation<CreateFaqRequestDto>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("answer") &&
                        v.getMessage().contains("ít nhất 20 ký tự"));
    }

    @Test
    @DisplayName("4. Publish FAQ đang DRAFT → chuyển sang PUBLISHED, có published_at và ghi audit")
    void publishFaq_FromDraft_Success() {
        sampleFaq.setStatus(FaqStatus.DRAFT);
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaqResponseDto result = faqService.publishFaq(authorId, faqId);

        assertThat(result.getStatus()).isEqualTo(FaqStatus.PUBLISHED);
        assertThat(result.getPublishedAt()).isNotNull();

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).logAction(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("PUBLISH_FAQ");
    }

    @Test
    @DisplayName("5. Publish FAQ đã PUBLISHED → idempotent, trả về FAQ hiện tại mà không lỗi")
    void publishFaq_AlreadyPublished_Idempotent() {
        sampleFaq.setStatus(FaqStatus.PUBLISHED);
        sampleFaq.setPublishedAt(Instant.now());
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(sampleFaq));

        FaqResponseDto result = faqService.publishFaq(authorId, faqId);

        assertThat(result.getStatus()).isEqualTo(FaqStatus.PUBLISHED);
        verify(faqRepository, never()).save(any(Faq.class));
        verify(auditLogService, never()).logAction(any());
    }

    @Test
    @DisplayName("6. Archive FAQ → chuyển sang ARCHIVED, có archived_at và ghi audit")
    void archiveFaq_Success() {
        sampleFaq.setStatus(FaqStatus.PUBLISHED);
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaqResponseDto result = faqService.archiveFaq(authorId, faqId);

        assertThat(result.getStatus()).isEqualTo(FaqStatus.ARCHIVED);
        assertThat(result.getArchivedAt()).isNotNull();

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).logAction(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("ARCHIVE_FAQ");
    }

    @Test
    @DisplayName("7. Update FAQ đã ARCHIVED → ném ngoại lệ BadRequestException 400")
    void updateFaq_Archived_ThrowsBadRequest() {
        sampleFaq.setStatus(FaqStatus.ARCHIVED);
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(sampleFaq));

        UpdateFaqRequestDto req = UpdateFaqRequestDto.builder()
                .category(FaqCategory.DISPUTE)
                .question("Câu hỏi cập nhật mới đủ dài?")
                .answer("Nội dung câu trả lời cập nhật hợp lệ trên hai mươi ký tự.")
                .build();

        assertThatThrownBy(() -> faqService.updateFaq(authorId, faqId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể chỉnh sửa câu hỏi đã lưu trữ");
    }

    @Test
    @DisplayName("8. getPublicFaqs chỉ trả về FAQ có trạng thái PUBLISHED, sắp xếp theo lượt xem giảm dần")
    void getPublicFaqs_OnlyPublished() {
        Faq published1 = Faq.builder()
                .id(UUID.randomUUID())
                .category(FaqCategory.ACCOUNT)
                .question("Câu hỏi 1 đã publish?")
                .answer("Câu trả lời 1 hợp lệ đủ dài để hiển thị trên help center.")
                .status(FaqStatus.PUBLISHED)
                .viewCount(100)
                .build();

        Faq published2 = Faq.builder()
                .id(UUID.randomUUID())
                .category(FaqCategory.ACCOUNT)
                .question("Câu hỏi 2 đã publish?")
                .answer("Câu trả lời 2 hợp lệ đủ dài để hiển thị trên help center.")
                .status(FaqStatus.PUBLISHED)
                .viewCount(50)
                .build();

        when(faqRepository.findByStatusOrderByViewCountDescPublishedAtDesc(FaqStatus.PUBLISHED))
                .thenReturn(List.of(published1, published2));

        List<FaqPublicResponseDto> result = faqService.getPublicFaqs(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getViewCount()).isEqualTo(100);
        assertThat(result.get(1).getViewCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("9. getPublicFaqById tăng view_count và lưu vào database")
    void getPublicFaqById_IncrementsViewCount() {
        sampleFaq.setStatus(FaqStatus.PUBLISHED);
        sampleFaq.setViewCount(10);
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FaqPublicResponseDto result = faqService.getPublicFaqById(faqId);

        assertThat(result.getViewCount()).isEqualTo(11);
        verify(faqRepository).save(argThat(f -> f.getViewCount() == 11));
    }

    @Test
    @DisplayName("10. getAnalytics trả đúng tổng view và tính tỷ lệ deflection rate chính xác")
    void getAnalytics_CalculatesCorrectly() {
        Faq f1 = Faq.builder()
                .id(UUID.randomUUID())
                .category(FaqCategory.ACCOUNT)
                .status(FaqStatus.PUBLISHED)
                .viewCount(20)
                .build();

        Faq f2 = Faq.builder()
                .id(UUID.randomUUID())
                .category(FaqCategory.PAYMENT)
                .status(FaqStatus.PUBLISHED)
                .viewCount(30)
                .build();

        when(faqRepository.findAll()).thenReturn(List.of(f1, f2));
        when(faqRepository.findTop5ByOrderByViewCountDesc()).thenReturn(List.of(f2, f1));

        // Tổng ticket = 10; ACCOUNT = 2, BILLING = 3 -> deflected = 5 -> rate = 50.0%
        when(supportTicketRepository.count()).thenReturn(10L);
        when(supportTicketRepository.countByCategory(TicketCategory.ACCOUNT)).thenReturn(2L);
        when(supportTicketRepository.countByCategory(TicketCategory.BILLING)).thenReturn(3L);

        FaqAnalyticsDto analytics = faqService.getAnalytics();

        assertThat(analytics.getTotalViews()).isEqualTo(50L);
        assertThat(analytics.getViewsByCategory().get(FaqCategory.ACCOUNT)).isEqualTo(20L);
        assertThat(analytics.getViewsByCategory().get(FaqCategory.PAYMENT)).isEqualTo(30L);
        assertThat(analytics.getTopFaqs()).hasSize(2);
        assertThat(analytics.getTicketDeflectionRate()).isEqualTo(50.0);
        assertThat(analytics.getCalculationFormula()).contains("deflectionRate =");
    }

    @Test
    @DisplayName("11. getAllFaqs với bộ lọc status và category chỉ trả về đúng nhóm")
    @SuppressWarnings("unchecked")
    void getAllFaqs_WithFilters_ReturnsFilteredPage() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Faq> page = new PageImpl<>(List.of(sampleFaq));
        when(faqRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<FaqResponseDto> result = faqService.getAllFaqs(FaqStatus.DRAFT, FaqCategory.ACCOUNT, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(faqId);
    }

    @Test
    @DisplayName("12. Tạo và cập nhật FAQ kèm ảnh và tệp đính kèm thành công (ADM-08)")
    void createAndUpdateFaq_WithAttachments_Success() {
        CreateFaqRequestDto req = CreateFaqRequestDto.builder()
                .category(FaqCategory.SHIFT)
                .question("Hướng dẫn quét mã QR check-in ca làm việc?")
                .answer("Người lao động mở ứng dụng, chọn mục Điểm danh và quét mã QR tại bàn tiếp đón của sự kiện.")
                .imageUrl("https://cani.vn/assets/qr-guide.png")
                .attachmentUrl("https://cani.vn/docs/quy-trinh-checkin.pdf")
                .attachmentName("quy-trinh-checkin.pdf")
                .build();

        when(faqRepository.save(any(Faq.class))).thenAnswer(invocation -> {
            Faq f = invocation.getArgument(0);
            f.setId(faqId);
            return f;
        });

        User author = User.builder().userId(authorId).fullName("Admin Hệ Thống").role(UserRole.ADMIN).build();
        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

        FaqResponseDto result = faqService.createFaq(authorId, req);

        assertThat(result).isNotNull();
        assertThat(result.getImageUrl()).isEqualTo("https://cani.vn/assets/qr-guide.png");
        assertThat(result.getAttachmentUrl()).isEqualTo("https://cani.vn/docs/quy-trinh-checkin.pdf");
        assertThat(result.getAttachmentName()).isEqualTo("quy-trinh-checkin.pdf");
    }
}

