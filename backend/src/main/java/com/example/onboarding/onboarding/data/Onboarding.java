package com.example.onboarding.onboarding.data;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "onboardings")
public class Onboarding {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String adminName;
    @Column(nullable = false)
    private String adminEmail;
    @Column(nullable = false)
    private String companyName;
    @Column(nullable = false)
    private String companyDomain;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnboardingStatus status;
    private String providerStatus;
    @Column(length = 300)
    private String statusMessage;
    @Column(nullable = false)
    private int revision;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Onboarding() { }

    public Onboarding(String adminName, String adminEmail, String companyName, String companyDomain) {
        this.id = UUID.randomUUID();
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.companyName = companyName;
        this.companyDomain = companyDomain;
        this.status = OnboardingStatus.PENDING;
        this.statusMessage = "Verification queued";
        this.revision = 1;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void updateContact(String email) {
        this.adminEmail = email;
        this.revision++;
        this.status = OnboardingStatus.PENDING;
        this.statusMessage = "Verification queued";
        this.providerStatus = null;
        this.updatedAt = Instant.now();
    }

    public void setVerification(OnboardingStatus status, String providerStatus, String message) {
        this.status = status;
        this.providerStatus = providerStatus;
        this.statusMessage = message;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAdminName() { return adminName; }
    public String getAdminEmail() { return adminEmail; }
    public String getCompanyName() { return companyName; }
    public String getCompanyDomain() { return companyDomain; }
    public OnboardingStatus getStatus() { return status; }
    public String getProviderStatus() { return providerStatus; }
    public String getStatusMessage() { return statusMessage; }
    public int getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
