package com.cani.service;

import com.cani.dto.skill.AdminSkillRequestDto;
import com.cani.dto.skill.AdminSkillResponseDto;
import com.cani.entity.Skill;
import com.cani.exception.BadRequestException;
import com.cani.exception.ResourceNotFoundException;
import com.cani.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSkillService {

    private final SkillRepository skillRepository;

    @Transactional(readOnly = true)
    public Page<AdminSkillResponseDto> getSkills(String keyword, String category, Boolean isActive, Pageable pageable) {
        Specification<Skill> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate descMatch = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern);
                predicates.add(cb.or(nameMatch, descMatch));
            }

            if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }

            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        return skillRepository.findAll(spec, pageable).map(AdminSkillResponseDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return skillRepository.findAllCategories();
    }

    @Transactional(readOnly = true)
    public AdminSkillResponseDto getSkillById(UUID skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng với ID: " + skillId));
        return AdminSkillResponseDto.fromEntity(skill);
    }

    @Transactional
    public AdminSkillResponseDto createSkill(AdminSkillRequestDto dto) {
        String trimmedName = dto.getName().trim();
        Optional<Skill> existing = skillRepository.findByNameIgnoreCase(trimmedName);
        if (existing.isPresent()) {
            throw new BadRequestException("Kỹ năng với tên '" + trimmedName + "' đã tồn tại trong hệ thống.");
        }

        Skill skill = Skill.builder()
                .name(trimmedName)
                .category(dto.getCategory() != null && !dto.getCategory().trim().isEmpty() ? dto.getCategory().trim() : "Khác")
                .description(dto.getDescription() != null ? dto.getDescription().trim() : "")
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        Skill saved = skillRepository.save(skill);
        log.info("Admin created new skill: {} (ID: {})", saved.getName(), saved.getSkillId());
        return AdminSkillResponseDto.fromEntity(saved);
    }

    @Transactional
    public AdminSkillResponseDto updateSkill(UUID skillId, AdminSkillRequestDto dto) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng với ID: " + skillId));

        String trimmedName = dto.getName().trim();
        Optional<Skill> existing = skillRepository.findByNameIgnoreCase(trimmedName);
        if (existing.isPresent() && !existing.get().getSkillId().equals(skillId)) {
            throw new BadRequestException("Tên kỹ năng '" + trimmedName + "' đã trùng với một kỹ năng khác.");
        }

        skill.setName(trimmedName);
        skill.setCategory(dto.getCategory() != null && !dto.getCategory().trim().isEmpty() ? dto.getCategory().trim() : "Khác");
        skill.setDescription(dto.getDescription() != null ? dto.getDescription().trim() : "");
        if (dto.getIsActive() != null) {
            skill.setIsActive(dto.getIsActive());
        }

        Skill updated = skillRepository.save(skill);
        log.info("Admin updated skill: {} (ID: {})", updated.getName(), updated.getSkillId());
        return AdminSkillResponseDto.fromEntity(updated);
    }

    @Transactional
    public AdminSkillResponseDto toggleStatus(UUID skillId) {
        Skill skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỹ năng với ID: " + skillId));

        boolean nextStatus = skill.getIsActive() == null || !skill.getIsActive();
        skill.setIsActive(nextStatus);

        Skill updated = skillRepository.save(skill);
        log.info("Admin toggled skill status: {} -> isActive: {}", updated.getName(), updated.getIsActive());
        return AdminSkillResponseDto.fromEntity(updated);
    }
}
