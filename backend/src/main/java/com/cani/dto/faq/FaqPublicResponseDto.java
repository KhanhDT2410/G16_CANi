package com.cani.dto.faq;

import com.cani.entity.enums.FaqCategory;
import com.cani.entity.enums.FaqTargetRole;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaqPublicResponseDto {

    private UUID id;
    private FaqCategory category;
    private FaqTargetRole targetRole;
    private String question;
    private String summary;
    private String answer;
    private String imageUrl;
    private String attachmentUrl;
    private String attachmentName;
    private Integer viewCount;
}

