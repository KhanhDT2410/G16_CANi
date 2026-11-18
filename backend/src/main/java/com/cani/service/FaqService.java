package com.cani.service;

import com.cani.dto.faq.*;
import com.cani.entity.AuditLog;
import com.cani.entity.Faq;
import com.cani.entity.User;
import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqStatus;
import com.cani.entity.enums.TicketCategory;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.FaqRepository;
import com.cani.repository.SupportTicketRepository;
import com.cani.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý nghiệp vụ Quản lý FAQ & Help Center (SRS FE-12, UC-110).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FaqService {

    private final FaqRepository faqRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // UC-110b: Help Center công khai cho người dùng
    // ------------------------------------------------------------------

    public List<FaqPublicResponseDto> getPublicFaqs(FaqCategory category) {
        List<Faq> faqs;
        if (category != null) {
            faqs = faqRepository.findByStatusAndCategoryOrderByViewCountDescPublishedAtDesc(FaqStatus.PUBLISHED, category);
        } else {
            faqs = faqRepository.findByStatusOrderByViewCountDescPublishedAtDesc(FaqStatus.PUBLISHED);
        }
        return faqs.stream().map(this::toPublicResponseDto).toList();
    }

    @Transactional
    public FaqPublicResponseDto getPublicFaqById(UUID id) {
        Faq faq = faqRepository.findById(id)
                .filter(f -> f.getStatus() == FaqStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi thường gặp", "id", id));

        faq.setViewCount(faq.getViewCount() + 1);
        Faq saved = faqRepository.save(faq);
        return toPublicResponseDto(saved);
    }

    // ------------------------------------------------------------------
    // UC-110a: Quản lý FAQ dành cho Admin & Customer Care
    // ------------------------------------------------------------------

    public Page<FaqResponseDto> getAllFaqs(FaqStatus status, FaqCategory category, Pageable pageable) {
        Specification<Faq> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return faqRepository.findAll(spec, pageable).map(this::toResponseDto);
    }

    public FaqResponseDto getFaqById(UUID id) {
        Faq faq = faqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi thường gặp", "id", id));
        return toResponseDto(faq);
    }

    @Transactional
    public FaqResponseDto createFaq(UUID authorId, CreateFaqRequestDto req) {
        Faq faq = Faq.builder()
                .category(req.getCategory())
                .question(req.getQuestion().trim())
                .summary(req.getSummary() != null && !req.getSummary().isBlank() ? req.getSummary().trim() : null)
                .answer(req.getAnswer().trim())
                .imageUrl(req.getImageUrl() != null && !req.getImageUrl().isBlank() ? req.getImageUrl().trim() : null)
                .attachmentUrl(req.getAttachmentUrl() != null && !req.getAttachmentUrl().isBlank() ? req.getAttachmentUrl().trim() : null)
                .attachmentName(req.getAttachmentName() != null && !req.getAttachmentName().isBlank() ? req.getAttachmentName().trim() : null)
                .status(FaqStatus.DRAFT)
                .authorId(authorId)
                .viewCount(0)
                .build();

        Faq saved = faqRepository.save(faq);
        recordAudit(authorId, "CREATE_FAQ", saved, null,
                Map.of("category", saved.getCategory().name(), "question", saved.getQuestion()));

        return toResponseDto(saved);
    }

    @Transactional
    public FaqResponseDto updateFaq(UUID actorId, UUID id, UpdateFaqRequestDto req) {
        Faq faq = faqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi thường gặp", "id", id));

        if (faq.getStatus() == FaqStatus.ARCHIVED) {
            throw new BadRequestException("Không thể chỉnh sửa câu hỏi đã lưu trữ");
        }

        Map<String, String> oldValues = Map.of(
                "category", faq.getCategory().name(),
                "question", faq.getQuestion()
        );

        faq.setCategory(req.getCategory());
        faq.setQuestion(req.getQuestion().trim());
        faq.setSummary(req.getSummary() != null && !req.getSummary().isBlank() ? req.getSummary().trim() : null);
        faq.setAnswer(req.getAnswer().trim());
        faq.setImageUrl(req.getImageUrl() != null && !req.getImageUrl().isBlank() ? req.getImageUrl().trim() : null);
        faq.setAttachmentUrl(req.getAttachmentUrl() != null && !req.getAttachmentUrl().isBlank() ? req.getAttachmentUrl().trim() : null);
        faq.setAttachmentName(req.getAttachmentName() != null && !req.getAttachmentName().isBlank() ? req.getAttachmentName().trim() : null);

        Faq saved = faqRepository.save(faq);
        recordAudit(actorId, "UPDATE_FAQ", saved, oldValues,
                Map.of("category", saved.getCategory().name(), "question", saved.getQuestion()));

        return toResponseDto(saved);
    }

    @Transactional
    public FaqResponseDto publishFaq(UUID actorId, UUID id) {
        Faq faq = faqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi thường gặp", "id", id));

        if (faq.getStatus() == FaqStatus.ARCHIVED) {
            throw new BadRequestException("Không thể xuất bản câu hỏi đã lưu trữ");
        }

        if (faq.getStatus() == FaqStatus.PUBLISHED) {
            // Idempotent: đã publish thì trả về bản ghi hiện tại
            return toResponseDto(faq);
        }

        Map<String, String> oldValues = Map.of("status", faq.getStatus().name());
        faq.setStatus(FaqStatus.PUBLISHED);
        faq.setPublishedAt(Instant.now());

        Faq saved = faqRepository.save(faq);
        recordAudit(actorId, "PUBLISH_FAQ", saved, oldValues, Map.of("status", FaqStatus.PUBLISHED.name()));

        return toResponseDto(saved);
    }

    @Transactional
    public FaqResponseDto archiveFaq(UUID actorId, UUID id) {
        Faq faq = faqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Câu hỏi thường gặp", "id", id));

        if (faq.getStatus() == FaqStatus.ARCHIVED) {
            return toResponseDto(faq);
        }

        Map<String, String> oldValues = Map.of("status", faq.getStatus().name());
        faq.setStatus(FaqStatus.ARCHIVED);
        faq.setArchivedAt(Instant.now());

        Faq saved = faqRepository.save(faq);
        recordAudit(actorId, "ARCHIVE_FAQ", saved, oldValues, Map.of("status", FaqStatus.ARCHIVED.name()));

        return toResponseDto(saved);
    }

    // ------------------------------------------------------------------
    // UC-110c: Thống kê & Tỷ lệ làm chệch hướng ticket (Ticket Deflection Rate)
    // ------------------------------------------------------------------

    public FaqAnalyticsDto getAnalytics() {
        List<Faq> allFaqs = faqRepository.findAll();

        long totalViews = allFaqs.stream()
                .mapToLong(f -> f.getViewCount() != null ? f.getViewCount() : 0)
                .sum();

        Map<FaqCategory, Long> viewsByCategory = new EnumMap<>(FaqCategory.class);
        for (FaqCategory cat : FaqCategory.values()) {
            viewsByCategory.put(cat, 0L);
        }
        for (Faq f : allFaqs) {
            if (f.getCategory() != null && f.getViewCount() != null) {
                viewsByCategory.put(f.getCategory(), viewsByCategory.get(f.getCategory()) + f.getViewCount());
            }
        }

        List<FaqResponseDto> topFaqs = faqRepository.findTop5ByOrderByViewCountDesc()
                .stream()
                .map(this::toResponseDto)
                .toList();

        // Tính ticket deflection rate:
        // Danh mục FAQ đã PUBLISHED
        Set<FaqCategory> publishedFaqCats = allFaqs.stream()
                .filter(f -> f.getStatus() == FaqStatus.PUBLISHED)
                .map(Faq::getCategory)
                .collect(Collectors.toSet());

        Set<TicketCategory> correspondingTicketCats = new HashSet<>();
        for (FaqCategory fc : publishedFaqCats) {
            TicketCategory tc = mapFaqCategoryToTicketCategory(fc);
            if (tc != null) {
                correspondingTicketCats.add(tc);
            }
        }

        long totalTickets = supportTicketRepository.count();
        long deflectedTickets = 0;
        for (TicketCategory tc : correspondingTicketCats) {
            deflectedTickets += supportTicketRepository.countByCategory(tc);
        }

        double deflectionRate = totalTickets > 0 ? (deflectedTickets * 100.0) / totalTickets : 0.0;
        deflectionRate = Math.round(deflectionRate * 100.0) / 100.0;

        String formula = "deflectionRate = (số ticket có category tương ứng FAQ đã publish) / (tổng số ticket) * 100";

        return FaqAnalyticsDto.builder()
                .totalViews(totalViews)
                .viewsByCategory(viewsByCategory)
                .topFaqs(topFaqs)
                .ticketDeflectionRate(deflectionRate)
                .calculationFormula(formula)
                .build();
    }

    private TicketCategory mapFaqCategoryToTicketCategory(FaqCategory faqCategory) {
        if (faqCategory == null) return null;
        return switch (faqCategory) {
            case ACCOUNT -> TicketCategory.ACCOUNT;
            case PAYMENT -> TicketCategory.BILLING;
            default -> null; // SHIFT, DISPUTE không có category tương ứng trực tiếp trong ticket
        };
    }

    private void recordAudit(UUID actorUserId, String action, Faq faq,
                             Map<String, String> oldValues, Map<String, String> newValues) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(actorUserId)
                    .action(action)
                    .entityType("FAQ")
                    .entityId(faq.getId())
                    .oldValues(oldValues != null ? objectMapper.writeValueAsString(oldValues) : null)
                    .newValues(newValues != null ? objectMapper.writeValueAsString(newValues) : null)
                    .build();
            auditLogService.logAction(entry);
        } catch (JsonProcessingException e) {
            log.error("Không thể chuẩn bị AuditLog cho FAQ {}: {}", faq.getId(), e.getMessage());
        }
    }

    private FaqResponseDto toResponseDto(Faq faq) {
        String authorName = null;
        if (faq.getAuthorId() != null) {
            authorName = userRepository.findById(faq.getAuthorId())
                    .map(User::getFullName)
                    .orElse(null);
        }

        return FaqResponseDto.builder()
                .id(faq.getId())
                .category(faq.getCategory())
                .question(faq.getQuestion())
                .summary(faq.getSummary())
                .answer(faq.getAnswer())
                .imageUrl(faq.getImageUrl())
                .attachmentUrl(faq.getAttachmentUrl())
                .attachmentName(faq.getAttachmentName())
                .status(faq.getStatus())
                .authorId(faq.getAuthorId())
                .authorName(authorName)
                .publishedAt(faq.getPublishedAt())
                .archivedAt(faq.getArchivedAt())
                .viewCount(faq.getViewCount() != null ? faq.getViewCount() : 0)
                .createdAt(faq.getCreatedAt())
                .updatedAt(faq.getUpdatedAt())
                .build();
    }

    private FaqPublicResponseDto toPublicResponseDto(Faq faq) {
        return FaqPublicResponseDto.builder()
                .id(faq.getId())
                .category(faq.getCategory())
                .question(faq.getQuestion())
                .summary(faq.getSummary())
                .answer(faq.getAnswer())
                .imageUrl(faq.getImageUrl())
                .attachmentUrl(faq.getAttachmentUrl())
                .attachmentName(faq.getAttachmentName())
                .viewCount(faq.getViewCount() != null ? faq.getViewCount() : 0)
                .build();
    }

    // ------------------------------------------------------------------
    // Quản lý upload & phục vụ file ảnh đính kèm FAQ từ thiết bị
    // ------------------------------------------------------------------

    private static final String UPLOAD_DIR = "uploads/faqs";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    public String uploadFaqImage(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tải lên không được để trống");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("Chỉ chấp nhận các định dạng ảnh: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BadRequestException("Dung lượng tệp ảnh không được vượt quá 5MB");
        }

        try {
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(UPLOAD_DIR);
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String savedFilename = "faq_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            java.nio.file.Path targetPath = uploadPath.resolve(savedFilename);
            java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return "/api/v1/faqs/images/" + savedFilename;
        } catch (java.io.IOException e) {
            log.error("Lỗi khi lưu tệp ảnh FAQ: {}", e.getMessage(), e);
            throw new BadRequestException("Không thể lưu tệp ảnh: " + e.getMessage());
        }
    }

    public org.springframework.core.io.Resource loadFaqImage(String filename) {
        try {
            // Ngăn chặn Path Traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                throw new BadRequestException("Tên tệp không hợp lệ");
            }

            java.nio.file.Path filePath = java.nio.file.Paths.get(UPLOAD_DIR).resolve(filename).normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResourceNotFoundException("Không tìm thấy tệp ảnh: " + filename);
            }
        } catch (java.net.MalformedURLException e) {
            throw new ResourceNotFoundException("Đường dẫn tệp không hợp lệ: " + filename);
        }
    }
}

