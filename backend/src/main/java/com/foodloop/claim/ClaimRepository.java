package com.foodloop.claim;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /**
     * SELECT ... FOR UPDATE: every move of a claim takes this lock first (then the listing's), so moves take turns.
     * Plain SQL: Hibernate's lock mode would add "OF <alias>", which TiDB refuses.
     */
    @Query(value = "select * from claims where id = :id for update", nativeQuery = true)
    Optional<Claim> lockById(Long id);

    Page<Claim> findByReceiverIdAndStatusIn(Long receiverId, Collection<ClaimStatus> statuses, Pageable pageable);

    long countByReceiverIdAndStatusIn(Long receiverId, Collection<ClaimStatus> statuses);

    List<Claim> findByListingIdInOrderById(Collection<Long> listingIds);

    List<Claim> findByListingIdOrderById(Long listingId);

    boolean existsByListingIdAndReceiverId(Long listingId, Long receiverId);

    /** The claim that is active on a listing (there is at most one: see uq_claims_one_active_per_listing). */
    @Query("select c from Claim c where c.listingId = :listingId and c.status in :statuses")
    Optional<Claim> findActiveByListing(Long listingId, Collection<ClaimStatus> statuses);

    /** For suspension and account deletion: a receiver's active claims. */
    List<Claim> findByReceiverIdAndStatusIn(Long receiverId, Collection<ClaimStatus> statuses);

    /** For suspension and account deletion: the active claims on a donor's listings. */
    @Query("select c from Claim c where c.status in :statuses and c.listingId in (select l.id from Listing l where l.donorId = :donorId)")
    List<Claim> findActiveForDonor(Long donorId, Collection<ClaimStatus> statuses);

    /** Expiry job: claims still before the hand-over whose listing's pick-up window has ended. */
    @Query("""
            select c from Claim c
            where c.status in (com.foodloop.claim.ClaimStatus.CLAIMED, com.foodloop.claim.ClaimStatus.READY)
              and c.id > :afterId
              and exists (select 1 from Listing l where l.id = c.listingId and l.pickupUntil <= :now)
            order by c.id
            """)
    List<Claim> findDueForExpiry(Instant now, long afterId, Limit limit);

    /** Auto-complete job: picked up at least 24 hours ago and the receiver never confirmed. */
    @Query("""
            select c from Claim c
            where c.status = com.foodloop.claim.ClaimStatus.PICKED_UP and c.pickedUpAt <= :cutoff and c.id > :afterId
            order by c.id
            """)
    List<Claim> findDueForAutoComplete(Instant cutoff, long afterId, Limit limit);

    /**
     * Ends a claim as expired only if it is still before the hand-over. Two runs at once, or a donor moving the claim
     * at the same moment, can't both win: only the call that gets 1 back goes on to send the email.
     */
    @Modifying
    @Transactional
    @Query("""
            update Claim c set c.status = com.foodloop.claim.ClaimStatus.EXPIRED, c.expiredAt = :now, c.updatedAt = :touched
            where c.id = :id and c.status in (com.foodloop.claim.ClaimStatus.CLAIMED, com.foodloop.claim.ClaimStatus.READY)
            """)
    int expireIfOpen(Long id, Instant now, Instant touched);

    @Modifying
    @Transactional
    @Query("""
            update Claim c set c.status = com.foodloop.claim.ClaimStatus.COMPLETED, c.completedAt = :now, c.autoCompleted = true,
                c.updatedAt = :touched
            where c.id = :id and c.status = com.foodloop.claim.ClaimStatus.PICKED_UP
            """)
    int autoCompleteIfPickedUp(Long id, Instant now, Instant touched);
}
