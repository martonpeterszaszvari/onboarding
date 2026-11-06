package com.onboarding.verification.repository;

import com.onboarding.verification.entity.VerificationJob;
import com.onboarding.verification.data.JobStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationJobRepository extends JpaRepository<VerificationJob, UUID> {
    Optional<VerificationJob> findByOnboardingIdAndRevision(UUID onboardingId, int revision);

    // Lock completion against a concurrent lease reclaim (fencing token check).
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from VerificationJob j where j.id = :id")
    Optional<VerificationJob> findLockedById(@Param("id") UUID id);

    @Query("select min(j.nextAttemptAt) from VerificationJob j where j.status = :pending")
    Optional<Instant> earliestPending(@Param("pending") JobStatus pending);

    @Query("select min(j.leaseUntil) from VerificationJob j where j.status = :processing")
    Optional<Instant> earliestProcessingLease(@Param("processing") JobStatus processing);

    // Reserve a brand-new SYNC job regardless of its firstAttemptAt grace period.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update VerificationJob j set j.status = :processing,
            j.claimToken = :token,
            j.leaseUntil = :leaseUntil,
            j.attemptCount = 1
        where j.id = :id and j.status = :pending and j.attemptCount = 0
        """)
    int claimInitial(@Param("id") UUID id,
                     @Param("pending") JobStatus pending,
                     @Param("processing") JobStatus processing,
                     @Param("token") UUID token,
                     @Param("leaseUntil") Instant leaseUntil);

    @Query("""
        select j.id from VerificationJob j where
            (j.status = :pending and j.nextAttemptAt <= :now)
            or (j.status = :processing and j.leaseUntil <= :now)
        order by j.nextAttemptAt asc
        """)
    List<UUID> findDue(@Param("pending") JobStatus pending,
                       @Param("processing") JobStatus processing,
                       @Param("now") Instant now,
                       Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update VerificationJob j set j.status = :processing,
            j.claimToken = :token,
            j.leaseUntil = :leaseUntil,
            j.attemptCount = j.attemptCount + 1
        where j.id = :id and
          ((j.status = :pending and j.nextAttemptAt <= :now) or
           (j.status = :processing and j.leaseUntil <= :now))
        """)
    int claim(@Param("id") UUID id,
              @Param("pending") JobStatus pending,
              @Param("processing") JobStatus processing,
              @Param("token") UUID token,
              @Param("now") Instant now,
              @Param("leaseUntil") Instant leaseUntil);
}
