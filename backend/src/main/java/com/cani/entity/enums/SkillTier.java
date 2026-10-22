package com.cani.entity.enums;

/**
 * Skill tiers and their Skill Point thresholds - single source of truth (BR-64).
 * Starter 0-99, Bronze 100-199, Silver 200-349, Gold 350-499,
 * Platinum 500-699, Diamond 700-899, Master 900+.
 */
public enum SkillTier {
    STARTER(0),
    BRONZE(100),
    SILVER(200),
    GOLD(350),
    PLATINUM(500),
    DIAMOND(700),
    MASTER(900);

    private final int minSp;

    SkillTier(int minSp) {
        this.minSp = minSp;
    }

    public int getMinSp() {
        return minSp;
    }

    public static SkillTier fromTotalSp(int totalSp) {
        SkillTier result = STARTER;
        for (SkillTier tier : values()) {
            if (totalSp >= tier.minSp) {
                result = tier;
            }
        }
        return result;
    }

    /** Next tier, or null when already at the top. */
    public SkillTier next() {
        int idx = ordinal() + 1;
        return idx < values().length ? values()[idx] : null;
    }
}
