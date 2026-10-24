package com.example.onboarding.verification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.onboarding.verification.data.JobStatus;
import com.example.onboarding.verification.data.VerificationJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VerificationJobRepository
        extends JpaRepository<VerificationJob, UUID> {

    Optional<VerificationJob> findByOnboardingIdAndRevision(
            UUID onboardingId, int revision
    );

    @Query("""
        select j.id from VerificationJob j
        where
          (j.status = :pending and j.nextAttemptAt <= :now)
          or
          (j.status = :processing and j.leaseUntil <= :now)
        order by j.nextAttemptAt asc
        """)
    List<UUID> findDue(
            @Param("pending") JobStatus pending,
            @Param("processing") JobStatus processing,
            @Param("now") Instant now,
            Pageable pageable
    );

    // Synchronous first attempt:
    // allowed before nextAttemptAt, but only once.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update VerificationJob j
        set j.status = :processing,
            j.claimToken = :token,
            j.leaseUntil = :leaseUntil,
            j.attemptCount = 1
        where j.id = :id
          and j.status = :pending
          and j.attemptCount = 0
        """)
    int claimInitial(
            @Param("id") UUID id,
            @Param("pending") JobStatus pending,
            @Param("processing") JobStatus processing,
            @Param("token") UUID token,
            @Param("leaseUntil") Instant leaseUntil
    );

    // Background attempts:
    // claim only due jobs or expired processing leases.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update VerificationJob j
        set j.status = :processing,
            j.claimToken = :token,
            j.leaseUntil = :leaseUntil,
            j.attemptCount = j.attemptCount + 1
        where j.id = :id
          and (
            (j.status = :pending and j.nextAttemptAt <= :now)
            or
            (j.status = :processing and j.leaseUntil <= :now)
          )
        """)
    int claim(
            @Param("id") UUID id,
            @Param("pending") JobStatus pending,
            @Param("processing") JobStatus processing,
            @Param("token") UUID token,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil
    );
}
