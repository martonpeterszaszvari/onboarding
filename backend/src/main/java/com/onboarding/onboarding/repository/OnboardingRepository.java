package com.onboarding.onboarding.repository;

import com.onboarding.onboarding.entity.Onboarding;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnboardingRepository extends JpaRepository<Onboarding, UUID> {
    List<Onboarding> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Onboarding o where o.id = :id")
    Optional<Onboarding> findLockedById(@Param("id") UUID id);
}
