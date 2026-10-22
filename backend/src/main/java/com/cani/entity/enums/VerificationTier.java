package com.cani.entity.enums;

/**
 * Skill verification tiers (BR-16) and the SP_verify each tier grants per skill (BR-65).
 */
public enum VerificationTier {
    SELF_DECLARED(0),
    AI_DOCUMENT(25),
    PLATFORM_PROVEN(50);

    private final int spVerify;

    VerificationTier(int spVerify) {
        this.spVerify = spVerify;
    }

    public int getSpVerify() {
        return spVerify;
    }
}
