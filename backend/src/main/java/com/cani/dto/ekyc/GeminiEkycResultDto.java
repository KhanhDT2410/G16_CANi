package com.cani.dto.ekyc;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiEkycResultDto {

    private String idNumber;
    private String fullName;
    private String dob;
    private String gender;
    private String nationality;
    private String originAddress;
    private String residenceAddress;
    private String expiryDate;
    private String issueDate;
    private String identificationFeatures;

    private double ocrConfidence;       // 0.0 -> 1.0
    private double faceMatchScore;      // 0.0 -> 100.0

    private boolean isCutCorner;
    private boolean isScreenCaptured;
    private boolean isPhotoshoped;
    private boolean isExpired;

    private String routingDecision;     // AUTO_APPROVED, MANUAL_REVIEW, REJECTED
    private String routingReason;

    private Map<String, Object> rawAnalysis;
}
