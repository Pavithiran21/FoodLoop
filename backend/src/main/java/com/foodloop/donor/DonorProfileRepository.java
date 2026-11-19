package com.foodloop.donor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface DonorProfileRepository extends JpaRepository<DonorProfile, Long> {

    /** Locked while an Admin decides, so two Admins can't verify and reject the same donor at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from DonorProfile p where p.userId = :userId")
    Optional<DonorProfile> lockByUserId(Long userId);

    long countByVerificationStatus(VerificationStatus status);

    /** The Verify donors list: donors with one status who haven't deleted their account, oldest request first. */
    @Query(value = """
            select new com.foodloop.admin.DonorCard(u.id, p.businessName, u.name, u.email, p.pickupAddress, p.fssaiNo,
                   p.verificationStatus, p.rejectionReason, coalesce(p.reviewRequestedAt, p.createdAt))
            from DonorProfile p, User u
            where u.id = p.userId and p.verificationStatus = :status and u.status <> com.foodloop.user.UserStatus.DELETED
            order by coalesce(p.reviewRequestedAt, p.createdAt), u.id
            """,
            countQuery = """
            select count(p) from DonorProfile p, User u
            where u.id = p.userId and p.verificationStatus = :status and u.status <> com.foodloop.user.UserStatus.DELETED
            """)
    org.springframework.data.domain.Page<com.foodloop.admin.DonorCard> findCards(VerificationStatus status,
            org.springframework.data.domain.Pageable pageable);
}
