CREATE TABLE onboardings (
    id UUID PRIMARY KEY,
    admin_name VARCHAR(120) NOT NULL,
    admin_email VARCHAR(255) NOT NULL,
    company_name VARCHAR(160) NOT NULL,
    company_domain VARCHAR(255) NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider_status VARCHAR(40),
    status_message VARCHAR(300),
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE verification_jobs (
    id UUID PRIMARY KEY,
    onboarding_id UUID NOT NULL REFERENCES onboardings(id),
    revision INTEGER NOT NULL,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    claim_token UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT verification_jobs_attempt_count_nonnegative CHECK (attempt_count >= 0)
);

CREATE INDEX idx_verification_jobs_pending ON verification_jobs (status, next_attempt_at);
CREATE INDEX idx_verification_jobs_lease ON verification_jobs (status, lease_until);
CREATE INDEX idx_verification_jobs_onboarding ON verification_jobs (onboarding_id);
