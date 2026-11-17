-- V29__add_enum_domain_aliases.sql
-- Ensure PostgreSQL domain aliases exist for Java enum class names (PascalCase & lowercase without underscore)
-- to prevent Hibernate typed cast exceptions (e.g., 'FULLY_STAFFED'::ShiftStatus or ::shiftstatus)

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'shiftstatus') THEN
        CREATE DOMAIN shiftstatus AS shift_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ShiftStatus') THEN
        CREATE DOMAIN "ShiftStatus" AS shift_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'proposalstatus') THEN
        CREATE DOMAIN proposalstatus AS proposal_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ProposalStatus') THEN
        CREATE DOMAIN "ProposalStatus" AS proposal_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'paymentstatus') THEN
        CREATE DOMAIN paymentstatus AS payment_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'PaymentStatus') THEN
        CREATE DOMAIN "PaymentStatus" AS payment_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'kycstatus') THEN
        CREATE DOMAIN kycstatus AS kyc_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'KycStatus') THEN
        CREATE DOMAIN "KycStatus" AS kyc_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'leadtime') THEN
        CREATE DOMAIN leadtime AS lead_time;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'LeadTime') THEN
        CREATE DOMAIN "LeadTime" AS lead_time;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notiftype') THEN
        CREATE DOMAIN notiftype AS notif_type;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'NotifType') THEN
        CREATE DOMAIN "NotifType" AS notif_type;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'skilltier') THEN
        CREATE DOMAIN skilltier AS skill_tier;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'SkillTier') THEN
        CREATE DOMAIN "SkillTier" AS skill_tier;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'subscriptionstatus') THEN
        CREATE DOMAIN subscriptionstatus AS subscription_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'SubscriptionStatus') THEN
        CREATE DOMAIN "SubscriptionStatus" AS subscription_status;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'verificationstatus') THEN
        CREATE DOMAIN verificationstatus AS verification_status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'VerificationStatus') THEN
        CREATE DOMAIN "VerificationStatus" AS verification_status;
    END IF;
END $$;
