package com.cani.dto.faq;

import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaqResponseDto {

    private UUID id;
    private FaqCategory category;
    private String question;
    private String answer;
    private FaqStatus status;
    private UUID authorId;
    private String authorName;
    private Instant publishedAt;
    private Instant archivedAt;
    private Integer viewCount;
    private Instant createdAt;
    private Instant updatedAt;
}

