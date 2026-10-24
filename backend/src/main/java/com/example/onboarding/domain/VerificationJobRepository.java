package com.example.onboarding.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationJobRepository extends JpaRepository<VerificationJob, UUID> {
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
