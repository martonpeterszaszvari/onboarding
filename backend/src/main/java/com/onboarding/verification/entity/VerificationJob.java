package com.onboarding.verification.entity;

import com.onboarding.verification.data.JobStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "verification_jobs")
@Data
public class VerificationJob {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID onboardingId;
    @Column(nullable = false)
    private int revision;
    @Column(nullable = false)
    private String email;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    @Column(nullable = false)
    private int attemptCount;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private Instant leaseUntil;
    private UUID claimToken;
    @Column(nullable = false)
    private Instant createdAt;

    protected VerificationJob() { }

    public VerificationJob(UUID onboardingId, int revision, String email) {
        this(onboardingId, revision, email, Instant.now());
    }

    public VerificationJob(UUID onboardingId, int revision, String email, Instant firstAttemptAt) {
        this.id = UUID.randomUUID();
        this.onboardingId = onboardingId;
        this.revision = revision;
        this.email = email;
        this.status = JobStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = firstAttemptAt;
        this.createdAt = Instant.now();
    }

    public void reschedule(Instant dueAt) {
        this.status = JobStatus.PENDING;
        this.nextAttemptAt = dueAt;
        this.claimToken = null;
        this.leaseUntil = null;
    }

    public void finish() {
        this.status = JobStatus.DONE;
        this.claimToken = null;
        this.leaseUntil = null;
    }

    public void cancel() {
        this.status = JobStatus.CANCELLED;
        this.claimToken = null;
        this.leaseUntil = null;
    }

}
